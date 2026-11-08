-- =============================================================
-- 增量迁移脚本
-- =============================================================
-- 本项目未使用 Flyway，改用「init.sql 全量 + upgrade/ 增量」的约定。
--
-- 使用方式：
--   mysql -uroot -p lilac-passage-creator < db/upgrade/V2__quota_refund.sql
--
-- 约定：
--   1. 文件名格式 V{序号}__{描述}.sql，序号递增
--   2. 每个脚本只做一件事，且必须可重复执行（用 IF NOT EXISTS / 先查后改）
--   3. 新环境直接跑 init.sql 即可（init.sql 已包含全部字段），
--      已有环境才需要按顺序跑 upgrade/
--   4. 执行记录建议手工维护在 docs/changes/ 对应文档里
-- =============================================================

-- -------------------------------------------------------------
-- V2 · 文章表增加配额退还标志位（步骤 C4）
-- -------------------------------------------------------------
-- 作用：作为「退还配额」的幂等标志位。
--   任务失败时先 CAS 抢占该标志位，只有抢到的调用才真正退还配额，
--   避免重试 / 多实例 / 重复回调导致用户凭空多出配额。
--
-- MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，
-- 故用 information_schema 判断后再执行，保证脚本可重复运行。
-- -------------------------------------------------------------

SET @col_exists := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'article'
      AND COLUMN_NAME = 'quotaRefunded'
);

SET @ddl := IF(@col_exists = 0,
    'ALTER TABLE `article` ADD COLUMN `quotaRefunded` tinyint NOT NULL DEFAULT 0 COMMENT ''配额是否已退还：0-未退还 1-已退还（幂等标志位）'' AFTER `completedTime`',
    'SELECT ''column quotaRefunded already exists, skipped'' AS msg'
);

PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
