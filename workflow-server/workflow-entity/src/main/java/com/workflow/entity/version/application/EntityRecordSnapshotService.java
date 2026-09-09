package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workflow.admin.dictionary.application.SysDictItemService;
import com.workflow.admin.dictionary.infrastructure.persistence.record.SysDictItem;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.organization.application.SysOrganizationService;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldOptionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityFieldOption;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.EntityVersionScopePreview;
import com.workflow.entity.version.application.model.FrozenValue;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 生成业务实体不可变完整快照，并冻结当时的中文显示值。
 */
@Service
@RequiredArgsConstructor
public class EntityRecordSnapshotService {

    public static final int HARD_MAX_ROWS_PER_RELATION = 500;
    public static final int HARD_MAX_ROWS_PER_VERSION = 2000;
    public static final long HARD_MAX_BYTES_PER_VERSION =
            5L * 1024L * 1024L;
    /** 数据集行内部保存父记录身份，历史详情和字段比较不得向用户展示。 */
    public static final String INTERNAL_PARENT_RECORD_ID =
            "__scopeParentRecordId";

    private static final List<SystemField> SYSTEM_FIELDS = List.of(
            new SystemField("id", "数据ID", "STRING"),
            new SystemField("dataNo", "业务编号", "STRING"),
            new SystemField("title", "标题", "STRING"),
            new SystemField("name", "名称", "STRING"),
            new SystemField("code", "编码", "STRING"),
            new SystemField("status", "实体状态", "STATUS"),
            new SystemField("processInstanceId", "流程实例ID", "STRING"),
            new SystemField("processStartTime", "流程开始时间", "DATETIME"),
            new SystemField("processEndTime", "流程结束时间", "DATETIME"),
            new SystemField("currentTaskId", "当前任务ID", "STRING"),
            new SystemField("currentTaskName", "当前任务名称", "STRING"),
            new SystemField("currentTaskAssignee", "当前任务办理人", "USER"),
            new SystemField("submitterId", "提交人ID", "USER"),
            new SystemField("submitterName", "提交人", "STRING"),
            new SystemField("deptId", "所属部门ID", "DEPT"),
            new SystemField("deptName", "所属部门", "STRING"),
            new SystemField("submitTime", "提交时间", "DATETIME"),
            new SystemField("createdAt", "创建时间", "DATETIME"),
            new SystemField("updatedAt", "更新时间", "DATETIME"),
            new SystemField("createdBy", "创建人", "USER"),
            new SystemField("updatedBy", "更新人", "USER"));

    private final EntityPublishedSnapshotService publishedSnapshotService;
    private final EntityFieldOptionMapper optionMapper;
    private final EntityStatusMapper statusMapper;
    private final SysDictItemService dictItemService;
    private final SysUserService userService;
    private final SysOrganizationService organizationService;
    private final ObjectMapper objectMapper;

    public SnapshotCapture capture(
            String entityCode,
            String recordId,
            Map<String, Object> aggregateRecord,
            boolean deletedSnapshot) {
        EntityPublishedSnapshot published =
                publishedSnapshotService
                        .getLatestByEntityCode(entityCode);
        Map<String, Object> record =
                deepCopy(aggregateRecord);
        Map<String, Object> customData =
                map(record.get("data"));
        List<Map<String, Object>> systemFields =
                captureSystemFields(
                        entityCode,
                        record);
        List<Map<String, Object>> businessFields =
                new ArrayList<>();
        List<Map<String, Object>> relationFields =
                new ArrayList<>();
        List<EntityField> publishedFields =
                published.getFields() == null
                        ? List.of()
                        : published.getFields();
        for (EntityField field : publishedFields) {
            if (field.getFieldType()
                    == EntityField.FieldType.SUB_LIST) {
                continue;
            }
            Map<String, Object> item =
                    captureBusinessField(field, customData);
            if (isRelation(field)) {
                relationFields.add(item);
            } else {
                businessFields.add(item);
            }
        }
        List<Map<String, Object>> allFields =
                new ArrayList<>();
        allFields.addAll(systemFields);
        allFields.addAll(businessFields);
        allFields.addAll(relationFields);

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("entityId", published.getEntityId());
        entity.put("entityCode", published.getEntityCode());
        entity.put("entityName", published.getEntityName());
        entity.put("releaseId", published.getHistoryId());
        entity.put("releaseVersion", published.getVersion());

        Map<String, Object> document = new LinkedHashMap<>();
        document.put("schemaVersion", 1);
        document.put("entity", entity);
        document.put("recordId", recordId);
        document.put("deletedSnapshot", deletedSnapshot);
        document.put("capturedAt", LocalDateTime.now());
        document.put("record", record);
        document.put("systemFields", systemFields);
        document.put("businessFields", businessFields);
        document.put("relationFields", relationFields);
        document.put("fields", allFields);

        Map<String, Object> hashMaterial =
                new LinkedHashMap<>();
        hashMaterial.put("entity", entity);
        hashMaterial.put("record", record);
        hashMaterial.put("fields", allFields);
        hashMaterial.put("deletedSnapshot", deletedSnapshot);
        return new SnapshotCapture(
                document,
                hash(hashMaterial),
                published.getHistoryId(),
                published.getVersion());
    }

    /**
     * 按当前 V2 冻结范围捕获根实体和多层组成关系数据集。
     *
     * <p>旧一层范围仍走同一逻辑；多层节点必须带有保存时冻结的父节点和关系路径。
     * 捕获严格拒绝发布漂移、记录环、同一组成子记录归属多个父记录以及任何预算超限，
     * 不允许用截断快照冒充完整业务版本。</p>
     */
    public SnapshotCaptureV2 captureV2(
            EntityVersionConfiguration configuration,
            String recordId,
            Map<String, Object> aggregateRecord,
            boolean deletedSnapshot) {
        EntityVersionConfiguration.SnapshotScope scope =
                configuration.getSnapshotScope();
        if (scope == null || scope.getRoot() == null) {
            throw new IllegalStateException("V2当前配置缺少冻结固化范围");
        }
        requireFrozenScopeCurrent(scope);
        Map<String, Object> record = deepCopy(aggregateRecord);
        Map<String, Object> customData = map(record.get("data"));
        Map<String, EntityVersionConfiguration.FieldPresentation>
                rootPresentation = rootPresentations(scope.getRoot());
        Map<String, FrozenValue> rootValues = new LinkedHashMap<>();
        for (Map.Entry<String, EntityVersionConfiguration.FieldPresentation> entry
                : rootPresentation.entrySet()) {
            Object raw = rootValue(record, customData, entry.getKey());
            rootValues.put(entry.getKey(), frozenValue(
                    configuration.getEntityCode(), entry.getValue(), raw));
        }

        Map<String, Object> entity = new LinkedHashMap<>();
        entity.put("entityCode", scope.getRoot().getEntityCode());
        entity.put("entityName", scope.getRoot().getEntityName());
        entity.put("releaseId", scope.getRoot().getEntityReleaseId());
        entity.put("releaseVersion", scope.getRoot().getEntityReleaseVersion());
        Map<String, Object> rootDocument = new LinkedHashMap<>();
        rootDocument.put("schemaVersion", 2);
        rootDocument.put("nodeCode", "ROOT");
        rootDocument.put("nodeKind", "ROOT");
        rootDocument.put("entity", entity);
        rootDocument.put("recordId", recordId);
        rootDocument.put("deletedSnapshot", deletedSnapshot);
        rootDocument.put("capturedAt", LocalDateTime.now());
        rootDocument.put("presentation", presentation(rootPresentation));
        rootDocument.put("values", rootValues);
        rootDocument.put("diffPolicy", configuration.getDiffPolicy());

        List<DatasetCapture> datasets = new ArrayList<>();
        int totalRows = 0;
        Map<String, List<CaptureNodeRecord>> recordsByNode =
                new LinkedHashMap<>();
        String rootIdentity = recordIdentity(
                scope.getRoot().getEntityCode(), recordId);
        recordsByNode.put("ROOT", List.of(new CaptureNodeRecord(
                record,
                recordId,
                java.util.Set.of(rootIdentity))));
        List<EntityVersionConfiguration.RelationScope> orderedRelations =
                safe(scope.getRelations()).stream()
                        .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                        .sorted(java.util.Comparator
                                .comparing((EntityVersionConfiguration.RelationScope item) ->
                                        item.getDepth() == null ? 1 : item.getDepth())
                                .thenComparing(
                                        EntityVersionConfiguration.RelationScope::getNodeCode))
                        .toList();
        for (EntityVersionConfiguration.RelationScope relation
                : orderedRelations) {
            if (Boolean.FALSE.equals(relation.getEnabled())) {
                continue;
            }
            String parentNodeCode = normalizedParentNode(relation);
            List<CaptureNodeRecord> parents = recordsByNode.get(
                    parentNodeCode);
            if (parents == null) {
                throw new BusinessConflictException(
                        "ENTITY_VERSION_SCOPE_PATH_INVALID",
                        "固化范围节点 " + relation.getNodeCode()
                                + " 的父节点不存在: " + parentNodeCode);
            }
            List<CapturedRawRow> rows = new ArrayList<>();
            java.util.Set<String> childIds = new java.util.LinkedHashSet<>();
            for (CaptureNodeRecord parent : parents) {
                List<Map<String, Object>> nestedRows = relationRows(
                        rowValue(parent.record(), relation.getDataKey()),
                        relation.getRelationType()).stream()
                        .filter(row -> matchesFilter(
                                row, relation.getFilter()))
                        .toList();
                for (Map<String, Object> row : nestedRows) {
                    String childId = firstText(row.get("id"));
                    if (!StringUtils.hasText(childId)) {
                        throw new BusinessConflictException(
                                "ENTITY_VERSION_SCOPE_ROW_ID_MISSING",
                                "关系 " + relation.getRelationName()
                                        + " 中存在没有稳定ID的子记录");
                    }
                    String identity = recordIdentity(
                            relation.getChildEntityCode(), childId);
                    if (parent.ancestry().contains(identity)) {
                        throw new BusinessConflictException(
                                "ENTITY_VERSION_SCOPE_RECORD_CYCLE",
                                "固化范围沿 " + relation.getRelationName()
                                        + " 发现记录环: " + childId);
                    }
                    if (!childIds.add(childId)) {
                        throw new BusinessConflictException(
                                "ENTITY_VERSION_SCOPE_DUPLICATE_OWNERSHIP",
                                "组成关系 " + relation.getRelationName()
                                        + " 中子记录 " + childId
                                        + " 同时归属多个父记录");
                    }
                    java.util.Set<String> ancestry =
                            new java.util.LinkedHashSet<>(parent.ancestry());
                    ancestry.add(identity);
                    rows.add(new CapturedRawRow(
                            row, childId, parent.recordId(),
                            java.util.Set.copyOf(ancestry)));
                }
            }
            int relationLimit = effectiveRelationLimit(scope, relation);
            if (rows.size() > relationLimit) {
                throw limitFailure(
                        relation.getRelationName(),
                        rows.size(),
                        relationLimit);
            }
            totalRows += rows.size();
            if (totalRows > configuredTotalLimit(scope)) {
                throw new BusinessConflictException(
                        "ENTITY_VERSION_SCOPE_LIMIT_EXCEEDED",
                        "固化范围关系记录总数 " + totalRows
                                + " 超过上限 " + configuredTotalLimit(scope));
            }
            Map<String, EntityVersionConfiguration.FieldPresentation>
                    fields = indexPresentation(relation.getFields());
            List<DatasetRowCapture> capturedRows = new ArrayList<>();
            List<CaptureNodeRecord> childRecords = new ArrayList<>();
            int order = 0;
            for (CapturedRawRow captured : rows) {
                Map<String, Object> row = captured.record();
                Map<String, FrozenValue> values = new LinkedHashMap<>();
                for (Map.Entry<String,
                        EntityVersionConfiguration.FieldPresentation> field
                        : fields.entrySet()) {
                    values.put(field.getKey(), frozenValue(
                            relation.getChildEntityCode(),
                            field.getValue(),
                            rowValue(row, field.getKey())));
                }
                String childId = captured.recordId();
                values.put(INTERNAL_PARENT_RECORD_ID, new FrozenValue(
                        captured.parentRecordId(),
                        captured.parentRecordId(),
                        List.of(), "INTERNAL", "RESOLVED"));
                boolean trackOrder = configuration.getDiffPolicy() != null
                        && Boolean.TRUE.equals(
                                configuration.getDiffPolicy().getTrackOrder());
                Map<String, Object> rowHashMaterial = new LinkedHashMap<>();
                // 子记录身份属于业务数据的一部分；同值记录被替换时也必须产生新版本差异。
                rowHashMaterial.put("recordId", childId);
                // 多层关系下父身份也是业务图的一部分；换父必须产生可比较差异。
                rowHashMaterial.put("parentRecordId",
                        captured.parentRecordId());
                rowHashMaterial.put("values", rawValues(values));
                if (trackOrder) {
                    rowHashMaterial.put("rowOrder", order);
                }
                capturedRows.add(new DatasetRowCapture(
                        childId,
                        firstText(
                                row.get("title"),
                                row.get("name"),
                                row.get("code"),
                                row.get("dataNo"),
                                childId),
                        order++,
                        values,
                        hash(rowHashMaterial)));
                childRecords.add(new CaptureNodeRecord(
                        row, childId, captured.ancestry()));
            }
            recordsByNode.put(relation.getNodeCode(), childRecords);
            Map<String, Object> selector = new LinkedHashMap<>();
            selector.put("parentNodeCode", parentNodeCode);
            selector.put("depth", relation.getDepth());
            selector.put("relationCode", relation.getRelationCode());
            selector.put("dataKey", relation.getDataKey());
            selector.put("childEntityCode", relation.getChildEntityCode());
            selector.put("childRefFieldCode", relation.getChildRefFieldCode());
            selector.put("relationType", relation.getRelationType());
            selector.put("relationDefinitionHash",
                    relation.getRelationDefinitionHash());
            selector.put("relationPath", relation.getRelationPath());
            selector.put("parentEntityReleaseId",
                    relation.getParentEntityReleaseId());
            selector.put("parentEntitySchemaHash",
                    relation.getParentEntitySchemaHash());
            selector.put("entitySchemaHash",
                    relation.getEntitySchemaHash());
            selector.put("filter", relation.getFilter());
            selector.put("maxRows", relationLimit);
            boolean trackOrder = configuration.getDiffPolicy() != null
                    && Boolean.TRUE.equals(
                            configuration.getDiffPolicy().getTrackOrder());
            selector.put("trackOrder", trackOrder);
            Map<String, Object> relationPresentation = presentation(fields);
            List<DatasetRowCapture> hashRows = new ArrayList<>(capturedRows);
            if (!trackOrder) {
                hashRows.sort(java.util.Comparator.comparing(
                        DatasetRowCapture::recordId));
            }
            datasets.add(new DatasetCapture(
                    relation.getNodeCode(),
                    relation.getRelationCode(),
                    relation.getRelationName(),
                    relation.getChildEntityCode(),
                    relation.getChildEntityName(),
                    relation.getEntityReleaseId(),
                    relation.getEntityReleaseVersion(),
                    selector,
                    relationPresentation,
                    capturedRows,
                    hash(hashRows.stream()
                            .map(DatasetRowCapture::rowHash).toList()),
                    hash(relationPresentation),
                    hash(selector)));
        }

        Map<String, Object> dataMaterial = new LinkedHashMap<>();
        dataMaterial.put("deletedSnapshot", deletedSnapshot);
        dataMaterial.put("root", rawValues(rootValues));
        dataMaterial.put("datasets", datasets.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DatasetCapture::nodeCode,
                        DatasetCapture::dataHash,
                        (left, right) -> left,
                        LinkedHashMap::new)));
        Map<String, Object> presentationMaterial = new LinkedHashMap<>();
        presentationMaterial.put("root", Map.of(
                "entityName", scope.getRoot().getEntityName(),
                "presentation", rootDocument.get("presentation")));
        presentationMaterial.put("datasets", datasets.stream()
                .collect(java.util.stream.Collectors.toMap(
                        DatasetCapture::nodeCode,
                        item -> Map.of(
                                "relationName", item.relationName(),
                                "entityName", item.entityName(),
                                "presentation", item.presentation()),
                        (left, right) -> left,
                        LinkedHashMap::new)));
        long size = serializedSize(rootDocument)
                + datasets.stream().mapToLong(this::serializedSize).sum();
        if (size > configuredByteLimit(scope)) {
            throw new BusinessConflictException(
                    "ENTITY_VERSION_SCOPE_LIMIT_EXCEEDED",
                    "固化快照大小 " + size + " 字节超过上限 "
                            + configuredByteLimit(scope) + " 字节");
        }
        return new SnapshotCaptureV2(
                rootDocument,
                hash(dataMaterial),
                hash(presentationMaterial),
                scope.getScopeHash(),
                scope.getRoot().getEntityReleaseId(),
                scope.getRoot().getEntityReleaseVersion(),
                datasets,
                totalRows,
                size);
    }

    public EntityVersionScopePreview previewV2(
            EntityVersionConfiguration configuration,
            Map<String, Object> aggregateRecord) {
        EntityVersionConfiguration.SnapshotScope scope =
                configuration.getSnapshotScope();
        Map<String, Object> rootRecord = deepCopy(aggregateRecord);
        List<EntityVersionScopePreview.DatasetPreview> previews =
                new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        Map<String, List<Map<String, Object>>> recordsByNode =
                new LinkedHashMap<>();
        recordsByNode.put("ROOT", List.of(rootRecord));
        int total = 0;
        boolean valid = true;
        boolean exceeds = false;
        List<EntityVersionConfiguration.RelationScope> orderedRelations =
                safe(scope.getRelations()).stream()
                        .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                        .sorted(java.util.Comparator
                                .comparing((EntityVersionConfiguration.RelationScope item) ->
                                        item.getDepth() == null ? 1 : item.getDepth())
                                .thenComparing(
                                        EntityVersionConfiguration.RelationScope::getNodeCode))
                        .toList();
        for (EntityVersionConfiguration.RelationScope relation
                : orderedRelations) {
            String parentNodeCode = normalizedParentNode(relation);
            List<Map<String, Object>> parents = recordsByNode.get(
                    parentNodeCode);
            if (parents == null) {
                warnings.add("固化范围节点 " + relation.getNodeCode()
                        + " 的父节点不存在: " + parentNodeCode);
                valid = false;
                recordsByNode.put(relation.getNodeCode(), List.of());
                continue;
            }
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> parent : parents) {
                rows.addAll(relationRows(
                        rowValue(parent, relation.getDataKey()),
                        relation.getRelationType()).stream()
                        .filter(row -> matchesFilter(
                                row, relation.getFilter()))
                        .toList());
            }
            recordsByNode.put(relation.getNodeCode(), rows);
            int count = rows.size();
            int max = effectiveRelationLimit(scope, relation);
            boolean itemExceeds = count > max;
            total += count;
            exceeds = exceeds || itemExceeds;
            previews.add(new EntityVersionScopePreview.DatasetPreview(
                    relation.getNodeCode(), relation.getRelationCode(),
                    relation.getRelationName(), relation.getChildEntityCode(),
                    relation.getChildEntityName(), count, max, itemExceeds));
        }
        long estimatedBytes = serializedSize(aggregateRecord);
        exceeds = exceeds
                || total > configuredTotalLimit(scope)
                || estimatedBytes > configuredByteLimit(scope);
        if (exceeds) {
            warnings.add("样例记录超过固化范围上限，正式捕获将整体失败");
        }
        return new EntityVersionScopePreview(
                valid, total, estimatedBytes, exceeds,
                previews, List.copyOf(warnings));
    }

    /** RELATED_MUTATION 触发判定与实际捕获共用同一固定过滤语义。 */
    public boolean matchesFixedFilter(
            Map<String, Object> row,
            EntityVersionConfiguration.FixedFilter filter) {
        return matchesFilter(row == null ? Map.of() : row, filter);
    }

    private List<Map<String, Object>> captureSystemFields(
            String entityCode,
            Map<String, Object> record) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (SystemField field : SYSTEM_FIELDS) {
            Object value = record.get(field.code());
            result.add(field(
                    field.code(),
                    field.name(),
                    field.type(),
                    value,
                    displaySystemValue(
                            entityCode,
                            field.type(),
                            value),
                    "SYSTEM",
                    null));
        }
        return result;
    }

    private Map<String, Object> captureBusinessField(
            EntityField field,
            Map<String, Object> customData) {
        Object value = customData.get(field.getFieldCode());
        String group = switch (field.getFieldType()) {
            case SUB_FORM -> "SUBFORM";
            case REFERENCE, MULTI_REFERENCE -> "RELATION";
            default -> "BUSINESS";
        };
        return field(
                field.getFieldCode(),
                field.getFieldName(),
                field.getFieldType() == null
                        ? "UNKNOWN"
                        : field.getFieldType().name(),
                value,
                displayFieldValue(field, value),
                group,
                field.getSortOrder());
    }

    private Map<String, Object> field(
            String code,
            String name,
            String type,
            Object value,
            Object displayValue,
            String group,
            Integer sortOrder) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("fieldCode", code);
        result.put("fieldName", name);
        result.put("fieldType", type);
        result.put("value", value);
        result.put("displayValue", displayValue);
        result.put("group", group);
        result.put("sortOrder", sortOrder);
        return result;
    }

    private Object displaySystemValue(
            String entityCode,
            String type,
            Object value) {
        if (value == null) {
            return null;
        }
        if ("STATUS".equals(type)) {
            EntityStatus status = statusMapper.findByEntityAndCode(
                    entityCode,
                    String.valueOf(value));
            return status == null
                    ? value : status.getStatusName();
        }
        if ("USER".equals(type)) {
            return displayUsers(value);
        }
        if ("DEPT".equals(type)) {
            return displayDepartments(value);
        }
        return value;
    }

    private Object displayFieldValue(
            EntityField field,
            Object value) {
        if (value == null) {
            return null;
        }
        if (field.getRefEntityType()
                == EntityField.RefEntityType.USER
                || field.getFieldType()
                == EntityField.FieldType.USER) {
            return displayUsers(value);
        }
        if (field.getRefEntityType()
                == EntityField.RefEntityType.DEPT
                || field.getFieldType()
                == EntityField.FieldType.DEPT) {
            return displayDepartments(value);
        }
        if (StringUtils.hasText(field.getDictType())) {
            return displayDictionary(
                    field.getDictType(),
                    value);
        }
        if (isOptionField(field)) {
            return displayOptions(field, value);
        }
        if (field.getFieldType()
                == EntityField.FieldType.BOOLEAN) {
            return Boolean.parseBoolean(
                    String.valueOf(value))
                    ? "是" : "否";
        }
        if (isRelation(field)) {
            return relationDisplay(value);
        }
        return value;
    }

    private Object displayUsers(Object value) {
        List<String> values = stringValues(value);
        if (values.isEmpty()) {
            return value;
        }
        return values.size() == 1
                ? userService.getDisplayName(values.get(0))
                : userService.getDisplayNames(values);
    }

    private Object displayDepartments(Object value) {
        List<String> values = stringValues(value);
        if (values.isEmpty()) {
            return value;
        }
        List<String> names = values.stream()
                .map(organizationService::getById)
                .filter(Objects::nonNull)
                .map(SysOrganization::getOrgName)
                .toList();
        return names.isEmpty()
                ? value : String.join(",", names);
    }

    private Object displayDictionary(
            String dictCode,
            Object value) {
        Map<String, String> labels = new LinkedHashMap<>();
        flattenDictItems(
                dictItemService.getItemTreeByDictCode(dictCode),
                labels);
        return displayMapped(value, labels);
    }

    private Object displayOptions(
            EntityField field,
            Object value) {
        Map<String, String> labels = new LinkedHashMap<>();
        if (StringUtils.hasText(field.getId())) {
            for (EntityFieldOption option
                    : optionMapper.findByFieldId(field.getId())) {
                labels.put(
                        option.getOptionValue(),
                        option.getOptionLabel());
            }
        }
        if (labels.isEmpty()
                && StringUtils.hasText(field.getOptionsJson())) {
            for (Map<String, Object> option
                    : readOptions(field.getOptionsJson())) {
                String optionValue = firstText(
                        option.get("value"),
                        option.get("optionValue"),
                        option.get("code"));
                String optionLabel = firstText(
                        option.get("label"),
                        option.get("optionLabel"),
                        option.get("name"));
                if (optionValue != null) {
                    labels.put(optionValue,
                            optionLabel == null
                                    ? optionValue
                                    : optionLabel);
                }
            }
        }
        return displayMapped(value, labels);
    }

    private Object displayMapped(
            Object value,
            Map<String, String> labels) {
        List<String> values = stringValues(value);
        if (values.isEmpty()) {
            return value;
        }
        List<String> result = values.stream()
                .map(item -> labels.getOrDefault(item, item))
                .toList();
        return result.size() == 1
                ? result.get(0)
                : String.join(",", result);
    }

    private Object relationDisplay(Object value) {
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .map(this::relationDisplay)
                    .toList();
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of(
                    "displayName", "label", "name",
                    "title", "code", "id")) {
                if (map.get(key) != null) {
                    return map.get(key);
                }
            }
        }
        return value;
    }

    private boolean isRelation(EntityField field) {
        return switch (field.getFieldType()) {
            case REFERENCE, MULTI_REFERENCE, SUB_FORM -> true;
            default -> false;
        };
    }

    private boolean isOptionField(EntityField field) {
        return switch (field.getFieldType()) {
            case SELECT, MULTI_SELECT, RADIO, CHECKBOX -> true;
            default -> false;
        };
    }

    private void flattenDictItems(
            List<SysDictItem> items,
            Map<String, String> labels) {
        if (items == null) {
            return;
        }
        for (SysDictItem item : items) {
            if (item.getItemValue() != null) {
                labels.put(item.getItemValue(),
                        item.getItemLabel());
            }
            if (item.getItemCode() != null) {
                labels.putIfAbsent(item.getItemCode(),
                        item.getItemLabel());
            }
            flattenDictItems(item.getChildren(), labels);
        }
    }

    private List<String> stringValues(Object value) {
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .filter(Objects::nonNull)
                    .map(String::valueOf)
                    .toList();
        }
        if (value != null && value.getClass().isArray()) {
            return objectMapper.convertValue(
                    value,
                    new TypeReference<>() {
                    });
        }
        return value == null
                ? List.of()
                : List.of(String.valueOf(value));
    }

    private List<Map<String, Object>> readOptions(
            String json) {
        try {
            return objectMapper.readValue(
                    json,
                    new TypeReference<>() {
                    });
        } catch (JsonProcessingException exception) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            return (Map<String, Object>) source;
        }
        return Map.of();
    }

    private Map<String, Object> deepCopy(
            Map<String, Object> value) {
        if (value == null) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(
                value,
                new TypeReference<>() {
                });
    }

    private String hash(Object material) {
        try {
            String canonical = objectMapper.writer()
                    .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(material);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "实体版本快照哈希生成失败",
                    exception);
        }
    }

    private Map<String, EntityVersionConfiguration.FieldPresentation>
            rootPresentations(EntityVersionConfiguration.ScopeNode root) {
        Map<String, EntityVersionConfiguration.FieldPresentation> result =
                new LinkedHashMap<>();
        for (SystemField system : SYSTEM_FIELDS) {
            EntityVersionConfiguration.FieldPresentation field =
                    new EntityVersionConfiguration.FieldPresentation();
            field.setFieldCode(system.code());
            field.setFieldName(system.name());
            field.setFieldLabel(system.name());
            field.setFieldType(system.type());
            field.setSectionCode("SYSTEM");
            field.setSectionName("系统字段");
            field.setSortOrder(result.size());
            result.put(field.getFieldCode(), field);
        }
        result.putAll(indexPresentation(root.getFields()));
        return result;
    }

    private Map<String, EntityVersionConfiguration.FieldPresentation>
            indexPresentation(
                    List<EntityVersionConfiguration.FieldPresentation> fields) {
        Map<String, EntityVersionConfiguration.FieldPresentation> result =
                new LinkedHashMap<>();
        for (EntityVersionConfiguration.FieldPresentation field : safe(fields)) {
            if (field != null && StringUtils.hasText(field.getFieldCode())) {
                result.put(field.getFieldCode(), field);
            }
        }
        return result;
    }

    private Map<String, Object> presentation(
            Map<String, EntityVersionConfiguration.FieldPresentation> fields) {
        Map<String, List<EntityVersionConfiguration.FieldPresentation>> sections =
                new LinkedHashMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (EntityVersionConfiguration.FieldPresentation field : fields.values()) {
            String code = StringUtils.hasText(field.getSectionCode())
                    ? field.getSectionCode() : "BUSINESS";
            String name = StringUtils.hasText(field.getSectionName())
                    ? field.getSectionName() : "业务字段";
            names.putIfAbsent(code, name);
            sections.computeIfAbsent(code, ignored -> new ArrayList<>())
                    .add(field);
        }
        List<Map<String, Object>> sectionValues = new ArrayList<>();
        for (Map.Entry<String,
                List<EntityVersionConfiguration.FieldPresentation>> entry
                : sections.entrySet()) {
            Map<String, Object> section = new LinkedHashMap<>();
            section.put("sectionCode", entry.getKey());
            section.put("sectionName", names.get(entry.getKey()));
            section.put("fields", entry.getValue());
            sectionValues.add(section);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "GENERATED_FORM");
        result.put("sections", sectionValues);
        result.put("fields", new ArrayList<>(fields.values()));
        return result;
    }

    private FrozenValue frozenValue(
            String entityCode,
            EntityVersionConfiguration.FieldPresentation field,
            Object raw) {
        if (raw == null) {
            return new FrozenValue(
                    null, null, List.of(), "EMPTY", "RESOLVED");
        }
        String type = field.getFieldType() == null
                ? "UNKNOWN" : field.getFieldType().toUpperCase(Locale.ROOT);
        Object display;
        Map<String, String> labels = field.getOptionLabels() == null
                ? Map.of() : field.getOptionLabels();
        if (StringUtils.hasText(field.getDictType())) {
            Map<String, String> dictLabels = new LinkedHashMap<>();
            flattenDictItems(dictItemService.getItemTreeByDictCode(
                    field.getDictType()), dictLabels);
            labels = dictLabels;
            display = displayMapped(raw, labels);
        } else if (!labels.isEmpty()) {
            display = displayMapped(raw, labels);
        } else if ("USER".equals(type)) {
            display = displayUsers(raw);
        } else if ("DEPT".equals(type)) {
            display = displayDepartments(raw);
        } else if ("STATUS".equals(type)) {
            display = displaySystemValue(entityCode, type, raw);
        } else if ("BOOLEAN".equals(type)) {
            display = Boolean.parseBoolean(String.valueOf(raw)) ? "是" : "否";
        } else if ("REFERENCE".equals(type)
                || "MULTI_REFERENCE".equals(type)) {
            display = relationDisplay(raw);
        } else {
            display = raw;
        }
        List<String> rawItems = stringValues(raw);
        List<FrozenValue.DisplayItem> displayItems = new ArrayList<>();
        for (String item : rawItems) {
            displayItems.add(new FrozenValue.DisplayItem(
                    item, labels.getOrDefault(item,
                    display instanceof String && rawItems.size() == 1
                            ? String.valueOf(display) : item)));
        }
        String displayText;
        if (display == null) {
            displayText = null;
        } else if (display instanceof String value) {
            displayText = value;
        } else {
            try {
                displayText = objectMapper.writeValueAsString(display);
            } catch (JsonProcessingException exception) {
                displayText = String.valueOf(display);
            }
        }
        return new FrozenValue(raw, displayText, displayItems,
                "PRESENT", "RESOLVED");
    }

    private Object rootValue(
            Map<String, Object> record,
            Map<String, Object> customData,
            String fieldCode) {
        return record.containsKey(fieldCode)
                ? record.get(fieldCode) : customData.get(fieldCode);
    }

    private List<Map<String, Object>> relationRows(
            Object value,
            String relationType) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (value instanceof Map<?, ?> row) {
            result.add(deepCopy(map(row)));
        } else if (value instanceof Collection<?> rows) {
            for (Object row : rows) {
                if (row instanceof Map<?, ?>) {
                    result.add(deepCopy(map(row)));
                }
            }
        }
        if ("ONE_TO_ONE".equals(relationType) && result.size() > 1) {
            throw new BusinessConflictException(
                    "ENTITY_VERSION_RELATION_CARDINALITY_VIOLATION",
                    "一对一关系实际存在 " + result.size()
                            + " 条子记录，拒绝生成不完整版本");
        }
        return result;
    }

    private boolean matchesFilter(
            Map<String, Object> row,
            EntityVersionConfiguration.FixedFilter filter) {
        if (filter == null || safe(filter.getConditions()).isEmpty()) {
            return true;
        }
        boolean any = "ANY".equalsIgnoreCase(filter.getLogic());
        for (EntityVersionConfiguration.FilterCondition condition
                : filter.getConditions()) {
            boolean matched = matchesCondition(row, condition);
            if (any && matched) {
                return true;
            }
            if (!any && !matched) {
                return false;
            }
        }
        return !any;
    }

    private boolean matchesCondition(
            Map<String, Object> row,
            EntityVersionConfiguration.FilterCondition condition) {
        Object actual = rowValue(row, condition.getFieldCode());
        Object expected = condition.getValue();
        String operator = condition.getOperator() == null
                ? "EQ" : condition.getOperator().toUpperCase(Locale.ROOT);
        return switch (operator) {
            case "EQ" -> equivalent(actual, expected);
            case "NE" -> !equivalent(actual, expected);
            case "EMPTY" -> actual == null || "".equals(actual)
                    || actual instanceof Collection<?> values && values.isEmpty();
            case "NOT_EMPTY" -> !(actual == null || "".equals(actual)
                    || actual instanceof Collection<?> values && values.isEmpty());
            case "IN" -> collection(expected).stream()
                    .anyMatch(candidate -> equivalent(actual, candidate));
            case "NOT_IN" -> collection(expected).stream()
                    .noneMatch(candidate -> equivalent(actual, candidate));
            case "CONTAINS" -> actual instanceof Collection<?> values
                    ? values.stream()
                            .anyMatch(candidate -> equivalent(candidate, expected))
                    : actual != null && expected != null
                            && String.valueOf(actual).contains(String.valueOf(expected));
            case "GT" -> compare(actual, expected) > 0;
            case "GTE" -> compare(actual, expected) >= 0;
            case "LT" -> compare(actual, expected) < 0;
            case "LTE" -> compare(actual, expected) <= 0;
            default -> false;
        };
    }

    private boolean equivalent(Object left, Object right) {
        if (Objects.equals(left, right)) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        Boolean leftBoolean = booleanValue(left);
        Boolean rightBoolean = booleanValue(right);
        if (leftBoolean != null
                && rightBoolean != null
                && (left instanceof Boolean
                        || right instanceof Boolean
                        || booleanWord(left)
                        || booleanWord(right))) {
            return leftBoolean.equals(rightBoolean);
        }
        if (left instanceof Number || right instanceof Number) {
            try {
                return new java.math.BigDecimal(String.valueOf(left))
                        .compareTo(new java.math.BigDecimal(
                                String.valueOf(right))) == 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return String.valueOf(left).equals(String.valueOf(right));
    }

    private Boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value).trim();
        if ("true".equalsIgnoreCase(text) || "1".equals(text)) {
            return true;
        }
        if ("false".equalsIgnoreCase(text) || "0".equals(text)) {
            return false;
        }
        return null;
    }

    private boolean booleanWord(Object value) {
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text)
                || "false".equalsIgnoreCase(text);
    }

    private Object path(Map<String, Object> row, String fieldCode) {
        if (!StringUtils.hasText(fieldCode)) {
            return null;
        }
        Object current = row;
        for (String part : fieldCode.split("\\.")) {
            if (!(current instanceof Map<?, ?> map)) {
                return null;
            }
            current = map.get(part);
        }
        return current;
    }

    private Object rowValue(
            Map<String, Object> row,
            String fieldCode) {
        Object direct = path(row, fieldCode);
        if (direct != null || row.containsKey(fieldCode)) {
            return direct;
        }
        return path(map(row.get("data")), fieldCode);
    }

    private Collection<?> collection(Object value) {
        return value instanceof Collection<?> values
                ? values : value == null ? List.of() : List.of(value);
    }

    private int compare(Object left, Object right) {
        if (left == null || right == null) {
            return left == right ? 0 : left == null ? -1 : 1;
        }
        try {
            return new java.math.BigDecimal(String.valueOf(left))
                    .compareTo(new java.math.BigDecimal(String.valueOf(right)));
        } catch (NumberFormatException ignored) {
            return String.valueOf(left).compareTo(String.valueOf(right));
        }
    }

    private Map<String, Object> rawValues(Map<String, FrozenValue> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, FrozenValue> entry : values.entrySet()) {
            result.put(entry.getKey(), entry.getValue().rawValue());
        }
        return result;
    }

    private int configuredRelationLimit(
            EntityVersionConfiguration.SnapshotScope scope) {
        Integer value = scope.getLimits() == null
                ? null : scope.getLimits().getMaxRowsPerRelation();
        return Math.min(HARD_MAX_ROWS_PER_RELATION,
                value == null ? HARD_MAX_ROWS_PER_RELATION : value);
    }

    private int effectiveRelationLimit(
            EntityVersionConfiguration.SnapshotScope scope,
            EntityVersionConfiguration.RelationScope relation) {
        int override = relation.getMaxRows() == null
                ? HARD_MAX_ROWS_PER_RELATION : relation.getMaxRows();
        return Math.min(configuredRelationLimit(scope),
                Math.min(HARD_MAX_ROWS_PER_RELATION, override));
    }

    private int configuredTotalLimit(
            EntityVersionConfiguration.SnapshotScope scope) {
        Integer value = scope.getLimits() == null
                ? null : scope.getLimits().getMaxRowsPerVersion();
        return Math.min(HARD_MAX_ROWS_PER_VERSION,
                value == null ? HARD_MAX_ROWS_PER_VERSION : value);
    }

    private long configuredByteLimit(
            EntityVersionConfiguration.SnapshotScope scope) {
        Long value = scope.getLimits() == null
                ? null : scope.getLimits().getMaxBytesPerVersion();
        return Math.min(HARD_MAX_BYTES_PER_VERSION,
                value == null ? HARD_MAX_BYTES_PER_VERSION : value);
    }

    private BusinessConflictException limitFailure(
            String relationName,
            int actual,
            int limit) {
        return new BusinessConflictException(
                "ENTITY_VERSION_SCOPE_LIMIT_EXCEEDED",
                "关系 " + relationName + " 的记录数 " + actual
                        + " 超过上限 " + limit);
    }

    /**
     * 按拓扑顺序校验冻结路径仍指向当前精确发布定义。
     *
     * <p>旧一层发布没有指纹时保留 historyId 校验；任何多层发布缺少路径或指纹都
     * fail-closed，防止配置损坏后降级成一层捕获。</p>
     */
    private void requireFrozenScopeCurrent(
            EntityVersionConfiguration.SnapshotScope scope) {
        Map<String, EntityPublishedSnapshot> currentByNode =
                new LinkedHashMap<>();
        Map<String, EntityVersionConfiguration.RelationScope> frozenByNode =
                new LinkedHashMap<>();
        EntityPublishedSnapshot root = requireCurrentRelease(
                scope.getRoot().getEntityCode(),
                scope.getRoot().getEntityReleaseId(),
                scope.getRoot().getEntitySchemaHash(),
                "根实体 " + scope.getRoot().getEntityName());
        currentByNode.put("ROOT", root);
        List<EntityVersionConfiguration.RelationScope> ordered =
                safe(scope.getRelations()).stream()
                        .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                        .sorted(java.util.Comparator
                                .comparing((EntityVersionConfiguration.RelationScope item) ->
                                        item.getDepth() == null ? 1 : item.getDepth())
                                .thenComparing(
                                        EntityVersionConfiguration.RelationScope::getNodeCode))
                        .toList();
        for (EntityVersionConfiguration.RelationScope relation : ordered) {
            String parentNodeCode = normalizedParentNode(relation);
            EntityPublishedSnapshot parent = currentByNode.get(parentNodeCode);
            if (parent == null) {
                throw new BusinessConflictException(
                        "ENTITY_VERSION_SCOPE_PATH_INVALID",
                        "冻结路径父节点不存在: " + relation.getNodeCode()
                                + " -> " + parentNodeCode);
            }
            if (!Objects.equals(parent.getEntityCode(),
                    relation.getParentEntityCode())
                    && StringUtils.hasText(relation.getParentEntityCode())) {
                throw stale("关系 " + relation.getRelationName()
                        + " 的父实体与冻结路径不一致");
            }
            boolean strictRelationPin = StringUtils.hasText(
                    relation.getRelationDefinitionHash())
                    || !safe(relation.getRelationPath()).isEmpty();
            EntityRelation frozenRelation = safe(parent.getRelations()).stream()
                    .filter(item -> item != null
                            && !Boolean.FALSE.equals(item.getEnabled())
                            && item.getOwnershipType()
                                    == EntityRelation.OwnershipType.COMPOSITION
                            && Objects.equals(item.getRelationCode(),
                                    relation.getRelationCode()))
                    .findFirst()
                    .orElse(null);
            if (strictRelationPin && frozenRelation == null) {
                throw stale("关系 " + relation.getRelationName()
                        + " 已从父实体发布中消失");
            }
            EntityPublishedSnapshot child = requireCurrentRelease(
                    relation.getChildEntityCode(),
                    relation.getEntityReleaseId(),
                    relation.getEntitySchemaHash(),
                    "关系 " + relation.getRelationName());
            if (StringUtils.hasText(relation.getRelationDefinitionHash())
                    && frozenRelation != null
                    && !Objects.equals(
                            relation.getRelationDefinitionHash(),
                            relationDefinitionHash(
                                    parent, child, frozenRelation))) {
                throw stale("关系 " + relation.getRelationName()
                        + " 的发布定义已变化");
            }
            if (!"ROOT".equals(parentNodeCode)
                    && (safe(relation.getRelationPath()).isEmpty()
                            || !StringUtils.hasText(
                                    relation.getRelationDefinitionHash())
                            || !StringUtils.hasText(
                                    relation.getEntitySchemaHash()))) {
                throw new BusinessConflictException(
                        "ENTITY_VERSION_SCOPE_PATH_INVALID",
                        "多层固化范围缺少完整冻结路径: "
                                + relation.getNodeCode());
            }
            List<EntityVersionConfiguration.RelationPathStep> path =
                    safe(relation.getRelationPath());
            if (!path.isEmpty()) {
                EntityVersionConfiguration.RelationScope parentScope =
                        frozenByNode.get(parentNodeCode);
                List<EntityVersionConfiguration.RelationPathStep> prefix =
                        parentScope == null
                                ? List.of()
                                : safe(parentScope.getRelationPath());
                EntityVersionConfiguration.RelationPathStep last =
                        path.get(path.size() - 1);
                if (path.size() != prefix.size() + 1
                        || !path.subList(0, prefix.size()).equals(prefix)
                        || !pathStepMatchesRelation(last, relation)) {
                    throw new BusinessConflictException(
                            "ENTITY_VERSION_SCOPE_PATH_INVALID",
                            "固化范围路径与节点定义不一致: "
                                    + relation.getNodeCode());
                }
            }
            currentByNode.put(relation.getNodeCode(), child);
            frozenByNode.put(relation.getNodeCode(), relation);
        }
    }

    /** 路径末跳必须完整等于节点冻结定义，不能只比较 relationCode。 */
    private boolean pathStepMatchesRelation(
            EntityVersionConfiguration.RelationPathStep step,
            EntityVersionConfiguration.RelationScope relation) {
        return Objects.equals(step.getParentNodeCode(),
                    normalizedParentNode(relation))
                && Objects.equals(step.getNodeCode(), relation.getNodeCode())
                && Objects.equals(step.getRelationCode(),
                    relation.getRelationCode())
                && Objects.equals(step.getSourceEntityCode(),
                    relation.getParentEntityCode())
                && Objects.equals(step.getSourceEntityReleaseId(),
                    relation.getParentEntityReleaseId())
                && Objects.equals(step.getSourceEntityReleaseVersion(),
                    relation.getParentEntityReleaseVersion())
                && Objects.equals(step.getSourceEntitySchemaHash(),
                    relation.getParentEntitySchemaHash())
                && Objects.equals(step.getTargetEntityCode(),
                    relation.getChildEntityCode())
                && Objects.equals(step.getTargetEntityReleaseId(),
                    relation.getEntityReleaseId())
                && Objects.equals(step.getTargetEntityReleaseVersion(),
                    relation.getEntityReleaseVersion())
                && Objects.equals(step.getTargetEntitySchemaHash(),
                    relation.getEntitySchemaHash())
                && Objects.equals(step.getDataKey(), relation.getDataKey())
                && Objects.equals(step.getChildRefFieldCode(),
                    relation.getChildRefFieldCode())
                && Objects.equals(step.getRelationType(),
                    relation.getRelationType())
                && Objects.equals(step.getRelationDefinitionHash(),
                    relation.getRelationDefinitionHash());
    }

    private EntityPublishedSnapshot requireCurrentRelease(
            String entityCode,
            String frozenReleaseId,
            String frozenSchemaHash,
            String label) {
        EntityPublishedSnapshot current = publishedSnapshotService
                .getLatestByEntityCode(entityCode);
        if (current == null || !Objects.equals(
                frozenReleaseId, current.getHistoryId())) {
            throw stale(label + " 的实体发布版本已变化");
        }
        if (StringUtils.hasText(frozenSchemaHash)
                && !Objects.equals(
                        frozenSchemaHash, entitySchemaHash(current))) {
            throw stale(label + " 的发布内容指纹已变化");
        }
        return current;
    }

    private BusinessConflictException stale(String message) {
        return new BusinessConflictException(
                "ENTITY_VERSION_SCOPE_STALE",
                message + "，请重新保存数据版本配置后再固化");
    }

    private String normalizedParentNode(
            EntityVersionConfiguration.RelationScope relation) {
        return StringUtils.hasText(relation.getParentNodeCode())
                ? relation.getParentNodeCode().trim() : "ROOT";
    }

    private String recordIdentity(String entityCode, String recordId) {
        return String.valueOf(entityCode) + ":" + String.valueOf(recordId);
    }

    private String entitySchemaHash(EntityPublishedSnapshot snapshot) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("historyId", snapshot.getHistoryId());
        material.put("entityId", snapshot.getEntityId());
        material.put("entityCode", snapshot.getEntityCode());
        material.put("version", snapshot.getVersion());
        material.put("fields", safe(snapshot.getFields()));
        material.put("relationsSnapshotAvailable",
                snapshot.isRelationsSnapshotAvailable());
        material.put("relations", safe(snapshot.getRelations()));
        return hash(material);
    }

    private String relationDefinitionHash(
            EntityPublishedSnapshot parent,
            EntityPublishedSnapshot child,
            EntityRelation relation) {
        Map<String, Object> material = new LinkedHashMap<>();
        material.put("sourceEntityCode", parent.getEntityCode());
        material.put("sourceReleaseId", parent.getHistoryId());
        material.put("targetEntityCode", child.getEntityCode());
        material.put("targetReleaseId", child.getHistoryId());
        material.put("relationCode", relation.getRelationCode());
        material.put("dataKey", firstText(
                relation.getDataKey(), relation.getParentFieldCode(),
                relation.getRelationCode()));
        material.put("childRefFieldCode", relation.getChildRefFieldCode());
        material.put("relationType", relation.getRelationType() == null
                ? null : relation.getRelationType().name());
        material.put("ownershipType", relation.getOwnershipType() == null
                ? null : relation.getOwnershipType().name());
        material.put("cascadeDelete", relation.getCascadeDelete());
        material.put("required", relation.getRequired());
        material.put("enabled", relation.getEnabled());
        return hash(material);
    }

    private long serializedSize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value).length;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实体版本快照大小计算失败", exception);
        }
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value != null
                    && StringUtils.hasText(
                            String.valueOf(value))) {
                return String.valueOf(value);
            }
        }
        return null;
    }

    private record SystemField(
            String code,
            String name,
            String type) {
    }

    public record SnapshotCapture(
            Map<String, Object> document,
            String hash,
            String entityReleaseId,
            Integer entityReleaseVersion) {
    }

    public record SnapshotCaptureV2(
            Map<String, Object> rootDocument,
            String dataHash,
            String presentationHash,
            String scopeHash,
            String entityReleaseId,
            Integer entityReleaseVersion,
            List<DatasetCapture> datasets,
            int relationRowCount,
            long sizeBytes) {
    }

    public record DatasetCapture(
            String nodeCode,
            String relationCode,
            String relationName,
            String entityCode,
            String entityName,
            String entityReleaseId,
            Integer entityReleaseVersion,
            Map<String, Object> selector,
            Map<String, Object> presentation,
            List<DatasetRowCapture> rows,
            String dataHash,
            String presentationHash,
            String scopeHash) {
    }

    public record DatasetRowCapture(
            String recordId,
            String recordTitle,
            Integer rowOrder,
            Map<String, FrozenValue> values,
            String rowHash) {
    }

    /** 捕获过程中保留原始节点及祖先身份，仅在内存中用于继续遍历和环检测。 */
    private record CaptureNodeRecord(
            Map<String, Object> record,
            String recordId,
            java.util.Set<String> ancestry) {
    }

    /** 一个已解析但尚未冻结展示值的子节点。 */
    private record CapturedRawRow(
            Map<String, Object> record,
            String recordId,
            String parentRecordId,
            java.util.Set<String> ancestry) {
    }

    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
