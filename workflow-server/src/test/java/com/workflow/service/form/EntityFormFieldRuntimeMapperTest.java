package com.workflow.service.form;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityFormField;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EntityFormFieldRuntimeMapperTest {

    @Test
    void nodeEditableKeepsFieldReadonlyConfiguration() {
        EntityFormField field = new EntityFormField();
        field.setFieldCode("lockedNote");
        field.setIsReadonly(1);

        assertEquals(1, EntityFormFieldRuntimeMapper.toMap(field, null, new ObjectMapper())
                .get("isReadonly"));
    }

    @Test
    void nodeReadonlyForcesIntegerReadonlyFlag() {
        EntityFormField field = new EntityFormField();
        field.setFieldCode("amount");
        field.setIsReadonly(0);

        assertEquals(1, EntityFormFieldRuntimeMapper.toMap(field, true, new ObjectMapper())
                .get("isReadonly"));
    }
}
