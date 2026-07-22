-- 表单与列表增量配置、草稿发布、统一数据源和版本化组件模板。

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing_v030$$
CREATE PROCEDURE workflow_add_column_if_missing_v030(
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

CALL workflow_add_column_if_missing_v030(
  'entity_form',
  'revision',
  'int NOT NULL DEFAULT 1 COMMENT ''草稿元数据修订号'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_form',
  'active_release_id',
  'varchar(64) DEFAULT NULL COMMENT ''当前激活发布快照ID'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_form',
  'draft_hash',
  'varchar(64) DEFAULT NULL COMMENT ''当前草稿内容哈希'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_config',
  'revision',
  'int NOT NULL DEFAULT 1 COMMENT ''草稿元数据修订号'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_config',
  'active_release_id',
  'varchar(64) DEFAULT NULL COMMENT ''当前激活发布快照ID'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_config',
  'draft_hash',
  'varchar(64) DEFAULT NULL COMMENT ''当前草稿内容哈希'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_config',
  'query_data_source_id',
  'varchar(64) DEFAULT NULL COMMENT ''统一列表查询数据源ID'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_field',
  'revision',
  'int NOT NULL DEFAULT 1 COMMENT ''字段草稿修订号'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_field',
  'order_key',
  'bigint NOT NULL DEFAULT 1000000 COMMENT ''稀疏排序键'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_field',
  'data_source_id',
  'varchar(64) DEFAULT NULL COMMENT ''统一数据源ID'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_field',
  'template_id',
  'varchar(64) DEFAULT NULL COMMENT ''来源模板ID'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_field',
  'template_version',
  'int DEFAULT NULL COMMENT ''锁定模板版本'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_field',
  'local_overrides_document',
  'longtext DEFAULT NULL COMMENT ''模板实例本地覆盖JSON文档'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_action',
  'revision',
  'int NOT NULL DEFAULT 1 COMMENT ''按钮草稿修订号'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_action',
  'order_key',
  'bigint NOT NULL DEFAULT 1000000 COMMENT ''稀疏排序键'''
);
CALL workflow_add_column_if_missing_v030(
  'entity_list_scene',
  'revision',
  'int NOT NULL DEFAULT 1 COMMENT ''场景草稿修订号'''
);

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing_v030;

CREATE TABLE IF NOT EXISTS entity_form_node (
  id varchar(64) NOT NULL COMMENT '稳定节点ID',
  form_id varchar(64) NOT NULL COMMENT '表单ID',
  parent_id varchar(64) DEFAULT NULL COMMENT '父节点ID',
  node_key varchar(100) NOT NULL COMMENT '表单内稳定节点编码',
  node_type varchar(30) NOT NULL COMMENT 'SECTION/GRID/TAB_SET/TAB/COLLAPSE/TEXT/FIELD/SUB_FORM/REPEATER/ACTION_SLOT',
  binding_type varchar(30) NOT NULL DEFAULT 'NONE' COMMENT 'ENTITY_FIELD/RELATION/COMPUTED/CONTEXT/NONE',
  binding_ref varchar(200) DEFAULT NULL COMMENT '字段、关系或上下文引用',
  props_document longtext DEFAULT NULL COMMENT '节点显式属性JSON文档',
  rules_document longtext DEFAULT NULL COMMENT '校验、显隐和权限规则JSON文档',
  data_source_bindings_document longtext DEFAULT NULL COMMENT '节点数据源绑定JSON文档',
  legacy_props_document longtext DEFAULT NULL COMMENT '无法识别的历史属性JSON文档',
  order_key bigint NOT NULL DEFAULT 1000000 COMMENT '稀疏排序键',
  revision int NOT NULL DEFAULT 1 COMMENT '节点草稿修订号',
  template_id varchar(64) DEFAULT NULL COMMENT '来源模板ID',
  template_version int DEFAULT NULL COMMENT '锁定模板版本',
  local_overrides_document longtext DEFAULT NULL COMMENT '模板实例本地覆盖JSON文档',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY idx_entity_form_node_key (form_id, node_key, deleted),
  KEY idx_entity_form_node_tree (form_id, parent_id, order_key, deleted),
  KEY idx_entity_form_node_binding (form_id, binding_type, binding_ref, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='实体表单递归草稿节点';

CREATE TABLE IF NOT EXISTS ui_config_release (
  id varchar(64) NOT NULL COMMENT '发布快照ID',
  config_type varchar(20) NOT NULL COMMENT 'FORM/LIST',
  config_id varchar(64) NOT NULL COMMENT '表单或列表配置ID',
  version int NOT NULL COMMENT '不可变版本号',
  snapshot_document longtext NOT NULL COMMENT '完整运行时快照JSON文档',
  content_hash varchar(64) NOT NULL COMMENT 'SHA-256内容哈希',
  status varchar(20) NOT NULL DEFAULT 'INACTIVE' COMMENT 'ACTIVE/INACTIVE',
  description varchar(500) DEFAULT NULL COMMENT '发布说明',
  published_by varchar(64) DEFAULT NULL COMMENT '发布人',
  published_at datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_ui_config_release_version (config_type, config_id, version),
  KEY idx_ui_config_release_active (config_type, config_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='表单与列表不可变发布快照';

CREATE TABLE IF NOT EXISTS ui_data_source_definition (
  id varchar(64) NOT NULL COMMENT '数据源ID',
  source_code varchar(100) NOT NULL COMMENT '稳定编码',
  source_name varchar(200) NOT NULL COMMENT '名称',
  source_type varchar(30) NOT NULL COMMENT 'ENTITY_QUERY/DICTIONARY/STATIC_OPTIONS/REGISTERED_PROVIDER/INTEGRATION_CONNECTOR/RUNTIME_CONTEXT/STRUCTURED_COMPUTE',
  provider_code varchar(100) DEFAULT NULL COMMENT 'Provider或Connector注册编码',
  scope_type varchar(20) NOT NULL DEFAULT 'GLOBAL' COMMENT 'GLOBAL/ENTITY/FORM/LIST',
  scope_id varchar(64) DEFAULT NULL COMMENT '作用域资源ID',
  config_document longtext DEFAULT NULL COMMENT '受控配置JSON文档',
  input_schema_document longtext DEFAULT NULL COMMENT '输入Schema JSON文档',
  output_schema_document longtext DEFAULT NULL COMMENT '输出Schema JSON文档',
  execution_policy_document longtext DEFAULT NULL COMMENT '分页、超时、缓存和失败策略JSON文档',
  revision int NOT NULL DEFAULT 1 COMMENT '修订号',
  enabled tinyint NOT NULL DEFAULT 1 COMMENT '是否启用',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ui_data_source_code (source_code, deleted),
  KEY idx_ui_data_source_catalog (source_type, scope_type, scope_id, enabled, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='受控UI数据源目录';

CREATE TABLE IF NOT EXISTS ui_component_template (
  id varchar(64) NOT NULL COMMENT '模板ID',
  template_key varchar(100) NOT NULL COMMENT '稳定模板编码',
  template_name varchar(200) NOT NULL COMMENT '模板名称',
  template_type varchar(30) NOT NULL COMMENT 'FIELD_GROUP/FORM_SECTION/SUB_FORM/LIST_COLUMN_GROUP/BUTTON_GROUP',
  current_version int NOT NULL DEFAULT 1 COMMENT '当前版本',
  status varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ui_component_template_key (template_key, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='版本化UI组件模板';

CREATE TABLE IF NOT EXISTS ui_component_template_version (
  id varchar(64) NOT NULL COMMENT '模板版本ID',
  template_id varchar(64) NOT NULL COMMENT '模板ID',
  version int NOT NULL COMMENT '版本号',
  snapshot_document longtext NOT NULL COMMENT '不可变模板快照JSON文档',
  content_hash varchar(64) NOT NULL COMMENT 'SHA-256内容哈希',
  description varchar(500) DEFAULT NULL COMMENT '版本说明',
  created_by varchar(64) DEFAULT NULL COMMENT '创建人',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ui_component_template_version (template_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='UI组件模板不可变版本';

UPDATE entity_list_field
SET order_key = (sort_order + 1) * 1000000
WHERE order_key = 1000000;

UPDATE entity_list_action
SET order_key = (sort_order + 1) * 1000000
WHERE order_key = 1000000;
