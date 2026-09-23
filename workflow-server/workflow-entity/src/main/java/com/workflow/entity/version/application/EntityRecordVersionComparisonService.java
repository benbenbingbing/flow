package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.FrozenValue;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2.ComparisonSummary;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2.FieldComparison;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2.FormSectionComparison;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2.NodeComparison;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2.RowChangeCounts;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2.RowComparison;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2.RowComparisonPage;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2.SnapshotRow;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2.SnapshotRowPage;
import com.workflow.entity.version.application.model.RecordVersionComparisonV2.VersionSide;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * V1/V2 兼容的类型化版本比较器。
 *
 * <p>本服务只读取快照自身冻结的中文元数据，禁止回查当前实体或字段名称。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityRecordVersionComparisonService {

    private final EntityRecordVersionMapper versionMapper;
    private final EntityRecordVersionDatasetMapper datasetMapper;
    private final EntityRecordVersionDatasetRowMapper rowMapper;
    private final ObjectMapper objectMapper;

    /**
     * 比较实体记录版本比较；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param fromVersionNo 起始版本号，作为 {@code requireVersion} 的输入影响后续处理
     * @param toVersionNo 截止版本号，作为 {@code requireVersion} 的输入影响后续处理
     * @return 比较后的实体记录版本比较结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public RecordVersionComparisonV2 compare(
            String entityCode,
            String recordId,
            Integer fromVersionNo,
            Integer toVersionNo) {
        EntityRecordVersion from = requireVersion(
                entityCode, recordId, fromVersionNo);
        EntityRecordVersion to = requireVersion(
                entityCode, recordId, toVersionNo);
        Map<String, Object> fromDocument = readMap(from.getSnapshotDocument());
        Map<String, Object> toDocument = readMap(to.getSnapshotDocument());
        int fromSchema = schemaVersion(from, fromDocument);
        int toSchema = schemaVersion(to, toDocument);
        String compatibility = fromSchema >= 2 && toSchema >= 2
                ? "FULL" : fromSchema == 1 && toSchema == 1
                        ? "LEGACY" : "PARTIAL";
        List<String> warnings = new ArrayList<>();
        if (!"FULL".equals(compatibility)) {
            warnings.add(fromSchema == toSchema
                    ? "旧V1版本仅支持根字段比较，关系数组不能提供可靠行级差异"
                    : "V1与V2仅比较可靠的根字段交集，关系行级差异不可用");
        }
        NodeSnapshot fromRoot = rootSnapshot(fromDocument, fromSchema);
        NodeSnapshot toRoot = rootSnapshot(toDocument, toSchema);
        EntityVersionConfiguration.DiffPolicy diffPolicy =
                diffPolicy(toDocument);
        Set<String> ignored = new LinkedHashSet<>(
                safe(diffPolicy.getIgnoredFieldCodes()));
        FieldDiff rootDiff = compareFields(fromRoot, toRoot, ignored);
        List<NodeComparison> nodes = new ArrayList<>();
        nodes.add(new NodeComparison(
                "ROOT", "ROOT", null, null,
                firstText(toRoot.entityName(), fromRoot.entityName(), "根实体"),
                fromRoot.entityName(), toRoot.entityName(),
                "FULL".equals(compatibility) ? "COMPARABLE" : compatibility,
                rootDiff.sections(), null));

        Totals totals = new Totals();
        totals.add(rootDiff);
        if (!Objects.equals(fromRoot.entityName(), toRoot.entityName())) {
            totals.schema++;
        }
        boolean scopeChanged = !Objects.equals(
                from.getScopeHash(), to.getScopeHash());
        if (fromSchema >= 2 && toSchema >= 2) {
            Map<String, EntityRecordVersionDataset> fromDatasets =
                    indexDatasets(datasetMapper.findByVersionId(from.getId()));
            Map<String, EntityRecordVersionDataset> toDatasets =
                    indexDatasets(datasetMapper.findByVersionId(to.getId()));
            Set<String> nodeCodes = union(
                    fromDatasets.keySet(), toDatasets.keySet());
            for (String nodeCode : nodeCodes) {
                EntityRecordVersionDataset left = fromDatasets.get(nodeCode);
                EntityRecordVersionDataset right = toDatasets.get(nodeCode);
                DatasetDiff diff = datasetDiff(left, right, ignored);
                totals.add(diff);
                scopeChanged = scopeChanged || diff.scopeChanged();
                nodes.add(new NodeComparison(
                        nodeCode,
                        "RELATION",
                        left == null ? null : left.getRelationName(),
                        right == null ? null : right.getRelationName(),
                        firstText(
                                right == null ? null : right.getRelationName(),
                                left == null ? null : left.getRelationName(),
                                nodeCode),
                        left == null ? null : left.getEntityName(),
                        right == null ? null : right.getEntityName(),
                        diff.comparability(),
                        List.of(),
                        diff.counts()));
            }
        }
        boolean hasChanges = totals.data > 0
                || totals.display > 0
                || totals.schema > 0
                || totals.added > 0
                || totals.removed > 0
                || totals.modified > 0
                || totals.moved > 0
                || scopeChanged;
        return new RecordVersionComparisonV2(
                2,
                compatibility,
                side(from, fromDocument, fromSchema),
                side(to, toDocument, toSchema),
                new ComparisonSummary(
                        totals.data,
                        totals.display,
                        totals.schema,
                        totals.added,
                        totals.removed,
                        totals.modified,
                        totals.moved,
                        scopeChanged,
                        hasChanges),
                diffPolicy,
                nodes,
                warnings);
    }

    /**
     * 比较行；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param fromVersionNo 起始版本号，作为 {@code requireVersion} 的输入影响后续处理
     * @param toVersionNo 截止版本号，作为 {@code requireVersion} 的输入影响后续处理
     * @param nodeCode 节点编码，后续用于比较行时定位或关联目标
     * @param requestedPageNum 请求页码，后续归一化并换算为数据库查询偏移
     * @param requestedPageSize 请求页大小，后续限制单次查询和返回数量
     * @param changedOnly 已变更仅，供本方法比较行时使用
     * @return 比较后的行结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Transactional(readOnly = true)
    public RowComparisonPage compareRows(
            String entityCode,
            String recordId,
            Integer fromVersionNo,
            Integer toVersionNo,
            String nodeCode,
            long requestedPageNum,
            long requestedPageSize,
            boolean changedOnly) {
        EntityRecordVersion from = requireVersion(
                entityCode, recordId, fromVersionNo);
        EntityRecordVersion to = requireVersion(
                entityCode, recordId, toVersionNo);
        if (value(from.getSchemaVersion(), 1) < 2
                || value(to.getSchemaVersion(), 1) < 2) {
            throw new IllegalArgumentException(
                    "V1快照不支持关系行级分页比较");
        }
        EntityRecordVersionDataset left = datasetMapper.findByNodeCode(
                from.getId(), nodeCode);
        EntityRecordVersionDataset right = datasetMapper.findByNodeCode(
                to.getId(), nodeCode);
        Set<String> ignored = ignoredFields(
                readMap(to.getSnapshotDocument()));
        DatasetDiff diff = datasetDiff(left, right, ignored);
        if (!"COMPARABLE".equals(diff.comparability())) {
            return new RowComparisonPage(
                    nodeCode,
                    firstText(right == null ? null : right.getRelationName(),
                            left == null ? null : left.getRelationName()),
                    List.of(), 0, 1,
                    normalizedPageSize(requestedPageSize), diff.counts());
        }
        List<RowComparison> changes = rowComparisons(
                left, right, ignored);
        if (changedOnly) {
            changes = changes.stream()
                    .filter(item -> !"UNCHANGED".equals(item.changeType())
                            || item.moved())
                    .toList();
        }
        long pageNum = Math.max(1, requestedPageNum);
        long pageSize = normalizedPageSize(requestedPageSize);
        int start = (int) Math.min(changes.size(), (pageNum - 1) * pageSize);
        int end = (int) Math.min(changes.size(), start + pageSize);
        return new RowComparisonPage(
                nodeCode,
                firstText(right.getRelationName(), left.getRelationName()),
                changes.subList(start, end),
                changes.size(), pageNum, pageSize, diff.counts());
    }

    /**
     * 处理快照行，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param versionNo 版本号，作为 {@code requireVersion} 的输入影响后续处理
     * @param nodeCode 节点编码，后续用于处理快照行时定位或关联目标
     * @param requestedPageNum 请求页码，后续归一化并换算为数据库查询偏移
     * @param requestedPageSize 请求页大小，后续限制单次查询和返回数量
     * @return 处理后的快照行结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    @Transactional(readOnly = true)
    public SnapshotRowPage snapshotRows(
            String entityCode,
            String recordId,
            Integer versionNo,
            String nodeCode,
            long requestedPageNum,
            long requestedPageSize) {
        EntityRecordVersion version = requireVersion(
                entityCode, recordId, versionNo);
        if (value(version.getSchemaVersion(), 1) < 2) {
            throw new IllegalArgumentException("V1快照没有独立关系数据集");
        }
        EntityRecordVersionDataset dataset = datasetMapper.findByNodeCode(
                version.getId(), nodeCode);
        if (dataset == null) {
            throw new IllegalArgumentException("版本关系数据集不存在: " + nodeCode);
        }
        long pageNum = Math.max(1, requestedPageNum);
        long pageSize = normalizedPageSize(requestedPageSize);
        long total = rowMapper.countByDatasetId(dataset.getId());
        List<SnapshotRow> rows = rowMapper.findPage(
                        dataset.getId(), (pageNum - 1) * pageSize, pageSize)
                .stream()
                .map(item -> new SnapshotRow(
                        item.getRecordId(), item.getRecordTitle(),
                        item.getRowOrder(), publicValues(
                                readValues(item.getValuesDocument()))))
                .toList();
        return new SnapshotRowPage(
                nodeCode,
                dataset.getRelationCode(),
                dataset.getRelationName(),
                dataset.getEntityCode(),
                dataset.getEntityName(),
                readMap(dataset.getPresentationDocument()),
                rows,
                total,
                pageNum,
                pageSize);
    }

    /**
     * 处理数据集差异，并将结果传给后续步骤。
     *
     * @param left 左侧，作为 {@code rowComparisons} 的输入影响后续处理
     * @param right 右侧，作为 {@code Boolean.TRUE.equals} 的输入影响后续处理
     * @param ignored {@code ignored}，作为 {@code rowComparisons} 的输入影响后续处理
     * @return 处理后的数据集差异结果，供调用方继续处理
     */
    private DatasetDiff datasetDiff(
            EntityRecordVersionDataset left,
            EntityRecordVersionDataset right,
            Set<String> ignored) {
        if (left == null || right == null
                || !Objects.equals(left.getScopeHash(), right.getScopeHash())
                || !Boolean.TRUE.equals(left.getComplete())
                || !Boolean.TRUE.equals(right.getComplete())) {
            return new DatasetDiff(
                    "SCOPE_CHANGED",
                    new RowChangeCounts(0, 0, 0, 0, 0, 0),
                    true, 0, 0, 1);
        }
        List<RowComparison> rows = rowComparisons(
                left, right, ignored);
        int added = count(rows, "ADDED");
        int removed = count(rows, "REMOVED");
        int modified = count(rows, "MODIFIED");
        int moved = (int) rows.stream()
                .filter(RowComparison::moved)
                .count();
        int unchanged = count(rows, "UNCHANGED");
        int data = 0;
        int display = 0;
        int schema = 0;
        for (RowComparison row : rows) {
            FieldDiff fields = fieldTotals(row.formSections());
            data += fields.dataChanges();
            display += fields.displayChanges();
            schema += fields.schemaChanges();
        }
        if (!Objects.equals(left.getRelationName(), right.getRelationName())) {
            schema++;
        }
        if (!Objects.equals(left.getEntityName(), right.getEntityName())) {
            schema++;
        }
        return new DatasetDiff(
                "COMPARABLE",
                new RowChangeCounts(added, removed, modified, moved,
                        unchanged, rows.size()),
                false, data, display, schema);
    }

    /**
     * 整理行{@code comparisons}数据，供调用方遍历或继续处理。
     *
     * @param left 左侧，作为 {@code indexRows} 的输入影响后续处理
     * @param right 右侧，作为 {@code indexRows} 的输入影响后续处理
     * @param ignored {@code ignored}，供本方法处理行{@code comparisons}时使用
     * @return 行比较集合，供调用方遍历或展示
     */
    private List<RowComparison> rowComparisons(
            EntityRecordVersionDataset left,
            EntityRecordVersionDataset right,
            Set<String> ignored) {
        Map<String, EntityRecordVersionDatasetRow> oldRows = indexRows(
                rowMapper.findByDatasetId(left.getId()));
        Map<String, EntityRecordVersionDatasetRow> newRows = indexRows(
                rowMapper.findByDatasetId(right.getId()));
        NodeSnapshot oldPresentation = datasetSnapshot(left, Map.of());
        NodeSnapshot newPresentation = datasetSnapshot(right, Map.of());
        boolean trackOrder = booleanValue(
                readMap(right.getSelectorDocument()).get("trackOrder"));
        List<RowComparison> result = new ArrayList<>();
        for (String id : union(oldRows.keySet(), newRows.keySet())) {
            EntityRecordVersionDatasetRow oldRow = oldRows.get(id);
            EntityRecordVersionDatasetRow newRow = newRows.get(id);
            Map<String, FrozenValue> oldValues = oldRow == null
                    ? Map.of() : readValues(oldRow.getValuesDocument());
            Map<String, FrozenValue> newValues = newRow == null
                    ? Map.of() : readValues(newRow.getValuesDocument());
            FieldDiff fields = compareFields(
                    oldPresentation.withValues(oldValues),
                    newPresentation.withValues(newValues),
                    ignored);
            Object oldParent = rawValue(
                    oldValues.get(EntityRecordSnapshotService
                            .INTERNAL_PARENT_RECORD_ID));
            Object newParent = rawValue(
                    newValues.get(EntityRecordSnapshotService
                            .INTERNAL_PARENT_RECORD_ID));
            // 旧一层 V2 行没有父身份元数据；首次引入该内部字段不能把所有历史行误报为换父。
            boolean bothTrackParent = oldValues.containsKey(
                    EntityRecordSnapshotService.INTERNAL_PARENT_RECORD_ID)
                    && newValues.containsKey(
                            EntityRecordSnapshotService
                                    .INTERNAL_PARENT_RECORD_ID);
            if (bothTrackParent
                    && !Objects.equals(oldParent, newParent)
                    && (oldParent != null || newParent != null)) {
                fields = withParentChange(fields, oldParent, newParent);
            }
            boolean moved = oldRow != null
                    && newRow != null
                    && trackOrder
                    && !Objects.equals(
                            oldRow.getRowOrder(), newRow.getRowOrder());
            String changeType;
            if (oldRow == null) {
                changeType = "ADDED";
            } else if (newRow == null) {
                changeType = "REMOVED";
            } else if (fields.dataChanges() > 0) {
                changeType = "MODIFIED";
            } else if (fields.displayChanges() > 0
                    || fields.schemaChanges() > 0) {
                changeType = "MODIFIED";
            } else if (moved) {
                changeType = "MOVED";
            } else {
                changeType = "UNCHANGED";
            }
            result.add(new RowComparison(
                    id,
                    oldRow == null ? null : oldRow.getRecordTitle(),
                    newRow == null ? null : newRow.getRecordTitle(),
                    changeType,
                    moved,
                    oldRow == null ? null : oldRow.getRowOrder(),
                    newRow == null ? null : newRow.getRowOrder(),
                    fields.sections()));
        }
        return result;
    }

    /**
     * 比较字段；结果供调用方的后续步骤使用。
     *
     * @param left 左侧，供本方法比较字段时使用
     * @param right 右侧，供本方法比较字段时使用
     * @param ignored {@code ignored}，供本方法比较字段时使用
     * @return 比较后的字段结果，供调用方继续处理
     */
    private FieldDiff compareFields(
            NodeSnapshot left,
            NodeSnapshot right,
            Set<String> ignored) {
        List<FieldComparison> comparisons = new ArrayList<>();
        int data = 0;
        int display = 0;
        int schema = 0;
        for (String code : orderedFieldCodes(left.fields(), right.fields())) {
            if (ignored.contains(code)) {
                continue;
            }
            EntityVersionConfiguration.FieldPresentation oldField =
                    left.fields().get(code);
            EntityVersionConfiguration.FieldPresentation newField =
                    right.fields().get(code);
            FrozenValue oldValue = left.values().get(code);
            FrozenValue newValue = right.values().get(code);
            List<String> schemaChanges = new ArrayList<>();
            String changeType;
            if (oldField == null) {
                schemaChanges.add("FIELD_ADDED");
                changeType = "NOT_COMPARABLE";
            } else if (newField == null) {
                schemaChanges.add("FIELD_REMOVED");
                changeType = "NOT_COMPARABLE";
            } else if (!Objects.equals(
                    oldField.getFieldType(), newField.getFieldType())) {
                schemaChanges.add("TYPE_CHANGED");
                changeType = "NOT_COMPARABLE";
            } else {
                Object oldRaw = oldValue == null ? null : oldValue.rawValue();
                Object newRaw = newValue == null ? null : newValue.rawValue();
                if (Objects.equals(oldRaw, newRaw)) {
                    changeType = "UNCHANGED";
                } else if (oldRaw == null) {
                    changeType = "ADDED";
                } else if (newRaw == null) {
                    changeType = "REMOVED";
                } else {
                    changeType = "MODIFIED";
                }
            }
            String oldName = fieldName(oldField);
            String newName = fieldName(newField);
            if (oldField != null && newField != null
                    && !Objects.equals(oldName, newName)) {
                schemaChanges.add("LABEL_CHANGED");
            }
            boolean displayChanged = oldField != null && newField != null
                    && Objects.equals(
                            oldValue == null ? null : oldValue.rawValue(),
                            newValue == null ? null : newValue.rawValue())
                    && !Objects.equals(
                            oldValue == null ? null : oldValue.displayText(),
                            newValue == null ? null : newValue.displayText());
            if (Set.of("ADDED", "REMOVED", "MODIFIED")
                    .contains(changeType)) {
                data++;
            }
            if (displayChanged) {
                display++;
            }
            schema += schemaChanges.size();
            comparisons.add(new FieldComparison(
                    code,
                    oldName,
                    newName,
                    firstText(newName, oldName, code),
                    oldField == null ? null : oldField.getFieldType(),
                    newField == null ? null : newField.getFieldType(),
                    oldValue,
                    newValue,
                    changeType,
                    displayChanged,
                    schemaChanges));
        }
        Map<String, List<FieldComparison>> sections = new LinkedHashMap<>();
        Map<String, String> sectionNames = new LinkedHashMap<>();
        for (FieldComparison comparison : comparisons) {
            EntityVersionConfiguration.FieldPresentation field =
                    right.fields().getOrDefault(
                            comparison.fieldCode(),
                            left.fields().get(comparison.fieldCode()));
            String sectionCode = field == null
                    || !StringUtils.hasText(field.getSectionCode())
                    ? "BUSINESS" : field.getSectionCode();
            String sectionName = field == null
                    || !StringUtils.hasText(field.getSectionName())
                    ? "业务字段" : field.getSectionName();
            sectionNames.putIfAbsent(sectionCode, sectionName);
            sections.computeIfAbsent(sectionCode, ignoredKey -> new ArrayList<>())
                    .add(comparison);
        }
        List<FormSectionComparison> grouped = new ArrayList<>();
        for (Map.Entry<String, List<FieldComparison>> entry : sections.entrySet()) {
            grouped.add(new FormSectionComparison(
                    entry.getKey(), sectionNames.get(entry.getKey()),
                    entry.getValue()));
        }
        return new FieldDiff(grouped, data, display, schema);
    }

    /**
     * 处理根快照，并将结果传给后续步骤。
     *
     * @param document 文档，作为 {@code NodeSnapshot} 的输入影响后续处理
     * @param schemaVersion 结构版本，供本方法处理根快照时使用
     * @return 处理后的根快照结果，供调用方继续处理
     */
    private NodeSnapshot rootSnapshot(
            Map<String, Object> document,
            int schemaVersion) {
        if (schemaVersion >= 2) {
            return new NodeSnapshot(
                    entityName(document),
                    presentationFields(map(document.get("presentation"))),
                    frozenValues(map(document.get("values"))));
        }
        Map<String, EntityVersionConfiguration.FieldPresentation> fields =
                new LinkedHashMap<>();
        Map<String, FrozenValue> values = new LinkedHashMap<>();
        for (Map<String, Object> item : mapList(document.get("fields"))) {
            String code = text(item.get("fieldCode"));
            if (!StringUtils.hasText(code)) {
                continue;
            }
            EntityVersionConfiguration.FieldPresentation field =
                    new EntityVersionConfiguration.FieldPresentation();
            field.setFieldCode(code);
            field.setFieldName(text(item.get("fieldName")));
            field.setFieldLabel(text(item.get("fieldName")));
            field.setFieldType(text(item.get("fieldType")));
            field.setSectionCode(defaultText(text(item.get("group")), "BUSINESS"));
            field.setSectionName(sectionName(field.getSectionCode()));
            fields.put(code, field);
            Object raw = item.get("value");
            Object display = item.get("displayValue");
            values.put(code, new FrozenValue(
                    raw,
                    display == null ? null : String.valueOf(display),
                    List.of(), raw == null ? "EMPTY" : "PRESENT",
                    "RESOLVED"));
        }
        return new NodeSnapshot(entityName(document), fields, values);
    }

    /**
     * 处理数据集快照，并将结果传给后续步骤。
     *
     * @param dataset 数据集，作为 {@code NodeSnapshot} 的输入影响后续处理
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的数据集快照结果，供调用方继续处理
     */
    private NodeSnapshot datasetSnapshot(
            EntityRecordVersionDataset dataset,
            Map<String, FrozenValue> values) {
        return new NodeSnapshot(
                dataset.getEntityName(),
                presentationFields(readMap(dataset.getPresentationDocument())),
                values);
    }

    /**
     * 整理展示字段数据，供调用方遍历或继续处理。
     *
     * @param presentation 展示，供本方法处理展示字段时使用
     * @return 展示字段键值结果，供调用方继续处理
     */
    private Map<String, EntityVersionConfiguration.FieldPresentation>
            presentationFields(Map<String, Object> presentation) {
        Map<String, EntityVersionConfiguration.FieldPresentation> result =
                new LinkedHashMap<>();
        for (Object value : list(presentation.get("fields"))) {
            EntityVersionConfiguration.FieldPresentation field =
                    objectMapper.convertValue(value,
                            EntityVersionConfiguration.FieldPresentation.class);
            if (StringUtils.hasText(field.getFieldCode())) {
                result.put(field.getFieldCode(), field);
            }
        }
        return result;
    }

    /**
     * 整理{@code frozen}值集合数据，供调用方遍历或继续处理。
     *
     * @param raw 待处理{@code frozen}值集合的原始输入，结果供调用方继续使用
     * @return {@code frozen}值集合键值结果，供调用方继续处理
     */
    private Map<String, FrozenValue> frozenValues(Map<String, Object> raw) {
        Map<String, FrozenValue> result = new LinkedHashMap<>();
        raw.forEach((code, value) -> result.put(code,
                objectMapper.convertValue(value, FrozenValue.class)));
        return result;
    }

    /**
     * 读取值集合；查询结果供调用方展示或继续处理。
     *
     * @param document 文档，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 值集合键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private Map<String, FrozenValue> readValues(String document) {
        try {
            return objectMapper.readValue(document,
                    new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("版本数据集行解析失败", exception);
        }
    }

    /**
     * 内部图身份用于比较和恢复预演，历史详情不得把它当作业务字段返回。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 公开值集合键值结果，供调用方继续处理
     */
    private Map<String, FrozenValue> publicValues(
            Map<String, FrozenValue> values) {
        Map<String, FrozenValue> result = new LinkedHashMap<>(values);
        result.keySet().removeIf(code -> code != null
                && code.startsWith("__scope"));
        return result;
    }

    /**
     * 子记录换父是关系图变化，即使业务字段完全相同也必须显示为修改。
     *
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param oldParent 旧父级，作为 {@code internalValue} 的输入影响后续处理
     * @param newParent 新父级，作为 {@code internalValue} 的输入影响后续处理
     * @return 处理后的父级变更结果，供调用方继续处理
     */
    private FieldDiff withParentChange(
            FieldDiff fields,
            Object oldParent,
            Object newParent) {
        List<FormSectionComparison> sections =
                new ArrayList<>(fields.sections());
        FrozenValue oldValue = internalValue(oldParent);
        FrozenValue newValue = internalValue(newParent);
        FieldComparison comparison = new FieldComparison(
                EntityRecordSnapshotService.INTERNAL_PARENT_RECORD_ID,
                "所属父记录", "所属父记录", "所属父记录",
                "REFERENCE", "REFERENCE",
                oldValue, newValue,
                oldParent == null ? "ADDED"
                        : newParent == null ? "REMOVED" : "MODIFIED",
                false, List.of());
        sections.add(new FormSectionComparison(
                "RELATION_CONTEXT", "关系归属", List.of(comparison)));
        return new FieldDiff(
                sections,
                fields.dataChanges() + 1,
                fields.displayChanges(),
                fields.schemaChanges());
    }

    /**
     * 处理内部值，并将结果传给后续步骤。
     *
     * @param value 待处理内部值的原始输入，结果供调用方继续使用
     * @return 处理后的内部值结果，供调用方继续处理
     */
    private FrozenValue internalValue(Object value) {
        return value == null ? null : new FrozenValue(
                value, String.valueOf(value), List.of(),
                "INTERNAL", "RESOLVED");
    }

    /**
     * 处理原始值，并将结果传给后续步骤。
     *
     * @param value 待处理原始值的原始输入，结果供调用方继续使用
     * @return 处理后的原始值结果，供调用方继续处理
     */
    private Object rawValue(FrozenValue value) {
        return value == null ? null : value.rawValue();
    }

    /**
     * 处理侧，并将结果传给后续步骤。
     *
     * @param version 版本，作为 {@code VersionSide} 的输入影响后续处理
     * @param document 文档，供本方法处理侧时使用
     * @param schemaVersion 结构版本，供本方法处理侧时使用
     * @return 处理后的侧结果，供调用方继续处理
     */
    private VersionSide side(
            EntityRecordVersion version,
            Map<String, Object> document,
            int schemaVersion) {
        return new VersionSide(
                version.getVersionNo(),
                version.getVersionTitle(),
                version.getScenarioCode(),
                version.getScenarioName(),
                version.getEntityCode(),
                entityName(document),
                schemaVersion,
                version.getScopeHash(),
                version.getCreateTime());
    }

    /**
     * 生成实体名称文本，供后续匹配或展示。
     *
     * @param document 文档，作为 {@code text} 的输入影响后续处理
     * @return 处理后的实体名称文本，供调用方比较或展示
     */
    private String entityName(Map<String, Object> document) {
        return text(map(document.get("entity")).get("entityName"));
    }

    /**
     * 整理{@code ignored}字段数据，供调用方遍历或继续处理。
     *
     * @param document 文档，作为 {@code list} 的输入影响后续处理
     * @return 实体记录版本比较集合，供调用方遍历或展示
     */
    private Set<String> ignoredFields(Map<String, Object> document) {
        return list(map(document.get("diffPolicy"))
                .get("ignoredFieldCodes")).stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.toSet());
    }

    /**
     * 处理差异策略，并将结果传给后续步骤。
     *
     * @param document 文档，供本方法处理差异策略时使用
     * @return 处理后的差异策略结果，供调用方继续处理
     */
    private EntityVersionConfiguration.DiffPolicy diffPolicy(
            Map<String, Object> document) {
        Object value = document.get("diffPolicy");
        if (!(value instanceof Map<?, ?>)) {
            return new EntityVersionConfiguration.DiffPolicy();
        }
        EntityVersionConfiguration.DiffPolicy result =
                objectMapper.convertValue(
                        value, EntityVersionConfiguration.DiffPolicy.class);
        if (result.getIgnoredFieldCodes() == null) {
            result.setIgnoredFieldCodes(new ArrayList<>());
        }
        return result;
    }

    /**
     * 整理索引{@code datasets}数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 索引{@code datasets}键值结果，供调用方继续处理
     */
    private Map<String, EntityRecordVersionDataset> indexDatasets(
            List<EntityRecordVersionDataset> values) {
        Map<String, EntityRecordVersionDataset> result = new LinkedHashMap<>();
        for (EntityRecordVersionDataset value : values) {
            result.put(value.getNodeCode(), value);
        }
        return result;
    }

    /**
     * 整理索引行数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 索引行键值结果，供调用方继续处理
     */
    private Map<String, EntityRecordVersionDatasetRow> indexRows(
            List<EntityRecordVersionDatasetRow> values) {
        Map<String, EntityRecordVersionDatasetRow> result = new LinkedHashMap<>();
        for (EntityRecordVersionDatasetRow value : values) {
            result.put(value.getRecordId(), value);
        }
        return result;
    }

    /**
     * 校验并获取版本；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param versionNo 版本号，作为 {@code versionMapper.findVersion} 的输入影响后续处理
     * @return 校验并获取后的版本结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityRecordVersion requireVersion(
            String entityCode, String recordId, Integer versionNo) {
        EntityRecordVersion value = versionMapper.findVersion(
                entityCode, recordId, versionNo);
        if (value == null) {
            throw new IllegalArgumentException(
                    "实体数据版本不存在: " + entityCode + "/"
                            + recordId + "/V" + versionNo);
        }
        return value;
    }

    /**
     * 处理结构版本，并将结果传给后续步骤。
     *
     * @param version 版本，供本方法处理结构版本时使用
     * @param document 文档，供本方法处理结构版本时使用
     * @return 处理后的结构版本结果，供调用方继续处理
     */
    private int schemaVersion(
            EntityRecordVersion version,
            Map<String, Object> document) {
        if (version.getSchemaVersion() != null) {
            return version.getSchemaVersion();
        }
        Object value = document.get("schemaVersion");
        return value instanceof Number number ? number.intValue() : 1;
    }

    /**
     * 统计实体记录版本比较；结果供后续判断或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param type 类型标识，决定后续实体记录版本比较采用的处理分支
     * @return 符合条件的实体记录版本比较数量
     */
    private int count(List<RowComparison> values, String type) {
        return (int) values.stream()
                .filter(item -> type.equals(item.changeType())).count();
    }

    /**
     * 处理字段{@code totals}，并将结果传给后续步骤。
     *
     * @param sections 区段集合，作为 {@code FieldDiff} 的输入影响后续处理
     * @return 处理后的字段{@code totals}结果，供调用方继续处理
     */
    private FieldDiff fieldTotals(List<FormSectionComparison> sections) {
        int data = 0;
        int display = 0;
        int schema = 0;
        for (FormSectionComparison section : sections) {
            for (FieldComparison field : section.fields()) {
                if (Set.of("ADDED", "REMOVED", "MODIFIED")
                        .contains(field.changeType())) {
                    data++;
                }
                if (field.displayChanged()) {
                    display++;
                }
                schema += field.schemaChanges().size();
            }
        }
        return new FieldDiff(sections, data, display, schema);
    }

    /**
     * 处理规范化分页大小，并将结果传给后续步骤。
     *
     * @param requested 请求，作为 {@code Math.max} 的输入影响后续处理
     * @return 处理后的规范化分页大小结果，供调用方继续处理
     */
    private long normalizedPageSize(long requested) {
        return Math.max(1, Math.min(100, requested));
    }

    /**
     * 生成字段名称文本，供后续匹配或展示。
     *
     * @param field 字段，作为 {@code firstText} 的输入影响后续处理
     * @return 处理后的字段名称文本，供调用方比较或展示
     */
    private String fieldName(
            EntityVersionConfiguration.FieldPresentation field) {
        return field == null ? null
                : firstText(field.getFieldLabel(), field.getFieldName(),
                        field.getFieldCode());
    }

    /**
     * 生成区段名称文本，供后续匹配或展示。
     *
     * @param code 编码，后续用于处理区段名称时定位或关联目标
     * @return 处理后的区段名称文本，供调用方比较或展示
     */
    private String sectionName(String code) {
        return switch (code) {
            case "SYSTEM" -> "系统字段";
            case "SUBFORM" -> "历史子表单";
            case "RELATION" -> "关系数据";
            default -> "业务字段";
        };
    }

    /**
     * 整理映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map
                ? (Map<String, Object>) map : Map.of();
    }

    /**
     * 整理映射列表数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射列表的原始输入，结果供调用方继续使用
     * @return 实体记录版本比较集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> mapList(Object value) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list(value)) {
            if (item instanceof Map<?, ?>) {
                result.add(map(item));
            }
        }
        return result;
    }

    /**
     * 列出实体记录版本比较；查询结果供调用方展示或继续处理。
     *
     * @param value 待列出实体记录版本比较的原始输入，结果供调用方继续使用
     * @return {@code collection<?>}集合，供调用方遍历或展示
     */
    private Collection<?> list(Object value) {
        return value instanceof Collection<?> collection
                ? collection : List.of();
    }

    /**
     * 读取键值配置，供后续规则或接口处理使用。
     *
     * @param document 文档，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 映射键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private Map<String, Object> readMap(String document) {
        try {
            return objectMapper.readValue(document,
                    new TypeReference<>() { });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实体版本快照解析失败", exception);
        }
    }

    /**
     * 整理{@code union}数据，供调用方遍历或继续处理。
     *
     * @param left 左侧，作为 {@code result.addAll} 的输入影响后续处理
     * @param right 右侧，作为 {@code result.addAll} 的输入影响后续处理
     * @return 实体记录版本比较集合，供调用方遍历或展示
     */
    private <T> Set<T> union(Collection<T> left, Collection<T> right) {
        Set<T> result = new LinkedHashSet<>();
        result.addAll(left);
        result.addAll(right);
        return result;
    }

    /**
     * 目标版本字段和分组优先；旧版已删除字段按旧冻结顺序追加。
     *
     * @param left 左侧，供本方法处理{@code ordered}字段编码集合时使用
     * @param right 右侧，作为 {@code orderedCodes} 的输入影响后续处理
     * @return 实体记录版本比较集合，供调用方遍历或展示
     */
    private List<String> orderedFieldCodes(
            Map<String, EntityVersionConfiguration.FieldPresentation> left,
            Map<String, EntityVersionConfiguration.FieldPresentation> right) {
        List<String> result = new ArrayList<>(orderedCodes(right));
        for (String code : orderedCodes(left)) {
            if (!right.containsKey(code)) {
                result.add(code);
            }
        }
        return result;
    }

    /**
     * 整理{@code ordered}编码集合数据，供调用方遍历或继续处理。
     *
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @return 实体记录版本比较集合，供调用方遍历或展示
     */
    private List<String> orderedCodes(
            Map<String, EntityVersionConfiguration.FieldPresentation> fields) {
        Map<String, Integer> sectionOrder = new LinkedHashMap<>();
        for (EntityVersionConfiguration.FieldPresentation field
                : fields.values()) {
            sectionOrder.computeIfAbsent(defaultText(
                    field.getSectionCode(), "BUSINESS"),
                    ignored -> sectionOrder.size());
        }
        return fields.values().stream()
                .sorted(java.util.Comparator
                        .comparingInt((EntityVersionConfiguration.FieldPresentation field) ->
                                sectionOrder.get(defaultText(
                                        field.getSectionCode(), "BUSINESS")))
                        .thenComparingInt(field -> value(
                                field.getSortOrder(), 0))
                        .thenComparing(
                                EntityVersionConfiguration.FieldPresentation::getFieldCode))
                .map(EntityVersionConfiguration.FieldPresentation::getFieldCode)
                .toList();
    }

    /**
     * 读取或规范化输入值，供后续计算与比较使用。
     *
     * @param value 待处理值的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的值结果，供调用方继续处理
     */
    private int value(Integer value, int fallback) {
        return value == null ? fallback : value;
    }

    /**
     * 将输入解析为布尔值，供后续条件判断使用。
     *
     * @param value 待处理布尔值值的原始输入，结果供调用方继续使用
     * @return 布尔值值条件成立时为 true，否则为 false
     */
    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool
                ? bool : Boolean.parseBoolean(String.valueOf(value));
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 生成默认文本文本，供后续匹配或展示。
     *
     * @param value 待处理默认文本的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的默认文本文本，供调用方比较或展示
     */
    private String defaultText(String value, String fallback) {
        return StringUtils.hasText(value) ? value : fallback;
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 整理安全数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 实体记录版本比较集合，供调用方遍历或展示
     */
    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }

    /**
     * 封装节点快照的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param entityName 实体名称，后续用于处理节点快照时匹配或展示
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     */
    private record NodeSnapshot(
            String entityName,
            Map<String, EntityVersionConfiguration.FieldPresentation> fields,
            Map<String, FrozenValue> values) {

        /**
         * 处理值集合，并将结果传给后续步骤。
         *
         * @param newValues 新值集合，作为 {@code NodeSnapshot} 的输入影响后续处理
         * @return 处理后的值集合结果，供调用方继续处理
         */
        private NodeSnapshot withValues(Map<String, FrozenValue> newValues) {
            return new NodeSnapshot(entityName, fields, newValues);
        }
    }

    /**
     * 封装字段差异的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param sections 区段集合，保存在对象中供后续校验、查询或展示
     * @param dataChanges 数据变更集合，保存在对象中供后续校验、查询或展示
     * @param displayChanges 展示变更集合，保存在对象中供后续校验、查询或展示
     * @param schemaChanges 结构变更集合，保存在对象中供后续校验、查询或展示
     */
    private record FieldDiff(
            List<FormSectionComparison> sections,
            int dataChanges,
            int displayChanges,
            int schemaChanges) {
    }

    /**
     * 封装数据集差异的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param comparability {@code comparability}，保存在对象中供后续校验、查询或展示
     * @param counts {@code counts}，保存在对象中供后续校验、查询或展示
     * @param scopeChanged 作用域已变更，保存在对象中供后续校验、查询或展示
     * @param dataChanges 数据变更集合，保存在对象中供后续校验、查询或展示
     * @param displayChanges 展示变更集合，保存在对象中供后续校验、查询或展示
     * @param schemaChanges 结构变更集合，保存在对象中供后续校验、查询或展示
     */
    private record DatasetDiff(
            String comparability,
            RowChangeCounts counts,
            boolean scopeChanged,
            int dataChanges,
            int displayChanges,
            int schemaChanges) {
    }

    /**
     * 负责{@code totals}的业务处理；协调校验、状态变化及后续结果传递。
     */
    private static final class Totals {
        private int data;
        private int display;
        private int schema;
        private int added;
        private int removed;
        private int modified;
        private int moved;

        /**
         * 添加{@code totals}；结果供后续流程传递或持久化。
         *
         * @param value 待添加{@code totals}的原始输入，结果供调用方继续使用
         */
        private void add(FieldDiff value) {
            data += value.dataChanges();
            display += value.displayChanges();
            schema += value.schemaChanges();
        }

        /**
         * 添加{@code totals}；结果供后续流程传递或持久化。
         *
         * @param value 待添加{@code totals}的原始输入，结果供调用方继续使用
         */
        private void add(DatasetDiff value) {
            data += value.dataChanges();
            display += value.displayChanges();
            schema += value.schemaChanges();
            added += value.counts().added();
            removed += value.counts().removed();
            modified += value.counts().modified();
            moved += value.counts().moved();
        }
    }
}
