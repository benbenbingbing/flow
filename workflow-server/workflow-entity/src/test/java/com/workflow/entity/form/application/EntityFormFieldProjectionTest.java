package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EntityFormFieldProjectionTest {
    private final JsonDocumentCodec codec = new JsonDocumentCodec(new ObjectMapper());
    private final EntityFormFieldProjection projection = new EntityFormFieldProjection(codec);

    @Test
    void newFormProjectsPropertiesAndValidationFromNodes() {
        EntityFormNode node = node();
        node.setPropsDocument("""
                {"fieldId":"amount-id","fieldCode":"amount","label":"金额",
                 "fieldType":"DECIMAL","componentType":"number","required":true,"gridSpan":12,
                 "componentProps":{"precision":2}}
                """);
        node.setRulesDocument("{\"validation\":[{\"min\":1}],\"extension\":{\"edit\":true}}");
        node.setDataSourceBindingsDocument("{\"FIELD_OPTIONS\":{\"serviceId\":\"source-1\"}}");

        EntityFormField field = projection.derive("form-1", List.of(node)).get(0);

        assertEquals("node-1", field.getId());
        assertEquals("amount-id", field.getFieldId());
        assertEquals("金额", field.getFieldLabel());
        assertEquals(1, field.getIsRequired());
        assertEquals(12, field.getGridSpan());
        assertEquals(2, codec.readObject(field.getComponentProps(), "组件").get("precision"));
        assertEquals(1, codec.readArray(field.getValidationRules(), "校验").size());
        assertTrue(field.getDataSourceBindings().containsKey("FIELD_OPTIONS"));
        assertTrue(projection.derive("new-form", List.of()).isEmpty());
    }

    @Test
    void explicitNodeClearsAndFalseValuesWinOverSnapshotFields() {
        EntityFormField previous = field();
        previous.setIsRequired(1);
        previous.setPlaceholder("旧提示");
        previous.setValidationRules("[{\"min\":1}]");
        EntityFormNode node = node();
        node.setPropsDocument("{\"fieldCode\":\"amount\",\"required\":false,\"placeholder\":null}");
        node.setRulesDocument("{\"validation\":null}");

        List<EntityFormNode> restored = projection.materialize("form-1", List.of(previous), List.of(node));
        EntityFormField actual = projection.derive("form-1", restored).get(0);

        assertEquals(0, actual.getIsRequired());
        assertNull(actual.getPlaceholder());
        assertNull(actual.getValidationRules());
        assertEquals("旧提示", previous.getPlaceholder());
        assertFalse(node.getPropsDocument().contains("fieldName"));
    }

    @Test
    void importingFieldOnlySubFormProducesStableNodeAndKeepsRules() {
        EntityFormField field = field();
        field.setId("published-node-id");
        field.setFieldType("SUB_FORM");
        field.setComponentType("sub_form");
        field.setComponentProps("{\"childEntityId\":\"child\",\"refFieldCode\":\"parentId\"}");
        field.setExtensionConfig("{\"modePermissions\":{\"create\":true}}");

        List<EntityFormNode> nodes = projection.materialize("form-1", List.of(field), List.of());
        EntityFormField result = projection.derive("form-1", nodes).get(0);

        assertEquals("published-node-id", nodes.get(0).getId());
        assertEquals("SUB_FORM", nodes.get(0).getNodeType());
        assertEquals("SUB_FORM", result.getFieldType());
        assertEquals("child", codec.readObject(result.getComponentProps(), "组件").get("childEntityId"));
        assertTrue(codec.readObject(result.getExtensionConfig(), "扩展").containsKey("modePermissions"));
    }

    @Test
    void removedFieldIsNotReintroducedIntoAnExistingNodeTree() {
        EntityFormNode node = node();
        node.setNodeKey("another");
        node.setPropsDocument("{\"fieldCode\":\"another\"}");
        EntityFormField removed = field();
        removed.setId("removed");

        List<EntityFormNode> result = projection.materialize("form-1", List.of(removed), List.of(node));

        assertEquals(1, result.size());
        assertEquals("another", projection.derive("form-1", result).get(0).getFieldCode());
    }

    private EntityFormNode node() {
        EntityFormNode node = new EntityFormNode();
        node.setId("node-1");
        node.setFormId("form-1");
        node.setNodeKey("amount");
        node.setNodeType("FIELD");
        return node;
    }
    private EntityFormField field() {
        EntityFormField field = new EntityFormField();
        field.setId("node-1");
        field.setFormId("form-1");
        field.setFieldCode("amount");
        field.setFieldName("金额");
        field.setFieldLabel("金额");
        return field;
    }
}
