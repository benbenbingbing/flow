-- 表单只保留一份当前发布配置；旧发布记录保留为只读历史，不能再作为运行来源。
CREATE TABLE entity_form_published (
    form_id VARCHAR(64) NOT NULL COMMENT '稳定表单身份，覆盖发布的唯一键',
    entity_id VARCHAR(64) NOT NULL COMMENT '表单所属实体，供运行归属校验',
    snapshot_document LONGTEXT NOT NULL COMMENT '当前完整发布配置，不保存历史版本',
    content_hash CHAR(64) NOT NULL COMMENT '完整性及页面过期检查用内容指纹',
    published_by VARCHAR(64) NULL,
    published_at DATETIME NOT NULL,
    PRIMARY KEY (form_id),
    KEY idx_entity_form_published_entity (entity_id),
    UNIQUE KEY uk_entity_form_published_hash (content_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='表单唯一当前发布配置';

-- 跨实体子表单可以互相引用，发布需统一串行校验以避免并发创建环。
CREATE TABLE entity_form_publication_lock (lock_id TINYINT NOT NULL PRIMARY KEY);
INSERT INTO entity_form_publication_lock(lock_id) VALUES (1);

-- 默认选择单独按实体唯一保存，保存草稿不会改变线上入口选择。
CREATE TABLE entity_form_current_default (
    entity_id VARCHAR(64) NOT NULL PRIMARY KEY,
    form_id VARCHAR(64) NOT NULL
);
