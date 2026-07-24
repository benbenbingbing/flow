package com.workflow.runner;

import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityFormNode;
import com.workflow.entity.EntityListConfig;
import com.workflow.mapper.EntityFormFieldMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityFormNodeMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.service.EntityFormNodeService;
import com.workflow.service.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * UI 配置启动迁移 Runner。
 * <p>应用启动时将历史表单字段迁移为表单节点结构、修复孤立 TAB 节点，
 * 并为缺失发布版本的表单/列表自动生成初始发布版本，保障升级后 UI 配置可用。
 * 迁移与发布均在新事务中执行，失败收集后统一输出报告。
 */
@Slf4j
@Order(50)
@Component
@RequiredArgsConstructor
public class UiConfigurationBootstrapRunner implements ApplicationRunner {

    /** 历史组件属性中需要识别为显式属性（而非遗留属性）的键集合 */
    private static final Set<String> EXPLICIT_COMPONENT_KEYS = Set.of(
            "options", "optionSource", "referenceConfig", "refConfig",
            "subFormConfig", "relationConfig", "events", "eventBindings",
            "display", "layout", "min", "max", "step", "rows",
            "multiple", "clearable", "filterable", "format", "valueFormat");

    private final EntityFormMapper formMapper;
    private final EntityFormFieldMapper fieldMapper;
    private final EntityFormNodeMapper nodeMapper;
    private final EntityListConfigMapper listMapper;
    private final EntityFormNodeService nodeService;
    private final UiConfigReleaseService releaseService;
    private final JsonDocumentCodec codec;
    private final UiConfigurationBootstrapTransactionExecutor transactionExecutor;

    /**
     * 应用启动入口：逐表单迁移节点并按需发布表单/列表初始版本，最后汇总迁移结果与失败报告。
     *
     * @param args 启动参数（本 Runner 未使用）
     */
    @Override
    public void run(ApplicationArguments args) {
        int migratedForms = 0;
        int migratedNodes = 0;
        int unknownProperties = 0;
        int formReleases = 0;
        int listReleases = 0;
        List<String> failures = new ArrayList<>();
        for (EntityForm form : formMapper.selectList(null)) {
            MigrationCount count;
            try {
                count = transactionExecutor.execute(() -> migrateForm(form));
                if (count.nodes() > 0) {
                    migratedForms++;
                    migratedNodes += count.nodes();
                    unknownProperties += count.unknownProperties();
                }
            } catch (RuntimeException exception) {
                failures.add("表单节点迁移失败 formId=" + form.getId()
                        + ": " + exception.getMessage());
                log.error("迁移历史表单节点失败: formId={}, message={}",
                        form.getId(), exception.getMessage(), exception);
                continue;
            }
            var activeRelease =
                    releaseService.active(UiConfigReleaseService.FORM, form.getId());
            // 仅在无活跃发布或存在孤立 TAB 修复时尝试发布
            if (activeRelease == null || !count.repairedNodeIds().isEmpty()) {
                try {
                    // 孤立 TAB 修复场景需通过安全校验，确保未发布草稿不包含其他差异
                    if (activeRelease != null
                            && !isSafeTabRepairRelease(
                                    form.getId(),
                                    count.repairedNodeIds())) {
                        throw new IllegalStateException(
                                "孤立TAB修复之外还存在未发布草稿，禁止自动发布迁移版本");
                    }
                    transactionExecutor.execute(() -> releaseService.publish(
                            UiConfigReleaseService.FORM,
                            form.getId(),
                            activeRelease == null
                                    ? "升级自动生成的初始发布版本"
                                    : "升级自动修复历史孤立TAB节点"));
                    formReleases++;
                } catch (RuntimeException exception) {
                    failures.add("表单初始发布失败 formId=" + form.getId()
                            + ": " + exception.getMessage());
                    log.error("生成表单初始发布版本失败: formId={}, message={}",
                            form.getId(), exception.getMessage(), exception);
                }
            }
        }
        for (EntityListConfig list : listMapper.selectList(null)) {
            // 列表仅需在缺失活跃发布时补发初始版本
            if (releaseService.active(UiConfigReleaseService.LIST, list.getId()) == null) {
                try {
                    transactionExecutor.execute(() -> releaseService.publish(
                            UiConfigReleaseService.LIST,
                            list.getId(),
                            "升级自动生成的初始发布版本"));
                    listReleases++;
                } catch (RuntimeException exception) {
                    failures.add("列表初始发布失败 listId=" + list.getId()
                            + ": " + exception.getMessage());
                    log.error("生成列表初始发布版本失败: listId={}, message={}",
                            list.getId(), exception.getMessage(), exception);
                }
            }
        }
        log.info(
                "UI配置迁移完成: migratedForms={}, nodes={}, unknownProperties={}, "
                        + "formReleases={}, listReleases={}",
                migratedForms,
                migratedNodes,
                unknownProperties,
                formReleases,
                listReleases);
        if (!failures.isEmpty()) {
            Map<String, Object> report = new LinkedHashMap<>();
            report.put("migratedForms", migratedForms);
            report.put("migratedNodes", migratedNodes);
            report.put("unknownProperties", unknownProperties);
            report.put("formReleases", formReleases);
            report.put("listReleases", listReleases);
            report.put("failureCount", failures.size());
            report.put("failures", failures);
            log.error("UI配置启动迁移失败报告: {}",
                    codec.write(report, "UI配置启动迁移失败报告"));
        }
    }

    /**
     * 迁移单个表单：修复孤立 TAB、将历史字段转为节点，并按差异落库或校验树结构。
     *
     * @param form 待迁移表单
     * @return 本次迁移的节点数、未识别属性数及修复的 TAB 节点ID集合
     */
    private MigrationCount migrateForm(EntityForm form) {
        List<EntityFormNode> nodes = new ArrayList<>(nodeMapper.findByFormId(form.getId()));
        Set<String> repairedNodeIds = repairLegacyTabParents(form.getId(), nodes);
        List<EntityFormField> fields = fieldMapper.selectByFormId(form.getId());
        Map<String, EntityFormNode> existingById = new LinkedHashMap<>();
        Map<String, EntityFormNode> existingByKey = new LinkedHashMap<>();
        Map<String, EntityFormNode> existingByBinding = new LinkedHashMap<>();
        for (EntityFormNode node : nodes) {
            existingById.put(node.getId(), node);
            if (StringUtils.hasText(node.getNodeKey())) {
                existingByKey.putIfAbsent(node.getNodeKey(), node);
            }
            if (StringUtils.hasText(node.getBindingRef())) {
                existingByBinding.putIfAbsent(node.getBindingRef(), node);
            }
        }
        int migratedNodes = repairedNodeIds.size();
        int unknownProperties = 0;
        for (int index = 0; index < fields.size(); index++) {
            EntityFormField field = fields.get(index);
            String nodeKey = StringUtils.hasText(field.getFieldCode())
                    ? field.getFieldCode()
                    : "legacy_" + (index + 1);
            String bindingRef = StringUtils.hasText(field.getRelationCode())
                    ? field.getRelationCode()
                    : field.getFieldCode();
            if (existingById.containsKey(field.getId())
                    || existingByKey.containsKey(nodeKey)
                    || (StringUtils.hasText(bindingRef)
                    && existingByBinding.containsKey(bindingRef))) {
                continue;
            }
            Map<String, Object> rawProps = read(field.getComponentProps(), "历史组件属性");
            Map<String, Object> explicitProps = new LinkedHashMap<>();
            Map<String, Object> legacyProps = new LinkedHashMap<>();
            rawProps.forEach((key, value) -> {
                if (EXPLICIT_COMPONENT_KEYS.contains(key)) {
                    explicitProps.put(key, value);
                } else {
                    legacyProps.put(key, value);
                }
            });
            explicitProps.put("fieldId", field.getFieldId());
            explicitProps.put("fieldCode", field.getFieldCode());
            explicitProps.put("label", field.getFieldLabel());
            explicitProps.put("componentType", field.getComponentType());
            explicitProps.put("placeholder", field.getPlaceholder());
            explicitProps.put("defaultValue", field.getDefaultValue());
            explicitProps.put("gridSpan", field.getGridSpan());
            explicitProps.put("required", Integer.valueOf(1).equals(field.getIsRequired()));
            explicitProps.put("readonly", Integer.valueOf(1).equals(field.getIsReadonly()));
            explicitProps.put("hidden", Integer.valueOf(1).equals(field.getIsHidden()));

            EntityFormNode node = new EntityFormNode();
            node.setId(field.getId());
            node.setFormId(form.getId());
            node.setNodeKey(nodeKey);
            node.setNodeType(resolveNodeType(field));
            node.setBindingType(resolveBindingType(field));
            node.setBindingRef(bindingRef);
            node.setPropsDocument(codec.write(explicitProps, "迁移表单节点属性"));
            node.setRulesDocument(codec.write(mergeRules(field), "迁移表单节点规则"));
            node.setLegacyPropsDocument(legacyProps.isEmpty()
                    ? null : codec.write(legacyProps, "迁移历史节点属性"));
            node.setOrderKey((index + 1L) * EntityFormNodeService.ORDER_STEP);
            node.setRevision(1);
            node.setDeleted(0);
            nodes.add(node);
            migratedNodes++;
            unknownProperties += legacyProps.size();
        }
        if (migratedNodes > 0) {
            if (migratedNodes > repairedNodeIds.size()) {
                nodeService.replaceByDiff(form.getId(), nodes);
            } else {
                nodeService.validateTree(form.getId());
            }
        }
        return new MigrationCount(
                migratedNodes,
                unknownProperties,
                repairedNodeIds);
    }

    /**
     * 修复历史孤立 TAB 节点：将缺少 TAB_SET 父级的 TAB 重新挂到 orderKey 最接近的 TAB_SET 下。
     *
     * @param formId 表单ID
     * @param nodes  表单全部节点
     * @return 已修复的 TAB 节点ID集合
     */
    private Set<String> repairLegacyTabParents(
            String formId,
            List<EntityFormNode> nodes) {
        Map<String, EntityFormNode> byId = new LinkedHashMap<>();
        List<EntityFormNode> tabSets = new ArrayList<>();
        for (EntityFormNode node : nodes) {
            byId.put(node.getId(), node);
            if ("TAB_SET".equals(node.getNodeType())) {
                tabSets.add(node);
            }
        }
        Set<String> repaired = new LinkedHashSet<>();
        for (EntityFormNode node : nodes) {
            if (!"TAB".equals(node.getNodeType())) {
                continue;
            }
            EntityFormNode parent = byId.get(node.getParentId());
            if (parent != null && "TAB_SET".equals(parent.getNodeType())) {
                continue;
            }
            EntityFormNode target = tabSets.stream()
                    .min(java.util.Comparator
                            .comparingLong((EntityFormNode candidate) ->
                                    Math.abs(orderKey(candidate)
                                            - orderKey(node)))
                            .thenComparing(EntityFormNode::getId))
                    .orElseThrow(() -> new IllegalStateException(
                            "历史TAB节点缺少可用TAB_SET父级: formId="
                                    + formId + ", nodeId=" + node.getId()));
            node.setParentId(target.getId());
            node.setRevision(node.getRevision() == null
                    ? 1
                    : node.getRevision() + 1);
            node.setUpdatedAt(java.time.LocalDateTime.now());
            nodeMapper.updateById(node);
            repaired.add(node.getId());
            log.warn(
                    "已修复历史孤立TAB节点: formId={}, nodeId={}, parentId={}",
                    formId,
                    node.getId(),
                    target.getId());
        }
        return repaired;
    }

    private long orderKey(EntityFormNode node) {
        return node.getOrderKey() == null ? 0L : node.getOrderKey();
    }

    /**
     * 判断当前草稿相对活跃发布的差异是否仅来自孤立 TAB 修复（即除修复节点 parentId 外无其他差异）。
     * <p>用于决定是否可安全自动发布迁移版本。
     *
     * @param formId         表单ID
     * @param repairedNodeIds 已修复的 TAB 节点ID集合
     * @return 仅当差异局限于修复节点的 parentId 时返回 true
     */
    private boolean isSafeTabRepairRelease(
            String formId,
            Set<String> repairedNodeIds) {
        if (repairedNodeIds.isEmpty()) {
            return true;
        }
        Map<String, Object> active = releaseService.activeSnapshot(
                UiConfigReleaseService.FORM, formId);
        Map<String, Object> draft = releaseService.draftSnapshot(
                UiConfigReleaseService.FORM, formId);
        if (active == null || draft == null) {
            return false;
        }
        Map<String, Object> activeRoot = new LinkedHashMap<>(active);
        Map<String, Object> draftRoot = new LinkedHashMap<>(draft);
        Object activeNodes = activeRoot.remove("nodes");
        Object draftNodes = draftRoot.remove("nodes");
        if (!equivalent(activeRoot, draftRoot)) {
            log.warn(
                    "孤立TAB修复安全校验发现非节点差异: formId={}, sections={}",
                    formId,
                    changedKeys(activeRoot, draftRoot));
            return false;
        }
        Map<String, Map<String, Object>> activeById = nodesById(activeNodes);
        Map<String, Map<String, Object>> draftById = nodesById(draftNodes);
        if (!activeById.keySet().equals(draftById.keySet())) {
            log.warn(
                    "孤立TAB修复安全校验发现节点集合差异: formId={}, activeOnly={}, draftOnly={}",
                    formId,
                    difference(activeById.keySet(), draftById.keySet()),
                    difference(draftById.keySet(), activeById.keySet()));
            return false;
        }
        for (String nodeId : activeById.keySet()) {
            Map<String, Object> activeNode =
                    comparableNode(activeById.get(nodeId));
            Map<String, Object> draftNode =
                    comparableNode(draftById.get(nodeId));
            if (repairedNodeIds.contains(nodeId)) {
                activeNode.put("parentId", draftNode.get("parentId"));
            }
            if (!equivalent(activeNode, draftNode)) {
                log.warn(
                        "孤立TAB修复安全校验发现节点属性差异: formId={}, nodeId={}, fields={}",
                        formId,
                        nodeId,
                        changedKeys(activeNode, draftNode));
                return false;
            }
        }
        return true;
    }

    private Set<String> changedKeys(
            Map<String, Object> left,
            Map<String, Object> right) {
        Set<String> keys = new LinkedHashSet<>(left.keySet());
        keys.addAll(right.keySet());
        keys.removeIf(key -> equivalent(left.get(key), right.get(key)));
        return keys;
    }

    private Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private boolean equivalent(Object left, Object right) {
        if (left == null || right == null) {
            return java.util.Objects.equals(left, right);
        }
        String leftDocument = codec.write(left, "迁移安全校验左值");
        String rightDocument = codec.write(right, "迁移安全校验右值");
        return java.util.Objects.equals(
                codec.canonicalize(leftDocument, "迁移安全校验左值"),
                codec.canonicalize(rightDocument, "迁移安全校验右值"));
    }

    private Map<String, Map<String, Object>> nodesById(Object value) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        if (!(value instanceof List<?> values)) {
            return result;
        }
        for (Object item : values) {
            if (!(item instanceof Map<?, ?> source)) {
                return Map.of();
            }
            Map<String, Object> node = new LinkedHashMap<>();
            source.forEach((key, child) ->
                    node.put(String.valueOf(key), child));
            Object id = node.get("id");
            if (id == null) {
                return Map.of();
            }
            result.put(String.valueOf(id), node);
        }
        return result;
    }

    private Map<String, Object> comparableNode(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        result.remove("revision");
        result.remove("updatedAt");
        result.remove("updateTime");
        return result;
    }

    /** 根据字段类型/组件类型推断表单节点类型（REPEATER/SUB_FORM/SECTION/TEXT/FIELD）。 */
    private String resolveNodeType(EntityFormField field) {
        String type = StringUtils.hasText(field.getFieldType())
                ? field.getFieldType().toUpperCase(Locale.ROOT)
                : "";
        String component = StringUtils.hasText(field.getComponentType())
                ? field.getComponentType().toUpperCase(Locale.ROOT)
                : "";
        if ("SUB_FORM_LIST".equals(type) || component.contains("REPEATER")) {
            return "REPEATER";
        }
        if ("SUB_FORM".equals(type) || component.contains("SUB_FORM")) {
            return "SUB_FORM";
        }
        if (component.contains("SECTION")) {
            return "SECTION";
        }
        if (component.contains("TEXT") && !StringUtils.hasText(field.getFieldId())) {
            return "TEXT";
        }
        return "FIELD";
    }

    /** 推断节点绑定类型：关联字段为 RELATION，普通实体字段为 ENTITY_FIELD，无字段为 NONE。 */
    private String resolveBindingType(EntityFormField field) {
        if (StringUtils.hasText(field.getRelationCode())
                || Set.of("SUB_FORM", "SUB_FORM_LIST").contains(
                        StringUtils.hasText(field.getFieldType())
                                ? field.getFieldType().toUpperCase(Locale.ROOT)
                                : "")) {
            return "RELATION";
        }
        return StringUtils.hasText(field.getFieldId())
                ? "ENTITY_FIELD"
                : "NONE";
    }

    /** 合并历史校验规则与扩展配置，扩展配置以 extension 键并入规则文档。 */
    private Map<String, Object> mergeRules(EntityFormField field) {
        Map<String, Object> rules =
                read(field.getValidationRules(), "历史校验规则");
        Map<String, Object> extension =
                read(field.getExtensionConfig(), "历史字段扩展配置");
        if (!extension.isEmpty()) {
            rules.put("extension", extension);
        }
        return rules;
    }

    /** 读取 JSON 文本为 Map；空串或 null 返回空 Map。 */
    private Map<String, Object> read(String value, String label) {
        return StringUtils.hasText(value)
                ? new LinkedHashMap<>(codec.readObject(value, label))
                : new LinkedHashMap<>();
    }

    /** 单次表单迁移结果统计：迁移节点数、未识别属性数、已修复的 TAB 节点ID集合。 */
    private record MigrationCount(
            int nodes,
            int unknownProperties,
            Set<String> repairedNodeIds) {
    }
}
