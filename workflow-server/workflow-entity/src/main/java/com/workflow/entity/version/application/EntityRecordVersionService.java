package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.entity.version.application.EntityRecordSnapshotService.SnapshotCapture;
import com.workflow.entity.version.application.EntityVersionPolicyMatcher.MatchedScenario;
import com.workflow.entity.version.application.model.EntityRecordVersionSummary;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 业务实体完整版本的写入、查询和比较服务。
 */
@Service
@RequiredArgsConstructor
public class EntityRecordVersionService {

    public static final String VERSION_CREATED_TOPIC =
            "ENTITY_RECORD_VERSION_CREATED";

    private final EntityRecordVersionMapper versionMapper;
    private final EntityRecordSnapshotService snapshotService;
    private final OutboxPublisher outboxPublisher;
    private final ObjectMapper objectMapper;

    @Transactional(rollbackFor = Exception.class)
    public EntityRecordVersion createIfMatched(
            EntityMutationCommand command,
            MatchedScenario scenario,
            Map<String, Object> aggregateRecord,
            boolean deletedSnapshot) {
        EntityRecordVersion existing =
                versionMapper.findIdempotent(
                        command.entityCode(),
                        command.recordId(),
                        command.context().idempotencyKey(),
                        scenario.scenarioCode());
        if (existing != null) {
            return existing;
        }
        SnapshotCapture capture = snapshotService.capture(
                command.entityCode(),
                command.recordId(),
                aggregateRecord,
                deletedSnapshot);
        int versionNo = value(
                versionMapper.findMaxVersionNo(
                        command.entityCode(),
                        command.recordId())) + 1;
        EntityRecordVersion version = new EntityRecordVersion();
        version.setId(id());
        version.setEntityCode(command.entityCode());
        version.setRecordId(command.recordId());
        version.setVersionNo(versionNo);
        version.setVersionTitle(title(
                scenario,
                versionNo,
                command));
        version.setScenarioCode(scenario.scenarioCode());
        version.setScenarioName(scenario.scenarioName());
        version.setOperationType(
                command.operationType().name());
        version.setSourceType(
                command.context().sourceType().name());
        version.setSourceId(command.context().sourceId());
        version.setBusinessIntentCode(
                command.context().businessIntentCode());
        version.setBusinessIntentName(
                command.context().businessIntentName());
        version.setSourceEntityCode(
                command.context().sourceEntityCode());
        version.setSourceRecordId(
                command.context().sourceRecordId());
        version.setProcessDefinitionId(
                command.context().processDefinitionId());
        version.setProcessInstanceId(
                command.context().processInstanceId());
        version.setTaskId(command.context().taskId());
        version.setOperatorId(
                command.context().operatorId());
        version.setOperatorName(
                command.context().operatorName());
        version.setBusinessTraceKey(
                command.context().businessTraceKey());
        version.setIdempotencyKey(
                command.context().idempotencyKey());
        version.setEntityReleaseId(
                capture.entityReleaseId());
        version.setEntityReleaseVersion(
                capture.entityReleaseVersion());
        version.setSnapshotHash(capture.hash());
        version.setSnapshotDocument(
                write(capture.document()));
        version.setCreateTime(LocalDateTime.now());
        try {
            versionMapper.insert(version);
        } catch (DuplicateKeyException exception) {
            EntityRecordVersion raced =
                    versionMapper.findIdempotent(
                            command.entityCode(),
                            command.recordId(),
                            command.context().idempotencyKey(),
                            scenario.scenarioCode());
            if (raced != null) {
                return raced;
            }
            throw exception;
        }
        outboxPublisher.publish(OutboxPublishRequest.of(
                VERSION_CREATED_TOPIC,
                version.getId(),
                "ENTITY_RECORD_VERSION",
                version.getId(),
                Map.of(
                        "versionId", version.getId(),
                        "entityCode", version.getEntityCode(),
                        "recordId", version.getRecordId(),
                        "versionNo", version.getVersionNo(),
                        "scenarioCode",
                        version.getScenarioCode())));
        return version;
    }

    @Transactional(readOnly = true)
    public Integer currentVersionNo(
            String entityCode,
            String recordId) {
        return value(versionMapper.findMaxVersionNo(
                entityCode, recordId));
    }

    @Transactional(readOnly = true)
    public List<EntityRecordVersionSummary> list(
            String entityCode,
            String recordId) {
        List<EntityRecordVersion> values =
                versionMapper.findByRecord(
                        entityCode, recordId);
        Map<Integer, EntityRecordVersion> byNo =
                new LinkedHashMap<>();
        for (EntityRecordVersion value : values) {
            byNo.put(value.getVersionNo(), value);
        }
        return values.stream().map(item -> {
            EntityRecordVersion previous =
                    byNo.get(item.getVersionNo() - 1);
            return new EntityRecordVersionSummary(
                    item.getId(),
                    item.getVersionNo(),
                    item.getVersionTitle(),
                    item.getScenarioCode(),
                    item.getScenarioName(),
                    item.getOperationType(),
                    item.getSourceType(),
                    item.getBusinessIntentCode(),
                    item.getBusinessIntentName(),
                    item.getOperatorId(),
                    item.getOperatorName(),
                    item.getProcessInstanceId(),
                    item.getSourceEntityCode(),
                    item.getSourceRecordId(),
                    previous == null
                            || !Objects.equals(
                                    previous.getSnapshotHash(),
                                    item.getSnapshotHash()),
                    item.getCreateTime());
        }).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(
            String entityCode,
            String recordId,
            Integer versionNo) {
        EntityRecordVersion version = requireVersion(
                entityCode, recordId, versionNo);
        Map<String, Object> result =
                objectMapper.convertValue(
                        version,
                        new TypeReference<>() {
                        });
        result.put("snapshot",
                read(version.getSnapshotDocument()));
        result.remove("snapshotDocument");
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> compare(
            String entityCode,
            String recordId,
            Integer fromVersionNo,
            Integer toVersionNo) {
        EntityRecordVersion from = requireVersion(
                entityCode, recordId, fromVersionNo);
        EntityRecordVersion to = requireVersion(
                entityCode, recordId, toVersionNo);
        Map<String, Map<String, Object>> fromFields =
                indexFields(read(from.getSnapshotDocument()));
        Map<String, Map<String, Object>> toFields =
                indexFields(read(to.getSnapshotDocument()));
        Map<String, String> groupLabels =
                new LinkedHashMap<>();
        groupLabels.put("SYSTEM", "系统字段");
        groupLabels.put("BUSINESS", "业务字段");
        groupLabels.put("SUBFORM", "子表单");
        groupLabels.put("RELATION", "关系数据");
        Map<String, List<Map<String, Object>>> grouped =
                new LinkedHashMap<>();
        groupLabels.keySet().forEach(key ->
                grouped.put(key, new ArrayList<>()));
        List<String> fieldCodes = new ArrayList<>();
        fieldCodes.addAll(fromFields.keySet());
        toFields.keySet().stream()
                .filter(code -> !fieldCodes.contains(code))
                .forEach(fieldCodes::add);
        int changedCount = 0;
        for (String fieldCode : fieldCodes) {
            Map<String, Object> left =
                    fromFields.get(fieldCode);
            Map<String, Object> right =
                    toFields.get(fieldCode);
            String changeType = changeType(left, right);
            if (!"UNCHANGED".equals(changeType)) {
                changedCount++;
            }
            String group = text(right == null
                    ? left.get("group")
                    : right.get("group"));
            if (!grouped.containsKey(group)) {
                group = "BUSINESS";
            }
            Map<String, Object> change =
                    new LinkedHashMap<>();
            change.put("fieldCode", fieldCode);
            change.put("fieldName", right != null
                    ? right.get("fieldName")
                    : left.get("fieldName"));
            change.put("fieldType", right != null
                    ? right.get("fieldType")
                    : left.get("fieldType"));
            change.put("oldValue", left == null
                    ? null : left.get("value"));
            change.put("newValue", right == null
                    ? null : right.get("value"));
            change.put("oldDisplayValue", left == null
                    ? null : left.get("displayValue"));
            change.put("newDisplayValue", right == null
                    ? null : right.get("displayValue"));
            change.put("changeType", changeType);
            grouped.get(group).add(change);
        }
        List<Map<String, Object>> groups =
                new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry
                : grouped.entrySet()) {
            groups.add(Map.of(
                    "code", entry.getKey(),
                    "name", groupLabels.get(entry.getKey()),
                    "fields", entry.getValue()));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fromVersion", summary(from));
        result.put("toVersion", summary(to));
        result.put("changedFieldCount", changedCount);
        result.put("hasChanges", changedCount > 0);
        result.put("message", changedCount == 0
                ? "无字段变化的正式版本"
                : "共 " + changedCount + " 个字段发生变化");
        result.put("groups", groups);
        return result;
    }

    private Map<String, Object> summary(
            EntityRecordVersion value) {
        return Map.of(
                "versionNo", value.getVersionNo(),
                "versionTitle", value.getVersionTitle(),
                "scenarioCode", value.getScenarioCode(),
                "scenarioName", value.getScenarioName(),
                "createTime", value.getCreateTime());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> indexFields(
            Map<String, Object> snapshot) {
        Map<String, Map<String, Object>> result =
                new LinkedHashMap<>();
        Object fields = snapshot.get("fields");
        if (!(fields instanceof List<?> list)) {
            return result;
        }
        for (Object value : list) {
            if (!(value instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> field =
                    (Map<String, Object>) map;
            String code = text(field.get("fieldCode"));
            if (StringUtils.hasText(code)) {
                result.put(code, field);
            }
        }
        return result;
    }

    private String changeType(
            Map<String, Object> left,
            Map<String, Object> right) {
        if (left == null) {
            return "ADDED";
        }
        if (right == null) {
            return "REMOVED";
        }
        return Objects.equals(left.get("value"),
                right.get("value"))
                ? "UNCHANGED" : "MODIFIED";
    }

    private EntityRecordVersion requireVersion(
            String entityCode,
            String recordId,
            Integer versionNo) {
        EntityRecordVersion version =
                versionMapper.findVersion(
                        entityCode,
                        recordId,
                        versionNo);
        if (version == null) {
            throw new IllegalArgumentException(
                    "实体数据版本不存在: "
                            + entityCode + "/"
                            + recordId + "/V"
                            + versionNo);
        }
        return version;
    }

    private String title(
            MatchedScenario scenario,
            int versionNo,
            EntityMutationCommand command) {
        String template =
                scenario.versionTitleTemplate();
        if (!StringUtils.hasText(template)) {
            return "V" + versionNo + " "
                    + scenario.scenarioName();
        }
        return template
                .replace("${versionNo}",
                        String.valueOf(versionNo))
                .replace("${scenarioName}",
                        scenario.scenarioName())
                .replace("${businessIntentName}",
                        command.context()
                                .businessIntentName());
    }

    private Map<String, Object> read(String document) {
        try {
            return objectMapper.readValue(
                    document,
                    new TypeReference<>() {
                    });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "实体数据版本快照解析失败",
                    exception);
        }
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "实体数据版本快照序列化失败",
                    exception);
        }
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String text(Object value) {
        return value == null
                ? null : String.valueOf(value);
    }

    private String id() {
        return UUID.randomUUID().toString()
                .replace("-", "");
    }
}
