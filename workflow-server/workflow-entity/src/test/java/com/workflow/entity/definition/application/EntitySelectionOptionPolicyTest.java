package com.workflow.entity.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.api.response.EntityFieldDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EntitySelectionOptionPolicyTest {
    @ParameterizedTest
    @EnumSource(value = EntityField.FieldType.class, names = {"SELECT", "MULTI_SELECT", "RADIO", "CHECKBOX"})
    void selectionDefinitionsAndPublicationRequireDictionary(EntityField.FieldType type) {
        EntityFieldDTO submitted = new EntityFieldDTO();
        submitted.setFieldType(type);
        assertThrows(IllegalArgumentException.class, () -> EntitySelectionOptionPolicy.validateSubmitted(submitted));
        submitted.setDictType("choices");
        assertDoesNotThrow(() -> EntitySelectionOptionPolicy.validateSubmitted(submitted));
        EntityField stored = new EntityField();
        stored.setFieldType(type);
        stored.setOptionsJson("[{\"value\":\"old\",\"label\":\"历史项\"}]");
        assertThrows(IllegalArgumentException.class, () -> EntitySelectionOptionPolicy.validatePublished(stored));
        stored.setDictType("choices");
        assertDoesNotThrow(() -> EntitySelectionOptionPolicy.validatePublished(stored));
        assertNotNull(stored.getOptionsJson(), "策略校验不得清空数据库中的历史数据");
    }

    @Test
    void oldInputIsRejectedEvenWhenDictionaryIsPresentAndNeverSerializedBack() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        EntityFieldDTO submitted = mapper.readValue("""
                {"fieldType":"SELECT","dictType":"choices","optionsJson":"[]"}
                """, EntityFieldDTO.class);
        assertThrows(IllegalArgumentException.class, () -> EntitySelectionOptionPolicy.validateSubmitted(submitted));
        submitted.setOptionsJson(null);
        submitted.setOptions(List.of(Map.of("value", "old", "label", "历史项")));
        assertThrows(IllegalArgumentException.class, () -> EntitySelectionOptionPolicy.validateSubmitted(List.of(submitted)));
        String response = mapper.writeValueAsString(submitted);
        assertFalse(response.contains("optionsJson"));
        assertFalse(response.contains("\"options\""));
    }

    @Test
    void formComponentsRejectEmbeddedCandidatesAndAllowDataSourceConfiguration() {
        for (String type : List.of("select", "select_multiple", "RADIO", "CHECKBOX")) {
            assertThrows(IllegalArgumentException.class, () -> EntitySelectionOptionPolicy.rejectEmbeddedOptions(
                    type, Map.of("options", List.of(Map.of("value", "old"))), "choice"));
            assertThrows(IllegalArgumentException.class, () -> EntitySelectionOptionPolicy.rejectEmbeddedOptions(
                    type, Map.of("optionsJson", "[]"), "choice"));
            assertDoesNotThrow(() -> EntitySelectionOptionPolicy.rejectEmbeddedOptions(
                    type, Map.of("dataSourceBindings", Map.of("FIELD_OPTIONS", List.of())), "choice"));
        }
    }
}
