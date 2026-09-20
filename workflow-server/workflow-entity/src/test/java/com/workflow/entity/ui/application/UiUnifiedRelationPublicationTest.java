package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;

/** 新发布不再接受展示实体字段或不绑定关系的主从节点，导入草稿也不能绕过。 */
class UiUnifiedRelationPublicationTest {
    @Test
    void publicationRequiresRelationBoundComponents() {
        UiConfigReleaseService service = mock(UiConfigReleaseService.class, CALLS_REAL_METHODS);
        ObjectMapper mapper = new ObjectMapper();
        ReflectionTestUtils.setField(service, "objectMapper", mapper);
        ReflectionTestUtils.setField(service, "codec", new JsonDocumentCodec(mapper));
        for (String type : List.of("SUB_FORM", "REPEATER")) {
            for (String binding : List.of("ENTITY_FIELD", "NONE")) {
                assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(service,
                        "validateUnifiedRelationComponents", Map.of("nodes", List.of(Map.of(
                                "nodeType", type, "bindingType", binding, "bindingRef", "details")))));
            }
            assertDoesNotThrow(() -> ReflectionTestUtils.invokeMethod(service,
                    "validateUnifiedRelationComponents", Map.of("nodes", List.of(Map.of(
                            "nodeType", type, "bindingType", "RELATION", "bindingRef", "details")))));
        }
        assertThrows(IllegalArgumentException.class, () -> ReflectionTestUtils.invokeMethod(service,
                "validateUnifiedRelationComponents", Map.of("nodes", List.of(Map.of(
                        "nodeType", "FIELD", "bindingType", "ENTITY_FIELD", "bindingRef", "old_list",
                        "propsDocument", "{\"fieldType\":\"SUB_LIST\",\"componentType\":\"sub_list\"}")))));
    }
}
