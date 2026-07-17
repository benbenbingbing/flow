package com.workflow.service.listfield;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityListField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ListFieldDataProviderRegistryTest {

    @Test
    void exposesProviderMetadataAndValidatesRequiredConfig() {
        ListFieldDataProvider provider = provider("CUSTOM_SCORE");
        ListFieldDataProviderRegistry registry = new ListFieldDataProviderRegistry(
                List.of(provider),
                new ObjectMapper());

        assertTrue(registry.supports("custom_score"));
        assertEquals("CUSTOM_SCORE", registry.getOptions().get(1).getValue());

        EntityListField field = new EntityListField();
        field.setDataSourceType("CUSTOM_SCORE");
        field.setDataSourceConfig("{}");
        assertThrows(IllegalArgumentException.class, () -> registry.validate(field));

        field.setDataSourceConfig("{\"sourceField\":\"amount\"}");
        registry.validate(field);
    }

    @Test
    void rejectsDuplicateProviderKeys() {
        assertThrows(
                IllegalStateException.class,
                () -> new ListFieldDataProviderRegistry(
                        List.of(provider("CUSTOM_SCORE"), provider("custom_score")),
                        new ObjectMapper()));
    }

    private ListFieldDataProvider provider(String key) {
        return new ListFieldDataProvider() {
            @Override
            public String getDataSourceType() {
                return key;
            }

            @Override
            public List<Map<String, Object>> getConfigSchema() {
                return List.of(Map.of(
                        "key", "sourceField",
                        "label", "来源字段",
                        "type", "text",
                        "required", true));
            }

            @Override
            public void enrich(
                    List<EntityDataDTO> records,
                    List<EntityListField> fields,
                    Map<String, Object> context) {
            }
        };
    }
}
