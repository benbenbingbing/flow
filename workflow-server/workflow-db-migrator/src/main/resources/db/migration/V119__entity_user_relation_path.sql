-- 历史部门的真实创建人不可推断，保留 NULL；后续新增由可信用户上下文写入。
ALTER TABLE `sys_organization`
  ADD COLUMN `create_by` varchar(64) DEFAULT NULL COMMENT '创建人用户ID，用于流程关系路径解析';

-- 保留管理员的名称、启用状态等设置，仅扩展内置解析器的配置契约。
UPDATE `process_person_resolver_definition`
SET `extra_param_schema_document` = '{"oneOf":[{"type":"object","additionalProperties":false,"required":["schemaVersion","entityCode","fieldCode"],"properties":{"schemaVersion":{"const":1},"entityCode":{"type":"string","minLength":1,"maxLength":128},"fieldCode":{"type":"string","minLength":1,"maxLength":100,"pattern":"^[A-Za-z][A-Za-z0-9_]{0,99}$"}}},{"type":"object","additionalProperties":false,"required":["schemaVersion","entityCode","path"],"properties":{"schemaVersion":{"const":2},"entityCode":{"type":"string","minLength":1,"maxLength":128},"path":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"object","additionalProperties":false,"required":["type","code"],"properties":{"type":{"enum":["REFERENCE_FIELD","SYSTEM_REFERENCE","RELATION","REVERSE_REFERENCE","PARENT_RELATION"]},"code":{"type":"string","minLength":1,"maxLength":100,"pattern":"^[A-Za-z][A-Za-z0-9_]{0,99}$"},"sourceEntityCode":{"type":"string","minLength":1,"maxLength":128},"targetEntityCode":{"type":"string","minLength":1,"maxLength":128}}}},"versions":{"type":"array","minItems":1,"maxItems":9,"items":{"type":"object","additionalProperties":false,"required":["entityCode"],"properties":{"entityCode":{"type":"string","minLength":1,"maxLength":128},"historyId":{"type":["string","null"]},"schemaHash":{"type":["string","null"]},"systemVersion":{"type":["integer","null"],"enum":[1,null]}}}}}}]}',
    `implementation_version` = 2,
    `revision` = `revision` + 1,
    `update_time` = CURRENT_TIMESTAMP
WHERE `resolver_code` = 'entityUserReferenceField';
