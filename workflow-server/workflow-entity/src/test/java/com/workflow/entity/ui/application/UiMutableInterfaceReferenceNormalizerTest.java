package com.workflow.entity.ui.application;

import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UiMutableInterfaceReferenceNormalizerTest {

    @Test
    void normalizesLegacyPairAndStripsPublishedIdentity() {
        UiExtensionDefinitionMapper mapper = mock(
                UiExtensionDefinitionMapper.class);
        UiExtensionDefinition migrated = definition(
                "extension-1", "DISABLED");
        when(mapper.selectById("legacy-service")).thenReturn(null);
        when(mapper.selectOne(any())).thenReturn(migrated);
        UiMutableInterfaceReferenceNormalizer normalizer =
                new UiMutableInterfaceReferenceNormalizer(mapper);

        Map<String, Object> result = normalizer.normalizeBindings(Map.of(
                "FORM_INIT", List.of(Map.of(
                        "serviceId", "legacy-service",
                        "operationCode", "load",
                        "serviceRevision", 7,
                        "executableSnapshot", "{}",
                        "definitionHash", "a".repeat(64),
                        "inputMapping", Map.of("id", "recordId")))));

        Map<?, ?> binding = (Map<?, ?>) ((List<?>) result.get(
                "FORM_INIT")).get(0);
        assertEquals("extension-1", binding.get("extensionId"));
        assertEquals(Map.of("id", "recordId"), binding.get("inputMapping"));
        assertFalse(binding.containsKey("serviceId"));
        assertFalse(binding.containsKey("operationCode"));
        assertFalse(binding.containsKey("executableSnapshot"));
        assertFalse(binding.containsKey("definitionHash"));
    }

    @Test
    void keepsOnlyCurrentExtensionIdentityOnMutableWrite() {
        UiExtensionDefinitionMapper mapper = mock(
                UiExtensionDefinitionMapper.class);
        when(mapper.selectById("extension-1")).thenReturn(
                definition("extension-1", "ACTIVE"));
        UiMutableInterfaceReferenceNormalizer normalizer =
                new UiMutableInterfaceReferenceNormalizer(mapper);

        Map<String, Object> result = normalizer.normalizeBindings(Map.of(
                "FIELD_OPTIONS", Map.of(
                        "extensionId", "extension-1",
                        "extensionKey", "people.options",
                        "extensionRevision", 4,
                        "outputMapping", Map.of("label", "name"))));

        Map<?, ?> binding = (Map<?, ?>) result.get("FIELD_OPTIONS");
        assertEquals("extension-1", binding.get("extensionId"));
        assertEquals(Map.of("label", "name"), binding.get("outputMapping"));
        assertFalse(binding.containsKey("extensionKey"));
        assertFalse(binding.containsKey("extensionRevision"));
    }

    @Test
    void rejectsIncompleteLegacyPair() {
        UiMutableInterfaceReferenceNormalizer normalizer =
                new UiMutableInterfaceReferenceNormalizer(
                        mock(UiExtensionDefinitionMapper.class));

        assertThrows(IllegalArgumentException.class,
                () -> normalizer.normalizeBindings(Map.of(
                        "BEFORE_SUBMIT", Map.of(
                                "serviceId", "legacy-service"))));
    }

    private UiExtensionDefinition definition(String id, String status) {
        UiExtensionDefinition value = new UiExtensionDefinition();
        value.setId(id);
        value.setExtensionType("INTERFACE");
        value.setStatus(status);
        value.setDeleted(0);
        return value;
    }
}
