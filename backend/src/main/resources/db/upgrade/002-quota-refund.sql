-- =============================================================
-- 增量脚本 002 · 文章表增加配额退还标志位
-- =============================================================
-- 本项目**不使用 Flyway**，采用「init.sql 全量 + upgrade/ 增量」的手工约定。
-- 文件名刻意不用 Flyway 的 V{版本}__{描述}.sql 格式，避免误以为有自动迁移。
--
-- 使用方式：
--   mysql -uroot -p lilac-passage-creator < db/upgrade/002-quota-refund.sql
--
-- 约定：
--   1. 文件名格式 0NN-{描述}.sql，序号递增（三位数便于排序）
--   2. 每个脚本只做一件事，且必须可重复执行
--      （用 IF NOT EXISTS / 先查 information_schema 再改）
--   3. 新环境只跑 init.sql 即可（已含全部字段）；
--      已有旧库才需要按序号依次跑 upgrade/
--   4. 执行记录手工维护在 docs/changes/ 对应文档里
--   5. **没有自动迁移机制**，改表后记得同步更新 init.sql
-- =============================================================

-- -------------------------------------------------------------
-- 002 · 文章表增加配额退还标志位（步骤 C4）
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
