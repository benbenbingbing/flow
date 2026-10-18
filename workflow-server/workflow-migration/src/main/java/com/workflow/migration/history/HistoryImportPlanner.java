package com.workflow.migration.history;

import com.workflow.contracts.history.*;
import com.workflow.contracts.history.HistoryImportModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 联合预检及依赖排序；源引用先在暂存行中解析，再查已提交的来源映射。 */
@Component
@RequiredArgsConstructor
public class HistoryImportPlanner {
    public static final int MAX_ROWS = 2000;
    public static final int MAX_GROUP_ROWS = 200;
    public static final Set<String> RESULTS = Set.of("APPROVED", "REJECTED", "WITHDRAWN", "TERMINATED", "COMPLETED_UNKNOWN");
    private final EntityHistoryImportPort entities;
    private final ProcessArchivePort processes;

    public record Validated(Input input, Map<String, EntitySchema> schemas, ProcessTemplate template,
                            Map<String, List<String>> groups, List<String> groupOrder) {}

    /** 校验全部引用和映射后才允许执行；循环依赖无安全创建顺序时明确拒绝。 */
    public Validated validate(Plan plan, Input input) {
        validatePlan(plan);
        if (input.entities().isEmpty()) throw new IllegalArgumentException("请先上传实体数据");
        if (input.entities().size() + input.instances().size() + input.events().size() > MAX_ROWS) throw new IllegalArgumentException("一个批次最多 2000 条实体、流程及审批记录");
        Map<String, EntitySpec> specs = index(plan.entities(), EntitySpec::key, "实体配置");
        Map<String, EntitySchema> schemas = new LinkedHashMap<>();
        specs.forEach((key, spec) -> { entities.validateImport(spec.entityCode()); schemas.put(key, entities.schema(spec.entityCode())); });
        for (var spec : specs.values()) for (var ref : safe(spec.references()).entrySet()) {
            if (!specs.containsKey(ref.getValue())) throw new IllegalArgumentException("引用的目标实体未配置: " + ref.getValue());
            var field = schemas.get(spec.key()).fields().stream().filter(f -> f.code().equals(ref.getKey())).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("关联字段不存在: " + ref.getKey()));
            boolean relationField = schemas.get(ref.getValue()).relations().stream().anyMatch(r ->
                    r.childEntity().equals(spec.entityCode()) && r.childField().equals(field.code()));
            if (!Objects.equals(field.referenceEntity(), specs.get(ref.getValue()).entityCode()) && !relationField)
                throw new IllegalArgumentException("字段关联目标与导入实体不一致: " + ref.getKey());
        }
        List<EntityRow> normalized = new ArrayList<>();
        for (EntityRow row : input.entities()) {
            requireText(row.sourceId(), "原业务记录ID", 500); requireText(row.groupKey(), "提交组", 500);
            if (!specs.containsKey(row.entityKey())) throw new IllegalArgumentException("未知实体页: " + row.entityKey());
            try {
                normalized.add(new EntityRow(row.entityKey(), row.sourceId(), row.groupKey(), entities.normalize(schemas.get(row.entityKey()), row.values())));
            } catch (IllegalArgumentException invalid) {
                throw new IllegalArgumentException("实体页 " + row.entityKey() + "，来源记录 " + row.sourceId() + ": " + invalid.getMessage(), invalid);
            }
        }
        Map<String, EntityRow> rows = index(normalized, HistoryImportPlanner::key, "实体来源记录");
        // 组成关系必须使用显式来源映射，且父子同组；不能通过填写新库 ID 拆散历史聚合。
        for (var parentSpec : specs.values()) for (var relation : schemas.get(parentSpec.key()).relations()) {
            if (!relation.composition()) continue;
            for (var child : normalized) if (specs.get(child.entityKey()).entityCode().equals(relation.childEntity())) {
                Object parentSource = child.values().get(relation.childField());
                if (parentSource == null || parentSource.toString().isBlank()) continue;
                String parentKey = safe(specs.get(child.entityKey()).references()).get(relation.childField());
                if (parentKey == null) throw new IllegalArgumentException("组成关系必须配置旧ID转换: " + relation.childField());
                var parent = rows.get(key(parentKey, parentSource.toString()));
                if (parent == null || !parent.groupKey().equals(child.groupKey()))
                    throw new IllegalArgumentException("主从记录必须在同一提交组: " + child.sourceId());
                if (specs.get(parentKey).readonly() && !specs.get(child.entityKey()).readonly())
                    throw new IllegalArgumentException("只读主记录的组成子实体也必须设为只读: " + child.entityKey());
            }
        }
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        Map<String, Set<String>> groupDependencies = new LinkedHashMap<>();
        rows.forEach((key, row) -> { dependencies.put(key, new LinkedHashSet<>()); groupDependencies.putIfAbsent(row.groupKey(), new LinkedHashSet<>()); });
        rows.forEach((key, row) -> {
            for (var ref : safe(specs.get(row.entityKey()).references()).entrySet()) {
                for (String oldId : referenceIds(row.values().get(ref.getKey()))) {
                    String target = key(ref.getValue(), oldId);
                    if (rows.containsKey(target)) {
                        dependencies.get(key).add(target);
                        String targetGroup = rows.get(target).groupKey();
                        if (!targetGroup.equals(row.groupKey())) groupDependencies.get(row.groupKey()).add(targetGroup);
                    } else {
                        SourceRecord source = entities.findSource(plan.sourceSystem(), ref.getValue(), oldId);
                        if (source == null || !source.entityCode().equals(specs.get(ref.getValue()).entityCode())) throw new IllegalArgumentException("缺少关联数据: " + ref.getValue() + "/" + oldId);
                    }
                }
            }
        });
        List<String> order = topological(dependencies);
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (String group : topological(groupDependencies)) {
            List<String> members = order.stream().filter(key -> rows.get(key).groupKey().equals(group)).toList();
            if (members.size() > MAX_GROUP_ROWS) throw new IllegalArgumentException("单个提交组最多 200 条实体记录: " + group);
            groups.put(group, members);
        }
        ProcessTemplate template = null;
        if (!input.instances().isEmpty()) {
            requireText(plan.processVersionId(), "展示流程版本", 64);
            template = processes.template(plan.processVersionId());
        }
        Map<String, InstanceRow> instances = index(input.instances(), InstanceRow::sourceId, "流程实例");
        for (var instance : instances.values()) {
            requireText(instance.sourceId(), "原实例ID", 500); requireText(instance.name(), "流程名称", 200); requireText(instance.initiator(), "发起人", 200);
            if (!rows.containsKey(key(instance.entityKey(), instance.recordId()))) throw new IllegalArgumentException("流程缺少实体行: " + instance.sourceId());
            if (!specs.get(instance.entityKey()).readonly()) throw new IllegalArgumentException("带历史流程的主实体必须设为只读: " + instance.entityKey());
            if (!RESULTS.contains(instance.result())) throw new IllegalArgumentException("仅支持已结束流程，结束结果无效: " + instance.sourceId());
            LocalDateTime start = time(instance.startedAt()), end = time(instance.endedAt());
            if (start != null && end != null && end.isBefore(start)) throw new IllegalArgumentException("结束时间早于开始时间: " + instance.sourceId());
            long actual = input.events().stream().filter(e -> instance.sourceId().equals(e.instanceId())).count();
            if (instance.expectedEvents() < 0 || actual != instance.expectedEvents()) throw new IllegalArgumentException("审批记录数不符: " + instance.sourceId());
        }
        index(input.events(), e -> key(e.instanceId(), e.sourceId()), "审批记录");
        for (var event : input.events()) {
            requireText(event.sourceId(), "原审批记录ID", 500); requireText(event.node(), "旧节点", 200); requireText(event.actor(), "审批人", 200); requireText(event.action(), "审批操作", 100);
            if (!instances.containsKey(event.instanceId())) throw new IllegalArgumentException("审批记录缺少流程概要: " + event.sourceId());
            String node = safe(plan.nodeMappings()).get(event.node());
            if (node == null || !(node.equals("TIMELINE_ONLY") || template.nodes().containsKey(node))) throw new IllegalArgumentException("旧节点未映射: " + event.node());
            time(event.handledAt());
            if (event.sequence() < 1) throw new IllegalArgumentException("审批顺序必须大于零: " + event.sourceId());
            if (event.comment() != null && event.comment().length() > 10000) throw new IllegalArgumentException("审批意见超过 10000 字");
        }
        return new Validated(new Input(normalized, input.instances(), input.events()), schemas, template, groups, new ArrayList<>(groups.keySet()));
    }

    public static void validatePlan(Plan plan) {
        if (plan == null) throw new IllegalArgumentException("缺少导入方案");
        requireText(plan.name(), "方案名称", 200); requireText(plan.sourceSystem(), "来源系统", 200);
        if (plan.entities() == null || plan.entities().isEmpty() || plan.entities().size() > 20) throw new IllegalArgumentException("请选择 1 到 20 个实体");
        index(plan.entities(), EntitySpec::key, "实体配置");
        if (plan.entities().stream().map(s -> s.key() == null ? "" : s.key().toLowerCase(Locale.ROOT)).distinct().count() != plan.entities().size())
            throw new IllegalArgumentException("Excel实体页标识不能仅大小写不同");
        for (var spec : plan.entities()) {
            if (spec.key() == null || !spec.key().matches("[a-zA-Z][a-zA-Z0-9_]{0,25}")) throw new IllegalArgumentException("实体页标识应为不超过26位的英文、数字或下划线");
            requireText(spec.entityCode(), "实体编码", 100);
        }
    }

    /** Kahn 排序保留输入顺序；剩余项说明循环，不猜测空外键后补写以绕过必填约束。 */
    public static List<String> topological(Map<String, Set<String>> graph) {
        List<String> result = new ArrayList<>();
        Set<String> done = new HashSet<>();
        while (done.size() < graph.size()) {
            boolean changed = false;
            for (var entry : graph.entrySet()) if (!done.contains(entry.getKey()) && done.containsAll(entry.getValue())) {
                done.add(entry.getKey()); result.add(entry.getKey()); changed = true;
            }
            if (!changed) throw new IllegalArgumentException("关联数据或提交组存在循环依赖，请调整实体关系或提交组");
        }
        return result;
    }
    public static List<String> referenceIds(Object value) {
        if (value == null || value.toString().isBlank()) return List.of();
        return value instanceof List<?> list ? list.stream().map(Object::toString).toList() : List.of(value.toString());
    }
    public static String key(EntityRow row) { return key(row.entityKey(), row.sourceId()); }
    public static String key(String type, String id) { return HistoryIdentity.hash(type, id); }
    private static LocalDateTime time(String raw) {
        return raw == null || raw.isBlank() ? null : LocalDateTime.parse(raw.replace(' ', 'T'));
    }
    public static <T> Map<String, T> index(List<T> input, Function<T, String> key, String label) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T item : input) if (result.putIfAbsent(key.apply(item), item) != null) throw new IllegalArgumentException(label + "重复: " + key.apply(item));
        return result;
    }
    public static Map<String, String> safe(Map<String, String> value) { return value == null ? Map.of() : value; }
    public static void requireText(String value, String label, int max) {
        if (value == null || value.isBlank() || value.length() > max) throw new IllegalArgumentException(label + "不能为空且不能超过" + max + "字");
    }
}
