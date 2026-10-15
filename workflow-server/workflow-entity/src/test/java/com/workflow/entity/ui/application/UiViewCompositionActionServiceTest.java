package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationBatchResult;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationResult;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.contracts.entity.ui.model.UiActionCommandPlan;
import com.workflow.contracts.entity.ui.model.UiActionMutationCommand;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.api.request.UiViewCompositionActionCapabilitiesRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionActionRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionActionCapabilitiesResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionResolveResponse;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UiViewCompositionActionServiceTest {

    @Mock
    private UiViewCompositionRuntimeService runtimeService;
    @Mock
    private UiConfigReleaseMapper releaseMapper;
    @Mock
    private UiConfigReleaseService releaseService;
    @Mock
    private EntityPublishedSnapshotService entitySnapshotService;
    @Mock
    private EntityDefinitionMapper entityMapper;
    @Mock
    private EntityDataDynamicService dynamicDataService;
    @Mock
    private SystemEntityReadService systemEntityReadService;
    @Mock
    private EntityActionCapabilityService capabilityService;
    @Mock
    private EntityMutationPort mutationPort;
    @Mock
    private EntityAggregateWriter aggregateWriter;
    @Mock
    private UiViewCompositionActionReceiptService actionReceiptService;
    @Mock
    private UiInterfaceExtensionService dataSourceService;

    private UiViewCompositionActionService service;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("action-user", "tester");
        SysMenuMapper menuMapper = org.mockito.Mockito.mock(
                SysMenuMapper.class);
        when(menuMapper.selectPermsByUserId("action-user"))
                .thenReturn(Set.of("*"));
        ReflectionTestUtils.setField(
                PermissionUtil.class, "staticMenuMapper", menuMapper);
        service = new UiViewCompositionActionService(
                runtimeService,
                releaseMapper,
                releaseService,
                entitySnapshotService,
                entityMapper,
                dynamicDataService,
                systemEntityReadService,
                capabilityService,
                mutationPort,
                aggregateWriter,
                actionReceiptService,
                dataSourceService,
                new ObjectMapper());
        when(actionReceiptService.acquire(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation ->
                        new UiViewCompositionActionReceiptService.AcquireResult(
                                new com.workflow.contracts.entity.mutation.model.EntityMutationCommand(
                                        "batch-receipt",
                                        "source_entity",
                                        "source-1",
                                        com.workflow.contracts.entity.mutation.model.EntityMutationOperationType.UPDATE,
                                        Map.of(),
                                        com.workflow.contracts.entity.mutation.model.EntityMutationContext.builder(
                                                com.workflow.contracts.entity.mutation.model.EntityMutationSourceType.FORM,
                                                "TEST",
                                                "测试")
                                                .trace(
                                                        "test-trace",
                                                        "test-batch-idempotency")
                                                .build()),
                                null));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void readonlyParentCannotReopenWriteActionsInChild() {
        formFixture(List.of("VIEW", "CREATE", "EDIT"), "target-1");
        UiViewCompositionResolveResponse resolved=runtimeService.resolve(compositionContext());
        when(runtimeService.resolve(any())).thenReturn(resolved.toBuilder().readonlyContext(true).build());
        UiViewCompositionActionCapabilitiesRequest request=new UiViewCompositionActionCapabilitiesRequest();
        request.setCompositionContext(compositionContext());
        var actions=service.capabilities(request).getActionCapabilities();
        assertTrue(actions.get("VIEW").isAvailable());
        assertFalse(actions.get("CREATE").isAvailable());
        assertFalse(actions.get("EDIT").isAvailable());
        verify(mutationPort,never()).executeBatch(any());
    }

    @Test
    void unauthorizedBusinessContextFailsBeforeMutation() {
        when(runtimeService.resolve(any()))
                .thenThrow(new BusinessForbiddenException(
                        "VIEW_COMPOSITION_ROW_OUT_OF_SCOPE",
                        "来源记录不可访问"));

        assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request(
                        "EXECUTE_INTERFACE", "op-1", "target-1")));

        verify(runtimeService).resolve(any());
        verify(mutationPort, never()).executeBatch(any());
    }

    @Test
    void formCapabilitiesExposeServerResolvedCreateValuesAndExactEdit() {
        formFixture(
                List.of("VIEW", "CREATE", "EDIT"),
                "target-1");
        UiViewCompositionActionCapabilitiesRequest request =
                new UiViewCompositionActionCapabilitiesRequest();
        request.setCompositionContext(compositionContext());

        UiViewCompositionActionCapabilitiesResponse response =
                service.capabilities(request);

        assertEquals(Set.of("VIEW", "CREATE", "EDIT"),
                response.getActionCapabilities().keySet());
        assertTrue(response.getActionCapabilities().get("CREATE")
                .isAvailable());
        assertEquals(
                Map.of("displayName", "来源名称"),
                response.getActionCapabilities().get("CREATE")
                        .getInitialValues());
        assertTrue(response.getActionCapabilities().get("EDIT")
                .isAvailable());
    }

    @Test
    void formSubmissionRejectsWrongRecordAndAcceptsExactPinnedContext() {
        formFixture(
                List.of("VIEW", "CREATE", "EDIT"),
                "target-1");

        Map<String, Object> trusted = service.authorizeFormSubmission(
                compositionContext(),
                "CREATE",
                "target_entity",
                null,
                "target-form",
                "target-config-hash");

        assertEquals(Map.of("displayName", "来源名称"), trusted);
        BusinessForbiddenException error = assertThrows(
                BusinessForbiddenException.class,
                () -> service.authorizeFormSubmission(
                        compositionContext(),
                        "EDIT",
                        "target_entity",
                        "other-target",
                        "target-form",
                        "target-config-hash"));
        assertEquals("VIEW_COMPOSITION_EDIT_TARGET_MISMATCH",
                error.getErrorCode());
    }

    @Test
    void editCapabilityRequiresResolvedTargetRecord() {
        formFixture(List.of("VIEW", "EDIT"), null);
        UiViewCompositionActionCapabilitiesRequest request =
                new UiViewCompositionActionCapabilitiesRequest();
        request.setCompositionContext(compositionContext());

        UiViewCompositionActionCapabilitiesResponse response =
                service.capabilities(request);

        assertFalse(response.getActionCapabilities().get("EDIT")
                .isAvailable());
        assertTrue(response.getActionCapabilities().get("EDIT")
                .getReason().contains("没有解析到"));
    }

    /** 删除的内置动作没有独立执行分支，也不能仅凭请求动作名写实体。 */
    @ParameterizedTest
    @ValueSource(strings = {"SELECT", "LINK", "UNLINK", "SAVE_WITH_FORM"})
    void removedBuiltinActionsCannotExecute(String action) {
        listFixture(List.of("VIEW"));

        BusinessForbiddenException failure = assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request(action, "removed-action", "target-1")));

        assertEquals("VIEW_COMPOSITION_ACTION_NOT_ALLOWED", failure.getErrorCode());
        verify(mutationPort, never()).executeBatch(any());
        verify(dataSourceService, never()).executePinnedOperation(any(), any(), any());
    }

    /** 关联内容的列表查询仍保持服务端计算的关系范围。 */
    @Test
    void listContextRetainsResolvedRelationshipFilters() {
        listFixture(List.of("VIEW"));

        var context = service.resolveListContext(compositionContext());

        assertEquals(Map.of("sourceRef", "source-1", "sourceRef_op", "EQ"),
                context.fixedFilters());
        assertEquals("target-list", context.targetContentId());
        assertFalse(context.matchNone());
    }

    /** FORM/LIST 独立扩展页面继续执行已发布的 READ 动作。 */
    @ParameterizedTest
    @CsvSource({"FORM,SAME_RECORD", "FORM,INTERFACE_SERVICE", "LIST,SAME_RECORD", "LIST,INTERFACE_SERVICE"})
    void readInterfaceActionReturnsPublishedResultWithoutEntityMutation(String contentType, String relationType) {
        fixture(List.of("VIEW"), contentType, "target-1");
        addWriteActionService("CALCULATE_TOTAL", new UiActionCommandPlan(List.of(), Map.of()));
        replaceRelationType(relationType);
        when(dataSourceService.validatePinnedActionOperation(
                "{}", "a".repeat(64), "service-action", "service.action", 1, "execute", "FORM"))
                .thenReturn(new UiInterfaceExtensionService.ActionOperationDescriptor(
                        "service-action", "service.action", 1, "execute", "READ",
                        "REGISTERED_PROVIDER", "provider.action", "FORM"));
        when(dataSourceService.executePinnedOperation(any(), any(), any()))
                .thenReturn(Map.of("total", 42));

        var response = service.execute(request("CALCULATE_TOTAL", "read-op", "target-1"));

        assertEquals(Map.of("total", 42), response.getActionResult());
        verify(mutationPort, never()).executeBatch(any());
    }

    /** 普通关系的旧发布不签发扩展能力，也不能通过直接请求动作端点执行残留绑定。 */
    @ParameterizedTest
    @ValueSource(strings = {"ENTITY_RELATION", "REFERENCE_FIELD", "REVERSE_REFERENCE", "FIELD_MATCH", ""})
    void ordinaryRelationshipCannotExecuteHistoricalInterfaceAction(String relationType) {
        listFixture(List.of("VIEW"));
        addWriteActionService("CALCULATE_TOTAL", new UiActionCommandPlan(List.of(), Map.of()));
        replaceRelationType(relationType);
        UiViewCompositionActionCapabilitiesRequest capabilitiesRequest = new UiViewCompositionActionCapabilitiesRequest();
        capabilitiesRequest.setCompositionContext(compositionContext());

        var capabilities = service.capabilities(capabilitiesRequest).getActionCapabilities();

        assertFalse(capabilities.containsKey("CALCULATE_TOTAL"));
        assertTrue(capabilities.get("VIEW").isAvailable());
        assertEquals("target-list", service.resolveListContext(compositionContext()).targetContentId());
        BusinessForbiddenException failure = assertThrows(BusinessForbiddenException.class,
                () -> service.execute(request("CALCULATE_TOTAL", "legacy-ordinary", "target-1")));
        assertEquals("VIEW_COMPOSITION_ACTION_NOT_ALLOWED", failure.getErrorCode());
        verify(dataSourceService, never()).validatePinnedActionOperation(any(), any(), any(), any(), any(), any(), any());
        verify(dataSourceService, never()).executePinnedOperation(any(), any(), any());
        verify(dataSourceService, never()).planPinnedActionOperation(any(), any(), any());
        verify(mutationPort, never()).executeBatch(any());
    }

    /** 接口扩展仍必须拒绝关联范围之外的可见记录。 */
    @Test
    void interfaceActionRejectsRecordOutsideResolvedListScope() {
        listFixture(List.of("VIEW"));
        addWriteActionService("RECALCULATE", new UiActionCommandPlan(List.of(), Map.of()));
        when(dynamicDataService.findPage(
                org.mockito.ArgumentMatchers.eq("target_entity"),
                org.mockito.ArgumentMatchers.eq("target-list-key"),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.eq(1L), org.mockito.ArgumentMatchers.eq(2L)))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 2));

        BusinessForbiddenException failure = assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request("RECALCULATE", "out-of-scope", "target-1")));

        assertEquals("VIEW_COMPOSITION_ACTION_TARGET_OUT_OF_SCOPE", failure.getErrorCode());
        verify(mutationPort, never()).executeBatch(any());
    }

    /** Provider 不能把计划中的记录 ID 当作新的授权来源。 */
    @Test
    void localWriteProviderCannotMutateUnselectedRelatedRecord() {
        listFixture(List.of("VIEW"));
        addWriteActionService(
                "EXECUTE_INTERFACE",
                new UiActionCommandPlan(
                        List.of(new UiActionMutationCommand(
                                "target_entity",
                                "target-2",
                                EntityMutationOperationType.UPDATE,
                                Map.of("displayName", "越界修改"))),
                        Map.of()));

        BusinessForbiddenException failure = assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request(
                        "EXECUTE_INTERFACE",
                        "service-out-of-scope",
                        "target-1")));

        assertEquals(
                "VIEW_COMPOSITION_ACTION_TARGET_OUT_OF_SCOPE",
                failure.getErrorCode());
        verify(mutationPort, never()).executeBatch(any());
    }

    /** 子命令回执键必须随批次主体隔离键变化，而不是只依赖客户端 operationId。 */
    @Test
    void localWriteCommandIdempotencyDerivesFromSubjectBoundBatchKey() {
        listFixture(List.of("VIEW"));
        addWriteActionService(
                "EXECUTE_INTERFACE",
                new UiActionCommandPlan(
                        List.of(new UiActionMutationCommand(
                                "target_entity",
                                "target-1",
                                EntityMutationOperationType.UPDATE,
                                Map.of("displayName", "重新计算"))),
                        Map.of()));
        when(actionReceiptService.acquire(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(batchReceipt("tenant-user-source-a"))
                .thenReturn(batchReceipt("tenant-user-source-b"));

        service.execute(request(
                "EXECUTE_INTERFACE", "same-client-op", "target-1"));
        service.execute(request(
                "EXECUTE_INTERFACE", "same-client-op", "target-1"));

        ArgumentCaptor<EntityMutationBatchCommand> batches =
                ArgumentCaptor.forClass(EntityMutationBatchCommand.class);
        verify(mutationPort, org.mockito.Mockito.times(2))
                .executeBatch(batches.capture());
        String first = batches.getAllValues().get(0).commands().get(0)
                .context().idempotencyKey();
        String second = batches.getAllValues().get(1).commands().get(0)
                .context().idempotencyKey();
        assertFalse(first.equals(second));
    }

    private void listFixture(List<String> actions) {
        fixture(actions, "LIST", "target-1");
    }

    private void formFixture(List<String> actions, String targetRecordId) {
        fixture(actions, "FORM", targetRecordId);
    }

    private void fixture(
            List<String> actions,
            String targetContentType,
            String targetRecordId) {
        EntityDefinition sourceEntity = entity(
                "source-entity-id", "source_entity");
        EntityDefinition targetEntity = entity(
                "target-entity-id", "target_entity");
        EntityField ownerName = field(
                "ownerName", EntityField.FieldType.STRING, null);
        ownerName.setEditable(true);
        EntityField targetRef = field(
                "targetRef", EntityField.FieldType.REFERENCE,
                targetEntity.getId());
        EntityField displayName = field(
                "displayName", EntityField.FieldType.STRING, null);
        displayName.setRuntimeReadable(true);
        displayName.setEditable(true);
        EntityField sourceRef = field(
                "sourceRef", EntityField.FieldType.REFERENCE,
                sourceEntity.getId());
        EntityPublishedSnapshot sourceSchema = snapshot(
                "source-history", sourceEntity, List.of(ownerName, targetRef));
        EntityPublishedSnapshot targetSchema = snapshot(
                "target-history", targetEntity, List.of(displayName, sourceRef));
        Map<String, Object> config = Map.of(
                "enabled", true,
                "actions", actions,
                "actionSettings", Map.of(
                        "create", Map.of(
                                "initialMappings", List.of(Map.of(
                                        "sourceField", "ownerName",
                                        "targetField", "displayName",
                                        "required", true)))),
                "entitySnapshots", Map.of(
                        "source", pin(
                                "source-history", sourceEntity, 1, "a"),
                        "target", pin(
                                "target-history", targetEntity, 1, "b")));
        Map<String, Object> ownerSnapshot = Map.of(
                "form", Map.of(
                        "id", "owner-form",
                        "entityId", sourceEntity.getId()),
                "legacyFields", List.of(Map.of(
                        "fieldCode", "ownerName",
                        "isHidden", 0,
                        "isReadonly", 0)),
                "viewCompositions", List.of(Map.of(
                        "compositionKey", "related-targets",
                        "config", config)));
        Map<String, Object> targetSnapshot = "FORM".equals(targetContentType)
                ? Map.of(
                        "form", Map.of(
                                "id", "target-form",
                                "entityId", targetEntity.getId()),
                        "legacyFields", List.of(Map.of(
                                "fieldCode", "displayName",
                                "isHidden", 0,
                                "isReadonly", 0)))
                : Map.of(
                        "list", Map.of(
                                "id", "target-list",
                                "entityId", targetEntity.getId(),
                                "fields", List.of(Map.of(
                                        "fieldCode", "displayName",
                                        "showInList", true,
                                        "dataSourceType", "ENTITY_FIELD"))));

        when(runtimeService.resolve(any()))
                .thenReturn(UiViewCompositionResolveResponse.builder()
                        .ownerType("FORM")
                        .ownerId("owner-form")
                        .releaseId("owner-release")
                        .releaseVersion(3)
                        .compositionKey("related-targets")
                        .sourceEntityCode(sourceEntity.getEntityCode())
                        .sourceRecordId("source-1")
                        .targetEntityId(targetEntity.getId())
                        .targetEntityCode(targetEntity.getEntityCode())
                        .targetContentType(targetContentType)
                        .targetContentId("FORM".equals(targetContentType)
                                ? "target-form" : "target-list")
                        .targetContentKey("FORM".equals(targetContentType)
                                ? "target-form-key" : "target-list-key")
                        .targetReleaseId("target-release")
                        .targetReleaseVersion(2)
                        .targetRecordId(targetRecordId)
                        .fixedFilters("FORM".equals(targetContentType)
                                ? Map.of()
                                : Map.of(
                                        "sourceRef", "source-1",
                                        "sourceRef_op", "EQ"))
                        .matchNone("FORM".equals(targetContentType)
                                && targetRecordId == null)
                        .targetContentHash("target-config-hash")
                        .build());
        UiConfigRelease ownerRelease = release(
                "owner-release", "FORM", "owner-form", 3);
        UiConfigRelease targetRelease = release(
                "target-release",
                targetContentType,
                "FORM".equals(targetContentType)
                        ? "target-form" : "target-list",
                2);
        when(releaseMapper.selectById("owner-release"))
                .thenReturn(ownerRelease);
        when(releaseMapper.selectById("target-release"))
                .thenReturn(targetRelease);
        when(releaseService.verifiedReleaseSnapshot(ownerRelease))
                .thenReturn(ownerSnapshot);
        when(releaseService.verifiedReleaseSnapshot(targetRelease))
                .thenReturn(targetSnapshot);
        when(entitySnapshotService.getPinnedByHistoryId("source-history"))
                .thenReturn(new EntityPublishedSnapshotService
                        .PinnedEntitySnapshot(sourceSchema, "a".repeat(64)));
        when(entitySnapshotService.getPinnedByHistoryId("target-history"))
                .thenReturn(new EntityPublishedSnapshotService
                        .PinnedEntitySnapshot(targetSchema, "b".repeat(64)));
        when(entityMapper.selectById(sourceEntity.getId()))
                .thenReturn(sourceEntity);
        when(entityMapper.selectById(targetEntity.getId()))
                .thenReturn(targetEntity);
        EntityDataDTO source = record("source-1", Map.of(
                "ownerName", "来源名称",
                "targetRef", "target-1"));
        EntityDataDTO target = record("target-1", Map.of(
                "displayName", "目标名称",
                "sourceRef", "source-1"));
        when(dynamicDataService.findAccessibleById(
                sourceEntity.getEntityCode(), "source-1", null))
                .thenReturn(source);
        when(dynamicDataService.findAccessibleById(
                targetEntity.getEntityCode(), "target-1", "target-list-key"))
                .thenReturn(target);
        when(dynamicDataService.findAccessibleById(
                targetEntity.getEntityCode(), "target-1", null))
                .thenReturn(target);
        when(dynamicDataService.findPage(
                targetEntity.getEntityCode(),
                "target-list-key",
                Map.of(
                        "sourceRef", "source-1",
                        "sourceRef_op", "EQ",
                        "id", "target-1",
                        "id_op", "EQ"),
                1,
                2)).thenReturn(new PageResult<>(
                        List.of(target), 1, 1, 2));
        when(mutationPort.executeBatch(any()))
                .thenAnswer(invocation -> result(
                        invocation.getArgument(0), false, true));
    }

    @SuppressWarnings("unchecked")
    private void addWriteActionService(
            String actionKey,
            UiActionCommandPlan plan) {
        UiConfigRelease ownerRelease = releaseMapper.selectById(
                "owner-release");
        Map<String, Object> ownerSnapshot =
                releaseService.verifiedReleaseSnapshot(ownerRelease);
        List<Map<String, Object>> compositions =
                (List<Map<String, Object>>) (List<?>) ownerSnapshot.get(
                        "viewCompositions");
        Map<String, Object> composition = new java.util.LinkedHashMap<>(
                compositions.get(0));
        Map<String, Object> config = new java.util.LinkedHashMap<>(
                (Map<String, Object>) composition.get("config"));
        Map<String, Object> binding = Map.of(
                "actionKey", actionKey,
                "serviceId", "service-action",
                "sourceCode", "service.action",
                "serviceRevision", 1,
                "operationCode", "execute",
                "executableSnapshot", "{}",
                "definitionHash", "a".repeat(64),
                "inputMappings", List.of(),
                "outputMappings", List.of(),
                "failurePolicy", "ERROR");
        config.put("specialHandling", Map.of(
                "mode", "INTERFACE_SERVICE",
                "actionServices", List.of(binding),
                "failurePolicy", "ERROR"));
        // 扩展动作只存在于独立扩展页面，普通关系 fixture 仍只开放标准展示/表单能力。
        config.put("relation", Map.of("type", "INTERFACE_SERVICE"));
        composition.put("config", config);
        Map<String, Object> replaced = new java.util.LinkedHashMap<>(
                ownerSnapshot);
        replaced.put("viewCompositions", List.of(composition));
        when(releaseService.verifiedReleaseSnapshot(ownerRelease))
                .thenReturn(replaced);
        when(dataSourceService.validatePinnedActionOperation(
                "{}",
                "a".repeat(64),
                "service-action",
                "service.action",
                1,
                "execute",
                "FORM"))
                .thenReturn(new UiInterfaceExtensionService.ActionOperationDescriptor(
                        "service-action",
                        "service.action",
                        1,
                        "execute",
                        "WRITE",
                        "REGISTERED_PROVIDER",
                        "provider.action",
                        "FORM"));
        when(dataSourceService.planPinnedActionOperation(
                org.mockito.ArgumentMatchers.eq("{}"),
                org.mockito.ArgumentMatchers.eq("a".repeat(64)),
                any())).thenReturn(plan);
    }

    @SuppressWarnings("unchecked")
    private void replaceRelationType(String relationType) {
        UiConfigRelease release = releaseMapper.selectById("owner-release");
        Map<String, Object> snapshot = new java.util.LinkedHashMap<>(releaseService.verifiedReleaseSnapshot(release));
        List<Map<String, Object>> compositions = (List<Map<String, Object>>) snapshot.get("viewCompositions");
        Map<String, Object> composition = new java.util.LinkedHashMap<>(compositions.get(0));
        Map<String, Object> config = new java.util.LinkedHashMap<>((Map<String, Object>) composition.get("config"));
        config.put("relation", Map.of("type", relationType));
        composition.put("config", config);
        snapshot.put("viewCompositions", List.of(composition));
        when(releaseService.verifiedReleaseSnapshot(release)).thenReturn(snapshot);
    }

    private UiViewCompositionActionReceiptService.AcquireResult batchReceipt(
            String idempotencyKey) {
        return new UiViewCompositionActionReceiptService.AcquireResult(
                new EntityMutationCommand(
                        "batch-receipt",
                        "source_entity",
                        "source-1",
                        EntityMutationOperationType.UPDATE,
                        Map.of(),
                        EntityMutationContext.builder(
                                        EntityMutationSourceType.FORM,
                                        "TEST",
                                        "测试")
                                .trace("test-trace", idempotencyKey)
                                .build()),
                null);
    }

    private EntityMutationBatchResult result(
            EntityMutationBatchCommand batch,
            boolean replayed,
            boolean changed) {
        List<EntityMutationResult> results = batch.commands().stream()
                .map(command -> new EntityMutationResult(
                        command.operationId(),
                        command.entityCode(),
                        command.recordId(),
                        command.operationType(),
                        Map.of(),
                        null,
                        null,
                        changed,
                        replayed))
                .toList();
        return new EntityMutationBatchResult(batch.operationId(), results);
    }

    private UiViewCompositionResolveRequest compositionContext() {
        UiViewCompositionResolveRequest context = new UiViewCompositionResolveRequest();
        context.setOwnerType("FORM"); context.setOwnerId("owner-form");
        context.setRecordId("source-1"); context.setCompositionKey("related-targets");
        return context;
    }

    private UiViewCompositionActionRequest request(
            String action,
            String operationId,
            String... ids) {
        UiViewCompositionActionRequest request =
                new UiViewCompositionActionRequest();
        request.setCompositionContext(compositionContext());
        request.setAction(action);
        request.setOperationId(operationId);
        request.setTargetRecordIds(List.of(ids));
        request.setInput(Map.of());
        return request;
    }

    private UiConfigRelease release(
            String id,
            String type,
            String configId,
            int version) {
        UiConfigRelease release = new UiConfigRelease();
        release.setId(id);
        release.setConfigType(type);
        release.setConfigId(configId);
        release.setVersion(version);
        return release;
    }

    private Map<String, Object> pin(
            String historyId,
            EntityDefinition entity,
            int version,
            String hashCharacter) {
        return Map.of(
                "historyId", historyId,
                "entityId", entity.getId(),
                "entityCode", entity.getEntityCode(),
                "version", version,
                "schemaHash", hashCharacter.repeat(64));
    }

    private EntityPublishedSnapshot snapshot(
            String historyId,
            EntityDefinition entity,
            List<EntityField> fields) {
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        snapshot.setHistoryId(historyId);
        snapshot.setEntityId(entity.getId());
        snapshot.setEntityCode(entity.getEntityCode());
        snapshot.setVersion(1);
        snapshot.setFields(fields);
        snapshot.setRelations(List.of());
        snapshot.setRelationsSnapshotAvailable(true);
        return snapshot;
    }

    private EntityDefinition entity(String id, String code) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId(id);
        entity.setEntityCode(code);
        entity.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
        return entity;
    }

    private EntityField field(
            String code,
            EntityField.FieldType type,
            String refEntityId) {
        EntityField field = new EntityField();
        field.setFieldCode(code);
        field.setFieldType(type);
        field.setRefEntityId(refEntityId);
        return field;
    }

    private EntityDataDTO record(
            String id,
            Map<String, Object> data) {
        EntityDataDTO record = new EntityDataDTO();
        record.setId(id);
        record.setData(data);
        return record;
    }

}
