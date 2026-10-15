package com.workflow.entity.ui.application.validation;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 子页面内置按钮的纯展示配置。动作、权限与显隐由运行时既有规则决定，不能写入此配置。
 */
public final class BuiltInButtonAppearancePolicy {

    public static final Set<String> RELATED_FORM_BUTTONS = Set.of("save", "open");
    public static final Set<String> RELATED_LIST_BUTTONS = Set.of("refresh", "open");
    public static final Set<String> COMPOSITION_BUTTONS = Set.of("add", "remove");
    private static final Set<String> ACTION_BAR_KEYS = Set.of("position", "align", "gap", "buttons");
    private static final Set<String> BUTTON_KEYS = Set.of(
            "label", "icon", "buttonType", "buttonAppearance", "size", "textColor",
            "backgroundColor", "borderColor", "borderRadius", "sort");
    private static final Set<String> ICONS = Set.of(
            "", "Check", "Close", "Document", "Download", "Edit", "Link", "Message", "Plus",
            "Printer", "Promotion", "Refresh", "RefreshLeft", "Select", "Setting", "Upload", "View", "Delete");
    private static final Pattern HEX_COLOR = Pattern.compile("#(?:[A-Fa-f0-9]{3}|[A-Fa-f0-9]{4}|[A-Fa-f0-9]{6}|[A-Fa-f0-9]{8})");

    private BuiltInButtonAppearancePolicy() {
    }

    /**
     * 校验并复制内置按钮外观；只保留调用方明确提供的属性，避免改写历史默认样式。
     *
     * @param raw 已显式配置的 actionBar，不能为 null
     * @param allowedButtons 当前宿主已有的动作标识，不能通过配置新增或替换动作
     * @return 不含业务行为或隐式默认值的独立配置副本
     * @throws IllegalArgumentException 存在未知字段、动作或非法外观值
     */
    public static Map<String, Object> normalize(Object raw, Set<String> allowedButtons) {
        Map<String, Object> source = requireObject(raw, "内置按钮外观");
        requireAllowedKeys(source, ACTION_BAR_KEYS, "内置按钮外观");
        Map<String, Object> result = new LinkedHashMap<>();
        putEnum(result, source, "position", Set.of("DEFAULT", "TOP", "BOTTOM", "BOTH"));
        putEnum(result, source, "align", Set.of("DEFAULT", "LEFT", "CENTER", "RIGHT"));
        putInteger(result, source, "gap", 40);
        if (source.containsKey("buttons")) {
            Map<String, Object> buttons = requireObject(source.get("buttons"), "内置按钮列表");
            requireAllowedKeys(buttons, allowedButtons, "内置按钮列表");
            Map<String, Object> normalized = new LinkedHashMap<>();
            buttons.forEach((key, value) -> normalized.put(key, normalizeButton(value, key)));
            result.put("buttons", normalized);
        }
        return result;
    }

    /** 白名单拒绝 visible、enabled、handler 等行为字段，确保改名或改样式不会改变固定功能。 */
    private static Map<String, Object> normalizeButton(Object raw, String action) {
        Map<String, Object> source = requireObject(raw, "内置按钮 " + action);
        requireAllowedKeys(source, BUTTON_KEYS, "内置按钮 " + action);
        Map<String, Object> result = new LinkedHashMap<>();
        if (source.containsKey("label")) {
            if (!(source.get("label") instanceof String label) || label.isBlank() || label.length() > 80) {
                throw new IllegalArgumentException("内置按钮文案必须为 1 到 80 字符的非空文本");
            }
            result.put("label", source.get("label"));
        }
        putEnum(result, source, "icon", ICONS);
        putEnum(result, source, "buttonType", Set.of("default", "primary", "success", "warning", "danger", "info"));
        putEnum(result, source, "buttonAppearance", Set.of("DEFAULT", "PLAIN", "TEXT", "LINK", "ROUND", "CIRCLE"));
        putEnum(result, source, "size", Set.of("small", "default", "large"));
        for (String key : Set.of("textColor", "backgroundColor", "borderColor")) {
            if (!source.containsKey(key)) continue;
            if (!(source.get(key) instanceof String color) || !HEX_COLOR.matcher(color).matches()) {
                throw new IllegalArgumentException(key + " 必须为十六进制颜色（#RGB、#RGBA、#RRGGBB 或 #RRGGBBAA）");
            }
            result.put(key, source.get(key));
        }
        putInteger(result, source, "borderRadius", 40);
        putInteger(result, source, "sort", 1000);
        return result;
    }

    private static void putEnum(Map<String, Object> result, Map<String, Object> source,
                                String key, Set<String> values) {
        if (!source.containsKey(key)) return;
        if (!(source.get(key) instanceof String value) || !values.contains(value)) {
            throw new IllegalArgumentException("内置按钮外观 " + key + " 的值无效");
        }
        result.put(key, source.get(key));
    }

    /** 精确检查整数，不能把小数截断后当作合法尺寸或排序值。 */
    private static void putInteger(Map<String, Object> result, Map<String, Object> source,
                                   String key, int max) {
        if (!source.containsKey(key)) return;
        if (source.get(key) instanceof Number number) {
            try {
                int value = new BigDecimal(number.toString()).intValueExact();
                if (value >= 0 && value <= max) {
                    result.put(key, value);
                    return;
                }
            } catch (ArithmeticException | NumberFormatException ignored) {
                // 非有限浮点数、超大整数和小数均不应进入 CSS 或排序配置。
            }
        }
        throw new IllegalArgumentException(key + " 必须为 0 到 " + max + " 之间的整数");
    }

    private static Map<String, Object> requireObject(Object raw, String label) {
        if (!(raw instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(label + " 必须为对象");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (!(key instanceof String text)) {
                throw new IllegalArgumentException(label + " 的字段名必须为字符串");
            }
            result.put(text, value);
        });
        return result;
    }

    private static void requireAllowedKeys(Map<String, Object> source, Set<String> allowed, String label) {
        for (String key : source.keySet()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(label + " 不支持字段: " + key + "；仅允许配置现有按钮的外观");
            }
        }
    }
}
