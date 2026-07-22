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
}
