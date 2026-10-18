-- 历史文件导入独立于引擎运行态。来源键使用摘要避免不同数据库字符排序规则合并旧 ID。
CREATE TABLE history_import_batch (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    owner_id VARCHAR(64) NOT NULL,
    name VARCHAR(200) NOT NULL,
    state VARCHAR(30) NOT NULL,
    revision INT NOT NULL,
    plan_json LONGTEXT NOT NULL,
    entities_json LONGTEXT NULL,
    history_json LONGTEXT NULL,
    validation_json LONGTEXT NULL,
    validation_hash VARCHAR(64) NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    KEY idx_history_batch_owner (owner_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE history_import_group (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    batch_id VARCHAR(64) NOT NULL,
    group_key VARCHAR(500) NOT NULL,
    group_hash VARCHAR(64) NOT NULL,
    state VARCHAR(30) NOT NULL,
    result_json LONGTEXT NULL,
    error_message VARCHAR(2000) NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_history_batch_group (batch_id, group_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE entity_import_source_link (
    source_hash VARCHAR(64) NOT NULL PRIMARY KEY,
    source_system VARCHAR(200) NOT NULL,
    source_entity_key VARCHAR(100) NOT NULL,
    source_record_id VARCHAR(500) NOT NULL,
    entity_code VARCHAR(100) NOT NULL,
    entity_record_id VARCHAR(64) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    readonly_flag INT NOT NULL DEFAULT 1,
    batch_id VARCHAR(64) NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_entity_import_target (entity_code, entity_record_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE process_archive_instance (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    source_hash VARCHAR(64) NOT NULL,
    source_system VARCHAR(200) NOT NULL,
    source_instance_id VARCHAR(500) NOT NULL,
    entity_code VARCHAR(100) NOT NULL,
    entity_record_id VARCHAR(64) NOT NULL,
    process_name VARCHAR(200) NOT NULL,
    end_result VARCHAR(40) NOT NULL,
    content_hash VARCHAR(64) NOT NULL,
    document_json LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL,
    UNIQUE KEY uk_process_archive_source (source_hash),
    KEY idx_archive_entity (entity_code, entity_record_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 所有事件作为一个不可变归档文档随实例原子保存，避免节点多次办理丢失。
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, path, component, icon, sort, visible, status, perm, create_time)
VALUES ('history_import_menu', '400', '历史数据导入', 'C', '/system/history-import', 'system/HistoryImport', 'Upload', 17, '0', '0', 'history-import:read', CURRENT_TIMESTAMP);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, sort, visible, status, perm, create_time)
VALUES ('history_import_execute', 'history_import_menu', '执行历史导入', 'F', 1, '0', '0', 'history-import:execute', CURRENT_TIMESTAMP);
INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, sort, visible, status, perm, create_time)
VALUES ('process_archive_read', 'history_import_menu', '查看迁入历史', 'F', 2, '0', '0', 'process:archive:read', CURRENT_TIMESTAMP);
