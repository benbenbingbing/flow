package com.workflow.entity.data.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 普通编辑不能通过任一请求层级或字段别名改写首次提交审计。 */
class EntityDataMutationPayloadMapperSubmissionAuditTest {

    private static final LocalDateTime FIRST_SUBMIT_TIME = LocalDateTime.of(2026, 10, 1, 9, 30);

    @ParameterizedTest(name = "nested={0}, snakeCase={1}, valueKind={2}")
    @MethodSource("submissionRequests")
    void preservesSubmittedAuditAgainstReplacementAndClearing(
            boolean nested, boolean snakeCase, String valueKind) {
        Map<String, Object> existing = existingBusinessData();
        existing.put("submit_time", FIRST_SUBMIT_TIME);
        existing.put("submitter_id", "first-submitter");
        existing.put("submitter_name", "首次提交人");

        Map<String, Object> updated = mapper().buildUpdateData(
                "asset", "record-1", request(nested, snakeCase, valueKind), existing);

        assertEquals(FIRST_SUBMIT_TIME, updated.get("submit_time"));
        assertEquals("first-submitter", updated.get("submitter_id"));
        assertEquals("首次提交人", updated.get("submitter_name"));
        assertEquals("编辑后的说明", updated.get("description"));
    }

    @ParameterizedTest(name = "nested={0}, snakeCase={1}, storedNulls={2}")
    @MethodSource("draftRequests")
    void cannotForgeFirstSubmissionOnDraft(boolean nested, boolean snakeCase, boolean storedNulls) {
        Map<String, Object> existing = existingBusinessData();
        // 数据库行可能包含 null 列，也可能省略 null 列，两种草稿表示都不能接受外部审计值。
        if (storedNulls) {
            existing.put("submit_time", null);
            existing.put("submitter_id", null);
            existing.put("submitter_name", null);
        }

        Map<String, Object> updated = mapper().buildUpdateData(
                "asset", "record-1", request(nested, snakeCase, "replacement"), existing);

        assertNull(updated.get("submit_time"));
        assertNull(updated.get("submitter_id"));
        assertNull(updated.get("submitter_name"));
        assertEquals("编辑后的说明", updated.get("description"));
    }

    private EntityDataMutationPayloadMapper mapper() {
        EntityPublishedSnapshotService snapshots = mock(EntityPublishedSnapshotService.class);
        EntityPublishedSnapshot snapshot = new EntityPublishedSnapshot();
        snapshot.setFields(List.of());
        when(snapshots.getLatestByEntityCode("asset")).thenReturn(snapshot);
        return new EntityDataMutationPayloadMapper(
                new EntityRuntimeRecordMapper(new ObjectMapper()),
                snapshots,
                mock(EntityDataMutationValidator.class));
    }

    private Map<String, Object> existingBusinessData() {
        Map<String, Object> existing = new HashMap<>();
        existing.put("id", "record-1");
        existing.put("description", "原说明");
        return existing;
    }

    private Map<String, Object> request(boolean nested, boolean snakeCase, String valueKind) {
        Map<String, Object> customData = new HashMap<>();
        customData.put("description", "编辑后的说明");
        Map<String, Object> request = new HashMap<>();
        request.put("data", customData);
        Map<String, Object> auditTarget = nested ? customData : request;
        Object timeValue = FIRST_SUBMIT_TIME.plusDays(1);
        Object idValue = "forged-submitter";
        Object nameValue = "伪造提交人";
        if (!"replacement".equals(valueKind)) {
            Object clearedValue = "null".equals(valueKind) ? null : "";
            timeValue = clearedValue;
            idValue = clearedValue;
            nameValue = clearedValue;
        }
        auditTarget.put(snakeCase ? "submit_time" : "submitTime", timeValue);
        auditTarget.put(snakeCase ? "submitter_id" : "submitterId", idValue);
        auditTarget.put(snakeCase ? "submitter_name" : "submitterName", nameValue);
        return request;
    }

    private static Stream<Arguments> submissionRequests() {
        return Stream.of(false, true).flatMap(nested ->
                Stream.of(false, true).flatMap(snakeCase ->
                        Stream.of("replacement", "null", "empty").map(valueKind ->
                                Arguments.of(nested, snakeCase, valueKind))));
    }

    private static Stream<Arguments> draftRequests() {
        return Stream.of(false, true).flatMap(nested ->
                Stream.of(false, true).flatMap(snakeCase ->
                        Stream.of(false, true).map(storedNulls ->
                                Arguments.of(nested, snakeCase, storedNulls))));
    }
}
