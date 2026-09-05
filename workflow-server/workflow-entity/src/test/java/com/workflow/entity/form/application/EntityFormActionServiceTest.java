package com.workflow.entity.form.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.api.response.FormActionRuntimeDTO;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import java.util.List;
import java.util.Map;
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
        // 此入口消费调用方钉定快照，不得再次读草稿、ACTIVE 或发布解析服务。
        verifyNoInteractions(
                formMapper, formNodeMapper, definitionMapper,
                dataService, releaseService);
        verify(capabilityService, times(2)).evaluateConfiguredAction(
                "work_order", "entity:work_order:create", null, null);
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
                  "sort":91,"availabilityRule":{"version":1,
                    "unavailableBehavior":"DISABLE","message":"记录已锁定",
                    "root":{"type":"FIELD","field":"status",
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

    private static EntityForm publishedForm(String viewConfig) {
        EntityForm form = new EntityForm();
        form.setId("pinned-form-release-v3");
        form.setEntityId("entity-1");
        form.setViewConfig(viewConfig);
        form.setNodes(List.of());
        return form;
    }

    private static String defaultActionBar() {
        return """
                {"actionBar":{"version":1,"builtInOverrides":{},
                  "customButtons":[]}}
                """;
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
