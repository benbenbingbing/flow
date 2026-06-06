package com.workflow.service.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityFormField;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityFormFieldRuntimeMapperTest {

    @Test
    void mapsSubFormAssociationMetadataForRuntimeRendering() {
        EntityFormField field = new EntityFormField();
        field.setId("field-1");
        field.setFieldId("entity-field-1");
        field.setFieldCode("items");
        field.setFieldName("明细");
        field.setFieldLabel("明细信息");
        field.setFieldType("SUB_FORM");
        field.setDisplayMode("tab");
        field.setRefEntityId("child-entity");
        field.setRefEntityType("CUSTOM");
        field.setRefFieldCode("parent_id");
        field.setChildEntityId("child-entity");
        field.setChildEntityCode("child");
        field.setChildRefFieldCode("parentId");
        field.setRelationType("ONE_TO_MANY");
        field.setCascadeDelete(true);
        field.setComponentProps("{\"subFormConfig\":{\"type\":\"ref\",\"refFormId\":\"form-child\"}}");
        field.setIsReadonly(0);

        Map<String, Object> result = EntityFormFieldRuntimeMapper.toMap(field, true, new ObjectMapper());

        assertEquals("items", result.get("fieldCode"));
        assertEquals("sub_form", result.get("componentType"));
        assertEquals(true, result.get("isReadonly"));
        assertEquals("tab", result.get("displayMode"));
        assertEquals("child-entity", result.get("refEntityId"));
        assertEquals("CUSTOM", result.get("refEntityType"));
        assertEquals("parent_id", result.get("refFieldCode"));
        assertEquals("child-entity", result.get("childEntityId"));
        assertEquals("child", result.get("childEntityCode"));
        assertEquals("parentId", result.get("childRefFieldCode"));
        assertEquals("ONE_TO_MANY", result.get("relationType"));
        assertEquals(true, result.get("cascadeDelete"));
        assertEquals("parentId", ((Map<?, ?>) result.get("relation")).get("childRefFieldCode"));
        assertEquals("form-child", ((Map<?, ?>) ((Map<?, ?>) result.get("componentProps")).get("subFormConfig")).get("refFormId"));
    }
}
