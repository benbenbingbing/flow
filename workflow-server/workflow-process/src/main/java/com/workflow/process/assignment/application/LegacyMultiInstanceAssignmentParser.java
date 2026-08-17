package com.workflow.process.assignment.application;

import org.springframework.util.StringUtils;

import java.lang.reflect.Array;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 将历史多实例独立人员字段归一为统一、可枚举的人员来源。
 *
 * <p>早期版本先后使用过 username/code、ID 和混合人员字段。发布校验、
 * 下一审批人预览以及 Flowable 多实例监听器必须复用同一投影，否则旧部署
 * 可能通过发布却在运行时得到不同的参与人集合。</p>
 */
public final class LegacyMultiInstanceAssignmentParser {

    private static final Set<String> LEGACY_KEYS = Set.of(
            "multiInstanceUsers",
            "multiInstanceUserIds",
            "multiInstanceUsernames",
            "multiInstanceGroupIds",
            "multiInstanceGroupCodes",
            "multiInstanceRoleIds",
            "multiInstanceRoleCodes",
            "collectionSource",
            "collectionResolverCode",
            "collectionInterface",
            "collectionExtraParams");

    private LegacyMultiInstanceAssignmentParser() {
    }

    /**
     * 解析旧配置，保留首次出现顺序并对各代同义字段做并集合并。
     *
     * @param config 单份历史配置
     * @return 不可变的历史人员来源投影
     */
    public static LegacyAssignment parse(Map<String, ?> config) {
        return parse(config, Map.of());
    }

    /**
     * 合并 assigneeConfig 与更早期 multiInstanceConfig 的历史人员字段。
     * 同名静态字段按主配置、备用配置顺序取并集；明确 variable 来源不会被
     * 备用或残留 resolverCode 误判为解析器。
     */
    public static LegacyAssignment parse(
            Map<String, ?> primary,
            Map<String, ?> fallback) {
        Map<String, ?> main = primary == null ? Map.of() : primary;
        Map<String, ?> secondary = fallback == null ? Map.of() : fallback;
        if (main.isEmpty() && secondary.isEmpty()) {
            return LegacyAssignment.empty();
        }
        LinkedHashSet<String> users = new LinkedHashSet<>();
        LinkedHashSet<String> groups = new LinkedHashSet<>();
        LinkedHashSet<String> roles = new LinkedHashSet<>();
        addValues(users, main.get("multiInstanceUsernames"));
        addValues(users, main.get("multiInstanceUserIds"));
        addValues(users, secondary.get("multiInstanceUsernames"));
        addValues(users, secondary.get("multiInstanceUserIds"));
        addValues(groups, main.get("multiInstanceGroupCodes"));
        addValues(groups, main.get("multiInstanceGroupIds"));
        addValues(groups, secondary.get("multiInstanceGroupCodes"));
        addValues(groups, secondary.get("multiInstanceGroupIds"));
        addRoles(roles, main.get("multiInstanceRoleCodes"));
        addRoles(roles, main.get("multiInstanceRoleIds"));
        addRoles(roles, secondary.get("multiInstanceRoleCodes"));
        addRoles(roles, secondary.get("multiInstanceRoleIds"));

        // 最早期 mixed 字段无法区分用户和组；沿用设计器迁移语义：ROLE_
        // 前缀可靠映射为角色，其余键作为用户键，由本地用户查询兼容 ID。
        List<String> mixed = new java.util.ArrayList<>(
                values(main.get("multiInstanceUsers")));
        mixed.addAll(values(secondary.get("multiInstanceUsers")));
        for (String value : mixed) {
            if (value.startsWith("ROLE_")) {
                addRole(roles, value);
            } else {
                users.add(value);
            }
        }

        String source = firstText(
                main.get("collectionSource"),
                secondary.get("collectionSource"));
        String resolverCode = firstText(
                main.get("collectionResolverCode"),
                main.get("collectionInterface"),
                secondary.get("collectionResolverCode"),
                secondary.get("collectionInterface"));
        boolean resolver = "interface".equalsIgnoreCase(source)
                || "resolver".equalsIgnoreCase(source)
                || (!StringUtils.hasText(source)
                && StringUtils.hasText(resolverCode));
        boolean declared = LEGACY_KEYS.stream().anyMatch(
                key -> main.containsKey(key)
                        || secondary.containsKey(key));
        Object extraParams = main.containsKey("collectionExtraParams")
                ? main.get("collectionExtraParams")
                : secondary.get("collectionExtraParams");
        return new LegacyAssignment(
                declared,
                resolver,
                resolverCode,
                mapValue(extraParams),
                List.copyOf(users),
                List.copyOf(groups),
                List.copyOf(roles));
    }

    /**
     * 构造运行时统一读取的配置视图。v2 明确忽略所有旧人员字段；无版本
     * 配置则把两份历史静态来源归一为 canonical username/code 列表。
     */
    public static Map<String, Object> mergeConfigs(
            Map<String, ?> primary,
            Map<String, ?> fallback) {
        Map<String, ?> main = primary == null ? Map.of() : primary;
        Map<String, ?> secondary = fallback == null ? Map.of() : fallback;
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        secondary.forEach(result::put);
        main.forEach(result::put);
        LEGACY_KEYS.forEach(result::remove);
        if (isVersionTwo(result.get("assignmentConfigVersion"))) {
            return result;
        }

        LegacyAssignment legacy = parse(main, secondary);
        if (legacy.resolver()) {
            result.put("collectionSource", "resolver");
            result.put("collectionResolverCode", legacy.resolverCode());
            result.put(
                    "collectionExtraParams",
                    legacy.resolverExtraParams());
        } else if (legacy.effective()) {
            result.put("collectionSource", "variable");
            putIfNotEmpty(
                    result,
                    "multiInstanceUsernames",
                    legacy.userKeys());
            putIfNotEmpty(
                    result,
                    "multiInstanceGroupCodes",
                    legacy.groupKeys());
            putIfNotEmpty(
                    result,
                    "multiInstanceRoleCodes",
                    legacy.roleKeys());
        }
        return result;
    }

    private static void putIfNotEmpty(
            Map<String, Object> target,
            String key,
            List<String> values) {
        if (!values.isEmpty()) {
            target.put(key, values);
        }
    }

    private static boolean isVersionTwo(Object value) {
        return value != null
                && "2".equals(String.valueOf(value).trim());
    }

    private static void addValues(
            Collection<String> target,
            Object raw) {
        target.addAll(values(raw));
    }

    private static void addRoles(
            Collection<String> target,
            Object raw) {
        for (String value : values(raw)) {
            addRole(target, value);
        }
    }

    private static void addRole(
            Collection<String> target,
            String value) {
        String normalized = value.startsWith("ROLE_")
                ? value.substring(5).trim() : value;
        if (StringUtils.hasText(normalized)) {
            target.add(normalized);
        }
    }

    private static List<String> values(Object raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectValues(values, raw);
        return List.copyOf(values);
    }

    private static void collectValues(
            Collection<String> target,
            Object raw) {
        if (raw == null) {
            return;
        }
        if (raw instanceof Collection<?> collection) {
            collection.forEach(value -> collectValues(target, value));
            return;
        }
        if (raw.getClass().isArray()) {
            for (int index = 0; index < Array.getLength(raw); index++) {
                collectValues(target, Array.get(raw, index));
            }
            return;
        }
        for (String item : String.valueOf(raw).split(",")) {
            String value = item.trim();
            if (StringUtils.hasText(value)) {
                target.add(value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return Collections.unmodifiableMap(
                (Map<String, Object>) new LinkedHashMap<>(map));
    }

    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * 规范化后的旧人员来源。仅存在空白历史字段时 {@link #effective()} 为
     * false，调用方应回退到基础办理人配置，避免空透传字段改变部署语义。
     */
    public record LegacyAssignment(
            boolean declared,
            boolean resolver,
            String resolverCode,
            Map<String, Object> resolverExtraParams,
            List<String> userKeys,
            List<String> groupKeys,
            List<String> roleKeys) {

        private static LegacyAssignment empty() {
            return new LegacyAssignment(
                    false,
                    false,
                    null,
                    Map.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }

        /** 是否存在会实际参与分配的解析器或静态人员。 */
        public boolean effective() {
            return resolver
                    || !userKeys.isEmpty()
                    || !groupKeys.isEmpty()
                    || !roleKeys.isEmpty();
        }

        /** 历史静态键中是否包含无法安全预览的表达式。 */
        public boolean containsExpression() {
            return containsExpression(userKeys)
                    || containsExpression(groupKeys)
                    || containsExpression(roleKeys);
        }

        private boolean containsExpression(Collection<String> values) {
            return values.stream().anyMatch(value -> value.contains("${")
                    || value.contains("#{"));
        }
    }
}
