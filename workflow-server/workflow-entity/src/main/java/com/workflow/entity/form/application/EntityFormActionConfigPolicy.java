package com.workflow.entity.form.application;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 表单动作栏配置的结构与安全边界。
 */
@Component
public class EntityFormActionConfigPolicy {

    public static final Set<String> MODES =
            Set.of("create", "edit", "approve", "view");
    public static final Set<String> BUILT_IN_KEYS =
            Set.of("close", "reset", "save", "saveAndStart",
                    "submitApproval");
    private static final Set<String> BUTTON_TYPES =
            Set.of("default", "primary", "success", "warning", "danger",
                    "info");
    private static final Set<String> PLACEMENTS =
            Set.of("FOOTER", "ACTION_SLOT");
    private static final Set<String> RULE_TYPES =
            Set.of("GROUP", "RELATION", "PROCESS_STATE", "STATUS_CODE",
                    "STATUS_CATEGORY", "FIELD", "USER_FIELD");
    private static final Set<String> RULE_OPERATORS =
            Set.of("EQ", "NE", "IN", "NOT_IN", "CONTAINS",
                    "NOT_CONTAINS", "EMPTY", "NOT_EMPTY",
                    "GT", "GTE", "LT", "LTE");
    private static final Pattern BUTTON_KEY =
            Pattern.compile("[a-z][a-z0-9_-]{0,63}");
    private static final Pattern PERMISSION_CODE =
            Pattern.compile("[A-Za-z0-9_.-]+(?::[A-Za-z0-9_.-]+)+");
    private static final int MAX_CUSTOM_BUTTONS = 50;
    private static final int MAX_RULE_DEPTH = 6;
    private static final int MAX_RULE_NODES = 100;

    /**
     * 校验 viewConfig 中的动作栏配置。
     */
    public void validate(
            Map<String, Object> viewConfig,
            boolean systemEntity,
            Set<String> actionSlotKeys,
            boolean requireExistingSlots,
            Set<String> boundButtonKeys,
            boolean requireBindings) {
        if (viewConfig == null || viewConfig.isEmpty()
                || viewConfig.get("actionBar") == null) {
            return;
        }
        Map<String, Object> actionBar =
                map(viewConfig.get("actionBar"), "表单按钮配置");
        int version = integer(actionBar.getOrDefault("version", 1),
                "表单按钮配置版本");
        if (version != 1) {
            throw new IllegalArgumentException(
                    "不支持的表单按钮配置版本: " + version);
        }
        validateBuiltInOverrides(
                mapOrEmpty(actionBar.get("builtInOverrides")),
                systemEntity);
        validateCustomButtons(
                list(actionBar.get("customButtons"), "自定义表单按钮"),
                systemEntity,
                actionSlotKeys == null ? Set.of() : actionSlotKeys,
                requireExistingSlots,
                boundButtonKeys == null ? Set.of() : boundButtonKeys,
                requireBindings);
    }

    /**
     * 返回动作栏配置，未配置时返回约定默认结构。
     */
    public Map<String, Object> actionBar(Map<String, Object> viewConfig) {
        Map<String, Object> configured = viewConfig == null
                ? Map.of()
                : mapOrEmpty(viewConfig.get("actionBar"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", 1);
        result.put("builtInOverrides",
                new LinkedHashMap<>(
                        mapOrEmpty(configured.get("builtInOverrides"))));
        result.put("customButtons",
                new ArrayList<>(
                        list(configured.get("customButtons"),
                                "自定义表单按钮")));
        return result;
    }

    private void validateBuiltInOverrides(
            Map<String, Object> overrides,
            boolean systemEntity) {
        for (Map.Entry<String, Object> entry : overrides.entrySet()) {
            String key = entry.getKey();
            if (!BUILT_IN_KEYS.contains(key)) {
                throw new IllegalArgumentException(
                        "不支持的表单内置按钮: " + key);
            }
            if (systemEntity && !"close".equals(key)) {
                throw new IllegalArgumentException(
                        "平台系统表表单只能调整关闭按钮");
            }
            Map<String, Object> override =
                    map(entry.getValue(), "内置按钮覆盖配置");
            optionalBoolean(override.get("enabled"), "内置按钮启用状态");
            optionalInteger(override.get("sort"), 0, 10_000,
                    "内置按钮排序");
            optionalEnum(override.get("buttonType"), BUTTON_TYPES,
                    "内置按钮样式");
            validateModes(override.get("enabledModes"), "内置按钮适用模式");
            validateLabels(override.get("labelByMode"));
            validateRule(override.get("availabilityRule"));
        }
    }

    private void validateCustomButtons(
            List<Map<String, Object>> buttons,
            boolean systemEntity,
            Set<String> actionSlotKeys,
            boolean requireExistingSlots,
            Set<String> boundButtonKeys,
            boolean requireBindings) {
        if (systemEntity && !buttons.isEmpty()) {
            throw new IllegalArgumentException(
                    "平台系统表表单不能配置自定义按钮");
        }
        if (buttons.size() > MAX_CUSTOM_BUTTONS) {
            throw new IllegalArgumentException(
                    "单个表单最多配置 " + MAX_CUSTOM_BUTTONS + " 个自定义按钮");
        }
        Set<String> keys = new LinkedHashSet<>();
        for (Map<String, Object> button : buttons) {
            String key = text(button.get("key"));
            if (!StringUtils.hasText(key)
                    || !BUTTON_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException(
                        "自定义按钮编码必须以小写字母开头，"
                                + "且只能包含小写字母、数字、下划线和短横线");
            }
            if (BUILT_IN_KEYS.contains(key) || !keys.add(key)) {
                throw new IllegalArgumentException(
                        "自定义按钮编码重复或占用内置编码: " + key);
            }
            requireText(button.get("label"), 60, "自定义按钮名称不能为空");
            optionalBoolean(button.get("enabled"), "自定义按钮启用状态");
            optionalInteger(button.get("sort"), 0, 10_000,
                    "自定义按钮排序");
            optionalEnum(button.get("buttonType"), BUTTON_TYPES,
                    "自定义按钮样式");
            validateModes(button.get("modes"), "自定义按钮适用模式");
            String placement = normalize(
                    button.getOrDefault("placement", "FOOTER"));
            if (!PLACEMENTS.contains(placement)) {
                throw new IllegalArgumentException(
                        "自定义按钮位置只能是 FOOTER 或 ACTION_SLOT");
            }
            if ("ACTION_SLOT".equals(placement)) {
                String slotKey = text(button.get("slotKey"));
                if (!StringUtils.hasText(slotKey)) {
                    throw new IllegalArgumentException(
                            "动作插槽按钮必须选择插槽");
                }
                if (requireExistingSlots
                        && !actionSlotKeys.contains(slotKey)) {
                    throw new IllegalArgumentException(
                            "自定义按钮引用的动作插槽不存在: " + slotKey);
                }
            }
            boolean enabled = !Boolean.FALSE.equals(button.get("enabled"));
            String permission = text(button.get("perm"));
            if (enabled && (!StringUtils.hasText(permission)
                    || permission.length() > 200
                    || !PERMISSION_CODE.matcher(permission).matches())) {
                throw new IllegalArgumentException(
                        "启用的自定义按钮必须配置合法权限码: "
                                + text(button.get("label")));
            }
            optionalBoolean(
                    button.get("validateBeforeExecute"),
                    "自定义按钮执行前校验");
            validateConfirm(button.get("confirm"));
            validateRule(button.get("availabilityRule"));
            if (enabled && requireBindings
                    && !boundButtonKeys.contains(key)) {
                throw new IllegalArgumentException(
                        "启用的自定义按钮必须绑定 FORM_BUTTON_CLICK 事件: "
                                + key);
            }
        }
    }

    private void validateLabels(Object value) {
        if (value == null) {
            return;
        }
        Map<String, Object> labels = map(value, "按钮模式名称");
        for (Map.Entry<String, Object> entry : labels.entrySet()) {
            if (!MODES.contains(entry.getKey())) {
                throw new IllegalArgumentException(
                        "不支持的按钮名称模式: " + entry.getKey());
            }
            requireText(entry.getValue(), 60, "按钮名称不能为空");
        }
    }

    private void validateModes(Object value, String label) {
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> values) || values.isEmpty()) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        for (Object item : values) {
            String mode = String.valueOf(item).toLowerCase(Locale.ROOT);
            if (!MODES.contains(mode)) {
                throw new IllegalArgumentException(
                        label + "包含不支持的模式: " + item);
            }
        }
    }

    private void validateConfirm(Object value) {
        if (value == null) {
            return;
        }
        Map<String, Object> confirm = map(value, "按钮确认配置");
        optionalBoolean(confirm.get("enabled"), "按钮确认状态");
        if (Boolean.TRUE.equals(confirm.get("enabled"))) {
            requireText(confirm.get("message"), 300,
                    "启用确认时必须填写确认提示");
        }
    }

    private void validateRule(Object value) {
        if (value == null) {
            return;
        }
        Map<String, Object> rule = map(value, "按钮适用条件");
        int version = integer(rule.getOrDefault("version", 1),
                "按钮条件版本");
        if (version != 1) {
            throw new IllegalArgumentException("不支持的按钮条件版本");
        }
        String behavior = normalize(
                rule.getOrDefault("unavailableBehavior", "HIDE"));
        if (!Set.of("HIDE", "DISABLE").contains(behavior)) {
            throw new IllegalArgumentException(
                    "按钮不可用行为只能是 HIDE 或 DISABLE");
        }
        int[] count = {0};
        validateRuleNode(rule.get("root"), 1, count);
    }

    private void validateRuleNode(
            Object value,
            int depth,
            int[] count) {
        if (value == null) {
            return;
        }
        if (depth > MAX_RULE_DEPTH || ++count[0] > MAX_RULE_NODES) {
            throw new IllegalArgumentException("按钮条件规则过于复杂");
        }
        Map<String, Object> node = map(value, "按钮条件节点");
        String type = normalize(node.get("type"));
        if (!RULE_TYPES.contains(type)) {
            throw new IllegalArgumentException(
                    "不支持的按钮条件类型: " + type);
        }
        if ("GROUP".equals(type)) {
            String logic = normalize(node.get("logic"));
            if (!Set.of("AND", "OR").contains(logic)) {
                throw new IllegalArgumentException(
                        "按钮条件组逻辑只能是 AND 或 OR");
            }
            List<Map<String, Object>> children =
                    list(node.get("children"), "按钮条件子节点");
            if (children.isEmpty()) {
                throw new IllegalArgumentException("按钮条件组不能为空");
            }
            for (Map<String, Object> child : children) {
                validateRuleNode(child, depth + 1, count);
            }
            return;
        }
        if ("RELATION".equals(type)) {
            requireText(node.get("relation"), 100,
                    "按钮用户关系不能为空");
            return;
        }
        requireText(node.get("operator"), 30,
                "按钮条件运算符不能为空");
        if (!RULE_OPERATORS.contains(normalize(node.get("operator")))) {
            throw new IllegalArgumentException(
                    "不支持的按钮条件运算符: "
                            + node.get("operator"));
        }
        if (Set.of("FIELD", "USER_FIELD").contains(type)) {
            requireText(node.get("field"), 100,
                    "按钮条件字段不能为空");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value, String label) {
        if (!(value instanceof Map<?, ?> source)) {
            throw new IllegalArgumentException(label + "必须是对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, item) ->
                result.put(String.valueOf(key), item));
        return result;
    }

    private Map<String, Object> mapOrEmpty(Object value) {
        return value == null ? Map.of() : map(value, "表单按钮配置");
    }

    private List<Map<String, Object>> list(
            Object value,
            String label) {
        if (value == null) {
            return List.of();
        }
        if (!(value instanceof List<?> source)) {
            throw new IllegalArgumentException(label + "必须是数组");
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : source) {
            result.add(map(item, label + "项"));
        }
        return result;
    }

    private void optionalBoolean(Object value, String label) {
        if (value != null && !(value instanceof Boolean)) {
            throw new IllegalArgumentException(label + "必须是布尔值");
        }
    }

    private void optionalInteger(
            Object value,
            int min,
            int max,
            String label) {
        if (value == null) {
            return;
        }
        int parsed = integer(value, label);
        if (parsed < min || parsed > max) {
            throw new IllegalArgumentException(
                    label + "必须在 " + min + " 到 " + max + " 之间");
        }
    }

    private int integer(Object value, String label) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(label + "必须是整数");
        }
    }

    private void optionalEnum(
            Object value,
            Set<String> allowed,
            String label) {
        if (value == null) {
            return;
        }
        String normalized = String.valueOf(value)
                .toLowerCase(Locale.ROOT);
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException(
                    label + "不支持: " + value);
        }
    }

    private void requireText(
            Object value,
            int maxLength,
            String message) {
        String text = text(value);
        if (!StringUtils.hasText(text) || text.length() > maxLength) {
            throw new IllegalArgumentException(message);
        }
    }

    private String normalize(Object value) {
        return text(value).toUpperCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
