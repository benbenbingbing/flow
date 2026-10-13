package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.record.UiExtensionDefinition;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import java.util.Map;
import static org.mockito.Mockito.*;

/** 发布从唯一 componentType 身份解析扩展版本和适用范围，不读取额外的节点扩展字段。 */
class UnifiedFormComponentReleaseTest {
    @Test
    void publishesCustomComponentsThroughFieldDefinitionAndSkipsBuiltIns() {
        ObjectMapper mapper = new ObjectMapper();
        UiConfigReleaseService service = mock(UiConfigReleaseService.class, CALLS_REAL_METHODS);
        UiExtensionDefinitionService extensions = mock(UiExtensionDefinitionService.class);
        UiExtensionDefinition definition = new UiExtensionDefinition();
        when(extensions.requireActive("FIELD", "project-review", 2)).thenReturn(definition);
        ReflectionTestUtils.setField(service, "objectMapper", mapper);
        ReflectionTestUtils.setField(service, "codec", new JsonDocumentCodec(mapper));
        ReflectionTestUtils.setField(service, "extensionDefinitionService", extensions);
        Map<String, Object> snapshot = Map.of("form", Map.of("id", "f"), "nodes", List.of(
                Map.of("nodeType", "FIELD", "bindingType", "ENTITY_FIELD", "componentVersion", 2, "snapshotVersion", 1,
                        "propsDocument", "{\"componentType\":\"project-review\",\"fieldType\":\"STRING\"}"),
                Map.of("nodeType", "FIELD", "bindingType", "ENTITY_FIELD",
                        "propsDocument", "{\"componentType\":\"input\",\"fieldType\":\"STRING\"}")));
        ReflectionTestUtils.invokeMethod(service, "validateExtensionReferences", snapshot);
        verify(extensions).requireActive("FIELD", "project-review", 2);
        verify(extensions).validateCompatibility(definition, null, "FIELD", "ENTITY_FIELD", 1);
        verify(extensions).validateFieldType(definition, "STRING");
        verifyNoMoreInteractions(extensions);
    }
}
