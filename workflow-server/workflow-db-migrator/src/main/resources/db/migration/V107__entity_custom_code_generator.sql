-- 存量实体继续使用内置规则；自定义实现通过稳定标识绑定，参数不包含运行期序号。
ALTER TABLE entity_code_rule
    ADD COLUMN generation_mode VARCHAR(20) NOT NULL DEFAULT 'RULE',
    ADD COLUMN generator_code VARCHAR(64) NULL,
    ADD COLUMN generator_config TEXT NULL;
