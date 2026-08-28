package com.workflow.entity.form.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.api.request.FormUniquePrecheckRequest;
import com.workflow.entity.form.api.response.FormUniquePrecheckResponse;
import com.workflow.entity.form.application.port.FormUniqueConflictQuery;
import com.workflow.entity.form.application.model.FormUniqueCheck;
import com.workflow.entity.form.application.model.FormUniqueRule;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.permission.api.response.DataPermissionResult;
import com.workflow.entity.permission.application.DataPermissionEngine;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublishedFormUniquePrecheckServiceTest {

    private final UiConfigReleaseService releaseService =
            mock(UiConfigReleaseService.class);
    private final FormUniqueConflictQuery conflictQuery =
            mock(FormUniqueConflictQuery.class);
    private final EntityDefinitionMapper definitionMapper =
            mock(EntityDefinitionMapper.class);
    private final EntityActionCapabilityService capabilityService =
            mock(EntityActionCapabilityService.class);
    private final SysUserService userService =
            mock(SysUserService.class);
    private final DataPermissionEngine dataPermissionEngine =
            mock(DataPermissionEngine.class);
    private final SysUser currentUser = new SysUser();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final FormUniqueRulePolicy rulePolicy =
            new FormUniqueRulePolicy(
                    objectMapper,
                    new PublishedFormConditionEvaluator(objectMapper));
    private final PublishedFormUniqueRuleService ruleService =
            new PublishedFormUniqueRuleService(
                    releaseService,
                    rulePolicy,
                    conflictQuery);
    private final PublishedFormUniquePrecheckService service =
            new PublishedFormUniquePrecheckService(
                    releaseService,
                    ruleService,
                    definitionMapper,
                    capabilityService,
                    userService,
                    dataPermissionEngine);

    @BeforeEach
    void configureEntity() {
        UserContext.setCurrentUser("user-1", "tester");
        currentUser.setId("user-1");
        when(userService.getById("user-1"))
                .thenReturn(currentUser);
        when(dataPermissionEngine.calculatePermission(
                "project", null, currentUser))
                .thenReturn(DataPermissionResult.allowAll());
        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        definition.setEntityCode("project");
        when(definitionMapper.selectById("entity-1"))
                .thenReturn(definition);
    }

    @AfterEach
    void clearUserContext() {
        UserContext.clear();
    }

    @Test
    void reportsDuplicateUsingOnlyServerResolvedPublishedRule() {
        EntityForm form = publishedForm(conditionalRule(true));
        when(releaseService.resolveAuthorizedRuntimeFormRelease(
                "form-1",
                "release-1",
                3,
                "signed-token"))
                .thenReturn(new ResolvedEntityFormRelease(
                        form,
                        "release-1",
                        3));
        when(conflictQuery.findCandidates(
                "project", "name", "project a", null))
                .thenReturn(List.of(Map.of(
                        "id", "other",
                        "name", " PROJECT A ",
                        "status", "IN_PROGRESS")));

        FormUniquePrecheckResponse response = service.precheck(
                "form-1",
                request(Map.of(
                        "name", "Project A",
                        "status", "IN_PROGRESS")));

        assertTrue(response.isChecked());
        assertFalse(response.isAvailable());
        assertTrue(response.getMessage().contains("已存在"));
        verify(capabilityService).requireStandardPermission(
                "project",
                EntityPermissionAction.CREATE);
    }

    @Test
    void editMergesExistingRecordAndExcludesItself() {
        EntityForm form = publishedForm(conditionalRule(true));
        when(releaseService.resolveAuthorizedRuntimeFormRelease(
                "form-1",
                "release-1",
                3,
                "signed-token"))
                .thenReturn(new ResolvedEntityFormRelease(
                        form,
                        "release-1",
                        3));
        when(conflictQuery.findRecord("project", "record-1"))
                .thenReturn(Map.of(
                        "id", "record-1",
                        "name", "Project A",
                        "status", "DRAFT"));
        when(conflictQuery.findCandidates(
                "project", "name", "project a", "record-1"))
                .thenReturn(List.of());
        FormUniquePrecheckRequest request = request(Map.of(
                "status", "IN_PROGRESS"));
        request.setRecordId("record-1");

        FormUniquePrecheckResponse response = service.precheck(
                "form-1",
                request);

        assertTrue(response.isChecked());
        assertTrue(response.isAvailable());
        assertNull(response.getMessage());
        verify(capabilityService).requireAnyStandardPermission(
                "project",
                EntityPermissionAction.UPDATE,
                EntityPermissionAction.APPROVE);
    }

    @Test
    void skipsWhenConditionIsFalseOrPrecheckIsDisabled() {
        EntityForm form = publishedForm(conditionalRule(true));
        when(releaseService.resolveAuthorizedRuntimeFormRelease(
                "form-1", "release-1", 3, "signed-token"))
                .thenReturn(new ResolvedEntityFormRelease(
                        form, "release-1", 3));

        FormUniquePrecheckResponse conditionSkipped = service.precheck(
                "form-1",
                request(Map.of(
                        "name", "Project A",
                        "status", "DRAFT")));

        assertFalse(conditionSkipped.isChecked());
        assertTrue(conditionSkipped.isAvailable());
        verify(conflictQuery, never()).findCandidates(
                "project", "name", "project a", null);

        EntityForm disabled = publishedForm(conditionalRule(false));
        when(releaseService.resolveAuthorizedRuntimeFormRelease(
                "form-2", "release-1", 3, "signed-token"))
                .thenReturn(new ResolvedEntityFormRelease(
                        disabled, "release-1", 3));
        FormUniquePrecheckResponse disabledResponse = service.precheck(
                "form-2",
                request(Map.of(
                        "name", "Project A",
                        "status", "IN_PROGRESS")));
        assertFalse(disabledResponse.isChecked());
        assertTrue(disabledResponse.isAvailable());
    }

    @Test
    void neverReadsDraftRulesWhenNoActiveReleaseExists() {
        EntityForm draft = publishedForm(conditionalRule(true));
        when(releaseService.resolveAuthorizedRuntimeFormRelease(
                "form-1", "release-1", 3, "signed-token"))
                .thenReturn(new ResolvedEntityFormRelease(
                        draft, null, null));

        FormUniquePrecheckResponse response = service.precheck(
                "form-1",
                request(Map.of(
                        "name", "Project A",
                        "status", "IN_PROGRESS")));

        assertFalse(response.isChecked());
        assertTrue(response.isAvailable());
        verify(conflictQuery, never()).findCandidates(
                "project", "name", "project a", null);
    }

    @Test
    void scopedUserSkipsGlobalPrecheckBeforeRawRecordLookup() {
        EntityForm form = publishedForm(conditionalRule(true));
        when(releaseService.resolveAuthorizedRuntimeFormRelease(
                "form-1", "release-1", 3, "signed-token"))
                .thenReturn(new ResolvedEntityFormRelease(
                        form, "release-1", 3));
        when(dataPermissionEngine.calculatePermission(
                "project", null, currentUser))
                .thenReturn(DataPermissionResult.withCondition(
                        "owner_id = #{permissionParameters.ownerId}",
                        Map.of("ownerId", "user-1")));
        FormUniquePrecheckRequest request = request(Map.of(
                "name", "Project A",
                "status", "IN_PROGRESS"));
        request.setRecordId("outside-scope-record");

        FormUniquePrecheckResponse response = service.precheck(
                "form-1", request);

        assertFalse(response.isChecked());
        assertTrue(response.isAvailable());
        assertNull(response.getMessage());
        verify(conflictQuery, never()).findRecord(
                "project", "outside-scope-record");
        verify(conflictQuery, never()).findCandidates(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deniedNullAndMissingUsersShareTheSameNonRevealingResult() {
        EntityForm form = publishedForm(conditionalRule(true));
        when(releaseService.resolveAuthorizedRuntimeFormRelease(
                "form-1", "release-1", 3, "signed-token"))
                .thenReturn(new ResolvedEntityFormRelease(
                        form, "release-1", 3));
        when(dataPermissionEngine.calculatePermission(
                "project", null, currentUser))
                .thenReturn(DataPermissionResult.denyAll(), null);

        FormUniquePrecheckResponse denied = service.precheck(
                "form-1", request(Map.of("name", "Project A")));
        FormUniquePrecheckResponse corrupt = service.precheck(
                "form-1", request(Map.of("name", "Project A")));
        UserContext.clear();
        FormUniquePrecheckResponse missingUser = service.precheck(
                "form-1", request(Map.of("name", "Project A")));

        for (FormUniquePrecheckResponse response
                : List.of(denied, corrupt, missingUser)) {
            assertFalse(response.isChecked());
            assertTrue(response.isAvailable());
            assertNull(response.getMessage());
        }
        verify(conflictQuery, never()).findRecord(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(conflictQuery, never()).findCandidates(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void permissionEngineFailureNeverFallsThroughToRawUniquenessQueries() {
        EntityForm form = publishedForm(conditionalRule(true));
        when(releaseService.resolveAuthorizedRuntimeFormRelease(
                "form-1", "release-1", 3, "signed-token"))
                .thenReturn(new ResolvedEntityFormRelease(
                        form, "release-1", 3));
        when(dataPermissionEngine.calculatePermission(
                "project", null, currentUser))
                .thenThrow(new IllegalStateException("permission unavailable"));

        assertThrows(IllegalStateException.class, () -> service.precheck(
                "form-1", request(Map.of("name", "Project A"))));

        verify(conflictQuery, never()).findRecord(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(conflictQuery, never()).findCandidates(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void authoritativeCheckIgnoresDisabledPrecheckAndFindsGlobalStorageConflict() {
        EntityForm form = publishedForm(conditionalRule(false));
        FormUniqueRule rule = rulePolicy.resolveRules(form).get(0);
        when(conflictQuery.findCandidatesForAuthoritativeCheck(
                "project", "name", "project a", "record-1"))
                .thenReturn(List.of(Map.of(
                        "id", "written-by-another-form",
                        "name", "Project A",
                        "status", "IN_PROGRESS")));

        FormUniqueCheck result = ruleService.check(
                rule,
                "project",
                "record-1",
                Map.of(
                        "id", "record-1",
                        "name", " project a ",
                        "status", "IN_PROGRESS"));

        assertTrue(result.checked());
        assertFalse(result.available());
        verify(conflictQuery)
                .findCandidatesForAuthoritativeCheck(
                        "project",
                        "name",
                        "project a",
                        "record-1");
        verify(conflictQuery, never()).findCandidates(
                "project", "name", "project a", "record-1");
    }

    private FormUniquePrecheckRequest request(
            Map<String, Object> formData) {
        FormUniquePrecheckRequest request =
                new FormUniquePrecheckRequest();
        request.setReleaseId("release-1");
        request.setReleaseVersion(3);
        request.setReleaseResolutionToken("signed-token");
        request.setRuleId("uq_project_name_active");
        request.setFieldCode("name");
        request.setFormData(formData);
        return request;
    }

    private EntityForm publishedForm(String validationRules) {
        EntityFormField field = new EntityFormField();
        field.setFieldCode("name");
        field.setFieldName("项目名称");
        field.setFieldLabel("项目名称");
        field.setFieldType("STRING");
        field.setValidationRules(validationRules);
        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        form.setFields(List.of(field));
        return form;
    }

    private String conditionalRule(boolean precheckEnabled) {
        return """
                {
                  "uniqueness": {
                    "version": 1,
                    "enabled": true,
                    "ruleId": "uq_project_name_active",
                    "mode": "CONDITIONAL",
                    "ignoreBlank": true,
                    "condition": {
                      "version": 1,
                      "root": {
                        "type": "GROUP",
                        "logic": "AND",
                        "children": [{
                          "type": "CONDITION",
                          "property": "status",
                          "operator": "==",
                          "value": "IN_PROGRESS"
                        }]
                      }
                    },
                    "message": "项目名称在当前状态下已存在",
                    "precheck": {
                      "enabled": %s,
                      "trigger": "CHANGE",
                      "debounceMs": 500,
                      "watchConditionFields": true
                    }
                  }
                }
                """.formatted(precheckEnabled);
    }
}
