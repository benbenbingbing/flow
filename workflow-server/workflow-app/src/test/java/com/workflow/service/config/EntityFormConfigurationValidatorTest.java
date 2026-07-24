package com.workflow.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 实体表单配置校验器测试。
 *
 * <p>被测对象：{@link EntityFormConfigurationValidator}，覆盖结构化校验与模式访问、
 * 拒绝未知模式与非法区间、空白 JSON 列归一化为 null 等场景。
 */
class EntityFormConfigurationValidatorTest {

    /** 被测表单配置校验器 */
    private final EntityFormConfigurationValidator validator =
            new EntityFormConfigurationValidator(new StructuredConfigValidator(new ObjectMapper()));

    /** 测试接受结构化校验与模式访问：验证合法校验规则与多模式扩展配置通过校验 */
    @Test
    void acceptsStructuredValidationAndModeAccess() {
        EntityFormField field = field();
        field.setValidationRules("{\"minLength\":2,\"maxLength\":20,\"format\":\"EMAIL\"}");
        field.setExtensionConfig("{\"modes\":{\"create\":{\"visible\":true,\"editable\":true},\"view\":{\"visible\":true,\"editable\":false}}}");

        assertDoesNotThrow(() -> validator.validateFields(List.of(field)));
    }

    /** 测试拒绝未知模式与非法区间：验证 min>max 与未知模式 delete 均抛出 IllegalArgumentException */
    @Test
    void rejectsUnknownModeAndInvalidRange() {
        EntityFormField field = field();
        field.setValidationRules("{\"min\":10,\"max\":1}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateFields(List.of(field)));

        field.setValidationRules("{}");
        field.setExtensionConfig("{\"modes\":{\"delete\":{\"visible\":true}}}");
        assertThrows(IllegalArgumentException.class, () -> validator.validateFields(List.of(field)));
    }

    /** 测试将空白 JSON 列归一化为 null：验证表单与字段的空白配置被置为 null */
    @Test
    void normalizesBlankJsonColumnsToNull() {
        EntityFormField field = field();
        field.setValidationRules("  ");
        field.setExtensionConfig("");
        EntityForm form = new EntityForm();
        form.setEntityId("entity-1");
        form.setFormName("演示表单");
        form.setFormKey("demoForm");
        form.setViewConfig("\n");
        form.setFields(List.of(field));

        validator.validateForm(form);

        assertNull(form.getViewConfig());
        assertNull(field.getValidationRules());
        assertNull(field.getExtensionConfig());
    }

    /** 构造基础测试表单字段 */
    private EntityFormField field() {
        EntityFormField field = new EntityFormField();
        field.setFieldCode("email");
        field.setComponentType("input");
        field.setGridSpan(24);
        return field;
    }
}
