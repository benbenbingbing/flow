INSERT INTO `process_person_resolver_definition` (
  `id`,`resolver_code`,`display_name`,`description`,`bean_name`,
  `implementation_version`,`contract_version`,`supported_usages_document`,
  `extra_param_schema_document`,`dynamic_extra_params`,`enabled`,`revision`,
  `create_time`,`update_time`,`deleted`
) VALUES (
  'person_resolver_entity_user_reference_001',
  'entityUserReferenceField',
  '实体用户关系字段',
  '从流程绑定实体的已发布用户单选或多选关系字段读取人员',
  'entityUserReferenceFieldPersonResolver',
  1,
  1,
  '["ASSIGNEE","CANDIDATE","MULTI_INSTANCE"]',
  '{"type":"object","additionalProperties":false,"required":["schemaVersion","entityCode","fieldCode"],"properties":{"schemaVersion":{"const":1},"entityCode":{"type":"string","minLength":1,"maxLength":128},"fieldCode":{"type":"string","minLength":1,"maxLength":100,"pattern":"^[A-Za-z][A-Za-z0-9_]{0,99}$"}}}',
  0,
  1,
  1,
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP,
  0
);
