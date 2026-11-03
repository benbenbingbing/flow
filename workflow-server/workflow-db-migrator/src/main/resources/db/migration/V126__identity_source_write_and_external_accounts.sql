-- 标准身份写入不负责取数和调度；此表仅保存稳定来源键及写入状态。
ALTER TABLE sys_user ADD COLUMN authentication_mode VARCHAR(32) NOT NULL DEFAULT 'LOCAL_PASSWORD'
    COMMENT '认证策略由框架维护；外部账号不允许本地密码登录或重置';
ALTER TABLE sys_user MODIFY COLUMN password VARCHAR(100) NULL;

CREATE TABLE sys_identity_write_lock (
    lock_key VARCHAR(64) NOT NULL PRIMARY KEY
) COMMENT='身份目录事务锁，覆盖组织树及来源首次创建';
INSERT INTO sys_identity_write_lock (lock_key) VALUES ('directory');

CREATE TABLE sys_identity_source_binding (
    source_key CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    source_code VARCHAR(64) NOT NULL,
    object_type VARCHAR(20) NOT NULL,
    external_id VARCHAR(255) NOT NULL,
    local_id VARCHAR(64) NOT NULL,
    external_account VARCHAR(255) NULL,
    source_version BIGINT NULL,
    payload_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_enabled TINYINT NOT NULL DEFAULT 1,
    local_allowed TINYINT NOT NULL DEFAULT 1,
    binding_status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_identity_source_local (object_type, local_id)
) COMMENT='外部数据对象到产品身份的映射；与 Embed 登录主体绑定分别维护';

-- 来源键哈希按精确 UTF-8 值生成，避免默认字符排序规则合并大小写不同的外部 ID。
UPDATE entity_field SET is_required = 0
WHERE field_code = 'password' AND entity_id IN
    (SELECT id FROM entity_definition WHERE table_name = 'sys_user' AND deleted = 0);
