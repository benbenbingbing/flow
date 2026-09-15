package com.workflow.entity.form.application;

import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** 表单字段间比较的配置契约；规则存在即生效，不接受独立模式或跳过开关。 */
public final class FormCrossFieldRulePolicy {
    public static final int MAX_RULES = 20;
    private static final Set<String> NUMBER_TYPES = Set.of("INTEGER", "LONG", "DECIMAL", "DOUBLE");
    private static final Set<String> OPERATORS = Set.of("EQ", "NE", "GT", "GE", "LT", "LE");
    private static final Set<String> RULE_KEYS = Set.of("id", "operator", "targetFieldCode", "message");
    private static final Pattern FIELD_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final Pattern RULE_ID = Pattern.compile("[A-Za-z0-9_-]{1,100}");

    private FormCrossFieldRulePolicy() {}

    /** 已验证的不可变规则；当前字段由规则所属节点确定。 */
    public record Rule(String id, String operator, String targetFieldCode, String message) {}

    public static boolean supports(String type) {
        return type != null && (NUMBER_TYPES.contains(type) || "DATE".equals(type) || "DATETIME".equals(type));
    }

    public static boolean compatible(String left, String right) {
        if (left == null || right == null) return false;
        return NUMBER_TYPES.contains(left) ? NUMBER_TYPES.contains(right) : supports(left) && left.equals(right);
    }

    /** 返回当前实体的普通绑定字段；容器内 FIELD 仍属本实体，子表容器内字段不参与。 */
    public static List<EntityFormField> boundFields(EntityForm form) {
        List<EntityFormField> fields = form.getFields() == null ? List.of() : form.getFields();
        if (form.getNodes() == null || form.getNodes().isEmpty()) return fields;
        Map<String, EntityFormNode> byId = new LinkedHashMap<>();
        form.getNodes().forEach(node -> byId.put(node.getId(), node));
        return fields.stream().filter(field -> {
            EntityFormNode node = byId.get(field.getId());
            if (node == null || !"FIELD".equals(node.getNodeType()) || !"ENTITY_FIELD".equals(node.getBindingType())) return false;
            Set<String> visited = new HashSet<>();
            while (node != null) {
                if (!visited.add(node.getId()) || "SUB_FORM".equals(node.getNodeType()) || "REPEATER".equals(node.getNodeType())) return false;
                node = byId.get(node.getParentId());
            }
            return true;
        }).toList();
    }

    /**
     * 校验节点配置结构与字段类型，不查询其他节点。空配置/空数组表示删除规则。
     * @throws IllegalArgumentException 未知版本、非法键、重复规则或类型不支持
     */
    public static List<Rule> parse(Object value, String fieldType) {
        if (value == null) return List.of();
        if (!(value instanceof Map<?, ?> config)
                || !(config.get("version") instanceof Number version)
                || !"1".equals(version.toString())
                || !(config.get("rules") instanceof List<?> items)
                || !Set.of("version", "rules").containsAll(config.keySet())) {
            throw invalid("配置必须包含 version=1 和 rules 数组，且不能包含额外配置项");
        }
        if (items.size() > MAX_RULES) throw invalid("每个字段最多配置 " + MAX_RULES + " 条规则");
        if (!items.isEmpty() && (fieldType == null || !supports(fieldType))) throw invalid("当前字段类型不支持跨字段校验");
        Set<String> ids = new HashSet<>();
        Set<String> comparisons = new HashSet<>();
        List<Rule> rules = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> map) || !RULE_KEYS.containsAll(map.keySet())) throw invalid("规则结构无效或包含不支持的配置项");
            String id = requiredText(map, "id");
            String operator = requiredText(map, "operator");
            String target = requiredText(map, "targetFieldCode");
            if (!RULE_ID.matcher(id).matches() || !ids.add(id)) throw invalid("规则标识无效或重复");
            if (!OPERATORS.contains(operator)) throw invalid("比较方式无效");
            if (!FIELD_CODE.matcher(target).matches()) throw invalid("比较字段编码无效");
            if (!comparisons.add(operator + ":" + target)) throw invalid("比较方式和目标字段重复");
            Object messageValue = map.get("message");
            if (map.containsKey("message") && !(messageValue instanceof String)) throw invalid("错误提示必须为文本");
            String message = messageValue instanceof String text ? text : "";
            if (message.codePointCount(0, message.length()) > 200) throw invalid("错误提示不能超过 200 字");
            rules.add(new Rule(id, operator, target, message));
        }
        return List.copyOf(rules);
    }

    /**
     * 保存/发布时校验完整引用。fieldTypes 必须是当前表单 FIELD 与所属实体真实字段的交集，
     * 类型来自实体元数据，不能接受虚拟字段或客户端伪造类型。
     */
    public static void validateReferences(Object config, String ownerCode, Map<String, String> fieldTypes) {
        if (config == null) return;
        String ownerType = fieldTypes.get(ownerCode);
        List<Rule> rules = parse(config, ownerType);
        for (Rule rule : rules) {
            if (ownerCode.equals(rule.targetFieldCode())) throw invalid("不能与当前字段自身比较");
            String targetType = fieldTypes.get(rule.targetFieldCode());
            if (targetType == null) throw invalid("比较字段未加入当前实体表单或已失效: " + rule.targetFieldCode());
            if (!compatible(ownerType, targetType)) throw invalid("比较字段类型不兼容: " + rule.targetFieldCode());
        }
    }

    public static boolean passes(String operator, int comparison) {
        return switch (operator) {
            case "EQ" -> comparison == 0;
            case "NE" -> comparison != 0;
            case "GT" -> comparison > 0;
            case "GE" -> comparison >= 0;
            case "LT" -> comparison < 0;
            case "LE" -> comparison <= 0;
            default -> throw invalid("比较方式无效");
        };
    }

    public static String operatorLabel(String operator) {
        return switch (operator) {
            case "EQ" -> "等于";
            case "NE" -> "不等于";
            case "GT" -> "大于";
            case "GE" -> "大于等于";
            case "LT" -> "小于";
            case "LE" -> "小于等于";
            default -> throw invalid("比较方式无效");
        };
    }

    private static String requiredText(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof String text) || text.isEmpty()) throw invalid(key + " 不能为空");
        return text;
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("跨字段校验：" + message);
    }
}
