-- 将已有动态实体的系统提交人字段升级为用户单选关系。
-- 历史发布快照保留原文，其指纹已被流程关系配置引用。
UPDATE entity_field
SET field_type = 'REFERENCE',
    ref_entity_type = 'USER',
    ref_entity_id = (SELECT id FROM entity_definition
                     WHERE entity_code = 'sys_user' AND storage_mode = 'SYSTEM' AND deleted = 0)
WHERE field_code = 'submitterId'
  AND field_type = 'STRING'
  AND is_system = 1
  AND deleted = 0
  AND entity_id IN (SELECT id FROM entity_definition WHERE storage_mode = 'DYNAMIC' AND deleted = 0);
