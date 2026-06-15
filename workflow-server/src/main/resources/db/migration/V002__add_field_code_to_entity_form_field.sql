-- 为 entity_form_field 添加 field_code 字段，持久化字段编码
-- 解决表单渲染时字段 key 与实体数据字段不匹配导致无法回显的问题
ALTER TABLE `entity_form_field`
    ADD COLUMN `field_code` VARCHAR(100) DEFAULT NULL COMMENT '字段编码（对应 entity_field 的 field_code）' AFTER `field_id`;

-- 初始化历史数据：根据 field_id 回填 field_code
UPDATE `entity_form_field` eff
    JOIN `entity_field` ef ON eff.field_id = ef.id
SET eff.field_code = ef.field_code
WHERE eff.field_code IS NULL;

-- 可选：清理 field_id 已失效（关联实体字段已不存在）的表单字段
-- 请先确认这些字段是否还有用，再执行以下删除
-- DELETE FROM `entity_form_field` WHERE field_id NOT IN (SELECT id FROM `entity_field`);
