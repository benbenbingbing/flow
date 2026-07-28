-- =====================================================================
-- 自定义实体与流程测试数据清理脚本（待确认）
-- 适用基线：workflow-server/workflow-app/.../V001__business_schema.sql
-- 数据库：MySQL 8.0+
-- 编写日期：2026-07-27
--
-- 重要：
-- 1. 本文件不是 Flyway 迁移，不要放入 db/migration。
-- 2. 默认 PREVIEW，只展示候选数据，不执行删除。
-- 3. 正式执行前必须停止 workflow-server，避免异步任务和 Outbox 继续写入。
-- 4. 正式执行前必须完成整库备份。动态业务表 DROP 属于 DDL，不能随事务回滚。
-- 5. 本脚本不会删除上传目录中的物理文件。
--
-- 建议备份命令（按实际连接参数调整）：
-- mysqldump --single-transaction --routines --triggers workflow \
--   > workflow_before_custom_data_cleanup_20260727.sql
--
-- 正式执行时，仅修改下面两项：
--   SET @cleanup_mode = 'EXECUTE';
--   SET @cleanup_confirm = 'DELETE_CUSTOM_ENTITY_PROCESS_DATA';
-- =====================================================================

SET NAMES utf8mb4;

SET @cleanup_mode = 'PREVIEW';
SET @cleanup_confirm = 'NOT_CONFIRMED';

-- 保留并在脚本结束时恢复当前会话设置。
SET @cleanup_old_foreign_key_checks = @@SESSION.FOREIGN_KEY_CHECKS;
SET @cleanup_old_sql_safe_updates = @@SESSION.SQL_SAFE_UPDATES;

-- =====================================================================
-- 一、候选数据快照
-- =====================================================================

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_entity;
CREATE TEMPORARY TABLE tmp_cleanup_entity (
    id BIGINT NOT NULL,
    entity_code VARCHAR(100) NOT NULL,
    entity_name VARCHAR(200) NOT NULL,
    table_name VARCHAR(100) NULL,
    lifecycle_mode VARCHAR(20) NULL,
    status VARCHAR(20) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tmp_cleanup_entity_code (entity_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 当前基线没有 entity_definition.is_system。
-- storage_mode='SYSTEM' 是系统实体的正式判定条件，其余均视为自定义实体。
INSERT INTO tmp_cleanup_entity (
    id,
    entity_code,
    entity_name,
    table_name,
    lifecycle_mode,
    status
)
SELECT
    id,
    entity_code,
    entity_name,
    table_name,
    lifecycle_mode,
    status
FROM entity_definition
WHERE COALESCE(storage_mode, 'DYNAMIC') <> 'SYSTEM';

-- 收集全部自定义实体物理表：
-- 1. 已登记实体的主表、_multi 多值表、_team 参与团队表；
-- 2. 历史遗留、已经失去 entity_definition 记录的孤立 biz_ 表。
-- SYSTEM 实体登记的物理表及其派生表始终排除。
DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_physical_table;
CREATE TEMPORARY TABLE tmp_cleanup_physical_table (
    table_name VARCHAR(64) NOT NULL,
    cleanup_reason VARCHAR(30) NOT NULL,
    PRIMARY KEY (table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_cleanup_physical_table (table_name, cleanup_reason)
SELECT
    CONVERT(t.table_name USING utf8mb4),
    CASE
        WHEN EXISTS (
            SELECT 1
            FROM entity_definition e
            WHERE CONVERT(t.table_name USING utf8mb4)
                  COLLATE utf8mb4_unicode_ci
                  = e.table_name COLLATE utf8mb4_unicode_ci
              AND COALESCE(e.storage_mode, 'DYNAMIC') <> 'SYSTEM'
        ) THEN 'ENTITY_MAIN'
        WHEN EXISTS (
            SELECT 1
            FROM entity_definition e
            WHERE CONVERT(t.table_name USING utf8mb4)
                  COLLATE utf8mb4_unicode_ci
                  = CONCAT(e.table_name, '_multi')
                    COLLATE utf8mb4_unicode_ci
              AND COALESCE(e.storage_mode, 'DYNAMIC') <> 'SYSTEM'
        ) THEN 'ENTITY_MULTI'
        WHEN EXISTS (
            SELECT 1
            FROM entity_definition e
            WHERE CONVERT(t.table_name USING utf8mb4)
                  COLLATE utf8mb4_unicode_ci
                  = CONCAT(e.table_name, '_team')
                    COLLATE utf8mb4_unicode_ci
              AND COALESCE(e.storage_mode, 'DYNAMIC') <> 'SYSTEM'
        ) THEN 'ENTITY_TEAM'
        ELSE 'ORPHAN_BIZ_TABLE'
    END
FROM information_schema.tables t
WHERE t.table_schema = DATABASE()
  AND LEFT(t.table_name, 4) = 'biz_'
  AND NOT EXISTS (
      SELECT 1
      FROM entity_definition system_entity
      WHERE system_entity.storage_mode = 'SYSTEM'
        AND (
            CONVERT(t.table_name USING utf8mb4)
                COLLATE utf8mb4_unicode_ci
                = system_entity.table_name COLLATE utf8mb4_unicode_ci
            OR CONVERT(t.table_name USING utf8mb4)
                COLLATE utf8mb4_unicode_ci
                = CONCAT(system_entity.table_name, '_multi')
                  COLLATE utf8mb4_unicode_ci
            OR CONVERT(t.table_name USING utf8mb4)
                COLLATE utf8mb4_unicode_ci
                = CONCAT(system_entity.table_name, '_team')
                  COLLATE utf8mb4_unicode_ci
        )
  );

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_entity_field;
CREATE TEMPORARY TABLE tmp_cleanup_entity_field (
    id VARCHAR(64) NOT NULL,
    entity_id BIGINT NOT NULL,
    dict_type VARCHAR(100) NULL,
    PRIMARY KEY (id),
    KEY idx_tmp_cleanup_entity_field_entity (entity_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_cleanup_entity_field (id, entity_id, dict_type)
SELECT CAST(f.id AS CHAR), f.entity_id, NULLIF(TRIM(f.dict_type), '')
FROM entity_field f
JOIN tmp_cleanup_entity e ON e.id = f.entity_id;

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_entity_form;
CREATE TEMPORARY TABLE tmp_cleanup_entity_form (
    id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_cleanup_entity_form (id)
SELECT f.id
FROM entity_form f
JOIN tmp_cleanup_entity e
  ON CAST(e.id AS CHAR) COLLATE utf8mb4_unicode_ci
     = f.entity_id COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_entity_list;
CREATE TEMPORARY TABLE tmp_cleanup_entity_list (
    id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_cleanup_entity_list (id)
SELECT l.id
FROM entity_list_config l
JOIN tmp_cleanup_entity e
  ON CAST(e.id AS CHAR) COLLATE utf8mb4_unicode_ci
     = l.entity_id COLLATE utf8mb4_unicode_ci
  OR e.entity_code
     = l.entity_code COLLATE utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_process;
CREATE TEMPORARY TABLE tmp_cleanup_process (
    id BIGINT NOT NULL,
    process_key VARCHAR(100) NOT NULL,
    process_name VARCHAR(200) NOT NULL,
    status VARCHAR(20) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tmp_cleanup_process_key (process_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 当前业务基线没有内置流程定义，process_definition_config 中的数据均为用户配置。
INSERT INTO tmp_cleanup_process (id, process_key, process_name, status)
SELECT id, process_key, process_name, status
FROM process_definition_config;

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_process_version;
CREATE TEMPORARY TABLE tmp_cleanup_process_version (
    id VARCHAR(64) NOT NULL,
    deployment_id VARCHAR(100) NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_cleanup_process_version (id, deployment_id)
SELECT CAST(id AS CHAR), deployment_id
FROM process_version_history;

-- 收集自定义实体菜单、实体权限菜单、流程启动菜单以及它们的全部子菜单。
DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_menu;
CREATE TEMPORARY TABLE tmp_cleanup_menu (
    id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_menu_next;
CREATE TEMPORARY TABLE tmp_cleanup_menu_next (
    id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS cleanup_collect_menu_candidates_20260727;
DELIMITER $$
CREATE PROCEDURE cleanup_collect_menu_candidates_20260727()
BEGIN
    DECLARE affected_rows INT DEFAULT 1;

    INSERT IGNORE INTO tmp_cleanup_menu (id)
    SELECT DISTINCT m.id
    FROM sys_menu m
    WHERE m.path = '/__entity_permissions__'
       OR EXISTS (
            SELECT 1
            FROM tmp_cleanup_entity e
            WHERE e.entity_code
                  = m.entity_code COLLATE utf8mb4_unicode_ci
       )
       OR EXISTS (
            SELECT 1
            FROM tmp_cleanup_process p
            WHERE CHAR_LENGTH(p.process_key) >= 4
              AND (
                  LOCATE(p.process_key, COALESCE(m.path, '')) > 0
                  OR LOCATE(p.process_key, COALESCE(m.query, '')) > 0
              )
       );

    WHILE affected_rows > 0 DO
        TRUNCATE TABLE tmp_cleanup_menu_next;

        INSERT IGNORE INTO tmp_cleanup_menu_next (id)
        SELECT child.id
        FROM sys_menu child
        JOIN tmp_cleanup_menu parent_candidate
          ON parent_candidate.id
             = child.parent_id COLLATE utf8mb4_unicode_ci;

        INSERT IGNORE INTO tmp_cleanup_menu (id)
        SELECT id
        FROM tmp_cleanup_menu_next;

        SET affected_rows = ROW_COUNT();
    END WHILE;
END$$
DELIMITER ;

CALL cleanup_collect_menu_candidates_20260727();
DROP PROCEDURE cleanup_collect_menu_candidates_20260727;
DROP TEMPORARY TABLE tmp_cleanup_menu_next;

-- 收集被自定义实体、表单、列表、流程节点或待删除菜单引用的字典。
-- 未被这些配置引用的基础字典不会删除。
DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_dict;
CREATE TEMPORARY TABLE tmp_cleanup_dict (
    id VARCHAR(64) NOT NULL,
    dict_code VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_tmp_cleanup_dict_code (dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 1. 实体字段的结构化字典绑定。
INSERT IGNORE INTO tmp_cleanup_dict (id, dict_code)
SELECT d.id, d.dict_code
FROM sys_dict d
JOIN tmp_cleanup_entity_field f
  ON f.dict_type = d.dict_code COLLATE utf8mb4_unicode_ci;

-- 2. 自定义作用域数据源中的字典绑定。
INSERT IGNORE INTO tmp_cleanup_dict (id, dict_code)
SELECT DISTINCT d.id, d.dict_code
FROM sys_dict d
JOIN ui_data_source_definition s
  ON UPPER(s.source_type) = 'DICTIONARY'
 AND JSON_UNQUOTE(JSON_EXTRACT(
       IF(JSON_VALID(s.config_document), s.config_document, '{}'),
       '$.dictCode'
     )) COLLATE utf8mb4_unicode_ci
     = d.dict_code COLLATE utf8mb4_unicode_ci
WHERE (
        UPPER(s.scope_type) = 'ENTITY'
        AND EXISTS (
            SELECT 1
            FROM tmp_cleanup_entity e
            WHERE s.scope_id COLLATE utf8mb4_unicode_ci IN (
                CAST(e.id AS CHAR) COLLATE utf8mb4_unicode_ci,
                e.entity_code
            )
        )
      )
   OR (
        UPPER(s.scope_type) = 'FORM'
        AND EXISTS (
            SELECT 1
            FROM tmp_cleanup_entity_form f
            WHERE f.id = s.scope_id
        )
      )
   OR (
        UPPER(s.scope_type) = 'LIST'
        AND EXISTS (
            SELECT 1
            FROM tmp_cleanup_entity_list l
            WHERE l.id = s.scope_id
        )
      );

-- 3. 流程节点与流程动作 JSON 中出现的字典编码。
INSERT IGNORE INTO tmp_cleanup_dict (id, dict_code)
SELECT DISTINCT d.id, d.dict_code
FROM sys_dict d
WHERE EXISTS (
    SELECT 1
    FROM process_node_config n
    WHERE JSON_SEARCH(
        IF(JSON_VALID(n.config_json), n.config_json, '{}'),
        'one',
        d.dict_code
    ) IS NOT NULL
)
OR EXISTS (
    SELECT 1
    FROM process_action a
    WHERE JSON_SEARCH(
        IF(JSON_VALID(a.params_json), a.params_json, '{}'),
        'one',
        d.dict_code
    ) IS NOT NULL
)
OR EXISTS (
    SELECT 1
    FROM process_definition_config p
    WHERE LOCATE(CONCAT('"', d.dict_code, '"'), COALESCE(p.bpmn_xml, '')) > 0
       OR LOCATE(
            CONCAT('&quot;', d.dict_code, '&quot;'),
            COALESCE(p.bpmn_xml, '')
          ) > 0
);

-- 4. 自定义表单节点 JSON 中出现的字典编码。
INSERT IGNORE INTO tmp_cleanup_dict (id, dict_code)
SELECT DISTINCT d.id, d.dict_code
FROM sys_dict d
JOIN entity_form_field ff
JOIN tmp_cleanup_entity_form tf
  ON tf.id = ff.form_id COLLATE utf8mb4_unicode_ci
WHERE JSON_SEARCH(
          IF(JSON_VALID(ff.component_props), ff.component_props, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL
   OR JSON_SEARCH(
          IF(JSON_VALID(ff.extension_config), ff.extension_config, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL
   OR JSON_SEARCH(
          IF(JSON_VALID(ff.validation_rules), ff.validation_rules, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL;

INSERT IGNORE INTO tmp_cleanup_dict (id, dict_code)
SELECT DISTINCT d.id, d.dict_code
FROM sys_dict d
JOIN entity_form_node fn
JOIN tmp_cleanup_entity_form tf
  ON tf.id = fn.form_id COLLATE utf8mb4_unicode_ci
WHERE JSON_SEARCH(
          IF(JSON_VALID(fn.props_document), fn.props_document, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL
   OR JSON_SEARCH(
          IF(JSON_VALID(fn.rules_document), fn.rules_document, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL
   OR JSON_SEARCH(
          IF(
              JSON_VALID(fn.data_source_bindings_document),
              fn.data_source_bindings_document,
              '{}'
          ),
          'one',
          d.dict_code
      ) IS NOT NULL;

INSERT IGNORE INTO tmp_cleanup_dict (id, dict_code)
SELECT DISTINCT d.id, d.dict_code
FROM sys_dict d
JOIN entity_form f
JOIN tmp_cleanup_entity_form tf
  ON tf.id = f.id COLLATE utf8mb4_unicode_ci
WHERE JSON_SEARCH(
          IF(JSON_VALID(f.init_config), f.init_config, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL
   OR JSON_SEARCH(
          IF(JSON_VALID(f.view_config), f.view_config, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL
   OR JSON_SEARCH(
          IF(
              JSON_VALID(f.data_source_bindings_document),
              f.data_source_bindings_document,
              '{}'
          ),
          'one',
          d.dict_code
      ) IS NOT NULL;

-- 5. 自定义列表 JSON 中出现的字典编码。
INSERT IGNORE INTO tmp_cleanup_dict (id, dict_code)
SELECT DISTINCT d.id, d.dict_code
FROM sys_dict d
JOIN entity_list_field lf
JOIN tmp_cleanup_entity_list tl
  ON tl.id = lf.list_config_id COLLATE utf8mb4_unicode_ci
WHERE JSON_SEARCH(
          IF(JSON_VALID(lf.data_source_config), lf.data_source_config, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL
   OR JSON_SEARCH(
          IF(JSON_VALID(lf.column_config), lf.column_config, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL
   OR JSON_SEARCH(
          IF(JSON_VALID(lf.query_config), lf.query_config, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL
   OR JSON_SEARCH(
          IF(JSON_VALID(lf.render_config), lf.render_config, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL;

INSERT IGNORE INTO tmp_cleanup_dict (id, dict_code)
SELECT DISTINCT d.id, d.dict_code
FROM sys_dict d
JOIN entity_list_config l
JOIN tmp_cleanup_entity_list tl
  ON tl.id = l.id COLLATE utf8mb4_unicode_ci
WHERE JSON_SEARCH(
          IF(JSON_VALID(l.view_config), l.view_config, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL
   OR JSON_SEARCH(
          IF(JSON_VALID(l.toolbar_config), l.toolbar_config, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL
   OR JSON_SEARCH(
          IF(JSON_VALID(l.row_action_config), l.row_action_config, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL
   OR JSON_SEARCH(
          IF(JSON_VALID(l.selection_config), l.selection_config, '{}'),
          'one',
          d.dict_code
      ) IS NOT NULL;

-- 6. 待删除菜单参数中出现的字典编码。
INSERT IGNORE INTO tmp_cleanup_dict (id, dict_code)
SELECT DISTINCT d.id, d.dict_code
FROM sys_dict d
JOIN sys_menu m
JOIN tmp_cleanup_menu tm ON tm.id = m.id COLLATE utf8mb4_unicode_ci
WHERE LOCATE(d.dict_code, COALESCE(m.query, '')) > 0;

-- 记录需要移除自定义实体可见范围的流程动作目录项。
DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_action_definition;
CREATE TEMPORARY TABLE tmp_cleanup_action_definition (
    id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO tmp_cleanup_action_definition (id)
SELECT DISTINCT binding.action_definition_id
FROM process_action_definition_entity binding
JOIN tmp_cleanup_entity e
  ON e.entity_code = binding.entity_code COLLATE utf8mb4_unicode_ci;

INSERT IGNORE INTO tmp_cleanup_action_definition (id)
SELECT DISTINCT d.id
FROM process_action_definition d
JOIN tmp_cleanup_entity e
WHERE JSON_SEARCH(
    IF(JSON_VALID(d.entity_codes_json), d.entity_codes_json, '[]'),
    'one',
    e.entity_code
) IS NOT NULL;

-- 配置迁移中的实体/流程资产和受影响发布包属于测试派生数据。
DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_migration_asset;
CREATE TEMPORARY TABLE tmp_cleanup_migration_asset (
    id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO tmp_cleanup_migration_asset (id)
SELECT a.id
FROM config_migration_asset a
WHERE UPPER(a.asset_type) = 'PROCESS'
   OR (
        UPPER(a.asset_type) = 'ENTITY'
        AND EXISTS (
            SELECT 1
            FROM tmp_cleanup_entity e
            WHERE e.entity_code = a.business_key
        )
      );

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_export_package;
CREATE TEMPORARY TABLE tmp_cleanup_export_package (
    id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO tmp_cleanup_export_package (id)
SELECT DISTINCT i.package_id
FROM config_export_package_item i
LEFT JOIN tmp_cleanup_migration_asset a ON a.id = i.asset_id
WHERE a.id IS NOT NULL
   OR UPPER(i.asset_type) = 'PROCESS'
   OR (
        UPPER(i.asset_type) = 'ENTITY'
        AND EXISTS (
            SELECT 1
            FROM tmp_cleanup_entity e
            WHERE e.entity_code = i.business_key
        )
      );

DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_import_package;
CREATE TEMPORARY TABLE tmp_cleanup_import_package (
    id VARCHAR(64) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO tmp_cleanup_import_package (id)
SELECT DISTINCT i.import_package_id
FROM config_import_item i
WHERE UPPER(i.asset_type) = 'PROCESS'
   OR (
        UPPER(i.asset_type) = 'ENTITY'
        AND EXISTS (
            SELECT 1
            FROM tmp_cleanup_entity e
            WHERE e.entity_code = i.business_key
        )
      );

-- =====================================================================
-- 二、执行前预览
-- =====================================================================

SELECT
    'CURRENT_DATABASE' AS item,
    DATABASE() AS value;

SELECT
    e.id,
    e.entity_code,
    e.entity_name,
    e.lifecycle_mode,
    e.status,
    e.table_name,
    CASE
        WHEN e.table_name IS NULL OR e.table_name = '' THEN 'NO_PHYSICAL_TABLE_RECORDED'
        WHEN t.table_name IS NULL THEN 'PHYSICAL_TABLE_NOT_FOUND'
        ELSE 'WILL_DROP'
    END AS physical_table_action,
    CASE
        WHEN mt.table_name IS NULL THEN 'NO_MULTI_TABLE'
        ELSE 'WILL_DROP'
    END AS multi_table_action,
    CASE
        WHEN tt.table_name IS NULL THEN 'NO_TEAM_TABLE'
        ELSE 'WILL_DROP'
    END AS team_table_action
FROM tmp_cleanup_entity e
LEFT JOIN information_schema.tables t
  ON t.table_schema = DATABASE()
 AND CONVERT(t.table_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
     = e.table_name
LEFT JOIN information_schema.tables mt
  ON mt.table_schema = DATABASE()
 AND CONVERT(mt.table_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
     = CONCAT(e.table_name, '_multi')
LEFT JOIN information_schema.tables tt
  ON tt.table_schema = DATABASE()
 AND CONVERT(tt.table_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
     = CONCAT(e.table_name, '_team')
ORDER BY e.entity_code;

SELECT id, process_key, process_name, status
FROM tmp_cleanup_process
ORDER BY process_key;

SELECT
    m.id,
    m.parent_id,
    m.menu_name,
    m.menu_type,
    m.path,
    m.entity_code,
    m.resource_type
FROM sys_menu m
JOIN tmp_cleanup_menu t ON t.id = m.id COLLATE utf8mb4_unicode_ci
ORDER BY m.parent_id, m.sort, m.id;

SELECT d.id, d.dict_code, d.dict_name
FROM sys_dict d
JOIN tmp_cleanup_dict t ON t.id = d.id COLLATE utf8mb4_unicode_ci
ORDER BY d.dict_code;

SELECT 'custom_entities' AS item, COUNT(*) AS candidate_count
FROM tmp_cleanup_entity
UNION ALL
SELECT 'process_definitions', COUNT(*)
FROM tmp_cleanup_process
UNION ALL
SELECT 'process_versions', COUNT(*)
FROM tmp_cleanup_process_version
UNION ALL
SELECT 'menus', COUNT(*)
FROM tmp_cleanup_menu
UNION ALL
SELECT 'dictionaries', COUNT(*)
FROM tmp_cleanup_dict
UNION ALL
SELECT 'physical_tables', COUNT(*)
FROM tmp_cleanup_physical_table
UNION ALL
SELECT 'migration_assets', COUNT(*)
FROM tmp_cleanup_migration_asset
UNION ALL
SELECT 'export_packages', COUNT(*)
FROM tmp_cleanup_export_package
UNION ALL
SELECT 'import_packages', COUNT(*)
FROM tmp_cleanup_import_package;

-- 孤立 biz_ 表也属于历史自定义实体数据，将随正式清理删除。
SELECT
    table_name AS orphan_custom_physical_table
FROM tmp_cleanup_physical_table
WHERE cleanup_reason = 'ORPHAN_BIZ_TABLE'
ORDER BY table_name;

-- =====================================================================
-- 三、执行过程
-- =====================================================================

-- 清空 Flowable 的流程仓库、运行时和历史数据，但保留：
--   ACT_GE_PROPERTY：Flowable 版本和 ID 生成器状态
--   ACT_ID_*：Flowable 身份目录（即使当前系统主要使用 sys_user）
DROP PROCEDURE IF EXISTS cleanup_flowable_process_data_20260727;
DELIMITER $$
CREATE PROCEDURE cleanup_flowable_process_data_20260727()
BEGIN
    DECLARE finished INT DEFAULT 0;
    DECLARE current_table VARCHAR(128);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND (
              table_name REGEXP '^ACT_RU_'
              OR table_name REGEXP '^ACT_HI_'
              OR table_name REGEXP '^ACT_RE_'
              OR table_name IN (
                  'ACT_EVT_LOG',
                  'ACT_PROCDEF_INFO',
                  'ACT_GE_BYTEARRAY'
              )
              OR table_name REGEXP '^FLW_RU_BATCH'
          )
        ORDER BY table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

    OPEN table_cursor;
    delete_loop: LOOP
        FETCH table_cursor INTO current_table;
        IF finished = 1 THEN
            LEAVE delete_loop;
        END IF;

        SET @cleanup_dynamic_sql = CONCAT(
            'DELETE FROM `',
            REPLACE(current_table, '`', '``'),
            '`'
        );
        PREPARE cleanup_statement FROM @cleanup_dynamic_sql;
        EXECUTE cleanup_statement;
        DEALLOCATE PREPARE cleanup_statement;
    END LOOP;
    CLOSE table_cursor;
END$$
DELIMITER ;

-- 删除自定义实体主表、_multi、_team 以及孤立 biz_ 物理表。
DROP PROCEDURE IF EXISTS cleanup_drop_entity_tables_20260727;
DELIMITER $$
CREATE PROCEDURE cleanup_drop_entity_tables_20260727()
BEGIN
    DECLARE finished INT DEFAULT 0;
    DECLARE physical_table VARCHAR(64);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
        FROM tmp_cleanup_physical_table
        ORDER BY table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

    OPEN table_cursor;
    drop_loop: LOOP
        FETCH table_cursor INTO physical_table;
        IF finished = 1 THEN
            LEAVE drop_loop;
        END IF;

        SET @cleanup_dynamic_sql = CONCAT(
            'DROP TABLE IF EXISTS `',
            REPLACE(physical_table, '`', '``'),
            '`'
        );
        PREPARE cleanup_statement FROM @cleanup_dynamic_sql;
        EXECUTE cleanup_statement;
        DEALLOCATE PREPARE cleanup_statement;
    END LOOP;
    CLOSE table_cursor;
END$$
DELIMITER ;

DROP PROCEDURE IF EXISTS cleanup_custom_entity_process_data_20260727;
DELIMITER $$
CREATE PROCEDURE cleanup_custom_entity_process_data_20260727()
cleanup_main: BEGIN
    DECLARE invalid_table_count INT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET SESSION FOREIGN_KEY_CHECKS = @cleanup_old_foreign_key_checks;
        SET SESSION SQL_SAFE_UPDATES = @cleanup_old_sql_safe_updates;
        RESIGNAL;
    END;

    IF UPPER(COALESCE(@cleanup_mode, 'PREVIEW')) <> 'EXECUTE'
       OR COALESCE(@cleanup_confirm, '') <> 'DELETE_CUSTOM_ENTITY_PROCESS_DATA' THEN
        SELECT
            'PREVIEW_ONLY' AS cleanup_status,
            '未设置 EXECUTE 和确认令牌，未执行任何 DELETE/DROP。' AS message;
        LEAVE cleanup_main;
    END IF;

    IF DATABASE() IS NULL OR DATABASE() = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '未选择数据库，请先 USE workflow 或指定目标数据库';
    END IF;

    SELECT COUNT(*)
    INTO invalid_table_count
    FROM tmp_cleanup_physical_table
    WHERE (
          table_name NOT REGEXP '^biz_[a-z0-9_]+$'
          OR CHAR_LENGTH(table_name) > 64
      );

    IF invalid_table_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '检测到非 biz_ 前缀或非法动态表名，已拒绝执行';
    END IF;

    SET SESSION SQL_SAFE_UPDATES = 0;
    SET SESSION FOREIGN_KEY_CHECKS = 0;

    START TRANSACTION;

    -- 1. 清理可能继续投递旧业务事件的 Outbox。
    DELETE FROM workflow_outbox_event;

    -- 2. 清理与实体、流程、动作执行和配置迁移有关的系统审计记录。
    DELETE FROM system_operation_log
    WHERE module_code IN ('ENTITY', 'PROCESS', 'ACTION', 'MIGRATION');

    DELETE operation_log
    FROM system_operation_log operation_log
    JOIN tmp_cleanup_menu menu_candidate
      ON menu_candidate.id
         = operation_log.target_id COLLATE utf8mb4_unicode_ci
    WHERE operation_log.module_code = 'SYSTEM';

    DELETE operation_log
    FROM system_operation_log operation_log
    JOIN tmp_cleanup_dict dict_candidate
      ON dict_candidate.id
         = operation_log.target_id COLLATE utf8mb4_unicode_ci
    WHERE operation_log.module_code = 'SYSTEM';

    DELETE operation_log
    FROM system_operation_log operation_log
    JOIN tmp_cleanup_dict dict_candidate
      ON dict_candidate.dict_code
         = operation_log.target_id COLLATE utf8mb4_unicode_ci
    WHERE operation_log.module_code = 'SYSTEM';

    -- 3. 清理配置迁移派生资产和受影响发布包。
    DELETE FROM config_migration_asset_dependency
    WHERE asset_id IN (SELECT id FROM tmp_cleanup_migration_asset);

    DELETE FROM config_export_package_item
    WHERE package_id IN (SELECT id FROM tmp_cleanup_export_package);

    DELETE FROM config_export_package
    WHERE id IN (SELECT id FROM tmp_cleanup_export_package);

    DELETE FROM config_import_item
    WHERE import_package_id IN (SELECT id FROM tmp_cleanup_import_package);

    DELETE baseline
    FROM config_asset_baseline baseline
    WHERE UPPER(baseline.asset_type) = 'PROCESS'
       OR (
            UPPER(baseline.asset_type) = 'ENTITY'
            AND EXISTS (
                SELECT 1
                FROM tmp_cleanup_entity e
                WHERE e.entity_code = baseline.business_key
            )
          )
       OR baseline.import_package_id IN (
            SELECT id FROM tmp_cleanup_import_package
       );

    DELETE FROM config_import_package
    WHERE id IN (SELECT id FROM tmp_cleanup_import_package);

    DELETE FROM config_migration_asset
    WHERE id IN (SELECT id FROM tmp_cleanup_migration_asset);

    -- 4. 清理 Flowable 流程仓库、运行时和历史数据。
    CALL cleanup_flowable_process_data_20260727();

    -- 5. 清理平台流程运行数据。
    DELETE FROM process_task_candidate_user;
    DELETE FROM process_task_candidate_group;
    DELETE FROM process_task_add_sign_user;
    DELETE FROM process_task_add_sign;
    DELETE FROM process_task_instance;
    DELETE FROM process_task;
    DELETE FROM process_cc_record;
    DELETE FROM process_action_execution;
    DELETE FROM process_operation_log;
    DELETE FROM process_draft;

    -- 6. 清理平台流程设计、发布和节点配置。
    DELETE FROM ui_config_hotfix_target;
    DELETE FROM process_ui_release_binding;
    DELETE FROM process_entity_status_mapping;
    DELETE FROM process_action;
    DELETE FROM process_node_approval_option;
    DELETE FROM process_node_approval;
    DELETE FROM process_form_field_config;
    DELETE FROM process_form_config;
    DELETE FROM process_node_assignee;
    DELETE FROM process_node_form;
    DELETE FROM process_node_config;
    DELETE FROM process_version_history;
    DELETE FROM process_definition_config;

    -- process_common_opinion、process_action_definition、
    -- process_person_resolver_definition 属于基础目录或用户偏好，予以保留。

    -- 7. 移除流程动作目录中指向待删除实体的可见范围。
    DELETE binding
    FROM process_action_definition_entity binding
    JOIN tmp_cleanup_entity e
      ON e.entity_code
         = binding.entity_code COLLATE utf8mb4_unicode_ci;

    UPDATE process_action_definition definition
    JOIN tmp_cleanup_action_definition affected
      ON affected.id = definition.id COLLATE utf8mb4_unicode_ci
    SET definition.entity_codes_json = COALESCE(
        (
            SELECT JSON_ARRAYAGG(binding.entity_code)
            FROM process_action_definition_entity binding
            WHERE binding.action_definition_id COLLATE utf8mb4_unicode_ci
                  = definition.id COLLATE utf8mb4_unicode_ci
        ),
        JSON_ARRAY()
    );

    -- 8. 清理自定义实体运行数据和状态历史。
    DELETE record
    FROM runtime_entity_record record
    JOIN tmp_cleanup_entity e
      ON e.entity_code
         = record.entity_code COLLATE utf8mb4_unicode_ci;

    DELETE history
    FROM entity_status_history history
    JOIN tmp_cleanup_entity e
      ON e.entity_code
         = history.entity_code COLLATE utf8mb4_unicode_ci;

    -- 9. 清理自定义实体表单/列表发布快照和作用域数据源。
    DELETE FROM ui_config_release_audit
    WHERE (
            config_type = 'FORM'
            AND config_id IN (SELECT id FROM tmp_cleanup_entity_form)
          )
       OR (
            config_type = 'LIST'
            AND config_id IN (SELECT id FROM tmp_cleanup_entity_list)
          );

    DELETE FROM ui_config_release
    WHERE (
            config_type = 'FORM'
            AND config_id IN (SELECT id FROM tmp_cleanup_entity_form)
          )
       OR (
            config_type = 'LIST'
            AND config_id IN (SELECT id FROM tmp_cleanup_entity_list)
          );

    DELETE data_source
    FROM ui_data_source_definition data_source
    WHERE (
            UPPER(data_source.scope_type) = 'ENTITY'
            AND EXISTS (
                SELECT 1
                FROM tmp_cleanup_entity e
                WHERE data_source.scope_id COLLATE utf8mb4_unicode_ci IN (
                    CAST(e.id AS CHAR) COLLATE utf8mb4_unicode_ci,
                    e.entity_code
                )
            )
          )
       OR (
            UPPER(data_source.scope_type) = 'FORM'
            AND data_source.scope_id IN (
                SELECT id FROM tmp_cleanup_entity_form
            )
          )
       OR (
            UPPER(data_source.scope_type) = 'LIST'
            AND data_source.scope_id IN (
                SELECT id FROM tmp_cleanup_entity_list
            )
          );

    -- 10. 清理列表数据范围。
    DELETE delegation
    FROM entity_list_scope_delegation delegation
    JOIN tmp_cleanup_entity entity_candidate
      ON entity_candidate.entity_code
         = delegation.entity_code COLLATE utf8mb4_unicode_ci;

    DELETE delegation
    FROM entity_list_scope_delegation delegation
    JOIN entity_list_scope_policy policy
      ON policy.id = delegation.policy_id
    JOIN tmp_cleanup_entity entity_candidate
      ON entity_candidate.entity_code
         = policy.entity_code COLLATE utf8mb4_unicode_ci;

    DELETE FROM entity_list_scope_binding
    WHERE entity_code IN (SELECT entity_code FROM tmp_cleanup_entity);

    DELETE FROM entity_list_scope_audit_log
    WHERE entity_code IN (SELECT entity_code FROM tmp_cleanup_entity);

    DELETE FROM entity_list_scope_release
    WHERE entity_code IN (SELECT entity_code FROM tmp_cleanup_entity);

    DELETE FROM entity_list_scope_policy
    WHERE entity_code IN (SELECT entity_code FROM tmp_cleanup_entity);

    -- 11. 清理实体列表。
    DELETE list_action
    FROM entity_list_action list_action
    JOIN tmp_cleanup_entity_list l ON l.id = list_action.list_config_id;

    DELETE list_field
    FROM entity_list_field list_field
    JOIN tmp_cleanup_entity_list l ON l.id = list_field.list_config_id;

    DELETE scene
    FROM entity_list_scene scene
    JOIN tmp_cleanup_entity_list l ON l.id = scene.list_config_id;

    DELETE list_config
    FROM entity_list_config list_config
    JOIN tmp_cleanup_entity_list l ON l.id = list_config.id;

    -- 12. 清理实体表单。
    DELETE node
    FROM entity_form_node node
    JOIN tmp_cleanup_entity_form f ON f.id = node.form_id;

    DELETE form_field
    FROM entity_form_field form_field
    JOIN tmp_cleanup_entity_form f
      ON f.id = form_field.form_id COLLATE utf8mb4_unicode_ci;

    DELETE form
    FROM entity_form form
    JOIN tmp_cleanup_entity_form f
      ON f.id = form.id COLLATE utf8mb4_unicode_ci;

    -- 13. 清理实体关系、状态、发布记录和编码规则。
    DELETE relation
    FROM entity_relation relation
    JOIN tmp_cleanup_entity entity_candidate
      ON CAST(entity_candidate.id AS CHAR) COLLATE utf8mb4_unicode_ci
         = relation.parent_entity_id COLLATE utf8mb4_unicode_ci;

    DELETE relation
    FROM entity_relation relation
    JOIN tmp_cleanup_entity entity_candidate
      ON CAST(entity_candidate.id AS CHAR) COLLATE utf8mb4_unicode_ci
         = relation.child_entity_id COLLATE utf8mb4_unicode_ci;

    DELETE relation
    FROM entity_relation relation
    JOIN tmp_cleanup_entity entity_candidate
      ON entity_candidate.entity_code
         = relation.parent_entity_code COLLATE utf8mb4_unicode_ci;

    DELETE relation
    FROM entity_relation relation
    JOIN tmp_cleanup_entity entity_candidate
      ON entity_candidate.entity_code
         = relation.child_entity_code COLLATE utf8mb4_unicode_ci;

    DELETE FROM entity_status
    WHERE entity_code IN (SELECT entity_code FROM tmp_cleanup_entity);

    DELETE publish_history
    FROM entity_publish_history publish_history
    JOIN tmp_cleanup_entity entity_candidate
      ON entity_candidate.entity_code
         = publish_history.entity_code COLLATE utf8mb4_unicode_ci;

    DELETE publish_history
    FROM entity_publish_history publish_history
    JOIN tmp_cleanup_entity entity_candidate
      ON CAST(entity_candidate.id AS CHAR) COLLATE utf8mb4_unicode_ci
         = publish_history.entity_id COLLATE utf8mb4_unicode_ci;

    DELETE FROM entity_code_rule
    WHERE entity_code COLLATE utf8mb4_unicode_ci IN (
        SELECT entity_code FROM tmp_cleanup_entity
    );

    -- 14. 清理字段附件项、选项及字段定义。
    DELETE item
    FROM entity_field_file_item item
    JOIN tmp_cleanup_entity_field f
      ON f.id = item.field_id COLLATE utf8mb4_unicode_ci;

    DELETE option_value
    FROM entity_field_option option_value
    JOIN tmp_cleanup_entity_field f ON f.id = option_value.field_id;

    DELETE entity_field_row
    FROM entity_field entity_field_row
    JOIN tmp_cleanup_entity e ON e.id = entity_field_row.entity_id;

    -- 15. 清理实体关联菜单、角色授权和工作台快捷入口。
    DELETE shortcut
    FROM workbench_shortcut shortcut
    WHERE (
            UPPER(COALESCE(shortcut.shortcut_type, '')) = 'MENU'
            AND shortcut.target_id IN (SELECT id FROM tmp_cleanup_menu)
          );

    DELETE shortcut
    FROM workbench_shortcut shortcut
    JOIN tmp_cleanup_entity entity_candidate
      ON entity_candidate.entity_code
         = shortcut.target_id COLLATE utf8mb4_unicode_ci
    WHERE UPPER(COALESCE(shortcut.shortcut_type, '')) = 'ENTITY';

    DELETE shortcut
    FROM workbench_shortcut shortcut
    JOIN tmp_cleanup_entity entity_candidate
      ON CAST(entity_candidate.id AS CHAR) COLLATE utf8mb4_unicode_ci
         = shortcut.target_id COLLATE utf8mb4_unicode_ci
    WHERE UPPER(COALESCE(shortcut.shortcut_type, '')) = 'ENTITY';

    DELETE FROM sys_role_menu
    WHERE menu_id COLLATE utf8mb4_unicode_ci IN (
        SELECT id FROM tmp_cleanup_menu
    );

    DELETE menu
    FROM sys_menu menu
    JOIN tmp_cleanup_menu candidate
      ON candidate.id = menu.id COLLATE utf8mb4_unicode_ci;

    -- 16. 清理被实体、流程、表单、列表或相关菜单引用的字典。
    DELETE item
    FROM sys_dict_item item
    JOIN tmp_cleanup_dict d
      ON d.id = item.dict_id COLLATE utf8mb4_unicode_ci;

    DELETE item
    FROM sys_dict_item item
    JOIN tmp_cleanup_dict d
      ON d.dict_code = item.dict_code COLLATE utf8mb4_unicode_ci;

    DELETE dictionary
    FROM sys_dict dictionary
    JOIN tmp_cleanup_dict d
      ON d.id = dictionary.id COLLATE utf8mb4_unicode_ci;

    -- 17. 最后删除自定义实体定义。
    DELETE definition
    FROM entity_definition definition
    JOIN tmp_cleanup_entity e ON e.id = definition.id;

    COMMIT;

    -- 动态表删除是 DDL，放在业务数据事务提交后执行。
    CALL cleanup_drop_entity_tables_20260727();

    SET SESSION FOREIGN_KEY_CHECKS = @cleanup_old_foreign_key_checks;
    SET SESSION SQL_SAFE_UPDATES = @cleanup_old_sql_safe_updates;

    SELECT
        'EXECUTED' AS cleanup_status,
        '自定义实体、流程及其关联菜单/字典已清理。' AS message;
END$$
DELIMITER ;

CALL cleanup_custom_entity_process_data_20260727();

-- =====================================================================
-- 四、执行后校验
-- PREVIEW 模式下这些结果仍是当前数据库原始数据。
-- =====================================================================

SELECT
    'remaining_custom_entities' AS item,
    COUNT(*) AS remaining_count
FROM entity_definition
WHERE COALESCE(storage_mode, 'DYNAMIC') <> 'SYSTEM'
UNION ALL
SELECT
    'remaining_process_definitions',
    COUNT(*)
FROM process_definition_config
UNION ALL
SELECT
    'remaining_process_versions',
    COUNT(*)
FROM process_version_history
UNION ALL
SELECT
    'remaining_candidate_menus',
    COUNT(*)
FROM sys_menu
WHERE id COLLATE utf8mb4_unicode_ci IN (
    SELECT id FROM tmp_cleanup_menu
)
UNION ALL
SELECT
    'remaining_candidate_dicts',
    COUNT(*)
FROM sys_dict
WHERE id COLLATE utf8mb4_unicode_ci IN (
    SELECT id FROM tmp_cleanup_dict
)
UNION ALL
SELECT
    'remaining_outbox_events',
    COUNT(*)
FROM workflow_outbox_event;

SELECT
    storage_mode,
    COUNT(*) AS entity_count
FROM entity_definition
GROUP BY storage_mode
ORDER BY storage_mode;

SELECT
    t.table_name AS remaining_custom_physical_table
FROM information_schema.tables t
JOIN tmp_cleanup_physical_table physical_table
  ON CONVERT(t.table_name USING utf8mb4) COLLATE utf8mb4_unicode_ci
     = physical_table.table_name
WHERE t.table_schema = DATABASE()
ORDER BY t.table_name;

-- Flowable 数据表应保留结构；EXECUTE 后其流程数据行应为 0。
-- ACT_GE_PROPERTY 和 ACT_ID_* 不在清理范围。
DROP TEMPORARY TABLE IF EXISTS tmp_cleanup_flowable_row_count;
CREATE TEMPORARY TABLE tmp_cleanup_flowable_row_count (
    table_name VARCHAR(64) NOT NULL,
    exact_rows BIGINT NOT NULL,
    PRIMARY KEY (table_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DROP PROCEDURE IF EXISTS cleanup_collect_flowable_row_counts_20260727;
DELIMITER $$
CREATE PROCEDURE cleanup_collect_flowable_row_counts_20260727()
BEGIN
    DECLARE finished INT DEFAULT 0;
    DECLARE flowable_table VARCHAR(64);
    DECLARE table_cursor CURSOR FOR
        SELECT table_name
        FROM information_schema.tables
        WHERE table_schema = DATABASE()
          AND (
              table_name REGEXP '^ACT_RU_'
              OR table_name REGEXP '^ACT_HI_'
              OR table_name REGEXP '^ACT_RE_'
              OR table_name IN (
                  'ACT_EVT_LOG',
                  'ACT_PROCDEF_INFO',
                  'ACT_GE_BYTEARRAY'
              )
              OR table_name REGEXP '^FLW_RU_BATCH'
          )
        ORDER BY table_name;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET finished = 1;

    OPEN table_cursor;
    count_loop: LOOP
        FETCH table_cursor INTO flowable_table;
        IF finished = 1 THEN
            LEAVE count_loop;
        END IF;

        SET @cleanup_dynamic_sql = CONCAT(
            'INSERT INTO tmp_cleanup_flowable_row_count ',
            '(table_name, exact_rows) SELECT ',
            QUOTE(flowable_table),
            ', COUNT(*) FROM `',
            REPLACE(flowable_table, '`', '``'),
            '`'
        );
        PREPARE cleanup_statement FROM @cleanup_dynamic_sql;
        EXECUTE cleanup_statement;
        DEALLOCATE PREPARE cleanup_statement;
    END LOOP;
    CLOSE table_cursor;
END$$
DELIMITER ;

CALL cleanup_collect_flowable_row_counts_20260727();
DROP PROCEDURE cleanup_collect_flowable_row_counts_20260727;

SELECT table_name, exact_rows
FROM tmp_cleanup_flowable_row_count
ORDER BY table_name;

-- 基础数据确认。
SELECT 'system_entities' AS item, COUNT(*) AS retained_count
FROM entity_definition
WHERE storage_mode = 'SYSTEM'
UNION ALL
SELECT 'system_users', COUNT(*) FROM sys_user
UNION ALL
SELECT 'system_roles', COUNT(*) FROM sys_role
UNION ALL
SELECT 'organizations', COUNT(*) FROM sys_organization
UNION ALL
SELECT 'process_action_catalog', COUNT(*) FROM process_action_definition
UNION ALL
SELECT 'person_resolver_catalog', COUNT(*) FROM process_person_resolver_definition
UNION ALL
SELECT 'flyway_history', COUNT(*) FROM flyway_schema_history;

-- 清理临时过程；临时表会在会话结束时自动释放。
DROP PROCEDURE cleanup_custom_entity_process_data_20260727;
DROP PROCEDURE cleanup_drop_entity_tables_20260727;
DROP PROCEDURE cleanup_flowable_process_data_20260727;

SET SESSION FOREIGN_KEY_CHECKS = @cleanup_old_foreign_key_checks;
SET SESSION SQL_SAFE_UPDATES = @cleanup_old_sql_safe_updates;
