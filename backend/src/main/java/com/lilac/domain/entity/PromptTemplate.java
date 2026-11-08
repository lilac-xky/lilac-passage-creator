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
 * 提示词模板表（步骤 D9/D10）
 *
 * <p>把原先硬编码在 {@code PromptConstant}（319 行）里的提示词迁到数据库，
 * 目的是支持：
 * <ul>
 *   <li>版本管理：同一 name 可以有多个 version，便于回滚</li>
 *   <li>热更新：改库即生效，不用改代码重新部署</li>
 *   <li>A/B 测试：同 name 多版本按 {@code abWeight} 分配流量</li>
 *   <li>效果回收：通过 {@code agent_log.templateId/templateVersion} 统计成功率与耗时</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "prompt_template", camelToUnderline = false)
public class PromptTemplate implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 模板名，对应原 PromptConstant 的常量名，如 AGENT1_TITLE_PROMPT
     */
    private String name;

    /**
     * 版本号，同一 name 下递增
     */
    private Integer version;

    /**
     * 所属智能体：TITLE / OUTLINE / CONTENT / IMAGE / STYLE / SVGD / OUTLINE_MODIFY
     */
    private String agentName;

    /**
     * 用途说明
     */
    private String description;

    /**
     * 模板正文，用 {varName} 做占位符
     */
    private String content;

    /**
     * 可用变量名，逗号分隔，如 topic,style
     */
    private String variables;

    /**
     * 是否启用：1-启用 0-停用
     */
    private Integer enabled;

    /**
     * A/B 权重（0-100）
     */
    private Integer abWeight;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @Column(isLogicDelete = true)
    private Integer isDelete;
}
