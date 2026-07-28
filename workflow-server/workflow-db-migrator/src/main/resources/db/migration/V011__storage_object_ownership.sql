CREATE TABLE storage_file_object (
  id VARCHAR(32) NOT NULL,
  storage_url VARCHAR(1024) NOT NULL,
  storage_key VARCHAR(512) NOT NULL,
  owner_user_id VARCHAR(64) NOT NULL,
  original_name VARCHAR(512) NULL,
  content_type VARCHAR(255) NULL,
  content_length BIGINT NOT NULL DEFAULT 0,
  deleted TINYINT NOT NULL DEFAULT 0,
  create_time DATETIME(6) NOT NULL,
  update_time DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uk_storage_file_url (storage_url(768)),
  KEY idx_storage_file_owner (owner_user_id, deleted, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
