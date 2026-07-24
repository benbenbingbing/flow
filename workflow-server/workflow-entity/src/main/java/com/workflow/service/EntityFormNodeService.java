package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.common.RevisionConflictException;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.EntityFormNodeCreateRequest;
import com.workflow.dto.EntityFormNodePatchRequest;
import com.workflow.dto.EntityFormNodeReorderRequest;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormNode;
import com.workflow.entity.EntityRelation;
import com.workflow.entity.UiConfigRelease;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityFormNodeMapper;
import com.workflow.mapper.EntityRelationMapper;
import com.workflow.mapper.UiConfigReleaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 实体表单节点服务，负责表单节点的创建、补丁、排序、删除、差异替换与树校验。
 *
 * <p>支持基于乐观锁的节点变更、父子关系与嵌套深度校验、子表单发布版本引用锁定、
 * 组件模板与扩展引用校验，以及通过差异比对批量重建表单节点树。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityFormNodeService {

    /** 节点排序步长，用于 orderKey 的稀疏分布以支持插入。 */
    public static final long ORDER_STEP = 1_000_000L;
    /** 表单节点最大嵌套深度。 */
    public static final int MAX_DEPTH = 8;

    private static final String ACTIVE_NODE_KEY_UNIQUE_INDEX =
            "uk_entity_form_node_active_key";
    private static final Pattern NODE_KEY =
            Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,99}");
    private static final Set<String> NODE_TYPES = Set.of(
            "SECTION", "GRID", "TAB_SET", "TAB", "COLLAPSE",
            "TEXT", "FIELD", "SUB_FORM", "REPEATER", "ACTION_SLOT");
    private static final Set<String> CONTAINER_TYPES = Set.of(
            "SECTION", "GRID", "TAB_SET", "TAB", "COLLAPSE", "SUB_FORM", "REPEATER");
    private static final Set<String> STANDARD_CONTAINER_CHILD_TYPES = Set.of(
            "SECTION", "GRID", "TAB_SET", "COLLAPSE",
            "TEXT", "FIELD", "SUB_FORM", "REPEATER", "ACTION_SLOT");
    private static final Map<String, Set<String>> ALLOWED_CHILD_TYPES = Map.of(
            "SECTION", STANDARD_CONTAINER_CHILD_TYPES,
            "GRID", STANDARD_CONTAINER_CHILD_TYPES,
            "TAB_SET", Set.of("TAB"),
            "TAB", STANDARD_CONTAINER_CHILD_TYPES,
            "COLLAPSE", STANDARD_CONTAINER_CHILD_TYPES,
            "SUB_FORM", STANDARD_CONTAINER_CHILD_TYPES,
            "REPEATER", STANDARD_CONTAINER_CHILD_TYPES);
    private static final Set<String> BINDING_TYPES = Set.of(
            "ENTITY_FIELD", "RELATION", "COMPUTED", "CONTEXT", "NONE");
    private static final Set<String> BINDABLE_NODE_TYPES = Set.of(
            "FIELD", "SUB_FORM", "REPEATER");
    private static final Set<String> COMPONENT_NODE_TYPES = Set.of(
            "FIELD", "SUB_FORM", "REPEATER");
    private static final Set<String> RULE_NODE_TYPES = Set.of(
            "FIELD", "SUB_FORM", "REPEATER", "ACTION_SLOT");
    private static final Set<String> DATA_SOURCE_NODE_TYPES = Set.of(
            "FIELD", "SUB_FORM", "REPEATER");
    private static final Set<String> SUB_FORM_NODE_TYPES = Set.of(
            "SUB_FORM", "REPEATER");
    private static final Set<String> CLEARABLE_PATCH_FIELDS = Set.of(
            "parentId", "bindingRef", "componentName", "componentVersion",
            "snapshotVersion", "childFormId", "childFormReleaseId",
            "childFormReleaseVersion", "rules", "dataSourceBindings",
            "templateId", "templateVersion", "localOverrides");
    private static final Set<String> IMMUTABLE_BOUND_PROP_KEYS = Set.of(
            "fieldId", "fieldCode", "fieldType");
    private static final Set<String> IMMUTABLE_SUB_FORM_CONFIG_KEYS = Set.of(
            "refEntityId", "childEntityId", "relationType",
            "childRefFieldCode", "refFieldCode", "relationCode");
    private static final Set<String> IMMUTABLE_REFERENCE_CONFIG_KEYS = Set.of(
            "refEntityType", "refEntityId", "entityCode");
    private static final Set<String> EDITABLE_LABEL_NODE_TYPES = Set.of(
            "SECTION", "TAB", "COLLAPSE",
            "FIELD", "SUB_FORM", "REPEATER");
    private static final Set<String> DATA_SOURCE_USAGES = Set.of(
            "FORM_INIT", "FIELD_OPTIONS", "FIELD_DEFAULT", "FIELD_COMPUTE",
            "SUBFORM_ROWS", "LIST_QUERY", "LIST_COLUMN", "AFTER_LOAD", "BEFORE_SUBMIT");

    private final EntityFormMapper formMapper;
    private final EntityFormNodeMapper nodeMapper;
    private final EntityRelationMapper relationMapper;
    private final UiConfigReleaseMapper releaseMapper;
    private final EntityDefinitionAccessPolicy entityAccessPolicy;
    private final JsonDocumentCodec codec;

    /**
     * 查询表单的所有节点。
     *
     * @param formId 表单ID
     * @return 节点列表
     */
    public List<EntityFormNode> findByFormId(String formId) {
        requireForm(formId);
        return nodeMapper.findByFormId(formId);
    }

    /**
     * 创建表单节点，校验配置合法性并落库。
     *
     * @param formId  表单ID
     * @param request 节点创建请求
     * @return 创建的节点
     * @throws IllegalArgumentException 配置非法或校验失败时抛出
     * @throws RevisionConflictException 节点 key 唯一冲突时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityFormNode create(String formId, EntityFormNodeCreateRequest request) {
        return createInternal(formId, request, false);
    }

    private EntityFormNode createInternal(
            String formId,
            EntityFormNodeCreateRequest request,
            boolean migrateUnsupported) {
        requireForm(formId);
        EntityFormNode node = new EntityFormNode();
        node.setId(StringUtils.hasText(request.getId())
                ? request.getId().trim()
                : UUID.randomUUID().toString().replace("-", ""));
        node.setFormId(formId);
        node.setParentId(blankToNull(request.getParentId()));
        node.setNodeKey(request.getNodeKey());
        node.setNodeType(normalize(request.getNodeType(), "FIELD"));
        node.setBindingType(normalize(request.getBindingType(), "NONE"));
        node.setBindingRef(blankToNull(request.getBindingRef()));
        node.setComponentName(blankToNull(request.getComponentName()));
        node.setComponentVersion(request.getComponentVersion());
        node.setSnapshotVersion(request.getSnapshotVersion());
        node.setPropsDocument(write(
                normalizeSubFormProps(
                        node.getNodeType(),
                        request.getProps(),
                        request.getChildFormId(),
                        request.getChildFormReleaseId(),
                        request.getChildFormReleaseVersion(),
                        true),
                "表单节点属性"));
        node.setRulesDocument(write(request.getRules(), "表单节点规则"));
        node.setDataSourceBindingsDocument(
                write(request.getDataSourceBindings(), "表单节点数据源绑定"));
        node.setLegacyPropsDocument(write(request.getLegacyProps(), "历史节点属性"));
        node.setOrderKey(request.getOrderKey() == null
                ? nextOrderKey(formId, node.getParentId())
                : request.getOrderKey());
        node.setRevision(1);
        node.setTemplateId(blankToNull(request.getTemplateId()));
        node.setTemplateVersion(request.getTemplateVersion());
        node.setLocalOverridesDocument(
                write(request.getLocalOverrides(), "模板本地覆盖"));
        node.setCreatedAt(LocalDateTime.now());
        node.setUpdatedAt(LocalDateTime.now());
        node.setDeleted(0);
        if (!migrateUnsupported) {
            validateCreateConfiguration(request, node.getNodeType());
        }
        normalizeAndValidateConfiguration(node, migrateUnsupported);
        validateNode(node, null);
        try {
            nodeMapper.insert(node);
        } catch (DataIntegrityViolationException exception) {
            throw translateNodeWriteException(
                    formId, node.getNodeKey(), exception);
        }
        touchForm(formId);
        return node;
    }

    /**
     * 按补丁请求更新节点属性，基于乐观锁更新并校验绑定状态。
     *
     * @param formId   表单ID
     * @param nodeId   节点ID
     * @param request  节点补丁请求
     * @return 更新后的节点
     * @throws IllegalArgumentException 节点不存在、绑定不完整或配置非法时抛出
     * @throws RevisionConflictException  版本冲突时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityFormNode patch(
            String formId,
            String nodeId,
            EntityFormNodePatchRequest request) {
        return patchInternal(
                formId, nodeId, request, PatchMode.USER_PROPERTY);
    }

    private EntityFormNode patchInternal(
            String formId,
            String nodeId,
            EntityFormNodePatchRequest request,
            PatchMode mode) {
        EntityFormNode current = requireNode(formId, nodeId);
        requireExpectedRevision(request.getExpectedRevision(), current);
        if (mode.userFacing() && !hasValidBindingState(current)) {
            throw new IllegalArgumentException(
                    "当前节点绑定配置不完整，请先通过配置迁移修复后再编辑");
        }
        if (mode == PatchMode.USER_PROPERTY) {
            validatePatchConstraints(current, request);
        }
        EntityFormNode updated = copy(current);
        normalizeAndValidateConfiguration(updated, true);
        applyPatch(updated, request);
        if (mode == PatchMode.USER_PROPERTY
                && !Objects.equals(
                        blankToNull(current.getParentId()),
                        blankToNull(updated.getParentId()))) {
            updated.setOrderKey(
                    nextOrderKey(formId, updated.getParentId()));
        }
        normalizeAndValidateConfiguration(updated, false);
        updated.setRevision(current.getRevision() + 1);
        updated.setUpdatedAt(LocalDateTime.now());
        validateNode(updated, current.getId());

        UpdateWrapper<EntityFormNode> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", nodeId)
                .eq("form_id", formId)
                .eq("revision", current.getRevision())
                .eq("deleted", 0)
                .set("parent_id", updated.getParentId())
                .set("node_key", updated.getNodeKey())
                .set("node_type", updated.getNodeType())
                .set("binding_type", updated.getBindingType())
                .set("binding_ref", updated.getBindingRef())
                .set("component_name", updated.getComponentName())
                .set("component_version", updated.getComponentVersion())
                .set("snapshot_version", updated.getSnapshotVersion())
                .set("props_document", updated.getPropsDocument())
                .set("rules_document", updated.getRulesDocument())
                .set("data_source_bindings_document", updated.getDataSourceBindingsDocument())
                .set("legacy_props_document", updated.getLegacyPropsDocument())
                .set("order_key", updated.getOrderKey())
                .set("template_id", updated.getTemplateId())
                .set("template_version", updated.getTemplateVersion())
                .set("local_overrides_document", updated.getLocalOverridesDocument())
                .set("revision", updated.getRevision())
                .set("update_time", updated.getUpdatedAt());
        try {
            if (nodeMapper.update(null, wrapper) != 1) {
                throw conflict(formId, nodeId);
            }
        } catch (DataIntegrityViolationException exception) {
            throw translateNodeWriteException(
                    formId, updated.getNodeKey(), exception);
        }
        touchForm(formId);
        return requireNode(formId, nodeId);
    }

    /**
     * 调整节点在同级中的排序位置，必要时自动重平衡 orderKey。
     *
     * @param formId   表单ID
     * @param nodeId   节点ID
     * @param request  排序请求，指定前后相邻节点
     * @return 更新后的节点
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityFormNode reorder(
            String formId,
            String nodeId,
            EntityFormNodeReorderRequest request) {
        EntityFormNode current = requireNode(formId, nodeId);
        requireExpectedRevision(request.getExpectedRevision(), current);
        String parentId = blankToNull(request.getParentId());
        validateParent(formId, nodeId, parentId);
        long previous = resolveBoundary(
                formId, parentId, request.getPreviousNodeId(), 0L);
        long next = resolveBoundary(
                formId, parentId, request.getNextNodeId(), previous + (ORDER_STEP * 2));
        if (next - previous <= 1) {
            rebalance(formId, parentId);
            previous = resolveBoundary(
                    formId, parentId, request.getPreviousNodeId(), 0L);
            next = resolveBoundary(
                    formId, parentId, request.getNextNodeId(), previous + (ORDER_STEP * 2));
        }
        EntityFormNodePatchRequest patch = new EntityFormNodePatchRequest();
        patch.setExpectedRevision(current.getRevision());
        if (parentId == null) {
            patch.setClearFields(Set.of("parentId"));
        } else {
            patch.setParentId(parentId);
        }
        patch.setOrderKey(previous + ((next - previous) / 2));
        return patchInternal(
                formId, nodeId, patch, PatchMode.USER_REORDER);
    }

    /**
     * 删除表单节点，存在子节点时拒绝删除。
     *
     * @param formId          表单ID
     * @param nodeId          节点ID
     * @param expectedRevision 期望版本号
     * @throws IllegalArgumentException 存在子节点或版本冲突时抛出
     * @throws RevisionConflictException  版本冲突时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(String formId, String nodeId, Integer expectedRevision) {
        EntityFormNode current = requireNode(formId, nodeId);
        requireExpectedRevision(expectedRevision, current);
        boolean hasChildren = nodeMapper.selectCount(
                new LambdaQueryWrapper<EntityFormNode>()
                        .eq(EntityFormNode::getFormId, formId)
                        .eq(EntityFormNode::getParentId, nodeId)
                        .eq(EntityFormNode::getDeleted, 0)) > 0;
        if (hasChildren) {
            throw new IllegalArgumentException("请先删除或移动当前节点的子节点");
        }
        UpdateWrapper<EntityFormNode> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", nodeId)
                .eq("form_id", formId)
                .eq("revision", current.getRevision())
                .eq("deleted", 0)
                .set("deleted", 1)
                .set("revision", current.getRevision() + 1)
                .set("update_time", LocalDateTime.now());
        if (nodeMapper.update(null, wrapper) != 1) {
            throw conflict(formId, nodeId);
        }
        touchForm(formId);
    }

    /**
     * 按差异批量替换表单节点（系统导入模式，不校验表单版本号）。
     *
     * @param formId   表单ID
     * @param incoming 目标节点列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceByDiff(String formId, List<EntityFormNode> incoming) {
        replaceByDiffInternal(
                formId,
                incoming,
                null,
                PatchMode.SYSTEM_IMPORT);
    }

    /**
     * 按差异批量替换表单节点（用户模式，校验表单版本号）。
     *
     * @param formId           表单ID
     * @param incoming         目标节点列表
     * @param expectedRevision 期望的表单版本号
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceByDiff(
            String formId,
            List<EntityFormNode> incoming,
            Integer expectedRevision) {
        replaceByDiffInternal(
                formId,
                incoming,
                expectedRevision,
                PatchMode.USER_PROPERTY);
    }

    private void replaceByDiffInternal(
            String formId,
            List<EntityFormNode> incoming,
            Integer expectedRevision,
            PatchMode mode) {
        if (mode.userFacing()) {
            requireFormForUpdate(formId, expectedRevision);
        } else {
            requireForm(formId);
        }
        List<EntityFormNode> existing = nodeMapper.findByFormId(formId);
        Map<String, EntityFormNode> existingById = new HashMap<>();
        existing.forEach(node -> existingById.put(node.getId(), node));
        Set<String> retained = new LinkedHashSet<>();
        List<EntityFormNode> sources =
                incoming == null ? List.of() : incoming;
        for (EntityFormNode source : sources) {
            if (StringUtils.hasText(source.getId())
                    && existingById.containsKey(source.getId())) {
                retained.add(source.getId());
            }
        }
        long fallbackOrder = ORDER_STEP;
        for (EntityFormNode source : sources) {
            EntityFormNode current = StringUtils.hasText(source.getId())
                    ? existingById.get(source.getId())
                    : null;
            if (current == null) {
                EntityFormNodeCreateRequest request = toCreateRequest(source, fallbackOrder);
                EntityFormNode created = createInternal(
                        formId,
                        request,
                        mode == PatchMode.SYSTEM_IMPORT);
                retained.add(created.getId());
            } else {
                retained.add(current.getId());
                EntityFormNodePatchRequest request =
                        toPatchRequest(source, current, mode);
                if (hasChanges(source, current)
                        || requiresLegacyReleasePin(source)) {
                    patchInternal(formId, current.getId(), request, mode);
                }
            }
            fallbackOrder += ORDER_STEP;
        }
        for (EntityFormNode node
                : missingNodesInDeletionOrder(existing, retained)) {
            delete(formId, node.getId(), node.getRevision());
        }
        validateTree(formId);
    }

    private List<EntityFormNode> missingNodesInDeletionOrder(
            List<EntityFormNode> existing,
            Set<String> retained) {
        Map<String, EntityFormNode> byId = new HashMap<>();
        existing.forEach(node -> byId.put(node.getId(), node));
        Map<String, Integer> depthById = new HashMap<>();
        return existing.stream()
                .filter(node -> !retained.contains(node.getId()))
                .sorted(Comparator
                        .<EntityFormNode>comparingInt(
                                node -> resolveNodeDepth(
                                        node, byId, depthById))
                        .reversed()
                        .thenComparing(EntityFormNode::getId))
                .toList();
    }

    private int resolveNodeDepth(
            EntityFormNode node,
            Map<String, EntityFormNode> byId,
            Map<String, Integer> depthById) {
        Integer cached = depthById.get(node.getId());
        if (cached != null) {
            return cached;
        }
        List<EntityFormNode> path = new ArrayList<>();
        Set<String> visiting = new LinkedHashSet<>();
        EntityFormNode current = node;
        int parentDepth = 0;
        while (current != null) {
            Integer currentDepth = depthById.get(current.getId());
            if (currentDepth != null) {
                parentDepth = currentDepth;
                break;
            }
            if (!visiting.add(current.getId())) {
                throw new IllegalArgumentException(
                        "表单节点父子关系存在循环，无法执行差异删除: "
                                + current.getNodeKey());
            }
            path.add(current);
            current = StringUtils.hasText(current.getParentId())
                    ? byId.get(current.getParentId())
                    : null;
        }
        for (int index = path.size() - 1; index >= 0; index--) {
            parentDepth++;
            depthById.put(path.get(index).getId(), parentDepth);
        }
        return depthById.get(node.getId());
    }

    /**
     * 校验表单节点树结构：父子类型兼容、循环引用、嵌套深度和子表单发布引用图。
     *
     * @param formId 表单ID
     * @throws IllegalArgumentException 树结构非法时抛出
     */
    public void validateTree(String formId) {
        requireForm(formId);
        List<EntityFormNode> nodes = nodeMapper.findByFormId(formId);
        Map<String, EntityFormNode> byId = new HashMap<>();
        nodes.forEach(node -> byId.put(node.getId(), node));
        for (EntityFormNode node : nodes) {
            validateNode(node, node.getId());
            validateParentChildType(node, byId.get(node.getParentId()));
            int depth = 1;
            Set<String> visited = new HashSet<>();
            String parentId = node.getParentId();
            while (StringUtils.hasText(parentId)) {
                if (!visited.add(parentId) || parentId.equals(node.getId())) {
                    throw new IllegalArgumentException("表单节点存在循环引用: " + node.getNodeKey());
                }
                EntityFormNode parent = byId.get(parentId);
                if (parent == null) {
                    throw new IllegalArgumentException("表单节点父级不存在: " + node.getNodeKey());
                }
                if (!CONTAINER_TYPES.contains(parent.getNodeType())) {
                    throw new IllegalArgumentException("非容器节点不能包含子节点: " + parent.getNodeKey());
                }
                parentId = parent.getParentId();
                depth++;
                if (depth > MAX_DEPTH) {
                    throw new IllegalArgumentException(
                            "表单嵌套层级不能超过 " + MAX_DEPTH + " 层");
                }
            }
        }
        validateReferencedForms(formId, nodes);
    }

    private void validateReferencedForms(
            String formId,
            List<EntityFormNode> currentDraftNodes) {
        validateReferencedFormGraph(
                formId,
                referencedFormReleases(currentDraftNodes),
                1,
                new LinkedHashSet<>(),
                new HashMap<>());
    }

    private void validateReferencedFormGraph(
            String formId,
            List<FormReleaseReference> references,
            int depth,
            LinkedHashSet<String> path,
            Map<String, List<FormReleaseReference>> releaseReferenceCache) {
        if (!path.add(formId)) {
            throw new IllegalArgumentException(
                    "子表单发布引用存在循环: "
                            + String.join(" -> ", path)
                            + " -> "
                            + formId);
        }
        for (FormReleaseReference reference : references) {
            if (path.contains(reference.formId())) {
                throw new IllegalArgumentException(
                        "子表单发布引用存在循环: "
                                + String.join(" -> ", path)
                                + " -> "
                                + reference.formId());
            }
            if (depth >= MAX_DEPTH) {
                throw new IllegalArgumentException(
                        "跨表单嵌套层级不能超过 " + MAX_DEPTH + " 层");
            }
            UiConfigRelease release = requireReferencedRelease(reference);
            List<FormReleaseReference> childReferences =
                    releaseReferenceCache.computeIfAbsent(
                            release.getId(),
                            ignored -> referencedFormReleases(release));
            validateReferencedFormGraph(
                    reference.formId(),
                    childReferences,
                    depth + 1,
                    path,
                    releaseReferenceCache);
        }
        path.remove(formId);
    }

    private List<FormReleaseReference> referencedFormReleases(
            List<EntityFormNode> nodes) {
        List<FormReleaseReference> references = new ArrayList<>();
        for (EntityFormNode node : nodes) {
            if (!Set.of("SUB_FORM", "REPEATER").contains(node.getNodeType())) {
                continue;
            }
            FormReleaseReference reference = readFormReleaseReference(
                    read(node.getPropsDocument(), "子表单节点属性"),
                    node.getNodeKey());
            addFormReleaseReference(references, reference);
        }
        return references;
    }

    private List<FormReleaseReference> referencedFormReleases(
            UiConfigRelease release) {
        Map<String, Object> snapshot = codec.readObject(
                release.getSnapshotDocument(), "子表单发布快照");
        Object rawNodes = snapshot.get("nodes");
        if (!(rawNodes instanceof List<?> nodes)) {
            return List.of();
        }
        List<FormReleaseReference> references = new ArrayList<>();
        for (Object rawNode : nodes) {
            if (!(rawNode instanceof Map<?, ?> node)) {
                continue;
            }
            String nodeType = normalize(text(node.get("nodeType")), null);
            if (!Set.of("SUB_FORM", "REPEATER").contains(nodeType)) {
                continue;
            }
            Map<String, Object> props = readSnapshotNodeProps(node);
            addFormReleaseReference(
                    references,
                    readFormReleaseReference(
                            props,
                            text(node.get("nodeKey"))));
        }
        return references;
    }

    private Map<String, Object> readSnapshotNodeProps(Map<?, ?> node) {
        Object propsDocument = node.get("propsDocument");
        if (propsDocument instanceof String document
                && StringUtils.hasText(document)) {
            return codec.readObject(document, "子表单发布节点属性");
        }
        return objectMap(node.get("props"), "子表单发布节点属性");
    }

    private void addFormReleaseReference(
            List<FormReleaseReference> references,
            FormReleaseReference reference) {
        if (reference == null) {
            return;
        }
        boolean exists = references.stream().anyMatch(existing ->
                Objects.equals(existing.formId(), reference.formId())
                        && Objects.equals(existing.releaseId(), reference.releaseId()));
        if (!exists) {
            references.add(reference);
        }
    }

    private Map<String, Object> normalizeSubFormProps(
            String nodeType,
            Map<String, Object> source,
            String explicitFormId,
            String explicitReleaseId,
            Integer explicitReleaseVersion,
            boolean pinLegacyReference) {
        Map<String, Object> props = mutableMap(source);
        if (!Set.of("SUB_FORM", "REPEATER").contains(normalize(nodeType, null))) {
            return props;
        }
        Map<String, Object> componentProps =
                objectMap(props.get("componentProps"), "子表单组件属性");
        Map<String, Object> nestedConfig =
                objectMap(componentProps.get("subFormConfig"), "子表单配置");
        Map<String, Object> directConfig =
                objectMap(props.get("subFormConfig"), "子表单配置");

        String formId = firstText(
                explicitFormId,
                props.get("childFormId"),
                props.get("refFormId"),
                props.get("publishedFormId"),
                directConfig.get("childFormId"),
                directConfig.get("refFormId"),
                directConfig.get("publishedFormId"),
                nestedConfig.get("childFormId"),
                nestedConfig.get("refFormId"),
                nestedConfig.get("publishedFormId"));
        String releaseId = firstText(
                explicitReleaseId,
                props.get("childFormReleaseId"),
                props.get("refFormReleaseId"),
                props.get("publishedFormReleaseId"),
                directConfig.get("childFormReleaseId"),
                directConfig.get("refFormReleaseId"),
                directConfig.get("publishedFormReleaseId"),
                nestedConfig.get("childFormReleaseId"),
                nestedConfig.get("refFormReleaseId"),
                nestedConfig.get("publishedFormReleaseId"));
        Integer releaseVersion = firstInteger(
                explicitReleaseVersion,
                props.get("childFormReleaseVersion"),
                props.get("refFormReleaseVersion"),
                props.get("publishedFormReleaseVersion"),
                directConfig.get("childFormReleaseVersion"),
                directConfig.get("refFormReleaseVersion"),
                directConfig.get("publishedFormReleaseVersion"),
                nestedConfig.get("childFormReleaseVersion"),
                nestedConfig.get("refFormReleaseVersion"),
                nestedConfig.get("publishedFormReleaseVersion"));

        if (!StringUtils.hasText(formId)
                && !StringUtils.hasText(releaseId)
                && releaseVersion == null) {
            return props;
        }

        UiConfigRelease release;
        if (StringUtils.hasText(releaseId)) {
            release = releaseMapper.selectById(releaseId);
        } else {
            if (!pinLegacyReference) {
                throw new IllegalArgumentException(
                        "子表单必须固定 childFormReleaseId 和 childFormReleaseVersion: "
                                + formId);
            }
            if (!StringUtils.hasText(formId)) {
                throw new IllegalArgumentException(
                        "子表单固定发布版本时 childFormId 不能为空");
            }
            release = releaseVersion == null
                    ? releaseMapper.findActive("FORM", formId)
                    : releaseMapper.findReleases("FORM", formId).stream()
                            .filter(candidate ->
                                    Objects.equals(
                                            candidate.getVersion(),
                                            releaseVersion))
                            .findFirst()
                            .orElse(null);
        }
        if (release == null) {
            throw new IllegalArgumentException(
                    "子表单引用的发布版本不存在: "
                            + (StringUtils.hasText(releaseId)
                                    ? releaseId
                                    : formId + "@v" + releaseVersion));
        }
        if (!StringUtils.hasText(formId)) {
            formId = release.getConfigId();
        }
        FormReleaseReference reference = new FormReleaseReference(
                formId,
                release.getId(),
                releaseVersion == null ? release.getVersion() : releaseVersion);
        release = requireReferencedRelease(reference);

        props.put("childFormId", release.getConfigId());
        props.put("refFormId", release.getConfigId());
        props.put("publishedFormId", release.getConfigId());
        props.put("childFormReleaseId", release.getId());
        props.put("refFormReleaseId", release.getId());
        props.put("publishedFormReleaseId", release.getId());
        props.put("childFormReleaseVersion", release.getVersion());
        props.put("refFormReleaseVersion", release.getVersion());
        props.put("publishedFormReleaseVersion", release.getVersion());

        Map<String, Object> normalizedNestedConfig =
                new LinkedHashMap<>(nestedConfig);
        normalizedNestedConfig.put("childFormId", release.getConfigId());
        normalizedNestedConfig.put("refFormId", release.getConfigId());
        normalizedNestedConfig.put("publishedFormId", release.getConfigId());
        normalizedNestedConfig.put("childFormReleaseId", release.getId());
        normalizedNestedConfig.put("refFormReleaseId", release.getId());
        normalizedNestedConfig.put("publishedFormReleaseId", release.getId());
        normalizedNestedConfig.put(
                "childFormReleaseVersion",
                release.getVersion());
        normalizedNestedConfig.put(
                "refFormReleaseVersion",
                release.getVersion());
        normalizedNestedConfig.put(
                "publishedFormReleaseVersion",
                release.getVersion());
        componentProps.put("subFormConfig", normalizedNestedConfig);
        props.put("componentProps", componentProps);
        return props;
    }

    private Map<String, Object> clearSubFormBindings(
            Map<String, Object> source,
            Set<String> clearFields) {
        Map<String, Object> props = mutableMap(source);
        if (clearFields == null || clearFields.isEmpty()) {
            return props;
        }
        Map<String, Object> componentProps =
                objectMap(props.get("componentProps"), "子表单组件属性");
        Map<String, Object> nestedConfig =
                objectMap(componentProps.get("subFormConfig"), "子表单配置");
        if (clearFields.contains("childFormId")) {
            removeFormIdAliases(props);
            removeFormIdAliases(nestedConfig);
        }
        if (clearFields.contains("childFormReleaseId")) {
            removeReleaseIdAliases(props);
            removeReleaseIdAliases(nestedConfig);
        }
        if (clearFields.contains("childFormReleaseVersion")) {
            removeReleaseVersionAliases(props);
            removeReleaseVersionAliases(nestedConfig);
        }
        if (nestedConfig.isEmpty()) {
            componentProps.remove("subFormConfig");
        } else {
            componentProps.put("subFormConfig", nestedConfig);
        }
        if (componentProps.isEmpty()) {
            props.remove("componentProps");
        } else {
            props.put("componentProps", componentProps);
        }
        return props;
    }

    private void removeFormIdAliases(Map<String, Object> value) {
        value.remove("childFormId");
        value.remove("refFormId");
        value.remove("publishedFormId");
    }

    private void removeReleaseIdAliases(Map<String, Object> value) {
        value.remove("childFormReleaseId");
        value.remove("refFormReleaseId");
        value.remove("publishedFormReleaseId");
    }

    private void removeReleaseVersionAliases(Map<String, Object> value) {
        value.remove("childFormReleaseVersion");
        value.remove("refFormReleaseVersion");
        value.remove("publishedFormReleaseVersion");
    }

    private Map<String, Object> normalizeRelationBoundSubFormProps(
            EntityFormNode node) {
        EntityRelation relation = requireBoundRelation(node);
        String expectedNodeType =
                relation.getRelationType()
                        == EntityRelation.RelationType.ONE_TO_ONE
                ? "SUB_FORM"
                : "REPEATER";
        if (!expectedNodeType.equals(
                normalize(node.getNodeType(), null))) {
            throw new IllegalArgumentException(
                    "关系 "
                            + relation.getRelationCode()
                            + " 必须使用 "
                            + expectedNodeType
                            + " 节点");
        }

        Map<String, Object> props =
                mutableMap(read(node.getPropsDocument(), "表单节点属性"));
        Map<String, Object> componentProps =
                objectMap(props.get("componentProps"), "子表单组件属性");
        Map<String, Object> subFormConfig =
                objectMap(componentProps.get("subFormConfig"), "子表单配置");
        putCanonicalRelationValue(
                subFormConfig,
                "relationCode",
                relation.getRelationCode());
        putCanonicalRelationValue(
                subFormConfig,
                "childEntityId",
                relation.getChildEntityId());
        putCanonicalRelationValue(
                subFormConfig,
                "refEntityId",
                relation.getChildEntityId());
        putCanonicalRelationValue(
                subFormConfig,
                "relationType",
                relation.getRelationType() == null
                        ? null
                        : relation.getRelationType().name());
        putCanonicalRelationValue(
                subFormConfig,
                "childRefFieldCode",
                relation.getChildRefFieldCode());
        putCanonicalRelationValue(
                subFormConfig,
                "refFieldCode",
                relation.getChildRefFieldCode());
        componentProps.put("subFormConfig", subFormConfig);
        props.put("componentProps", componentProps);
        return props;
    }

    private void putCanonicalRelationValue(
            Map<String, Object> target,
            String key,
            String expected) {
        Object current = target.get(key);
        if (EntityFormNodePropertyPolicy.meaningful(current)
                && !Objects.equals(
                        String.valueOf(current).trim(),
                        expected)) {
            throw new IllegalArgumentException(
                    "子表单关系属性与实体关系定义不一致: " + key);
        }
        if (StringUtils.hasText(expected)) {
            target.put(key, expected);
        }
    }

    private EntityRelation requireBoundRelation(EntityFormNode node) {
        EntityForm form = requireForm(node.getFormId());
        EntityRelation relation =
                relationMapper.selectActiveByBindingRef(
                        form.getEntityId(),
                        node.getBindingRef());
        if (relation == null) {
            throw new IllegalArgumentException(
                    "表单节点绑定的实体关系不存在或已禁用: "
                            + node.getBindingRef());
        }
        if (relation.getRelationType() == null
                || !StringUtils.hasText(relation.getChildEntityId())
                || !StringUtils.hasText(relation.getChildRefFieldCode())) {
            throw new IllegalArgumentException(
                    "实体关系配置不完整: "
                            + relation.getRelationCode());
        }
        return relation;
    }

    private void validateSubFormReleaseBinding(EntityFormNode node) {
        if (!Set.of("SUB_FORM", "REPEATER").contains(node.getNodeType())) {
            return;
        }
        FormReleaseReference reference = readFormReleaseReference(
                read(node.getPropsDocument(), "子表单节点属性"),
                node.getNodeKey());
        if (reference != null) {
            UiConfigRelease release =
                    requireReferencedRelease(reference);
            validateRelationReleaseEntity(node, release);
        }
    }

    private void validateRelationReleaseEntity(
            EntityFormNode node,
            UiConfigRelease release) {
        if (!"RELATION".equals(
                normalize(node.getBindingType(), "NONE"))) {
            return;
        }
        EntityRelation relation = requireBoundRelation(node);
        EntityForm childForm =
                formMapper.selectById(release.getConfigId());
        if (childForm == null
                || !Objects.equals(
                        relation.getChildEntityId(),
                        childForm.getEntityId())) {
            throw new IllegalArgumentException(
                    "子表单发布版本所属实体与绑定关系不一致: "
                            + release.getConfigId());
        }
    }

    private FormReleaseReference readFormReleaseReference(
            Map<String, Object> props,
            String nodeLabel) {
        if (props == null || props.isEmpty()) {
            return null;
        }
        Map<String, Object> componentProps =
                objectMap(props.get("componentProps"), "子表单组件属性");
        Map<String, Object> nestedConfig =
                objectMap(componentProps.get("subFormConfig"), "子表单配置");
        Map<String, Object> directConfig =
                objectMap(props.get("subFormConfig"), "子表单配置");
        String formId = firstText(
                props.get("childFormId"),
                props.get("refFormId"),
                props.get("publishedFormId"),
                directConfig.get("childFormId"),
                directConfig.get("refFormId"),
                directConfig.get("publishedFormId"),
                nestedConfig.get("childFormId"),
                nestedConfig.get("refFormId"),
                nestedConfig.get("publishedFormId"));
        String releaseId = firstText(
                props.get("childFormReleaseId"),
                props.get("refFormReleaseId"),
                props.get("publishedFormReleaseId"),
                directConfig.get("childFormReleaseId"),
                directConfig.get("refFormReleaseId"),
                directConfig.get("publishedFormReleaseId"),
                nestedConfig.get("childFormReleaseId"),
                nestedConfig.get("refFormReleaseId"),
                nestedConfig.get("publishedFormReleaseId"));
        Integer releaseVersion = firstInteger(
                props.get("childFormReleaseVersion"),
                props.get("refFormReleaseVersion"),
                props.get("publishedFormReleaseVersion"),
                directConfig.get("childFormReleaseVersion"),
                directConfig.get("refFormReleaseVersion"),
                directConfig.get("publishedFormReleaseVersion"),
                nestedConfig.get("childFormReleaseVersion"),
                nestedConfig.get("refFormReleaseVersion"),
                nestedConfig.get("publishedFormReleaseVersion"));
        if (!StringUtils.hasText(formId)
                && !StringUtils.hasText(releaseId)
                && releaseVersion == null) {
            return null;
        }
        if (!StringUtils.hasText(formId)
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null) {
            throw new IllegalArgumentException(
                    "子表单节点必须固定 childFormId、childFormReleaseId "
                            + "和 childFormReleaseVersion: "
                            + (StringUtils.hasText(nodeLabel)
                                    ? nodeLabel
                                    : formId));
        }
        return new FormReleaseReference(
                formId.trim(),
                releaseId.trim(),
                releaseVersion);
    }

    private UiConfigRelease requireReferencedRelease(
            FormReleaseReference reference) {
        UiConfigRelease release = releaseMapper.selectById(reference.releaseId());
        if (release == null
                || !"FORM".equalsIgnoreCase(release.getConfigType())
                || !Objects.equals(reference.formId(), release.getConfigId())) {
            throw new IllegalArgumentException(
                    "子表单发布版本与表单不匹配: "
                            + reference.formId()
                            + "@"
                            + reference.releaseId());
        }
        if (!Objects.equals(reference.releaseVersion(), release.getVersion())) {
            throw new IllegalArgumentException(
                    "子表单发布版本号不匹配: "
                            + reference.formId()
                            + " 期望 v"
                            + reference.releaseVersion()
                            + "，实际 v"
                            + release.getVersion());
        }
        if (!StringUtils.hasText(release.getSnapshotDocument())) {
            throw new IllegalArgumentException(
                    "子表单发布快照为空: "
                            + reference.formId()
                            + "@v"
                            + reference.releaseVersion());
        }
        return release;
    }

    private Map<String, Object> mutableMap(Map<String, Object> source) {
        return source == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source);
    }

    private Map<String, Object> objectMap(Object value, String label) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, item) -> result.put(String.valueOf(key), item));
            return result;
        }
        if (value instanceof String document && StringUtils.hasText(document)) {
            return codec.readObject(document, label);
        }
        return new LinkedHashMap<>();
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text.trim();
            }
        }
        return null;
    }

    private Integer firstInteger(Object... values) {
        for (Object value : values) {
            if (value instanceof Number number) {
                return number.intValue();
            }
            if (value != null && StringUtils.hasText(String.valueOf(value))) {
                try {
                    return Integer.valueOf(String.valueOf(value).trim());
                } catch (NumberFormatException exception) {
                    throw new IllegalArgumentException(
                            "子表单发布版本号格式不正确: " + value);
                }
            }
        }
        return null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private record FormReleaseReference(
            String formId,
            String releaseId,
            Integer releaseVersion) {
    }

    private void validateCreateConfiguration(
            EntityFormNodeCreateRequest request,
            String nodeType) {
        EntityFormNodePropertyPolicy.normalizeProps(
                nodeType, request.getProps(), false);
        EntityFormNodePropertyPolicy.normalizeRules(
                nodeType,
                request.getRules(),
                request.getProps(),
                false);
        EntityFormNodePropertyPolicy.normalizeDataSourceBindings(
                nodeType, request.getDataSourceBindings());
        EntityFormNodePropertyPolicy.validateExtension(
                nodeType,
                request.getComponentName(),
                request.getComponentVersion(),
                request.getSnapshotVersion());
        if (!EntityFormNodePropertyPolicy.supportsChildForm(nodeType)
                && (StringUtils.hasText(request.getChildFormId())
                || StringUtils.hasText(request.getChildFormReleaseId())
                || request.getChildFormReleaseVersion() != null)) {
            throw new IllegalArgumentException(
                    nodeType + " 节点不支持子表单发布引用");
        }
        EntityFormNodePropertyPolicy.validateBinding(
                nodeType,
                normalize(request.getBindingType(), "NONE"),
                blankToNull(request.getBindingRef()));
        EntityFormNodePropertyPolicy.validateTemplate(
                nodeType,
                request.getTemplateId(),
                request.getTemplateVersion(),
                request.getLocalOverrides());
    }

    private void normalizeAndValidateConfiguration(
            EntityFormNode node,
            boolean migrateUnsupported) {
        String nodeType = normalize(node.getNodeType(), "FIELD");
        Map<String, Object> inactive = new LinkedHashMap<>();

        EntityFormNodePropertyPolicy.NormalizedProps normalizedProps =
                EntityFormNodePropertyPolicy.normalizeProps(
                        nodeType,
                        read(node.getPropsDocument(), "表单节点属性"),
                        migrateUnsupported);
        node.setPropsDocument(write(
                normalizedProps.active(), "表单节点属性"));
        if (!normalizedProps.inactive().isEmpty()) {
            inactive.put("props", normalizedProps.inactive());
        }

        Map<String, Object> rules =
                read(node.getRulesDocument(), "表单节点规则");
        try {
            EntityFormNodePropertyPolicy.NormalizedRules normalizedRules =
                    EntityFormNodePropertyPolicy.normalizeRules(
                            nodeType,
                            rules,
                            normalizedProps.active(),
                            migrateUnsupported);
            node.setRulesDocument(write(
                    normalizedRules.active(), "表单节点规则"));
            if (!normalizedRules.inactive().isEmpty()) {
                inactive.put("rules", normalizedRules.inactive());
            }
        } catch (IllegalArgumentException exception) {
            if (!migrateUnsupported
                    || !EntityFormNodePropertyPolicy.meaningful(rules)) {
                throw exception;
            }
            inactive.put("rules", rules);
            node.setRulesDocument(null);
        }

        Map<String, Object> bindings = read(
                node.getDataSourceBindingsDocument(),
                "表单节点数据源绑定");
        try {
            Map<String, Object> normalizedBindings =
                    EntityFormNodePropertyPolicy.normalizeDataSourceBindings(
                            nodeType, bindings);
            node.setDataSourceBindingsDocument(write(
                    normalizedBindings, "表单节点数据源绑定"));
        } catch (IllegalArgumentException exception) {
            if (!migrateUnsupported
                    || !EntityFormNodePropertyPolicy.meaningful(bindings)) {
                throw exception;
            }
            inactive.put("dataSourceBindings", bindings);
            node.setDataSourceBindingsDocument(null);
        }

        try {
            EntityFormNodePropertyPolicy.validateExtension(
                    nodeType,
                    node.getComponentName(),
                    node.getComponentVersion(),
                    node.getSnapshotVersion());
        } catch (IllegalArgumentException exception) {
            if (!migrateUnsupported) {
                throw exception;
            }
            Map<String, Object> component = new LinkedHashMap<>();
            component.put("componentName", node.getComponentName());
            component.put("componentVersion", node.getComponentVersion());
            component.put("snapshotVersion", node.getSnapshotVersion());
            if (EntityFormNodePropertyPolicy.meaningful(component)) {
                inactive.put("component", component);
            }
            node.setComponentName(null);
            node.setComponentVersion(null);
            node.setSnapshotVersion(null);
        }

        Map<String, Object> localOverrides =
                read(node.getLocalOverridesDocument(), "模板本地覆盖");
        try {
            EntityFormNodePropertyPolicy.validateTemplate(
                    nodeType,
                    node.getTemplateId(),
                    node.getTemplateVersion(),
                    localOverrides);
        } catch (IllegalArgumentException exception) {
            if (!migrateUnsupported) {
                throw exception;
            }
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("templateId", node.getTemplateId());
            template.put("templateVersion", node.getTemplateVersion());
            template.put("localOverrides", localOverrides);
            if (EntityFormNodePropertyPolicy.meaningful(template)) {
                inactive.put("template", template);
            }
            node.setTemplateId(null);
            node.setTemplateVersion(null);
            node.setLocalOverridesDocument(null);
        }

        try {
            EntityFormNodePropertyPolicy.validateBinding(
                    nodeType,
                    node.getBindingType(),
                    node.getBindingRef());
        } catch (IllegalArgumentException exception) {
            if (!migrateUnsupported) {
                throw exception;
            }
            inactive.put("binding", Map.of(
                    "bindingType",
                    Objects.toString(node.getBindingType(), "NONE"),
                    "bindingRef",
                    Objects.toString(node.getBindingRef(), "")));
            node.setBindingType("NONE");
            node.setBindingRef(null);
        }

        if ("RELATION".equals(normalize(
                node.getBindingType(), "NONE"))
                && SUB_FORM_NODE_TYPES.contains(nodeType)) {
            node.setPropsDocument(write(
                    normalizeRelationBoundSubFormProps(node),
                    "表单节点属性"));
        }

        mergeInactiveConfiguration(node, nodeType, inactive);
    }

    private void mergeInactiveConfiguration(
            EntityFormNode node,
            String nodeType,
            Map<String, Object> inactive) {
        if (inactive.isEmpty()) {
            return;
        }
        Map<String, Object> legacy = mutableMap(
                read(node.getLegacyPropsDocument(), "历史节点属性"));
        Map<String, Object> inactiveByType = objectMap(
                legacy.get("inactiveNodeProperties"),
                "非活动节点属性");
        Map<String, Object> typeProperties = objectMap(
                inactiveByType.get(nodeType),
                "非活动节点类型属性");
        typeProperties.putAll(inactive);
        inactiveByType.put(nodeType, typeProperties);
        legacy.put("inactiveNodeProperties", inactiveByType);
        node.setLegacyPropsDocument(write(legacy, "历史节点属性"));
    }

    private void validateNode(EntityFormNode node, String excludeId) {
        if (!StringUtils.hasText(node.getNodeKey())
                || !NODE_KEY.matcher(node.getNodeKey()).matches()) {
            throw new IllegalArgumentException("节点编码格式不正确");
        }
        node.setNodeType(normalize(node.getNodeType(), "FIELD"));
        node.setBindingType(normalize(node.getBindingType(), "NONE"));
        if (!NODE_TYPES.contains(node.getNodeType())) {
            throw new IllegalArgumentException("不支持的表单节点类型: " + node.getNodeType());
        }
        if (!BINDING_TYPES.contains(node.getBindingType())) {
            throw new IllegalArgumentException("不支持的节点绑定类型: " + node.getBindingType());
        }
        validateSubFormReleaseBinding(node);
        if (StringUtils.hasText(node.getComponentName())
                && (node.getComponentVersion() == null
                || node.getComponentVersion() < 1
                || node.getSnapshotVersion() == null
                || node.getSnapshotVersion() < 1)) {
            throw new IllegalArgumentException(
                    "节点扩展组件必须锁定实现版本和配置快照版本");
        }
        if (!StringUtils.hasText(node.getComponentName())
                && (node.getComponentVersion() != null
                || node.getSnapshotVersion() != null)) {
            throw new IllegalArgumentException(
                    "未配置节点扩展组件时不能单独保存组件版本");
        }
        LambdaQueryWrapper<EntityFormNode> duplicateQuery =
                new LambdaQueryWrapper<EntityFormNode>()
                        .eq(EntityFormNode::getFormId, node.getFormId())
                        .eq(EntityFormNode::getNodeKey, node.getNodeKey())
                        .eq(EntityFormNode::getDeleted, 0);
        if (StringUtils.hasText(excludeId)) {
            duplicateQuery.ne(EntityFormNode::getId, excludeId);
        }
        if (nodeMapper.selectCount(duplicateQuery) > 0) {
            throw duplicateNodeKeyConflict(
                    node.getFormId(), node.getNodeKey());
        }
        validateParent(node.getFormId(), excludeId, node.getParentId());
        EntityFormNode parent = StringUtils.hasText(node.getParentId())
                ? requireNode(node.getFormId(), node.getParentId())
                : null;
        validateParentChildType(node, parent);
        validateExistingChildren(node);
        if (StringUtils.hasText(node.getDataSourceBindingsDocument())) {
            Map<String, Object> bindings = codec.readObject(
                    node.getDataSourceBindingsDocument(), "节点数据源绑定");
            for (String usage : bindings.keySet()) {
                if (!DATA_SOURCE_USAGES.contains(usage.toUpperCase(Locale.ROOT))) {
                    throw new IllegalArgumentException("不支持的数据源绑定位置: " + usage);
                }
            }
        }
    }

    private void validateParent(String formId, String nodeId, String parentId) {
        int parentDepth = 0;
        if (StringUtils.hasText(parentId)) {
            EntityFormNode parent = requireNode(formId, parentId);
            if (!CONTAINER_TYPES.contains(parent.getNodeType())) {
                throw new IllegalArgumentException("父节点不是容器节点");
            }
            Set<String> visited = new HashSet<>();
            EntityFormNode current = parent;
            while (current != null) {
                if (Objects.equals(nodeId, current.getId())) {
                    throw new IllegalArgumentException("移动节点会形成循环引用");
                }
                if (!visited.add(current.getId())) {
                    throw new IllegalArgumentException("父节点链存在循环引用");
                }
                parentDepth++;
                current = StringUtils.hasText(current.getParentId())
                        ? requireNode(formId, current.getParentId())
                        : null;
            }
        }
        int subtreeHeight = currentSubtreeHeight(formId, nodeId);
        if (parentDepth + subtreeHeight > MAX_DEPTH) {
            throw new IllegalArgumentException(
                    "表单嵌套层级不能超过 " + MAX_DEPTH + " 层");
        }
    }

    private int currentSubtreeHeight(String formId, String nodeId) {
        if (!StringUtils.hasText(nodeId)) {
            return 1;
        }
        List<EntityFormNode> nodes = nodeMapper.findByFormId(formId);
        if (nodes == null || nodes.isEmpty()) {
            return 1;
        }
        Map<String, List<EntityFormNode>> childrenByParent = new HashMap<>();
        for (EntityFormNode node : nodes) {
            if (StringUtils.hasText(node.getParentId())) {
                childrenByParent
                        .computeIfAbsent(node.getParentId(), ignored -> new ArrayList<>())
                        .add(node);
            }
        }
        return currentSubtreeHeight(
                nodeId,
                childrenByParent,
                new HashSet<>());
    }

    private int currentSubtreeHeight(
            String nodeId,
            Map<String, List<EntityFormNode>> childrenByParent,
            Set<String> visiting) {
        if (!visiting.add(nodeId)) {
            throw new IllegalArgumentException("节点子树存在循环引用");
        }
        int height = 1;
        for (EntityFormNode child :
                childrenByParent.getOrDefault(nodeId, List.of())) {
            height = Math.max(
                    height,
                    1 + currentSubtreeHeight(
                            child.getId(),
                            childrenByParent,
                            visiting));
        }
        visiting.remove(nodeId);
        return height;
    }

    private void validateParentChildType(
            EntityFormNode child,
            EntityFormNode parent) {
        if (parent == null) {
            if ("TAB".equals(child.getNodeType())) {
                throw new IllegalArgumentException("TAB 节点只能位于 TAB_SET 下");
            }
            return;
        }
        Set<String> allowedChildren = ALLOWED_CHILD_TYPES.get(parent.getNodeType());
        if (allowedChildren == null || !allowedChildren.contains(child.getNodeType())) {
            if ("TAB".equals(child.getNodeType())) {
                throw new IllegalArgumentException("TAB 节点只能位于 TAB_SET 下");
            }
            if ("TAB_SET".equals(parent.getNodeType())) {
                throw new IllegalArgumentException("TAB_SET 的直接子节点只能是 TAB");
            }
            throw new IllegalArgumentException(
                    parent.getNodeType()
                            + " 节点不能直接包含 "
                            + child.getNodeType()
                            + " 节点");
        }
    }

    private void validateExistingChildren(EntityFormNode parent) {
        List<EntityFormNode> children =
                nodeMapper.findSiblings(parent.getFormId(), parent.getId());
        for (EntityFormNode child : children) {
            validateParentChildType(child, parent);
        }
    }

    private void applyPatch(EntityFormNode target, EntityFormNodePatchRequest request) {
        Set<String> clear = request.getClearFields() == null
                ? Set.of() : request.getClearFields();
        if (request.getParentId() != null || clear.contains("parentId")) {
            target.setParentId(clear.contains("parentId")
                    ? null : blankToNull(request.getParentId()));
        }
        if (request.getNodeKey() != null) {
            target.setNodeKey(request.getNodeKey());
        }
        if (request.getNodeType() != null) {
            target.setNodeType(normalize(request.getNodeType(), null));
        }
        if (request.getBindingType() != null) {
            target.setBindingType(normalize(request.getBindingType(), null));
        }
        if (request.getBindingRef() != null || clear.contains("bindingRef")) {
            target.setBindingRef(clear.contains("bindingRef")
                    ? null : blankToNull(request.getBindingRef()));
        }
        if (request.getComponentName() != null
                || clear.contains("componentName")) {
            target.setComponentName(clear.contains("componentName")
                    ? null : blankToNull(request.getComponentName()));
        }
        if (request.getComponentVersion() != null
                || clear.contains("componentVersion")) {
            target.setComponentVersion(clear.contains("componentVersion")
                    ? null : request.getComponentVersion());
        }
        if (request.getSnapshotVersion() != null
                || clear.contains("snapshotVersion")) {
            target.setSnapshotVersion(clear.contains("snapshotVersion")
                    ? null : request.getSnapshotVersion());
        }
        boolean hasExplicitSubFormBinding =
                request.getChildFormId() != null
                        || request.getChildFormReleaseId() != null
                        || request.getChildFormReleaseVersion() != null;
        if (request.getProps() != null
                || clear.contains("props")
                || hasExplicitSubFormBinding
                || Set.of("childFormId",
                        "childFormReleaseId",
                        "childFormReleaseVersion")
                        .stream()
                        .anyMatch(clear::contains)
                || Set.of("SUB_FORM", "REPEATER").contains(target.getNodeType())) {
            Map<String, Object> props = clear.contains("props")
                    ? new LinkedHashMap<>()
                    : request.getProps() != null
                            ? request.getProps()
                            : read(target.getPropsDocument(), "表单节点属性");
            props = clearSubFormBindings(props, clear);
            target.setPropsDocument(write(
                    normalizeSubFormProps(
                            target.getNodeType(),
                            props,
                            request.getChildFormId(),
                            request.getChildFormReleaseId(),
                            request.getChildFormReleaseVersion(),
                            true),
                    "表单节点属性"));
        }
        if (request.getRules() != null || clear.contains("rules")) {
            target.setRulesDocument(clear.contains("rules")
                    ? null : write(request.getRules(), "表单节点规则"));
        }
        if (request.getDataSourceBindings() != null
                || clear.contains("dataSourceBindings")) {
            target.setDataSourceBindingsDocument(clear.contains("dataSourceBindings")
                    ? null
                    : write(request.getDataSourceBindings(), "表单节点数据源绑定"));
        }
        if (request.getLegacyProps() != null || clear.contains("legacyProps")) {
            if (clear.contains("legacyProps")) {
                target.setLegacyPropsDocument(null);
            } else {
                Map<String, Object> legacy = mutableMap(
                        read(target.getLegacyPropsDocument(), "历史节点属性"));
                legacy.putAll(request.getLegacyProps());
                target.setLegacyPropsDocument(
                        write(legacy, "历史节点属性"));
            }
        }
        if (request.getOrderKey() != null) {
            target.setOrderKey(request.getOrderKey());
        }
        if (request.getTemplateId() != null || clear.contains("templateId")) {
            target.setTemplateId(clear.contains("templateId")
                    ? null : blankToNull(request.getTemplateId()));
        }
        if (request.getTemplateVersion() != null || clear.contains("templateVersion")) {
            target.setTemplateVersion(clear.contains("templateVersion")
                    ? null : request.getTemplateVersion());
        }
        if (request.getLocalOverrides() != null || clear.contains("localOverrides")) {
            target.setLocalOverridesDocument(clear.contains("localOverrides")
                    ? null : write(request.getLocalOverrides(), "模板本地覆盖"));
        }
    }

    private void validatePatchConstraints(
            EntityFormNode current,
            EntityFormNodePatchRequest request) {
        Set<String> clear = request.getClearFields() == null
                ? Set.of() : request.getClearFields();
        for (String field : clear) {
            if (!CLEARABLE_PATCH_FIELDS.contains(field)) {
                throw new IllegalArgumentException("不支持清空表单节点字段: " + field);
            }
        }
        String nodeType = request.getNodeType() == null
                ? normalize(current.getNodeType(), "FIELD")
                : normalize(request.getNodeType(), "FIELD");
        validateTechnicalIdentity(current, request, clear);
        validateRequestedPatchConfiguration(
                current,
                nodeType,
                request,
                clear);
    }

    private void validateTechnicalIdentity(
            EntityFormNode current,
            EntityFormNodePatchRequest request,
            Set<String> clear) {
        if (request.getNodeKey() != null
                && !Objects.equals(
                        request.getNodeKey(),
                        current.getNodeKey())) {
            throw new IllegalArgumentException(
                    "节点编码由系统管理，不能通过属性 PATCH 修改");
        }
        if (request.getNodeType() != null
                && !Objects.equals(
                        normalize(request.getNodeType(), "FIELD"),
                        normalize(current.getNodeType(), "FIELD"))) {
            throw new IllegalArgumentException(
                    "节点类型由系统管理，不能通过属性 PATCH 修改");
        }
        if (request.getOrderKey() != null
                && !Objects.equals(
                        request.getOrderKey(),
                        current.getOrderKey())) {
            throw new IllegalArgumentException(
                    "节点排序必须使用节点排序接口");
        }
        if (request.getLegacyProps() != null
                || clear.contains("legacyProps")) {
            throw new IllegalArgumentException(
                    "历史兼容属性由服务端管理，不能直接修改");
        }
        validateReadOnlyDisplayProps(current, request);
        if (hasValidBindingState(current)) {
            validateBoundNodeIdentity(current, request, clear);
        }
    }

    private void validateBoundNodeIdentity(
            EntityFormNode current,
            EntityFormNodePatchRequest request,
            Set<String> clear) {
        if (request.getBindingType() != null
                && !Objects.equals(
                        normalize(request.getBindingType(), "NONE"),
                        normalize(current.getBindingType(), "NONE"))) {
            throw new IllegalArgumentException("已绑定节点不能修改绑定类型");
        }
        if ((request.getBindingRef() != null || clear.contains("bindingRef"))
                && !Objects.equals(
                        clear.contains("bindingRef")
                                ? null : blankToNull(request.getBindingRef()),
                        blankToNull(current.getBindingRef()))) {
            throw new IllegalArgumentException("已绑定节点不能修改绑定引用");
        }
        if (isBound(current)) {
            validateBoundProps(current, request, clear);
        }
    }

    private void validateReadOnlyDisplayProps(
            EntityFormNode current,
            EntityFormNodePatchRequest request) {
        String nodeType = normalize(current.getNodeType(), "FIELD");
        if (EDITABLE_LABEL_NODE_TYPES.contains(nodeType)
                || request.getProps() == null) {
            return;
        }
        Map<String, Object> currentProps =
                read(current.getPropsDocument(), "表单节点属性");
        Object currentLabel = currentProps.get("label");
        Object requestedLabel = request.getProps().get("label");
        if (EntityFormNodePropertyPolicy.meaningful(currentLabel)
                && !Objects.deepEquals(
                        currentLabel,
                        requestedLabel)) {
            throw new IllegalArgumentException(
                    nodeType + " 节点显示标识由系统管理，不能直接修改");
        }
    }

    private void validateBoundProps(
            EntityFormNode current,
            EntityFormNodePatchRequest request,
            Set<String> clear) {
        if (clear.contains("props")) {
            throw new IllegalArgumentException(
                    "已绑定节点不能清空数据语义属性");
        }
        if (request.getProps() == null) {
            return;
        }
        Map<String, Object> currentProps =
                read(current.getPropsDocument(), "表单节点属性");
        Map<String, Object> requestedProps = request.getProps();
        requireSameMeaningfulValues(
                currentProps,
                requestedProps,
                IMMUTABLE_BOUND_PROP_KEYS,
                "已绑定节点不能修改字段身份属性");

        Map<String, Object> currentComponentProps =
                objectMap(
                        currentProps.get("componentProps"),
                        "字段组件属性");
        Map<String, Object> requestedComponentProps =
                objectMap(
                        requestedProps.get("componentProps"),
                        "字段组件属性");
        String nodeType = normalize(current.getNodeType(), "FIELD");
        if (SUB_FORM_NODE_TYPES.contains(nodeType)) {
            requireSameMeaningfulValues(
                    objectMap(
                            currentComponentProps.get("subFormConfig"),
                            "子表单配置"),
                    objectMap(
                            requestedComponentProps.get("subFormConfig"),
                            "子表单配置"),
                    IMMUTABLE_SUB_FORM_CONFIG_KEYS,
                    "已绑定子表单不能修改实体关系属性");
        }
        if ("FIELD".equals(nodeType)) {
            requireSameMeaningfulValues(
                    objectMap(
                            currentComponentProps.get("refConfig"),
                            "实体引用配置"),
                    objectMap(
                            requestedComponentProps.get("refConfig"),
                            "实体引用配置"),
                    IMMUTABLE_REFERENCE_CONFIG_KEYS,
                    "已绑定引用字段不能修改引用实体属性");
        }
    }

    private void requireSameMeaningfulValues(
            Map<String, Object> current,
            Map<String, Object> requested,
            Set<String> keys,
            String message) {
        for (String key : keys) {
            Object expected = current.get(key);
            if (EntityFormNodePropertyPolicy.meaningful(expected)
                    && !Objects.deepEquals(
                            expected,
                            requested.get(key))) {
                throw new IllegalArgumentException(
                        message + ": " + key);
            }
        }
    }

    private void validateRequestedPatchConfiguration(
            EntityFormNode current,
            String nodeType,
            EntityFormNodePatchRequest request,
            Set<String> clear) {
        if (request.getProps() != null && !clear.contains("props")) {
            EntityFormNodePropertyPolicy.normalizeProps(
                    nodeType, request.getProps(), false);
        }
        if (request.getRules() != null && !clear.contains("rules")) {
            Map<String, Object> props = request.getProps() == null
                    ? read(current.getPropsDocument(), "表单节点属性")
                    : request.getProps();
            EntityFormNodePropertyPolicy.normalizeRules(
                    nodeType,
                    request.getRules(),
                    props,
                    false);
        }
        if (request.getDataSourceBindings() != null
                && !clear.contains("dataSourceBindings")) {
            EntityFormNodePropertyPolicy.normalizeDataSourceBindings(
                    nodeType, request.getDataSourceBindings());
        }
        if (!EntityFormNodePropertyPolicy.supportsExtension(nodeType)
                && (request.getComponentName() != null
                || request.getComponentVersion() != null
                || request.getSnapshotVersion() != null)) {
            throw new IllegalArgumentException(
                    nodeType + " 节点不支持扩展组件配置");
        }
        if (!EntityFormNodePropertyPolicy.supportsChildForm(nodeType)
                && (request.getChildFormId() != null
                || request.getChildFormReleaseId() != null
                || request.getChildFormReleaseVersion() != null)) {
            throw new IllegalArgumentException(
                    nodeType + " 节点不支持子表单发布引用");
        }
        if (!EntityFormNodePropertyPolicy.supportsTemplate(nodeType)
                && (StringUtils.hasText(request.getTemplateId())
                || request.getTemplateVersion() != null
                || EntityFormNodePropertyPolicy.meaningful(
                        request.getLocalOverrides()))) {
            throw new IllegalArgumentException(
                    nodeType + " 节点不支持组件模板配置");
        }
        if (request.getBindingType() != null
                && !EntityFormNodePropertyPolicy.bindingTypes(nodeType)
                        .contains(normalize(request.getBindingType(), "NONE"))) {
            throw new IllegalArgumentException(
                    nodeType + " 节点不支持绑定类型: "
                            + normalize(request.getBindingType(), "NONE"));
        }
        if (request.getBindingRef() != null
                && !EntityFormNodePropertyPolicy.bindingTypes(nodeType)
                        .stream()
                        .anyMatch(type -> !"NONE".equals(type))) {
            throw new IllegalArgumentException(
                    nodeType + " 节点不支持数据绑定");
        }
    }

    private boolean hasValidBindingState(EntityFormNode node) {
        try {
            EntityFormNodePropertyPolicy.validateBinding(
                    node.getNodeType(),
                    node.getBindingType(),
                    node.getBindingRef());
            return true;
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }

    private boolean isBound(EntityFormNode node) {
        return !"NONE".equals(normalize(node.getBindingType(), "NONE"))
                || StringUtils.hasText(node.getBindingRef());
    }

    private EntityFormNode copy(EntityFormNode source) {
        EntityFormNode target = new EntityFormNode();
        target.setId(source.getId());
        target.setFormId(source.getFormId());
        target.setParentId(source.getParentId());
        target.setNodeKey(source.getNodeKey());
        target.setNodeType(source.getNodeType());
        target.setBindingType(source.getBindingType());
        target.setBindingRef(source.getBindingRef());
        target.setComponentName(source.getComponentName());
        target.setComponentVersion(source.getComponentVersion());
        target.setSnapshotVersion(source.getSnapshotVersion());
        target.setPropsDocument(source.getPropsDocument());
        target.setRulesDocument(source.getRulesDocument());
        target.setDataSourceBindingsDocument(source.getDataSourceBindingsDocument());
        target.setLegacyPropsDocument(source.getLegacyPropsDocument());
        target.setOrderKey(source.getOrderKey());
        target.setRevision(source.getRevision());
        target.setTemplateId(source.getTemplateId());
        target.setTemplateVersion(source.getTemplateVersion());
        target.setLocalOverridesDocument(source.getLocalOverridesDocument());
        target.setCreatedAt(source.getCreatedAt());
        target.setDeleted(source.getDeleted());
        return target;
    }

    private EntityFormNodeCreateRequest toCreateRequest(
            EntityFormNode source,
            long fallbackOrder) {
        EntityFormNodeCreateRequest request = new EntityFormNodeCreateRequest();
        request.setId(source.getId());
        request.setParentId(source.getParentId());
        request.setNodeKey(source.getNodeKey());
        request.setNodeType(source.getNodeType());
        request.setBindingType(source.getBindingType());
        request.setBindingRef(source.getBindingRef());
        request.setComponentName(source.getComponentName());
        request.setComponentVersion(source.getComponentVersion());
        request.setSnapshotVersion(source.getSnapshotVersion());
        request.setProps(read(source.getPropsDocument(), "表单节点属性"));
        request.setRules(read(source.getRulesDocument(), "表单节点规则"));
        request.setDataSourceBindings(
                read(source.getDataSourceBindingsDocument(), "表单节点数据源绑定"));
        request.setLegacyProps(read(source.getLegacyPropsDocument(), "历史节点属性"));
        request.setOrderKey(source.getOrderKey() == null
                ? fallbackOrder : source.getOrderKey());
        request.setTemplateId(source.getTemplateId());
        request.setTemplateVersion(source.getTemplateVersion());
        request.setLocalOverrides(
                read(source.getLocalOverridesDocument(), "模板本地覆盖"));
        return request;
    }

    private EntityFormNodePatchRequest toPatchRequest(
            EntityFormNode source,
            EntityFormNode current,
            PatchMode mode) {
        EntityFormNodePatchRequest request = new EntityFormNodePatchRequest();
        Set<String> clear = new LinkedHashSet<>();
        request.setExpectedRevision(mode.userFacing()
                ? source.getRevision()
                : current.getRevision());
        request.setParentId(source.getParentId());
        request.setNodeKey(source.getNodeKey());
        request.setNodeType(source.getNodeType());
        request.setBindingType(source.getBindingType());
        request.setBindingRef(source.getBindingRef());
        request.setComponentName(source.getComponentName());
        request.setComponentVersion(source.getComponentVersion());
        request.setSnapshotVersion(source.getSnapshotVersion());
        request.setProps(read(source.getPropsDocument(), "表单节点属性"));
        request.setRules(read(source.getRulesDocument(), "表单节点规则"));
        request.setDataSourceBindings(
                read(source.getDataSourceBindingsDocument(), "表单节点数据源绑定"));
        if (mode == PatchMode.SYSTEM_IMPORT) {
            request.setLegacyProps(
                    read(source.getLegacyPropsDocument(), "历史节点属性"));
        } else if (!Objects.equals(
                source.getLegacyPropsDocument(),
                current.getLegacyPropsDocument())) {
            throw new IllegalArgumentException(
                    "历史兼容属性由服务端管理，整包更新不能修改");
        }
        request.setOrderKey(source.getOrderKey());
        request.setTemplateId(source.getTemplateId());
        request.setTemplateVersion(source.getTemplateVersion());
        request.setLocalOverrides(
                read(source.getLocalOverridesDocument(), "模板本地覆盖"));
        addClearIfMissing(
                clear, "parentId", source.getParentId(), current.getParentId());
        addClearIfMissing(
                clear, "bindingRef", source.getBindingRef(), current.getBindingRef());
        addClearIfMissing(
                clear, "componentName",
                source.getComponentName(), current.getComponentName());
        addClearIfMissing(
                clear, "componentVersion",
                source.getComponentVersion(), current.getComponentVersion());
        addClearIfMissing(
                clear, "snapshotVersion",
                source.getSnapshotVersion(), current.getSnapshotVersion());
        addClearIfMissing(
                clear, "props",
                source.getPropsDocument(), current.getPropsDocument());
        addClearIfMissing(
                clear, "rules",
                source.getRulesDocument(), current.getRulesDocument());
        addClearIfMissing(
                clear, "dataSourceBindings",
                source.getDataSourceBindingsDocument(),
                current.getDataSourceBindingsDocument());
        if (mode == PatchMode.SYSTEM_IMPORT) {
            addClearIfMissing(
                    clear, "legacyProps",
                    source.getLegacyPropsDocument(),
                    current.getLegacyPropsDocument());
        }
        addClearIfMissing(
                clear, "templateId",
                source.getTemplateId(), current.getTemplateId());
        addClearIfMissing(
                clear, "templateVersion",
                source.getTemplateVersion(), current.getTemplateVersion());
        addClearIfMissing(
                clear, "localOverrides",
                source.getLocalOverridesDocument(),
                current.getLocalOverridesDocument());
        request.setClearFields(clear.isEmpty() ? null : clear);
        return request;
    }

    private void addClearIfMissing(
            Set<String> clear,
            String field,
            Object sourceValue,
            Object currentValue) {
        if (sourceValue == null && currentValue != null) {
            clear.add(field);
        }
    }

    private boolean hasChanges(EntityFormNode source, EntityFormNode current) {
        return !Objects.equals(source.getParentId(), current.getParentId())
                || !Objects.equals(source.getNodeKey(), current.getNodeKey())
                || !Objects.equals(normalize(source.getNodeType(), "FIELD"), current.getNodeType())
                || !Objects.equals(normalize(source.getBindingType(), "NONE"), current.getBindingType())
                || !Objects.equals(source.getBindingRef(), current.getBindingRef())
                || !Objects.equals(source.getComponentName(), current.getComponentName())
                || !Objects.equals(source.getComponentVersion(), current.getComponentVersion())
                || !Objects.equals(source.getSnapshotVersion(), current.getSnapshotVersion())
                || !Objects.equals(source.getPropsDocument(), current.getPropsDocument())
                || !Objects.equals(source.getRulesDocument(), current.getRulesDocument())
                || !Objects.equals(
                        source.getDataSourceBindingsDocument(),
                        current.getDataSourceBindingsDocument())
                || !Objects.equals(source.getLegacyPropsDocument(), current.getLegacyPropsDocument())
                || !Objects.equals(source.getOrderKey(), current.getOrderKey())
                || !Objects.equals(source.getTemplateId(), current.getTemplateId())
                || !Objects.equals(source.getTemplateVersion(), current.getTemplateVersion())
                || !Objects.equals(
                        source.getLocalOverridesDocument(),
                        current.getLocalOverridesDocument());
    }

    private boolean requiresLegacyReleasePin(EntityFormNode node) {
        if (!Set.of("SUB_FORM", "REPEATER").contains(
                normalize(node.getNodeType(), null))) {
            return false;
        }
        Map<String, Object> props = read(
                node.getPropsDocument(), "子表单节点属性");
        Map<String, Object> componentProps =
                objectMap(props.get("componentProps"), "子表单组件属性");
        Map<String, Object> nestedConfig =
                objectMap(componentProps.get("subFormConfig"), "子表单配置");
        Map<String, Object> directConfig =
                objectMap(props.get("subFormConfig"), "子表单配置");
        String formId = firstText(
                props.get("childFormId"),
                props.get("refFormId"),
                props.get("publishedFormId"),
                directConfig.get("childFormId"),
                directConfig.get("refFormId"),
                directConfig.get("publishedFormId"),
                nestedConfig.get("childFormId"),
                nestedConfig.get("refFormId"),
                nestedConfig.get("publishedFormId"));
        String releaseId = firstText(
                props.get("childFormReleaseId"),
                props.get("refFormReleaseId"),
                props.get("publishedFormReleaseId"),
                directConfig.get("childFormReleaseId"),
                directConfig.get("refFormReleaseId"),
                directConfig.get("publishedFormReleaseId"),
                nestedConfig.get("childFormReleaseId"),
                nestedConfig.get("refFormReleaseId"),
                nestedConfig.get("publishedFormReleaseId"));
        Integer releaseVersion = firstInteger(
                props.get("childFormReleaseVersion"),
                props.get("refFormReleaseVersion"),
                props.get("publishedFormReleaseVersion"),
                directConfig.get("childFormReleaseVersion"),
                directConfig.get("refFormReleaseVersion"),
                directConfig.get("publishedFormReleaseVersion"),
                nestedConfig.get("childFormReleaseVersion"),
                nestedConfig.get("refFormReleaseVersion"),
                nestedConfig.get("publishedFormReleaseVersion"));
        return StringUtils.hasText(formId)
                && (!StringUtils.hasText(releaseId) || releaseVersion == null);
    }

    private void requireExpectedRevision(Integer expected, EntityFormNode current) {
        if (expected == null) {
            throw new IllegalArgumentException("expectedRevision 不能为空");
        }
        if (!expected.equals(current.getRevision())) {
            throw new RevisionConflictException("节点已被其他人修改", current);
        }
    }

    private RevisionConflictException conflict(String formId, String nodeId) {
        EntityFormNode latest = nodeMapper.selectById(nodeId);
        return new RevisionConflictException(
                "节点已被其他人修改，请刷新后重试",
                latest != null && formId.equals(latest.getFormId()) ? latest : null);
    }

    private RevisionConflictException duplicateNodeKeyConflict(
            String formId,
            String nodeKey) {
        return new RevisionConflictException(
                "同一表单内节点编码已被其他请求占用，请刷新后重试: "
                        + nodeKey,
                nodeMapper.findActiveByFormIdAndNodeKey(formId, nodeKey));
    }

    private RuntimeException translateNodeWriteException(
            String formId,
            String nodeKey,
            DataIntegrityViolationException exception) {
        if (containsConstraint(exception, ACTIVE_NODE_KEY_UNIQUE_INDEX)) {
            return duplicateNodeKeyConflict(formId, nodeKey);
        }
        return exception;
    }

    private boolean containsConstraint(
            Throwable throwable,
            String constraintName) {
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())
                    && current.getMessage().contains(constraintName)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private EntityForm requireForm(String formId) {
        EntityForm form = formMapper.selectById(formId);
        if (form == null) {
            throw new IllegalArgumentException("表单不存在");
        }
        entityAccessPolicy.requireDynamicById(form.getEntityId());
        return form;
    }

    private EntityForm requireFormForUpdate(
            String formId,
            Integer expectedRevision) {
        if (expectedRevision == null) {
            throw new IllegalArgumentException("expectedRevision 不能为空");
        }
        EntityForm form = formMapper.selectByIdForUpdate(formId);
        if (form == null) {
            throw new IllegalArgumentException("表单不存在");
        }
        entityAccessPolicy.requireDynamicById(form.getEntityId());
        int currentRevision =
                form.getRevision() == null ? 1 : form.getRevision();
        if (!Objects.equals(expectedRevision, currentRevision)) {
            throw new RevisionConflictException(
                    "表单草稿已被其他人修改，请刷新后重试",
                    form);
        }
        return form;
    }

    private EntityFormNode requireNode(String formId, String nodeId) {
        EntityFormNode node = nodeMapper.selectById(nodeId);
        if (node == null || !formId.equals(node.getFormId())
                || Integer.valueOf(1).equals(node.getDeleted())) {
            throw new IllegalArgumentException("表单节点不存在");
        }
        return node;
    }

    private void touchForm(String formId) {
        UpdateWrapper<EntityForm> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", formId)
                .setSql("revision = revision + 1")
                .set("draft_hash", null)
                .set("update_time", LocalDateTime.now());
        formMapper.update(null, wrapper);
    }

    private long nextOrderKey(String formId, String parentId) {
        List<EntityFormNode> siblings = nodeMapper.findSiblings(formId, parentId);
        return siblings.isEmpty()
                ? ORDER_STEP
                : siblings.get(siblings.size() - 1).getOrderKey() + ORDER_STEP;
    }

    private long resolveBoundary(
            String formId,
            String parentId,
            String nodeId,
            long fallback) {
        if (!StringUtils.hasText(nodeId)) {
            return fallback;
        }
        EntityFormNode node = requireNode(formId, nodeId);
        if (!Objects.equals(blankToNull(parentId), blankToNull(node.getParentId()))) {
            throw new IllegalArgumentException("排序边界节点不在同一父节点下");
        }
        return node.getOrderKey();
    }

    private void rebalance(String formId, String parentId) {
        List<EntityFormNode> siblings =
                new ArrayList<>(nodeMapper.findSiblings(formId, parentId));
        long order = ORDER_STEP;
        for (EntityFormNode sibling : siblings) {
            if (!Objects.equals(sibling.getOrderKey(), order)) {
                UpdateWrapper<EntityFormNode> wrapper = new UpdateWrapper<>();
                wrapper.eq("id", sibling.getId())
                        .set("order_key", order)
                        .setSql("revision = revision + 1")
                        .set("update_time", LocalDateTime.now());
                nodeMapper.update(null, wrapper);
            }
            order += ORDER_STEP;
        }
    }

    private String write(Map<String, Object> value, String label) {
        return value == null || value.isEmpty() ? null : codec.write(value, label);
    }

    private Map<String, Object> read(String value, String label) {
        return StringUtils.hasText(value) ? codec.readObject(value, label) : null;
    }

    private String normalize(String value, String fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private enum PatchMode {
        USER_PROPERTY,
        USER_REORDER,
        SYSTEM_IMPORT;

        boolean userFacing() {
            return this != SYSTEM_IMPORT;
        }
    }
}
