package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFieldFileItemMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.form.application.PublishedFormConditionEvaluator;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.form.application.context.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.form.application.validation.PublishedFormCrossFieldValidator;
import com.workflow.entity.form.application.validation.PublishedFormRequiredValidator;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiInterfaceExtensionService;
import com.workflow.entity.ui.application.validation.UiExtensionDefinitionValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 直接插入只放行写入表单未声明的标量列。使用真实表单处理器和校验器，
 * 防止为此绕过整张表单，或在预检/正式提交之间丢失未知列。
 */
class ExcelDirectInsertFormSubmissionTest {
    private final UiConfigReleaseService releases = mock(UiConfigReleaseService.class);
    private final UiInterfaceExtensionService interfaces = mock(UiInterfaceExtensionService.class);
    private final EntityForm form = new EntityForm();
    private PublishedFormSubmissionService submissions;

    @BeforeEach
    void setup() {
        var json = new ObjectMapper().findAndRegisterModules();
        var codec = new JsonDocumentCodec(json);
        var conditions = new PublishedFormConditionEvaluator(json);
        var dynamic = mock(EntityDataDynamicService.class);
        var required = new PublishedFormRequiredValidator(dynamic, mock(EntityFieldMapper.class),
                mock(EntityFieldFileItemMapper.class), conditions, codec, json);
        var crossFields = new PublishedFormCrossFieldValidator(dynamic, json, codec, conditions);
        submissions = new PublishedFormSubmissionService(mock(EntityDefinitionMapper.class),
                mock(EntityFormMapper.class), mock(EntityRelationMapper.class), releases, interfaces,
                codec, mock(UiExtensionDefinitionValidator.class), required, crossFields);
        form.setId("write-form");
        form.setEntityId("employee");
        form.setFields(List.of(field("name", "STRING", true)));
        var resolved = new ResolvedEntityFormRelease(form, "hash", 1);
        when(releases.resolveAuthorizedRuntimeFormRelease("write-form", "hash", Map.of())).thenReturn(resolved);
        when(releases.resolveRuntimeFormRelease("write-form", "hash", 1)).thenReturn(resolved);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void keepsMappedScalarOutsideWriteFormInPreviewAndSubmission(boolean preview) {
        Map<String, Object> row = Map.of("name", "张三", "serial", "EXCEL-001");
        assertThat(apply(row, preview)).containsAllEntriesOf(row);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void directColumnDoesNotSkipRequiredFieldsInsideWriteForm(boolean preview) {
        assertThatThrownBy(() -> apply(Map.of("serial", "EXCEL-001"), preview))
                .hasMessageContaining("name").hasMessageContaining("必填");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void directColumnDoesNotSkipCrossFieldRulesInsideWriteForm(boolean preview) {
        EntityFormField lower = field("lower", "INTEGER", false);
        lower.setValidationRules("""
                {"crossField":{"version":1,"rules":[{"id":"range","operator":"LE",
                "targetFieldCode":"upper","message":"下限不能大于上限"}]}}
                """);
        form.setFields(List.of(lower, field("upper", "INTEGER", false)));
        assertThatThrownBy(() -> apply(Map.of("lower", 5, "upper", 3, "serial", "EXCEL-001"), preview))
                .hasMessageContaining("下限不能大于上限");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void stillRunsWholeFormBeforeSubmitWithDirectColumnAvailable(boolean preview) {
        form.setDataSourceBindingsDocument("""
                {"BEFORE_SUBMIT":[{"extensionId":"normalize-name","sideEffectFree":true}]}
                """);
        when(interfaces.execute(eq("normalize-name"), any())).thenAnswer(call -> {
            com.workflow.entity.ui.api.request.UiExtensionExecuteRequest request = call.getArgument(1);
            assertThat(((Map<?, ?>) request.getInput().get("formData")).get("serial"))
                    .isEqualTo("EXCEL-001");
            return Map.of("name", "处理后的姓名");
        });

        assertThat(apply(Map.of("serial", "EXCEL-001"), preview))
                .containsEntry("name", "处理后的姓名").containsEntry("serial", "EXCEL-001");
        verify(interfaces).execute(eq("normalize-name"), any());
    }

    /** 两个真实入口共享相同的已发布表单；只有解析服务和外部接口被替换。 */
    private Map<String, Object> apply(Map<String, Object> data, boolean preview) {
        var execution = new FormSubmissionExecutionContext("excel_test", "EXCEL_CREATE", Map.of());
        try (var ignored = ExcelAtomicExecutionContext.open()) {
            return preview
                    ? submissions.previewSideEffectFreeForm("write-form", "hash", 1,
                            "employee", null, "create", data, execution, null)
                    : submissions.applyAuthorizedFormWithRelease("write-form", "hash", Map.of(),
                            "employee", null, "create", data, execution).data();
        }
    }

    private EntityFormField field(String code, String type, boolean required) {
        var field = new EntityFormField();
        field.setFieldCode(code);
        field.setFieldName(code);
        field.setFieldType(type);
        field.setIsRequired(required ? 1 : 0);
        return field;
    }
}
