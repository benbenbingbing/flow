package com.workflow.entity.list.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 旧发布和导入包仍可读取，但已退役配置不能借恢复、复制或再发布重新进入存储。 */
class EntityListRetiredContextBindingTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsHistoricalListWithoutReintroducingRetiredMetadata() throws Exception {
        EntityListConfigDTO list = objectMapper.readValue("""
                {"listKey":"all","contextBindingConfig":{"parentField":"project_id"},
                 "fixedFilterConfig":{"status":"APPROVED"},
                 "selectionConfig":{"selectionMode":"SINGLE"}}
                """, EntityListConfigDTO.class);

        JsonNode serialized = objectMapper.valueToTree(list);
        assertEquals("all", list.getListKey());
        assertFalse(serialized.has("contextBindingConfig"));
        assertEquals("APPROVED", serialized.path("fixedFilterConfig").path("status").asText());
        assertEquals("SINGLE", serialized.path("selectionConfig").path("selectionMode").asText());
    }

    @Test
    void onlyIgnoresTheExplicitlyRetiredField() {
        assertThrows(UnrecognizedPropertyException.class, () -> objectMapper.readValue(
                "{\"unknownListSetting\":true}", EntityListConfigDTO.class));
    }
}
