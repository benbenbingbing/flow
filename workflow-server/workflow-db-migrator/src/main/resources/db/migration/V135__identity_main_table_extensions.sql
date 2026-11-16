-- 用户、组织主表预留五个可查询文本槽位，具体用途和外部字段映射由接入业务约定。
-- MySQL 兼容存量迁移，由 Flyway 执行一次；不从历史原文猜测映射，不新增索引或种子数据。
ALTER TABLE sys_user ADD COLUMN ext1 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段1：供接入业务约定用途，保存来源映射后的文本，用于主表查询和关联。';
ALTER TABLE sys_user ADD COLUMN ext2 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段2：供接入业务约定用途，保存来源映射后的文本，用于主表查询和关联。';
ALTER TABLE sys_user ADD COLUMN ext3 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段3：供接入业务约定用途，保存来源映射后的文本，用于主表查询和关联。';
ALTER TABLE sys_user ADD COLUMN ext4 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段4：供接入业务约定用途，保存来源映射后的文本，用于主表查询和关联。';
ALTER TABLE sys_user ADD COLUMN ext5 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段5：供接入业务约定用途，保存来源映射后的文本，用于主表查询和关联。';
ALTER TABLE sys_organization ADD COLUMN ext1 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段1：供接入业务约定用途，保存来源映射后的文本，用于主表查询和关联。';
ALTER TABLE sys_organization ADD COLUMN ext2 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段2：供接入业务约定用途，保存来源映射后的文本，用于主表查询和关联。';
ALTER TABLE sys_organization ADD COLUMN ext3 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段3：供接入业务约定用途，保存来源映射后的文本，用于主表查询和关联。';
ALTER TABLE sys_organization ADD COLUMN ext4 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段4：供接入业务约定用途，保存来源映射后的文本，用于主表查询和关联。';
ALTER TABLE sys_organization ADD COLUMN ext5 VARCHAR(500) DEFAULT NULL
    COMMENT '扩展字段5：供接入业务约定用途，保存来源映射后的文本，用于主表查询和关联。';
