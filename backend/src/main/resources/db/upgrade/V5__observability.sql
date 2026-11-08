-- =============================================================
-- V5 · 补充可观测性埋点字段（步骤 E1）
-- =============================================================
-- 背景：对 7 篇真实文章做效果分析时发现两个数据缺口：
--   1. agent_log 没有 token 数、模型名 —— 无法统计成本
--   2. article 没有降级图计数 —— 无法量化"配图静默降级"的影响
--      （实测 34 张图有 5 张降级，占 14.7%，但 status 仍为「已完成」，
--        用户完全无法感知）
--
-- 本脚本补充上述字段，并新增一张聚合统计表。
-- 可重复执行。
-- =============================================================

-- -------------------------------------------------------------
-- 1. agent_log 增加 token 与模型信息
-- -------------------------------------------------------------

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='agent_log' AND COLUMN_NAME='model');
SET @d := IF(@c=0,
  'ALTER TABLE `agent_log` ADD COLUMN `model` varchar(64) DEFAULT NULL COMMENT ''使用的模型名'' AFTER `agentName`',
  'SELECT ''model exists'' AS msg');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='agent_log' AND COLUMN_NAME='promptTokens');
SET @d := IF(@c=0,
  'ALTER TABLE `agent_log` ADD COLUMN `promptTokens` int DEFAULT NULL COMMENT ''输入 token 数'' AFTER `durationMs`',
  'SELECT ''promptTokens exists'' AS msg');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='agent_log' AND COLUMN_NAME='completionTokens');
SET @d := IF(@c=0,
  'ALTER TABLE `agent_log` ADD COLUMN `completionTokens` int DEFAULT NULL COMMENT ''输出 token 数'' AFTER `promptTokens`',
  'SELECT ''completionTokens exists'' AS msg');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='agent_log' AND COLUMN_NAME='totalTokens');
SET @d := IF(@c=0,
  'ALTER TABLE `agent_log` ADD COLUMN `totalTokens` int DEFAULT NULL COMMENT ''总 token 数（便于聚合与成本估算）'' AFTER `completionTokens`',
  'SELECT ''totalTokens exists'' AS msg');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- -------------------------------------------------------------
-- 2. article 增加配图降级统计
-- -------------------------------------------------------------

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='article' AND COLUMN_NAME='imageTotal');
SET @d := IF(@c=0,
  'ALTER TABLE `article` ADD COLUMN `imageTotal` int NOT NULL DEFAULT 0 COMMENT ''配图总数'' AFTER `images`',
  'SELECT ''imageTotal exists'' AS msg');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='article' AND COLUMN_NAME='imageFallbackCount');
SET @d := IF(@c=0,
  'ALTER TABLE `article` ADD COLUMN `imageFallbackCount` int NOT NULL DEFAULT 0 COMMENT ''其中降级到兜底图源的数量（PICSUM）'' AFTER `imageTotal`',
  'SELECT ''imageFallbackCount exists'' AS msg');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- -------------------------------------------------------------
-- 3. 回填历史数据（把已有的 7 篇文章算出来）
-- -------------------------------------------------------------
-- 这一步让历史数据也能参与统计，同时验证字段可用。
-- -------------------------------------------------------------

UPDATE `article` a
SET a.`imageTotal` = IFNULL(JSON_LENGTH(a.`images`), 0)
WHERE a.`images` IS NOT NULL;

UPDATE `article` a
SET a.`imageFallbackCount` = (
    SELECT COUNT(*)
    FROM JSON_TABLE(a.`images`, '$[*]' COLUMNS(m VARCHAR(32) PATH '$.method')) jt
    WHERE UPPER(jt.m) = 'PICSUM'
)
WHERE a.`images` IS NOT NULL;

-- -------------------------------------------------------------
-- 4. 效果回收：按模板版本聚合的视图
-- -------------------------------------------------------------
-- 把 agent_log 按 templateVersion 聚合，直接得到「某版本 prompt 的
-- 成功率 / 平均耗时」——这是 prompt 版本管理的最终目的。
-- -------------------------------------------------------------

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
