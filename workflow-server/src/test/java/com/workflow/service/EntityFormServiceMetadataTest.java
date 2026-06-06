package com.workflow.service;

import com.workflow.entity.EntityField;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityRelation;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityFormFieldMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityRelationMapper;
import org.junit.jupiter.api.Test;

import java.beans.Introspector;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EntityFormServiceMetadataTest {

    @Test
    void getFormFieldsCarriesSubFormAssociationMetadataFromEntityField() throws Exception {
        EntityFormFieldMapper formFieldMapper = mock(EntityFormFieldMapper.class);
        EntityFieldMapper entityFieldMapper = mock(EntityFieldMapper.class);
        EntityRelationMapper relationMapper = mock(EntityRelationMapper.class);
        EntityFormService service = new EntityFormService(
                mock(EntityFormMapper.class),
                formFieldMapper,
                mock(EntityDefinitionMapper.class),
                entityFieldMapper,
                relationMapper
        );

        EntityFormField formField = new EntityFormField();
        formField.setId("form-field-1");
        formField.setFieldId("entity-field-1");

        EntityField entityField = new EntityField();
        entityField.setEntityId("parent-id");
        entityField.setFieldCode("items");
        entityField.setFieldName("明细");
        entityField.setFieldType(EntityField.FieldType.SUB_FORM);
        entityField.setRefEntityId("child-entity");
        entityField.setRefEntityType(EntityField.RefEntityType.CUSTOM);
        entityField.setDisplayMode("tab");
        entityField.setRefFieldCode("parent_id");

        EntityRelation relation = new EntityRelation();
        relation.setParentEntityId("parent-id");
        relation.setParentFieldCode("items");
        relation.setChildEntityId("child-entity");
        relation.setChildEntityCode("child");
        relation.setChildRefFieldCode("parentId");
        relation.setRelationType(EntityRelation.RelationType.ONE_TO_MANY);
        relation.setCascadeDelete(true);

        when(formFieldMapper.selectByFormId("form-1")).thenReturn(List.of(formField));
        when(entityFieldMapper.findByIdString("entity-field-1")).thenReturn(entityField);
        when(relationMapper.selectByParentField("parent-id", "items")).thenReturn(relation);

        EntityFormField resolved = service.getFormFields("form-1").get(0);

        assertEquals("items", resolved.getFieldCode());
        assertEquals("SUB_FORM", resolved.getFieldType());
        assertEquals("tab", readBeanProperty(resolved, "displayMode"));
        assertEquals("parentId", readBeanProperty(resolved, "refFieldCode"));
        assertEquals("child-entity", readBeanProperty(resolved, "childEntityId"));
        assertEquals("ONE_TO_MANY", readBeanProperty(resolved, "relationType"));
        assertEquals(true, readBeanProperty(resolved, "cascadeDelete"));
    }

    private static Object readBeanProperty(Object target, String propertyName) throws Exception {
        for (var descriptor : Introspector.getBeanInfo(target.getClass()).getPropertyDescriptors()) {
            if (propertyName.equals(descriptor.getName()) && descriptor.getReadMethod() != null) {
                return descriptor.getReadMethod().invoke(target);
            }
        }
        fail("Missing readable bean property: " + propertyName);
        return null;
    }
}
