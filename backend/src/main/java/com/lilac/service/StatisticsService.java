package com.lilac.service;

import com.lilac.domain.vo.StatisticsVO;

/**
 * 统计服务
 */
public interface StatisticsService {

    /**
     * 获取系统统计数据
     *
     * @return 统计数据
     */
    StatisticsVO getStatistics();
}