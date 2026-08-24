package com.workflow.entity.form.application.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.application.EntityFormActionConfigPolicy;
import com.workflow.entity.form.application.PublishedFormConditionEvaluator;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.application.UiDataSourceDefinitionValidator;
import com.workflow.entity.ui.application.validation.StructuredConfigValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/** 表单级统一数据源的保存与发布共用规则测试。 */
class EntityFormConfigurationValidatorDataSourceTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final EntityFormConfigurationValidator validator =
            new EntityFormConfigurationValidator(
                    new StructuredConfigValidator(OBJECT_MAPPER),
                    new EntityFormActionConfigPolicy(),
                    new UiDataSourceDefinitionValidator(
                            new JsonDocumentCodec(OBJECT_MAPPER)),
                    new PublishedFormConditionEvaluator(OBJECT_MAPPER),
                    mock(EntityFieldMapper.class),
                    mock(EntityFieldFileItemMapper.class));

    /** 同一生命周期位置的多个数据源步骤不能写入相同输出目标。 */
    @Test
    void rejectsDuplicateOutputTargetWithinUsage() {
        EntityForm form = formWithBindings(
                """
                {
                  "FORM_INIT": [
                    {
                      "serviceId": "source-1",
                      "outputMapping": {
                        "owner.name": "data.ownerName"
                      }
                    },
                    {
                      "serviceId": "source-2",
                      "outputMapping": {
                        "owner. .name": "data.approverName"
                      }
                    }
                  ]
                }
                """);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> validator.validateForm(form));

        assertTrue(exception.getMessage().contains("FORM_INIT"));
        assertTrue(exception.getMessage().contains("步骤 1 与步骤 2"));
        assertTrue(exception.getMessage().contains("owner.name"));
    }

    /** 不同生命周期位置各自独立执行，允许复用相同输出目标。 */
    @Test
    void acceptsSameOutputTargetAcrossUsages() {
        EntityForm form = formWithBindings(
                """
                {
                  "FORM_INIT": {
                    "serviceId": "source-1",
                    "outputMapping": {
                      "owner.name": "data.ownerName"
                    }
                  },
                  "AFTER_LOAD": {
                    "serviceId": "source-2",
                    "outputMapping": {
                      "owner.name": "data.ownerName"
                    }
                  }
                }
                """);

        assertDoesNotThrow(() -> validator.validateForm(form));
    }

    private EntityForm formWithBindings(String bindings) {
        EntityForm form = new EntityForm();
        form.setEntityId("entity-1");
        form.setFormName("数据源表单");
        form.setFormKey("dataSourceForm");
        form.setDataSourceBindingsDocument(bindings);
        return form;
    }
}
