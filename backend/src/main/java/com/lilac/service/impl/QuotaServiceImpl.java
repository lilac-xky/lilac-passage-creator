package com.lilac.service.impl;

import com.mybatisflex.core.constant.SqlOperator;
import com.mybatisflex.core.query.QueryColumn;
import com.mybatisflex.core.query.QueryCondition;
import com.mybatisflex.core.query.QueryWrapper;
import com.lilac.domain.entity.Article;
import com.lilac.domain.entity.User;
import com.lilac.enums.HttpsCodeEnum;
import com.lilac.exception.BusinessException;
import com.lilac.mapper.ArticleMapper;
import com.lilac.mapper.UserMapper;
import com.lilac.service.QuotaService;
import com.lilac.service.UserService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.lilac.constant.UserConstant.ADMIN_ROLE;
import static com.lilac.constant.UserConstant.VIP_ROLE;

@Service
@Slf4j
public class QuotaServiceImpl implements QuotaService {

    @Resource
    private UserService userService;

    @Resource
    private UserMapper userMapper;

    @Resource
    private ArticleMapper articleMapper;

    /**
     * 检查用户配额
     */
    @Override
    public boolean hasQuota(User user) {
        // 管理员和VIP用户无限配额
        if (isAdmin(user) || isVip(user)) {
            return true;
        }
        // 从数据库查询最新配额，避免使用缓存的旧数据
        User freshUser = userService.getById(user.getId());
        if (freshUser == null) {
            return false;
        }
        Integer quota = freshUser.getQuota();
        return quota != null && quota > 0;
    }

    /**
     * 消耗配额
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void consumeQuota(User user) {
        // 管理员和VIP用户跳过检查
        if (isAdmin(user) || isVip(user)) {
            return;
        }

        // 使用原子更新：UPDATE user SET quota = quota - 1 WHERE id = ? AND quota > 0
        // 通过影响行数判断是否成功，避免并发问题
        int affectedRows = userMapper.decrementQuota(user.getId());

        if (affectedRows > 0) {
            log.info("用户配额已消耗, userId={}", user.getId());
        } else {
            log.warn("用户配额扣减失败（可能配额不足或并发冲突）, userId={}", user.getId());
        }
    }

    /**
     * 检查用户配额并消耗配额
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void checkAndConsumeQuota(User user) {
        // 管理员和VIP用户跳过检查
        if (isAdmin(user) || isVip(user)) {
            return;
        }

        // 使用原子更新：检查与消费合并为一个原子操作
        // UPDATE user SET quota = quota - 1 WHERE id = ? AND quota > 0
        int affectedRows = userMapper.decrementQuota(user.getId());

        if (affectedRows == 0) {
            // 影响行数为0，说明配额不足（已被其他请求消耗）
            throw new BusinessException(HttpsCodeEnum.OPERATION_ERROR, "配额不足，无法创建文章");
        }

        log.info("用户配额检查并消耗成功, userId={}", user.getId());
    }

    /**
     * 任务失败时退还配额（幂等）
     *
     * <p><b>为什么需要幂等</b>
     *
     * <p>失败回调可能被触发多次：
     * <ul>
     *   <li>异步任务重试</li>
     *   <li>多实例部署下多个节点都可能走到 catch 分支</li>
     *   <li>Spring 事务回滚后重新执行</li>
     * </ul>
     * 如果无条件 {@code quota + 1}，用户可能凭空多出配额 ——
     * 这是一个比"少退"更严重的问题（可被刷）。
     *
     * <p><b>实现方式：CAS 抢占标志位</b>
     *
     * <pre>
     * UPDATE article SET quotaRefunded = 1
     *  WHERE taskId = ? AND (quotaRefunded IS NULL OR quotaRefunded = 0)
     * </pre>
     * 只有影响行数为 1（即本次调用抢到了标志位）才执行配额 +1。
     * 重复调用时影响行数为 0，直接返回。
     *
     * <p>两个操作在同一事务内，任一步失败整体回滚，
     * 保证「标志位已置位但配额没退」这种中间态不会出现。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean refundQuotaOnce(String taskId) {
        if (taskId == null || taskId.isBlank()) {
            log.warn("退还配额失败：taskId 为空");
            return false;
        }

        // ① 查任务，拿到 userId
        Article article = articleMapper.selectOneByCondition(
                QueryCondition.create(new QueryColumn("taskId"), SqlOperator.EQUALS, taskId));
        if (article == null) {
            log.warn("退还配额失败：任务不存在, taskId={}", taskId);
            return false;
        }

        Long userId = article.getUserId();
        if (userId == null) {
            log.warn("退还配额失败：任务缺少 userId, taskId={}", taskId);
            return false;
        }

        // ② 判断任务属主是否真的被扣过配额
        //
        //    checkAndConsumeQuota 对 ADMIN / VIP 是直接 return 的，
        //    他们创建任务时并没有扣配额。如果这里无条件 +1，
        //    等于给 VIP 反复凭空加配额 —— 必须排除。
        User owner = userService.getById(userId);
        if (owner == null) {
            log.warn("退还配额失败：用户不存在, taskId={}, userId={}", taskId, userId);
            return false;
        }
        if (isAdmin(owner) || isVip(owner)) {
            // 没扣过，无需退还；但要把任务标记为「已处理」避免反复判定
            markRefunded(taskId);
            log.info("任务属主为管理员/VIP，创建时未扣配额，跳过退还, taskId={}, userId={}", taskId, userId);
            return false;
        }

        // ③ CAS 抢占退还标志位（幂等的关键）
        //    UPDATE article SET quotaRefunded = 1
        //     WHERE taskId = ? AND (quotaRefunded IS NULL OR quotaRefunded = 0)
        if (!claimRefundFlag(taskId)) {
            log.info("配额已退还过，跳过, taskId={}", taskId);
            return false;
        }

        // ④ 真正退还配额
        int affected = userMapper.incrementQuota(userId);
        if (affected == 0) {
            log.warn("退还配额时用户不存在, taskId={}, userId={}", taskId, userId);
            throw new IllegalStateException("用户不存在，无法退还配额: " + userId);
        }

        log.info("任务失败，配额已退还, taskId={}, userId={}", taskId, userId);
        return true;
    }

    /**
     * CAS 抢占退还标志位。
     *
     * @return true 表示本次调用抢到了（此前未退还）
     */
    private boolean claimRefundFlag(String taskId) {
        Article patch = new Article();
        patch.setQuotaRefunded(1);
        return articleMapper.updateByQuery(
                patch,
                true,
                QueryWrapper.create()
                        .where("taskId = ?", taskId)
                        .and("(quotaRefunded IS NULL OR quotaRefunded = 0)")) > 0;
    }

    /**
     * 仅标记已处理，不退还配额（用于 VIP/管理员场景）。
     */
    private void markRefunded(String taskId) {
        Article patch = new Article();
        patch.setQuotaRefunded(1);
        articleMapper.updateByQuery(
                patch,
                true,
                QueryWrapper.create()
                        .where("taskId = ?", taskId)
                        .and("(quotaRefunded IS NULL OR quotaRefunded = 0)"));
    }

    /**
     * 判断是否为管理员
     */
    private boolean isAdmin(User user) {
        return ADMIN_ROLE.equals(user.getUserRole());
    }

    /**
     * 判断是否为 VIP
     */
    private boolean isVip(User user) {
        return VIP_ROLE.equals(user.getUserRole());
    }
}
