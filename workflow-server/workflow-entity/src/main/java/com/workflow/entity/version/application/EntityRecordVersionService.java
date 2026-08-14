package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.result.PageResult;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.EntityAggregateWriter;
import com.workflow.entity.version.application.EntityRecordSnapshotService.SnapshotCapture;
import com.workflow.entity.version.application.EntityRecordSnapshotService.SnapshotCaptureV2;
import com.workflow.entity.version.application.EntityRecordSnapshotService.DatasetCapture;
import com.workflow.entity.version.application.EntityRecordSnapshotService.DatasetRowCapture;
import com.workflow.entity.version.application.EntityVersionPolicyMatcher.MatchedScenario;
import com.workflow.entity.version.api.request.ManualVersionCaptureRequest;
import com.workflow.entity.version.application.model.EntityRecordVersionSummary;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionCounterMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionDatasetMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionDatasetRowMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionCounter;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionDataset;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionDatasetRow;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import com.workflow.outbox.api.OutboxPublishRequest;
import com.workflow.outbox.api.OutboxPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
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
    private final EntityVersionConfigurationService configurationService;
    private final EntityVersionPolicyMatcher policyMatcher;
    private final EntityRecordVersionCounterMapper counterMapper;
    private final EntityRecordVersionDatasetMapper datasetMapper;
    private final EntityRecordVersionDatasetRowMapper datasetRowMapper;
    private final EntityDataDynamicService dataService;
    private final EntityAggregateWriter aggregateWriter;

    @Transactional(rollbackFor = Exception.class)
    public EntityRecordVersion createIfMatched(
            EntityMutationCommand command,
            MatchedScenario scenario,
            Map<String, Object> aggregateRecord,
            boolean deletedSnapshot) {
        String requestHash = requestHash(
                command, scenario, deletedSnapshot);
        EntityRecordVersion existing = requireIdempotentMatch(
                command, scenario, requestHash);
        if (existing != null) {
            return existing;
        }
        EntityVersionConfiguration published = null;
        if (StringUtils.hasText(scenario.releaseId())) {
            published = configurationService.getPublishedRelease(
                            command.entityCode(), scenario.releaseId())
                    .orElseThrow(() -> new BusinessConflictException(
                            "ENTITY_VERSION_RELEASE_NOT_FOUND",
                            "命中的数据版本发布快照不存在或不属于当前实体: "
                                    + scenario.releaseId()));
        }
        SnapshotCapture legacyCapture = null;
        SnapshotCaptureV2 captureV2 = null;
        if (published != null
                && value(published.getSchemaVersion()) >= 2) {
            captureV2 = snapshotService.captureV2(
                    published,
                    command.recordId(),
                    aggregateRecord,
                    deletedSnapshot);
        } else {
            legacyCapture = snapshotService.capture(
                    command.entityCode(),
                    command.recordId(),
                    aggregateRecord,
                    deletedSnapshot);
        }
        lockCounter(command.entityCode(), command.recordId());
        existing = requireIdempotentMatch(
                command, scenario, requestHash, true);
        if (existing != null) {
            return existing;
        }
        int versionNo = incrementCounter(
                command.entityCode(), command.recordId());
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
        version.setRequestHash(requestHash);
        version.setConfigReleaseId(scenario.releaseId());
        version.setConfigReleaseVersion(scenario.releaseVersion());
        if (captureV2 != null) {
            populateV2(version, captureV2);
        } else {
            populateV1(version, legacyCapture);
        }
        version.setCreateTime(LocalDateTime.now());
        for (int attempt = 1; ; attempt++) {
            try {
                versionMapper.insert(version);
                break;
            } catch (DuplicateKeyException exception) {
                EntityRecordVersion raced = findIdempotent(command, true);
                if (raced != null) {
                    if (StringUtils.hasText(raced.getRequestHash())
                            && !Objects.equals(
                                    raced.getRequestHash(), requestHash)) {
                        throw new BusinessConflictException(
                                "ENTITY_VERSION_IDEMPOTENCY_CONFLICT",
                                "相同 Idempotency-Key 已用于不同的版本固化请求");
                    }
                    return raced;
                }
                if (attempt >= 3) {
                    throw exception;
                }
                // 滚动升级期间旧 Pod 仍可能用 MAX+1 分配同一版本号。
                // 重新按真实表最大值追平计数器后有限重试，不覆盖任何历史版本。
                lockCounter(command.entityCode(), command.recordId());
                int retriedVersionNo = incrementCounter(
                        command.entityCode(), command.recordId());
                version.setVersionNo(retriedVersionNo);
                version.setVersionTitle(title(
                        scenario, retriedVersionNo, command));
            }
        }
        if (captureV2 != null) {
            persistDatasets(version, captureV2);
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

    @Transactional(
            rollbackFor = Exception.class,
            isolation = Isolation.READ_COMMITTED)
    public EntityRecordVersion captureManual(
            String entityCode,
            String recordId,
            ManualVersionCaptureRequest request,
            String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            throw new IllegalArgumentException("手工固化必须提供 Idempotency-Key");
        }
        EntityVersionConfiguration configuration = configurationService
                .getPublished(entityCode)
                .filter(item -> Boolean.TRUE.equals(item.getEnabled()))
                .orElseThrow(() -> new IllegalArgumentException(
                        "实体没有启用的已发布数据版本策略: " + entityCode));
        if (value(configuration.getSchemaVersion()) < 2) {
            throw new IllegalArgumentException("手工固化只支持已发布的V2版本策略");
        }
        ManualVersionCaptureRequest effective = request == null
                ? new ManualVersionCaptureRequest() : request;
        MatchedScenario scenario = policyMatcher.matchManual(
                        configuration, effective.getTriggerCode())
                .orElseThrow(() -> new IllegalArgumentException(
                        "没有可用的手工固化触发器"));
        dataService.findAccessibleById(
                entityCode, recordId, null);
        aggregateWriter.lock(entityCode, recordId);
        EntityDataDTO record = dataService.findAccessibleById(
                entityCode, recordId, null);
        Map<String, Object> aggregate = objectMapper.convertValue(
                record, new TypeReference<>() { });
        Map<String, Object> extras = new LinkedHashMap<>();
        extras.put("manualReason", effective.getReason());
        EntityMutationContext context = EntityMutationContext.builder(
                        EntityMutationSourceType.SYSTEM_TASK,
                        defaultText(effective.getBusinessIntentCode(),
                                "MANUAL_CHECKPOINT"),
                        defaultText(effective.getBusinessIntentName(),
                                "手工固化"))
                .sourceId("manual-capture")
                .operator(UserContext.getUserId(), UserContext.getUsername())
                .trace("manual:" + entityCode + ":" + recordId,
                        idempotencyKey.trim())
                .extraParams(extras)
                .build();
        EntityMutationCommand command = new EntityMutationCommand(
                idempotencyKey,
                entityCode,
                recordId,
                EntityMutationOperationType.UPDATE,
                Map.of(),
                context);
        return createIfMatched(
                command, scenario, aggregate, false);
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
        return listPage(entityCode, recordId, 1, 200).getRecords();
    }

    @Transactional(readOnly = true)
    public PageResult<EntityRecordVersionSummary> listPage(
            String entityCode,
            String recordId,
            long requestedPageNum,
            long requestedPageSize) {
        long pageNum = Math.max(1, requestedPageNum);
        long pageSize = Math.max(1, Math.min(100, requestedPageSize));
        long total = versionMapper.countByRecord(entityCode, recordId);
        List<EntityRecordVersion> values = versionMapper.findSummaryPage(
                entityCode, recordId, (pageNum - 1) * pageSize, pageSize);
        List<EntityRecordVersionSummary> records = values.stream()
                .map(item -> toSummary(
                        item,
                        versionMapper.findDataHash(
                                entityCode,
                                recordId,
                                item.getVersionNo() - 1)))
                .toList();
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    private EntityRecordVersionSummary toSummary(
            EntityRecordVersion item,
            String previousHash) {
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
                    previousHash == null
                            || !Objects.equals(previousHash,
                                    firstText(item.getDataHash(),
                                            item.getSnapshotHash())),
                    item.getCreateTime());
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
        if (value(version.getSchemaVersion()) >= 2) {
            result.put("datasets", datasetMapper.findByVersionId(
                            version.getId()).stream()
                    .map(this::datasetSummary)
                    .toList());
        } else {
            result.put("datasets", List.of());
        }
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

    private EntityRecordVersion requireIdempotentMatch(
            EntityMutationCommand command,
            MatchedScenario scenario,
            String requestHash) {
        return requireIdempotentMatch(
                command, scenario, requestHash, false);
    }

    private EntityRecordVersion requireIdempotentMatch(
            EntityMutationCommand command,
            MatchedScenario scenario,
            String requestHash,
            boolean lock) {
        EntityRecordVersion existing = findIdempotent(command, lock);
        if (existing != null
                && StringUtils.hasText(existing.getRequestHash())
                && !Objects.equals(existing.getRequestHash(), requestHash)) {
            throw new BusinessConflictException(
                    "ENTITY_VERSION_IDEMPOTENCY_CONFLICT",
                    "相同 Idempotency-Key 已用于不同的版本固化请求");
        }
        return existing;
    }

    private EntityRecordVersion findIdempotent(
            EntityMutationCommand command,
            boolean lock) {
        if (lock) {
            return versionMapper.findIdempotentForUpdate(
                    command.entityCode(),
                    command.recordId(),
                    command.context().idempotencyKey());
        }
        return versionMapper.findIdempotent(
                command.entityCode(),
                command.recordId(),
                command.context().idempotencyKey());
    }

    private void lockCounter(String entityCode, String recordId) {
        int initial = value(versionMapper.findMaxVersionNo(
                entityCode, recordId));
        counterMapper.initialize(entityCode, recordId, initial);
        EntityRecordVersionCounter counter = counterMapper.lock(
                entityCode, recordId);
        if (counter == null) {
            throw new IllegalStateException("实体记录版本号计数器初始化失败");
        }
    }

    private int incrementCounter(String entityCode, String recordId) {
        EntityRecordVersionCounter counter = counterMapper.lock(
                entityCode, recordId);
        if (counter == null) {
            throw new IllegalStateException("实体记录版本号计数器不存在");
        }
        int next = value(counter.getLastVersionNo()) + 1;
        if (counterMapper.update(entityCode, recordId, next) != 1) {
            throw new IllegalStateException("实体记录版本号计数器更新失败");
        }
        return next;
    }

    private void populateV1(
            EntityRecordVersion version,
            SnapshotCapture capture) {
        version.setSchemaVersion(1);
        version.setEntityReleaseId(capture.entityReleaseId());
        version.setEntityReleaseVersion(capture.entityReleaseVersion());
        version.setSnapshotHash(capture.hash());
        version.setDataHash(capture.hash());
        version.setDatasetCount(0);
        version.setSnapshotRowCount(1);
        version.setSnapshotDocument(write(capture.document()));
        version.setSnapshotSizeBytes((long) version.getSnapshotDocument()
                .getBytes(StandardCharsets.UTF_8).length);
        version.setCompleteness("COMPLETE");
    }

    private void populateV2(
            EntityRecordVersion version,
            SnapshotCaptureV2 capture) {
        version.setSchemaVersion(2);
        version.setEntityReleaseId(capture.entityReleaseId());
        version.setEntityReleaseVersion(capture.entityReleaseVersion());
        version.setSnapshotHash(capture.dataHash());
        version.setDataHash(capture.dataHash());
        version.setPresentationHash(capture.presentationHash());
        version.setScopeHash(capture.scopeHash());
        version.setDatasetCount(capture.datasets().size());
        version.setSnapshotRowCount(capture.relationRowCount() + 1);
        version.setSnapshotSizeBytes(capture.sizeBytes());
        version.setCompleteness("COMPLETE");
        version.setSnapshotDocument(write(capture.rootDocument()));
    }

    private void persistDatasets(
            EntityRecordVersion version,
            SnapshotCaptureV2 capture) {
        LocalDateTime now = LocalDateTime.now();
        for (DatasetCapture item : capture.datasets()) {
            EntityRecordVersionDataset dataset =
                    new EntityRecordVersionDataset();
            dataset.setId(id());
            dataset.setVersionId(version.getId());
            dataset.setNodeCode(item.nodeCode());
            dataset.setNodeKind("RELATION");
            dataset.setRelationCode(item.relationCode());
            dataset.setRelationName(item.relationName());
            dataset.setEntityCode(item.entityCode());
            dataset.setEntityName(item.entityName());
            dataset.setEntityReleaseId(item.entityReleaseId());
            dataset.setEntityReleaseVersion(item.entityReleaseVersion());
            dataset.setSelectorDocument(write(item.selector()));
            dataset.setPresentationDocument(write(item.presentation()));
            dataset.setDataHash(item.dataHash());
            dataset.setPresentationHash(item.presentationHash());
            dataset.setScopeHash(item.scopeHash());
            dataset.setRowCount(item.rows().size());
            dataset.setComplete(true);
            dataset.setCreateTime(now);
            datasetMapper.insert(dataset);
            for (DatasetRowCapture row : item.rows()) {
                EntityRecordVersionDatasetRow value =
                        new EntityRecordVersionDatasetRow();
                value.setId(id());
                value.setDatasetId(dataset.getId());
                value.setRecordId(row.recordId());
                value.setRecordTitle(row.recordTitle());
                value.setRowOrder(row.rowOrder());
                value.setRowHash(row.rowHash());
                value.setValuesDocument(write(row.values()));
                value.setCreateTime(now);
                datasetRowMapper.insert(value);
            }
        }
    }

    private Map<String, Object> datasetSummary(
            EntityRecordVersionDataset dataset) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeCode", dataset.getNodeCode());
        result.put("nodeKind", dataset.getNodeKind());
        result.put("relationCode", dataset.getRelationCode());
        result.put("relationName", dataset.getRelationName());
        result.put("entityCode", dataset.getEntityCode());
        result.put("entityName", dataset.getEntityName());
        result.put("rowCount", dataset.getRowCount());
        result.put("complete", dataset.getComplete());
        result.put("presentation", read(dataset.getPresentationDocument()));
        return result;
    }

    private String requestHash(
            EntityMutationCommand command,
            MatchedScenario scenario,
            boolean deletedSnapshot) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("entityCode", command.entityCode());
        material.put("recordId", command.recordId());
        material.put("operationType", command.operationType().name());
        material.put("payload", command.payload());
        material.put("deletedSnapshot", deletedSnapshot);
        material.put("triggerCode", scenario.scenarioCode());
        material.put("releaseId", scenario.releaseId());
        material.put("businessIntentCode",
                command.context().businessIntentCode());
        material.put("sourceEntityCode",
                command.context().sourceEntityCode());
        material.put("sourceRecordId",
                command.context().sourceRecordId());
        material.put("extraParams", command.context().extraParams());
        return digest(material);
    }

    private String digest(Object value) {
        try {
            byte[] bytes = objectMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException("版本请求摘要计算失败", exception);
        }
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
        String triggerName = defaultText(
                scenario.scenarioName(), scenario.scenarioCode());
        if (!StringUtils.hasText(template)) {
            return "V" + versionNo + " "
                    + triggerName;
        }
        return template
                .replace("${versionNo}",
                        String.valueOf(versionNo))
                .replace("${scenarioName}",
                        triggerName)
                .replace("${triggerName}",
                        triggerName)
                .replace("${businessIntentName}",
                        defaultText(command.context()
                                .businessIntentName(), ""));
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

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private String id() {
        return UUID.randomUUID().toString()
                .replace("-", "");
    }
}
