-- 自定义表单、节点和字段组件扩展清单及精确版本绑定。

DELIMITER $$

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing_v031$$
CREATE PROCEDURE workflow_add_column_if_missing_v031(
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

CALL workflow_add_column_if_missing_v031(
  'entity_form',
  'custom_component_version',
  'int DEFAULT NULL COMMENT ''自定义整页表单组件锁定版本'''
);
CALL workflow_add_column_if_missing_v031(
  'entity_form',
  'custom_component_snapshot_version',
  'int DEFAULT NULL COMMENT ''自定义整页表单配置快照版本'''
);
CALL workflow_add_column_if_missing_v031(
  'entity_form_node',
  'component_name',
  'varchar(100) DEFAULT NULL COMMENT ''节点扩展组件注册名'''
);
CALL workflow_add_column_if_missing_v031(
  'entity_form_node',
  'component_version',
  'int DEFAULT NULL COMMENT ''节点扩展组件锁定版本'''
);
CALL workflow_add_column_if_missing_v031(
  'entity_form_node',
  'snapshot_version',
  'int DEFAULT NULL COMMENT ''节点扩展配置快照版本'''
);

DROP PROCEDURE IF EXISTS workflow_add_column_if_missing_v031;

CREATE TABLE IF NOT EXISTS ui_extension_definition (
  id varchar(64) NOT NULL COMMENT '扩展定义ID',
  extension_type varchar(20) NOT NULL COMMENT 'FORM/NODE/FIELD/LIST',
  extension_key varchar(100) NOT NULL COMMENT '前端或后端稳定注册名',
  display_name varchar(200) NOT NULL COMMENT '显示名称',
  version int NOT NULL COMMENT '扩展实现版本',
  snapshot_version int NOT NULL DEFAULT 1 COMMENT '配置快照协议版本',
  supported_modes_document longtext DEFAULT NULL COMMENT '支持的运行模式JSON数组',
  supported_node_types_document longtext DEFAULT NULL COMMENT '支持的节点类型JSON数组',
  supported_bindings_document longtext DEFAULT NULL COMMENT '支持的绑定类型JSON数组',
  config_schema_document longtext DEFAULT NULL COMMENT '配置Schema JSON文档',
  capabilities_document longtext DEFAULT NULL COMMENT '扩展能力声明JSON文档',
  status varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/DISABLED',
  revision int NOT NULL DEFAULT 1 COMMENT '定义修订号',
  create_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted tinyint NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  UNIQUE KEY uk_ui_extension_version (extension_type, extension_key, version, deleted),
  KEY idx_ui_extension_catalog (extension_type, extension_key, status, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='受控UI扩展组件清单';
