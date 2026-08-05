package com.workflow.entity.form.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubFormParameterContractPolicyTest {

    @Test
    void resolvesTrustedParametersAndInitializesOnlyEmptyFields() {
        SubFormParameterContractPolicy.Contract contract =
                new SubFormParameterContractPolicy.Contract(
                        true,
                        1,
                        Map.of(
                                "projectId",
                                "parent.data.project_id"),
                        Map.of(
                                "source_dept_id",
                                "parent.data.dept_id",
                                "fixed_flag",
                                Map.of("literal", "Y")));
        Map<String, Object> schema = Map.of(
                "type",
                "object",
                "required",
                List.of("projectId"),
                "properties",
                Map.of(
                        "projectId",
                        Map.of(
                                "type",
                                "string",
                                "title",
                                "项目ID"),
                        "mode",
                        Map.of(
                                "type",
                                "string",
                                "title",
                                "模式",
                                "default",
                                "CREATE")));
        Map<String, Object> source =
                SubFormParameterContractPolicy.runtimeSource(
                        "parent-1",
                        Map.of(
                                "project_id",
                                "project-1",
                                "dept_id",
                                "dept-1"),
                        Map.of("userId", "user-1"),
                        Map.of(),
                        Map.of(),
                        Map.of());

        Map<String, Object> params =
                SubFormParameterContractPolicy.resolveParameters(
                        contract,
                        schema,
                        source);

        assertEquals("project-1", params.get("projectId"));
        assertEquals("CREATE", params.get("mode"));

        Map<String, Object> row = new java.util.LinkedHashMap<>();
        row.put("source_dept_id", "manual-dept");
        row.put("fixed_flag", "");
        assertTrue(
                SubFormParameterContractPolicy
                        .applyEmptyOnlyInitialization(
                                row,
                                contract,
                                source,
                                List.of("parent_id")));
        assertEquals("manual-dept", row.get("source_dept_id"));
        assertEquals("Y", row.get("fixed_flag"));
        assertFalse(
                SubFormParameterContractPolicy
                        .applyEmptyOnlyInitialization(
                                row,
                                contract,
                                source,
                                List.of("parent_id")));
    }

    @Test
    void validatesTargetsTypesRequiredParametersAndManagedFields() {
        EntityField projectField = new EntityField();
        projectField.setFieldCode("project_id");
        projectField.setFieldType(EntityField.FieldType.STRING);
        EntityFormField departmentField = childField(
                "source_dept_id",
                "STRING",
                0);
        Map<String, Object> schema = Map.of(
                "type",
                "object",
                "required",
                List.of("projectId"),
                "properties",
                Map.of(
                        "projectId",
                        Map.of(
                                "type",
                                "string",
                                "title",
                                "项目ID")));
        SubFormParameterContractPolicy.Contract valid =
                new SubFormParameterContractPolicy.Contract(
                        true,
                        1,
                        Map.of(
                                "projectId",
                                "parent.data.project_id"),
                        Map.of(
                                "source_dept_id",
                                "parent.data.project_id"));

        assertDoesNotThrow(() ->
                SubFormParameterContractPolicy.validateContract(
                        valid,
                        schema,
                        List.of(projectField),
                        List.of(departmentField),
                        "parent_id"));

        IllegalArgumentException missingParameter =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SubFormParameterContractPolicy
                                .validateContract(
                                        new SubFormParameterContractPolicy.Contract(
                                                true,
                                                1,
                                                Map.of(),
                                                Map.of()),
                                        schema,
                                        List.of(projectField),
                                        List.of(departmentField),
                                        "parent_id"));
        assertTrue(missingParameter.getMessage().contains("必填参数"));

        IllegalArgumentException invalidTarget =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SubFormParameterContractPolicy
                                .validateContract(
                                        new SubFormParameterContractPolicy.Contract(
                                                true,
                                                1,
                                                Map.of(
                                                        "missing",
                                                        "parent.data.project_id"),
                                                Map.of()),
                                        schema,
                                        List.of(projectField),
                                        List.of(departmentField),
                                        "parent_id"));
        assertTrue(invalidTarget.getMessage().contains("已失效"));

        IllegalArgumentException managedField =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SubFormParameterContractPolicy
                                .validateContract(
                                        new SubFormParameterContractPolicy.Contract(
                                                true,
                                                1,
                                                Map.of(
                                                        "projectId",
                                                        "parent.data.project_id"),
                                                Map.of(
                                                        "parent_id",
                                                        "parent.recordId")),
                                        schema,
                                        List.of(projectField),
                                        List.of(
                                                departmentField,
                                                childField(
                                                        "parent_id",
                                                        "STRING",
                                                        0)),
                                        "parent_id"));
        assertTrue(managedField.getMessage().contains("系统维护"));

        IllegalArgumentException unsupportedVersion =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> SubFormParameterContractPolicy
                                .validateShape(
                                        new SubFormParameterContractPolicy.Contract(
                                                true,
                                                2,
                                                Map.of(),
                                                Map.of())));
        assertTrue(unsupportedVersion.getMessage().contains("版本"));
    }

    private EntityFormField childField(
            String code,
            String type,
            int readonly) {
        EntityFormField field = new EntityFormField();
        field.setFieldCode(code);
        field.setFieldType(type);
        field.setIsReadonly(readonly);
        return field;
    }
}
