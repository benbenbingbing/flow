package com.workflow.process.assignment.extension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;

/** 人员解析器的 V1/V2 参数 schema；必须与目录升级迁移保持一致。 */
final class EntityUserPathSchema {
    private EntityUserPathSchema() { }
    static Map<String, Object> schema() {
        try {
            return new ObjectMapper().readValue("""
                {"oneOf":[{"type":"object","additionalProperties":false,"required":["schemaVersion","entityCode","fieldCode"],"properties":{"schemaVersion":{"const":1},"entityCode":{"type":"string","minLength":1,"maxLength":128},"fieldCode":{"type":"string","minLength":1,"maxLength":100,"pattern":"^[A-Za-z][A-Za-z0-9_]{0,99}$"}}},{"type":"object","additionalProperties":false,"required":["schemaVersion","entityCode","path"],"properties":{"schemaVersion":{"const":2},"entityCode":{"type":"string","minLength":1,"maxLength":128},"path":{"type":"array","minItems":1,"maxItems":8,"items":{"type":"object","additionalProperties":false,"required":["type","code"],"properties":{"type":{"enum":["REFERENCE_FIELD","SYSTEM_REFERENCE","RELATION","REVERSE_REFERENCE","PARENT_RELATION"]},"code":{"type":"string","minLength":1,"maxLength":100,"pattern":"^[A-Za-z][A-Za-z0-9_]{0,99}$"},"sourceEntityCode":{"type":"string","minLength":1,"maxLength":128},"targetEntityCode":{"type":"string","minLength":1,"maxLength":128}}}},"versions":{"type":"array","minItems":1,"maxItems":9,"items":{"type":"object","additionalProperties":false,"required":["entityCode"],"properties":{"entityCode":{"type":"string","minLength":1,"maxLength":128},"historyId":{"type":["string","null"]},"schemaHash":{"type":["string","null"]},"systemVersion":{"type":["integer","null"],"enum":[1,null]}}}}}}]}
                """, new TypeReference<Map<String, Object>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("内置人员路径 schema 无效", e);
        }
    }
}
