-- =============================================================
-- lilac-passage-creator 建表脚本
-- =============================================================
-- 数据来源：2026-10-26 从本地 MySQL 8.0 实库执行
--           mysqldump --no-data 导出，去掉 AUTO_INCREMENT 计数器
--           与 /*! */ 版本注释、SET NAMES 等机器相关语句后整理而成。
--
-- 使用方式：
--   mysql -uroot -p < backend/src/main/resources/db/init.sql
--   或在 Navicat 中打开本文件直接执行。
--
-- 注意：
--   1. 实体类全部标注 @Table(camelToUnderline = false)，
--      并配合 map-underscore-to-camel-case: false，
--      因此列名就是驼峰原样（taskId 而非 task_id），
--      手写 SQL 时字段名必须带反引号或原样驼峰。
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
    `status`              varchar(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING/PROCESSING/COMPLETED/FAILED',
    `phase`               varchar(50)           DEFAULT 'PENDING' COMMENT '当前阶段：PENDING/TITLE_GENERATING/TITLE_SELECTING/OUTLINE_GENERATING/OUTLINE_EDITING/CONTENT_GENERATING',
    `errorMessage`        text COMMENT '错误信息',
    `createTime`          datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `completedTime`       datetime              DEFAULT NULL COMMENT '完成时间',
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
    `id`           bigint      NOT NULL AUTO_INCREMENT COMMENT 'id',
    `taskId`       varchar(64) NOT NULL COMMENT '任务ID',
    `agentName`    varchar(50) NOT NULL COMMENT '智能体名称',
    `startTime`    datetime    NOT NULL COMMENT '开始时间',
    `endTime`      datetime             DEFAULT NULL COMMENT '结束时间',
    `durationMs`   int                  DEFAULT NULL COMMENT '耗时（毫秒）',
    `status`       varchar(20) NOT NULL COMMENT '状态：SUCCESS/FAILED',
    `errorMessage` text COMMENT '错误信息',
    `prompt`       text COMMENT '使用的Prompt',
    `inputData`    json                 DEFAULT NULL COMMENT '输入数据（JSON格式）',
    `outputData`   json                 DEFAULT NULL COMMENT '输出数据（JSON格式）',
    `createTime`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updateTime`   datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `isDelete`     tinyint     NOT NULL DEFAULT '0' COMMENT '是否删除',
    PRIMARY KEY (`id`),
    KEY `idx_taskId` (`taskId`),
    KEY `idx_agentName` (`agentName`),
    KEY `idx_status` (`status`),
    KEY `idx_createTime` (`createTime`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT ='智能体执行日志表';
