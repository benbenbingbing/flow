package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.version.application.EntityRelatedVersionCaptureService.RootKey;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
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
        when(configurationService.findPublishedScopedConfigurations(
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
        when(configurationService.findPublishedScopedConfigurations(
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
        when(configurationService.findPublishedScopedConfigurations(
                "asset_line")).thenReturn(List.of(scoped));
        when(configurationService.findPublishedRelatedConfigurations(
                "asset_line")).thenReturn(List.of());

        Set<RootKey> locked = service.lockRelatedRoots(
                command("asset-1"), record("asset-1"));
        service.captureRelated(
                command("asset-1"), record("asset-1"), record("asset-1"));

        assertEquals(Set.of(new RootKey("asset", "asset-1")), locked);
        verifyNoInteractions(policyMatcher, versionService, dataService);
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
