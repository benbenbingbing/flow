package com.workflow.entity.form.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** 发布校验和提交共用规则，计算结果只能来自服务端同一份已发布配置。 */
@Component
@RequiredArgsConstructor
public class FormContextRules {
    public static final Set<String> MODES = Set.of("create", "edit", "approve");
    private static final List<String> CONDITIONS = List.of(
            "visibilityConditionConfig", "enabledConditionConfig", "disabledConditionConfig", "requiredConditionConfig");
    private static final Set<String> NUMBERS = Set.of("INTEGER", "LONG", "DECIMAL", "DOUBLE", "NUMBER");

    private final JsonDocumentCodec codec;
    private final PublishedFormConditionEvaluator conditions;
    private final EntityFieldMapper entityFields;

    private record FieldConfig(String key, EntityFormNode node, Map<?, ?> props, Map<?, ?> rules) {
        String label() {
            Object label = props.get("label");
            return label == null ? key : label.toString();
        }
    }

    private record Calculation(FieldConfig field, FormContextFormula formula) { }

    /** 校验上下文计算的入口和字段引用，避免新增默认值与新增模式的计算写入同一字段。 */
    public void validate(EntityForm form) {
        Map<String, FieldConfig> fields = fields(form);
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        for (FieldConfig field : fields.values()) {
            try {
                String source = formulaSource(field);
                Map<?, ?> config = map(field.rules().get("contextCalculation"));
                if (FormContextDefaults.hasReference(source) || !config.isEmpty()) {
                    if (!supportedValues(config.get("modes"), MODES)
                            || !Integer.valueOf(1).equals(config.get("expressionVersion"))) {
                        throw new IllegalArgumentException("请选择上下文公式生效模式");
                    }
                    FormContextFormula formula = FormContextDefaults.compile(source);
                    if (formula.contextKeys().isEmpty()) throw new IllegalArgumentException("上下文公式没有引用参数");
                    if (formula.contextKeys().stream().anyMatch(FormContextDefaults::taskOnly)
                            && !((List<?>) config.get("modes")).contains("approve")) {
                        throw new IllegalArgumentException("待办参数需要在审批模式使用");
                    }
                    if (((List<?>) config.get("modes")).contains("create")
                            && Set.of("fixed", "formula", "dataSource").contains(text(field.props().get("defaultValueMode")))) {
                        throw new IllegalArgumentException("新增模式不能同时配置默认值与上下文计算");
                    }
                    track(dependencies, fields, field, formula);
                }
                String defaultSource = text(field.props().get("defaultFormula"));
                if (FormContextDefaults.hasReference(defaultSource)) {
                    FormContextDefaults.validate(defaultSource);
                    track(dependencies, fields, field, FormContextDefaults.compile(defaultSource));
                }
                for (String name : CONDITIONS) {
                    Object rule = field.rules().get(name);
                    if (referencesContext(rule)) {
                        Set<String> sources = new LinkedHashSet<>();
                        conditionProperties(rule, sources);
                        sources.removeIf(property -> property.startsWith("ctx."));
                        dependencies.computeIfAbsent(field.key(), key -> new LinkedHashSet<>()).addAll(sources);
                    }
                }
            } catch (IllegalArgumentException error) {
                throw new IllegalArgumentException(field.label() + " " + error.getMessage(), error);
            }
        }
        Set<String> finished = new LinkedHashSet<>();
        for (String key : dependencies.keySet()) visit(key, dependencies, new LinkedHashSet<>(), finished);
    }

    private static void track(Map<String, Set<String>> graph, Map<String, FieldConfig> fields, FieldConfig field,
            FormContextFormula formula) {
        for (String source : formula.fieldKeys()) {
            if (!fields.containsKey(source)) throw new IllegalArgumentException("公式引用了不可用的表单字段 " + source);
        }
        graph.computeIfAbsent(field.key(), key -> new LinkedHashSet<>()).addAll(formula.fieldKeys());
    }

    private static void visit(String node, Map<String, Set<String>> graph, Set<String> visiting, Set<String> finished) {
        if (finished.contains(node) || !graph.containsKey(node)) return;
        if (!visiting.add(node)) throw new IllegalArgumentException("字段规则存在循环引用 " + node);
        for (String dependency : graph.get(node)) visit(dependency, graph, visiting, finished);
        visiting.remove(node);
        finished.add(node);
    }

    private Map<String, FieldConfig> fields(EntityForm form) {
        Map<String, FieldConfig> result = new LinkedHashMap<>();
        for (EntityFormNode node : form.getNodes() == null ? List.<EntityFormNode>of() : form.getNodes()) {
            if (!"FIELD".equalsIgnoreCase(node.getNodeType()) || !"ENTITY_FIELD".equalsIgnoreCase(node.getBindingType())
                    || node.getPropsDocument() == null || node.getPropsDocument().isBlank()) continue;
            Map<?, ?> props = codec.readObject(node.getPropsDocument(), "已发布表单属性");
            String key = text(props.get("fieldCode"));
            if (key.isEmpty()) key = text(node.getBindingRef());
            if (!key.isEmpty()) result.put(key, new FieldConfig(key, node, props, nodeLinkageRules(props)));
        }
        return result;
    }

    private static String formulaSource(FieldConfig field) {
        return text(field.rules().containsKey("valueFormula") ? field.rules().get("valueFormula") : field.rules().get("calculationFormula"));
    }

    public static boolean referencesContext(Object rule) {
        Set<String> properties = new LinkedHashSet<>();
        conditionProperties(rule, properties);
        return properties.stream().anyMatch(property -> property.startsWith("ctx."));
    }

    /** 字段将联动规则存入组件属性，布局节点将规则存入节点属性，两种发布结构统一在此读取。 */
    public static Map<?, ?> nodeLinkageRules(Map<?, ?> props) {
        if (props.get("linkageRules") instanceof Map<?, ?> rules) return rules;
        return map(map(props.get("componentProps")).get("linkageRules"));
    }

    private static void conditionProperties(Object node, Set<String> properties) {
        if (node instanceof Map<?, ?> object) {
            if (object.get("property") instanceof String property) properties.add(property);
            conditionProperties(object.get("root"), properties);
            conditionProperties(object.get("children"), properties);
        } else if (node instanceof List<?> list) list.forEach(child -> conditionProperties(child, properties));
    }

    private static boolean supportedValues(Object raw, Set<String> supported) {
        return raw instanceof List<?> values && !values.isEmpty() && values.stream().allMatch(supported::contains)
                && values.stream().distinct().count() == values.size();
    }

    private static Map<?, ?> map(Object raw) { return raw instanceof Map<?, ?> value ? value : Map.of(); }
    private static String text(Object raw) { return raw instanceof String value ? value : ""; }

    final class Submission {
        private final EntityForm form;
        private final String mode;
        private final FormContextDefaults.RuntimeValues values;
        private final Map<String, Object> original;
        private final Map<String, Object> params;
        private final Map<String, FieldConfig> fields;
        private final Map<String, EntityFormNode> nodes = new LinkedHashMap<>();
        private final Map<String, Calculation> calculations = new LinkedHashMap<>();

        Submission(EntityForm form, String mode, FormContextDefaults.RuntimeValues values,
                Map<String, Object> original, Map<String, Object> params) {
            this.form = form;
            this.mode = mode;
            this.values = values;
            this.original = original;
            this.params = params;
            this.fields = fields(form);
            if (form.getNodes() != null) form.getNodes().forEach(node -> nodes.put(node.getId(), node));
            fields.forEach((key, field) -> {
                if (map(field.rules().get("contextCalculation")).get("modes") instanceof List<?> modes && modes.contains(mode)) {
                    calculations.put(key, new Calculation(field, FormContextDefaults.compile(formulaSource(field))));
                }
            });
        }

        /** 先移除客户端的计算目标，避免前置扩展读取或信任浏览器传来的计算值。 */
        void discardClientCalculations(Map<String, Object> patch) {
            calculations.keySet().forEach(patch::remove);
        }

        /** 前置扩展完成后按依赖计算，随后对提交里有变化的条件字段复核。 */
        void finish(Map<String, Object> patch) {
            Map<String, Object> record = new LinkedHashMap<>(original);
            record.putAll(patch);
            Map<String, Object> trusted = new LinkedHashMap<>(calculations.size());
            Set<String> completed = new LinkedHashSet<>();
            for (String key : calculations.keySet()) {
                calculate(key, record, trusted, new LinkedHashSet<>(), completed);
            }
            discardClientCalculations(patch);
            patch.putAll(trusted);
            fields.forEach((key, field) -> {
                if (!patch.containsKey(key) || sameValue(original.get(key), patch.get(key)) || trusted.containsKey(key)) return;
                boolean restricted = CONDITIONS.stream().limit(3).anyMatch(name -> referencesContext(field.rules().get(name)))
                        || ancestors(field).stream().anyMatch(props -> referencesContext(nodeLinkageRules(props).get("visibilityConditionConfig")));
                if (restricted && !editable(field, record)) {
                    throw error(field, "当前不可编辑");
                }
            });
        }

        private void calculate(String key, Map<String, Object> record, Map<String, Object> trusted, Set<String> visiting,
                Set<String> completed) {
            Calculation calculation = calculations.get(key);
            if (calculation == null || completed.contains(key)) return;
            FieldConfig field = calculation.field();
            if (!visiting.add(key)) throw error(field, "存在循环计算");
            for (String dependency : calculation.formula().fieldKeys()) calculate(dependency, record, trusted, visiting, completed);
            if (editable(field, record)) {
                try {
                    Object result = calculation.formula().evaluate(record, params, values.values(), values.today());
                    if (result == null || "".equals(result)) throw new IllegalArgumentException("缺少计算所需的数据");
                    Object value = convert(field, result);
                    record.put(key, value);
                    trusted.put(key, value);
                } catch (RuntimeException failure) {
                    throw error(field, failure.getMessage() == null ? "上下文公式计算失败" : failure.getMessage());
                }
            }
            visiting.remove(key);
            completed.add(key);
        }

        private boolean editable(FieldConfig field, Map<String, Object> record) {
            Map<String, Object> state = withVariables(record, values.values());
            return permitsEdit(field.props()) && ancestors(field).stream().allMatch(props -> permitsEdit(props)
                    && visible(nodeLinkageRules(props), state)) && visible(field.rules(), state)
                    && conditions.evaluate(field.rules().get("enabledConditionConfig"), null, state, true)
                    && !conditions.evaluate(field.rules().get("disabledConditionConfig"), text(field.rules().get("disabledRule")), state, false);
        }

        private List<Map<?, ?>> ancestors(FieldConfig field) {
            List<Map<?, ?>> result = new java.util.ArrayList<>();
            Set<String> visited = new LinkedHashSet<>();
            for (EntityFormNode node = nodes.get(field.node().getParentId()); node != null; node = nodes.get(node.getParentId())) {
                if (!visited.add(node.getId())) throw error(field, "所属容器存在循环引用");
                result.add(codec.readObject(node.getPropsDocument(), "已发布表单容器属性"));
            }
            return result;
        }

        private boolean permitsEdit(Map<?, ?> props) {
            Object configured = map(props.get("modeAccess")).get(mode);
            if (configured == null) configured = map(map(props.get("componentProps")).get("modeAccess")).get(mode);
            if (configured == null) configured = map(codec.readObject(text(props.get("extensionConfig")),
                    "已发布表单模式权限").get("modes")).get(mode);
            Map<?, ?> access = map(configured);
            if (flag(props.get("readonly")) || flag(props.get("hidden")) || flag(props.get("disabled"))
                    || Set.of("HIDDEN", "READONLY").contains(text(configured).toUpperCase(java.util.Locale.ROOT))
                    || Boolean.FALSE.equals(access.get("editable")) || Boolean.FALSE.equals(access.get("visible"))) return false;
            String permission = text(props.get("permissionCode"));
            return permission.isBlank() || PermissionUtil.hasPermission(permission);
        }

        private boolean visible(Map<?, ?> rules, Map<String, Object> state) {
            return conditions.evaluate(rules.get("visibilityConditionConfig"), text(rules.get("visibilityRule")), state, true);
        }

        private Object convert(FieldConfig field, Object result) {
            String type = text(field.props().get("fieldType"));
            if (!NUMBERS.contains(type)) return result;
            BigDecimal number = new BigDecimal(result.toString());
            if ("INTEGER".equals(type)) return number.intValueExact();
            if ("LONG".equals(type)) return number.longValueExact();
            if ("DECIMAL".equals(type)) {
                EntityField metadata = entityFields.findByEntityId(form.getEntityId()).stream()
                        .filter(item -> field.key().equals(item.getFieldCode())).findFirst().orElse(null);
                if (metadata != null && metadata.getFieldPrecision() != null) {
                    number = number.setScale(metadata.getFieldPrecision(), RoundingMode.HALF_UP);
                }
            }
            return number;
        }
    }

    /** 向条件求值器附加隔离的上下文变量；此结果不可交给实体写入。 */
    public static Map<String, Object> withVariables(Map<String, Object> record, Map<String, Object> variables) {
        Map<String, Object> state = new LinkedHashMap<>(record);
        state.keySet().removeIf(key -> key.startsWith("ctx."));
        variables.forEach((key, value) -> state.put("ctx." + key, value));
        return state;
    }

    private static boolean sameValue(Object left, Object right) {
        return Objects.equals(left, right) || left != null && right != null && left.toString().equals(right.toString());
    }

    private static boolean flag(Object raw) { return Boolean.TRUE.equals(raw) || "1".equals(String.valueOf(raw)); }

    private static BusinessConflictException error(FieldConfig field, String message) {
        return new BusinessConflictException("FORM_CONTEXT_RULE_FAILED", field.label() + " " + message);
    }
}
