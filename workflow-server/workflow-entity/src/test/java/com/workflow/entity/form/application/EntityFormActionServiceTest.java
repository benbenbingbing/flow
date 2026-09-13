package com.workflow.entity.form.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.ProcessCatalogItem;
import com.workflow.contracts.process.port.ProcessCatalogPort;
import com.workflow.contracts.process.port.ProcessTaskAccessPort.ActionableTaskContext;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.api.response.FormActionRuntimeDTO;
import com.workflow.entity.form.api.request.FormActionResolveRequest;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 固定 Published Form 的标准动作栏解析契约。 */
class EntityFormActionServiceTest {

    private final EntityFormMapper formMapper = mock(EntityFormMapper.class);
    private final EntityFormNodeMapper formNodeMapper =
            mock(EntityFormNodeMapper.class);
    private final EntityDefinitionMapper definitionMapper =
            mock(EntityDefinitionMapper.class);
    private final EntityDataDynamicService dataService =
            mock(EntityDataDynamicService.class);
    private final EntityActionCapabilityService capabilityService =
            mock(EntityActionCapabilityService.class);
    private final UiConfigReleaseService releaseService =
            mock(UiConfigReleaseService.class);
    private final ProcessCatalogPort processCatalogPort =
            mock(ProcessCatalogPort.class);
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    private EntityFormActionService service;
    private EntityDefinition definition;

    @BeforeEach
    void setUp() {
        service = new EntityFormActionService(
                formMapper, formNodeMapper, definitionMapper, dataService,
                capabilityService, new EntityFormActionConfigPolicy(),
                releaseService, processCatalogPort,
                new JsonDocumentCodec(objectMapper), objectMapper);
        definition = new EntityDefinition();
        definition.setId("entity-1");
        definition.setEntityCode("work_order");
        definition.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        definition.setLifecycleMode(EntityDefinition.LifecycleMode.WORKFLOW);
        definition.setProcessDefinitionId("process-1");
        when(processCatalogPort.findItemsByIds(List.of("process-1")))
                .thenReturn(Map.of("process-1", new ProcessCatalogItem(
                        "process-1", "work-order", "工单流程", "PUBLISHED")));
        when(capabilityService.evaluateConfiguredAction(
                anyString(), any(), any(), any()))
                .thenReturn(EntityActionCapabilityDTO.allowed());
        when(capabilityService.evaluateApprovalAction(
                anyString(), any(), any(), any()))
                .thenReturn(EntityActionCapabilityDTO.allowed());
    }

    @Test
    void exactPublishedSnapshotUsesCanonicalCreateButtonsWithoutFollowingActive() {
        EntityForm pinned = publishedForm(defaultActionBar());

        List<FormActionRuntimeDTO> actions =
                service.resolveTrustedPublishedSnapshot(
                        pinned, definition, "CREATE", null);

        assertEquals(List.of("close", "reset", "save", "saveAndStart"),
                actions.stream().map(FormActionRuntimeDTO::getKey).toList());
        assertEquals(List.of(10, 20, 30, 40),
                actions.stream().map(FormActionRuntimeDTO::getSort).toList());
        assertEquals(List.of("取消", "重置", "保存", "保存并发起流程"),
                actions.stream().map(FormActionRuntimeDTO::getLabel).toList());
        assertTrue(actions.stream().allMatch(FormActionRuntimeDTO::isVisible));
        assertTrue(actions.stream().allMatch(FormActionRuntimeDTO::isEnabled));
        assertTrue(actions.stream().allMatch(action ->
                "DEFAULT".equals(action.getButtonAppearance())));
        // 此入口消费调用方钉定快照，不得再次读草稿、ACTIVE 或发布解析服务。
        verifyNoInteractions(
                formMapper, formNodeMapper, definitionMapper,
                dataService, releaseService);
        verify(capabilityService, times(2)).evaluateConfiguredAction(
                "work_order", "entity:work_order:create", null, null);
    }

    @Test
    void exposesCustomButtonAppearanceAndDefaultsLegacyConfiguration() {
        EntityForm configured = publishedForm("""
                {"actionBar":{"version":1,"builtInOverrides":{},
                  "customButtons":[{"key":"generate","label":"生成",
                    "icon":"Document","buttonAppearance":"ROUND",
                    "enabled":true,"modes":["create"],
                    "perm":"entity:work_order:generate"}]}}
                """);

        FormActionRuntimeDTO custom = action(
                service.resolveTrustedPublishedSnapshot(
                        configured, definition, "create", null),
                "generate");
        FormActionRuntimeDTO legacy = action(
                service.resolveTrustedPublishedSnapshot(
                        customButtonForm("create"), definition,
                        "create", null),
                "generate");
        EntityForm incompleteCircle = publishedForm("""
                {"actionBar":{"version":1,"builtInOverrides":{},
                  "customButtons":[{"key":"generate","label":"生成",
                    "icon":"UnknownIcon","buttonAppearance":"CIRCLE",
                    "enabled":true,
                    "modes":["create"],
                    "perm":"entity:work_order:generate"}]}}
                """);
        FormActionRuntimeDTO safeFallback = action(
                service.resolveTrustedPublishedSnapshot(
                        incompleteCircle, definition, "create", null),
                "generate");

        assertEquals("ROUND", custom.getButtonAppearance());
        assertEquals("DEFAULT", legacy.getButtonAppearance());
        assertEquals("DEFAULT", safeFallback.getButtonAppearance());
    }

    @Test
    void mappedUserCreatePermissionControlsBothMutationButtons() {
        when(capabilityService.evaluateConfiguredAction(
                eq("work_order"), eq("entity:work_order:create"),
                isNull(), isNull()))
                .thenReturn(EntityActionCapabilityDTO.hidden("缺少新增权限"));

        List<FormActionRuntimeDTO> actions =
                service.resolveTrustedPublishedSnapshot(
                        publishedForm(defaultActionBar()),
                        definition, "create", null);

        for (String key : List.of("save", "saveAndStart")) {
            FormActionRuntimeDTO action = action(actions, key);
            assertFalse(action.isVisible());
            assertFalse(action.isEnabled());
            assertEquals("缺少新增权限", action.getReason());
        }
        assertTrue(action(actions, "close").isEnabled());
        assertTrue(action(actions, "reset").isEnabled());
    }

    @Test
    void unpublishedWorkflowHidesSaveAndStartButKeepsSave() {
        when(processCatalogPort.findItemsByIds(List.of("process-1")))
                .thenReturn(Map.of("process-1", new ProcessCatalogItem(
                        "process-1", "work-order", "工单流程", "DRAFT")));

        List<FormActionRuntimeDTO> actions =
                service.resolveTrustedPublishedSnapshot(
                        publishedForm(defaultActionBar()),
                        definition, "create", null);

        assertTrue(action(actions, "save").isVisible());
        assertTrue(action(actions, "save").isEnabled());
        assertFalse(action(actions, "saveAndStart").isVisible());
        assertEquals("当前数据不能发起流程",
                action(actions, "saveAndStart").getReason());
        verify(capabilityService, times(1)).evaluateConfiguredAction(
                "work_order", "entity:work_order:create", null, null);
    }

    @Test
    void viewOverrideAndAvailabilityRuleUseAlreadyAuthorizedRow() {
        EntityDataDTO authorizedRow = new EntityDataDTO();
        authorizedRow.setId("record-1");
        authorizedRow.setStatus("LOCKED");
        when(capabilityService.evaluateConfiguredAction(
                eq("work_order"), isNull(),
                any(EntityActionRuleDTO.class), same(authorizedRow)))
                .thenReturn(EntityActionCapabilityDTO.disabled("记录已锁定"));
        String actionBar = """
                {"actionBar":{"version":1,"builtInOverrides":{"close":{
                  "labelByMode":{"view":"退出详情"},"buttonType":"danger",
                  "sort":91,"availabilityRule":{"version":2,
                    "disabledMessage":"记录已锁定",
                    "enabledWhen":{"type":"FIELD","field":"status",
                      "operator":"EQ","value":"OPEN"}}}},"customButtons":[]}}
                """;

        List<FormActionRuntimeDTO> actions =
                service.resolveTrustedPublishedSnapshot(
                        publishedForm(actionBar), definition,
                        "view", authorizedRow);

        assertEquals(1, actions.size());
        FormActionRuntimeDTO close = actions.get(0);
        assertEquals("close", close.getKey());
        assertEquals("退出详情", close.getLabel());
        assertEquals("danger", close.getButtonType());
        assertEquals(91, close.getSort());
        assertTrue(close.isVisible());
        assertFalse(close.isEnabled());
        assertEquals("记录已锁定", close.getReason());
        verify(capabilityService).evaluateConfiguredAction(
                eq("work_order"), isNull(),
                any(EntityActionRuleDTO.class), same(authorizedRow));
    }

    @Test
    void formRuleNormalizesCommaSeparatedInValues() {
        EntityDataDTO row = new EntityDataDTO();
        row.setId("record-1");
        String actionBar = """
                {"actionBar":{"version":1,"builtInOverrides":{"close":{
                  "availabilityRule":{"version":2,"disabledMessage":"",
                    "visibleWhen":{"type":"STATUS_CODE","operator":"IN",
                      "value":"DRAFT, REVIEW"}}}},"customButtons":[]}}
                """;

        service.resolveTrustedPublishedSnapshot(
                publishedForm(actionBar), definition, "view", row);

        verify(capabilityService).evaluateConfiguredAction(
                eq("work_order"), isNull(),
                argThat(rule -> List.of("DRAFT", "REVIEW").equals(
                        rule.getVisibleWhen().getValue())),
                same(row));
    }

    @Test
    void builtInMutationRequiresPinnedContextAndRejectsHiddenOverride() {
        FormActionResolveRequest incomplete = new FormActionResolveRequest();
        incomplete.setFormId("form-1");
        incomplete.setMode("create");
        com.workflow.core.error.BusinessForbiddenException missing =
                assertThrows(
                        com.workflow.core.error.BusinessForbiddenException.class,
                        () -> service.requireBuiltInMutationAction(
                                incomplete, "save"));
        assertEquals("FORM_ACTION_RELEASE_CONTEXT_REQUIRED",
                missing.getErrorCode());

        EntityForm stored = publishedForm("""
                {"actionBar":{"version":1,"builtInOverrides":{"save":{
                  "availabilityRule":{"version":2,
                    "visibleWhen":{"type":"FIELD","field":"status",
                      "operator":"EQ","value":"DRAFT"},
                    "disabledMessage":""}}},"customButtons":[]}}
                """);
        stored.setId("form-1");
        stored.setActiveReleaseId("release-1");
        when(formMapper.selectById("form-1")).thenReturn(stored);
        when(definitionMapper.selectById("entity-1"))
                .thenReturn(definition);
        when(releaseService.resolveRuntimeEventSnapshot(
                "form-1", "release-1", 1, "signed-token"))
                .thenReturn(new UiConfigReleaseService.ResolvedUiEventSnapshot(
                        snapshot(stored),
                        "release-1",
                        1,
                        "release-1",
                        false,
                        "hash-1"));
        when(capabilityService.evaluateConfiguredAction(
                eq("work_order"), isNull(),
                any(EntityActionRuleDTO.class), isNull()))
                .thenReturn(EntityActionCapabilityDTO.hidden(
                        "当前数据不满足显示条件"));
        FormActionResolveRequest request = new FormActionResolveRequest();
        request.setFormId("form-1");
        request.setReleaseId("release-1");
        request.setReleaseVersion(1);
        request.setReleaseResolutionToken("signed-token");
        request.setEntityCode("work_order");
        request.setMode("create");

        com.workflow.core.error.BusinessForbiddenException denied =
                assertThrows(
                        com.workflow.core.error.BusinessForbiddenException.class,
                        () -> service.requireBuiltInMutationAction(
                                request, "save"));

        assertEquals("FORM_BUILT_IN_ACTION_DENIED",
                denied.getErrorCode());
        assertEquals("当前数据不满足显示条件",
                denied.getMessage());
    }

    @Test
    void systemEntityCloseHonorsVisibilityAndEnabledConditions() {
        definition.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
        EntityForm form = publishedForm("""
                {"actionBar":{"version":1,"builtInOverrides":{"close":{
                  "availabilityRule":{"version":2,
                    "visibleWhen":{"type":"USER_FIELD","field":"username",
                      "operator":"NOT_EMPTY"},
                    "enabledWhen":{"type":"USER_FIELD","field":"deptId",
                      "operator":"EQ","value":"dept-allowed"},
                    "disabledMessage":"当前用户不可关闭"}}},
                  "customButtons":[]}}
                """);
        when(capabilityService.evaluateConfiguredAction(
                eq("work_order"), isNull(),
                any(EntityActionRuleDTO.class), isNull()))
                .thenReturn(
                        EntityActionCapabilityDTO.hidden("不显示"),
                        EntityActionCapabilityDTO.disabled("当前用户不可关闭"));

        FormActionRuntimeDTO hidden = service
                .resolveTrustedPublishedSnapshot(
                        form, definition, "view", null).get(0);
        FormActionRuntimeDTO disabled = service
                .resolveTrustedPublishedSnapshot(
                        form, definition, "view", null).get(0);

        assertFalse(hidden.isVisible());
        assertTrue(disabled.isVisible());
        assertFalse(disabled.isEnabled());
        assertEquals("当前用户不可关闭", disabled.getReason());
    }

    @Test
    void trustedButtonSnapshotDoesNotResolveActiveReleaseAgain() {
        EntityForm form = publishedForm("""
                {"actionBar":{"version":1,"builtInOverrides":{},
                  "customButtons":[{"key":"generate","label":"生成",
                    "enabled":true,"modes":["create"],
                    "perm":"entity:work_order:create"}]}}
                """);
        form.setId("form-1");
        when(definitionMapper.selectById("entity-1"))
                .thenReturn(definition);
        UiEventExecuteRequest request = buttonRequest(
                "create", null);

        service.requireCustomButton(
                request,
                Map.of(
                        "configType", "FORM",
                        "form", objectMapper.convertValue(
                                form, Map.class),
                        "nodes", List.of(),
                        "eventBindings", List.of()));

        verifyNoInteractions(formMapper, formNodeMapper, dataService,
                releaseService);
        verify(capabilityService).requireStandardPermission(
                "work_order",
                EntityPermissionAction.CREATE);
        assertEquals("generate",
                request.getServerPublishedButton().get("key"));
        assertEquals("生成",
                request.getServerPublishedButton().get("label"));
    }

    @Test
    void clientCannotClaimApproveModeForCreateRecordContext() {
        EntityForm form = publishedForm("""
                {"actionBar":{"version":1,"builtInOverrides":{},
                  "customButtons":[{"key":"generate","label":"生成",
                    "enabled":true,"modes":["approve"],
                    "perm":"entity:work_order:approve"}]}}
                """);
        form.setId("form-1");
        when(definitionMapper.selectById("entity-1"))
                .thenReturn(definition);

        assertThrows(
                ForbiddenException.class,
                () -> service.requireCustomButton(
                        buttonRequest("approve", null),
                        Map.of(
                                "configType", "FORM",
                                "form", objectMapper.convertValue(
                                        form, Map.class),
                                "nodes", List.of(),
                                "eventBindings", List.of())));

        verifyNoInteractions(dataService, releaseService);
    }

    @Test
    void customCreateButtonIntersectsCreatePermission() {
        when(capabilityService.evaluateConfiguredAction(
                eq("work_order"), eq("entity:work_order:create"),
                isNull(), isNull()))
                .thenReturn(EntityActionCapabilityDTO.hidden(
                        "缺少新增权限"));

        FormActionRuntimeDTO custom = action(
                service.resolveTrustedPublishedSnapshot(
                        customButtonForm("create"),
                        definition,
                        "create",
                        null),
                "generate");

        assertFalse(custom.isVisible());
        assertFalse(custom.isEnabled());
        assertEquals("缺少新增权限", custom.getReason());
    }

    @Test
    void customEditButtonIntersectsUpdatePermission() {
        EntityDataDTO row = new EntityDataDTO();
        row.setId("record-1");
        when(capabilityService.evaluateConfiguredAction(
                eq("work_order"), eq("entity:work_order:update"),
                isNull(), same(row)))
                .thenReturn(EntityActionCapabilityDTO.disabled(
                        "记录不可编辑"));

        FormActionRuntimeDTO custom = action(
                service.resolveTrustedPublishedSnapshot(
                        customButtonForm("edit"),
                        definition,
                        "edit",
                        row),
                "generate");

        assertTrue(custom.isVisible());
        assertFalse(custom.isEnabled());
        assertEquals("记录不可编辑", custom.getReason());
    }

    @Test
    void customButtonIntersectionAlwaysPrioritizesHidden() {
        EntityDataDTO row = new EntityDataDTO();
        row.setId("record-1");
        when(capabilityService.evaluateConfiguredAction(
                eq("work_order"), eq("entity:work_order:update"),
                isNull(), same(row)))
                .thenReturn(EntityActionCapabilityDTO.disabled(
                        "记录不可编辑"));
        when(capabilityService.evaluateConfiguredAction(
                eq("work_order"), eq("entity:work_order:generate"),
                any(EntityActionRuleDTO.class), same(row)))
                .thenReturn(EntityActionCapabilityDTO.hidden(
                        "不满足显示条件"));
        EntityForm form = publishedForm("""
                {"actionBar":{"version":1,"builtInOverrides":{},
                  "customButtons":[{"key":"generate","label":"生成",
                    "enabled":true,"modes":["edit"],
                    "perm":"entity:work_order:generate",
                    "availabilityRule":{"version":2,"disabledMessage":"",
                      "visibleWhen":{"type":"FIELD","field":"status",
                        "operator":"EQ","value":"DRAFT"}}}]}}
                """);

        FormActionRuntimeDTO custom = action(
                service.resolveTrustedPublishedSnapshot(
                        form, definition, "edit", row),
                "generate");

        assertFalse(custom.isVisible());
        assertFalse(custom.isEnabled());
        assertEquals("不满足显示条件", custom.getReason());
    }

    @Test
    void customApproveButtonRequiresRealActionableTask() {
        EntityDataDTO row = new EntityDataDTO();
        row.setId("record-1");
        when(capabilityService.evaluateApprovalAction(
                eq("work_order"), same(row),
                any(EntityActionRuleDTO.class), isNull()))
                .thenReturn(EntityActionCapabilityDTO.hidden(
                        "当前用户没有可办理任务"));

        FormActionRuntimeDTO custom = action(
                service.resolveTrustedPublishedSnapshot(
                        customButtonForm("approve"),
                        definition,
                        "approve",
                        row),
                "generate");

        assertFalse(custom.isVisible());
        assertFalse(custom.isEnabled());
        assertEquals("当前用户没有可办理任务", custom.getReason());
    }

    @Test
    void customApproveButtonWithoutRecordContextIsDisabled() {
        FormActionRuntimeDTO custom = action(
                service.resolveTrustedPublishedSnapshot(
                        customButtonForm("approve"),
                        definition,
                        "approve",
                        null),
                "generate");

        assertTrue(custom.isVisible());
        assertFalse(custom.isEnabled());
        assertEquals("缺少可访问的记录上下文", custom.getReason());
    }

    @Test
    void legacyCustomButtonWithoutModesDefaultsToEditOnly() {
        EntityForm form = publishedForm("""
                {"actionBar":{"version":1,"builtInOverrides":{},
                  "customButtons":[{"key":"generate","label":"生成",
                    "enabled":true,
                    "perm":"entity:work_order:update"}]}}
                """);
        form.setId("form-1");
        when(definitionMapper.selectById("entity-1"))
                .thenReturn(definition);
        EntityDataDTO row = new EntityDataDTO();
        row.setId("record-1");
        when(dataService.findAccessibleById(
                "work_order", "record-1", null))
                .thenReturn(row);

        service.requireCustomButton(
                buttonRequest("edit", "record-1"),
                Map.of(
                        "configType", "FORM",
                        "form", objectMapper.convertValue(
                                form, Map.class),
                        "nodes", List.of(),
                        "eventBindings", List.of()));

        verify(capabilityService).requireStandardPermission(
                "work_order", EntityPermissionAction.UPDATE);
        assertFalse(service.resolveTrustedPublishedSnapshot(
                        form, definition, "create", null)
                .stream()
                .anyMatch(action -> "generate".equals(action.getKey())));
    }

    @Test
    void approveButtonBindsTrustedTaskAndPinnedProcessRelease() {
        EntityForm form = customButtonForm("approve");
        form.setId("form-1");
        when(definitionMapper.selectById("entity-1"))
                .thenReturn(definition);
        EntityDataDTO row = approvalRow();
        when(dataService.findAccessibleById(
                "work_order", "record-1", null))
                .thenReturn(row);
        when(capabilityService.evaluateApprovalAction(
                eq("work_order"), same(row),
                any(EntityActionRuleDTO.class), isNull()))
                .thenReturn(EntityActionCapabilityDTO.allowedForTask(
                        "task-r1"));
        when(capabilityService.findActionableApprovalTaskContext(
                row, "task-r1"))
                .thenReturn(Optional.of(approvalTask("task-r1")));
        UiEventExecuteRequest request = buttonRequest(
                "approve", "record-1");
        request.setTaskId("task-r1");
        request.setReleaseResolutionToken("active-task-token-r1");

        String mode = service.requireCustomButton(
                request,
                snapshot(form),
                "form-release-r1",
                1);

        assertEquals("approve", mode);
        assertEquals("task-r1", request.getServerTaskId());
        assertEquals("process-r1",
                request.getServerProcessInstanceId());
        verify(releaseService).requireActiveTaskReleaseToken(
                "active-task-token-r1",
                "form-1",
                "form-release-r1",
                1,
                "process-history-r1",
                "approve-node",
                "task-r1",
                "process-r1",
                "work_order",
                "record-1");
    }

    @Test
    void approveButtonAcceptsExactOlderParallelTaskInsteadOfLatestSummary() {
        EntityForm form = customButtonForm("approve");
        form.setId("form-1");
        when(definitionMapper.selectById("entity-1"))
                .thenReturn(definition);
        EntityDataDTO row = approvalRow();
        when(dataService.findAccessibleById(
                "work_order", "record-1", null))
                .thenReturn(row);
        // 能力摘要可只带同记录的最近待办；执行必须以显式 taskId 精确回查。
        when(capabilityService.evaluateApprovalAction(
                eq("work_order"), same(row),
                any(EntityActionRuleDTO.class), isNull()))
                .thenReturn(EntityActionCapabilityDTO.allowedForTask(
                        "task-latest"));
        when(capabilityService.findActionableApprovalTaskContext(
                row, "task-older"))
                .thenReturn(Optional.of(approvalTask("task-older")));
        UiEventExecuteRequest request = buttonRequest(
                "approve", "record-1");
        request.setTaskId("task-older");
        request.setReleaseResolutionToken("active-task-token-older");

        assertEquals("approve", service.requireCustomButton(
                request,
                snapshot(form),
                "form-release-r1",
                1));

        assertEquals("task-older", request.getServerTaskId());
        verify(releaseService).requireActiveTaskReleaseToken(
                "active-task-token-older",
                "form-1",
                "form-release-r1",
                1,
                "process-history-r1",
                "approve-node",
                "task-older",
                "process-r1",
                "work_order",
                "record-1");
    }

    @Test
    void approveButtonRejectsTransferredTaskCoordinate() {
        EntityForm form = customButtonForm("approve");
        form.setId("form-1");
        when(definitionMapper.selectById("entity-1"))
                .thenReturn(definition);
        EntityDataDTO row = approvalRow();
        when(dataService.findAccessibleById(
                "work_order", "record-1", null))
                .thenReturn(row);
        when(capabilityService.evaluateApprovalAction(
                eq("work_order"), same(row),
                any(EntityActionRuleDTO.class), isNull()))
                .thenReturn(EntityActionCapabilityDTO.allowedForTask(
                        "task-current"));
        UiEventExecuteRequest request = buttonRequest(
                "approve", "record-1");
        request.setTaskId("task-transferred");
        request.setReleaseResolutionToken("old-task-token");

        com.workflow.core.error.BusinessForbiddenException error =
                assertThrows(
                        com.workflow.core.error.BusinessForbiddenException.class,
                        () -> service.requireCustomButton(
                                request,
                                snapshot(form),
                                "form-release-r1",
                                1));

        assertEquals("UI_EVENT_APPROVAL_TASK_CONTEXT_MISMATCH",
                error.getErrorCode());
        verifyNoInteractions(releaseService);
    }

    @Test
    void approveActionResolveAlsoRequiresActiveTaskReleaseToken() {
        EntityForm stored = customButtonForm("approve");
        stored.setId("form-1");
        stored.setActiveReleaseId("current-r2");
        when(formMapper.selectById("form-1")).thenReturn(stored);
        when(definitionMapper.selectById("entity-1"))
                .thenReturn(definition);
        EntityDataDTO row = approvalRow();
        when(dataService.findAccessibleById(
                "work_order", "record-1", null))
                .thenReturn(row);
        when(releaseService.resolveRuntimeEventSnapshot(
                "form-1", null, null, null))
                .thenReturn(new UiConfigReleaseService.ResolvedUiEventSnapshot(
                        snapshot(stored),
                        "current-r2",
                        2,
                        "current-r2",
                        false,
                        "hash-r2"));
        when(capabilityService.evaluateApprovalAction(
                eq("work_order"), same(row),
                any(EntityActionRuleDTO.class), isNull()))
                .thenReturn(EntityActionCapabilityDTO.allowedForTask(
                        "task-r1"));
        when(capabilityService.findActionableApprovalTaskContext(
                row, "task-r1"))
                .thenReturn(Optional.of(approvalTask("task-r1")));
        org.mockito.Mockito.doThrow(
                        new com.workflow.core.error.BusinessForbiddenException(
                                "UI_EVENT_APPROVAL_RELEASE_CONTEXT_REQUIRED",
                                "missing token"))
                .when(releaseService)
                .requireActiveTaskReleaseToken(
                        isNull(), eq("form-1"), eq("current-r2"), eq(2),
                        eq("process-history-r1"), eq("approve-node"),
                        eq("task-r1"), eq("process-r1"),
                        eq("work_order"), eq("record-1"));
        FormActionResolveRequest request = new FormActionResolveRequest();
        request.setFormId("form-1");
        request.setEntityCode("work_order");
        request.setMode("approve");
        request.setRecordId("record-1");
        request.setTaskId("task-r1");

        com.workflow.core.error.BusinessForbiddenException error =
                assertThrows(
                        com.workflow.core.error.BusinessForbiddenException.class,
                        () -> service.resolve(request));

        assertEquals("UI_EVENT_APPROVAL_RELEASE_CONTEXT_REQUIRED",
                error.getErrorCode());
    }

    private Map<String, Object> snapshot(EntityForm form) {
        return Map.of(
                "configType", "FORM",
                "form", objectMapper.convertValue(form, Map.class),
                "nodes", List.of(),
                "eventBindings", List.of());
    }

    private static EntityDataDTO approvalRow() {
        EntityDataDTO row = new EntityDataDTO();
        row.setId("record-1");
        row.setEntityCode("work_order");
        row.setProcessInstanceId("process-r1");
        return row;
    }

    private static ActionableTaskContext approvalTask(String taskId) {
        return new ActionableTaskContext(
                taskId,
                "process-r1",
                "definition-r1",
                "process-history-r1",
                "approve-node",
                "work_order",
                "record-1");
    }

    private static EntityForm publishedForm(String viewConfig) {
        EntityForm form = new EntityForm();
        form.setId("pinned-form-release-v3");
        form.setEntityId("entity-1");
        form.setViewConfig(viewConfig);
        form.setNodes(List.of());
        return form;
    }

    private static EntityForm customButtonForm(String mode) {
        return publishedForm("""
                {"actionBar":{"version":1,"builtInOverrides":{},
                  "customButtons":[{"key":"generate","label":"生成",
                    "enabled":true,"modes":["%s"],
                    "perm":"entity:work_order:generate"}]}}
                """.formatted(mode));
    }

    private static String defaultActionBar() {
        return """
                {"actionBar":{"version":1,"builtInOverrides":{},
                  "customButtons":[]}}
                """;
    }

    private static UiEventExecuteRequest buttonRequest(
            String mode,
            String recordId) {
        UiEventExecuteRequest request = new UiEventExecuteRequest();
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setEntityCode("work_order");
        request.setEventCode("FORM_BUTTON_CLICK");
        request.setTargetType("BUTTON");
        request.setTargetKey("generate");
        request.setRecordId(recordId);
        request.setContext(Map.of("mode", mode));
        request.setInput(Map.of());
        return request;
    }

    private static FormActionRuntimeDTO action(
            List<FormActionRuntimeDTO> actions,
            String key) {
        return actions.stream()
                .filter(item -> key.equals(item.getKey()))
                .findFirst()
                .orElseThrow();
    }
}
