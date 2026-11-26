package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.ui.context.CommonInvocationContext;
import com.workflow.contracts.entity.ui.context.FormInvocationContext;
import com.workflow.contracts.entity.ui.model.EntityDescriptor;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiExtensionDefinitionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 继承绑定只修改表单运行时投影，不接触接口或表单草稿。 */
class EntityFieldOptionInterfacesTest {
    @Test
    void projectsEntityBindingAndRejectsFormOverride() {
        var codec = new JsonDocumentCodec(new ObjectMapper());
        var fields = mock(EntityFieldMapper.class);
        var interfaces = mock(UiExtensionDefinitionMapper.class);
        var service = new EntityFieldOptionInterfaces(fields, interfaces, mock(EntityDefinitionMapper.class), codec);
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

    @Test
    void projectsOnlyDynamicSystemStatusAndPreservesItsStorageType() {
        var codec = new JsonDocumentCodec(new ObjectMapper());
        var fields = mock(EntityFieldMapper.class);
        var entities = mock(EntityDefinitionMapper.class);
        var service = new EntityFieldOptionInterfaces(fields, mock(UiExtensionDefinitionMapper.class), entities, codec);
        var field = new EntityField();
        field.setId("status-1");
        field.setFieldCode("status");
        field.setIsSystem(true);
        field.setEditable(false);
        var entity = new EntityDefinition();
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        when(entities.selectById("entity-1")).thenReturn(entity);
        when(fields.findByEntityId("entity-1")).thenReturn(List.of(field));
        var node = new EntityFormNode();
        node.setNodeType("FIELD");
        node.setBindingType("ENTITY_FIELD");
        node.setBindingRef("status-1");
        node.setPropsDocument("{\"fieldCode\":\"status\",\"fieldType\":\"STRING\",\"componentType\":\"input\"}");

        var projected = service.inherit("entity-1", List.of(node)).get(0);
        assertEquals(Map.of("FIELD_OPTIONS", Map.of("extensionId", EntityStatusOptionsProvider.EXTENSION_ID)),
                codec.readObject(projected.getDataSourceBindingsDocument(), "状态选项"));
        assertEquals(Map.of("fieldCode", "status", "fieldType", "STRING", "componentType", "select", "readonly", true),
                codec.readObject(projected.getPropsDocument(), "状态属性"));
        assertNull(node.getDataSourceBindingsDocument());
        assertTrue(node.getPropsDocument().contains("input"));

        entity.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
        assertSame(node, service.inherit("entity-1", List.of(node)).get(0));
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        field.setIsSystem(false);
        assertSame(node, service.inherit("entity-1", List.of(node)).get(0));
    }

    @Test
    void providerUsesAuthorizedEntityAndReturnsStandardOptions() {
        var statuses = mock(EntityStatusService.class);
        var review = new EntityStatus();
        review.setStatusCode("REVIEW");
        review.setStatusName("复核中");
        var done = new EntityStatus();
        done.setStatusCode("DONE");
        done.setStatusName("已完成");
        when(statuses.findByEntityCode("expense")).thenReturn(List.of(review, done));
        var provider = new EntityStatusOptionsProvider(statuses);

        assertEquals(List.of(Map.of("value", "REVIEW", "label", "复核中"), Map.of("value", "DONE", "label", "已完成")),
                provider.execute(formContext("FIELD_OPTIONS"), null, Map.of(), Map.of("entityCode", "another-entity")));
        verify(statuses).findByEntityCode("expense");
        assertThrows(IllegalArgumentException.class, () -> provider.execute(formContext("FORM_INIT"), null, Map.of(), Map.of()));
        verifyNoMoreInteractions(statuses);
    }

    private static FormInvocationContext formContext(String usage) {
        var common = new CommonInvocationContext(EntityStatusOptionsProvider.EXTENSION_ID, "list", usage,
                "FORM", "form-1", "FIELD", "status", "user-1", null, null, null, null, "release-1", 1, null);
        return new FormInvocationContext(common, new EntityDescriptor("entity-1", "expense", "费用", "DYNAMIC", 1),
                "form-1", "expense_form", "费用表单", "view", "record-1", "status", null, null);
    }
}
