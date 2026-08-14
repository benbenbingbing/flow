package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.entity.version.application.EntityRecordSnapshotService.SnapshotCapture;
import com.workflow.entity.version.application.EntityRecordSnapshotService.SnapshotCaptureV2;
import com.workflow.entity.version.application.EntityVersionPolicyMatcher.MatchedScenario;
import com.workflow.entity.version.api.request.ManualVersionCaptureRequest;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionCounterMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionDatasetMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionDatasetRowMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionCounter;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import com.workflow.outbox.api.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class EntityRecordVersionServiceTest {

    @Mock
    private EntityRecordVersionMapper versionMapper;
    @Mock
    private EntityRecordSnapshotService snapshotService;
    @Mock
    private OutboxPublisher outboxPublisher;
    @Mock
    private EntityVersionConfigurationService configurationService;
    @Mock
    private EntityVersionPolicyMatcher policyMatcher;
    @Mock
    private EntityRecordVersionCounterMapper counterMapper;
    @Mock
    private EntityRecordVersionDatasetMapper datasetMapper;
    @Mock
    private EntityRecordVersionDatasetRowMapper datasetRowMapper;
    @Mock
    private EntityDataDynamicService dataService;
    @Mock
    private EntityAggregateWriter aggregateWriter;

    private ObjectMapper objectMapper;
    private EntityRecordVersionService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        service = new EntityRecordVersionService(
                versionMapper,
                snapshotService,
                outboxPublisher,
                objectMapper,
                configurationService,
                policyMatcher,
                counterMapper,
                datasetMapper,
                datasetRowMapper,
                dataService,
                aggregateWriter);
        EntityVersionConfiguration legacyRelease =
                new EntityVersionConfiguration();
        legacyRelease.setSchemaVersion(1);
        legacyRelease.setActiveReleaseId("release-1");
        lenient().when(configurationService.getPublishedRelease(
                        anyString(), anyString()))
                .thenReturn(Optional.of(legacyRelease));
    }

    @Test
    void manualCaptureUsesReadCommittedForLockThenReload() throws Exception {
        Transactional transactional = EntityRecordVersionService.class
                .getMethod(
                        "captureManual",
                        String.class,
                        String.class,
                        ManualVersionCaptureRequest.class,
                        String.class)
                .getAnnotation(Transactional.class);

        assertEquals(Isolation.READ_COMMITTED, transactional.isolation());
    }

    @Test
    void sameSnapshotWithDifferentMutationKeysCreatesNextVersion() {
        Map<String, Object> snapshot =
                snapshot("相同名称");
        when(snapshotService.capture(
                eq("asset"),
                eq("record-1"),
                any(),
                eq(false))).thenReturn(
                        new SnapshotCapture(
                                snapshot,
                                "same-hash",
                                "entity-release-1",
                                1));
        when(versionMapper.findMaxVersionNo(
                "asset",
                "record-1")).thenReturn(0, 1);
        when(counterMapper.lock("asset", "record-1"))
                .thenReturn(counter(0), counter(0), counter(1), counter(1));
        when(counterMapper.update(eq("asset"), eq("record-1"), any()))
                .thenReturn(1);
        MatchedScenario scenario = scenario();

        EntityRecordVersion first =
                service.createIfMatched(
                        command("mutation-1"),
                        scenario,
                        Map.of("id", "record-1"),
                        false);
        EntityRecordVersion second =
                service.createIfMatched(
                        command("mutation-2"),
                        scenario,
                        Map.of("id", "record-1"),
                        false);

        assertEquals(1, first.getVersionNo());
        assertEquals(2, second.getVersionNo());
        assertEquals(first.getSnapshotHash(),
                second.getSnapshotHash());
        verify(versionMapper, times(2))
                .insert(any(EntityRecordVersion.class));
        verify(outboxPublisher, times(2))
                .publish(any());
    }

    @Test
    void compareReportsFormalVersionWithoutFieldChanges()
            throws Exception {
        String document =
                objectMapper.writeValueAsString(
                        snapshot("相同名称"));
        EntityRecordVersion first =
                storedVersion(1, document);
        EntityRecordVersion second =
                storedVersion(2, document);
        when(versionMapper.findVersion(
                "asset",
                "record-1",
                1)).thenReturn(first);
        when(versionMapper.findVersion(
                "asset",
                "record-1",
                2)).thenReturn(second);

        Map<String, Object> result =
                service.compare(
                        "asset",
                        "record-1",
                        1,
                        2);

        assertEquals(false, result.get("hasChanges"));
        assertEquals(0, result.get("changedFieldCount"));
        assertEquals(
                "无字段变化的正式版本",
                result.get("message"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> groups =
                (List<Map<String, Object>>) result.get(
                        "groups");
        assertEquals(
                List.of(
                        "SYSTEM",
                        "BUSINESS",
                        "SUBFORM",
                        "RELATION"),
                groups.stream()
                        .map(group -> String.valueOf(
                                group.get("code")))
                        .toList());
        assertFalse(groups.isEmpty());
    }

    @Test
    void sameIdempotencyKeyWithDifferentPayloadOrTriggerIsRejected() {
        when(snapshotService.capture(
                eq("asset"), eq("record-1"), any(), eq(false)))
                .thenReturn(new SnapshotCapture(
                        snapshot("名称"), "hash", "entity-release-1", 1));
        when(versionMapper.findMaxVersionNo("asset", "record-1"))
                .thenReturn(0);
        when(counterMapper.lock("asset", "record-1"))
                .thenReturn(counter(0), counter(0));
        when(counterMapper.update("asset", "record-1", 1)).thenReturn(1);
        AtomicReference<EntityRecordVersion> stored = new AtomicReference<>();
        when(versionMapper.findIdempotent(
                eq("asset"), eq("record-1"), eq("same-key")))
                .thenAnswer(invocation -> stored.get());
        when(versionMapper.insert(any(EntityRecordVersion.class)))
                .thenAnswer(invocation -> {
                    stored.set(invocation.getArgument(0));
                    return 1;
                });

        service.createIfMatched(
                command("same-key", Map.of("data", Map.of("name", "A"))),
                scenario(), Map.of("id", "record-1"), false);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.createIfMatched(
                        command("same-key",
                                Map.of("data", Map.of("name", "B"))),
                        scenario(), Map.of("id", "record-1"), false));
        assertEquals("ENTITY_VERSION_IDEMPOTENCY_CONFLICT",
                exception.getErrorCode());

        MatchedScenario anotherTrigger = new MatchedScenario(
                "MANUAL_REVIEW",
                "人工复核",
                null,
                90,
                "release-1",
                1);
        BusinessConflictException triggerConflict = assertThrows(
                BusinessConflictException.class,
                () -> service.createIfMatched(
                        command("same-key",
                                Map.of("data", Map.of("name", "A"))),
                        anotherTrigger,
                        Map.of("id", "record-1"),
                        false));
        assertEquals("ENTITY_VERSION_IDEMPOTENCY_CONFLICT",
                triggerConflict.getErrorCode());
        verify(versionMapper, times(1)).insert(any(EntityRecordVersion.class));
    }

    @Test
    void manualCaptureIsIdempotentAndUsesCounterOnce() {
        EntityVersionConfiguration configuration =
                new EntityVersionConfiguration();
        configuration.setEnabled(true);
        configuration.setSchemaVersion(2);
        configuration.setActiveReleaseId("release-1");
        when(configurationService.getPublished("asset"))
                .thenReturn(Optional.of(configuration));
        when(configurationService.getPublishedRelease(
                "asset", "release-1"))
                .thenReturn(Optional.of(configuration));
        MatchedScenario manual = new MatchedScenario(
                "MANUAL_CHECKPOINT", "手工固化",
                "V${versionNo} ${triggerName}", 1, "release-1", 1);
        when(policyMatcher.matchManual(configuration, null))
                .thenReturn(Optional.of(manual));
        EntityDataDTO record = new EntityDataDTO();
        record.setId("record-1");
        record.setEntityCode("asset");
        record.setData(Map.of("name", "服务器"));
        when(dataService.findAccessibleById("asset", "record-1", null))
                .thenReturn(record);
        when(snapshotService.captureV2(
                eq(configuration), eq("record-1"), any(), eq(false)))
                .thenReturn(new SnapshotCaptureV2(
                        Map.of("schemaVersion", 2),
                        "data-hash", "presentation-hash", "scope-hash",
                        "entity-release-1", 1, List.of(), 0, 100));
        when(versionMapper.findMaxVersionNo("asset", "record-1"))
                .thenReturn(0);
        when(counterMapper.lock("asset", "record-1"))
                .thenReturn(counter(0), counter(0));
        when(counterMapper.update("asset", "record-1", 1)).thenReturn(1);
        AtomicReference<EntityRecordVersion> stored = new AtomicReference<>();
        when(versionMapper.findIdempotent(
                eq("asset"), eq("record-1"), eq("manual-key")))
                .thenAnswer(invocation -> stored.get());
        when(versionMapper.insert(any(EntityRecordVersion.class)))
                .thenAnswer(invocation -> {
                    stored.set(invocation.getArgument(0));
                    return 1;
                });

        EntityRecordVersion first = service.captureManual(
                "asset", "record-1", new ManualVersionCaptureRequest(),
                "manual-key");
        EntityRecordVersion replay = service.captureManual(
                "asset", "record-1", new ManualVersionCaptureRequest(),
                "manual-key");

        assertSame(first, replay);
        assertEquals("V1 手工固化", first.getVersionTitle());
        verify(aggregateWriter, times(2)).lock("asset", "record-1");
        verify(dataService, times(4)).findAccessibleById(
                "asset", "record-1", null);
        verify(versionMapper, times(1)).insert(any(EntityRecordVersion.class));
        verify(counterMapper, times(2)).lock("asset", "record-1");
    }

    @Test
    void retriesVersionNumberAfterRollingUpgradeAllocatorCollision() {
        when(snapshotService.capture(
                eq("asset"), eq("record-1"), any(), eq(false)))
                .thenReturn(new SnapshotCapture(
                        snapshot("名称"), "hash", "entity-release-1", 1));
        when(versionMapper.findMaxVersionNo("asset", "record-1"))
                .thenReturn(0, 1);
        when(counterMapper.lock("asset", "record-1"))
                .thenReturn(
                        counter(0), counter(0),
                        counter(1), counter(1));
        when(counterMapper.update("asset", "record-1", 1)).thenReturn(1);
        when(counterMapper.update("asset", "record-1", 2)).thenReturn(1);
        when(versionMapper.insert(any(EntityRecordVersion.class)))
                .thenThrow(new org.springframework.dao.DuplicateKeyException(
                        "legacy allocator used V1"))
                .thenReturn(1);

        EntityRecordVersion created = service.createIfMatched(
                command("rolling-key"),
                scenario(),
                Map.of("id", "record-1"),
                false);

        assertEquals(2, created.getVersionNo());
        assertEquals("V2 变更审批生效", created.getVersionTitle());
        verify(versionMapper, times(2)).insert(any(EntityRecordVersion.class));
    }

    @Test
    void missingMatchedReleaseFailsInsteadOfSilentlyCapturingV1() {
        when(configurationService.getPublishedRelease(
                "asset", "release-1")).thenReturn(Optional.empty());

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.createIfMatched(
                        command("mutation-missing-release"),
                        scenario(), Map.of("id", "record-1"), false));

        assertEquals("ENTITY_VERSION_RELEASE_NOT_FOUND",
                exception.getErrorCode());
        verify(snapshotService, never()).capture(
                anyString(), anyString(), any(), anyBoolean());
        verify(snapshotService, never()).captureV2(
                any(), anyString(), any(), anyBoolean());
    }

    private EntityMutationCommand command(
            String idempotencyKey) {
        return command(idempotencyKey, Map.of());
    }

    private EntityMutationCommand command(
            String idempotencyKey,
            Map<String, Object> payload) {
        return new EntityMutationCommand(
                idempotencyKey,
                "asset",
                "record-1",
                EntityMutationOperationType.UPDATE,
                payload,
                EntityMutationContext.builder(
                                EntityMutationSourceType.APPROVAL_TASK,
                                "CHANGE_EFFECTIVE",
                                "变更审批生效")
                        .operator("user-1", "张三")
                        .trace("trace-" + idempotencyKey,
                                idempotencyKey)
                        .build());
    }

    private MatchedScenario scenario() {
        return new MatchedScenario(
                "CHANGE_EFFECTIVE",
                "变更审批生效",
                null,
                100,
                "release-1",
                1);
    }

    private EntityRecordVersion storedVersion(
            int versionNo,
            String document) {
        EntityRecordVersion value =
                new EntityRecordVersion();
        value.setId("version-" + versionNo);
        value.setEntityCode("asset");
        value.setRecordId("record-1");
        value.setVersionNo(versionNo);
        value.setVersionTitle("V" + versionNo);
        value.setScenarioCode("CHANGE_EFFECTIVE");
        value.setScenarioName("变更审批生效");
        value.setSnapshotDocument(document);
        value.setSnapshotHash("same-hash");
        value.setCreateTime(LocalDateTime.of(
                2026,
                7,
                28,
                10,
                versionNo));
        return value;
    }

    private EntityRecordVersionCounter counter(int versionNo) {
        EntityRecordVersionCounter value =
                new EntityRecordVersionCounter();
        value.setEntityCode("asset");
        value.setRecordId("record-1");
        value.setLastVersionNo(versionNo);
        return value;
    }

    private Map<String, Object> snapshot(
            String name) {
        Map<String, Object> field = Map.of(
                "fieldCode", "name",
                "fieldName", "名称",
                "fieldType", "TEXT",
                "value", name,
                "displayValue", name,
                "group", "BUSINESS");
        return Map.of(
                "recordId", "record-1",
                "record", Map.of(
                        "id", "record-1",
                        "data", Map.of("name", name)),
                "fields", List.of(field));
    }
}
