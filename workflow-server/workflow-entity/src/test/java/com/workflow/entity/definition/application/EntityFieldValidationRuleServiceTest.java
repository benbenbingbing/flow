package com.workflow.entity.definition.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityFieldValidationRuleServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EntityFieldValidationRuleService service =
            new EntityFieldValidationRuleService(objectMapper);

    @Test
    void normalizesEverySupportedTextRule() throws Exception {
        String normalized = service.validateAndNormalize(
                EntityField.FieldType.STRING,
                """
                {"minLength":2,"maxLength":100,"format":"email"}
                """,
                "邮箱");

        assertEquals(
                Map.of(
                        "minLength", 2,
                        "maxLength", 100,
                        "format", "EMAIL"),
                objectMapper.readValue(normalized, Map.class));
    }

    @Test
    void normalizesEverySupportedNumberRule() throws Exception {
        String normalized = service.validateAndNormalize(
                EntityField.FieldType.DECIMAL,
                """
                {"min":0.01,"max":100}
                """,
                "完成比例");

        Map<?, ?> rules = objectMapper.readValue(normalized, Map.class);
        assertEquals(0.01d, ((Number) rules.get("min")).doubleValue());
        assertEquals(100, ((Number) rules.get("max")).intValue());
    }

    @Test
    void rejectsUnknownIncompatibleAndInvalidRules() {
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateAndNormalize(
                        EntityField.FieldType.STRING,
                        """
                        {"pattern":"x"}
                        """,
                        "名称"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateAndNormalize(
                        EntityField.FieldType.DATE,
                        """
                        {"minLength":1}
                        """,
                        "日期"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateAndNormalize(
                        EntityField.FieldType.STRING,
                        """
                        {"minLength":5,"maxLength":2}
                        """,
                        "名称"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateAndNormalize(
                        EntityField.FieldType.DECIMAL,
                        """
                        {"min":10,"max":2}
                        """,
                        "比例"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateAndNormalize(
                        EntityField.FieldType.STRING,
                        """
                        {"format":"IP"}
                        """,
                        "地址"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateAndNormalize(
                        EntityField.FieldType.STRING,
                        "{bad json",
                        "名称"));
    }

    @Test
    void executesMinLengthAndMaxLength() {
        EntityField field = field(
                EntityField.FieldType.STRING,
                """
                {"minLength":2,"maxLength":4}
                """);

        assertDoesNotThrow(() -> service.validateValue(field, "测试"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateValue(field, "短"));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateValue(field, "超过四个字符"));
    }

    @Test
    void executesEverySupportedFormat() {
        assertFormat("EMAIL", "test@example.com", "not-email");
        assertFormat("PHONE", "13800138000", "123");
        assertFormat("URL", "https://example.com/a", "example.com");
    }

    @Test
    void executesMinAndMax() {
        EntityField field = field(
                EntityField.FieldType.DECIMAL,
                """
                {"min":0.01,"max":100}
                """);

        assertDoesNotThrow(() -> service.validateValue(field, 50));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateValue(field, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateValue(field, 101));
    }

    private void assertFormat(
            String format,
            String valid,
            String invalid) {
        EntityField field = field(
                EntityField.FieldType.STRING,
                "{\"format\":\"" + format + "\"}");
        assertDoesNotThrow(() -> service.validateValue(field, valid));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.validateValue(field, invalid));
    }

    private EntityField field(
            EntityField.FieldType type,
            String rules) {
        EntityField field = new EntityField();
        field.setFieldName("测试字段");
        field.setFieldType(type);
        field.setValidateRules(rules);
        return field;
    }
}
