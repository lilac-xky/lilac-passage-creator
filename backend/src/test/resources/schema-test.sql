-- =============================================================
-- 测试专用 schema（步骤 G1）
-- =============================================================
-- ⚠️ 本文件由 `scripts/gen-test-schema.mjs` 从 db/init.sql 生成，
--    **请勿手工编辑**。改了 init.sql 后请重新运行生成器。
--
-- 与 init.sql 的**唯一差异**（其余逐字一致）：
--   1. 去掉 CREATE DATABASE / USE —— H2 不支持
--   2. 去掉 ENGINE= / DEFAULT CHARSET= / COLLATE= —— 无需（H2 虽能接受，
--      但保留会让与 MySQL 的差异更难看出，这里一并去掉以便对照）
--   3. 索引名加表前缀 —— H2 的索引名在库内全局唯一，
--      而 MySQL 是表内唯一。init.sql 里多个表都叫 idx_userId/idx_status，
--      H2 会报 "Index already exists"。
--
-- 为什么这样做而不是直接让 Spring 加载 init.sql：
--   实测发现 init.sql 有 5 条语句 H2 无法执行（见 docs/changes/19-test-profile.md）。
--   直接加载会在启动阶段失败，且错误信息不明显。
--
-- 为什么不用 Testcontainers 跑真 MySQL：
--   - 需要 Docker；CI 虽是 ubuntu-latest 可用，但起容器约 20~30s
--   - 本项目 SQL 全是标准语法，H2 MySQL 模式足够
--   - 主要目的是**验证实体与建表脚本一致**，不需要 MySQL 特有行为
--
-- ⚠️ 双份维护的代价：
--   本文件与 init.sql 是两份独立的 DDL。为降低漂移风险，
--   测试里有一条 TestSchemaConsistencyTest 会比对两者的「表/列清单」，
--   不一致就失败。
-- =============================================================

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
    UNIQUE KEY `user_uk_userAccount` (`userAccount`),
    KEY `user_idx_userName` (`userName`)
);

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
    `imageTotal`          int          NOT NULL DEFAULT '0' COMMENT '配图总数',
    `imageFallbackCount`  int          NOT NULL DEFAULT '0' COMMENT '其中降级到兜底图源的数量（PICSUM）',
    `imageDegraded`       tinyint      NOT NULL DEFAULT '0' COMMENT '配图降级是否达告警级（降级率>=30%）：0-正常/轻微 1-严重',
    `status`              varchar(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/COMPLETED/FAILED',
    `phase`               varchar(50)           DEFAULT 'PENDING' COMMENT '当前阶段',
    `errorMessage`        text COMMENT '错误信息',
    `createTime`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `completedTime`       datetime              DEFAULT NULL COMMENT '完成时间',
    `quotaRefunded`       tinyint      NOT NULL DEFAULT '0' COMMENT '配额是否已退还：0-未退还 1-已退还（幂等标志位）',
    `updateTime`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`            tinyint      NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `article_uk_taskId` (`taskId`),
    KEY `article_idx_userId` (`userId`),
    KEY `article_idx_status` (`status`),
    KEY `article_idx_createTime` (`createTime`),
    KEY `article_idx_userId_status` (`userId`, `status`)
);

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
    KEY `payment_idx_userId` (`userId`),
    KEY `payment_idx_stripeSessionId` (`stripeSessionId`),
    KEY `payment_idx_status` (`status`),
    KEY `payment_idx_createTime` (`createTime`)
);

-- -------------------------------------------------------------
-- 4. 智能体执行日志表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `agent_log`
(
    `id`               bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `taskId`           varchar(64) NOT NULL COMMENT '任务ID',
    `agentName`        varchar(50) NOT NULL COMMENT '智能体名称',
    `model`            varchar(64)          DEFAULT NULL COMMENT '使用的模型名',
    `startTime`        datetime    NOT NULL COMMENT '开始时间',
    `endTime`          datetime             DEFAULT NULL COMMENT '结束时间',
    `durationMs`       int                  DEFAULT NULL COMMENT '耗时（毫秒）',
    `promptTokens`     int                  DEFAULT NULL COMMENT '输入 token 数',
    `completionTokens` int                  DEFAULT NULL COMMENT '输出 token 数',
    `totalTokens`      int                  DEFAULT NULL COMMENT '总 token 数',
    `status`           varchar(20) NOT NULL COMMENT '状态：SUCCESS/FAILED',
    `errorMessage`     text COMMENT '错误信息',
    `prompt`           text COMMENT '使用的Prompt',
    `templateId`       bigint               DEFAULT NULL COMMENT '使用的提示词模板ID',
    `templateVersion`  int                  DEFAULT NULL COMMENT '使用的提示词模板版本号',
    `inputData`        json                 DEFAULT NULL COMMENT '输入数据（JSON格式）',
    `outputData`       json                 DEFAULT NULL COMMENT '输出数据（JSON格式）',
    `createTime`       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`       datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`         tinyint     NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `log_idx_taskId` (`taskId`),
    KEY `log_idx_agentName` (`agentName`),
    KEY `log_idx_status` (`status`),
    KEY `log_idx_createTime` (`createTime`)
);

-- -------------------------------------------------------------
-- 5. 提示词模板表
-- -------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `prompt_template`
(
    `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT 'id',
    `name`        varchar(64)  NOT NULL COMMENT '模板名',
    `version`     int          NOT NULL DEFAULT '1' COMMENT '版本号',
    `agentName`   varchar(32)  NOT NULL COMMENT '所属智能体',
    `description` varchar(256)          DEFAULT NULL COMMENT '用途说明',
    `content`     mediumtext   NOT NULL COMMENT '模板正文',
    `variables`   varchar(512)          DEFAULT NULL COMMENT '可用变量名',
    `enabled`     tinyint      NOT NULL DEFAULT '1' COMMENT '是否启用',
    `abWeight`    int          NOT NULL DEFAULT '100' COMMENT 'A/B 权重',
    `createTime`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`  datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`    tinyint      NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    UNIQUE KEY `prompt_uk_name_version` (`name`, `version`),
    KEY `prompt_idx_agentName` (`agentName`),
    KEY `prompt_idx_enabled` (`enabled`),
    KEY `prompt_idx_name_enabled` (`name`, `enabled`)
);

-- -------------------------------------------------------------
-- 6. 统计视图
-- -------------------------------------------------------------

CREATE OR REPLACE VIEW `v_prompt_effect` AS
SELECT
    COALESCE(`templateId`, 0)                AS `templateId`,
    COALESCE(`templateVersion`, 0)           AS `templateVersion`,
    `agentName`,
    COUNT(*)                                 AS `totalRuns`,
    SUM(CASE WHEN `status` = 'SUCCESS' THEN 1 ELSE 0 END) AS `successRuns`,
    ROUND(100.0 * SUM(CASE WHEN `status` = 'SUCCESS' THEN 1 ELSE 0 END) / COUNT(*), 1) AS `successRate`,
    ROUND(AVG(`durationMs`))                 AS `avgDurationMs`,
    MAX(`durationMs`)                        AS `maxDurationMs`,
    ROUND(AVG(`totalTokens`))                AS `avgTotalTokens`
FROM `agent_log`
WHERE `isDelete` = 0
GROUP BY `templateId`, `templateVersion`, `agentName`;

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
  AND `imageTotal` > 0;
