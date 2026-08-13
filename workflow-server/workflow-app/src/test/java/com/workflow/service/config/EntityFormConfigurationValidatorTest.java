package com.workflow.service.config;

import com.workflow.entity.form.application.validation.EntityFormConfigurationValidator;
import com.workflow.entity.form.application.EntityFormActionConfigPolicy;
import com.workflow.entity.form.application.PublishedFormConditionEvaluator;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import com.workflow.entity.ui.application.UiDataSourceDefinitionValidator;
import com.workflow.entity.ui.application.validation.StructuredConfigValidator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 实体表单配置校验器测试。
 *
 * <p>被测对象：{@link EntityFormConfigurationValidator}，覆盖结构化校验与模式访问、
 * 拒绝未知模式与非法区间、空白 JSON 列归一化为 null 等场景。
 */
class EntityFormConfigurationValidatorTest {

    private static final ObjectMapper OBJECT_MAPPER =
            new ObjectMapper();

    private final EntityFieldMapper entityFieldMapper =
            mock(EntityFieldMapper.class);
    private final EntityFieldFileItemMapper fileItemMapper =
            mock(EntityFieldFileItemMapper.class);

    /** 被测表单配置校验器 */
    private final EntityFormConfigurationValidator validator =
            new EntityFormConfigurationValidator(
                    new StructuredConfigValidator(OBJECT_MAPPER),
                    new EntityFormActionConfigPolicy(),
                    new UiDataSourceDefinitionValidator(
                            new JsonDocumentCodec(
                                    OBJECT_MAPPER)),
                    new PublishedFormConditionEvaluator(
                            OBJECT_MAPPER),
                    entityFieldMapper,
                    fileItemMapper);

    /** 测试接受结构化校验与模式访问：验证合法校验规则与多模式扩展配置通过校验 */
    @Test
    void acceptsStructuredValidationAndModeAccess() {
        EntityFormField field = field();
        field.setValidationRules(
                "{\"minLength\":2,\"maxLength\":50,"
                        + "\"format\":\"EMAIL\","
                        + "\"pattern\":\"^[^@]+@example\\\\.com$\"}");
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

    /** 测试拒绝语法错误的表单正则表达式。 */
    @Test
    void rejectsInvalidRegexValidation() {
        EntityFormField field = field();
        field.setValidationRules("{\"pattern\":\"[unclosed\"}");

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateFields(List.of(field)));
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

    /** 测试子表单输入参数 Schema 的编码、中文名、类型与默认值均受统一 Schema 校验。 */
    @Test
    void validatesSubFormInputParameterSchema() {
        EntityForm form = new EntityForm();
        form.setEntityId("entity-1");
        form.setFormName("项目成员子表单");
        form.setFormKey("projectMemberSubForm");
        form.setViewConfig(
                """
                {
                  "inputParameterSchema": {
                    "type": "object",
                    "required": ["projectId"],
                    "properties": {
                      "projectId": {
                        "type": "string",
                        "title": "项目ID"
                      },
                      "quantity": {
                        "type": "integer",
                        "title": "数量",
                        "default": 1
                      }
                    }
                  }
                }
                """);

        assertDoesNotThrow(() -> validator.validateForm(form));

        form.setViewConfig(
                """
                {
                  "inputParameterSchema": {
                    "type": "object",
                    "properties": {
                      "quantity": {
                        "type": "integer",
                        "title": "数量",
                        "default": "one"
                      }
                    }
                  }
                }
                """);
        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateForm(form));
    }

    @Test
    void validatesAttachmentItemRuleAgainstCurrentEntityState() {
        EntityForm form = attachmentRuleForm("status");
        EntityField status = entityField(
                "field-status",
                "status",
                EntityField.FieldType.SELECT);
        EntityField documents = entityField(
                "field-documents",
                "documents",
                EntityField.FieldType.FILE);
        EntityFieldFileItem item = attachmentItem(false);
        when(entityFieldMapper.findByEntityId("entity-1"))
                .thenReturn(List.of(status, documents));
        when(fileItemMapper.findByFieldId("field-documents"))
                .thenReturn(List.of(item));

        assertDoesNotThrow(() -> validator.validateForm(form));

        when(fileItemMapper.findByFieldId("field-documents"))
                .thenReturn(List.of());
        IllegalArgumentException deletedItem = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateForm(form));
        assertTrue(deletedItem.getMessage().contains("引用已失效"));
    }

    @Test
    void rejectsInvalidConditionReferenceAndFixedRequiredOverride() {
        EntityField status = entityField(
                "field-status",
                "status",
                EntityField.FieldType.SELECT);
        EntityField documents = entityField(
                "field-documents",
                "documents",
                EntityField.FieldType.FILE);
        when(entityFieldMapper.findByEntityId("entity-1"))
                .thenReturn(List.of(status, documents));
        when(fileItemMapper.findByFieldId("field-documents"))
                .thenReturn(List.of(attachmentItem(false)));

        IllegalArgumentException invalidReference = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateForm(
                        attachmentRuleForm("missingField")));
        assertTrue(invalidReference.getMessage().contains(
                "引用字段不存在"));

        when(fileItemMapper.findByFieldId("field-documents"))
                .thenReturn(List.of(attachmentItem(true)));
        IllegalArgumentException fixedRequired = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateForm(
                        attachmentRuleForm("status")));
        assertTrue(fixedRequired.getMessage().contains(
                "已是实体固定必填"));
    }

    private EntityForm attachmentRuleForm(String conditionProperty) {
        EntityForm form = new EntityForm();
        form.setEntityId("entity-1");
        form.setFormName("附件条件表单");
        form.setFormKey("attachmentRuleForm");

        EntityFormField status = field();
        status.setFieldId("field-status");
        status.setFieldCode("status");
        status.setFieldType("SELECT");

        EntityFormField documents = field();
        documents.setFieldId("field-documents");
        documents.setFieldCode("documents");
        documents.setFieldName("项目附件");
        documents.setFieldType("FILE");
        documents.setComponentType("file");
        Map<String, Object> condition = Map.of(
                "type", "CONDITION",
                "property", conditionProperty,
                "operator", "==",
                "value", "REVIEW");
        Map<String, Object> conditionConfig = Map.of(
                "version", 1,
                "root", Map.of(
                        "type", "GROUP",
                        "logic", "AND",
                        "children", List.of(condition)));
        documents.setComponentProps(writeJson(Map.of(
                "fileItems", List.of(Map.of(
                        "itemKey", "afi_contract",
                        "itemName", "合同终稿",
                        "nameAliases", List.of("合同初稿"),
                        "required", false,
                        "fileTypes", ".pdf",
                        "maxSize", 20,
                        "maxCount", 2)),
                "attachmentItemRequiredRules", Map.of(
                        "version", 1,
                        "items", List.of(Map.of(
                                "itemKey", "afi_contract",
                                "requiredConditionConfig",
                                conditionConfig))))));
        form.setFields(List.of(status, documents));
        return form;
    }

    private EntityField entityField(
            String id,
            String code,
            EntityField.FieldType type) {
        EntityField field = new EntityField();
        field.setId(id);
        field.setFieldCode(code);
        field.setFieldType(type);
        return field;
    }

    private EntityFieldFileItem attachmentItem(boolean required) {
        EntityFieldFileItem item = new EntityFieldFileItem();
        item.setItemKey("afi_contract");
        item.setItemName("合同终稿");
        item.setRequired(required);
        return item;
    }

    private String writeJson(Object value) {
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
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
