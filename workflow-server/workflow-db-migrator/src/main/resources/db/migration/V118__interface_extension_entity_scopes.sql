-- 接口新配置按实体集合限定可用范围；FORM/LIST 业务上下文继续决定具体调用场景。
ALTER TABLE ui_extension_definition
    ADD COLUMN scope_entity_ids_document LONGTEXT NULL
        COMMENT '接口适用实体ID数组JSON；GLOBAL不限定实体，ENTITY覆盖所选实体内相应上下文的全部页面';

-- 不重写旧 scope_type/scope_id，也不改发布快照：旧配置按原边界读取，编辑保存时转为实体集合。
