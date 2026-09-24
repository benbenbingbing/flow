package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldOptionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityFieldOption;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 把版本范围解析为不可变的组成关系树。
 *
 * <p>旧配置没有 {@code parentNodeCode} 时仍按 ROOT 的一层关系处理。新配置可把
 * 任一已冻结节点作为父节点；配置保存生效后，捕获只使用这里冻结的实体版本、
 * 关系路径和字段结构，不再通过 SUB_FORM/SUB_LIST 或当前实体定义推断范围。</p>
 */
@Component
@RequiredArgsConstructor
public class EntityVersionScopeFreezer {

    private static final Set<String> FILTER_SYSTEM_FIELDS = Set.of(
            "id", "entityCode", "name", "code",
            "status", "processInstanceId", "processStartTime",
            "processEndTime", "processStatus", "currentTaskId", "currentTaskName",
            "currentTaskAssignee", "submitterId", "submitterName",
            "deptId", "deptName", "submitTime", "create_time",
            "update_time", "create_by", "update_by");

    private final EntityPublishedSnapshotService snapshotService;
    private final ObjectMapper objectMapper;
    private final EntityFieldOptionMapper optionMapper;

    private static final int HARD_MAX_DEPTH = 8;
    private static final int HARD_MAX_SCOPE_NODES = 64;

    /**
     * 在写入当前配置前冻结实体版本、关系路径、字段展示和范围摘要。
     *
     * @param source 已经结构校验的候选配置
     * @return 与运行时实体定义解耦的冻结副本
     * @throws IllegalArgumentException 范围引用无效或超过治理上限时抛出
     */
    public EntityVersionConfiguration freeze(
            EntityVersionConfiguration source) {
        EntityVersionConfiguration document = copy(source);
        // 冻结副本和当前已发布实体快照，避免后续修改原配置或发布新版本改变本次范围。
        EntityPublishedSnapshot rootSnapshot = snapshotService
                .getLatestByEntityCode(document.getEntityCode());
        EntityVersionConfiguration.SnapshotScope scope =
                document.getSnapshotScope();
        if (scope == null) {
            scope = new EntityVersionConfiguration.SnapshotScope();
            document.setSnapshotScope(scope);
        }
        EntityVersionConfiguration.ScopeNode rootNode = scope.getRoot();
        if (rootNode == null) {
            rootNode = new EntityVersionConfiguration.ScopeNode();
            scope.setRoot(rootNode);
        }
        rootNode.setNodeCode("ROOT");
        rootNode.setEntityCode(rootSnapshot.getEntityCode());
        rootNode.setEntityName(rootSnapshot.getEntityName());
        rootNode.setEntityReleaseId(rootSnapshot.getHistoryId());
        rootNode.setEntityReleaseVersion(rootSnapshot.getVersion());
        rootNode.setEntitySchemaHash(entitySchemaHash(rootSnapshot));
        rootNode.setFields(selectFields(
                rootSnapshot.getFields(),
                rootNode.getFieldMode(),
                rootNode.getFieldCodes()));

        List<EntityVersionConfiguration.RelationScope> requested = safe(
                scope.getRelations()).stream()
                .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                .toList();
        // 用户配置只能收紧硬上限，不能放大固化范围导致捕获和恢复成本失控。
        int configuredNodeLimit = scope.getLimits() == null
                || scope.getLimits().getMaxScopeNodes() == null
                ? HARD_MAX_SCOPE_NODES
                : Math.min(HARD_MAX_SCOPE_NODES,
                        scope.getLimits().getMaxScopeNodes());
        if (requested.size() > configuredNodeLimit) {
            throw new IllegalArgumentException(
                    "固化范围节点数 " + requested.size()
                            + " 超过上限 " + configuredNodeLimit);
        }

        Map<String, EntityVersionConfiguration.RelationScope> pending =
                new LinkedHashMap<>();
        for (EntityVersionConfiguration.RelationScope relationScope
                : requested) {
            String nodeCode = firstText(
                    text(relationScope.getNodeCode()),
                    StringUtils.hasText(relationScope.getRelationCode())
                            ? "REL_" + relationScope.getRelationCode() : null);
            if (!StringUtils.hasText(nodeCode)
                    || "ROOT".equalsIgnoreCase(nodeCode)
                    || pending.putIfAbsent(nodeCode, relationScope) != null) {
                throw new IllegalArgumentException(
                        "固化范围节点编码为空、重复或使用保留值: " + nodeCode);
            }
            relationScope.setNodeCode(nodeCode);
            relationScope.setParentNodeCode(firstText(
                    text(relationScope.getParentNodeCode()), "ROOT"));
        }

        Map<String, EntityPublishedSnapshot> nodeSnapshots =
                new LinkedHashMap<>();
        nodeSnapshots.put("ROOT", rootSnapshot);
        Map<String, EntityVersionConfiguration.RelationScope> frozenByCode =
                new LinkedHashMap<>();
        List<EntityVersionConfiguration.RelationScope> frozenRelations =
                new ArrayList<>();
        // 仅在父节点已冻结后解析子关系；无法推进说明路径缺失或存在环。
        while (!pending.isEmpty()) {
            boolean progressed = false;
            for (Map.Entry<String,
                    EntityVersionConfiguration.RelationScope> entry
                    : new ArrayList<>(pending.entrySet())) {
                EntityVersionConfiguration.RelationScope relationScope =
                        entry.getValue();
                String parentNodeCode = relationScope.getParentNodeCode();
                EntityPublishedSnapshot parent = nodeSnapshots.get(
                        parentNodeCode);
                if (parent == null) {
                    continue;
                }
                EntityRelation relation = compositionRelations(parent).get(
                        relationScope.getRelationCode());
                if (relation == null) {
                    throw new IllegalArgumentException(
                            "固化范围节点 " + relationScope.getNodeCode()
                                    + " 引用了父节点未发布的组成关系: "
                                    + relationScope.getRelationCode());
                }
                EntityPublishedSnapshot child = snapshotService
                        .getLatestByEntityCode(relation.getChildEntityCode());
                validateAndNormalizeFilter(
                        relationScope.getFilter(),
                        child.getFields(),
                        relation.getRelationName());
                freezeRelationNode(
                        relationScope,
                        parentNodeCode,
                        parent,
                        child,
                        relation,
                        frozenByCode.get(parentNodeCode));
                int configuredDepth = scope.getLimits() == null
                        || scope.getLimits().getMaxDepth() == null
                        ? HARD_MAX_DEPTH
                        : Math.min(HARD_MAX_DEPTH,
                                scope.getLimits().getMaxDepth());
                if (relationScope.getDepth() > configuredDepth) {
                    throw new IllegalArgumentException(
                            "固化范围路径深度 " + relationScope.getDepth()
                                    + " 超过上限 " + configuredDepth
                                    + ": " + relationScope.getNodeCode());
                }
                nodeSnapshots.put(relationScope.getNodeCode(), child);
                frozenByCode.put(relationScope.getNodeCode(), relationScope);
                frozenRelations.add(relationScope);
                pending.remove(entry.getKey());
                progressed = true;
            }
            if (!progressed) {
                throw new IllegalArgumentException(
                        "固化范围存在父节点缺失或节点环: "
                                + String.join(",", pending.keySet()));
            }
        }
        frozenRelations.sort(Comparator
                .comparing(EntityVersionConfiguration.RelationScope::getDepth)
                .thenComparing(
                        EntityVersionConfiguration.RelationScope::getNodeCode));
        scope.setRelations(frozenRelations);
        // 摘要绑定最终范围内容，后续捕获和恢复据此检测配置漂移。
        scope.setScopeHash(hash(scopeMaterial(scope)));
        document.setSchemaVersion(2);
        document.setRelationOptions(List.of());
        document.setFieldOptions(List.of());
        return document;
    }

    /**
     * 冻结一个关系节点及从 ROOT 到它的完整路径。
     *
     * <p>路径被冗余保存是有意的：捕获、锁根和恢复预演都能仅依赖单个节点完成
     * fail-closed 校验，不需要运行时再拼接当前关系。</p>
     *
     * @param scope 作用域，作为 {@code relationPathStep} 的输入影响后续处理
     * @param parentNodeCode 父级节点编码，后续用于处理{@code freeze}关系节点时定位或关联目标
     * @param parent 父级，作为 {@code scope.setParentEntityCode} 的输入影响后续处理
     * @param child 子级，作为 {@code scope.setEntityCode} 的输入影响后续处理
     * @param relation 关系，作为 {@code scope.setRelationName} 的输入影响后续处理
     * @param parentScope 父级作用域，作为 {@code scope.setDepth} 的输入影响后续处理
     */
    private void freezeRelationNode(
            EntityVersionConfiguration.RelationScope scope,
            String parentNodeCode,
            EntityPublishedSnapshot parent,
            EntityPublishedSnapshot child,
            EntityRelation relation,
            EntityVersionConfiguration.RelationScope parentScope) {
        scope.setParentNodeCode(parentNodeCode);
        scope.setDepth(parentScope == null ? 1 : parentScope.getDepth() + 1);
        scope.setParentEntityCode(parent.getEntityCode());
        scope.setParentEntityName(parent.getEntityName());
        scope.setParentEntityReleaseId(parent.getHistoryId());
        scope.setParentEntityReleaseVersion(parent.getVersion());
        scope.setParentEntitySchemaHash(entitySchemaHash(parent));
        scope.setEntityCode(child.getEntityCode());
        scope.setEntityName(child.getEntityName());
        scope.setEntityReleaseId(child.getHistoryId());
        scope.setEntityReleaseVersion(child.getVersion());
        scope.setEntitySchemaHash(entitySchemaHash(child));
        scope.setRelationName(relation.getRelationName());
        scope.setChildEntityCode(child.getEntityCode());
        scope.setChildEntityName(child.getEntityName());
        scope.setDataKey(firstText(
                relation.getDataKey(),
                relation.getParentFieldCode(),
                relation.getRelationCode()));
        scope.setChildRefFieldCode(relation.getChildRefFieldCode());
        scope.setRelationType(relation.getRelationType() == null
                ? null : relation.getRelationType().name());
        scope.setRelationDefinitionHash(relationDefinitionHash(
                parent, child, relation));
        scope.setFields(selectFields(
                child.getFields(),
                scope.getFieldMode(),
                scope.getFieldCodes()));

        EntityVersionConfiguration.RelationPathStep step =
                relationPathStep(scope);
        List<EntityVersionConfiguration.RelationPathStep> path =
                new ArrayList<>();
        if (parentScope != null) {
            // 路径步骤是可序列化 DTO；必须深拷贝，避免子节点修改前缀时污染父节点冻结定义。
            path.addAll(safe(parentScope.getRelationPath()).stream()
                    .map(item -> objectMapper.convertValue(
                            item,
                            EntityVersionConfiguration.RelationPathStep.class))
                    .toList());
        }
        path.add(step);
        scope.setRelationPath(path);
    }

    /**
     * 处理关系路径步骤，并将结果传给后续步骤。
     *
     * @param scope 作用域，作为 {@code step.setParentNodeCode} 的输入影响后续处理
     * @return 处理后的关系路径步骤结果，供调用方继续处理
     */
    private EntityVersionConfiguration.RelationPathStep relationPathStep(
            EntityVersionConfiguration.RelationScope scope) {
        EntityVersionConfiguration.RelationPathStep step =
                new EntityVersionConfiguration.RelationPathStep();
        step.setParentNodeCode(scope.getParentNodeCode());
        step.setNodeCode(scope.getNodeCode());
        step.setRelationCode(scope.getRelationCode());
        step.setRelationName(scope.getRelationName());
        step.setSourceEntityCode(scope.getParentEntityCode());
        step.setSourceEntityReleaseId(scope.getParentEntityReleaseId());
        step.setSourceEntityReleaseVersion(
                scope.getParentEntityReleaseVersion());
        step.setSourceEntitySchemaHash(scope.getParentEntitySchemaHash());
        step.setTargetEntityCode(scope.getChildEntityCode());
        step.setTargetEntityReleaseId(scope.getEntityReleaseId());
        step.setTargetEntityReleaseVersion(scope.getEntityReleaseVersion());
        step.setTargetEntitySchemaHash(scope.getEntitySchemaHash());
        step.setDataKey(scope.getDataKey());
        step.setChildRefFieldCode(scope.getChildRefFieldCode());
        step.setRelationType(scope.getRelationType());
        step.setRelationDefinitionHash(scope.getRelationDefinitionHash());
        return step;
    }

    /**
     * 为管理端读取结果补充当前可选字段和直接组成关系。
     * 这些派生选项不属于运行时配置，保存时会被剔除。
     *
     * @param document 当前配置或新建默认配置
     * @return 补充了管理选项的同一配置对象
     */
    public EntityVersionConfiguration enrichManagementOptions(
            EntityVersionConfiguration document) {
        if (document == null || !StringUtils.hasText(document.getEntityCode())) {
            return document;
        }
        EntityPublishedSnapshot root = snapshotService
                .getLatestByEntityCode(document.getEntityCode());
        document.setFieldOptions(selectFields(
                root.getFields(), "ALL_PUBLISHED", List.of()));
        List<EntityVersionConfiguration.RelationOption> options =
                new ArrayList<>();
        for (EntityRelation relation : safe(root.getRelations())) {
            if (relation == null
                    || Boolean.FALSE.equals(relation.getEnabled())
                    || relation.getOwnershipType()
                            != EntityRelation.OwnershipType.COMPOSITION
                    || !StringUtils.hasText(relation.getRelationCode())
                    || !StringUtils.hasText(relation.getChildEntityCode())) {
                continue;
            }
            EntityPublishedSnapshot child = snapshotService
                    .getLatestByEntityCode(relation.getChildEntityCode());
            EntityVersionConfiguration.RelationOption option =
                    new EntityVersionConfiguration.RelationOption();
            option.setRelationCode(relation.getRelationCode());
            option.setRelationName(relation.getRelationName());
            option.setChildEntityCode(child.getEntityCode());
            option.setChildEntityName(child.getEntityName());
            option.setRelationType(relation.getRelationType() == null
                    ? null : relation.getRelationType().name());
            option.setFields(selectFields(
                    child.getFields(), "ALL_PUBLISHED", List.of()));
            options.add(option);
        }
        document.setRelationOptions(options);
        return document;
    }

    /**
     * 查询字段；查询结果供调用方展示或继续处理。
     *
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param fieldMode 字段模式标识，决定后续字段采用的处理分支
     * @param selectedCodes 已选择编码集合，供本方法查询字段时使用
     * @return 实体版本配置集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private List<EntityVersionConfiguration.FieldPresentation> selectFields(
            List<EntityField> fields,
            String fieldMode,
            List<String> selectedCodes) {
        Set<String> selected = new LinkedHashSet<>(
                selectedCodes == null ? List.of() : selectedCodes);
        boolean all = !"SELECTED".equalsIgnoreCase(fieldMode);
        List<EntityVersionConfiguration.FieldPresentation> result =
                new ArrayList<>();
        for (EntityField field : safe(fields)) {
            if (field == null
                    || !StringUtils.hasText(field.getFieldCode())
                    || field.getFieldType() == EntityField.FieldType.SUB_FORM
                    || field.getFieldType() == EntityField.FieldType.SUB_LIST
                    || (!all && !selected.contains(field.getFieldCode()))) {
                continue;
            }
            EntityVersionConfiguration.FieldPresentation presentation =
                    new EntityVersionConfiguration.FieldPresentation();
            presentation.setFieldCode(field.getFieldCode());
            presentation.setFieldName(field.getFieldName());
            presentation.setFieldLabel(field.getFieldName());
            presentation.setFieldType(field.getFieldType() == null
                    ? "UNKNOWN" : field.getFieldType().name());
            presentation.setSortOrder(field.getSortOrder() == null
                    ? 0 : field.getSortOrder());
            presentation.setRenderHint(renderHint(field.getFieldType()));
            presentation.setDictType(field.getDictType());
            presentation.setOptionLabels(readOptionLabels(field));
            result.add(presentation);
        }
        result.sort(Comparator
                .comparing(EntityVersionConfiguration.FieldPresentation::getSortOrder)
                .thenComparing(EntityVersionConfiguration.FieldPresentation::getFieldCode));
        if (!all) {
            Set<String> resolved = result.stream()
                    .map(EntityVersionConfiguration.FieldPresentation::getFieldCode)
                    .collect(java.util.stream.Collectors.toSet());
            for (String code : selected) {
                if (!resolved.contains(code)) {
                    throw new IllegalArgumentException(
                            "固化范围引用了未发布或不可持久化字段: " + code);
                }
            }
        }
        return result;
    }

    /**
     * 校验与规范化过滤；不满足约束时阻止后续处理。
     *
     * @param filter 过滤，供本方法校验与规范化过滤时使用
     * @param childFields 子级字段，供本方法校验与规范化过滤时使用
     * @param relationName 关系名称，后续用于校验与规范化过滤时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateAndNormalizeFilter(
            EntityVersionConfiguration.FixedFilter filter,
            List<EntityField> childFields,
            String relationName) {
        if (filter == null || filter.getConditions() == null) {
            return;
        }
        Set<String> allowed = new LinkedHashSet<>(FILTER_SYSTEM_FIELDS);
        for (EntityField field : safe(childFields)) {
            if (field != null && StringUtils.hasText(field.getFieldCode())) {
                allowed.add(field.getFieldCode());
            }
        }
        for (EntityVersionConfiguration.FilterCondition condition
                : filter.getConditions()) {
            String code = text(condition.getFieldCode());
            if (code != null && code.startsWith("data.")) {
                code = text(code.substring("data.".length()));
            }
            if (code == null || !allowed.contains(code)) {
                throw new IllegalArgumentException(
                        "关系 " + firstText(relationName, "未命名关系")
                                + " 的固定过滤字段未发布或不存在: "
                                + condition.getFieldCode());
            }
            condition.setFieldCode(code);
        }
    }

    /**
     * 整理作用域材料数据，供调用方遍历或继续处理。
     *
     * @param scope 作用域，作为 {@code result.put} 的输入影响后续处理
     * @return 作用域材料键值结果，供调用方继续处理
     */
    private Map<String, Object> scopeMaterial(
            EntityVersionConfiguration.SnapshotScope scope) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("root", nodeScopeMaterial(scope.getRoot()));
        result.put("relations", safe(scope.getRelations()).stream()
                .map(this::relationScopeMaterial)
                .toList());
        result.put("limits", scope.getLimits());
        return result;
    }

    /**
     * 整理节点作用域材料数据，供调用方遍历或继续处理。
     *
     * @param node 节点，作为 {@code result.put} 的输入影响后续处理
     * @return 节点作用域材料键值结果，供调用方继续处理
     */
    private Map<String, Object> nodeScopeMaterial(
            EntityVersionConfiguration.ScopeNode node) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("nodeCode", node.getNodeCode());
        result.put("entityCode", node.getEntityCode());
        result.put("entityReleaseId", node.getEntityReleaseId());
        result.put("entityReleaseVersion", node.getEntityReleaseVersion());
        result.put("entitySchemaHash", node.getEntitySchemaHash());
        result.put("fieldCodes", safe(node.getFields()).stream()
                .map(EntityVersionConfiguration.FieldPresentation::getFieldCode)
                .toList());
        return result;
    }

    /**
     * 整理关系作用域材料数据，供调用方遍历或继续处理。
     *
     * @param relation 关系，作为 {@code nodeScopeMaterial} 的输入影响后续处理
     * @return 关系作用域材料键值结果，供调用方继续处理
     */
    private Map<String, Object> relationScopeMaterial(
            EntityVersionConfiguration.RelationScope relation) {
        Map<String, Object> result = nodeScopeMaterial(relation);
        result.put("parentNodeCode", relation.getParentNodeCode());
        result.put("depth", relation.getDepth());
        result.put("parentEntityCode", relation.getParentEntityCode());
        result.put("parentEntityReleaseId",
                relation.getParentEntityReleaseId());
        result.put("parentEntityReleaseVersion",
                relation.getParentEntityReleaseVersion());
        result.put("parentEntitySchemaHash",
                relation.getParentEntitySchemaHash());
        result.put("relationCode", relation.getRelationCode());
        result.put("relationDefinitionHash",
                relation.getRelationDefinitionHash());
        result.put("relationPath", relation.getRelationPath());
        result.put("childEntityCode", relation.getChildEntityCode());
        result.put("dataKey", relation.getDataKey());
        result.put("childRefFieldCode", relation.getChildRefFieldCode());
        result.put("relationType", relation.getRelationType());
        result.put("filter", relation.getFilter());
        result.put("maxRows", relation.getMaxRows());
        return result;
    }

    /**
     * 整理组合关系集合数据，供调用方遍历或继续处理。
     *
     * @param snapshot 快照，供本方法处理组合关系集合时使用
     * @return 组合关系集合键值结果，供调用方继续处理
     */
    private Map<String, EntityRelation> compositionRelations(
            EntityPublishedSnapshot snapshot) {
        Map<String, EntityRelation> result = new LinkedHashMap<>();
        for (EntityRelation relation : safe(snapshot.getRelations())) {
            if (relation != null
                    && StringUtils.hasText(relation.getRelationCode())
                    && !Boolean.FALSE.equals(relation.getEnabled())
                    && relation.getOwnershipType()
                            == EntityRelation.OwnershipType.COMPOSITION) {
                result.put(relation.getRelationCode(), relation);
            }
        }
        return result;
    }

    /**
     * 实体指纹覆盖身份、字段和关系，避免只固定 historyId 时历史行被改写。
     *
     * @param snapshot 快照，作为 {@code material.put} 的输入影响后续处理
     * @return 处理后的实体结构哈希文本，供调用方比较或展示
     */
    private String entitySchemaHash(EntityPublishedSnapshot snapshot) {
        return hash(EntityVersionFingerprint.entitySchemaMaterial(snapshot));
    }

    /**
     * 关系摘要只包含决定遍历、归属和基数的服务端字段。
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
        return hash(EntityVersionFingerprint.relationDefinitionMaterial(parent, child, relation));
    }

    /**
     * 读取选项{@code labels}；查询结果供调用方展示或继续处理。
     *
     * @param field 字段，供本方法读取选项{@code labels}时使用
     * @return 选项{@code labels}键值结果，供调用方继续处理
     */
    private Map<String, String> readOptionLabels(EntityField field) {
        Map<String, String> structured = new LinkedHashMap<>();
        if (StringUtils.hasText(field.getId())) {
            for (EntityFieldOption option
                    : optionMapper.findByFieldId(field.getId())) {
                if (StringUtils.hasText(option.getOptionValue())) {
                    structured.put(option.getOptionValue(),
                            firstText(option.getOptionLabel(),
                                    option.getOptionValue()));
                }
            }
        }
        if (!structured.isEmpty()) {
            return structured;
        }
        String json = field.getOptionsJson();
        if (!StringUtils.hasText(json)) {
            return structured;
        }
        try {
            List<Map<String, Object>> values = objectMapper.readValue(
                    json, new TypeReference<>() { });
            Map<String, String> result = new LinkedHashMap<>();
            for (Map<String, Object> value : values) {
                String code = firstText(
                        text(value.get("value")),
                        text(value.get("optionValue")),
                        text(value.get("code")));
                String label = firstText(
                        text(value.get("label")),
                        text(value.get("optionLabel")),
                        text(value.get("name")), code);
                if (code != null) {
                    result.put(code, label);
                }
            }
            return result;
        } catch (JsonProcessingException ignored) {
            return new LinkedHashMap<>();
        }
    }

    /**
     * 生成{@code render}{@code hint}文本，供后续匹配或展示。
     *
     * @param type 类型标识，决定后续{@code render}{@code hint}采用的处理分支
     * @return 处理后的{@code render}{@code hint}文本，供调用方比较或展示
     */
    private String renderHint(EntityField.FieldType type) {
        if (type == null) {
            return "TEXT";
        }
        return switch (type) {
            case TEXT, RICH_TEXT -> "LONG_TEXT";
            case SELECT, MULTI_SELECT, RADIO, CHECKBOX -> "TAG";
            case FILE, IMAGE -> "ATTACHMENT";
            case USER, DEPT, REFERENCE, MULTI_REFERENCE -> "REFERENCE";
            default -> "TEXT";
        };
    }

    /**
     * 复制实体版本作用域{@code freezer}；结果供后续流程传递或持久化。
     *
     * @param source 待复制实体版本作用域{@code freezer}的原始输入，结果供调用方继续使用
     * @return 复制后的实体版本作用域{@code freezer}结果，供调用方继续处理
     */
    private EntityVersionConfiguration copy(EntityVersionConfiguration source) {
        return objectMapper.convertValue(source, EntityVersionConfiguration.class);
    }

    /**
     * 生成哈希文本，供后续匹配或展示。
     *
     * @param value 待处理哈希的原始输入，结果供调用方继续使用
     * @return 处理后的哈希文本，供调用方比较或展示
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private String hash(Object value) {
        try {
            return EntityVersionFingerprint.hash(objectMapper, value);
        } catch (Exception exception) {
            throw new IllegalStateException("数据版本范围摘要计算失败", exception);
        }
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        if (value == null) {
            return null;
        }
        String result = String.valueOf(value).trim();
        return result.isEmpty() ? null : result;
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
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 整理安全数据，供调用方遍历或继续处理。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 实体版本作用域{@code freezer}集合，供调用方遍历或展示
     */
    private <T> List<T> safe(List<T> values) {
        return values == null ? List.of() : values;
    }
}
