package com.workflow.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityFormField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityFormConfigurationValidatorTest {

    private final EntityFormConfigurationValidator validator =
            new EntityFormConfigurationValidator(new StructuredConfigValidator(new ObjectMapper()));

    @Test
    void acceptsStructuredValidationAndModeAccess() {
        EntityFormField field = field();
        field.setValidationRules("{\"minLength\":2,\"maxLength\":20,\"format\":\"EMAIL\"}");
        field.setExtensionConfig("{\"modes\":{\"create\":{\"visible\":true,\"editable\":true},\"view\":{\"visible\":true,\"editable\":false}}}");

        assertDoesNotThrow(() -> validator.validateFields(List.of(field)));
    }

    @Test
    void rejectsUnknownModeAndInvalidRange() {
        EntityFormField field = field();
        field.setValidationRules("{\"min\":10,\"max\":1}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateFields(List.of(field)));

        field.setValidationRules("{}");
        field.setExtensionConfig("{\"modes\":{\"delete\":{\"visible\":true}}}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateFields(List.of(field)));
    }

    private EntityFormField field() {
        EntityFormField field = new EntityFormField();
        field.setFieldCode("email");
        field.setComponentType("input");
        field.setGridSpan(24);
        return field;
    }
}
