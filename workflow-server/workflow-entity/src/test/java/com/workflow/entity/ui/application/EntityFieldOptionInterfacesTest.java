package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 继承绑定只修改表单运行时投影，不接触接口或表单草稿。 */
class EntityFieldOptionInterfacesTest {
    @Test
    void projectsEntityBindingAndRejectsFormOverride() {
        var codec = new JsonDocumentCodec(new ObjectMapper());
        var fields = mock(EntityFieldMapper.class);
        var interfaces = mock(UiExtensionDefinitionMapper.class);
        var service = new EntityFieldOptionInterfaces(fields, interfaces, codec);
        var field = new EntityField();
        field.setId("field-1");
        field.setFieldCode("supplier");
        field.setOptionsInterfaceId("read-1");
        when(fields.findByEntityId("entity-1")).thenReturn(List.of(field));
        var node = new EntityFormNode();
        node.setNodeType("FIELD");
        node.setBindingType("ENTITY_FIELD");
        node.setBindingRef("field-1");
        node.setPropsDocument("{\"componentType\":\"select\"}");
        var projected = service.inherit("entity-1", List.of(node)).get(0);
        assertNull(node.getDataSourceBindingsDocument());
        assertTrue(projected.getDataSourceBindingsDocument().contains("\"extensionId\":\"read-1\""));
        node.setDataSourceBindingsDocument("{\"FIELD_OPTIONS\":{\"extensionId\":\"other-read\"}}");
        assertThrows(IllegalArgumentException.class, () -> service.inherit("entity-1", List.of(node)));
        verifyNoInteractions(interfaces);
    }
}
