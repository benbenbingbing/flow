package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.version.application.EntityRelatedVersionCaptureService.RootKey;
import com.workflow.entity.version.application.EntityVersionPolicyMatcher.MatchedScenario;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class EntityRelatedVersionCaptureServiceTest {

    @Mock
    private EntityVersionConfigurationService configurationService;
    @Mock
    private EntityVersionPolicyMatcher policyMatcher;
    @Mock
    private EntityRecordSnapshotService snapshotService;
    @Mock
    private EntityRecordVersionService versionService;
    @Mock
    private EntityDataDynamicService dataService;
    @Mock
    private EntityAggregateWriter aggregateWriter;

    private EntityRelatedVersionCaptureService service;

    @BeforeEach
    void setUp() {
        service = new EntityRelatedVersionCaptureService(
                configurationService,
                policyMatcher,
                snapshotService,
                versionService,
                dataService,
                aggregateWriter,
                new ObjectMapper());
    }

    @Test
    void locksOldAndRequestedNewParentsInStableOrder() {
        EntityMutationCommand command = command("asset-2");
        when(configurationService.findCurrentScopedConfigurations(
                "asset_line")).thenReturn(List.of(configuration()));

        Set<RootKey> roots = service.lockRelatedRoots(
                command, record("asset-1"));

        assertEquals(Set.of(
                new RootKey("asset", "asset-1"),
                new RootKey("asset", "asset-2")), roots);
        InOrder order = inOrder(aggregateWriter);
        order.verify(aggregateWriter).lock("asset", "asset-1");
        order.verify(aggregateWriter).lock("asset", "asset-2");
    }

    @Test
    void rejectsParentMoveObservedAfterChildLockInsteadOfReverseLocking() {
        EntityMutationCommand command = command("asset-2");
        when(configurationService.findCurrentScopedConfigurations(
                "asset_line")).thenReturn(List.of(configuration()));
        Set<RootKey> locked = service.lockRelatedRoots(
                command, record("asset-1"));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.requireRootsLocked(
                        command, locked, record("asset-3")));

        assertEquals("ENTITY_RELATED_ROOT_LOCK_CONFLICT",
                exception.getErrorCode());
    }

    @Test
    void scopeWithoutPropagationTriggerStillLocksParentButDoesNotCapture() {
        EntityVersionConfiguration scoped = configuration();
        scoped.setTriggers(List.of());
        when(configurationService.findCurrentScopedConfigurations(
                "asset_line")).thenReturn(List.of(scoped));
        when(configurationService.findCurrentRelatedConfigurations(
                "asset_line")).thenReturn(List.of());

        Set<RootKey> locked = service.lockRelatedRoots(
                command("asset-1"), record("asset-1"));
        service.captureRelated(
                command("asset-1"),
                record("asset-1"),
                record("asset-1"),
                locked);

        assertEquals(Set.of(new RootKey("asset", "asset-1")), locked);
        verifyNoInteractions(policyMatcher, versionService, dataService);
    }

    @Test
    void captureCarriesOneCurrentConfigurationThroughTheWholePlan() {
        EntityMutationCommand command = command("asset-1");
        EntityVersionConfiguration configuration = configuration();
        Map<String, Object> record = record("asset-1");
        EntityVersionConfiguration.RelationScope relation = configuration
                .getSnapshotScope().getRelations().get(0);
        MatchedScenario scenario = new MatchedScenario(
                "RELATED_LINE_CHANGE",
                "行项变化",
                null,
                10,
                configuration);
        when(configurationService.findCurrentRelatedConfigurations(
                "asset_line")).thenReturn(List.of(configuration));
        when(snapshotService.matchesFixedFilter(
                record, relation.getFilter())).thenReturn(true);
        when(policyMatcher.matchRelated(
                configuration,
                null,
                command,
                record,
                record)).thenReturn(Optional.of(scenario));
        when(dataService.findById("asset", "asset-1"))
                .thenReturn(dto("asset-1", Map.of("name", "资产")));

        service.captureRelated(
                command,
                record,
                record,
                Set.of(new RootKey("asset", "asset-1")));

        verify(configurationService, times(1))
                .findCurrentRelatedConfigurations("asset_line");
        verify(versionService).createIfMatched(
                any(), eq(scenario), anyMap(), eq(false));
    }

    @Test
    void captureRejectsAParentIntroducedByAConcurrentConfigSave() {
        EntityMutationCommand command = command("asset-1");
        EntityVersionConfiguration configuration = configuration();
        Map<String, Object> record = record("asset-1");
        EntityVersionConfiguration.RelationScope relation = configuration
                .getSnapshotScope().getRelations().get(0);
        when(configurationService.findCurrentRelatedConfigurations(
                "asset_line")).thenReturn(List.of(configuration));
        when(snapshotService.matchesFixedFilter(
                record, relation.getFilter())).thenReturn(true);
        when(policyMatcher.matchRelated(
                configuration,
                null,
                command,
                record,
                record)).thenReturn(Optional.of(new MatchedScenario(
                        "RELATED_LINE_CHANGE",
                        "行项变化",
                        null,
                        10,
                        configuration)));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.captureRelated(
                        command, record, record, Set.of()));

        assertEquals("ENTITY_RELATED_ROOT_LOCK_CONFLICT",
                exception.getErrorCode());
        verifyNoInteractions(versionService, dataService);
    }

    @Test
    void resolvesThreeLevelFrozenPathBeforeLockingRoot() {
        EntityMutationCommand command = new EntityMutationCommand(
                "operation-check",
                "asset_check",
                "check-1",
                EntityMutationOperationType.UPDATE,
                Map.of("data", Map.of("componentId", "component-1")),
                EntityMutationContext.builder(
                                EntityMutationSourceType.CUSTOM_INTERFACE,
                                "CHECK_CHANGE",
                                "检查变化")
                        .trace("trace-check", "mutation-check")
                        .build());
        when(configurationService.findCurrentScopedConfigurations(
                "asset_check")).thenReturn(List.of(
                        multiLevelConfiguration()));
        when(dataService.findById("asset_component", "component-1"))
                .thenReturn(dto("component-1", Map.of("lineId", "line-1")));
        when(dataService.findById("asset_line", "line-1"))
                .thenReturn(dto("line-1", Map.of("assetId", "asset-1")));

        Set<RootKey> roots = service.lockRelatedRoots(
                command,
                Map.of("id", "check-1", "data",
                        Map.of("componentId", "component-1")));

        assertEquals(Set.of(new RootKey("asset", "asset-1")), roots);
        org.mockito.Mockito.verify(aggregateWriter)
                .lock("asset", "asset-1");
    }

    private EntityVersionConfiguration configuration() {
        EntityVersionConfiguration value = new EntityVersionConfiguration();
        value.setEntityCode("asset");
        EntityVersionConfiguration.RelationScope relation =
                new EntityVersionConfiguration.RelationScope();
        relation.setRelationCode("asset_lines");
        relation.setChildEntityCode("asset_line");
        relation.setChildRefFieldCode("assetId");
        value.getSnapshotScope().setRelations(List.of(relation));
        EntityVersionConfiguration.CaptureTrigger trigger =
                new EntityVersionConfiguration.CaptureTrigger();
        trigger.setTriggerCode("RELATED_LINE_CHANGE");
        trigger.setTriggerType("RELATED_MUTATION");
        trigger.setRelationCode("asset_lines");
        value.setTriggers(List.of(trigger));
        return value;
    }

    private EntityVersionConfiguration multiLevelConfiguration() {
        EntityVersionConfiguration value = new EntityVersionConfiguration();
        value.setEntityCode("asset");
        EntityVersionConfiguration.RelationScope relation =
                new EntityVersionConfiguration.RelationScope();
        relation.setNodeCode("CHECKS");
        relation.setParentNodeCode("COMPONENTS");
        relation.setDepth(3);
        relation.setRelationCode("component_checks");
        relation.setChildEntityCode("asset_check");
        relation.setChildRefFieldCode("componentId");
        relation.setRelationPath(List.of(
                pathStep("LINES", "asset_line", "assetId"),
                pathStep("COMPONENTS", "asset_component", "lineId"),
                pathStep("CHECKS", "asset_check", "componentId")));
        value.getSnapshotScope().setRelations(List.of(relation));
        return value;
    }

    private EntityVersionConfiguration.RelationPathStep pathStep(
            String nodeCode,
            String targetEntityCode,
            String childRefFieldCode) {
        EntityVersionConfiguration.RelationPathStep step =
                new EntityVersionConfiguration.RelationPathStep();
        step.setNodeCode(nodeCode);
        step.setTargetEntityCode(targetEntityCode);
        step.setChildRefFieldCode(childRefFieldCode);
        return step;
    }

    private EntityDataDTO dto(String id, Map<String, Object> data) {
        EntityDataDTO value = new EntityDataDTO();
        value.setId(id);
        value.setData(data);
        return value;
    }

    private EntityMutationCommand command(String requestedParentId) {
        return new EntityMutationCommand(
                "operation-1",
                "asset_line",
                "line-1",
                EntityMutationOperationType.UPDATE,
                Map.of("data", Map.of("assetId", requestedParentId)),
                EntityMutationContext.builder(
                                EntityMutationSourceType.CUSTOM_INTERFACE,
                                "LINE_CHANGE",
                                "明细变更")
                        .trace("trace-1", "mutation-1")
                        .build());
    }

    private Map<String, Object> record(String parentId) {
        return Map.of(
                "id", "line-1",
                "data", Map.of("assetId", parentId));
    }
}
