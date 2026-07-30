package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.entity.version.application.EntityRecordSnapshotService.SnapshotCapture;
import com.workflow.entity.version.application.EntityVersionPolicyMatcher.MatchedScenario;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import com.workflow.outbox.api.OutboxPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityRecordVersionServiceTest {

    @Mock
    private EntityRecordVersionMapper versionMapper;
    @Mock
    private EntityRecordSnapshotService snapshotService;
    @Mock
    private OutboxPublisher outboxPublisher;

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
                objectMapper);
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

    private EntityMutationCommand command(
            String idempotencyKey) {
        return new EntityMutationCommand(
                idempotencyKey,
                "asset",
                "record-1",
                EntityMutationOperationType.UPDATE,
                Map.of(),
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
