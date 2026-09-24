package com.workflow.entity.form.application;

import com.workflow.entity.definition.application.EntityUiConfigurationPolicy;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.database.JdbcWriteAttempt;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.entity.ui.model.UiDataSourceUsages;
import com.workflow.entity.form.api.request.EntityFormNodeCreateRequest;
import com.workflow.entity.form.api.request.EntityFormNodePatchRequest;
import com.workflow.entity.form.api.request.EntityFormNodeReorderRequest;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
import com.workflow.entity.ui.application.UiExtensionReferencePolicy;
import com.workflow.entity.ui.application.UiMutableInterfaceReferenceNormalizer;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormNodeMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityRelationMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiEventBindingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
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
 * <p>
 * 支持基于乐观锁的节点变更、父子关系与嵌套深度校验、子表单发布版本引用锁定、
 * 组件扩展引用校验，以及通过差异比对批量重建表单节点树。
 * </p>
 */
@Service
@RequiredArgsConstructor
public class EntityFormNodeService {

        /** 节点排序步长，用于 orderKey 的稀疏分布以支持插入。 */
        public static final long ORDER_STEP = 1_000_000L;
        /** 表单节点最大嵌套深度。 */
        public static final int MAX_DEPTH = 8;

        private static final Pattern NODE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_-]{0,99}");
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
        private static final Set<String> SUB_FORM_NODE_TYPES = Set.of(
                        "SUB_FORM", "REPEATER");
        private static final Set<String> CLEARABLE_PATCH_FIELDS = Set.of(
                        "parentId", "bindingRef", "componentName", "componentVersion",
                        "snapshotVersion", "childFormId", "childFormReleaseId",
                        "childFormReleaseVersion", "rules", "dataSourceBindings");
        private static final Set<String> IMMUTABLE_BOUND_PROP_KEYS = Set.of(
                        "fieldId", "fieldCode", "fieldType");
        private static final Set<String> IMMUTABLE_SUB_FORM_CONFIG_KEYS = Set.of(
                        "refEntityId", "childEntityId", "relationType",
                        "childRefFieldCode", "refFieldCode", "relationCode",
                        "dataKey");
        private static final Set<String> IMMUTABLE_REFERENCE_CONFIG_KEYS = Set.of(
                        "refEntityType", "refEntityId", "entityCode");
        private static final Set<String> EDITABLE_LABEL_NODE_TYPES = Set.of(
                        "SECTION", "TAB", "COLLAPSE",
                        "FIELD", "SUB_FORM", "REPEATER");
        private static final Set<String> DATA_SOURCE_USAGES = Set.of(
                        UiDataSourceUsages.FORM_INIT,
                        UiDataSourceUsages.FIELD_OPTIONS,
                        UiDataSourceUsages.FIELD_DEFAULT,
                        UiDataSourceUsages.FIELD_COMPUTE,
                        UiDataSourceUsages.SUBFORM_ROWS,
                        UiDataSourceUsages.LIST_QUERY,
                        UiDataSourceUsages.LIST_COLUMN,
                        UiDataSourceUsages.AFTER_LOAD,
                        UiDataSourceUsages.BEFORE_SUBMIT);
        private static final Set<String> SYSTEM_READ_ONLY_DATA_SOURCE_USAGES =
                        Set.of(
                                        UiDataSourceUsages.FORM_INIT,
                                        UiDataSourceUsages.FIELD_OPTIONS,
                                        UiDataSourceUsages.AFTER_LOAD);

        private final EntityFormMapper formMapper;
        private final EntityFormNodeMapper nodeMapper;
        private final EntityRelationMapper relationMapper;
        private final UiConfigReleaseMapper releaseMapper;
        private final EntityUiConfigurationPolicy entityUiConfigurationPolicy;
        private final EntityDefinitionMapper definitionMapper;
        private final EntityFieldMapper fieldMapper;
        private final SystemEntityFieldPolicy systemEntityFieldPolicy;
        private final UiEventBindingMapper eventBindingMapper;
        private final JsonDocumentCodec codec;
        private final JdbcWriteAttempt writeAttempt;
        private UiMutableInterfaceReferenceNormalizer interfaceReferenceNormalizer;

        /**
         * 节点草稿落库前把历史 service/operation pair 迁移为 extensionId。
         * 使用可选 setter 以兼容不启动 Spring 的节点策略单元测试。
         *
         * @param value 待设置接口引用{@code normalizer}的原始输入，结果供调用方继续使用
         */
        @Autowired(required = false)
        public void setInterfaceReferenceNormalizer(
                        UiMutableInterfaceReferenceNormalizer value) {
                this.interfaceReferenceNormalizer = value;
        }

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
         * @throws IllegalArgumentException  配置非法或校验失败时抛出
         * @throws RevisionConflictException 节点 key 唯一冲突时抛出
         */
        @Transactional(rollbackFor = Exception.class)
        public EntityFormNode create(String formId, EntityFormNodeCreateRequest request) {
                return createInternal(formId, request, false);
        }

        /**
         * 创建内部；结果供后续流程传递或持久化。
         *
         * @param formId 表单ID，后续用于创建内部时定位或关联目标
         * @param request 本次请求，后续经校验后用于创建内部
         * @param migrateUnsupported 迁移{@code unsupported}，供本方法创建内部时使用
         * @return 创建后的内部结果，供调用方继续处理
         */
        private EntityFormNode createInternal(
                        String formId,
                        EntityFormNodeCreateRequest request,
                        boolean migrateUnsupported) {
                return createInternal(
                                formId,
                                request,
                                migrateUnsupported,
                                true);
        }

        /**
         * 创建内部；结果供后续流程传递或持久化。
         *
         * @param formId 表单ID，后续用于创建内部时定位或关联目标
         * @param request 本次请求，后续经校验后用于创建内部
         * @param migrateUnsupported 迁移{@code unsupported}，作为 {@code normalizeAndValidateConfiguration} 的输入影响后续处理
         * @param touchOwner 更新访问时间归属方，供本方法创建内部时使用
         * @return 创建后的内部结果，供调用方继续处理
         */
        private EntityFormNode createInternal(
                        String formId,
                        EntityFormNodeCreateRequest request,
                        boolean migrateUnsupported,
                        boolean touchOwner) {
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
                node.setCreatedAt(LocalDateTime.now());
                node.setUpdatedAt(LocalDateTime.now());
                node.setDeleted(0);
                if (!migrateUnsupported) {
                        validateCreateConfiguration(request, node.getNodeType());
                }
                normalizeAndValidateConfiguration(node, migrateUnsupported);
                validateNode(node, null);
                validateSystemNode(node);
                try {
                        writeAttempt.execute(() -> nodeMapper.insert(node));
                } catch (DuplicateKeyException exception) {
                        throw translateNodeWriteException(
                                        formId, node.getNodeKey(), node.getId(), exception);
                }
                if (touchOwner) {
                        touchForm(formId);
                }
                return node;
        }

        /**
         * 按补丁请求更新节点属性，基于乐观锁更新并校验绑定状态。
         *
         * @param formId  表单ID
         * @param nodeId  节点ID
         * @param request 节点补丁请求
         * @return 更新后的节点
         * @throws IllegalArgumentException  节点不存在、绑定不完整或配置非法时抛出
         * @throws RevisionConflictException 版本冲突时抛出
         */
        @Transactional(rollbackFor = Exception.class)
        public EntityFormNode patch(
                        String formId,
                        String nodeId,
                        EntityFormNodePatchRequest request) {
                return patchInternal(
                                formId, nodeId, request, PatchMode.USER_PROPERTY);
        }

        /**
         * 处理补丁内部，并将结果传给后续步骤。
         *
         * @param formId 表单ID，后续用于处理补丁内部时定位或关联目标
         * @param nodeId 节点ID，后续用于处理补丁内部时定位或关联目标
         * @param request 本次请求，后续经校验后用于处理补丁内部
         * @param mode 模式标识，决定后续补丁内部采用的处理分支
         * @return 处理后的补丁内部结果，供调用方继续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private EntityFormNode patchInternal(
                        String formId,
                        String nodeId,
                        EntityFormNodePatchRequest request,
                        PatchMode mode) {
                EntityFormNode current = requireNode(formId, nodeId);
                requireExpectedRevision(request.getExpectedRevision(), current);
                if (mode.userFacing()
                                && mode != PatchMode.USER_REPLACE
                                && !hasValidBindingState(current)) {
                        throw new IllegalArgumentException(
                                        "当前节点绑定配置不完整，请先通过配置迁移修复后再编辑");
                }
                if (mode.userFacing()) {
                        validatePatchConstraints(current, request, mode);
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
                validateSystemNode(updated);

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
                                .set("revision", updated.getRevision())
                                .set("update_time", updated.getUpdatedAt());
                int affected;
                try {
                        affected = writeAttempt.execute(() -> nodeMapper.update(null, wrapper));
                } catch (DuplicateKeyException exception) {
                        throw translateNodeWriteException(
                                        formId, updated.getNodeKey(), nodeId, exception);
                }
                if (affected != 1) {
                        throw conflict(formId, nodeId);
                }
                touchForm(formId);
                return requireNode(formId, nodeId);
        }

        /**
         * 调整节点在同级中的排序位置，必要时自动重平衡 orderKey。
         *
         * @param formId  表单ID
         * @param nodeId  节点ID
         * @param request 排序请求，指定前后相邻节点
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
         * 删除表单节点，存在子节点时拒绝删除；同时清理失去最后一个节点的字段事件草稿。
         * 已发布事件保存在独立快照中，不受草稿删除影响。
         *
         * @param formId           表单ID
         * @param nodeId           节点ID
         * @param expectedRevision 期望版本号
         * @throws IllegalArgumentException  存在子节点或版本冲突时抛出
         * @throws RevisionConflictException 版本冲突时抛出
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
                removeDeletedFieldEventBindings(formId, current);
                touchForm(formId);
        }

        /**
         * 字段事件按编码绑定；仅在最后一个同编码节点删除后清理本表单的 FIELD 绑定。
         * 与节点删除共用事务，防止重新添加同编码字段时接上旧执行链；
         * OWNER、BUTTON、实体默认事件以及其他表单的绑定不属于本次删除范围。
         *
         * @param formId 表单ID，后续用于移除已删除字段事件绑定集合时定位或关联目标
         * @param removed {@code removed}，作为 {@code projection.fieldEventTargetKeys} 的输入影响后续处理
         */
        private void removeDeletedFieldEventBindings(String formId, EntityFormNode removed) {
                EntityFormFieldProjection projection = new EntityFormFieldProjection(codec);
                Set<String> removedKeys = projection.fieldEventTargetKeys(List.of(removed));
                if (removedKeys.isEmpty()) {
                        return;
                }
                removedKeys.removeAll(projection.fieldEventTargetKeys(nodeMapper.findByFormId(formId)));
                if (removedKeys.isEmpty()) {
                        return;
                }
                eventBindingMapper.deleteFormFieldBindings(formId, removedKeys);
        }

        /**
         * 锁定表单下全部节点草稿，供配置级撤销在重算 hash 前建立并发边界。
         *
         * @param formId 表单ID，后续用于锁定草稿节点集合发布版本时定位或关联目标
         */
        public void lockDraftNodesForRelease(String formId) {
                nodeMapper.findAllByFormIdForUpdate(formId);
        }

        /**
         * 使用不可变发布快照精确重建表单节点草稿。
         *
         * <p>节点逻辑删除后原稳定 ID 仍占用主键，因此恢复时先物理清理当前
         * 草稿节点，再按父节点优先顺序以发布 ID 重建。调用方必须已锁定表单
         * owner；本方法不触碰 owner revision，确保一次撤销只递增一次配置修订号。</p>
         *
         * @param formId 表单ID，后续用于恢复已发布节点集合时定位或关联目标
         * @param publishedNodes 已发布节点集合，作为 {@code nodesInRestoreOrder} 的输入影响后续处理
         */
        @Transactional(rollbackFor = Exception.class)
        public void restorePublishedNodes(
                        String formId,
                        List<EntityFormNode> publishedNodes) {
                requireForm(formId);
                lockDraftNodesForRelease(formId);
                nodeMapper.deleteAllByFormIdForReleaseRestore(formId);
                List<EntityFormNode> ordered = nodesInRestoreOrder(
                                publishedNodes == null ? List.of() : publishedNodes);
                long fallbackOrder = ORDER_STEP;
                for (EntityFormNode source : ordered) {
                        createInternal(
                                        formId,
                                        toCreateRequest(
                                                        source,
                                                        fallbackOrder,
                                                        PatchMode.SYSTEM_IMPORT),
                                        true,
                                        false);
                        fallbackOrder += ORDER_STEP;
                }
                validateTree(formId);
        }

        /**
         * 整理节点集合恢复顺序数据，供调用方遍历或继续处理。
         *
         * @param nodes 节点集合，供本方法处理节点集合恢复顺序时使用
         * @return 实体表单节点集合，供调用方遍历或展示
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private List<EntityFormNode> nodesInRestoreOrder(
                        List<EntityFormNode> nodes) {
                Map<String, EntityFormNode> byId = new HashMap<>();
                for (EntityFormNode node : nodes) {
                        if (!StringUtils.hasText(node.getId())
                                        || byId.put(node.getId(), node) != null) {
                                throw new IllegalArgumentException(
                                                "发布快照包含缺失或重复的表单节点ID");
                        }
                }
                Map<String, Integer> depthById = new HashMap<>();
                List<EntityFormNode> ordered = new ArrayList<>(nodes);
                ordered.sort(Comparator
                                .comparingInt((EntityFormNode node) ->
                                                resolveNodeDepth(
                                                                node,
                                                                byId,
                                                                depthById))
                                .thenComparing(node ->
                                                node.getOrderKey() == null
                                                                ? Long.MAX_VALUE
                                                                : node.getOrderKey())
                                .thenComparing(EntityFormNode::getId));
                return ordered;
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
                                PatchMode.USER_REPLACE);
        }

        /**
         * 处理替换差异内部，并将结果传给后续步骤。
         *
         * @param formId 表单ID，后续用于处理替换差异内部时定位或关联目标
         * @param incoming {@code incoming}，供本方法处理替换差异内部时使用
         * @param expectedRevision 预期修订版本，作为 {@code requireFormForUpdate} 的输入影响后续处理
         * @param mode 模式标识，决定后续替换差异内部采用的处理分支
         */
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
                List<EntityFormNode> sources = incoming == null ? List.of() : incoming;
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
                                EntityFormNodeCreateRequest request = toCreateRequest(source, fallbackOrder, mode);
                                EntityFormNode created = createInternal(
                                                formId,
                                                request,
                                                mode == PatchMode.SYSTEM_IMPORT);
                                retained.add(created.getId());
                        } else {
                                retained.add(current.getId());
                                EntityFormNodePatchRequest request = toPatchRequest(source, current, mode);
                                if (hasChanges(source, current, mode)
                                                || requiresLegacyReleasePin(source)) {
                                        patchInternal(formId, current.getId(), request, mode);
                                }
                        }
                        fallbackOrder += ORDER_STEP;
                }
                for (EntityFormNode node : missingNodesInDeletionOrder(existing, retained)) {
                        delete(formId, node.getId(), node.getRevision());
                }
                validateTree(formId);
        }

        /**
         * 整理缺失节点集合{@code deletion}顺序数据，供调用方遍历或继续处理。
         *
         * @param existing 已有，供本方法处理缺失节点集合{@code deletion}顺序时使用
         * @param retained {@code retained}，供本方法处理缺失节点集合{@code deletion}顺序时使用
         * @return 实体表单节点集合，供调用方遍历或展示
         */
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

        /**
         * 解析节点深度；输出作为后续校验或处理的输入。
         *
         * @param node 节点，作为 {@code depthById.get} 的输入影响后续处理
         * @param byId ID，后续用于解析节点深度时定位或关联目标
         * @param depthById 深度ID，后续用于解析节点深度时定位或关联目标
         * @return 解析后的节点深度结果，供调用方继续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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

        /**
         * 校验已引用表单集合；不满足约束时阻止后续处理。
         *
         * @param formId 表单ID，后续用于校验已引用表单集合时定位或关联目标
         * @param currentDraftNodes 当前草稿节点集合，作为 {@code validateReferencedFormGraph} 的输入影响后续处理
         */
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

        /**
         * 校验已引用表单图；不满足约束时阻止后续处理。
         *
         * @param formId 表单ID，后续用于校验已引用表单图时定位或关联目标
         * @param references 引用，供本方法校验已引用表单图时使用
         * @param depth 深度，供本方法校验已引用表单图时使用
         * @param path 路径，作为 {@code IllegalArgumentException} 的输入影响后续处理
         * @param releaseReferenceCache 发布版本引用缓存，供本方法校验已引用表单图时使用
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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
                        List<FormReleaseReference> childReferences = releaseReferenceCache.computeIfAbsent(
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

        /**
         * 整理已引用表单{@code releases}数据，供调用方遍历或继续处理。
         *
         * @param nodes 节点集合，供本方法处理已引用表单{@code releases}时使用
         * @return 表单发布版本引用集合，供调用方遍历或展示
         */
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

        /**
         * 整理已引用表单{@code releases}数据，供调用方遍历或继续处理。
         *
         * @param release 发布版本，作为 {@code codec.readObject} 的输入影响后续处理
         * @return 表单发布版本引用集合，供调用方遍历或展示
         */
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

        /**
         * 读取快照节点属性；查询结果供调用方展示或继续处理。
         *
         * @param node 节点，作为 {@code objectMap} 的输入影响后续处理
         * @return 快照节点属性键值结果，供调用方继续处理
         */
        private Map<String, Object> readSnapshotNodeProps(Map<?, ?> node) {
                Object propsDocument = node.get("propsDocument");
                if (propsDocument instanceof String document
                                && StringUtils.hasText(document)) {
                        return codec.readObject(document, "子表单发布节点属性");
                }
                return objectMap(node.get("props"), "子表单发布节点属性");
        }

        /**
         * 添加表单发布版本引用；结果供后续流程传递或持久化。
         *
         * @param references 引用，供本方法添加表单发布版本引用时使用
         * @param reference 引用，作为 {@code references.add} 的输入影响后续处理
         */
        private void addFormReleaseReference(
                        List<FormReleaseReference> references,
                        FormReleaseReference reference) {
                if (reference == null) {
                        return;
                }
                boolean exists = references.stream()
                                .anyMatch(existing -> Objects.equals(existing.formId(), reference.formId())
                                                && Objects.equals(existing.releaseId(), reference.releaseId()));
                if (!exists) {
                        references.add(reference);
                }
        }

        /**
         * 规范化子级表单属性；输出作为后续校验或处理的输入。
         *
         * @param nodeType 节点类型标识，决定后续子级表单属性采用的处理分支
         * @param source 待规范化子级表单属性的原始输入，结果供调用方继续使用
         * @param explicitFormId {@code explicit}表单ID，后续用于规范化子级表单属性时定位或关联目标
         * @param explicitReleaseId {@code explicit}发布版本ID，后续用于规范化子级表单属性时定位或关联目标
         * @param explicitReleaseVersion {@code explicit}发布版本，作为 {@code firstInteger} 的输入影响后续处理
         * @param pinLegacyReference 固定旧版引用，供本方法规范化子级表单属性时使用
         * @return 子级表单属性键值结果，供调用方继续处理
         */
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
                Map<String, Object> componentProps = objectMap(props.get("componentProps"), "子表单组件属性");
                Map<String, Object> nestedConfig = objectMap(componentProps.get("subFormConfig"), "子表单配置");
                Map<String, Object> directConfig = objectMap(props.get("subFormConfig"), "子表单配置");

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
                                        : releaseMapper.findByVersion(
                                                        "FORM",
                                                        formId,
                                                        releaseVersion);
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

                Map<String, Object> normalizedNestedConfig = new LinkedHashMap<>(nestedConfig);
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

        /**
         * 清理子级表单绑定集合；后续读取或执行将使用更新后的状态。
         *
         * @param source 待清理子级表单绑定集合的原始输入，结果供调用方继续使用
         * @param clearFields {@code clear}字段，供本方法清理子级表单绑定集合时使用
         * @return 子级表单绑定集合键值结果，供调用方继续处理
         */
        private Map<String, Object> clearSubFormBindings(
                        Map<String, Object> source,
                        Set<String> clearFields) {
                Map<String, Object> props = mutableMap(source);
                if (clearFields == null || clearFields.isEmpty()) {
                        return props;
                }
                Map<String, Object> componentProps = objectMap(props.get("componentProps"), "子表单组件属性");
                Map<String, Object> nestedConfig = objectMap(componentProps.get("subFormConfig"), "子表单配置");
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

        /**
         * 移除表单ID{@code aliases}；后续读取或执行将使用更新后的状态。
         *
         * @param value 待移除表单ID{@code aliases}的原始输入，结果供调用方继续使用
         */
        private void removeFormIdAliases(Map<String, Object> value) {
                value.remove("childFormId");
                value.remove("refFormId");
                value.remove("publishedFormId");
        }

        /**
         * 移除发布版本ID{@code aliases}；后续读取或执行将使用更新后的状态。
         *
         * @param value 待移除发布版本ID{@code aliases}的原始输入，结果供调用方继续使用
         */
        private void removeReleaseIdAliases(Map<String, Object> value) {
                value.remove("childFormReleaseId");
                value.remove("refFormReleaseId");
                value.remove("publishedFormReleaseId");
        }

        /**
         * 移除发布版本{@code aliases}；后续读取或执行将使用更新后的状态。
         *
         * @param value 待移除发布版本{@code aliases}的原始输入，结果供调用方继续使用
         */
        private void removeReleaseVersionAliases(Map<String, Object> value) {
                value.remove("childFormReleaseVersion");
                value.remove("refFormReleaseVersion");
                value.remove("publishedFormReleaseVersion");
        }

        /**
         * 规范化关系绑定子级表单属性；输出作为后续校验或处理的输入。
         *
         * @param node 节点，作为 {@code requireBoundRelation} 的输入影响后续处理
         * @return 关系绑定子级表单属性键值结果，供调用方继续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private Map<String, Object> normalizeRelationBoundSubFormProps(
                        EntityFormNode node) {
                EntityRelation relation = requireBoundRelation(node);
                if (relation.getOwnershipType() != EntityRelation.OwnershipType.COMPOSITION) {
                        throw new IllegalArgumentException("子表单／明细编辑必须使用组成关系；普通关联请添加关联表单或列表");
                }
                String expectedNodeType = relation.getRelationType() == EntityRelation.RelationType.ONE_TO_ONE
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

                Map<String, Object> props = mutableMap(read(node.getPropsDocument(), "表单节点属性"));
                String relationDataKey = effectiveDataKey(relation);
                if (StringUtils.hasText(relationDataKey)) {
                        putCanonicalRelationValue(
                                        props,
                                        "fieldCode",
                                        relationDataKey);
                }
                Map<String, Object> componentProps = objectMap(props.get("componentProps"), "子表单组件属性");
                Map<String, Object> subFormConfig = objectMap(componentProps.get("subFormConfig"), "子表单配置");
                putCanonicalRelationValue(
                                subFormConfig,
                                "relationCode",
                                relation.getRelationCode());
                if (StringUtils.hasText(relationDataKey)) {
                        putCanonicalRelationValue(
                                        subFormConfig,
                                        "dataKey",
                                        relationDataKey);
                }
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

        /**
         * 写入规范关系值；后续读取或执行将使用更新后的状态。
         *
         * @param target 目标，供本方法写入规范关系值时使用
         * @param key 键，后续用于授权校验、关联或幂等去重
         * @param expected 预期，作为 {@code target.put} 的输入影响后续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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

        /**
         * 校验并获取绑定关系；不满足约束时阻止后续处理。
         *
         * @param node 节点，作为 {@code requireForm} 的输入影响后续处理
         * @return 校验并获取后的绑定关系结果，供调用方继续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private EntityRelation requireBoundRelation(EntityFormNode node) {
                EntityForm form = requireForm(node.getFormId());
                EntityRelation relation = relationMapper.selectActiveByBindingRef(
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

        /**
         * 校验子级表单发布版本绑定；不满足约束时阻止后续处理。
         *
         * @param node 节点，作为 {@code readFormReleaseReference} 的输入影响后续处理
         */
        private void validateSubFormReleaseBinding(EntityFormNode node) {
                if (!Set.of("SUB_FORM", "REPEATER").contains(node.getNodeType())) {
                        return;
                }
                FormReleaseReference reference = readFormReleaseReference(
                                read(node.getPropsDocument(), "子表单节点属性"),
                                node.getNodeKey());
                if (reference != null) {
                        UiConfigRelease release = requireReferencedRelease(reference);
                        validateRelationReleaseEntity(node, release);
                        subFormParameterContractValidator().validateNode(
                                        requireForm(node.getFormId()),
                                        node,
                                        release);
                }
        }

        /**
         * 校验发布快照中的子表单参数契约。
         *
         * <p>
         * 用于历史版本激活和热修复快照校验，避免只校验当前草稿节点而遗漏
         * 快照中已经失效的参数或子字段映射。
         * </p>
         *
         * @param parentForm 父级表单，供本方法校验快照子级表单参数{@code contracts}时使用
         */
        public void validateSnapshotSubFormParameterContracts(
                        EntityForm parentForm) {
                subFormParameterContractValidator()
                                .validateSnapshot(parentForm);
        }

        /**
         * 处理子级表单参数契约校验器，并将结果传给后续步骤。
         *
         * @return 处理后的子级表单参数契约校验器结果，供调用方继续处理
         */
        private SubFormParameterContractReleaseValidator subFormParameterContractValidator() {
                return new SubFormParameterContractReleaseValidator(
                                formMapper,
                                fieldMapper,
                                relationMapper,
                                releaseMapper,
                                codec);
        }

        /**
         * 校验关系发布版本实体；不满足约束时阻止后续处理。
         *
         * @param node 节点，作为 {@code requireBoundRelation} 的输入影响后续处理
         * @param release 发布版本，作为 {@code formMapper.selectById} 的输入影响后续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private void validateRelationReleaseEntity(
                        EntityFormNode node,
                        UiConfigRelease release) {
                if (!"RELATION".equals(
                                normalize(node.getBindingType(), "NONE"))) {
                        return;
                }
                EntityRelation relation = requireBoundRelation(node);
                EntityForm childForm = formMapper.selectById(release.getConfigId());
                if (childForm == null
                                || !Objects.equals(
                                                relation.getChildEntityId(),
                                                childForm.getEntityId())) {
                        throw new IllegalArgumentException(
                                        "子表单发布版本所属实体与绑定关系不一致: "
                                                        + release.getConfigId());
                }
        }

        /**
         * 读取表单发布版本引用；查询结果供调用方展示或继续处理。
         *
         * @param props 属性，作为 {@code objectMap} 的输入影响后续处理
         * @param nodeLabel 节点标签，后续用于读取表单发布版本引用时匹配或展示
         * @return 读取后的表单发布版本引用结果，供调用方继续处理
         */
        private FormReleaseReference readFormReleaseReference(
                        Map<String, Object> props,
                        String nodeLabel) {
                if (props == null || props.isEmpty()) {
                        return null;
                }
                Map<String, Object> componentProps = objectMap(props.get("componentProps"), "子表单组件属性");
                Map<String, Object> nestedConfig = objectMap(componentProps.get("subFormConfig"), "子表单配置");
                Map<String, Object> directConfig = objectMap(props.get("subFormConfig"), "子表单配置");
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

        /**
         * 校验并获取已引用发布版本；不满足约束时阻止后续处理。
         *
         * @param reference 引用，作为 {@code releaseMapper.selectById} 的输入影响后续处理
         * @return 校验并获取后的已引用发布版本结果，供调用方继续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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

        /**
         * 整理可变映射数据，供调用方遍历或继续处理。
         *
         * @param source 待处理可变映射的原始输入，结果供调用方继续使用
         * @return 可变映射键值结果，供调用方继续处理
         */
        private Map<String, Object> mutableMap(Map<String, Object> source) {
                return source == null
                                ? new LinkedHashMap<>()
                                : new LinkedHashMap<>(source);
        }

        /**
         * 整理对象映射数据，供调用方遍历或继续处理。
         *
         * @param value 待处理对象映射的原始输入，结果供调用方继续使用
         * @param label 标签，后续用于处理对象映射时匹配或展示
         * @return 对象映射键值结果，供调用方继续处理
         */
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

        /**
         * 按候选顺序取首个非空文本，供后续匹配或展示使用。
         *
         * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
         * @return 处理后的首个文本文本，供调用方比较或展示
         */
        private String firstText(Object... values) {
                for (Object value : values) {
                        String text = text(value);
                        if (StringUtils.hasText(text)) {
                                return text.trim();
                        }
                }
                return null;
        }

        /**
         * 处理首个整数，并将结果传给后续步骤。
         *
         * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
         * @return 处理后的首个整数结果，供调用方继续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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
         * 封装表单发布版本引用的不可变数据；各分量供后续校验、传递或结果展示使用。
         *
         * @param formId 表单 ID，后续用于定位已发布表单
         * @param releaseId 发布版本 ID，后续用于解析固定配置
         * @param releaseVersion 发布版本号，后续用于校验快照一致性
         */
        private record FormReleaseReference(
                        String formId,
                        String releaseId,
                        Integer releaseVersion) {
        }

        /**
         * 校验创建配置；不满足约束时阻止后续处理。
         *
         * @param request 本次请求，后续经校验后用于校验创建配置
         * @param nodeType 节点类型标识，决定后续创建配置采用的处理分支
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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
        }

        /**
         * 规范化与校验配置；输出作为后续校验或处理的输入。
         *
         * @param node 节点，作为 {@code normalize} 的输入影响后续处理
         * @param migrateUnsupported 迁移{@code unsupported}，供本方法规范化与校验配置时使用
         */
        private void normalizeAndValidateConfiguration(
                        EntityFormNode node,
                        boolean migrateUnsupported) {
                String nodeType = normalize(node.getNodeType(), "FIELD");
                Map<String, Object> inactive = new LinkedHashMap<>();

                EntityFormNodePropertyPolicy.NormalizedProps normalizedProps = EntityFormNodePropertyPolicy
                                .normalizeProps(
                                                nodeType,
                                                read(node.getPropsDocument(), "表单节点属性"),
                                                migrateUnsupported);
                node.setPropsDocument(write(
                                normalizedProps.active(), "表单节点属性"));
                if (!normalizedProps.inactive().isEmpty()) {
                        inactive.put("props", normalizedProps.inactive());
                }

                Map<String, Object> rules = read(node.getRulesDocument(), "表单节点规则");
                try {
                        EntityFormNodePropertyPolicy.NormalizedRules normalizedRules = EntityFormNodePropertyPolicy
                                        .normalizeRules(
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
                        Map<String, Object> normalizedBindings = EntityFormNodePropertyPolicy
                                        .normalizeDataSourceBindings(
                                                        nodeType, bindings);
                        if (interfaceReferenceNormalizer != null) {
                                normalizedBindings = interfaceReferenceNormalizer
                                                .normalizeBindings(normalizedBindings);
                        }
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

        /**
         * 合并{@code inactive}配置；结果供后续流程传递或持久化。
         *
         * @param node 节点，作为 {@code mutableMap} 的输入影响后续处理
         * @param nodeType 节点类型标识，决定后续{@code inactive}配置采用的处理分支
         * @param inactive {@code inactive}，作为 {@code typeProperties.putAll} 的输入影响后续处理
         */
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

        /**
         * 校验节点；不满足约束时阻止后续处理。
         *
         * @param node 节点，作为 {@code node.setNodeType} 的输入影响后续处理
         * @param excludeId 排除ID，后续用于校验节点时定位或关联目标
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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
                // 发布前再次核对权威关系，防止保存节点后实体关系已改为普通关联或更换外键。
                if (SUB_FORM_NODE_TYPES.contains(node.getNodeType()) && "RELATION".equals(node.getBindingType())) {
                        normalizeRelationBoundSubFormProps(node);
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
                String componentExtensionType =
                                UiExtensionReferencePolicy
                                                .resolveNodeExtensionType(
                                                                node.getNodeType(),
                                                                read(
                                                                                node.getPropsDocument(),
                                                                                "表单节点扩展属性"));
                if (UiExtensionReferencePolicy.FIELD.equals(
                                componentExtensionType)
                                && !StringUtils.hasText(
                                                node.getComponentName())) {
                        throw new IllegalArgumentException(
                                        "FIELD 字段组件扩展必须配置 componentName");
                }
                LambdaQueryWrapper<EntityFormNode> duplicateQuery = new LambdaQueryWrapper<EntityFormNode>()
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

        /**
         * 校验系统节点；不满足约束时阻止后续处理。
         *
         * @param node 节点，作为 {@code formMapper.selectById} 的输入影响后续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private void validateSystemNode(EntityFormNode node) {
                EntityForm form = formMapper.selectById(node.getFormId());
                EntityDefinition entity = form == null
                                ? null
                                : definitionMapper.selectById(form.getEntityId());
                if (entity == null
                                || entity.getStorageMode() != EntityDefinition.StorageMode.SYSTEM) {
                        return;
                }
                if ("ACTION_SLOT".equals(node.getNodeType())) {
                        throw new IllegalArgumentException(
                                        "平台系统表查看表单不能配置动作插槽");
                }
                if (StringUtils.hasText(node.getComponentName())) {
                        throw new IllegalArgumentException(
                                        "平台系统表查看表单不能配置自定义写入组件");
                }
                Map<String, Object> rules = read(
                                node.getRulesDocument(), "表单节点规则");
                if (rules != null && !rules.isEmpty()) {
                        throw new IllegalArgumentException(
                                        "平台系统表查看表单不能配置提交校验规则");
                }
                Map<String, Object> bindings = read(
                                node.getDataSourceBindingsDocument(),
                                "表单节点数据源绑定");
                if (bindings != null) {
                        for (String usage : bindings.keySet()) {
                                if (!SYSTEM_READ_ONLY_DATA_SOURCE_USAGES.contains(
                                                normalize(usage, ""))) {
                                        throw new IllegalArgumentException(
                                                        "平台系统表查看表单不允许数据源位置: "
                                                                        + usage);
                                }
                        }
                }
                if (!"ENTITY_FIELD".equals(node.getBindingType())) {
                        return;
                }
                EntityField field = fieldMapper.findByEntityIdAndFieldCode(
                                entity.getId(), node.getBindingRef());
                if (field == null
                                || !systemEntityFieldPolicy
                                                .isUiConfigurable(entity, field)) {
                        throw new IllegalArgumentException(
                                        "平台系统表字段不可配置: "
                                                        + node.getBindingRef());
                }
                Map<String, Object> props = read(
                                node.getPropsDocument(), "表单节点属性");
                if (props == null
                                || !Boolean.TRUE.equals(props.get("readonly"))) {
                        throw new IllegalArgumentException(
                                        "平台系统表字段节点必须设置为只读");
                }
                String configuredFieldCode = text(
                                props.get("fieldCode"));
                String configuredFieldId = text(
                                props.get("fieldId"));
                if (StringUtils.hasText(configuredFieldCode)
                                && !Objects.equals(
                                                configuredFieldCode,
                                                field.getFieldCode())) {
                        throw new IllegalArgumentException(
                                        "平台系统表字段编码与字段目录不一致");
                }
                if (StringUtils.hasText(configuredFieldId)
                                && !Objects.equals(
                                                configuredFieldId,
                                                field.getId())) {
                        throw new IllegalArgumentException(
                                        "平台系统表字段标识与字段目录不一致");
                }
        }

        /**
         * 校验父级；不满足约束时阻止后续处理。
         *
         * @param formId 表单ID，后续用于校验父级时定位或关联目标
         * @param nodeId 节点ID，后续用于校验父级时定位或关联目标
         * @param parentId 父级ID，后续用于校验父级时定位或关联目标
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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

        /**
         * 处理当前{@code subtree}{@code height}，并将结果传给后续步骤。
         *
         * @param formId 表单ID，后续用于处理当前{@code subtree}{@code height}时定位或关联目标
         * @param nodeId 节点ID，后续用于处理当前{@code subtree}{@code height}时定位或关联目标
         * @return 处理后的当前{@code subtree}{@code height}结果，供调用方继续处理
         */
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

        /**
         * 处理当前{@code subtree}{@code height}，并将结果传给后续步骤。
         *
         * @param nodeId 节点ID，后续用于处理当前{@code subtree}{@code height}时定位或关联目标
         * @param childrenByParent 子节点父级，供本方法处理当前{@code subtree}{@code height}时使用
         * @param visiting {@code visiting}，供本方法处理当前{@code subtree}{@code height}时使用
         * @return 处理后的当前{@code subtree}{@code height}结果，供调用方继续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private int currentSubtreeHeight(
                        String nodeId,
                        Map<String, List<EntityFormNode>> childrenByParent,
                        Set<String> visiting) {
                if (!visiting.add(nodeId)) {
                        throw new IllegalArgumentException("节点子树存在循环引用");
                }
                int height = 1;
                for (EntityFormNode child : childrenByParent.getOrDefault(nodeId, List.of())) {
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

        /**
         * 校验父级子级类型；不满足约束时阻止后续处理。
         *
         * @param child 子级，供本方法校验父级子级类型时使用
         * @param parent 父级，作为 {@code ALLOWED_CHILD_TYPES.get} 的输入影响后续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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

        /**
         * 校验已有子节点；不满足约束时阻止后续处理。
         *
         * @param parent 父级，作为 {@code nodeMapper.findSiblings} 的输入影响后续处理
         */
        private void validateExistingChildren(EntityFormNode parent) {
                List<EntityFormNode> children = nodeMapper.findSiblings(parent.getFormId(), parent.getId());
                for (EntityFormNode child : children) {
                        validateParentChildType(child, parent);
                }
        }

        /**
         * 应用补丁，并将结果传给后续步骤。
         *
         * @param target 目标，作为 {@code target.setPropsDocument} 的输入影响后续处理
         * @param request 本次请求，后续经校验后用于应用补丁
         */
        private void applyPatch(EntityFormNode target, EntityFormNodePatchRequest request) {
                Set<String> clear = request.getClearFields() == null
                                ? Set.of()
                                : request.getClearFields();
                if (request.getParentId() != null || clear.contains("parentId")) {
                        target.setParentId(clear.contains("parentId")
                                        ? null
                                        : blankToNull(request.getParentId()));
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
                                        ? null
                                        : blankToNull(request.getBindingRef()));
                }
                if (request.getComponentName() != null
                                || clear.contains("componentName")) {
                        target.setComponentName(clear.contains("componentName")
                                        ? null
                                        : blankToNull(request.getComponentName()));
                }
                if (request.getComponentVersion() != null
                                || clear.contains("componentVersion")) {
                        target.setComponentVersion(clear.contains("componentVersion")
                                        ? null
                                        : request.getComponentVersion());
                }
                if (request.getSnapshotVersion() != null
                                || clear.contains("snapshotVersion")) {
                        target.setSnapshotVersion(clear.contains("snapshotVersion")
                                        ? null
                                        : request.getSnapshotVersion());
                }
                boolean hasExplicitSubFormBinding = request.getChildFormId() != null
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
                                        ? null
                                        : write(request.getRules(), "表单节点规则"));
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
        }

        /**
         * 校验补丁{@code constraints}；不满足约束时阻止后续处理。
         *
         * @param current 当前，作为 {@code validateTechnicalIdentity} 的输入影响后续处理
         * @param request 本次请求，后续经校验后用于校验补丁{@code constraints}
         * @param mode 模式标识，决定后续补丁{@code constraints}采用的处理分支
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private void validatePatchConstraints(
                        EntityFormNode current,
                        EntityFormNodePatchRequest request,
                        PatchMode mode) {
                Set<String> clear = request.getClearFields() == null
                                ? Set.of()
                                : request.getClearFields();
                for (String field : clear) {
                        if (!CLEARABLE_PATCH_FIELDS.contains(field)) {
                                throw new IllegalArgumentException("不支持清空表单节点字段: " + field);
                        }
                }
                String nodeType = request.getNodeType() == null
                                ? normalize(current.getNodeType(), "FIELD")
                                : normalize(request.getNodeType(), "FIELD");
                validateTechnicalIdentity(current, request, clear, mode);
                validateRequestedPatchConfiguration(
                                current,
                                nodeType,
                                request,
                                clear);
        }

        /**
         * 校验{@code technical}身份；不满足约束时阻止后续处理。
         *
         * @param current 当前，作为 {@code normalize} 的输入影响后续处理
         * @param request 本次请求，后续经校验后用于校验{@code technical}身份
         * @param clear {@code clear}，作为 {@code validateUnboundSubFormRelationRepair} 的输入影响后续处理
         * @param mode 模式标识，决定后续{@code technical}身份采用的处理分支
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private void validateTechnicalIdentity(
                        EntityFormNode current,
                        EntityFormNodePatchRequest request,
                        Set<String> clear,
                        PatchMode mode) {
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
                if (mode == PatchMode.USER_PROPERTY
                                && request.getOrderKey() != null
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
                if (mode == PatchMode.USER_REPLACE
                                && isUnboundSubFormRelationRepair(
                                                current, request, clear)) {
                        validateUnboundSubFormRelationRepair(
                                        current, request, clear);
                } else if (hasValidBindingState(current)) {
                        validateBoundNodeIdentity(current, request, clear);
                } else if (mode == PatchMode.USER_REPLACE) {
                        validateInvalidBindingRepair(current, request, clear);
                }
        }

        /**
         * 判断是否{@code unbound}子级表单关系{@code repair}；判断结果决定调用方的后续分支。
         *
         * @param current 当前，作为 {@code normalize} 的输入影响后续处理
         * @param request 本次请求，后续经校验后用于判断是否{@code unbound}子级表单关系{@code repair}
         * @param clear {@code clear}，供本方法判断是否{@code unbound}子级表单关系{@code repair}时使用
         * @return {@code unbound}子级表单关系{@code repair}条件成立时为 true，否则为 false
         */
        private boolean isUnboundSubFormRelationRepair(
                        EntityFormNode current,
                        EntityFormNodePatchRequest request,
                        Set<String> clear) {
                if (!SUB_FORM_NODE_TYPES.contains(
                                normalize(current.getNodeType(), "FIELD"))
                                || !"NONE".equals(
                                                normalize(current.getBindingType(), "NONE"))
                                || StringUtils.hasText(current.getBindingRef())) {
                        return false;
                }
                String requestedType = request.getBindingType() == null
                                ? "NONE"
                                : normalize(request.getBindingType(), "NONE");
                String requestedRef = clear.contains("bindingRef")
                                ? null
                                : blankToNull(request.getBindingRef());
                return "RELATION".equals(requestedType)
                                && StringUtils.hasText(requestedRef);
        }

        /**
         * 校验{@code unbound}子级表单关系{@code repair}；不满足约束时阻止后续处理。
         *
         * @param current 当前，作为 {@code validateInvalidBindingRepair} 的输入影响后续处理
         * @param request 本次请求，后续经校验后用于校验{@code unbound}子级表单关系{@code repair}
         * @param clear {@code clear}，作为 {@code validateInvalidBindingRepair} 的输入影响后续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private void validateUnboundSubFormRelationRepair(
                        EntityFormNode current,
                        EntityFormNodePatchRequest request,
                        Set<String> clear) {
                validateInvalidBindingRepair(current, request, clear);
                EntityForm form = requireForm(current.getFormId());
                EntityRelation relation = relationMapper.selectActiveByBindingRef(
                                form.getEntityId(),
                                request.getBindingRef());
                if (relation == null) {
                        throw new IllegalArgumentException(
                                        "表单节点绑定的实体关系不存在或已禁用: "
                                                        + request.getBindingRef());
                }
                Map<String, Object> props = request.getProps() == null
                                ? read(current.getPropsDocument(), "表单节点属性")
                                : request.getProps();
                String fieldCode = blankToNull(
                                Objects.toString(props.get("fieldCode"), null));
                if (StringUtils.hasText(effectiveDataKey(relation))
                                && !Objects.equals(
                                                effectiveDataKey(relation), fieldCode)) {
                        throw new IllegalArgumentException(
                                        "子表单节点字段与实体关系不匹配: fieldCode");
                }
        }

        /**
         * 生成有效数据键文本，供后续匹配或展示。
         *
         * @param relation 关系，供本方法处理有效数据键时使用
         * @return 处理后的有效数据键文本，供调用方比较或展示
         */
        private String effectiveDataKey(EntityRelation relation) {
                if (StringUtils.hasText(relation.getDataKey())) {
                        return relation.getDataKey();
                }
                if (StringUtils.hasText(relation.getParentFieldCode())) {
                        return relation.getParentFieldCode();
                }
                // 旧测试桩或历史导入数据可能只保留 relationCode；此时不能
                // 把关系标识误当成表单数据属性，沿用节点自身 fieldCode。
                return null;
        }

        /**
         * 校验无效绑定{@code repair}；不满足约束时阻止后续处理。
         *
         * @param current 当前，作为 {@code blankToNull} 的输入影响后续处理
         * @param request 本次请求，后续经校验后用于校验无效绑定{@code repair}
         * @param clear {@code clear}，作为 {@code validateBoundProps} 的输入影响后续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private void validateInvalidBindingRepair(
                        EntityFormNode current,
                        EntityFormNodePatchRequest request,
                        Set<String> clear) {
                String bindingType = request.getBindingType() == null
                                ? normalize(current.getBindingType(), "NONE")
                                : normalize(request.getBindingType(), "NONE");
                String bindingRef = clear.contains("bindingRef")
                                ? null
                                : request.getBindingRef() == null
                                                ? blankToNull(current.getBindingRef())
                                                : blankToNull(request.getBindingRef());
                try {
                        EntityFormNodePropertyPolicy.validateBinding(
                                        current.getNodeType(),
                                        bindingType,
                                        bindingRef);
                } catch (IllegalArgumentException exception) {
                        throw new IllegalArgumentException(
                                        "保存全部草稿时必须修复当前节点的非法绑定: "
                                                        + exception.getMessage(),
                                        exception);
                }
                if (isBound(current)) {
                        validateBoundProps(current, request, clear);
                }
        }

        /**
         * 校验绑定节点身份；不满足约束时阻止后续处理。
         *
         * @param current 当前，作为 {@code normalize} 的输入影响后续处理
         * @param request 本次请求，后续经校验后用于校验绑定节点身份
         * @param clear {@code clear}，作为 {@code validateBoundProps} 的输入影响后续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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
                                                                ? null
                                                                : blankToNull(request.getBindingRef()),
                                                blankToNull(current.getBindingRef()))) {
                        throw new IllegalArgumentException("已绑定节点不能修改绑定引用");
                }
                if (isBound(current)) {
                        validateBoundProps(current, request, clear);
                }
        }

        /**
         * 校验读取仅展示属性；不满足约束时阻止后续处理。
         *
         * @param current 当前，作为 {@code normalize} 的输入影响后续处理
         * @param request 本次请求，后续经校验后用于校验读取仅展示属性
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private void validateReadOnlyDisplayProps(
                        EntityFormNode current,
                        EntityFormNodePatchRequest request) {
                String nodeType = normalize(current.getNodeType(), "FIELD");
                if (EDITABLE_LABEL_NODE_TYPES.contains(nodeType)
                                || request.getProps() == null) {
                        return;
                }
                Map<String, Object> currentProps = read(current.getPropsDocument(), "表单节点属性");
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

        /**
         * 校验绑定属性；不满足约束时阻止后续处理。
         *
         * @param current 当前，作为 {@code read} 的输入影响后续处理
         * @param request 本次请求，后续经校验后用于校验绑定属性
         * @param clear {@code clear}，供本方法校验绑定属性时使用
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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
                Map<String, Object> currentProps = read(current.getPropsDocument(), "表单节点属性");
                Map<String, Object> requestedProps = request.getProps();
                requireSameMeaningfulValues(
                                currentProps,
                                requestedProps,
                                IMMUTABLE_BOUND_PROP_KEYS,
                                "已绑定节点不能修改字段身份属性");

                Map<String, Object> currentComponentProps = objectMap(
                                currentProps.get("componentProps"),
                                "字段组件属性");
                Map<String, Object> requestedComponentProps = objectMap(
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

        /**
         * 校验并获取相同{@code meaningful}值集合；不满足约束时阻止后续处理。
         *
         * @param current 当前，供本方法校验并获取相同{@code meaningful}值集合时使用
         * @param requested 请求，供本方法校验并获取相同{@code meaningful}值集合时使用
         * @param keys 键集合，供本方法校验并获取相同{@code meaningful}值集合时使用
         * @param message 消息，作为 {@code IllegalArgumentException} 的输入影响后续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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

        /**
         * 校验请求补丁配置；不满足约束时阻止后续处理。
         *
         * @param current 当前，供本方法校验请求补丁配置时使用
         * @param nodeType 节点类型标识，决定后续请求补丁配置采用的处理分支
         * @param request 本次请求，后续经校验后用于校验请求补丁配置
         * @param clear {@code clear}，供本方法校验请求补丁配置时使用
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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

        /**
         * 判断是否具有有效绑定状态；判断结果决定调用方的后续分支。
         *
         * @param node 节点，作为 {@code EntityFormNodePropertyPolicy.validateBinding} 的输入影响后续处理
         * @return 有效绑定状态条件成立时为 true，否则为 false
         */
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

        /**
         * 判断是否绑定；判断结果决定调用方的后续分支。
         *
         * @param node 节点，作为 {@code equals} 的输入影响后续处理
         * @return 绑定条件成立时为 true，否则为 false
         */
        private boolean isBound(EntityFormNode node) {
                return !"NONE".equals(normalize(node.getBindingType(), "NONE"))
                                || StringUtils.hasText(node.getBindingRef());
        }

        /**
         * 复制实体表单节点；结果供后续流程传递或持久化。
         *
         * @param source 待复制实体表单节点的原始输入，结果供调用方继续使用
         * @return 复制后的实体表单节点结果，供调用方继续处理
         */
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
                target.setCreatedAt(source.getCreatedAt());
                target.setDeleted(source.getDeleted());
                return target;
        }

        /**
         * 转换为创建请求；输出作为后续校验或处理的输入。
         *
         * @param source 待转换为创建请求的原始输入，结果供调用方继续使用
         * @param fallbackOrder 兜底顺序，主值不可用时供后续处理兜底
         * @param mode 模式标识，决定后续创建请求采用的处理分支
         * @return 转换为后的创建请求结果，供调用方继续处理
         */
        private EntityFormNodeCreateRequest toCreateRequest(
                        EntityFormNode source,
                        long fallbackOrder,
                        PatchMode mode) {
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
                if (mode == PatchMode.SYSTEM_IMPORT) {
                        request.setLegacyProps(
                                        read(source.getLegacyPropsDocument(), "历史节点属性"));
                }
                request.setOrderKey(source.getOrderKey() == null
                                ? fallbackOrder
                                : source.getOrderKey());
                return request;
        }

        /**
         * 转换为补丁请求；输出作为后续校验或处理的输入。
         *
         * @param source 待转换为补丁请求的原始输入，结果供调用方继续使用
         * @param current 当前，供本方法转换为补丁请求时使用
         * @param mode 模式标识，决定后续补丁请求采用的处理分支
         * @return 转换为后的补丁请求结果，供调用方继续处理
         */
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
                }
                request.setOrderKey(source.getOrderKey());
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
                request.setClearFields(clear.isEmpty() ? null : clear);
                return request;
        }

        /**
         * 添加{@code clear}条件缺失；结果供后续流程传递或持久化。
         *
         * @param clear {@code clear}，供本方法添加{@code clear}条件缺失时使用
         * @param field 字段，作为 {@code clear.add} 的输入影响后续处理
         * @param sourceValue 来源值，供本方法添加{@code clear}条件缺失时使用
         * @param currentValue 当前值，供本方法添加{@code clear}条件缺失时使用
         */
        private void addClearIfMissing(
                        Set<String> clear,
                        String field,
                        Object sourceValue,
                        Object currentValue) {
                if (sourceValue == null && currentValue != null) {
                        clear.add(field);
                }
        }

        /**
         * 判断是否具有变更集合；判断结果决定调用方的后续分支。
         *
         * @param source 待判断是否具有变更集合的原始输入，结果供调用方继续使用
         * @param current 当前，供本方法判断是否具有变更集合时使用
         * @param mode 模式标识，决定后续变更集合采用的处理分支
         * @return 变更集合条件成立时为 true，否则为 false
         */
        private boolean hasChanges(
                        EntityFormNode source,
                        EntityFormNode current,
                        PatchMode mode) {
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
                                || mode == PatchMode.SYSTEM_IMPORT
                                                && !Objects.equals(
                                                                source.getLegacyPropsDocument(),
                                                                current.getLegacyPropsDocument())
                                || !Objects.equals(source.getOrderKey(), current.getOrderKey());
        }

        /**
         * 判断需要旧版发布版本固定条件是否成立，供调用方选择后续分支。
         *
         * @param node 节点，作为 {@code read} 的输入影响后续处理
         * @return 需要旧版发布版本固定条件成立时为 true，否则为 false
         */
        private boolean requiresLegacyReleasePin(EntityFormNode node) {
                if (!Set.of("SUB_FORM", "REPEATER").contains(
                                normalize(node.getNodeType(), null))) {
                        return false;
                }
                Map<String, Object> props = read(
                                node.getPropsDocument(), "子表单节点属性");
                Map<String, Object> componentProps = objectMap(props.get("componentProps"), "子表单组件属性");
                Map<String, Object> nestedConfig = objectMap(componentProps.get("subFormConfig"), "子表单配置");
                Map<String, Object> directConfig = objectMap(props.get("subFormConfig"), "子表单配置");
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

        /**
         * 校验并获取预期修订版本；不满足约束时阻止后续处理。
         *
         * @param expected 预期，供本方法校验并获取预期修订版本时使用
         * @param current 当前，作为 {@code RevisionConflictException} 的输入影响后续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private void requireExpectedRevision(Integer expected, EntityFormNode current) {
                if (expected == null) {
                        throw new IllegalArgumentException("expectedRevision 不能为空");
                }
                if (!expected.equals(current.getRevision())) {
                        throw new RevisionConflictException("节点已被其他人修改", current);
                }
        }

        /**
         * 构造业务冲突异常，供调用方刷新或重试。
         *
         * @param formId 表单ID，后续用于处理冲突时定位或关联目标
         * @param nodeId 节点ID，后续用于处理冲突时定位或关联目标
         * @return 处理后的冲突结果，供调用方继续处理
         */
        private RevisionConflictException conflict(String formId, String nodeId) {
                EntityFormNode latest = nodeMapper.selectById(nodeId);
                return new RevisionConflictException(
                                "节点已被其他人修改，请刷新后重试",
                                latest != null && formId.equals(latest.getFormId()) ? latest : null);
        }

        /**
         * 构造{@code duplicate}节点键冲突异常，供调用方区分失败原因。
         *
         * @param formId 表单ID，后续用于处理{@code duplicate}节点键冲突时定位或关联目标
         * @param nodeKey 节点键，后续用于授权校验、关联或幂等去重
         * @return 处理后的{@code duplicate}节点键冲突结果，供调用方继续处理
         */
        private RevisionConflictException duplicateNodeKeyConflict(
                        String formId,
                        String nodeKey) {
                return new RevisionConflictException(
                                "同一表单内节点编码已被其他请求占用，请刷新后重试: "
                                                + nodeKey,
                                nodeMapper.findActiveByFormIdAndNodeKey(formId, nodeKey));
        }

        /**
         * 唯一冲突已由执行器恢复事务并按方言识别；再用当前读确认实际占用节点。
         * 不依赖驱动错误文本或约束名称，主键冲突等无对应占用节点的失败仍原样抛出。
         *
         * @param formId 表单ID，后续用于处理{@code translate}节点写入异常时定位或关联目标
         * @param nodeKey 节点键，后续用于授权校验、关联或幂等去重
         * @param attemptedNodeId {@code attempted}节点ID，后续用于处理{@code translate}节点写入异常时定位或关联目标
         * @param exception 异常，供本方法处理{@code translate}节点写入异常时使用
         * @return 处理后的{@code translate}节点写入异常结果，供调用方继续处理
         */
        private RuntimeException translateNodeWriteException(
                        String formId,
                        String nodeKey,
                        String attemptedNodeId,
                        DuplicateKeyException exception) {
                EntityFormNode occupying = nodeMapper.findActiveByFormIdAndNodeKeyForConflict(
                                formId, nodeKey);
                if (occupying != null && !Objects.equals(attemptedNodeId, occupying.getId())) {
                        return new RevisionConflictException(
                                        "同一表单内节点编码已被其他请求占用，请刷新后重试: " + nodeKey,
                                        occupying);
                }
                return exception;
        }

        /**
         * 校验并获取表单；不满足约束时阻止后续处理。
         *
         * @param formId 表单ID，后续用于校验并获取表单时定位或关联目标
         * @return 校验并获取后的表单结果，供调用方继续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private EntityForm requireForm(String formId) {
                EntityForm form = formMapper.selectById(formId);
                if (form == null) {
                        throw new IllegalArgumentException("表单不存在");
                }
                entityUiConfigurationPolicy.requireConfigurableById(
                                form.getEntityId());
                return form;
        }

        /**
         * 校验并获取表单更新；不满足约束时阻止后续处理。
         *
         * @param formId 表单ID，后续用于校验并获取表单更新时定位或关联目标
         * @param expectedRevision 预期修订版本，作为 {@code IllegalArgumentException} 的输入影响后续处理
         * @return 校验并获取后的表单更新结果，供调用方继续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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
                entityUiConfigurationPolicy.requireConfigurableById(
                                form.getEntityId());
                int currentRevision = form.getRevision() == null ? 1 : form.getRevision();
                if (!Objects.equals(expectedRevision, currentRevision)) {
                        throw new RevisionConflictException(
                                        "表单草稿已被其他人修改，请刷新后重试",
                                        form);
                }
                return form;
        }

        /**
         * 校验并获取节点；不满足约束时阻止后续处理。
         *
         * @param formId 表单ID，后续用于校验并获取节点时定位或关联目标
         * @param nodeId 节点ID，后续用于校验并获取节点时定位或关联目标
         * @return 校验并获取后的节点结果，供调用方继续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        private EntityFormNode requireNode(String formId, String nodeId) {
                EntityFormNode node = nodeMapper.selectById(nodeId);
                if (node == null || !formId.equals(node.getFormId())
                                || Integer.valueOf(1).equals(node.getDeleted())) {
                        throw new IllegalArgumentException("表单节点不存在");
                }
                return node;
        }

        /**
         * 处理更新访问时间表单，并将结果传给后续步骤。
         *
         * @param formId 表单ID，后续用于处理更新访问时间表单时定位或关联目标
         */
        private void touchForm(String formId) {
                UpdateWrapper<EntityForm> wrapper = new UpdateWrapper<>();
                wrapper.eq("id", formId)
                                .setSql("revision = revision + 1")
                                .set("draft_hash", null)
                                .set("update_time", LocalDateTime.now());
                formMapper.update(null, wrapper);
        }

        /**
         * 处理下一步顺序键，并将结果传给后续步骤。
         *
         * @param formId 表单ID，后续用于处理下一步顺序键时定位或关联目标
         * @param parentId 父级ID，后续用于处理下一步顺序键时定位或关联目标
         * @return 处理后的下一步顺序键结果，供调用方继续处理
         */
        private long nextOrderKey(String formId, String parentId) {
                List<EntityFormNode> siblings = nodeMapper.findSiblings(formId, parentId);
                return siblings.isEmpty()
                                ? ORDER_STEP
                                : siblings.get(siblings.size() - 1).getOrderKey() + ORDER_STEP;
        }

        /**
         * 解析{@code boundary}；输出作为后续校验或处理的输入。
         *
         * @param formId 表单ID，后续用于解析{@code boundary}时定位或关联目标
         * @param parentId 父级ID，后续用于解析{@code boundary}时定位或关联目标
         * @param nodeId 节点ID，后续用于解析{@code boundary}时定位或关联目标
         * @param fallback 兜底，主值不可用时供后续处理兜底
         * @return 解析后的{@code boundary}结果，供调用方继续处理
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
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

        /**
         * 处理{@code rebalance}，并将结果传给后续步骤。
         *
         * @param formId 表单ID，后续用于处理{@code rebalance}时定位或关联目标
         * @param parentId 父级ID，后续用于处理{@code rebalance}时定位或关联目标
         */
        private void rebalance(String formId, String parentId) {
                List<EntityFormNode> siblings = new ArrayList<>(nodeMapper.findSiblings(formId, parentId));
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

        /**
         * 写入实体表单节点；后续读取或执行将使用更新后的状态。
         *
         * @param value 待写入实体表单节点的原始输入，结果供调用方继续使用
         * @param label 标签，后续用于写入实体表单节点时匹配或展示
         * @return 写入后的实体表单节点文本，供调用方比较或展示
         */
        private String write(Map<String, Object> value, String label) {
                return value == null || value.isEmpty() ? null : codec.write(value, label);
        }

        /**
         * 读取实体表单节点；查询结果供调用方展示或继续处理。
         *
         * @param value 待读取实体表单节点的原始输入，结果供调用方继续使用
         * @param label 标签，后续用于读取实体表单节点时匹配或展示
         * @return 实体表单节点键值结果，供调用方继续处理
         */
        private Map<String, Object> read(String value, String label) {
                return StringUtils.hasText(value) ? codec.readObject(value, label) : null;
        }

        /**
         * 规范化输入值，确保后续比较和持久化使用一致格式。
         *
         * @param value 待规范化实体表单节点的原始输入，结果供调用方继续使用
         * @param fallback 兜底，主值不可用时供后续处理兜底
         * @return 规范化后的实体表单节点文本，供调用方比较或展示
         */
        private String normalize(String value, String fallback) {
                if (!StringUtils.hasText(value)) {
                        return fallback;
                }
                return value.trim().toUpperCase(Locale.ROOT);
        }

        /**
         * 把空白文本转为 null，避免后续把空字符串当作有效配置。
         *
         * @param value 待处理空白截止空值的原始输入，结果供调用方继续使用
         * @return 处理后的空白截止空值文本，供调用方比较或展示
         */
        private String blankToNull(String value) {
                return StringUtils.hasText(value) ? value.trim() : null;
        }

        /**
         * 定义补丁模式的可选值；调用方据此选择对应的处理分支。
         */
        private enum PatchMode {
                USER_PROPERTY,
                USER_REORDER,
                USER_REPLACE,
                SYSTEM_IMPORT;

                /**
                 * 判断用户{@code facing}条件是否成立，供调用方选择后续分支。
                 *
                 * @return 用户{@code facing}条件成立时为 true，否则为 false
                 */
                boolean userFacing() {
                        return this != SYSTEM_IMPORT;
                }
        }
}
