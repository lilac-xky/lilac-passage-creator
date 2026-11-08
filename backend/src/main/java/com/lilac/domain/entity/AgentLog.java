package com.lilac.domain.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 智能体日志表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "agent_log", camelToUnderline = false)
public class AgentLog implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 任务ID
     */
    private String taskId;

    /**
     * 智能体名称
     */
    private String agentName;

    /**
     * 使用的模型名（步骤 E1 补充，用于成本归因）
     */
    private String model;

    /**
     * 开始时间
     */
    private LocalDateTime startTime;

    /**
     * 结束时间
     */
    private LocalDateTime endTime;

    /**
     * 耗时（毫秒）
     */
    private Integer durationMs;

    /**
     * 输入 token 数（步骤 E1 补充，用于成本统计）
     */
    private Integer promptTokens;

    /**
     * 输出 token 数（步骤 E1 补充）
     */
    private Integer completionTokens;

    /**
     * 总 token 数（步骤 E1 补充，便于聚合与成本估算）
     */
    private Integer totalTokens;

    /**
     * 状态：SUCCESS/FAILED
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 使用的Prompt
     */
    private String prompt;

    /**
     * 使用的提示词模板 ID（步骤 D9 预留，E1 补齐写入）
     */
    private Long templateId;

    /**
     * 使用的提示词模板版本号（步骤 D9 预留，E1 补齐写入）
     */
    private Integer templateVersion;

    /**
     * 输入数据（JSON格式）
     */
    private String inputData;

    /**
     * 输出数据（JSON格式）
     */
    private String outputData;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否删除
     */
    @Column(isLogicDelete = true)
    private Integer isDelete;

}