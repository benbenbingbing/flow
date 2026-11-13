-- MySQL 兼容的存量库迁移：为定制代码预留五个可选文本字段，不回填已有字典项。
-- 由 Flyway 按版本执行一次；这里使用普通 ADD COLUMN，不提供重复手工执行或逐列跳过逻辑。
-- 字段含义、类型转换和业务校验由定制模块约定，通用字典只保存和读取文本。
ALTER TABLE sys_dict_item ADD COLUMN ext1 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段1：供定制模块约定用途，通用字典仅保存文本，不解释业务含义。';
ALTER TABLE sys_dict_item ADD COLUMN ext2 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段2：供定制模块约定用途，通用字典仅保存文本，不解释业务含义。';
ALTER TABLE sys_dict_item ADD COLUMN ext3 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段3：供定制模块约定用途，通用字典仅保存文本，不解释业务含义。';
ALTER TABLE sys_dict_item ADD COLUMN ext4 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段4：供定制模块约定用途，通用字典仅保存文本，不解释业务含义。';
ALTER TABLE sys_dict_item ADD COLUMN ext5 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段5：供定制模块约定用途，通用字典仅保存文本，不解释业务含义。';
