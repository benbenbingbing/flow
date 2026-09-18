package com.workflow.entity.definition.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EntityRelationFieldPolicyTest {

    @Test
    void acceptsOrdinaryVarcharFieldsAndCorrectReferences() {
        for (var type : new EntityField.FieldType[]{EntityField.FieldType.STRING,
                EntityField.FieldType.SELECT, EntityField.FieldType.RADIO, EntityField.FieldType.REFERENCE}) {
            for (Integer length : new Integer[]{null, 64, 200, 4096}) {
                EntityField field = field(type);
                field.setFieldLength(length);
                if (type == EntityField.FieldType.REFERENCE) field.setRefEntityId("parent");
                assertNull(EntityRelationFieldPolicy.violation(field, "parent"), type + "/" + length);
            }
        }
    }

    @Test
    void rejectsNonScalarOrIncompatibleStorageEvenWithForgedDatabaseType() {
        for (var type : EntityField.FieldType.values()) {
            if (java.util.Set.of(EntityField.FieldType.STRING, EntityField.FieldType.SELECT,
                    EntityField.FieldType.RADIO, EntityField.FieldType.REFERENCE).contains(type)) continue;
            EntityField field = field(type);
            field.setDbType("VARCHAR(200)");
            assertNotNull(EntityRelationFieldPolicy.violation(field, "parent"), type.name());
        }
        EntityField multiple = field(EntityField.FieldType.STRING);
        multiple.setValueStorage("MULTI_TABLE");
        assertNotNull(EntityRelationFieldPolicy.violation(multiple, "parent"));
        multiple.setValueStorage(null);
        multiple.setDbColumnName("id");
        assertNotNull(EntityRelationFieldPolicy.violation(multiple, "parent"));
    }

    @Test
    void checksCapacityAndDoesNotOverrideExistingTargetMetadata() {
        EntityField field = field(EntityField.FieldType.STRING);
        for (int length : new int[]{0, 32, 63, 4097}) {
            field.setFieldLength(length);
            assertEquals("ENTITY_RELATION_CHILD_REF_LENGTH_INVALID",
                    EntityRelationFieldPolicy.violation(field, "parent").code());
        }
        field.setFieldLength(200);
        field.setRefEntityId("other");
        assertEquals("ENTITY_RELATION_CHILD_REF_TARGET_INVALID",
                EntityRelationFieldPolicy.violation(field, "parent").code());
        field.setRefEntityId(null);
        field.setRefEntityType(EntityField.RefEntityType.USER);
        assertNotNull(EntityRelationFieldPolicy.violation(field, "parent"));
        field.setRefEntityType(null);
        field.setFieldType(EntityField.FieldType.REFERENCE);
        assertNotNull(EntityRelationFieldPolicy.violation(field, "parent"));
    }

    private EntityField field(EntityField.FieldType type) {
        EntityField field = new EntityField();
        field.setFieldCode("parentId");
        field.setFieldType(type);
        return field;
    }
}
