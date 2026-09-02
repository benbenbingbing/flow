-- Expand 阶段：用虚拟列把数字流程 ID 别名（如 01/1）投影为同一 BIGINT，
-- 旧版本继续写别名时索引也会即时更新。本次只建普通索引；唯一约束必须
-- 等所有旧版本写者退出后，在后续 contract 迁移中单独建立。
ALTER TABLE `entity_definition`
  ADD COLUMN `active_process_definition_key` BIGINT
    GENERATED ALWAYS AS (
      CASE
        WHEN COALESCE(`deleted`, 0) = 0
          AND TRIM(`process_definition_id`) REGEXP '^[0-9]+$'
          AND NULLIF(TRIM(LEADING '0' FROM
            TRIM(`process_definition_id`)), '') IS NOT NULL
          AND (
            CHAR_LENGTH(TRIM(LEADING '0' FROM
              TRIM(`process_definition_id`))) < 19
            OR (
              CHAR_LENGTH(TRIM(LEADING '0' FROM
                TRIM(`process_definition_id`))) = 19
              AND TRIM(LEADING '0' FROM
                TRIM(`process_definition_id`)) <= '9223372036854775807'
            )
          )
        THEN CAST(TRIM(LEADING '0' FROM
          TRIM(`process_definition_id`)) AS UNSIGNED)
        ELSE NULL
      END
    ) VIRTUAL,
  ADD KEY `idx_entity_definition_process_binding`
    (`active_process_definition_key`);
