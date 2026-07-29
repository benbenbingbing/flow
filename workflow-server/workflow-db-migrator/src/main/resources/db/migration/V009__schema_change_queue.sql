CREATE TABLE workflow_schema_change (
    id VARCHAR(36) NOT NULL,
    ddl_hash CHAR(64) NOT NULL,
    ddl_statement MEDIUMTEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt INT NOT NULL DEFAULT 0,
    owner_id VARCHAR(128) DEFAULT NULL,
    lease_token BIGINT NOT NULL DEFAULT 0,
    lease_until DATETIME(6) DEFAULT NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    last_error VARCHAR(1000) DEFAULT NULL,
    create_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    update_time DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    completed_time DATETIME(6) DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_schema_change_hash (ddl_hash),
    KEY idx_schema_change_claim
        (status, next_attempt_at, lease_until, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
