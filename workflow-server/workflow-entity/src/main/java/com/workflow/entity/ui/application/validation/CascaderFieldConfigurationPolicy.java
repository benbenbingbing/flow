package com.workflow.entity.ui.application.validation;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 级联字段的发布契约。与 PC、移动端共享固定的节点语义，避免发布后各端解释不同。
 */
final class CascaderFieldConfigurationPolicy {

    private CascaderFieldConfigurationPolicy() {
    }

    /**
     * 校验组件参数并返回唯一启用的字段选项绑定；静态模式返回空映射。
     * 这里只检查声明，接口是否可读、是否属于当前表单由发布引用校验器核验。
     */
    static Map<?, ?> validate(Map<?, ?> props, Map<?, ?> bindings, String path) {
        String source = option(props, "cascaderSource", "STATIC");
        String loadMode = option(props, "cascaderLoadMode", "ALL");
        if (!Set.of("STATIC", "INTERFACE").contains(source)) {
            throw invalid(path, "数据来源必须为 STATIC 或 INTERFACE");
        }
        if (!Set.of("ALL", "CHILDREN").contains(loadMode)) {
            throw invalid(path, "加载方式必须为 ALL 或 CHILDREN");
        }
        if ("STATIC".equals(source)) {
            Object options = props.get("cascaderOptions");
            if (options != null) {
                validateTree(options, 1, new int[]{0}, path);
            }
            return Map.of();
        }
        Object configured = bindings.get("FIELD_OPTIONS");
        List<?> candidates = configured == null ? List.of()
                : configured instanceof List<?> list ? list : List.of(configured);
        List<?> active = candidates.stream().filter(value ->
                !(value instanceof Map<?, ?> binding)
                        || !Boolean.FALSE.equals(binding.get("enabled"))).toList();
        if (active.size() != 1 || !(active.get(0) instanceof Map<?, ?> binding)
                || !hasText(binding.get("extensionId"))
                && !(hasText(binding.get("serviceId")) && hasText(binding.get("operationCode")))) {
            throw invalid(path, "接口模式必须配置且仅配置一条启用的 FIELD_OPTIONS 接口");
        }
        return binding;
    }

    /**
     * 静态树也使用接口全量树契约；同级编码唯一，跨分支重名通过完整路径区分。
     * 限制深度和数量，避免误填超大树导致两个客户端递归或渲染失控。
     */
    private static void validateTree(Object value, int depth, int[] count, String path) {
        if (!(value instanceof List<?> nodes)) {
            throw invalid(path, "选项必须为 JSON 数组");
        }
        if (depth > 32) throw invalid(path, "选项层级不能超过 32 层");
        Set<String> values = new HashSet<>();
        for (Object item : nodes) {
            if (++count[0] > 10000) throw invalid(path, "选项不能超过 10000 个节点");
            if (!(item instanceof Map<?, ?> node)) throw invalid(path, "每个选项必须为对象");
            Object code = node.get("value");
            String key;
            if (code instanceof String text && !text.isBlank()) {
                key = "string:" + text;
            } else if (code instanceof Number number && Double.isFinite(number.doubleValue())) {
                key = "number:" + new BigDecimal(number.toString()).stripTrailingZeros().toPlainString();
            } else {
                throw invalid(path, "选项 value 必须为非空字符串或有限数字");
            }
            if (!values.add(key)) throw invalid(path, "同一级选项 value 必须唯一: " + code);
            if (!(node.get("label") instanceof String label) || label.isBlank()) {
                throw invalid(path, "选项 label 必须为非空字符串");
            }
            for (String flag : List.of("disabled", "leaf")) {
                if (node.containsKey(flag) && !(node.get(flag) instanceof Boolean)) {
                    throw invalid(path, "选项 " + flag + " 必须为布尔值");
                }
            }
            Object children = node.get("children");
            if (node.containsKey("children") && !(children instanceof List<?>)) {
                throw invalid(path, "选项 children 必须为数组");
            }
            boolean hasChildren = children instanceof List<?> list && !list.isEmpty();
            if (Boolean.TRUE.equals(node.get("leaf")) && hasChildren
                    || Boolean.FALSE.equals(node.get("leaf")) && !hasChildren) {
                throw invalid(path, "全量选项的 leaf 与 children 不一致");
            }
            if (hasChildren) validateTree(children, depth + 1, count, path);
        }
    }

    private static String option(Map<?, ?> props, String key, String fallback) {
        Object value = props.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private static boolean hasText(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private static IllegalArgumentException invalid(String path, String message) {
        return new IllegalArgumentException("级联选择" + message + ": " + path);
    }
}
