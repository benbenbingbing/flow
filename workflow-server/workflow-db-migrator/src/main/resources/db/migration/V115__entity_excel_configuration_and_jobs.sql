-- 默认指针与方案分离，所有配置变更在 entity_definition 行锁内串行校验。
CREATE TABLE entity_excel_settings (
    entity_id VARCHAR(64) NOT NULL PRIMARY KEY,
    draft_json LONGTEXT NOT NULL,
    published_json LONGTEXT NULL,
    revision INT NOT NULL DEFAULT 0,
    import_hash CHAR(64) NULL,
    export_hash CHAR(64) NULL,
    published_by VARCHAR(64) NULL,
    published_at DATETIME NULL,
    updated_at DATETIME NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE entity_excel_scheme (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    entity_id VARCHAR(64) NOT NULL,
    scheme_code VARCHAR(100) NOT NULL,
    name VARCHAR(200) NOT NULL,
    draft_json LONGTEXT NOT NULL,
    revision INT NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_excel_scheme_entity_code (entity_id, scheme_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE entity_excel_scheme_published (
    scheme_id VARCHAR(64) NOT NULL PRIMARY KEY,
    document_json LONGTEXT NOT NULL,
    content_hash CHAR(64) NOT NULL,
    write_form_id VARCHAR(64) NULL,
    published_by VARCHAR(64) NULL,
    published_at DATETIME NOT NULL,
    KEY idx_excel_scheme_write_form (write_form_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 同一用户在同一入口的 requestId 唯一；payload_hash 再验证文件及参数相同，防止重试重复新增。
CREATE TABLE entity_excel_job (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    entity_id VARCHAR(64) NOT NULL,
    entity_code VARCHAR(100) NOT NULL,
    operation VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    origin_key VARCHAR(500) NOT NULL,
    request_id VARCHAR(128) NOT NULL,
    payload_hash CHAR(64) NOT NULL,
    config_json LONGTEXT NOT NULL,
    input_file_id VARCHAR(64) NULL,
    output_file_id VARCHAR(64) NULL,
    error_file_id VARCHAR(64) NULL,
    file_name VARCHAR(255) NULL,
    content_type VARCHAR(150) NULL,
    counters_json LONGTEXT NULL,
    error_message LONGTEXT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    UNIQUE KEY uk_excel_job_request (user_id, origin_key, request_id),
    KEY idx_excel_job_created (created_at),
    KEY idx_excel_job_user (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE entity_excel_job_row (
    id VARCHAR(64) NOT NULL PRIMARY KEY,
    job_id VARCHAR(64) NOT NULL,
    sheet_no INT NOT NULL,
    excel_row_number INT NOT NULL,
    data_json LONGTEXT NULL,
    errors_json LONGTEXT NULL,
    UNIQUE KEY uk_excel_job_row (job_id, sheet_no, excel_row_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
