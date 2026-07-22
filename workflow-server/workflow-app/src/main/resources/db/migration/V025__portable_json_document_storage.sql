-- JSON 文档可移植存储与关键配置关系化。
-- Flowable ACT_* 表保持原状；平台表和动态 biz_* 表不再依赖 MySQL JSON 类型。

CREATE TABLE IF NOT EXISTS `system_json_document_migration_log` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `table_name` varchar(128) NOT NULL COMMENT '迁移表名',
  `column_name` varchar(128) NOT NULL COMMENT '迁移字段名',
  `source_type` varchar(64) NOT NULL COMMENT '迁移前类型',
  `target_type` varchar(64) NOT NULL COMMENT '迁移后逻辑类型',
  `total_rows` bigint NOT NULL DEFAULT 0 COMMENT '总行数',
  `non_null_rows` bigint NOT NULL DEFAULT 0 COMMENT '非空行数',
  `invalid_rows` bigint NOT NULL DEFAULT 0 COMMENT '非法文档行数',
  `max_document_length` bigint DEFAULT NULL COMMENT '最大文档长度',
  `status` varchar(20) NOT NULL COMMENT 'SUCCESS/FAILED',
  `message` varchar(1000) DEFAULT NULL COMMENT '迁移说明',
  `migrated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '迁移时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_json_document_migration` (`table_name`,`column_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='JSON文档存储迁移审计';

CREATE TABLE IF NOT EXISTS `entity_list_action` (
  `id` varchar(64) NOT NULL COMMENT '主键ID',
  `list_config_id` varchar(64) NOT NULL COMMENT '列表配置ID',
  `position` varchar(20) NOT NULL COMMENT 'TOOLBAR/ROW',
  `button_key` varchar(100) NOT NULL COMMENT '稳定按钮编码',
  `button_type` varchar(30) NOT NULL DEFAULT 'built-in' COMMENT 'built-in/custom',
  `button_label` varchar(200) NOT NULL COMMENT '按钮名称',
  `icon` varchar(100) DEFAULT NULL COMMENT '图标',
  `style_type` varchar(30) DEFAULT NULL COMMENT '按钮样式',
  `link_mode` tinyint NOT NULL DEFAULT 0 COMMENT '是否链接按钮',
  `custom_mode` varchar(30) DEFAULT NULL COMMENT 'handler/component/open-list',
  `handler_code` varchar(200) DEFAULT NULL COMMENT '处理器或组件编码',
  `permission_code` varchar(200) DEFAULT NULL COMMENT '功能权限码',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序号',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  `unavailable_behavior` varchar(20) DEFAULT NULL COMMENT 'HIDE/DISABLE',
  `action_params_document` longtext COMMENT '按钮扩展参数JSON文档',
  `availability_rule_document` longtext COMMENT '按钮适用条件JSON文档',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `deleted` tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_action` (`list_config_id`,`position`,`button_key`,`deleted`),
  KEY `idx_entity_list_action_runtime` (`list_config_id`,`position`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='实体列表按钮配置';

CREATE TABLE IF NOT EXISTS `entity_list_scene` (
  `id` varchar(64) NOT NULL,
  `list_config_id` varchar(64) NOT NULL,
  `scene_code` varchar(30) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_list_scene` (`list_config_id`,`scene_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='实体列表允许场景';

CREATE TABLE IF NOT EXISTS `entity_field_option` (
  `id` varchar(64) NOT NULL,
  `field_id` varchar(64) NOT NULL,
  `option_value` varchar(500) NOT NULL,
  `option_label` varchar(500) NOT NULL,
  `style_type` varchar(50) DEFAULT NULL,
  `disabled` tinyint NOT NULL DEFAULT 0,
  `sort_order` int NOT NULL DEFAULT 0,
  `option_document` longtext COMMENT '选项扩展JSON文档',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_entity_field_option` (`field_id`,`option_value`),
  KEY `idx_entity_field_option_sort` (`field_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='实体字段静态选项';

CREATE TABLE IF NOT EXISTS `process_node_approval_option` (
  `id` varchar(64) NOT NULL,
  `approval_config_id` varchar(64) NOT NULL,
  `option_value` varchar(100) NOT NULL,
  `option_label` varchar(200) NOT NULL,
  `style_type` varchar(50) DEFAULT NULL,
  `show_comment` tinyint NOT NULL DEFAULT 1,
  `remark_required` tinyint NOT NULL DEFAULT 0,
  `sort_order` int NOT NULL DEFAULT 0,
  `option_document` longtext COMMENT '审批项扩展JSON文档',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_approval_option` (`approval_config_id`,`option_value`),
  KEY `idx_process_approval_option_sort` (`approval_config_id`,`sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='流程节点审批选项';

CREATE TABLE IF NOT EXISTS `process_action_definition_entity` (
  `id` varchar(64) NOT NULL,
  `action_definition_id` varchar(64) NOT NULL,
  `entity_code` varchar(100) NOT NULL,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_action_definition_entity` (`action_definition_id`,`entity_code`),
  KEY `idx_process_action_entity_code` (`entity_code`,`action_definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='流程动作可见实体';

CREATE TABLE IF NOT EXISTS `config_migration_asset_dependency` (
  `id` varchar(64) NOT NULL,
  `asset_id` varchar(64) NOT NULL,
  `dependency_type` varchar(50) NOT NULL,
  `dependency_key` varchar(300) NOT NULL,
  `required` tinyint NOT NULL DEFAULT 1,
  `source_description` varchar(500) DEFAULT NULL,
  `dependency_document` longtext COMMENT '依赖扩展JSON文档',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_asset_dependency` (`asset_id`,`dependency_type`,`dependency_key`),
  KEY `idx_config_dependency_lookup` (`dependency_type`,`dependency_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='配置迁移资产依赖';

CREATE TABLE IF NOT EXISTS `process_task_candidate_user` (
  `id` varchar(64) NOT NULL,
  `task_instance_id` varchar(64) NOT NULL,
  `user_id` varchar(100) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_task_candidate_user` (`task_instance_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='流程任务候选用户';

CREATE TABLE IF NOT EXISTS `process_task_candidate_group` (
  `id` varchar(64) NOT NULL,
  `task_instance_id` varchar(64) NOT NULL,
  `group_code` varchar(100) NOT NULL,
  `sort_order` int NOT NULL DEFAULT 0,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_process_task_candidate_group` (`task_instance_id`,`group_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='流程任务候选组';

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing_v025$$
CREATE PROCEDURE workflow_add_column_if_missing_v025(
    IN p_table VARCHAR(128),
    IN p_column VARCHAR(128),
    IN p_definition VARCHAR(2000)
)
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = p_table
          AND column_name = p_column
    ) THEN
        SET @add_column_sql = CONCAT(
            'ALTER TABLE `', p_table, '` ADD COLUMN `', p_column, '` ', p_definition
        );
        PREPARE add_column_stmt FROM @add_column_sql;
        EXECUTE add_column_stmt;
        DEALLOCATE PREPARE add_column_stmt;
    END IF;
END$$

DELIMITER ;

CALL workflow_add_column_if_missing_v025(
  'entity_form',
  'init_config',
  'longtext DEFAULT NULL COMMENT ''表单初始化配置JSON文档'''
);
CALL workflow_add_column_if_missing_v025(
  'process_operation_log',
  'old_value_format',
  'varchar(20) NOT NULL DEFAULT ''JSON'' COMMENT ''JSON/PLAIN_TEXT'''
);
CALL workflow_add_column_if_missing_v025(
  'process_operation_log',
  'new_value_format',
  'varchar(20) NOT NULL DEFAULT ''JSON'' COMMENT ''JSON/PLAIN_TEXT'''
);

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing_v025;

UPDATE entity_list_field
SET data_source_config = NULL
WHERE data_source_config IS NOT NULL
  AND TRIM(data_source_config) = '';

UPDATE entity_field
SET options_json = REPLACE(options_json, '\\\"', '"')
WHERE options_json IS NOT NULL
  AND JSON_VALID(options_json) = 0
  AND JSON_VALID(REPLACE(options_json, '\\\"', '"')) = 1;

UPDATE process_operation_log
SET old_value_format = CASE
      WHEN old_value IS NULL OR JSON_VALID(old_value) = 1 THEN 'JSON'
      ELSE 'PLAIN_TEXT'
    END,
    new_value_format = CASE
      WHEN new_value IS NULL OR JSON_VALID(new_value) = 1 THEN 'JSON'
      ELSE 'PLAIN_TEXT'
    END;

INSERT IGNORE INTO entity_list_action (
  id, list_config_id, position, button_key, button_type, button_label,
  icon, style_type, link_mode, custom_mode, handler_code, permission_code,
  sort_order, enabled, unavailable_behavior, action_params_document,
  availability_rule_document
)
SELECT
  SHA2(CONCAT('entity-list-action:', config.id, ':TOOLBAR:', item.ordinality, ':',
      COALESCE(JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.key')), item.ordinality)), 256),
  config.id,
  'TOOLBAR',
  COALESCE(JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.key')), CONCAT('toolbar_', item.ordinality)),
  COALESCE(JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.type')), 'built-in'),
  COALESCE(JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.label')),
      JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.key')), CONCAT('按钮', item.ordinality)),
  JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.icon')),
  JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.buttonType')),
  COALESCE(JSON_EXTRACT(item.button, '$.link') = TRUE, 0),
  JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.customMode')),
  COALESCE(
      JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.customHandler')),
      JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.customComponent'))
  ),
  JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.perm')),
  COALESCE(JSON_EXTRACT(item.button, '$.sort'), item.ordinality),
  COALESCE(JSON_EXTRACT(item.button, '$.enabled') <> FALSE, 1),
  JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.availabilityRule.unavailableBehavior')),
  CAST(item.button AS CHAR CHARACTER SET utf8mb4),
  CAST(JSON_EXTRACT(item.button, '$.availabilityRule') AS CHAR CHARACTER SET utf8mb4)
FROM entity_list_config config
JOIN JSON_TABLE(
  config.toolbar_config,
  '$[*]' COLUMNS (
    ordinality FOR ORDINALITY,
    button JSON PATH '$'
  )
) item
WHERE config.toolbar_config IS NOT NULL
  AND JSON_VALID(config.toolbar_config) = 1;

INSERT IGNORE INTO entity_list_action (
  id, list_config_id, position, button_key, button_type, button_label,
  icon, style_type, link_mode, custom_mode, handler_code, permission_code,
  sort_order, enabled, unavailable_behavior, action_params_document,
  availability_rule_document
)
SELECT
  SHA2(CONCAT('entity-list-action:', config.id, ':ROW:', item.ordinality, ':',
      COALESCE(JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.key')), item.ordinality)), 256),
  config.id,
  'ROW',
  COALESCE(JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.key')), CONCAT('row_', item.ordinality)),
  COALESCE(JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.type')), 'built-in'),
  COALESCE(JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.label')),
      JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.key')), CONCAT('按钮', item.ordinality)),
  JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.icon')),
  JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.buttonType')),
  COALESCE(JSON_EXTRACT(item.button, '$.link') = TRUE, 0),
  JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.customMode')),
  COALESCE(
      JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.customHandler')),
      JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.customComponent'))
  ),
  JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.perm')),
  COALESCE(JSON_EXTRACT(item.button, '$.sort'), item.ordinality),
  COALESCE(JSON_EXTRACT(item.button, '$.enabled') <> FALSE, 1),
  JSON_UNQUOTE(JSON_EXTRACT(item.button, '$.availabilityRule.unavailableBehavior')),
  CAST(item.button AS CHAR CHARACTER SET utf8mb4),
  CAST(JSON_EXTRACT(item.button, '$.availabilityRule') AS CHAR CHARACTER SET utf8mb4)
FROM entity_list_config config
JOIN JSON_TABLE(
  config.row_action_config,
  '$[*]' COLUMNS (
    ordinality FOR ORDINALITY,
    button JSON PATH '$'
  )
) item
WHERE config.row_action_config IS NOT NULL
  AND JSON_VALID(config.row_action_config) = 1;

INSERT IGNORE INTO entity_list_scene (id, list_config_id, scene_code, sort_order)
SELECT
  SHA2(CONCAT('entity-list-scene:', config.id, ':', scene.scene_code), 256),
  config.id,
  scene.scene_code,
  scene.ordinality
FROM entity_list_config config
JOIN JSON_TABLE(
  config.allowed_scenes,
  '$[*]' COLUMNS (
    ordinality FOR ORDINALITY,
    scene_code VARCHAR(30) PATH '$'
  )
) scene
WHERE config.allowed_scenes IS NOT NULL
  AND JSON_VALID(config.allowed_scenes) = 1;

INSERT IGNORE INTO entity_field_option (
  id, field_id, option_value, option_label, style_type, disabled,
  sort_order, option_document
)
SELECT
  SHA2(CONCAT('entity-field-option:', field.id, ':', option_item.option_value), 256),
  field.id,
  option_item.option_value,
  COALESCE(option_item.option_label, option_item.option_value),
  option_item.style_type,
  COALESCE(option_item.disabled, 0),
  option_item.ordinality,
  CAST(option_item.option_document AS CHAR CHARACTER SET utf8mb4)
FROM entity_field field
JOIN JSON_TABLE(
  field.options_json,
  '$[*]' COLUMNS (
    ordinality FOR ORDINALITY,
    option_value VARCHAR(500) PATH '$.value',
    option_label VARCHAR(500) PATH '$.label' NULL ON EMPTY,
    style_type VARCHAR(50) PATH '$.type' NULL ON EMPTY,
    disabled BOOLEAN PATH '$.disabled' DEFAULT 'false' ON EMPTY,
    option_document JSON PATH '$'
  )
) option_item
WHERE field.options_json IS NOT NULL
  AND JSON_VALID(field.options_json) = 1
  AND option_item.option_value IS NOT NULL;

INSERT IGNORE INTO process_node_approval_option (
  id, approval_config_id, option_value, option_label, style_type,
  show_comment, remark_required, sort_order, option_document
)
SELECT
  SHA2(CONCAT('process-approval-option:', approval.id, ':', option_item.option_value), 256),
  approval.id,
  option_item.option_value,
  COALESCE(option_item.option_label, option_item.option_value),
  option_item.style_type,
  COALESCE(option_item.show_comment, 1),
  COALESCE(option_item.remark_required, 0),
  option_item.ordinality,
  CAST(option_item.option_document AS CHAR CHARACTER SET utf8mb4)
FROM process_node_approval approval
JOIN JSON_TABLE(
  approval.options_json,
  '$[*]' COLUMNS (
    ordinality FOR ORDINALITY,
    option_value VARCHAR(100) PATH '$.value',
    option_label VARCHAR(200) PATH '$.label' NULL ON EMPTY,
    style_type VARCHAR(50) PATH '$.type' NULL ON EMPTY,
    show_comment BOOLEAN PATH '$.showComment' DEFAULT 'true' ON EMPTY,
    remark_required BOOLEAN PATH '$.remarkRequired' DEFAULT 'false' ON EMPTY,
    option_document JSON PATH '$'
  )
) option_item
WHERE approval.options_json IS NOT NULL
  AND JSON_VALID(approval.options_json) = 1
  AND option_item.option_value IS NOT NULL;

INSERT IGNORE INTO process_action_definition_entity (
  id, action_definition_id, entity_code
)
SELECT
  SHA2(CONCAT('process-action-definition-entity:', definition.id, ':', visible.entity_code), 256),
  definition.id,
  visible.entity_code
FROM process_action_definition definition
JOIN JSON_TABLE(
  definition.entity_codes_json,
  '$[*]' COLUMNS (
    entity_code VARCHAR(100) PATH '$'
  )
) visible
WHERE definition.entity_codes_json IS NOT NULL
  AND JSON_VALID(definition.entity_codes_json) = 1
  AND visible.entity_code IS NOT NULL;

INSERT IGNORE INTO config_migration_asset_dependency (
  id, asset_id, dependency_type, dependency_key, required,
  source_description, dependency_document
)
SELECT
  SHA2(CONCAT('config-asset-dependency:', asset.id, ':',
      dependency.dependency_type, ':', dependency.dependency_key), 256),
  asset.id,
  dependency.dependency_type,
  dependency.dependency_key,
  COALESCE(dependency.required_flag, 1),
  dependency.source_description,
  CAST(dependency.dependency_document AS CHAR CHARACTER SET utf8mb4)
FROM config_migration_asset asset
JOIN JSON_TABLE(
  asset.dependencies_json,
  '$[*]' COLUMNS (
    dependency_type VARCHAR(50) PATH '$.type',
    dependency_key VARCHAR(300) PATH '$.key',
    required_flag BOOLEAN PATH '$.required' DEFAULT 'true' ON EMPTY,
    source_description VARCHAR(500) PATH '$.source' NULL ON EMPTY,
    dependency_document JSON PATH '$'
  )
) dependency
WHERE asset.dependencies_json IS NOT NULL
  AND JSON_VALID(asset.dependencies_json) = 1
  AND dependency.dependency_type IS NOT NULL
  AND dependency.dependency_key IS NOT NULL;

INSERT IGNORE INTO process_task_candidate_user (
  id, task_instance_id, user_id, sort_order
)
SELECT
  SHA2(CONCAT('process-task-candidate-user:', task.id, ':', candidate.user_id), 256),
  task.id,
  candidate.user_id,
  candidate.ordinality
FROM process_task_instance task
JOIN JSON_TABLE(
  task.candidate_users,
  '$[*]' COLUMNS (
    ordinality FOR ORDINALITY,
    user_id VARCHAR(100) PATH '$'
  )
) candidate
WHERE task.candidate_users IS NOT NULL
  AND JSON_VALID(task.candidate_users) = 1
  AND JSON_TYPE(task.candidate_users) = 'ARRAY';

INSERT IGNORE INTO process_task_candidate_group (
  id, task_instance_id, group_code, sort_order
)
SELECT
  SHA2(CONCAT('process-task-candidate-group:', task.id, ':', candidate.group_code), 256),
  task.id,
  candidate.group_code,
  candidate.ordinality
FROM process_task_instance task
JOIN JSON_TABLE(
  task.candidate_groups,
  '$[*]' COLUMNS (
    ordinality FOR ORDINALITY,
    group_code VARCHAR(100) PATH '$'
  )
) candidate
WHERE task.candidate_groups IS NOT NULL
  AND JSON_VALID(task.candidate_groups) = 1
  AND JSON_TYPE(task.candidate_groups) = 'ARRAY';

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_convert_json_columns_v025$$
CREATE PROCEDURE workflow_convert_json_columns_v025()
BEGIN
    DECLARE v_done INT DEFAULT 0;
    DECLARE v_table VARCHAR(128);
    DECLARE v_column VARCHAR(128);
    DECLARE v_nullable VARCHAR(3);
    DECLARE v_comment TEXT;
    DECLARE json_cursor CURSOR FOR
        SELECT table_name, column_name, is_nullable, column_comment
        FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND data_type = 'json'
          AND table_name NOT LIKE 'ACT\\_%'
        ORDER BY table_name, ordinal_position;
    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN json_cursor;
    conversion_loop: LOOP
        FETCH json_cursor INTO v_table, v_column, v_nullable, v_comment;
        IF v_done = 1 THEN
            LEAVE conversion_loop;
        END IF;

        SET @audit_sql = CONCAT(
            'INSERT INTO system_json_document_migration_log (',
            'id, table_name, column_name, source_type, target_type, total_rows, ',
            'non_null_rows, invalid_rows, max_document_length, status, message) ',
            'SELECT SHA2(CONCAT(''json-document:'', ', QUOTE(v_table), ', '':'', ',
            QUOTE(v_column), '), 256), ',
            QUOTE(v_table), ', ', QUOTE(v_column), ', ''JSON'', ''PORTABLE_TEXT'', ',
            'COUNT(*), COALESCE(SUM(`', REPLACE(v_column, '`', '``'),
            '` IS NOT NULL), 0), 0, ',
            'MAX(CHAR_LENGTH(CAST(`', REPLACE(v_column, '`', '``'), '` AS CHAR))), ',
            '''SUCCESS'', ''Converted by V025'' FROM `',
            REPLACE(v_table, '`', '``'), '` ',
            'ON DUPLICATE KEY UPDATE total_rows=VALUES(total_rows), ',
            'non_null_rows=VALUES(non_null_rows), ',
            'max_document_length=VALUES(max_document_length), ',
            'status=''SUCCESS'', message=''Converted by V025'', migrated_at=NOW()'
        );
        PREPARE audit_stmt FROM @audit_sql;
        EXECUTE audit_stmt;
        DEALLOCATE PREPARE audit_stmt;

        SET @alter_sql = CONCAT(
            'ALTER TABLE `', REPLACE(v_table, '`', '``'), '` MODIFY COLUMN `',
            REPLACE(v_column, '`', '``'), '` LONGTEXT ',
            IF(v_nullable = 'NO', 'NOT NULL', 'NULL'),
            IF(v_comment IS NULL OR v_comment = '', '',
               CONCAT(' COMMENT ''', REPLACE(v_comment, '''', ''''''), ''''))
        );
        PREPARE alter_stmt FROM @alter_sql;
        EXECUTE alter_stmt;
        DEALLOCATE PREPARE alter_stmt;
    END LOOP;
    CLOSE json_cursor;
END$$

DELIMITER ;

CALL workflow_convert_json_columns_v025();
DROP PROCEDURE IF EXISTS workflow_convert_json_columns_v025;

SET @remaining_native_json = (
  SELECT COUNT(*)
  FROM information_schema.columns
  WHERE table_schema = DATABASE()
    AND data_type = 'json'
    AND table_name NOT LIKE 'ACT\\_%'
);

SET @json_assert_sql = IF(
  @remaining_native_json = 0,
  'SELECT 1',
  'SIGNAL SQLSTATE ''45000'' SET MESSAGE_TEXT = ''V025执行后仍存在非Flowable原生JSON字段'''
);
PREPARE json_assert_stmt FROM @json_assert_sql;
EXECUTE json_assert_stmt;
DEALLOCATE PREPARE json_assert_stmt;
