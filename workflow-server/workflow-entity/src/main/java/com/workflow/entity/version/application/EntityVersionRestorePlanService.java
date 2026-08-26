package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.version.application.model.EntityVersionRestorePlan;
import com.workflow.entity.version.application.model.EntityVersionRestorePlan.Action;
import com.workflow.entity.version.application.model.EntityVersionRestorePlan.Blocker;
import com.workflow.entity.version.application.model.FrozenValue;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionDatasetMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionDatasetRowMapper;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityRecordVersionMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionDataset;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionDatasetRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 生成历史业务版本的只读恢复预演。
 *
 * <p>服务仅比较冻结快照与当前聚合，并汇总 CREATE/UPDATE/DELETE/LINK/UNLINK
 * 意图及权限、流程、发布版本阻断项。恢复执行链尚未具备跨层事务、流程补偿和并发
 * 冲突保证，因此计划始终包含 EXECUTION_DISABLED 阻断，且本类没有任何写方法。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityVersionRestorePlanService {

    private final EntityRecordVersionMapper versionMapper;
    private final EntityRecordVersionDatasetMapper datasetMapper;
    private final EntityRecordVersionDatasetRowMapper rowMapper;
    private final EntityDataDynamicService dataService;
    private final EntityActionCapabilityService capabilityService;
    private final EntityPublishedSnapshotService snapshotService;
    private final ObjectMapper objectMapper;

    /**
     * 预演把当前聚合恢复为指定历史版本。
     *
     * @return 只读计划；{@code executable} 固定为 false
     */
    @Transactional(readOnly = true)
    public EntityVersionRestorePlan plan(
            String entityCode,
            String recordId,
            Integer targetVersionNo) {
        EntityRecordVersion version = versionMapper.findVersion(
                entityCode, recordId, targetVersionNo);
        if (version == null) {
            throw new IllegalArgumentException(
                    "实体数据版本不存在: " + entityCode + "/"
                            + recordId + "/V" + targetVersionNo);
        }
        if (value(version.getSchemaVersion(), 1) < 2) {
            throw new IllegalArgumentException(
                    "旧V1版本缺少可靠关系数据集，不能生成整体恢复预演");
        }

        EntityDataDTO currentDto = dataService
                .findAccessibleIncludingDeletedById(
                        entityCode, recordId, null);
        Map<String, Object> currentRoot = objectMapper.convertValue(
                currentDto, new TypeReference<>() { });
        Map<String, Object> rootDocument = readMap(
                version.getSnapshotDocument());
        List<Action> actions = new ArrayList<>();
        List<Blocker> blockers = new ArrayList<>();
        Set<String> permissionChecks = new LinkedHashSet<>();
        Set<String> releaseChecks = new LinkedHashSet<>();

        checkRelease(
                "ROOT", entityCode, recordId,
                version.getEntityReleaseId(), blockers, releaseChecks);
        checkActiveProcess(
                "ROOT", entityCode, recordId, currentRoot, blockers);
        boolean targetDeleted = Boolean.TRUE.equals(
                rootDocument.get("deletedSnapshot"));
        if (targetDeleted) {
            addAction(actions, new Action(
                    "DELETE", "ROOT", entityCode, recordId,
                    null, List.of(), "删除当前根记录以匹配历史删除快照"));
            checkPermission(entityCode, EntityPermissionAction.DELETE,
                    "ROOT", recordId, blockers, permissionChecks);
        } else {
            List<String> changed = changedFields(
                    readValues(map(rootDocument.get("values"))), currentRoot);
            if (!changed.isEmpty()) {
                addAction(actions, new Action(
                        "UPDATE", "ROOT", entityCode, recordId,
                        null, changed, "恢复根记录字段"));
                checkPermission(entityCode, EntityPermissionAction.UPDATE,
                        "ROOT", recordId, blockers, permissionChecks);
            }
        }

        Map<String, List<CurrentNode>> currentByNode =
                new LinkedHashMap<>();
        currentByNode.put("ROOT", List.of(new CurrentNode(
                recordId, null, currentRoot)));
        List<EntityRecordVersionDataset> datasets =
                new ArrayList<>(datasetMapper.findByVersionId(version.getId()));
        datasets.sort(Comparator
                .comparingInt(this::datasetDepth)
                .thenComparing(EntityRecordVersionDataset::getNodeCode));
        for (EntityRecordVersionDataset dataset : datasets) {
            Map<String, Object> selector = readMap(
                    dataset.getSelectorDocument());
            String parentNodeCode = firstText(
                    text(selector.get("parentNodeCode")), "ROOT");
            String dataKey = text(selector.get("dataKey"));
            String relationType = text(selector.get("relationType"));
            List<CurrentNode> parents = currentByNode.get(parentNodeCode);
            if (parents == null || !StringUtils.hasText(dataKey)) {
                blockers.add(new Blocker(
                        "ENTITY_VERSION_RESTORE_PATH_INVALID",
                        dataset.getNodeCode(), dataset.getEntityCode(), null,
                        "历史数据集缺少可解析的冻结父路径"));
                currentByNode.put(dataset.getNodeCode(), List.of());
                continue;
            }
            Map<String, CurrentNode> currentRows = currentRows(
                    parents, dataKey, relationType,
                    dataset, blockers);
            currentByNode.put(dataset.getNodeCode(),
                    new ArrayList<>(currentRows.values()));
            Map<String, TargetRow> targetRows = targetRows(dataset);
            checkRelease(
                    dataset.getNodeCode(), dataset.getEntityCode(), null,
                    dataset.getEntityReleaseId(), blockers, releaseChecks);

            for (String childId : union(
                    currentRows.keySet(), targetRows.keySet())) {
                CurrentNode current = currentRows.get(childId);
                TargetRow target = targetRows.get(childId);
                if (current == null) {
                    String targetParent = firstText(
                            target.parentRecordId(), recordId);
                    addAction(actions, new Action(
                            "CREATE", dataset.getNodeCode(),
                            dataset.getEntityCode(), childId,
                            targetParent,
                            publicFieldCodes(target.values()),
                            "重建历史版本中的组成记录"));
                    addAction(actions, new Action(
                            "LINK", dataset.getNodeCode(),
                            dataset.getEntityCode(), childId,
                            targetParent, List.of(),
                            "把重建记录关联到历史父记录"));
                    checkPermission(dataset.getEntityCode(),
                            EntityPermissionAction.CREATE,
                            dataset.getNodeCode(), childId,
                            blockers, permissionChecks);
                    checkPermission(parentEntityCode(
                                    parentNodeCode, entityCode, datasets),
                            EntityPermissionAction.UPDATE,
                            parentNodeCode, targetParent,
                            blockers, permissionChecks);
                    continue;
                }
                if (target == null) {
                    addAction(actions, new Action(
                            "UNLINK", dataset.getNodeCode(),
                            dataset.getEntityCode(), childId,
                            current.parentRecordId(), List.of(),
                            "解除历史版本中不存在的组成关系"));
                    addAction(actions, new Action(
                            "DELETE", dataset.getNodeCode(),
                            dataset.getEntityCode(), childId,
                            current.parentRecordId(), List.of(),
                            "删除历史版本中不存在的组成记录"));
                    checkPermission(dataset.getEntityCode(),
                            EntityPermissionAction.DELETE,
                            dataset.getNodeCode(), childId,
                            blockers, permissionChecks);
                    checkActiveProcess(
                            dataset.getNodeCode(), dataset.getEntityCode(),
                            childId, current.record(), blockers);
                    continue;
                }
                if (!Objects.equals(current.parentRecordId(),
                        target.parentRecordId())) {
                    addAction(actions, new Action(
                            "UNLINK", dataset.getNodeCode(),
                            dataset.getEntityCode(), childId,
                            current.parentRecordId(), List.of(),
                            "解除当前父记录关系"));
                    addAction(actions, new Action(
                            "LINK", dataset.getNodeCode(),
                            dataset.getEntityCode(), childId,
                            target.parentRecordId(), List.of(),
                            "恢复历史父记录关系"));
                    checkPermission(dataset.getEntityCode(),
                            EntityPermissionAction.UPDATE,
                            dataset.getNodeCode(), childId,
                            blockers, permissionChecks);
                }
                List<String> changed = changedFields(
                        target.values(), current.record());
                if (!changed.isEmpty()) {
                    addAction(actions, new Action(
                            "UPDATE", dataset.getNodeCode(),
                            dataset.getEntityCode(), childId,
                            target.parentRecordId(), changed,
                            "恢复组成记录字段"));
                    checkPermission(dataset.getEntityCode(),
                            EntityPermissionAction.UPDATE,
                            dataset.getNodeCode(), childId,
                            blockers, permissionChecks);
                }
                checkActiveProcess(
                        dataset.getNodeCode(), dataset.getEntityCode(),
                        childId, current.record(), blockers);
            }
        }

        blockers.add(new Blocker(
                "ENTITY_VERSION_RESTORE_EXECUTION_DISABLED",
                "ROOT", entityCode, recordId,
                "当前仅支持恢复预演；跨层事务、流程补偿和并发冲突处理完成前不开放执行入口"));
        EntityVersionRestorePlan.Summary summary = summary(actions, blockers);
        return new EntityVersionRestorePlan(
                1, entityCode, recordId, targetVersionNo,
                false, summary, actions, blockers);
    }

    private Map<String, CurrentNode> currentRows(
            List<CurrentNode> parents,
            String dataKey,
            String relationType,
            EntityRecordVersionDataset dataset,
            List<Blocker> blockers) {
        Map<String, CurrentNode> result = new LinkedHashMap<>();
        for (CurrentNode parent : parents) {
            List<Map<String, Object>> rows = relationRows(
                    rowValue(parent.record(), dataKey), relationType);
            for (Map<String, Object> row : rows) {
                String childId = text(row.get("id"));
                if (!StringUtils.hasText(childId)) {
                    blockers.add(new Blocker(
                            "ENTITY_VERSION_RESTORE_ROW_ID_MISSING",
                            dataset.getNodeCode(), dataset.getEntityCode(),
                            null, "当前组成记录缺少稳定ID"));
                    continue;
                }
                if (result.putIfAbsent(childId, new CurrentNode(
                        childId, parent.recordId(), row)) != null) {
                    blockers.add(new Blocker(
                            "ENTITY_VERSION_RESTORE_DUPLICATE_OWNERSHIP",
                            dataset.getNodeCode(), dataset.getEntityCode(),
                            childId, "当前组成记录同时归属多个父记录"));
                }
            }
        }
        return result;
    }

    private Map<String, TargetRow> targetRows(
            EntityRecordVersionDataset dataset) {
        Map<String, TargetRow> result = new LinkedHashMap<>();
        for (EntityRecordVersionDatasetRow row
                : rowMapper.findByDatasetId(dataset.getId())) {
            Map<String, FrozenValue> values = readValuesDocument(
                    row.getValuesDocument());
            FrozenValue parent = values.remove(
                    EntityRecordSnapshotService.INTERNAL_PARENT_RECORD_ID);
            result.put(row.getRecordId(), new TargetRow(
                    row.getRecordId(), text(raw(parent)), values));
        }
        return result;
    }

    private List<String> changedFields(
            Map<String, FrozenValue> target,
            Map<String, Object> current) {
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, FrozenValue> entry : target.entrySet()) {
            if (entry.getKey().startsWith("__scope")) {
                continue;
            }
            Object currentValue = rowValue(current, entry.getKey());
            if (!Objects.equals(raw(entry.getValue()), currentValue)) {
                result.add(entry.getKey());
            }
        }
        return result;
    }

    private void checkPermission(
            String entityCode,
            EntityPermissionAction action,
            String nodeCode,
            String recordId,
            List<Blocker> blockers,
            Set<String> checked) {
        if (!StringUtils.hasText(entityCode)
                || !checked.add(entityCode + ":" + action.name())) {
            return;
        }
        try {
            capabilityService.requireStandardPermission(entityCode, action);
        } catch (ForbiddenException exception) {
            blockers.add(new Blocker(
                    "ENTITY_VERSION_RESTORE_PERMISSION_DENIED",
                    nodeCode, entityCode, recordId,
                    "缺少“" + action.getLabel() + "”权限"));
        }
    }

    private void checkRelease(
            String nodeCode,
            String entityCode,
            String recordId,
            String expectedReleaseId,
            List<Blocker> blockers,
            Set<String> checked) {
        if (!StringUtils.hasText(entityCode)
                || !checked.add(entityCode + ":" + expectedReleaseId)) {
            return;
        }
        EntityPublishedSnapshot current = snapshotService
                .findLatestByEntityCode(entityCode);
        if (current == null || !Objects.equals(
                expectedReleaseId, current.getHistoryId())) {
            blockers.add(new Blocker(
                    "ENTITY_VERSION_RESTORE_RELEASE_CHANGED",
                    nodeCode, entityCode, recordId,
                    "实体发布版本已变化，历史字段和关系不能直接执行恢复"));
        }
    }

    private void checkActiveProcess(
            String nodeCode,
            String entityCode,
            String recordId,
            Map<String, Object> record,
            List<Blocker> blockers) {
        if (StringUtils.hasText(text(rowValue(record, "processInstanceId")))
                || StringUtils.hasText(text(rowValue(
                        record, "currentTaskId")))) {
            blockers.add(new Blocker(
                    "ENTITY_VERSION_RESTORE_PROCESS_ACTIVE",
                    nodeCode, entityCode, recordId,
                    "记录正在流程中，恢复必须先由流程协调服务确认或补偿"));
        }
    }

    private int datasetDepth(EntityRecordVersionDataset dataset) {
        Object depth = readMap(dataset.getSelectorDocument()).get("depth");
        return depth instanceof Number number ? number.intValue() : 1;
    }

    private String parentEntityCode(
            String parentNodeCode,
            String rootEntityCode,
            List<EntityRecordVersionDataset> datasets) {
        if ("ROOT".equals(parentNodeCode)) {
            return rootEntityCode;
        }
        return datasets.stream()
                .filter(item -> Objects.equals(
                        item.getNodeCode(), parentNodeCode))
                .map(EntityRecordVersionDataset::getEntityCode)
                .findFirst().orElse(null);
    }

    private EntityVersionRestorePlan.Summary summary(
            List<Action> actions,
            List<Blocker> blockers) {
        return new EntityVersionRestorePlan.Summary(
                count(actions, "CREATE"),
                count(actions, "UPDATE"),
                count(actions, "DELETE"),
                count(actions, "LINK"),
                count(actions, "UNLINK"),
                blockers.size());
    }

    private int count(List<Action> actions, String operation) {
        return (int) actions.stream()
                .filter(item -> operation.equals(item.operation())).count();
    }

    private void addAction(List<Action> actions, Action action) {
        actions.add(action);
    }

    private List<String> publicFieldCodes(
            Map<String, FrozenValue> values) {
        return values.keySet().stream()
                .filter(code -> !code.startsWith("__scope"))
                .toList();
    }

    private Set<String> union(
            Collection<String> left,
            Collection<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.addAll(right);
        return result;
    }

    private List<Map<String, Object>> relationRows(
            Object value,
            String relationType) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof Map<?, ?> map) {
            result.add(stringMap(map));
        } else if (value instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item instanceof Map<?, ?> map) {
                    result.add(stringMap(map));
                }
            }
        }
        if ("ONE_TO_ONE".equals(relationType) && result.size() > 1) {
            throw new IllegalStateException(
                    "当前一对一组成关系存在多条记录，拒绝生成恢复预演");
        }
        return result;
    }

    private Object rowValue(Map<String, Object> record, String fieldCode) {
        if (record == null || !StringUtils.hasText(fieldCode)) {
            return null;
        }
        Object direct = path(record, fieldCode);
        if (direct != null || record.containsKey(fieldCode)) {
            return direct;
        }
        return path(map(record.get("data")), fieldCode);
    }

    private Object path(Map<String, Object> source, String fieldCode) {
        Object current = source;
        for (String part : fieldCode.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private Map<String, FrozenValue> readValues(
            Map<String, Object> values) {
        Map<String, FrozenValue> result = new LinkedHashMap<>();
        values.forEach((code, value) -> result.put(code,
                objectMapper.convertValue(value, FrozenValue.class)));
        return result;
    }

    private Map<String, FrozenValue> readValuesDocument(String document) {
        try {
            return objectMapper.readValue(document, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("版本数据集行解析失败", exception);
        }
    }

    private Map<String, Object> readMap(String document) {
        if (!StringUtils.hasText(document)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(document, new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实体版本快照解析失败", exception);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
    }

    private Map<String, Object> stringMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private Object raw(FrozenValue value) {
        return value == null ? null : value.rawValue();
    }

    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    private record CurrentNode(
            String recordId,
            String parentRecordId,
            Map<String, Object> record) {
    }

    private record TargetRow(
            String recordId,
            String parentRecordId,
            Map<String, FrozenValue> values) {
    }
}
