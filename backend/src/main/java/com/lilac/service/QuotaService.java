package com.lilac.service;

import com.lilac.domain.entity.User;

public interface QuotaService {

    /**
     * 检查用户是否有配额
     *
     * @param user 用户
     * @return true 存在配额，false 不存在配额
     */
    boolean hasQuota(User user);

    /**
     * 消耗配额
     *
     * @param user 用户
     */
    void consumeQuota(User user);

    /**
     * 检查用户是否有配额并消耗配额
     *
     * @param user 用户
     */
    void checkAndConsumeQuota(User user);

    /**
     * 任务失败时退还配额（幂等，步骤 C4）。
     *
     * <p>发布任务时通过 {@link #checkAndConsumeQuota} 扣除了 1 次配额。
     * 若任务在后续阶段执行失败，用户并没有得到任何产出，
     * 这次配额应当退还，否则相当于「白扣一次」。
     *
     * <p><b>幂等保证</b>：通过 {@code article.quotaRefunded} 标志位实现。
     * 只有第一次调用会真正退还，重复调用是空操作。
     * 这样即使：
     * <ul>
     *   <li>同一个失败回调被触发多次</li>
     *   <li>多实例部署下多个节点同时处理</li>
     *   <li>用户手动刷新导致重复请求</li>
     * </ul>
     * 也只会退还一次。
     *
     * <p>注意：管理员与 VIP 用户创建任务时本身不扣配额，
     * 因此也不会退还。
     *
     * @param taskId 任务 ID
     * @return true 表示本次调用真正完成了退还；false 表示无需退还或已退还过
     */
    boolean refundQuotaOnce(String taskId);
}
