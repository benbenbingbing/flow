package com.workflow.migration.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

/**
 * 迁移专用引用协议。只转换有明确身份语义的位置，不猜测业务参数中的 *Id 字段。
 * 包内的 wf-ref 标识是编码，落库前必须解析成本地 ID；旧包中的裸 ID 必须重新导出。
 */
final class ConfigMigrationReferenceSupport {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Set<String> DOCUMENTS = Set.of("propsDocument", "componentProps",
            "matchConfig", "filterConfig", "availabilityRule", "visibleWhen", "enabledWhen",
            "toolbarButtons", "rowActions", "toolbarConfig", "rowActionConfig", "viewConfig",
            "actionConfigDocument", "configDocument", "legacyPropsDocument");
    private static final Map<String, String> USER_FIELDS = Map.of(
            "id", "USER", "userId", "USER", "deptId", "DEPT", "orgId", "DEPT", "roleIds", "ROLE");

    private ConfigMigrationReferenceSupport() { }

    /** URI 编码保证带逗号、斜杠的编码不会被历史人员列表拆分。 */
    static String reference(String type, String code) {
        if (code == null || code.isBlank()) throw new IllegalArgumentException("迁移引用缺少编码: " + type);
        return "wf-ref://" + type + "/" + URLEncoder.encode(code, StandardCharsets.UTF_8);
    }

    /** 严格校验类型，禁止把旧 ID 当作编码或在目标环境按 ID 猜测。 */
    static String code(String type, String value) {
        String prefix = "wf-ref://" + type + "/";
        if (value == null || !value.startsWith(prefix) || value.length() == prefix.length()) {
            throw new IllegalArgumentException("引用缺少稳定编码，请在源系统重新导出: " + type + " / " + value);
        }
        return URLDecoder.decode(value.substring(prefix.length()), StandardCharsets.UTF_8);
    }

    /**
     * 同一遍历用于导出、分析和导入，包含嵌套 JSON 字符串及规则树。
     * subForm 回调负责固定版本引用；返回新对象，避免改写历史快照和签名输入。
     */
    static Map<String, Object> rewrite(Map<String, Object> source,
            BiFunction<String, String, String> identity,
            UnaryOperator<Map<String, Object>> subForm) {
        return object(walk(source, identity, subForm));
    }

    private static Object walk(Object value, BiFunction<String, String, String> identity,
            UnaryOperator<Map<String, Object>> subForm) {
        if (value instanceof Collection<?> values) {
            return values.stream().map(item -> walk(item, identity, subForm)).toList();
        }
        if (!(value instanceof Map<?, ?> raw)) return value;
        Map<String, Object> result = object(raw);
        for (Map.Entry<String, Object> entry : new ArrayList<>(result.entrySet())) {
            Object child = entry.getValue();
            if (child instanceof String text && DOCUMENTS.contains(entry.getKey()) && !text.isBlank()) {
                try {
                    Object parsed = JSON.readValue(text, Object.class);
                    result.put(entry.getKey(), JSON.writeValueAsString(walk(parsed, identity, subForm)));
                } catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                    throw new IllegalArgumentException("迁移配置 JSON 无效: " + entry.getKey(), exception);
                }
            } else if (!Set.of("paramsJson", "extraParams", "implementationConfigDocument", "childFormReleaseRef").contains(entry.getKey())) {
                result.put(entry.getKey(), walk(child, identity, subForm));
            }
        }
        String scopeType = text(result.get("scopeType")).toUpperCase(java.util.Locale.ROOT);
        if (Set.of("USER", "ROLE", "GROUP", "DEPT", "ORG").contains(scopeType)) {
            rewriteValue(result, "targetIds", "ORG".equals(scopeType) ? "DEPT" : scopeType, identity);
        }
        if ("USER_FIELD".equalsIgnoreCase(text(result.get("type")))) {
            String type = USER_FIELDS.get(text(result.get("field")));
            if (type != null) rewriteValue(result, "value", type, identity);
        }
        // 模板只用于复制初始化，迁移保留已复制的实际属性，不能携带源库绑定。
        if (result.containsKey("templateId") || result.containsKey("templateVersion")) {
            result.remove("templateId");
            result.remove("templateVersion");
            result.remove("localOverrides");
            result.remove("localOverridesDocument");
        }
        if (result.get("inactive") instanceof Map<?, ?> inactive && inactive.containsKey("template")) {
            Map<String, Object> retained = object(inactive);
            retained.remove("template");
            result.put("inactive", retained);
        }
        return subForm.apply(result);
    }

    private static void rewriteValue(Map<String, Object> result, String key, String type,
            BiFunction<String, String, String> identity) {
        if (!result.containsKey(key)) return;
        Object value = result.get(key);
        if (value instanceof Collection<?> values) {
            result.put(key, values.stream().map(item -> scalar(item, type, identity)).toList());
        } else {
            result.put(key, scalar(value, type, identity));
        }
    }

    private static Object scalar(Object value, String type, BiFunction<String, String, String> identity) {
        if (value == null || text(value).isBlank()) return value;
        if (value instanceof Map<?, ?>) return value; // 运行时变量对象不是固定身份常量。
        String key = text(value);
        if (key.contains("${") || key.contains("#{")) return value;
        return identity.apply(type, key);
    }

    static Map<String, Object> object(Object value) {
        return value instanceof Map<?, ?> ? JSON.convertValue(value, new TypeReference<>() { }) : new LinkedHashMap<>();
    }

    static String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
