-- 数据版本配置收敛为每个实体一份当前配置，保存后立即生效。
-- 本迁移只做滚动发布安全的 expand：旧 Pod 仍需使用的发布表和旧列继续保留。
-- 新 Pod 在过渡期通过应用查询兼容 active release，不创建需要 SUPER 权限的数据库触发器。
-- 后续版本须先停止兼容读写并完成全量滚动，再由更晚的 contract 迁移删除旧结构。
-- 已发布配置优先作为迁移后的当前配置，避免未发布草稿在升级时被意外启用；
-- 没有有效发布快照的配置保留编辑内容，但统一转为停用。
-- MySQL DDL 非事务执行：若旧版 V082 曾在后续语句失败，列可能已经存在。
-- 这里先检查结构再动态执行 ALTER，使修复失败历史后可以安全重跑。
SET @flow_v082_config_document_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'entity_version_config'
      AND column_name = 'config_document'
);
SET @flow_v082_add_config_document_sql = IF(
    @flow_v082_config_document_exists = 0,
    'ALTER TABLE `entity_version_config` ADD COLUMN `config_document` longtext COLLATE utf8mb4_unicode_ci NULL COMMENT ''当前生效的数据版本配置JSON'' AFTER `draft_document`',
    'SELECT 1'
);
PREPARE flow_v082_add_config_document
    FROM @flow_v082_add_config_document_sql;
EXECUTE flow_v082_add_config_document;
DEALLOCATE PREPARE flow_v082_add_config_document;

UPDATE `entity_version_config` c
LEFT JOIN `entity_version_config_release` r
       ON r.`id` = c.`active_release_id`
      AND r.`config_id` = c.`id`
SET c.`config_document` = CASE
        -- 旧运行时以发布表 contract_version 为准；写回 JSON 后新旧 Pod 语义一致。
        WHEN r.`id` IS NOT NULL
         AND JSON_VALID(r.`config_document`) = 1
            THEN JSON_REMOVE(
                JSON_SET(
                    CAST(r.`config_document` AS JSON),
                    '$.schemaVersion',
                    COALESCE(r.`contract_version`, 1)),
                '$.status',
                '$.migrationState',
                '$.activeReleaseId',
                '$.activeReleaseVersion')
        WHEN JSON_VALID(c.`draft_document`) = 1
            THEN JSON_REMOVE(
                JSON_SET(
                    CAST(c.`draft_document` AS JSON),
                    '$.schemaVersion',
                    COALESCE(c.`contract_version`, 2),
                    '$.enabled',
                    CAST('false' AS JSON)),
                '$.status',
                '$.migrationState',
                '$.activeReleaseId',
                '$.activeReleaseVersion')
        ELSE '{"schemaVersion":2,"enabled":false,"triggers":[],"snapshotScope":{"relations":[]},"diffPolicy":{}}'
    END,
    c.`enabled` = CASE
        WHEN r.`id` IS NOT NULL
         AND JSON_VALID(r.`config_document`) = 1
         AND JSON_UNQUOTE(JSON_EXTRACT(r.`config_document`, '$.enabled')) = 'true'
            THEN 1
        ELSE 0
    END,
    c.`revision` = c.`revision` + 1,
    c.`update_time` = CURRENT_TIMESTAMP
WHERE c.`deleted` = 0
  AND c.`config_document` IS NULL;

-- MySQL DDL 失败后应用可能仍有旧 Pod 存活。它们只会切换 active release，
-- 因此恢复重跑时再做一次最终投影，覆盖首次回填后发生的旧版发布。
-- 这是同一逻辑 revision 的存储对账，不得再次增加 revision。
UPDATE `entity_version_config` c
JOIN `entity_version_config_release` r
  ON r.`id` = c.`active_release_id`
 AND r.`config_id` = c.`id`
SET c.`config_document` = JSON_REMOVE(
        JSON_SET(
            CAST(r.`config_document` AS JSON),
            '$.schemaVersion',
            COALESCE(r.`contract_version`, 1)),
        '$.status',
        '$.migrationState',
        '$.activeReleaseId',
        '$.activeReleaseVersion'),
    c.`enabled` = CASE
        WHEN JSON_UNQUOTE(JSON_EXTRACT(
                r.`config_document`, '$.enabled')) = 'true'
            THEN 1
        ELSE 0
    END
WHERE c.`deleted` = 0
  AND JSON_VALID(r.`config_document`) = 1;

-- 软删除配置不参与运行，只补齐安全文档。列在 expand 阶段保持可空，
-- 以兼容旧 Pod 在滚动窗口内创建尚未发布的配置行。
UPDATE `entity_version_config`
SET `config_document` = CASE
        WHEN JSON_VALID(`draft_document`) = 1
            THEN JSON_REMOVE(
                JSON_SET(
                    CAST(`draft_document` AS JSON),
                    '$.schemaVersion',
                    COALESCE(`contract_version`, 1),
                    '$.enabled',
                    CAST('false' AS JSON)),
                '$.status',
                '$.migrationState',
                '$.activeReleaseId',
                '$.activeReleaseVersion')
        ELSE '{"schemaVersion":2,"enabled":false,"triggers":[],"snapshotScope":{"relations":[]},"diffPolicy":{}}'
    END
WHERE `config_document` IS NULL;
