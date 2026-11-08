-- =============================================================
-- lilac-passage-creator 建表脚本（全量）
-- =============================================================
-- 本文件是**新环境的唯一入口**：跑完它就有完整的最新结构。
-- 已有旧库请按顺序执行 db/upgrade/ 下的增量脚本。
--
-- 最后同步：2026-11-08（已包含 002~006 全部变更）
--   基  线：init.sql 初版（4 张表）
--   002  ：article.quotaRefunded
--   003  ：prompt_template 表 + agent_log.templateId/templateVersion
--   005  ：agent_log.model/promptTokens/completionTokens/totalTokens
--          article.imageTotal/imageFallbackCount
--   006  ：article.imageDegraded
--
-- ⚠️ 本文件不使用 Flyway，没有自动迁移机制，手动执行：
--      mysql -uroot -p < backend/src/main/resources/db/init.sql
--
-- ⚠️ 本文件与 upgrade/ 下的脚本会双份维护，改结构时**两处都要同步**。
--    （这是不用迁移工具的代价，已在 docs/changes/ 记录）
--
-- 注意：
--   1. 实体类全部标注 @Table(camelToUnderline = false)，
--      并配合 map-underscore-to-camel-case: false，
--      因此列名就是驼峰原样（taskId 而非 task_id）。
--   2. `user` 是 MySQL 保留字，任何地方引用都要加反引号。
-- =============================================================

CREATE DATABASE IF NOT EXISTS `lilac-passage-creator`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `lilac-passage-creator`;

-- -------------------------------------------------------------
-- 1. 用户表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user`
(
    `id`           bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
    `userAccount`  varchar(256) NOT NULL COMMENT '账号',
    `userPassword` varchar(512) NOT NULL COMMENT '密码',
    `userName`     varchar(256)          DEFAULT NULL COMMENT '用户昵称',
    `userAvatar`   varchar(1024)         DEFAULT NULL COMMENT '用户头像',
    `userProfile`  varchar(512)          DEFAULT NULL COMMENT '用户简介',
    `userRole`     varchar(256) NOT NULL DEFAULT 'user' COMMENT '用户角色：user/admin',
    `quota`        int          NOT NULL DEFAULT '10' COMMENT '剩余配额',
    `vipTime`      datetime              DEFAULT NULL COMMENT '成为会员时间',
    `editTime`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '编辑时间',
    `createTime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`     tinyint      NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_userAccount` (`userAccount`),
    KEY `idx_userName` (`userName`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='用户';

-- -------------------------------------------------------------
-- 2. 文章表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `article`
(
    `id`                  bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
    `taskId`              varchar(64)  NOT NULL COMMENT '任务ID（UUID）',
    `userId`              bigint       NOT NULL COMMENT '用户ID',
    `topic`               varchar(500) NOT NULL COMMENT '选题',
    `userDescription`     text COMMENT '用户补充描述',
    `enabledImageMethods` json                  DEFAULT NULL COMMENT '允许的配图方式列表',
    `style`               varchar(20)           DEFAULT NULL COMMENT '文章风格：tech/emotional/educational/humorous',
    `mainTitle`           varchar(200)          DEFAULT NULL COMMENT '主标题',
    `subTitle`            varchar(300)          DEFAULT NULL COMMENT '副标题',
    `titleOptions`        json                  DEFAULT NULL COMMENT '标题方案列表（3-5个方案）',
    `outline`             json                  DEFAULT NULL COMMENT '大纲（JSON格式）',
    `content`             text COMMENT '正文（Markdown格式）',
    `fullContent`         text COMMENT '完整图文（Markdown格式，含配图）',
    `coverImage`          varchar(512)          DEFAULT NULL COMMENT '封面图 URL',
    `images`              json                  DEFAULT NULL COMMENT '配图列表（JSON数组）',
    -- V5：配图降级统计（用于发现"静默降级"问题）
    `imageTotal`          int          NOT NULL DEFAULT '0' COMMENT '配图总数',
    `imageFallbackCount`  int          NOT NULL DEFAULT '0' COMMENT '其中降级到兜底图源的数量（PICSUM）',
    -- V6：降级严重程度（降级率 >= 30% 判为严重）
    `imageDegraded`       tinyint      NOT NULL DEFAULT '0' COMMENT '配图降级是否达告警级（降级率>=30%）：0-正常/轻微 1-严重',
    `status`              varchar(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/COMPLETED/FAILED',
    `phase`               varchar(50)           DEFAULT 'PENDING' COMMENT '当前阶段：PENDING/TITLE_GENERATING/TITLE_SELECTING/OUTLINE_GENERATING/OUTLINE_EDITING/CONTENT_GENERATING',
    `errorMessage`        text COMMENT '错误信息',
    `createTime`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `completedTime`       datetime              DEFAULT NULL COMMENT '完成时间',
    -- V2：配额退还幂等标志位
    `quotaRefunded`       tinyint      NOT NULL DEFAULT '0' COMMENT '配额是否已退还：0-未退还 1-已退还（幂等标志位）',
    `updateTime`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`            tinyint      NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_taskId` (`taskId`),
    KEY `idx_userId` (`userId`),
    KEY `idx_status` (`status`),
    KEY `idx_createTime` (`createTime`),
    KEY `idx_userId_status` (`userId`, `status`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='文章表';

-- -------------------------------------------------------------
-- 3. 支付记录表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `payment_record`
(
    `id`                    bigint         NOT NULL AUTO_INCREMENT COMMENT '主键',
    `userId`                bigint         NOT NULL COMMENT '用户ID',
    `stripeSessionId`       varchar(128)            DEFAULT NULL COMMENT 'Stripe Checkout Session ID',
    `stripePaymentIntentId` varchar(128)            DEFAULT NULL COMMENT 'Stripe 支付意向ID',
    `amount`                decimal(10, 2) NOT NULL COMMENT '金额（美元）',
    `currency`              varchar(8)              DEFAULT 'usd' COMMENT '货币',
    `status`                varchar(32)    NOT NULL COMMENT '状态：PENDING/SUCCEEDED/FAILED/REFUNDED',
    `productType`           varchar(32)    NOT NULL COMMENT '产品类型：VIP_PERMANENT',
    `description`           varchar(256)            DEFAULT NULL COMMENT '描述',
    `refundTime`            datetime                DEFAULT NULL COMMENT '退款时间',
    `refundReason`          varchar(512)            DEFAULT NULL COMMENT '退款原因',
    `createTime`            datetime                DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`            datetime                DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_userId` (`userId`),
    KEY `idx_stripeSessionId` (`stripeSessionId`),
    KEY `idx_status` (`status`),
    KEY `idx_createTime` (`createTime`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='支付记录表';

-- -------------------------------------------------------------
-- 4. 智能体执行日志表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_log`
(
    `id`               bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `taskId`           varchar(64) NOT NULL COMMENT '任务ID',
    `agentName`        varchar(50) NOT NULL COMMENT '智能体名称',
    -- V5：模型名，用于成本归因
    `model`            varchar(64)          DEFAULT NULL COMMENT '使用的模型名',
    `startTime`        datetime    NOT NULL COMMENT '开始时间',
    `endTime`          datetime             DEFAULT NULL COMMENT '结束时间',
    `durationMs`       int                  DEFAULT NULL COMMENT '耗时（毫秒）',
    -- V5：token 统计，用于成本估算
    `promptTokens`     int                  DEFAULT NULL COMMENT '输入 token 数',
    `completionTokens` int                  DEFAULT NULL COMMENT '输出 token 数',
    `totalTokens`      int                  DEFAULT NULL COMMENT '总 token 数（便于聚合与成本估算）',
    `status`           varchar(20) NOT NULL COMMENT '状态：SUCCESS/FAILED',
    `errorMessage`     text COMMENT '错误信息',
    `prompt`           text COMMENT '使用的Prompt',
    -- V3：提示词模板版本追踪，用于效果回收
    `templateId`       bigint               DEFAULT NULL COMMENT '使用的提示词模板ID',
    `templateVersion`  int                  DEFAULT NULL COMMENT '使用的提示词模板版本号',
    `inputData`        json                 DEFAULT NULL COMMENT '输入数据（JSON格式）',
    `outputData`       json                 DEFAULT NULL COMMENT '输出数据（JSON格式）',
    `createTime`       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`         tinyint     NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_taskId` (`taskId`),
    KEY `idx_agentName` (`agentName`),
    KEY `idx_status` (`status`),
    KEY `idx_createTime` (`createTime`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='智能体执行日志表';

-- -------------------------------------------------------------
-- 5. 提示词模板表（V3 新增）
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `prompt_template`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
    `name`        varchar(64)  NOT NULL COMMENT '模板名，对应原 PromptConstant 的常量名，如 AGENT1_TITLE_PROMPT',
    `version`     int          NOT NULL DEFAULT '1' COMMENT '版本号，同一 name 下递增',
    `agentName`   varchar(32)  NOT NULL COMMENT '所属智能体：TITLE/OUTLINE/CONTENT/IMAGE/STYLE/SVGD/OUTLINE_MODIFY',
    `description` varchar(256)          DEFAULT NULL COMMENT '用途说明',
    `content`     mediumtext   NOT NULL COMMENT '模板正文，用 {varName} 做占位符',
    `variables`   varchar(512)          DEFAULT NULL COMMENT '可用变量名，逗号分隔，如 topic,style',
    `enabled`     tinyint      NOT NULL DEFAULT '1' COMMENT '是否启用：1-启用 0-停用',
    `abWeight`    int          NOT NULL DEFAULT '100' COMMENT 'A/B 权重（0-100），同 name 下多版本按权重分配',
    `createTime`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`    tinyint      NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name_version` (`name`, `version`),
    KEY `idx_agentName` (`agentName`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_name_enabled` (`name`, `enabled`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='提示词模板表';

-- -------------------------------------------------------------
-- 6. 统计视图
-- -------------------------------------------------------------
-- 注意：视图依赖上面的表，必须在建表之后创建。
-- -------------------------------------------------------------

-- 按提示词模板版本聚合效果（V5）
CREATE OR REPLACE VIEW `v_prompt_effect` AS
SELECT
    COALESCE(`templateId`, 0)                AS `templateId`,
    COALESCE(`templateVersion`, 0)           AS `templateVersion`,
    `agentName`,
    COUNT(*)                                 AS `totalRuns`,
    SUM(`status` = 'SUCCESS')                AS `successRuns`,
    ROUND(100.0 * SUM(`status` = 'SUCCESS') / COUNT(*), 1) AS `successRate`,
    ROUND(AVG(`durationMs`))                 AS `avgDurationMs`,
    MAX(`durationMs`)                        AS `maxDurationMs`,
    ROUND(AVG(`totalTokens`))                AS `avgTotalTokens`
FROM `agent_log`
WHERE `isDelete` = 0
GROUP BY `templateId`, `templateVersion`, `agentName`;

-- 按配图降级率排序（V6）
CREATE OR REPLACE VIEW `v_image_degradation` AS
SELECT
    `taskId`,
    `mainTitle`,
    `imageTotal`,
    `imageFallbackCount`,
    CASE WHEN `imageTotal` = 0 THEN 0
         ELSE ROUND(100.0 * `imageFallbackCount` / `imageTotal`, 1)
    END AS `fallbackRate`,
    `imageDegraded`,
    `status`,
    `createTime`
FROM `article`
WHERE `isDelete` = 0
  AND `imageTotal` > 0
ORDER BY `fallbackRate` DESC;

-- -------------------------------------------------------------
-- 7. 提示词初始数据
-- -------------------------------------------------------------
-- 11 个模板的初始化数据在 db/upgrade/004-prompt-seed.sql。
-- 因为内容较长（约 300 行、14KB），不并入本文件，避免本文件过于臃肿。
--
-- 新环境初始化顺序：
--   1. mysql -uroot -p < init.sql                       （建库建表）
--   2. mysql -uroot -p lilac-passage-creator \
--        -e "source db/upgrade/004-prompt-seed.sql"      （灌入提示词）
--
-- 若未执行第 2 步：应用仍能正常运行 ——
-- PromptTemplateService 查不到模板时会自动回退到
-- PromptConstant 中的硬编码兜底。这是刻意的降级设计。
-- -------------------------------------------------------------
