-- =============================================================
-- 增量脚本 003 · 新增 prompt_template 表（步骤 D9/D10）
-- =============================================================
-- 目的：把 PromptConstant 里 319 行的硬编码提示词迁到数据库，
--       支持版本管理、热更新、A/B 测试与效果回收。
--
-- 使用方式：
--   mysql -uroot -p lilac-passage-creator < db/upgrade/003-prompt-template.sql
--
-- 本脚本可重复执行。
-- =============================================================

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
-- agent_log 增加模板版本追踪（用于效果回收）
-- -------------------------------------------------------------
-- 原 agent_log.prompt 存的是 "类名.方法名"，无法按模板版本聚合。
-- 加两列后可以统计：某模板版本的成功率 / 平均耗时 / 采用率。
-- -------------------------------------------------------------

SET @col1 := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_log'
                AND COLUMN_NAME = 'templateId');
SET @ddl1 := IF(@col1 = 0,
    'ALTER TABLE `agent_log` ADD COLUMN `templateId` bigint DEFAULT NULL COMMENT ''使用的提示词模板ID'' AFTER `prompt`',
    'SELECT ''column templateId already exists, skipped'' AS msg');
PREPARE s1 FROM @ddl1;
EXECUTE s1;
DEALLOCATE PREPARE s1;

SET @col2 := (SELECT COUNT(*) FROM information_schema.COLUMNS
              WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'agent_log'
                AND COLUMN_NAME = 'templateVersion');
SET @ddl2 := IF(@col2 = 0,
    'ALTER TABLE `agent_log` ADD COLUMN `templateVersion` int DEFAULT NULL COMMENT ''使用的提示词模板版本号'' AFTER `templateId`',
    'SELECT ''column templateVersion already exists, skipped'' AS msg');
PREPARE s2 FROM @ddl2;
EXECUTE s2;
DEALLOCATE PREPARE s2;
