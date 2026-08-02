package com.lilac.controller;

import com.lilac.annotation.AuthCheck;
import com.lilac.constant.UserConstant;
import com.lilac.domain.result.Result;
import com.lilac.domain.vo.StatisticsVO;
import com.lilac.service.StatisticsService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 统计分析控制器
 */
@RestController
@RequestMapping("/statistics")
@Slf4j
public class StatisticsController {

    @Resource
    private StatisticsService statisticsService;

    /**
     * 获取系统统计数据（仅管理员）
     */
    @GetMapping("/overview")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public Result<StatisticsVO> getStatistics() {
        StatisticsVO statistics = statisticsService.getStatistics();
        return Result.success(statistics);
    }
}