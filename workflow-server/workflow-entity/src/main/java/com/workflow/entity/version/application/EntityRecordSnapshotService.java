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
            new SystemField("name", "名称", "STRING"),
            new SystemField("code", "编码", "STRING"),
            new SystemField("status", "实体状态", "STATUS"),
            new SystemField("processInstanceId", "流程实例ID", "STRING"),
            new SystemField("processStartTime", "流程开始时间", "DATETIME"),
            new SystemField("processEndTime", "流程结束时间", "DATETIME"),
            new SystemField("processStatus", "流程状态", "SELECT"),
            new SystemField("currentTaskId", "当前任务ID", "STRING"),
            new SystemField("currentTaskName", "当前任务名称", "STRING"),
            new SystemField("currentTaskAssignee", "当前任务办理人", "USER"),
            new SystemField("submitterId", "提交人ID", "USER"),
            new SystemField("submitterName", "提交人", "STRING"),
            new SystemField("deptId", "所属部门ID", "DEPT"),
            new SystemField("deptName", "所属部门", "STRING"),
            new SystemField("submitTime", "提交时间", "DATETIME"),
            new SystemField("create_time", "创建时间", "DATETIME"),
            new SystemField("update_time", "更新时间", "DATETIME"),
            new SystemField("create_by", "创建人", "USER"),
            new SystemField("update_by", "更新人", "USER"));

    private final EntityPublishedSnapshotService publishedSnapshotService;
    private final EntityFieldOptionMapper optionMapper;
    private final EntityStatusMapper statusMapper;
    private final SysDictItemService dictItemService;
    private final SysUserService userService;
    private final SysOrganizationService organizationService;
    private final ObjectMapper objectMapper;

    /**
     * 捕获实体记录快照；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param aggregateRecord 聚合对象记录，作为 {@code deepCopy} 的输入影响后续处理
     * @param deletedSnapshot 已删除快照，作为 {@code document.put} 的输入影响后续处理
     * @return 捕获后的实体记录快照结果，供调用方继续处理
     */
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
     *
     * @param configuration 配置内容，决定后续{@code v2}的处理规则
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param aggregateRecord 聚合对象记录，作为 {@code deepCopy} 的输入影响后续处理
     * @param deletedSnapshot 已删除快照，作为 {@code rootDocument.put} 的输入影响后续处理
     * @return 捕获后的{@code v2}结果，供调用方继续处理
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
                                row.get("name"),
                                row.get("code"),
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

    /**
     * 处理预览{@code v2}，并将结果传给后续步骤。
     *
     * @param configuration 配置内容，决定后续预览{@code v2}的处理规则
     * @param aggregateRecord 聚合对象记录，作为 {@code deepCopy} 的输入影响后续处理
     * @return 处理后的预览{@code v2}结果，供调用方继续处理
     */
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

    /**
     * RELATED_MUTATION 触发判定与实际捕获共用同一固定过滤语义。
     *
     * @param row 行，作为 {@code matchesFilter} 的输入影响后续处理
     * @param filter 过滤，供本方法判断是否匹配固定过滤时使用
     * @return 固定过滤条件成立时为 true，否则为 false
     */
    public boolean matchesFixedFilter(
            Map<String, Object> row,
            EntityVersionConfiguration.FixedFilter filter) {
        return matchesFilter(row == null ? Map.of() : row, filter);
    }

    /**
     * 捕获系统字段；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param record 记录，供本方法捕获系统字段时使用
     * @return 实体记录快照集合，供调用方遍历或展示
     */
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

    /**
     * 捕获业务字段；结果供调用方的后续步骤使用。
     *
     * @param field 字段，作为 {@code customData.get} 的输入影响后续处理
     * @param customData 自定义数据，供本方法捕获业务字段时使用
     * @return 业务字段键值结果，供调用方继续处理
     */
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

    /**
     * 整理字段数据，供调用方遍历或继续处理。
     *
     * @param code 编码，后续用于处理字段时定位或关联目标
     * @param name 名称，后续用于处理字段时匹配或展示
     * @param type 类型标识，决定后续字段采用的处理分支
     * @param value 待处理字段的原始输入，结果供调用方继续使用
     * @param displayValue 展示值，作为 {@code result.put} 的输入影响后续处理
     * @param group 分组，作为 {@code result.put} 的输入影响后续处理
     * @param sortOrder 排序顺序，作为 {@code result.put} 的输入影响后续处理
     * @return 字段键值结果，供调用方继续处理
     */
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

    /**
     * 处理展示系统值，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param type 类型标识，决定后续展示系统值采用的处理分支
     * @param value 待处理展示系统值的原始输入，结果供调用方继续使用
     * @return 处理后的展示系统值结果，供调用方继续处理
     */
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

    /**
     * 处理展示字段值，并将结果传给后续步骤。
     *
     * @param field 字段，作为 {@code displayDictionary} 的输入影响后续处理
     * @param value 待处理展示字段值的原始输入，结果供调用方继续使用
     * @return 处理后的展示字段值结果，供调用方继续处理
     */
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

    /**
     * 处理展示用户集合，并将结果传给后续步骤。
     *
     * @param value 待处理展示用户集合的原始输入，结果供调用方继续使用
     * @return 处理后的展示用户集合结果，供调用方继续处理
     */
    private Object displayUsers(Object value) {
        List<String> values = stringValues(value);
        if (values.isEmpty()) {
            return value;
        }
        return values.size() == 1
                ? userService.getDisplayName(values.get(0))
                : userService.getDisplayNames(values);
    }

    /**
     * 处理展示{@code departments}，并将结果传给后续步骤。
     *
     * @param value 待处理展示{@code departments}的原始输入，结果供调用方继续使用
     * @return 处理后的展示{@code departments}结果，供调用方继续处理
     */
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

    /**
     * 处理展示{@code dictionary}，并将结果传给后续步骤。
     *
     * @param dictCode 字典编码，后续用于处理展示{@code dictionary}时定位或关联目标
     * @param value 待处理展示{@code dictionary}的原始输入，结果供调用方继续使用
     * @return 处理后的展示{@code dictionary}结果，供调用方继续处理
     */
    private Object displayDictionary(
            String dictCode,
            Object value) {
        Map<String, String> labels = new LinkedHashMap<>();
        flattenDictItems(
                dictItemService.getItemTreeByDictCode(dictCode),
                labels);
        return displayMapped(value, labels);
    }

    /**
     * 处理展示选项，并将结果传给后续步骤。
     *
     * @param field 字段，供本方法处理展示选项时使用
     * @param value 待处理展示选项的原始输入，结果供调用方继续使用
     * @return 处理后的展示选项结果，供调用方继续处理
     */
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

    /**
     * 处理展示{@code mapped}，并将结果传给后续步骤。
     *
     * @param value 待处理展示{@code mapped}的原始输入，结果供调用方继续使用
     * @param labels {@code labels}，供本方法处理展示{@code mapped}时使用
     * @return 处理后的展示{@code mapped}结果，供调用方继续处理
     */
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

    /**
     * 处理关系展示，并将结果传给后续步骤。
     *
     * @param value 待处理关系展示的原始输入，结果供调用方继续使用
     * @return 处理后的关系展示结果，供调用方继续处理
     */
    private Object relationDisplay(Object value) {
        if (value instanceof Collection<?> values) {
            return values.stream()
                    .map(this::relationDisplay)
                    .toList();
        }
        if (value instanceof Map<?, ?> map) {
            for (String key : List.of(
                    "displayName", "label", "name",
                    "code", "id")) {
                if (map.get(key) != null) {
                    return map.get(key);
                }
            }
        }
        return value;
    }

    /**
     * 判断是否关系；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否关系时使用
     * @return 关系条件成立时为 true，否则为 false
     */
    private boolean isRelation(EntityField field) {
        return switch (field.getFieldType()) {
            case REFERENCE, MULTI_REFERENCE, SUB_FORM -> true;
            default -> false;
        };
    }

    /**
     * 判断是否选项字段；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否选项字段时使用
     * @return 选项字段条件成立时为 true，否则为 false
     */
    private boolean isOptionField(EntityField field) {
        return switch (field.getFieldType()) {
            case SELECT, MULTI_SELECT, RADIO, CHECKBOX -> true;
            default -> false;
        };
    }

    /**
     * 处理{@code flatten}字典条目，并将结果传给后续步骤。
     *
     * @param items 条目，供本方法处理{@code flatten}字典条目时使用
     * @param labels {@code labels}，供本方法处理{@code flatten}字典条目时使用
     */
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

    /**
     * 整理字符串值集合数据，供调用方遍历或继续处理。
     *
     * @param value 待处理字符串值集合的原始输入，结果供调用方继续使用
     * @return 实体记录快照集合，供调用方遍历或展示
     */
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

    /**
     * 读取选项；查询结果供调用方展示或继续处理。
     *
     * @param json JSON，作为 {@code objectMapper.readValue} 的输入影响后续处理
     * @return 实体记录快照集合，供调用方遍历或展示
     */
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

    /**
     * 整理映射数据，供调用方遍历或继续处理。
     *
     * @param value 待处理映射的原始输入，结果供调用方继续使用
     * @return 映射键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        if (value instanceof Map<?, ?> source) {
            return (Map<String, Object>) source;
        }
        return Map.of();
    }

    /**
     * 整理{@code deep}副本数据，供调用方遍历或继续处理。
     *
     * @param value 待处理{@code deep}副本的原始输入，结果供调用方继续使用
     * @return {@code deep}副本键值结果，供调用方继续处理
     */
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

    /**
     * 生成哈希文本，供后续匹配或展示。
     *
     * @param material 材料，供本方法处理哈希时使用
     * @return 处理后的哈希文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 整理根{@code presentations}数据，供调用方遍历或继续处理。
     *
     * @param root 根，作为 {@code result.putAll} 的输入影响后续处理
     * @return 根{@code presentations}键值结果，供调用方继续处理
     */
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

    /**
     * 整理索引展示数据，供调用方遍历或继续处理。
     *
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @return 索引展示键值结果，供调用方继续处理
     */
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

    /**
     * 整理展示数据，供调用方遍历或继续处理。
     *
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @return 展示键值结果，供调用方继续处理
     */
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

    /**
     * 处理{@code frozen}值，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param field 字段，作为 {@code flattenDictItems} 的输入影响后续处理
     * @param raw 待处理{@code frozen}值的原始输入，结果供调用方继续使用
     * @return 处理后的{@code frozen}值结果，供调用方继续处理
     */
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

    /**
     * 处理根值，并将结果传给后续步骤。
     *
     * @param record 记录，供本方法处理根值时使用
     * @param customData 自定义数据，供本方法处理根值时使用
     * @param fieldCode 字段编码，后续用于处理根值时定位或关联目标
     * @return 处理后的根值结果，供调用方继续处理
     */
    private Object rootValue(
            Map<String, Object> record,
            Map<String, Object> customData,
            String fieldCode) {
        return record.containsKey(fieldCode)
                ? record.get(fieldCode) : customData.get(fieldCode);
    }

    /**
     * 整理关系行数据，供调用方遍历或继续处理。
     *
     * @param value 待处理关系行的原始输入，结果供调用方继续使用
     * @param relationType 关系类型标识，决定后续关系行采用的处理分支
     * @return 实体记录快照集合，供调用方遍历或展示
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
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

    /**
     * 判断是否匹配过滤；判断结果决定调用方的后续分支。
     *
     * @param row 行，作为 {@code matchesCondition} 的输入影响后续处理
     * @param filter 过滤，作为 {@code equalsIgnoreCase} 的输入影响后续处理
     * @return 过滤条件成立时为 true，否则为 false
     */
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

    /**
     * 判断是否匹配条件；判断结果决定调用方的后续分支。
     *
     * @param row 行，作为 {@code rowValue} 的输入影响后续处理
     * @param condition 筛选条件，后续与权限约束合并为查询条件
     * @return 条件条件成立时为 true，否则为 false
     */
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

    /**
     * 判断{@code equivalent}条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，作为 {@code booleanValue} 的输入影响后续处理
     * @param right 右侧，作为 {@code booleanValue} 的输入影响后续处理
     * @return {@code equivalent}条件成立时为 true，否则为 false
     */
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

    /**
     * 将输入解析为布尔值，供后续条件判断使用。
     *
     * @param value 待处理布尔值值的原始输入，结果供调用方继续使用
     * @return 处理后的布尔值值结果，供调用方继续处理
     */
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

    /**
     * 判断布尔值{@code word}条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理布尔值{@code word}的原始输入，结果供调用方继续使用
     * @return 布尔值{@code word}条件成立时为 true，否则为 false
     */
    private boolean booleanWord(Object value) {
        String text = String.valueOf(value).trim();
        return "true".equalsIgnoreCase(text)
                || "false".equalsIgnoreCase(text);
    }

    /**
     * 处理路径，并将结果传给后续步骤。
     *
     * @param row 行，供本方法处理路径时使用
     * @param fieldCode 字段编码，后续用于处理路径时定位或关联目标
     * @return 处理后的路径结果，供调用方继续处理
     */
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

    /**
     * 处理行值，并将结果传给后续步骤。
     *
     * @param row 行，作为 {@code path} 的输入影响后续处理
     * @param fieldCode 字段编码，后续用于处理行值时定位或关联目标
     * @return 处理后的行值结果，供调用方继续处理
     */
    private Object rowValue(
            Map<String, Object> row,
            String fieldCode) {
        Object direct = path(row, fieldCode);
        if (direct != null || row.containsKey(fieldCode)) {
            return direct;
        }
        return path(map(row.get("data")), fieldCode);
    }

    /**
     * 整理集合数据，供调用方遍历或继续处理。
     *
     * @param value 待处理集合的原始输入，结果供调用方继续使用
     * @return {@code collection<?>}集合，供调用方遍历或展示
     */
    private Collection<?> collection(Object value) {
        return value instanceof Collection<?> values
                ? values : value == null ? List.of() : List.of(value);
    }

    /**
     * 比较实体记录快照；结果供调用方的后续步骤使用。
     *
     * @param left 左侧，作为 {@code java.math.BigDecimal} 的输入影响后续处理
     * @param right 右侧，供本方法比较实体记录快照时使用
     * @return 比较后的实体记录快照结果，供调用方继续处理
     */
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

    /**
     * 整理原始值集合数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 原始值集合键值结果，供调用方继续处理
     */
    private Map<String, Object> rawValues(Map<String, FrozenValue> values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, FrozenValue> entry : values.entrySet()) {
            result.put(entry.getKey(), entry.getValue().rawValue());
        }
        return result;
    }

    /**
     * 处理已配置关系上限，并将结果传给后续步骤。
     *
     * @param scope 作用域，供本方法处理已配置关系上限时使用
     * @return 处理后的已配置关系上限结果，供调用方继续处理
     */
    private int configuredRelationLimit(
            EntityVersionConfiguration.SnapshotScope scope) {
        Integer value = scope.getLimits() == null
                ? null : scope.getLimits().getMaxRowsPerRelation();
        return Math.min(HARD_MAX_ROWS_PER_RELATION,
                value == null ? HARD_MAX_ROWS_PER_RELATION : value);
    }

    /**
     * 处理有效关系上限，并将结果传给后续步骤。
     *
     * @param scope 作用域，作为 {@code Math.min} 的输入影响后续处理
     * @param relation 关系，供本方法处理有效关系上限时使用
     * @return 处理后的有效关系上限结果，供调用方继续处理
     */
    private int effectiveRelationLimit(
            EntityVersionConfiguration.SnapshotScope scope,
            EntityVersionConfiguration.RelationScope relation) {
        int override = relation.getMaxRows() == null
                ? HARD_MAX_ROWS_PER_RELATION : relation.getMaxRows();
        return Math.min(configuredRelationLimit(scope),
                Math.min(HARD_MAX_ROWS_PER_RELATION, override));
    }

    /**
     * 处理已配置总数上限，并将结果传给后续步骤。
     *
     * @param scope 作用域，供本方法处理已配置总数上限时使用
     * @return 处理后的已配置总数上限结果，供调用方继续处理
     */
    private int configuredTotalLimit(
            EntityVersionConfiguration.SnapshotScope scope) {
        Integer value = scope.getLimits() == null
                ? null : scope.getLimits().getMaxRowsPerVersion();
        return Math.min(HARD_MAX_ROWS_PER_VERSION,
                value == null ? HARD_MAX_ROWS_PER_VERSION : value);
    }

    /**
     * 处理已配置{@code byte}上限，并将结果传给后续步骤。
     *
     * @param scope 作用域，供本方法处理已配置{@code byte}上限时使用
     * @return 处理后的已配置{@code byte}上限结果，供调用方继续处理
     */
    private long configuredByteLimit(
            EntityVersionConfiguration.SnapshotScope scope) {
        Long value = scope.getLimits() == null
                ? null : scope.getLimits().getMaxBytesPerVersion();
        return Math.min(HARD_MAX_BYTES_PER_VERSION,
                value == null ? HARD_MAX_BYTES_PER_VERSION : value);
    }

    /**
     * 构造上限失败异常，供调用方区分失败原因。
     *
     * @param relationName 关系名称，后续用于处理上限失败时匹配或展示
     * @param actual 实际，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @param limit 上限参数，用于限制后续查询范围和返回数量
     * @return 处理后的上限失败结果，供调用方继续处理
     */
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
     *
     * @param scope 作用域，作为 {@code requireCurrentRelease} 的输入影响后续处理
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

    /**
     * 路径末跳必须完整等于节点冻结定义，不能只比较 relationCode。
     *
     * @param step 步骤，供本方法处理路径步骤匹配关系时使用
     * @param relation 关系，供本方法处理路径步骤匹配关系时使用
     * @return 路径步骤匹配关系条件成立时为 true，否则为 false
     */
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

    /**
     * 校验并获取当前发布版本；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param frozenReleaseId {@code frozen}发布版本ID，后续用于校验并获取当前发布版本时定位或关联目标
     * @param frozenSchemaHash {@code frozen}结构哈希，供本方法校验并获取当前发布版本时使用
     * @param label 标签，后续用于校验并获取当前发布版本时匹配或展示
     * @return 校验并获取后的当前发布版本结果，供调用方继续处理
     */
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

    /**
     * 构造{@code stale}异常，供调用方区分失败原因。
     *
     * @param message 消息，作为 {@code BusinessConflictException} 的输入影响后续处理
     * @return 处理后的{@code stale}结果，供调用方继续处理
     */
    private BusinessConflictException stale(String message) {
        return new BusinessConflictException(
                "ENTITY_VERSION_SCOPE_STALE",
                message + "，请重新保存数据版本配置后再固化");
    }

    /**
     * 生成规范化父级节点文本，供后续匹配或展示。
     *
     * @param relation 关系，供本方法处理规范化父级节点时使用
     * @return 处理后的规范化父级节点文本，供调用方比较或展示
     */
    private String normalizedParentNode(
            EntityVersionConfiguration.RelationScope relation) {
        return StringUtils.hasText(relation.getParentNodeCode())
                ? relation.getParentNodeCode().trim() : "ROOT";
    }

    /**
     * 记录身份；供后续追溯或审计使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 记录后的身份文本，供调用方比较或展示
     */
    private String recordIdentity(String entityCode, String recordId) {
        return String.valueOf(entityCode) + ":" + String.valueOf(recordId);
    }

    /**
     * 生成实体结构哈希文本，供后续匹配或展示。
     *
     * @param snapshot 快照，作为 {@code material.put} 的输入影响后续处理
     * @return 处理后的实体结构哈希文本，供调用方比较或展示
     */
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

    /**
     * 生成关系定义哈希文本，供后续匹配或展示。
     *
     * @param parent 父级，作为 {@code material.put} 的输入影响后续处理
     * @param child 子级，作为 {@code material.put} 的输入影响后续处理
     * @param relation 关系，作为 {@code material.put} 的输入影响后续处理
     * @return 处理后的关系定义哈希文本，供调用方比较或展示
     */
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

    /**
     * 处理{@code serialized}大小，并将结果传给后续步骤。
     *
     * @param value 待处理{@code serialized}大小的原始输入，结果供调用方继续使用
     * @return 处理后的{@code serialized}大小结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private long serializedSize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value).length;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("实体版本快照大小计算失败", exception);
        }
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
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

    /**
     * 封装系统字段的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param code 业务编码，供后续匹配和引用
     * @param name 展示名称，供界面或日志识别
     * @param type 类型标识，决定后续系统字段采用的处理分支
     */
    private record SystemField(
            String code,
            String name,
            String type) {
    }

    /**
     * 封装快照捕获的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param document 文档，保存在对象中供后续校验、查询或展示
     * @param hash 哈希，保存在对象中供后续校验、查询或展示
     * @param entityReleaseId 实体发布版本ID，后续用于处理快照捕获时定位或关联目标
     * @param entityReleaseVersion 实体发布版本，保存在对象中供后续校验、查询或展示
     */
    public record SnapshotCapture(
            Map<String, Object> document,
            String hash,
            String entityReleaseId,
            Integer entityReleaseVersion) {
    }

    /**
     * 封装快照捕获{@code v2}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param rootDocument 根文档，保存在对象中供后续校验、查询或展示
     * @param dataHash 数据哈希，保存在对象中供后续校验、查询或展示
     * @param presentationHash 展示哈希，保存在对象中供后续校验、查询或展示
     * @param scopeHash 作用域哈希，保存在对象中供后续校验、查询或展示
     * @param entityReleaseId 实体发布版本ID，后续用于处理快照捕获{@code v2}时定位或关联目标
     * @param entityReleaseVersion 实体发布版本，保存在对象中供后续校验、查询或展示
     * @param datasets {@code datasets}，保存在对象中供后续校验、查询或展示
     * @param relationRowCount 关系行数量，保存在对象中供后续校验、查询或展示
     * @param sizeBytes 大小字节，保存在对象中供后续校验、查询或展示
     */
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

    /**
     * 封装数据集捕获的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param nodeCode 节点编码，后续用于处理数据集捕获时定位或关联目标
     * @param relationCode 关系编码，后续用于处理数据集捕获时定位或关联目标
     * @param relationName 关系名称，后续用于处理数据集捕获时匹配或展示
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityName 实体名称，后续用于处理数据集捕获时匹配或展示
     * @param entityReleaseId 实体发布版本ID，后续用于处理数据集捕获时定位或关联目标
     * @param entityReleaseVersion 实体发布版本，保存在对象中供后续校验、查询或展示
     * @param selector {@code selector}，保存在对象中供后续校验、查询或展示
     * @param presentation 展示，保存在对象中供后续校验、查询或展示
     * @param rows 行，保存在对象中供后续校验、查询或展示
     * @param dataHash 数据哈希，保存在对象中供后续校验、查询或展示
     * @param presentationHash 展示哈希，保存在对象中供后续校验、查询或展示
     * @param scopeHash 作用域哈希，保存在对象中供后续校验、查询或展示
     */
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

    /**
     * 封装数据集行捕获的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param recordTitle 记录{@code title}，后续用于处理数据集行捕获时匹配或展示
     * @param rowOrder 行顺序，保存在对象中供后续校验、查询或展示
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param rowHash 行哈希，保存在对象中供后续校验、查询或展示
     */
    public record DatasetRowCapture(
            String recordId,
            String recordTitle,
            Integer rowOrder,
            Map<String, FrozenValue> values,
            String rowHash) {
    }

    /**
     * 捕获过程中保留原始节点及祖先身份，仅在内存中用于继续遍历和环检测。
     *
     * @param record 记录，保存在对象中供后续校验、查询或展示
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param ancestry {@code ancestry}，保存在对象中供后续校验、查询或展示
     */
    private record CaptureNodeRecord(
            Map<String, Object> record,
            String recordId,
            java.util.Set<String> ancestry) {
    }

    /**
     * 一个已解析但尚未冻结展示值的子节点。
     *
     * @param record 记录，保存在对象中供后续校验、查询或展示
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param parentRecordId 父级记录ID，后续用于处理{@code captured}原始行时定位或关联目标
     * @param ancestry {@code ancestry}，保存在对象中供后续校验、查询或展示
     */
    private record CapturedRawRow(
            Map<String, Object> record,
            String recordId,
            String parentRecordId,
            java.util.Set<String> ancestry) {
    }

    /**
     * 整理安全数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 实体记录快照集合，供调用方遍历或展示
     */
    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
