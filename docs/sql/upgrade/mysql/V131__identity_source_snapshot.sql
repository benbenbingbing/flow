-- MySQL 存量库手工升级：选中目标业务库后执行，连接字符集使用 utf8mb4。
-- 与已提交的 V131 Java 迁移等价，仅补建来源身份最新原文快照表，不回填历史资料。
-- 此文件不属于 Flyway 扫描目录；手工执行不登记迁移历史，后续可由原 Java 迁移正常登记 V131。
-- IF NOT EXISTS 支持重复执行，不修改已有表及其数据，也不自动修复已有表的结构差异。
CREATE TABLE IF NOT EXISTS sys_identity_source_snapshot (
    source_key CHAR(64) NOT NULL COMMENT '关联来源绑定的稳定哈希主键，每个用户或组织仅保留一条原文',
    source_version BIGINT COMMENT '原文对应的来源版本，SERIAL 模式为空',
    payload_hash CHAR(64) NOT NULL COMMENT '原文对应的业务快照哈希，用于追溯映射后的资料',
    raw_json LONGTEXT NOT NULL COMMENT '单条来源 JSON 对象原文，仅在管理端授权后按需读取',
    raw_hash CHAR(64) NOT NULL COMMENT '原文 UTF-8 字节的 SHA-256，用于跳过完全重复的原文写入',
    raw_bytes INTEGER NOT NULL COMMENT '原文 UTF-8 字节数，用于大小校验及管理端展示',
    is_current SMALLINT DEFAULT 1 NOT NULL COMMENT '是否对应当前业务资料，无原文的业务更新后标为过期',
    recorded_at DATETIME(6) NOT NULL COMMENT '实际留存原文的时间，重复同步和过期标记不刷新',
    recorded_by VARCHAR(64) NOT NULL COMMENT '实际留存原文的有效操作人，用于审计追溯',
    CONSTRAINT pk_identity_snapshot PRIMARY KEY (source_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='来源身份最新原文快照';
