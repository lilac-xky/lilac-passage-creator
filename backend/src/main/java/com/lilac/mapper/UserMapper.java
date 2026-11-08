package com.lilac.mapper;

import com.lilac.domain.entity.User;
import com.mybatisflex.core.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 用户 Mapper
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Update("UPDATE user SET quota = quota - 1 WHERE id = #{userId} AND quota > 0")
    int decrementQuota(@Param("userId") Long userId);

    /**
     * 退还 1 次配额（步骤 C4）。
     *
     * <p>用于任务在执行阶段失败时把创建任务时扣掉的配额还回去。
     *
     * <p><b>幂等性说明</b>：本方法本身不幂等，多次调用会多次退还。
     * 幂等由调用方保证 —— {@code QuotaService.refundQuotaOnce}
     * 通过先 CAS 抢占 {@code article.quotaRefunded} 标志位再调用本方法，
     * 确保同一任务只会退还一次。
     */
    @Update("UPDATE user SET quota = quota + 1 WHERE id = #{userId}")
    int incrementQuota(@Param("userId") Long userId);
}
