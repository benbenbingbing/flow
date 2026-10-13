package com.workflow.entity.excel.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.excel.model.ExcelSchemeDocument.Column;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 按固定的已发布写入表单识别“表单未知列”。仅处理方案中的实体字段，不能据此允许文件中的任意表头写入。
 * 子表单及明细节点中的同名字段不属于主表；隐藏、只读等显示状态不改变主表字段身份。
 */
public final class ExcelImportFormColumnPolicy {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> CHILD_NODES = Set.of("SUB_FORM", "SUBFORM", "REPEATER", "SUB_LIST");

    private ExcelImportFormColumnPolicy() {}

    /**
     * 返回本次导入实际使用的映射。REJECT 在读文件/生成模板前报告全部表单外字段，避免静默丢值。
     * DIRECT 仍由 Excel 和实体校验；IGNORE 只去除这些列的文件输入，不关闭实体正常默认值或存储约束。
     */
    public static Selection resolve(EntityExcelRuntimeResolver.Plan plan) {
        var document = plan.scheme().document();
        String policy = document.importConfig().unknownColumnPolicy();
        if (!Set.of("DIRECT", "REJECT", "IGNORE").contains(policy))
            throw new IllegalArgumentException("表单未知列策略必须为 DIRECT、REJECT 或 IGNORE");
        List<Column> columns = document.columns().stream().filter(Column::importEnabled).toList();
        Set<String> entityFields = plan.entity().snapshot().getFields().stream()
                .map(field -> field.getFieldCode()).collect(Collectors.toSet());
        Set<String> configured = formFields(plan.writeForm() == null ? null : plan.writeForm().form());
        Set<String> unknown = new LinkedHashSet<>();
        for (Column column : columns) {
            if (!entityFields.contains(column.fieldCode()))
                throw new IllegalArgumentException("已发布实体字段不存在：" + column.fieldCode());
            if (!configured.contains(column.fieldCode())) unknown.add(column.fieldCode());
        }
        if ("REJECT".equals(policy) && !unknown.isEmpty()) {
            String labels = columns.stream().filter(column -> unknown.contains(column.fieldCode()))
                    .map(column -> column.header() + "（" + column.fieldCode() + "）")
                    .collect(Collectors.joining("、"));
            throw new IllegalArgumentException("写入表单未配置以下导入字段（未知列）：" + labels);
        }
        Set<String> ignored = "IGNORE".equals(policy) ? Set.copyOf(unknown) : Set.of();
        List<Column> imported = columns.stream().filter(column -> !ignored.contains(column.fieldCode())).toList();
        // 所有输入都被忽略时，每行仅剩默认值，不能静默创建与 Excel 内容无关的记录。
        if ("IGNORE".equals(policy) && !columns.isEmpty() && imported.isEmpty())
            throw new IllegalArgumentException("忽略未知列后没有可导入字段，请调整方案或未知列策略");
        return new Selection(policy, Set.copyOf(unknown), "DIRECT".equals(policy) ? Set.copyOf(unknown) : Set.of(),
                ignored, imported);
    }

    /**
     * 汇总当前表单的主字段；旧快照允许仅有 fields，新快照同时支持 FIELD 的实体绑定。
     * 不递归读取节点 props 中内嵌的子表单定义；平铺节点通过父链排除子表/明细后代。
     */
    public static Set<String> formFields(EntityForm form) {
        if (form == null) return Set.of();
        Set<String> fields = new LinkedHashSet<>();
        formFieldsForDefaults(form).forEach(field -> add(fields, field.getFieldCode()));
        fieldNodes(form).forEach(node -> add(fields, fieldCode(node)));
        return Set.copyOf(fields);
    }

    /** 当前表单主字段节点，供默认值读取与字段策略共用同一层级边界。 */
    static List<EntityFormNode> fieldNodes(EntityForm form) {
        if (form == null || form.getNodes() == null) return List.of();
        Map<String, EntityFormNode> nodes = index(form);
        return form.getNodes().stream().filter(node -> node != null && !Integer.valueOf(1).equals(node.getDeleted()))
                .filter(node -> "FIELD".equals(normalize(node.getNodeType())))
                .filter(node -> node.getFormId() == null || form.getId() == null || form.getId().equals(node.getFormId()))
                .filter(node -> !childNode(node, nodes))
                .filter(node -> node.getBindingType() == null || node.getBindingType().isBlank()
                        || "ENTITY_FIELD".equals(normalize(node.getBindingType())))
                .toList();
    }

    /** 排除旧 fields 投影中可能带入的子节点，保留没有节点的旧发布字段。 */
    static List<EntityFormField> formFieldsForDefaults(EntityForm form) {
        if (form == null || form.getFields() == null) return List.of();
        Map<String, EntityFormNode> nodes = index(form);
        Set<String> rootCodes = fieldNodes(form).stream().map(ExcelImportFormColumnPolicy::fieldCode).collect(Collectors.toSet());
        Set<String> childCodes = new HashSet<>();
        nodes.values().stream().filter(node -> childNode(node, nodes)).forEach(node -> childCodes.add(fieldCode(node)));
        return form.getFields().stream().filter(field -> field != null)
                .filter(field -> field.getFormId() == null || form.getId() == null || form.getId().equals(field.getFormId()))
                .filter(field -> !CHILD_NODES.contains(normalize(field.getFieldType())))
                .filter(field -> !nodes.containsKey(field.getId()) || !childNode(nodes.get(field.getId()), nodes))
                .filter(field -> !childCodes.contains(field.getFieldCode()) || rootCodes.contains(field.getFieldCode()))
                .toList();
    }

    static String fieldCode(EntityFormNode node) {
        if ("ENTITY_FIELD".equals(normalize(node.getBindingType())) && node.getBindingRef() != null && !node.getBindingRef().isBlank())
            return node.getBindingRef().strip();
        if (node.getPropsDocument() == null || node.getPropsDocument().isBlank()) return "";
        try {
            Map<String, Object> props = JSON.readValue(node.getPropsDocument(), new TypeReference<>() {});
            return props.get("fieldCode") == null ? "" : props.get("fieldCode").toString().strip();
        } catch (JsonProcessingException invalid) {
            throw new IllegalArgumentException("写入表单字段配置无效", invalid);
        }
    }

    private static Map<String, EntityFormNode> index(EntityForm form) {
        Map<String, EntityFormNode> nodes = new HashMap<>();
        if (form.getNodes() != null) for (var node : form.getNodes()) if (node != null) {
            if (node.getId() != null) nodes.put(node.getId(), node);
            if (node.getNodeKey() != null) nodes.putIfAbsent(node.getNodeKey(), node);
        }
        return nodes;
    }

    private static boolean childNode(EntityFormNode node, Map<String, EntityFormNode> nodes) {
        Set<EntityFormNode> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (EntityFormNode current = node; current != null; current = nodes.get(current.getParentId())) {
            // 非法循环无法可靠证明主表身份，按子节点排除而不是把未知字段误识别为已配置。
            if (!seen.add(current) || CHILD_NODES.contains(normalize(current.getNodeType()))) return true;
        }
        return false;
    }

    private static String normalize(String value) { return value == null ? "" : value.strip().toUpperCase(Locale.ROOT); }
    private static void add(Set<String> fields, String code) { if (code != null && !code.isBlank()) fields.add(code.strip()); }

    /** importColumns 保留实际导入顺序；ignoredFields 用于识别已映射但应跳过的文件表头。 */
    public record Selection(String policy, Set<String> unknownFields, Set<String> directFields,
                            Set<String> ignoredFields, List<Column> importColumns) {}
}
