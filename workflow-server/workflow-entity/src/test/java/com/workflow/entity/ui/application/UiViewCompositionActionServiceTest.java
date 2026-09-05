package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.admin.authorization.menu.infrastructure.persistence.mapper.SysMenuMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.mutation.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.EntityMutationBatchResult;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.contracts.ui.UiActionCommandPlan;
import com.workflow.contracts.ui.UiActionMutationCommand;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.ui.api.request.UiViewCompositionActionCapabilitiesRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionActionRequest;
import com.workflow.entity.ui.api.request.UiViewCompositionLinkCandidatesRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionActionCapabilitiesResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionActionResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionLinkCandidatesResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionResolveResponse;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UiViewCompositionActionServiceTest {

    @Mock
    private UiViewCompositionRuntimeService runtimeService;
    @Mock
    private UiViewCompositionTokenService tokenService;
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
    private UiDataSourceService dataSourceService;

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
                tokenService,
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
                                new com.workflow.contracts.entity.mutation
                                        .EntityMutationCommand(
                                        "batch-receipt",
                                        "source_entity",
                                        "source-1",
                                        com.workflow.contracts.entity.mutation
                                                .EntityMutationOperationType.UPDATE,
                                        Map.of(),
                                        com.workflow.contracts.entity.mutation
                                                .EntityMutationContext.builder(
                                                com.workflow.contracts.entity.mutation
                                                        .EntityMutationSourceType.FORM,
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
    void tamperedActionTokenFailsBeforeRuntimeResolution() {
        when(tokenService.verifySourceRow("tampered"))
                .thenThrow(new BusinessForbiddenException(
                        "INVALID_VIEW_COMPOSITION_TOKEN",
                        "令牌签名无效"));

        assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request(
                        "tampered", "SELECT", "op-1", "target-1")));

        verify(runtimeService, never()).resolve(any());
        verify(mutationPort, never()).executeBatch(any());
    }

    @Test
    void selectRereadsTargetAndOnlyReturnsPublishedFieldPatch() {
        fixture(
                List.of("VIEW", "SELECT"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);

        UiViewCompositionActionResponse response = service.execute(
                request("action-token", "SELECT", "select-1", "target-1"));

        assertEquals(Map.of("ownerName", "目标名称"),
                response.getSourcePatch());
        assertTrue(response.getChangedReferences().isEmpty());
        assertTrue(response.getActionCapabilities().get("SELECT")
                .isAvailable());
        assertFalse(response.getActionCapabilities().get("CREATE")
                .isAvailable());
        verify(dynamicDataService).findPage(
                "target_entity",
                "target-list-key",
                Map.of(
                        "sourceRef", "source-1",
                        "sourceRef_op", "EQ",
                        "id", "target-1",
                        "id_op", "EQ"),
                1,
                2);
        verify(mutationPort, never()).executeBatch(any());
    }

    @Test
    void selectRejectsTargetOutsideTrustedListScope() {
        fixture(
                List.of("VIEW", "SELECT"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        when(dynamicDataService.findPage(
                any(), any(), any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 2));

        BusinessForbiddenException failure = assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request(
                        "action-token", "SELECT", "select-2", "target-1")));

        assertEquals(
                "VIEW_COMPOSITION_SELECTION_OUT_OF_SCOPE",
                failure.getErrorCode());
    }

    @Test
    void undeclaredMutationActionIsRejected() {
        fixture(
                List.of("VIEW", "SELECT"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);

        BusinessForbiddenException failure = assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request(
                        "action-token", "LINK", "link-1", "target-1")));

        assertEquals("VIEW_COMPOSITION_ACTION_NOT_ALLOWED",
                failure.getErrorCode());
        verify(mutationPort, never()).executeBatch(any());
    }

    @Test
    void hiddenTargetListFieldDisablesSelectCapability() {
        fixture(
                List.of("VIEW", "SELECT"),
                "REVERSE_REFERENCE",
                false,
                EntityRelation.OwnershipType.ASSOCIATION);
        UiViewCompositionActionCapabilitiesRequest request =
                new UiViewCompositionActionCapabilitiesRequest();
        request.setActionContextToken("action-token");

        UiViewCompositionActionCapabilitiesResponse response =
                service.capabilities(request);

        assertFalse(response.getActionCapabilities().get("SELECT")
                .isAvailable());
        assertTrue(response.getActionCapabilities().get("SELECT")
                .getReason().contains("未在固定列表版本中显示"));
    }

    @Test
    void compositionRelationCannotBeLinkedOrUnlinkedIndependently() {
        fixture(
                List.of("VIEW", "LINK", "UNLINK"),
                "ENTITY_RELATION",
                true,
                EntityRelation.OwnershipType.COMPOSITION);
        UiViewCompositionActionCapabilitiesRequest request =
                new UiViewCompositionActionCapabilitiesRequest();
        request.setActionContextToken("action-token");

        UiViewCompositionActionCapabilitiesResponse response =
                service.capabilities(request);

        assertFalse(response.getActionCapabilities().get("LINK")
                .isAvailable());
        assertTrue(response.getActionCapabilities().get("LINK")
                .getReason().contains("组成型关系"));
        assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request(
                        "action-token", "LINK", "link-2", "target-1")));
        verify(mutationPort, never()).executeBatch(any());
    }

    @Test
    void reverseReferenceLinkAndUnlinkUseAtomicMutationBatch() {
        Fixture fixture = fixture(
                List.of("VIEW", "LINK", "UNLINK"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        AtomicReference<String> current = new AtomicReference<>();
        when(dynamicDataService.findAccessibleById(
                "target_entity", "target-1", "target-list-key"))
                .thenAnswer(ignored -> record(
                        "target-1",
                        Map.of("displayName", "目标名称",
                                "sourceRef",
                                current.get() == null ? "" : current.get())));
        doAnswer(invocation -> {
                    EntityMutationBatchCommand batch = invocation.getArgument(0);
                    Object value = batch.commands().get(0).payload()
                            .get("data") instanceof Map<?, ?> data
                            ? data.get("sourceRef") : null;
                    current.set(value == null ? null : String.valueOf(value));
                    return result(batch, false, true);
                }).when(mutationPort).executeBatch(any());

        UiViewCompositionActionResponse linked = service.execute(
                request("action-token", "LINK", "roundtrip-link", "target-1"));
        UiViewCompositionActionResponse unlinked = service.execute(
                request("action-token", "UNLINK", "roundtrip-unlink", "target-1"));

        assertTrue(linked.getChangedReferences().get(0).isLinked());
        assertFalse(unlinked.getChangedReferences().get(0).isLinked());
        assertEquals(null, current.get());
        ArgumentCaptor<EntityMutationBatchCommand> batches =
                ArgumentCaptor.forClass(EntityMutationBatchCommand.class);
        verify(mutationPort, org.mockito.Mockito.times(2))
                .executeBatch(batches.capture());
        assertTrue(batches.getAllValues().stream()
                .allMatch(EntityMutationBatchCommand::atomic));
        assertEquals("source-1", ((Map<?, ?>) batches.getAllValues()
                .get(0).commands().get(0).payload().get("data"))
                .get("sourceRef"));
        assertEquals(null, ((Map<?, ?>) batches.getAllValues()
                .get(1).commands().get(0).payload().get("data"))
                .get("sourceRef"));
        assertEquals(fixture.sourceEntity().getEntityCode(),
                batches.getAllValues().get(0).commands().get(0)
                        .context().sourceEntityCode());
    }

    @Test
    void singleReferenceMutationReturnsAuthoritativeHostPatch() {
        fixture(
                List.of("VIEW", "LINK", "UNLINK"),
                "REFERENCE_FIELD",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        AtomicReference<String> current = new AtomicReference<>();
        when(dynamicDataService.findAccessibleById(
                "source_entity", "source-1", null))
                .thenAnswer(ignored -> record(
                        "source-1",
                        nullableData(
                                "ownerName", "来源名称",
                                "targetRef", current.get())));
        when(tokenService.verifyCandidateList("candidate-token"))
                .thenReturn(candidateClaims(Map.of(), false));
        EntityDataDTO target = record("target-1", Map.of(
                "displayName", "目标名称",
                "sourceRef", "source-1"));
        when(dynamicDataService.findPage(
                "target_entity",
                "target-list-key",
                Map.of("id", "target-1", "id_op", "EQ"),
                1,
                2)).thenReturn(new PageResult<>(
                        List.of(target), 1, 1, 2));
        doAnswer(invocation -> {
                    EntityMutationBatchCommand batch = invocation.getArgument(0);
                    Object value = ((Map<?, ?>) batch.commands().get(0)
                            .payload().get("data")).get("targetRef");
                    current.set(value == null ? null : String.valueOf(value));
                    return result(batch, false, true);
                }).when(mutationPort).executeBatch(any());

        UiViewCompositionActionResponse linked = service.execute(request(
                "action-token", "LINK", "reference-link", "target-1"));
        UiViewCompositionActionResponse unlinked = service.execute(request(
                "action-token", "UNLINK", "reference-unlink", "target-1"));

        assertEquals("target-1", linked.getSourcePatch().get("targetRef"));
        assertTrue(unlinked.getSourcePatch().containsKey("targetRef"));
        assertNull(unlinked.getSourcePatch().get("targetRef"));
        assertNull(current.get());
    }

    @Test
    void duplicateOperationIdUsesStablePersistentIdempotencyKey() {
        fixture(
                List.of("VIEW", "LINK"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        when(dynamicDataService.findAccessibleById(
                "target_entity", "target-1", "target-list-key"))
                .thenReturn(record("target-1", Map.of(
                        "displayName", "目标名称",
                        "sourceRef", "")));
        AtomicReference<Boolean> replay = new AtomicReference<>(false);
        doAnswer(invocation -> {
                    EntityMutationBatchCommand batch = invocation.getArgument(0);
                    boolean value = replay.getAndSet(true);
                    return result(batch, value, false);
                }).when(mutationPort).executeBatch(any());

        UiViewCompositionActionResponse first = service.execute(
                request("action-token", "LINK", "same-operation", "target-1"));
        UiViewCompositionActionResponse second = service.execute(
                request("action-token", "LINK", "same-operation", "target-1"));

        assertFalse(first.isReplayed());
        assertTrue(second.isReplayed());
        ArgumentCaptor<EntityMutationBatchCommand> batches =
                ArgumentCaptor.forClass(EntityMutationBatchCommand.class);
        verify(mutationPort, org.mockito.Mockito.times(2))
                .executeBatch(batches.capture());
        assertEquals(
                batches.getAllValues().get(0).commands().get(0)
                        .context().idempotencyKey(),
                batches.getAllValues().get(1).commands().get(0)
                        .context().idempotencyKey());
    }

    @Test
    void unlinkRejectsRecordLinkedToAnotherSource() {
        fixture(
                List.of("VIEW", "UNLINK"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        when(dynamicDataService.findAccessibleById(
                "target_entity", "target-1", "target-list-key"))
                .thenReturn(record("target-1", Map.of(
                        "displayName", "目标名称",
                        "sourceRef", "other-source")));
        when(dynamicDataService.findPage(
                any(), any(), any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 2));

        var failure = assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request(
                        "action-token", "UNLINK", "cross-unlink", "target-1")));

        assertEquals("VIEW_COMPOSITION_SELECTION_OUT_OF_SCOPE",
                failure.getErrorCode());
        verify(mutationPort, never()).executeBatch(any());
    }

    @Test
    void relationMutationRequiresUpdatePermissionOnAuthoritativeSide() {
        fixture(
                List.of("VIEW", "LINK"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        SysMenuMapper menuMapper = org.mockito.Mockito.mock(
                SysMenuMapper.class);
        when(menuMapper.selectPermsByUserId("action-user"))
                .thenReturn(Set.of("entity:target_entity:view"));
        ReflectionTestUtils.setField(
                PermissionUtil.class, "staticMenuMapper", menuMapper);
        UiViewCompositionActionCapabilitiesRequest request =
                new UiViewCompositionActionCapabilitiesRequest();
        request.setActionContextToken("action-token");

        UiViewCompositionActionCapabilitiesResponse response =
                service.capabilities(request);

        assertFalse(response.getActionCapabilities().get("LINK")
                .isAvailable());
        assertTrue(response.getActionCapabilities().get("LINK")
                .getReason().contains("编辑权限"));
        assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request(
                        "action-token", "LINK", "no-update", "target-1")));
        verify(mutationPort, never()).executeBatch(any());
    }

    @Test
    void selectLinkRejectsAccessibleIdOutsideResolvedFixedFilters() {
        fixture(
                List.of("VIEW", "SELECT", "LINK"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION,
                "LINK");
        when(dynamicDataService.findAccessibleById(
                "target_entity", "target-1", "target-list-key"))
                .thenReturn(record("target-1", Map.of(
                        "displayName", "目标名称",
                        "sourceRef", "")));
        when(dynamicDataService.findPage(
                any(), any(), any(),
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 2));

        BusinessForbiddenException failure = assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(candidateSelectRequest(
                        "tampered-select-link",
                        "target-1")));

        assertEquals("VIEW_COMPOSITION_LINK_CANDIDATE_OUT_OF_SCOPE",
                failure.getErrorCode());
        verify(mutationPort, never()).executeBatch(any());
    }

    @Test
    void reverseReferenceCandidateTokenOnlyContainsUnlinkedScope() {
        fixture(
                List.of("VIEW", "LINK", "UNLINK"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        when(tokenService.issueCandidateList(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn("candidate-issued");
        UiViewCompositionLinkCandidatesRequest request =
                new UiViewCompositionLinkCandidatesRequest();
        request.setActionContextToken("action-token");
        request.setAction("LINK");

        UiViewCompositionLinkCandidatesResponse response =
                service.linkCandidates(request);

        assertEquals("candidate-issued",
                response.getCandidateListContextToken());
        assertFalse(response.isMatchNone());
        verify(tokenService).issueCandidateList(
                "FORM",
                "owner-form",
                "owner-release",
                3,
                "related-targets",
                "source_entity",
                "source-1",
                "target_entity",
                "target-list",
                "target-release",
                2,
                Map.of(
                        "sourceRef", true,
                        "sourceRef_op", "IS_NULL"),
                false);
    }

    @Test
    void relationScopeRemainsSignedWhenListAlreadyUsesIsNullFilter() {
        Fixture fixture = fixture(
                List.of("VIEW", "LINK", "UNLINK"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        UiConfigRelease targetRelease = releaseMapper.selectById(
                "target-release");
        when(releaseService.verifiedReleaseSnapshot(targetRelease))
                .thenReturn(Map.of(
                        "list", Map.of(
                                "id", "target-list",
                                "entityId", fixture.targetEntity().getId(),
                                "fixedFilterConfig", Map.of(
                                        "sourceRef_op", "IS_NULL"),
                                "fields", List.of(Map.of(
                                        "fieldCode", "displayName",
                                        "showInList", true,
                                        "dataSourceType", "ENTITY_FIELD")))));
        when(tokenService.issueCandidateList(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn("candidate-issued");
        UiViewCompositionLinkCandidatesRequest request =
                new UiViewCompositionLinkCandidatesRequest();
        request.setActionContextToken("action-token");
        request.setAction("LINK");

        UiViewCompositionLinkCandidatesResponse response =
                service.linkCandidates(request);

        assertFalse(response.isMatchNone());
        verify(tokenService).issueCandidateList(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(Map.of(
                        "sourceRef", true,
                        "sourceRef_op", "IS_NULL")),
                org.mockito.ArgumentMatchers.eq(false));
    }

    @Test
    void singleReferenceWithExistingValueHasNoMisleadingCandidates() {
        fixture(
                List.of("VIEW", "LINK", "UNLINK"),
                "REFERENCE_FIELD",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        when(tokenService.issueCandidateList(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyMap(),
                org.mockito.ArgumentMatchers.anyBoolean()))
                .thenReturn("candidate-issued");
        UiViewCompositionLinkCandidatesRequest request =
                new UiViewCompositionLinkCandidatesRequest();
        request.setActionContextToken("action-token");
        request.setAction("LINK");

        UiViewCompositionLinkCandidatesResponse response =
                service.linkCandidates(request);

        assertTrue(response.isMatchNone());
        verify(tokenService).issueCandidateList(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.eq(Map.of()),
                org.mockito.ArgumentMatchers.eq(true));
    }

    @Test
    void linkCannotUseOnlyOrdinaryActionTokenWithoutCandidateScope() {
        fixture(
                List.of("VIEW", "LINK"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        UiViewCompositionActionRequest request = request(
                "action-token", "LINK", "missing-candidate", "target-1");
        request.setCandidateListContextToken(null);

        BusinessForbiddenException failure = assertThrows(
                BusinessForbiddenException.class,
                () -> service.execute(request));

        assertEquals(
                "VIEW_COMPOSITION_LINK_CANDIDATE_CONTEXT_REQUIRED",
                failure.getErrorCode());
        verify(actionReceiptService, never()).acquire(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyString());
        verify(mutationPort, never()).executeBatch(any());
    }

    @Test
    void batchFingerprintConflictStopsBeforeRecordMutation() {
        fixture(
                List.of("VIEW", "LINK"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
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
                .thenThrow(new BusinessConflictException(
                        "ENTITY_MUTATION_IDEMPOTENCY_CONFLICT",
                        "同一操作不能改变动作或目标集合"));

        assertThrows(
                BusinessConflictException.class,
                () -> service.execute(request(
                        "action-token", "LINK", "conflict-op", "target-1")));

        verify(aggregateWriter, never()).lock(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
        verify(mutationPort, never()).executeBatch(any());
    }

    @Test
    void exactSingleReferenceReplaySurvivesExpectedRelationChange() {
        fixture(
                List.of("VIEW", "LINK"),
                "REFERENCE_FIELD",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        when(tokenService.verifyCandidateList("candidate-token"))
                .thenReturn(candidateClaims(Map.of(), false));
        UiViewCompositionActionResponse historical =
                UiViewCompositionActionResponse.builder()
                        .operationId("reference-replay")
                        .action("LINK")
                        .sourceRecordId("source-1")
                        .sourcePatch(Map.of("targetRef", "target-1"))
                        .changedReferences(List.of())
                        .actionCapabilities(Map.of())
                        .replayed(true)
                        .build();
        when(actionReceiptService.acquire(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq("LINK"),
                org.mockito.ArgumentMatchers.eq(List.of("target-1")),
                org.mockito.ArgumentMatchers.eq("reference-replay")))
                .thenReturn(new UiViewCompositionActionReceiptService
                        .AcquireResult(null, historical));

        UiViewCompositionActionResponse replay = service.execute(request(
                "action-token", "LINK", "reference-replay", "target-1"));

        assertTrue(replay.isReplayed());
        assertEquals("target-1", replay.getSourcePatch().get("targetRef"));
        verify(mutationPort, never()).executeBatch(any());
    }

    @Test
    void formCapabilitiesExposeServerResolvedCreateValuesAndExactEdit() {
        formFixture(
                List.of("VIEW", "CREATE", "EDIT"),
                "target-1",
                false);
        UiViewCompositionActionCapabilitiesRequest request =
                new UiViewCompositionActionCapabilitiesRequest();
        request.setActionContextToken("action-token");

        UiViewCompositionActionCapabilitiesResponse response =
                service.capabilities(request);

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
                "target-1",
                false);

        Map<String, Object> trusted = service.authorizeFormSubmission(
                "action-token",
                "CREATE",
                "target_entity",
                null,
                "target-form",
                "target-release",
                2,
                "target-form-token");

        assertEquals(Map.of("displayName", "来源名称"), trusted);
        verify(releaseService).resolveAuthorizedRuntimeFormRelease(
                "target-form",
                "target-release",
                2,
                "target-form-token");
        BusinessForbiddenException error = assertThrows(
                BusinessForbiddenException.class,
                () -> service.authorizeFormSubmission(
                        "action-token",
                        "EDIT",
                        "target_entity",
                        "other-target",
                        "target-form",
                        "target-release",
                        2,
                        "target-form-token"));
        assertEquals("VIEW_COMPOSITION_EDIT_TARGET_MISMATCH",
                error.getErrorCode());
    }

    @Test
    void createAndSaveWithFormFailClosedWhenAtomicBridgeIsUnavailable() {
        formFixture(
                List.of("VIEW", "CREATE", "SAVE_WITH_FORM"),
                "target-1",
                true);
        UiViewCompositionActionCapabilitiesRequest request =
                new UiViewCompositionActionCapabilitiesRequest();
        request.setActionContextToken("action-token");

        UiViewCompositionActionCapabilitiesResponse response =
                service.capabilities(request);

        assertFalse(response.getActionCapabilities().get("CREATE")
                .isAvailable());
        assertTrue(response.getActionCapabilities().get("CREATE")
                .getReason().contains("同一事务"));
        assertFalse(response.getActionCapabilities().get("SAVE_WITH_FORM")
                .isAvailable());
        assertTrue(response.getActionCapabilities().get("SAVE_WITH_FORM")
                .getReason().contains("子表单或重复器"));
    }

    @Test
    void editCapabilityRequiresResolvedTargetRecord() {
        formFixture(List.of("VIEW", "EDIT"), null, false);
        UiViewCompositionActionCapabilitiesRequest request =
                new UiViewCompositionActionCapabilitiesRequest();
        request.setActionContextToken("action-token");

        UiViewCompositionActionCapabilitiesResponse response =
                service.capabilities(request);

        assertFalse(response.getActionCapabilities().get("EDIT")
                .isAvailable());
        assertTrue(response.getActionCapabilities().get("EDIT")
                .getReason().contains("没有解析到"));
    }

    /** Provider 不能把计划中的记录 ID 当作新的授权来源。 */
    @Test
    void localWriteProviderCannotMutateUnselectedRelatedRecord() {
        fixture(
                List.of("VIEW"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        addWriteActionService(
                "RECALCULATE",
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
                        "action-token",
                        "RECALCULATE",
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
        fixture(
                List.of("VIEW"),
                "REVERSE_REFERENCE",
                true,
                EntityRelation.OwnershipType.ASSOCIATION);
        addWriteActionService(
                "RECALCULATE",
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
                "action-token", "RECALCULATE", "same-client-op", "target-1"));
        service.execute(request(
                "action-token", "RECALCULATE", "same-client-op", "target-1"));

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

    private Fixture fixture(
            List<String> actions,
            String relationType,
            boolean targetFieldVisible,
            EntityRelation.OwnershipType ownershipType) {
        return fixture(
                actions,
                relationType,
                targetFieldVisible,
                ownershipType,
                "FILL_FIELDS",
                "LIST",
                "target-1",
                false);
    }

    private Fixture fixture(
            List<String> actions,
            String relationType,
            boolean targetFieldVisible,
            EntityRelation.OwnershipType ownershipType,
            String selectResult) {
        return fixture(
                actions,
                relationType,
                targetFieldVisible,
                ownershipType,
                selectResult,
                "LIST",
                "target-1",
                false);
    }

    private Fixture formFixture(
            List<String> actions,
            String targetRecordId,
            boolean associateAfterCreate) {
        return fixture(
                actions,
                "REFERENCE_FIELD",
                true,
                EntityRelation.OwnershipType.ASSOCIATION,
                "FILL_FIELDS",
                "FORM",
                targetRecordId,
                associateAfterCreate);
    }

    private Fixture fixture(
            List<String> actions,
            String relationType,
            boolean targetFieldVisible,
            EntityRelation.OwnershipType ownershipType,
            String selectResult,
            String targetContentType,
            String targetRecordId,
            boolean associateAfterCreate) {
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
        EntityRelation entityRelation = new EntityRelation();
        entityRelation.setRelationCode("source-targets");
        entityRelation.setChildEntityId(targetEntity.getId());
        entityRelation.setChildEntityCode(targetEntity.getEntityCode());
        entityRelation.setChildRefFieldCode("sourceRef");
        entityRelation.setOwnershipType(ownershipType);
        entityRelation.setEnabled(true);
        EntityPublishedSnapshot sourceSchema = snapshot(
                "source-history",
                sourceEntity,
                List.of(ownerName, targetRef),
                List.of(entityRelation));
        EntityPublishedSnapshot targetSchema = snapshot(
                "target-history",
                targetEntity,
                List.of(displayName, sourceRef),
                List.of());

        Map<String, Object> relation = switch (relationType) {
            case "REFERENCE_FIELD" -> Map.of(
                    "type", relationType,
                    "sourceField", "targetRef");
            case "ENTITY_RELATION" -> Map.of(
                    "type", relationType,
                    "relationCode", "source-targets");
            default -> Map.of(
                    "type", relationType,
                    "targetField", "sourceRef");
        };
        Map<String, Object> config = Map.of(
                "enabled", true,
                "actions", actions,
                "relation", relation,
                "actionSettings", Map.of(
                        "select", Map.of(
                                "mode", "SINGLE",
                                "result", selectResult,
                                "mappings", List.of(Map.of(
                                        "sourceField", "displayName",
                                        "targetField", "ownerName",
                                        "required", true))),
                        "create", Map.of(
                                "associateAfterCreate", associateAfterCreate,
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
                                        "showInList", targetFieldVisible,
                                        "dataSourceType", "ENTITY_FIELD"))));

        UiViewCompositionTokenService.Claims claims = claims();
        when(tokenService.verifySourceRow("action-token"))
                .thenReturn(claims);
        when(tokenService.verifyCandidateList("candidate-token"))
                .thenReturn(candidateClaims());
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
                        .targetReleaseResolutionToken(
                                "FORM".equals(targetContentType)
                                        ? "target-form-token" : null)
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
        when(dynamicDataService.findPage(
                targetEntity.getEntityCode(),
                "target-list-key",
                Map.of(
                        "sourceRef", true,
                        "sourceRef_op", "IS_NULL",
                        "id", "target-1",
                        "id_op", "EQ"),
                1,
                2)).thenReturn(new PageResult<>(
                        List.of(target), 1, 1, 2));
        when(mutationPort.executeBatch(any()))
                .thenAnswer(invocation -> result(
                        invocation.getArgument(0), false, true));
        return new Fixture(sourceEntity, targetEntity);
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
                .thenReturn(new UiDataSourceService.ActionOperationDescriptor(
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

    private UiViewCompositionActionRequest request(
            String token,
            String action,
            String operationId,
            String... ids) {
        UiViewCompositionActionRequest request =
                new UiViewCompositionActionRequest();
        request.setActionContextToken(token);
        if ("LINK".equals(action)) {
            request.setCandidateListContextToken("candidate-token");
        }
        request.setAction(action);
        request.setOperationId(operationId);
        request.setTargetRecordIds(List.of(ids));
        request.setInput(Map.of());
        return request;
    }

    private UiViewCompositionActionRequest candidateSelectRequest(
            String operationId,
            String... ids) {
        UiViewCompositionActionRequest request = request(
                "action-token", "SELECT", operationId, ids);
        request.setCandidateListContextToken("candidate-token");
        return request;
    }

    private UiViewCompositionTokenService.Claims candidateClaims() {
        return candidateClaims(
                Map.of(
                        "sourceRef", true,
                        "sourceRef_op", "IS_NULL"),
                false);
    }

    private UiViewCompositionTokenService.Claims candidateClaims(
            Map<String, Object> filters,
            boolean matchNone) {
        return new UiViewCompositionTokenService.Claims(
                "CANDIDATE_LIST",
                "FORM",
                "owner-form",
                "owner-release",
                3,
                "related-targets",
                "source_entity",
                "source-1",
                "target_entity",
                "target-list",
                "target-release",
                2,
                filters,
                matchNone,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "action-user",
                1L,
                Long.MAX_VALUE);
    }

    private Map<String, Object> nullableData(
            String firstKey,
            Object firstValue,
            String secondKey,
            Object secondValue) {
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put(firstKey, firstValue);
        result.put(secondKey, secondValue);
        return result;
    }

    private UiViewCompositionTokenService.Claims claims() {
        return new UiViewCompositionTokenService.Claims(
                "SOURCE_ROW",
                "FORM",
                "owner-form",
                "owner-release",
                3,
                "related-targets",
                "source_entity",
                "source-1",
                null,
                null,
                null,
                null,
                Map.of(),
                false,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                "action-user",
                1L,
                Long.MAX_VALUE);
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
            List<EntityField> fields,
            List<EntityRelation> relations) {
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        snapshot.setHistoryId(historyId);
        snapshot.setEntityId(entity.getId());
        snapshot.setEntityCode(entity.getEntityCode());
        snapshot.setVersion(1);
        snapshot.setFields(fields);
        snapshot.setRelations(relations);
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

    private record Fixture(
            EntityDefinition sourceEntity,
            EntityDefinition targetEntity) {
    }
}
