package com.workflow.entity.form.application.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.application.EntityFormActionConfigPolicy;
import com.workflow.entity.form.application.FormUniqueRulePolicy;
import com.workflow.entity.form.application.PublishedFormConditionEvaluator;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.ui.application.UiDataSourceDefinitionValidator;
import com.workflow.entity.ui.application.validation.StructuredConfigValidator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 表单发布边界的显隐、禁用、必填结构化条件一致性测试。 */
class EntityFormConfigurationValidatorConditionTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Map<String, String> CONDITION_LABELS = Map.of(
            "visibilityConditionConfig", "显示条件",
            "disabledConditionConfig", "禁用条件",
            "requiredConditionConfig", "必填条件");

    private final EntityFormConfigurationValidator validator = validator();

    /** 三类条件都完整且引用当前表单字段时允许形成发布快照。 */
    @Test
    void acceptsCompleteVisibilityDisabledAndRequiredConditions() {
        EntityFormField target = field("target");
        target.setComponentProps("""
                {
                  "linkageRules": {
                    "visibilityConditionConfig": %s,
                    "disabledConditionConfig": %s,
                    "requiredConditionConfig": %s
                  }
                }
                """.formatted(
                condition("status", "==", "OPEN"),
                condition("status", "==", "LOCKED"),
                condition("status", "notEmpty", "")));

        assertDoesNotThrow(() -> validator.validateForm(
                form(field("status"), target)));
    }

    /** 新发布快照不能保留会让浏览器回退旧表达式的不完整结构化条件。 */
    @Test
    void rejectsIncompleteConfigurationForEveryFieldStateCondition() {
        for (Map.Entry<String, String> definition
                : CONDITION_LABELS.entrySet()) {
            EntityFormField target = field("target");
            target.setComponentProps("""
                    {
                      "linkageRules": {
                        "%s": %s,
                        "%s": "${status} == 'OPEN'"
                      }
                    }
                    """.formatted(
                    definition.getKey(),
                    condition("status", "==", ""),
                    legacyExpressionKey(definition.getKey())));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> validator.validateForm(
                            form(field("status"), target)));

            assertTrue(exception.getMessage().contains(
                    definition.getValue()));
            assertTrue(exception.getMessage().contains("条件值不能为空"));
        }
    }

    /** 发布时三类条件都必须引用当前表单或实体中真实存在的字段。 */
    @Test
    void rejectsMissingFieldReferenceForEveryFieldStateCondition() {
        for (Map.Entry<String, String> definition
                : CONDITION_LABELS.entrySet()) {
            EntityFormField target = field("target");
            target.setComponentProps("""
                    {"linkageRules":{"%s":%s}}
                    """.formatted(
                    definition.getKey(),
                    condition("missing", "==", "OPEN")));

            IllegalArgumentException exception = assertThrows(
                    IllegalArgumentException.class,
                    () -> validator.validateForm(
                            form(field("status"), target)));

            assertTrue(exception.getMessage().contains(
                    definition.getValue()));
            assertTrue(exception.getMessage().contains(
                    "引用字段不存在: missing"));
        }
    }

    private static EntityFormConfigurationValidator validator() {
        PublishedFormConditionEvaluator evaluator =
                new PublishedFormConditionEvaluator(OBJECT_MAPPER);
        return new EntityFormConfigurationValidator(
                new StructuredConfigValidator(OBJECT_MAPPER),
                new EntityFormActionConfigPolicy(),
                new UiDataSourceDefinitionValidator(
                        new JsonDocumentCodec(OBJECT_MAPPER)),
                evaluator,
                mock(EntityFieldMapper.class),
                mock(EntityFieldFileItemMapper.class),
                new FormUniqueRulePolicy(OBJECT_MAPPER, evaluator));
    }

    private static EntityForm form(EntityFormField... fields) {
        EntityForm form = new EntityForm();
        form.setEntityId("entity-1");
        form.setFormName("条件一致性表单");
        form.setFormKey("conditionParityForm");
        form.setFields(List.of(fields));
        return form;
    }

    private static EntityFormField field(String code) {
        EntityFormField field = new EntityFormField();
        field.setFieldCode(code);
        field.setFieldLabel(code);
        field.setFieldType("STRING");
        field.setGridSpan(24);
        return field;
    }

    private static String condition(
            String property,
            String operator,
            String value) {
        return """
                {
                  "version": 1,
                  "root": {
                    "type": "CONDITION",
                    "property": "%s",
                    "operator": "%s",
                    "value": "%s"
                  }
                }
                """.formatted(property, operator, value);
    }

    private static String legacyExpressionKey(String configKey) {
        return switch (configKey) {
            case "visibilityConditionConfig" -> "visibilityRule";
            case "disabledConditionConfig" -> "disabledRule";
            case "requiredConditionConfig" -> "requiredRule";
            default -> throw new IllegalArgumentException(
                    "未知条件配置: " + configKey);
        };
    }
}
