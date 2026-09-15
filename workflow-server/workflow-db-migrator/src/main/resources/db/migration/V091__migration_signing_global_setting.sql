-- 迁移签名密钥仅保存在系统全局设置；每个数据库初始化独立随机值，不读取环境变量。
-- 使用既有通用文本列的 JSON 字符串协议，不提供公开固定默认密钥。
INSERT INTO sys_global_setting
    (id, scope_type, owner_id, setting_key, name, setting_value_type, setting_value, remark)
SELECT 'setting_migration_signing_key', 'SYSTEM', '0', 'config.migration.signing_key',
       '配置迁移签名密钥', 'STRING', JSON_QUOTE(LOWER(HEX(RANDOM_BYTES(32)))),
       '用于迁移包 HMAC-SHA256 签名与验签；修改后立即生效，已生成的包保留原签名。仅系统级维护，不回显密钥。'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_global_setting
    WHERE scope_type = 'SYSTEM' AND owner_id = '0' AND setting_key = 'config.migration.signing_key'
);

-- 独立保存验签结论与确认记录，避免依赖分析覆盖来源确认信息。
-- 旧记录无法推断当时的验签证据，因此明确标记 UNKNOWN。
ALTER TABLE config_import_package
    ADD COLUMN signature_status varchar(32) NOT NULL DEFAULT 'UNKNOWN'
        COMMENT '来源校验：VERIFIED-签名通过 MISMATCH_CONFIRMED-人工确认 UNKNOWN-未记录',
    ADD COLUMN signature_confirmed_by varchar(100) DEFAULT NULL COMMENT '签名不匹配时确认来源的操作人',
    ADD COLUMN signature_confirmed_at datetime(6) DEFAULT NULL COMMENT '签名不匹配时人工确认时间';
