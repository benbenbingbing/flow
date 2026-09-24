-- 表单节点模板退役：应用模板后的节点属性已经独立保存在 props_document 中。
-- 仅清除绑定元数据，保留实际布局、校验、数据源和组件配置；不改写不可变发布历史及其哈希。
ALTER TABLE entity_form_node
    DROP COLUMN template_id,
    DROP COLUMN template_version,
    DROP COLUMN local_overrides_document;

UPDATE entity_form_node
SET legacy_props_document = JSON_REMOVE(legacy_props_document, '$.inactive.template')
WHERE JSON_VALID(legacy_props_document)
  AND JSON_CONTAINS_PATH(legacy_props_document, 'one', '$.inactive.template');

-- 模板主表、版本表仍被列表列初始化模板使用，不能删除共享表。
DELETE template_version
FROM ui_component_template_version template_version
JOIN ui_component_template template ON template.id = template_version.template_id
WHERE template.template_type IN ('FIELD_GROUP', 'FORM_SECTION', 'SUB_FORM');

DELETE FROM ui_component_template
WHERE template_type IN ('FIELD_GROUP', 'FORM_SECTION', 'SUB_FORM');
