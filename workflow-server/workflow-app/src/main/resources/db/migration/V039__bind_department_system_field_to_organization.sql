-- 将动态实体的所属部门系统字段绑定到系统组织实体。
-- 幂等执行：仅修复缺失或不一致的字段元数据，不修改业务记录。

UPDATE entity_field department_field
JOIN entity_definition owner_definition
  ON owner_definition.id = department_field.entity_id
 AND owner_definition.storage_mode = 'DYNAMIC'
JOIN entity_definition organization_definition
  ON organization_definition.entity_code = 'sys_organization'
 AND organization_definition.storage_mode = 'SYSTEM'
SET department_field.field_type = 'REFERENCE',
    department_field.ref_entity_type = 'DEPT',
    department_field.ref_entity_id = organization_definition.id,
    department_field.editable = 1
WHERE department_field.field_code = 'deptId'
  AND department_field.is_system = 1
  AND (
      department_field.field_type <> 'REFERENCE'
      OR department_field.ref_entity_type IS NULL
      OR department_field.ref_entity_type <> 'DEPT'
      OR department_field.ref_entity_id IS NULL
      OR department_field.ref_entity_id <> organization_definition.id
      OR department_field.editable <> 1
  );
