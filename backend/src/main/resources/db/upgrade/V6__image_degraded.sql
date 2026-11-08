-- =============================================================
-- V6 · 配图降级可见化（步骤 E2）
-- =============================================================
-- 背景（承接 V5 的数据发现）：
--   实测 34 张配图有 5 张（14.7%）降级到 PICSUM 占位图，
--   最严重一篇 10 张降级 5 张且封面也降级，
--   但 article.status 仍是「已完成」、errorMessage 为 NULL，
--   用户在页面上完全无法察觉。
--
-- 本脚本补充「降级严重程度」字段，让前端可以显式提示用户。
--
-- 设计说明（重要）：
--   为什么新增 imageDegraded 而不是新增 status='PARTIAL'？
--   前端有多处 `status === '已完成'` 的分支判断
--   （ArticleCreate.vue:720 会话恢复、ArticleList.vue:134 标签映射），
--   新增状态值会让这些逻辑走错分支 —— 恢复会话时会把已完成的任务
--   当成"仍在生成"。所以保持 status 语义不变，用独立字段表达降级程度。
-- =============================================================

SET @c := (SELECT COUNT(*) FROM information_schema.COLUMNS
           WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='article' AND COLUMN_NAME='imageDegraded');
SET @d := IF(@c=0,
  'ALTER TABLE `article` ADD COLUMN `imageDegraded` tinyint NOT NULL DEFAULT 0 COMMENT ''配图降级是否达告警级（降级率>=30%%）：0-正常/轻微 1-严重'' AFTER `imageFallbackCount`',
  'SELECT ''imageDegraded exists'' AS msg');
PREPARE s FROM @d; EXECUTE s; DEALLOCATE PREPARE s;

-- -------------------------------------------------------------
-- 回填历史数据
-- -------------------------------------------------------------
-- 按 30% 阈值标注已有的 7 篇。
-- 实测：368efa8a 那篇 10 张降级 5 张 = 50%，应标为 1（严重）。
-- -------------------------------------------------------------

UPDATE `article`
SET `imageDegraded` = CASE
    WHEN `imageTotal` > 0
         AND (`imageFallbackCount` * 100 / `imageTotal`) >= 30
    THEN 1
    ELSE 0
END;

-- -------------------------------------------------------------
-- 便于排查的视图：按降级率排序
-- -------------------------------------------------------------

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
