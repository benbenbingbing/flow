package com.workflow.entity.form.application;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 前端自定义校验的持久化协议。服务端检查结构和资源上限，不执行浏览器 JS；
 * 实现版本、参数 Schema 与实体范围由前端注册表校验，服务端业务校验仍独立执行。
 */
public final class FormCustomValidatorRulePolicy {
    private static final Pattern NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_.:-]{0,99}$");
    private static final Set<String> CONFIG_KEYS = Set.of("version", "rules");
    private static final Set<String> RULE_KEYS = Set.of("name", "version", "params", "triggers");

    private FormCustomValidatorRulePolicy() {}

    /**
     * 校验已存在的 customValidators 配置；未配置时调用方不调用本方法。
     * rules=[] 是合法的显式清空，不能被通用 JSON 裁剪删除。
     * @throws IllegalArgumentException 结构、名称、版本、重复规则或触发时机不合法。
     */
    public static void validate(Object value) {
        if (!(value instanceof Map<?, ?> config) || !CONFIG_KEYS.equals(config.keySet())
                || !isVersion(config.get("version")) || ((Number) config.get("version")).longValue() != 1
                || !(config.get("rules") instanceof List<?> rules) || rules.size() > 20) {
            throw new IllegalArgumentException("自定义校验必须包含 version: 1 和 rules 数组，最多 20 条规则");
        }
        Set<String> seen = new HashSet<>();
        for (Object item : rules) {
            if (!(item instanceof Map<?, ?> rule) || !RULE_KEYS.equals(rule.keySet())
                    || !(rule.get("name") instanceof String name) || !NAME.matcher(name).matches()
                    || !isVersion(rule.get("version")) || !(rule.get("params") instanceof Map<?, ?> params)
                    || !(rule.get("triggers") instanceof List<?> triggers)) {
                throw new IllegalArgumentException("自定义校验规则需要 name、正整数 version、params 对象和 triggers 数组");
            }
            if (!seen.add(name + "@" + ((Number) rule.get("version")).longValue())) {
                throw new IllegalArgumentException("同名同版本的自定义校验不能重复配置");
            }
            if (triggers.size() > 2 || new HashSet<>(triggers).size() != triggers.size()
                    || triggers.stream().anyMatch(trigger -> !"BLUR".equals(trigger) && !"CHANGE".equals(trigger))) {
                throw new IllegalArgumentException("自定义校验触发时机只允许 BLUR、CHANGE；空数组表示仅提交");
            }
            validateJson(params, 0, new int[]{0});
        }
    }

    private static boolean isVersion(Object value) {
        if (!(value instanceof Number)) return false;
        try {
            BigDecimal number = new BigDecimal(value.toString());
            return number.scale() <= 0 && number.signum() > 0
                    && number.compareTo(BigDecimal.valueOf(9_007_199_254_740_991L)) <= 0;
        } catch (NumberFormatException error) {
            return false;
        }
    }

    /** 限制嵌套深度与大小，避免扩展参数使设计器、发布快照和浏览器校验消耗失控。 */
    private static void validateJson(Object value, int depth, int[] count) {
        if (depth > 12 || ++count[0] > 1000) throw new IllegalArgumentException("自定义校验参数过大或嵌套过深");
        if (value == null || value instanceof Boolean) return;
        if (value instanceof String text && text.length() <= 10_000) return;
        if (value instanceof Number number && Double.isFinite(number.doubleValue())) return;
        if (value instanceof List<?> list) {
            list.forEach(item -> validateJson(item, depth + 1, count));
            return;
        }
        if (value instanceof Map<?, ?> map) {
            map.forEach((key, item) -> {
                if (!(key instanceof String text) || text.length() > 100) throw new IllegalArgumentException("自定义校验参数键不合法");
                validateJson(item, depth + 1, count);
            });
            return;
        }
        throw new IllegalArgumentException("自定义校验参数必须是有限大小的 JSON 数据");
    }
}
