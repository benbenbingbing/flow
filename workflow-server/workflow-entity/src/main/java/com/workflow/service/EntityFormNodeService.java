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
import com.workflow.entity.UiConfigRelease;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityFormNodeMapper;
import com.workflow.mapper.UiConfigReleaseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
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

@Service
@RequiredArgsConstructor
public class EntityFormNodeService {

    public static final long ORDER_STEP = 1_000_000L;
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
    private static final Set<String> DATA_SOURCE_USAGES = Set.of(
            "FORM_INIT", "FIELD_OPTIONS", "FIELD_DEFAULT", "FIELD_COMPUTE",
            "SUBFORM_ROWS", "LIST_QUERY", "LIST_COLUMN", "AFTER_LOAD", "BEFORE_SUBMIT");

    private final EntityFormMapper formMapper;
    private final EntityFormNodeMapper nodeMapper;
    private final UiConfigReleaseMapper releaseMapper;
    private final EntityDefinitionAccessPolicy entityAccessPolicy;
    private final JsonDocumentCodec codec;

    public List<EntityFormNode> findByFormId(String formId) {
        requireForm(formId);
        return nodeMapper.findByFormId(formId);
    }

    @Transactional(rollbackFor = Exception.class)
    public EntityFormNode create(String formId, EntityFormNodeCreateRequest request) {
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

    @Transactional(rollbackFor = Exception.class)
    public EntityFormNode patch(
            String formId,
            String nodeId,
            EntityFormNodePatchRequest request) {
        EntityFormNode current = requireNode(formId, nodeId);
        requireExpectedRevision(request.getExpectedRevision(), current);
        EntityFormNode updated = copy(current);
        applyPatch(updated, request);
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
        patch.setParentId(parentId);
        patch.setOrderKey(previous + ((next - previous) / 2));
        return patch(formId, nodeId, patch);
    }

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

    @Transactional(rollbackFor = Exception.class)
    public void replaceByDiff(String formId, List<EntityFormNode> incoming) {
        requireForm(formId);
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
                EntityFormNode created = create(formId, request);
                retained.add(created.getId());
            } else {
                retained.add(current.getId());
                EntityFormNodePatchRequest request = toPatchRequest(source, current);
                if (hasChanges(source, current)
                        || requiresLegacyReleasePin(source)) {
                    patch(formId, current.getId(), request);
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
        if (!componentProps.isEmpty() || props.containsKey("componentProps")) {
            componentProps.put("subFormConfig", nestedConfig);
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

    private void validateSubFormReleaseBinding(EntityFormNode node) {
        if (!Set.of("SUB_FORM", "REPEATER").contains(node.getNodeType())) {
            return;
        }
        FormReleaseReference reference = readFormReleaseReference(
                read(node.getPropsDocument(), "子表单节点属性"),
                node.getNodeKey());
        if (reference != null) {
            requireReferencedRelease(reference);
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
        if (!StringUtils.hasText(parentId)) {
            return;
        }
        EntityFormNode parent = requireNode(formId, parentId);
        if (Objects.equals(nodeId, parentId)) {
            throw new IllegalArgumentException("节点不能作为自己的父节点");
        }
        if (!CONTAINER_TYPES.contains(parent.getNodeType())) {
            throw new IllegalArgumentException("父节点不是容器节点");
        }
        ArrayDeque<String> parents = new ArrayDeque<>();
        parents.add(parentId);
        int depth = 1;
        while (!parents.isEmpty()) {
            String currentId = parents.removeFirst();
            if (Objects.equals(nodeId, currentId)) {
                throw new IllegalArgumentException("移动节点会形成循环引用");
            }
            EntityFormNode current = nodeMapper.selectById(currentId);
            if (current != null && StringUtils.hasText(current.getParentId())) {
                parents.addLast(current.getParentId());
                depth++;
                if (depth >= MAX_DEPTH) {
                    throw new IllegalArgumentException(
                            "表单嵌套层级不能超过 " + MAX_DEPTH + " 层");
                }
            }
        }
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
            target.setLegacyPropsDocument(clear.contains("legacyProps")
                    ? null : write(request.getLegacyProps(), "历史节点属性"));
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
            EntityFormNode current) {
        EntityFormNodePatchRequest request = new EntityFormNodePatchRequest();
        request.setExpectedRevision(current.getRevision());
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
        request.setOrderKey(source.getOrderKey());
        request.setTemplateId(source.getTemplateId());
        request.setTemplateVersion(source.getTemplateVersion());
        request.setLocalOverrides(
                read(source.getLocalOverridesDocument(), "模板本地覆盖"));
        return request;
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
}
