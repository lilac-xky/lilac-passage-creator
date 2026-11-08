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
 * 文章表
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "article", camelToUnderline = false)
public class Article implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * id
     */
    @Id(keyType = KeyType.Auto)
    private Long id;

    /**
     * 任务ID（UUID）
     */
    private String taskId;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 选题
     */
    private String topic;

    /**
     * 主标题
     */
    private String mainTitle;

    /**
     * 副标题
     */
    private String subTitle;

    /**
     * 大纲（JSON格式）
     */
    private String outline;

    /**
     * 正文（Markdown格式，不含图片）
     */
    private String content;

    /**
     * 完整图文（Markdown格式，含图片）
     */
    private String fullContent;

    /**
     * 封面图 URL
     */
    private String coverImage;

    /**
     * 配图列表（JSON数组）
     */
    private String images;

    /**
     * 配图总数（步骤 E1）
     *
     * <p>冗余存储便于聚合查询，避免每次都解析 JSON。
     */
    private Integer imageTotal;

    /**
     * 其中降级到兜底图源（PICSUM）的数量（步骤 E1）
     *
     * <p><b>为什么要单独记这个</b>：实测 34 张配图中 5 张降级（14.7%），
     * 但文章 status 仍是「已完成」、errorMessage 为 NULL，
     * 用户在页面上看不出任何异常 —— 这是**静默降级**。
     * 记录该字段后可：
     * <ul>
     *   <li>在前端提示「本文 N 张配图使用了占位图」</li>
     *   <li>按降级率排序定位问题图源</li>
     *   <li>降级率超阈值时把状态标为 PARTIAL 而非 COMPLETED</li>
     * </ul>
     */
    private Integer imageFallbackCount;

    /**
     * 配图降级是否达到告警级别（步骤 E2）：0-正常/轻微 1-严重降级
     *
     * <p>判定标准：降级率 >= 30%。
     *
     * <p><b>为什么用独立字段而不是把 status 改成 PARTIAL</b>：
     * 前端多处用 {@code status === '已完成'} 做分支（会话恢复、标签映射），
     * 新增状态值会让这些逻辑走错分支。保持 status 语义不变、
     * 用本字段表达降级程度，是兼容性更好的选择。
     */
    private Integer imageDegraded;

    /**
     * 状态：PENDING/PROCESSING/COMPLETED/FAILED
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 文章风格：tech/emotional/educational/humorous
     */
    private String style;

    /**
     * 用户补充描述
     */
    private String userDescription;

    /**
     * 允许的配图方式列表（JSON格式）
     */
    private String enabledImageMethods;

    /**
     * 标题方案列表（JSON格式）
     */
    private String titleOptions;

    /**
     * 当前阶段：PENDING/TITLE_GENERATING/TITLE_SELECTING/OUTLINE_GENERATING/OUTLINE_EDITING/CONTENT_GENERATING
     */
    private String phase;

    /**
     * 配额是否已退还（步骤 C4）。
     *
     * <p>0/null = 未退还，1 = 已退还。
     *
     * <p>作用：作为「退还配额」的<b>幂等标志位</b>。
     * 任务失败时会调用 {@code QuotaService.refundQuotaOnce}，
     * 该方法先 {@code UPDATE article SET quotaRefunded = 1 WHERE taskId = ? AND quotaRefunded = 0}，
     * 只有影响行数为 1（即抢到标志位）才真正退还配额。
     * 这样即使失败回调被重复触发（重试、多实例、用户重复操作），
     * 也只会退还一次。
     */
    private Integer quotaRefunded;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 完成时间
     */
    private LocalDateTime completedTime;

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