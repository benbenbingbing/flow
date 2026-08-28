package com.workflow.process.assignment.relative;

import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code relativeOrgPosition} extraParams 的 V1 值对象与唯一解析器。
 *
 * <p>运行时、发布校验和设计试算必须共用本类，避免三条链路对默认值
 * 或层级语义作出不同解释。</p>
 */
public record RelativeOrgPositionConfig(
        int schemaVersion,
        Anchor anchor,
        String positionCode,
        Hierarchy hierarchy,
        MultipleMatchPolicy multipleMatchPolicy) {

    public static final String RESOLVER_CODE = "relativeOrgPosition";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CHAIN_DEPTH = 32;
    private static final Set<String> TOP_LEVEL_FIELDS = Set.of(
            "schemaVersion",
            "subject",
            "anchor",
            "positionCode",
            "hierarchy",
            "multipleMatchPolicy");

    /** 对外部 JSON 执行严格、失败关闭的 V1 解析。 */
    public static RelativeOrgPositionConfig parse(
            Map<String, Object> extraParams) {
        Map<String, Object> source = extraParams == null
                ? Map.of() : extraParams;
        rejectUnknownFields(source, TOP_LEVEL_FIELDS, "extraParams");
        int schemaVersion = requiredInteger(
                source, "schemaVersion", "extraParams");
        if (schemaVersion != SCHEMA_VERSION) {
            throw invalid("不支持的 schemaVersion: " + schemaVersion);
        }
        String subject = requiredText(
                source, "subject", "extraParams").toUpperCase(Locale.ROOT);
        if (!"PROCESS_INITIATOR".equals(subject)) {
            throw invalid("subject V1 仅支持 PROCESS_INITIATOR");
        }
        Anchor anchor = enumValue(
                Anchor.class,
                requiredText(source, "anchor", "extraParams"),
                "anchor");
        String positionCode = requiredText(
                source, "positionCode", "extraParams")
                .toUpperCase(Locale.ROOT);
        if (!positionCode.matches("[A-Za-z][A-Za-z0-9_\\-]{0,99}")) {
            throw invalid("positionCode 格式不合法");
        }
        Object rawHierarchy = source.get("hierarchy");
        if (!(rawHierarchy instanceof Map<?, ?> hierarchyMap)) {
            throw invalid("hierarchy 必须是对象");
        }
        Hierarchy hierarchy = parseHierarchy(
                stringKeyMap(hierarchyMap), anchor);
        MultipleMatchPolicy policy = enumValue(
                MultipleMatchPolicy.class,
                requiredText(
                        source,
                        "multipleMatchPolicy",
                        "extraParams"),
                "multipleMatchPolicy");
        return new RelativeOrgPositionConfig(
                schemaVersion,
                anchor,
                positionCode,
                hierarchy,
                policy);
    }

    /**
     * 校验人员数量策略与实际任务模式一致。
     *
     * @param assignmentMode 普通任务的 DIRECT/CANDIDATE
     * @param multiInstance 当前节点是否由 BPMN loop 定义为多实例
     */
    public void validateAssignmentMode(
            String assignmentMode,
            boolean multiInstance) {
        if (multiInstance) {
            if (multipleMatchPolicy != MultipleMatchPolicy.ALL) {
                throw invalid("多实例任务 multipleMatchPolicy 只允许 ALL");
            }
            return;
        }
        String mode = StringUtils.hasText(assignmentMode)
                ? assignmentMode.trim().toUpperCase(Locale.ROOT)
                : "DIRECT";
        if ("DIRECT".equals(mode)) {
            if (multipleMatchPolicy == MultipleMatchPolicy.ALL) {
                throw invalid("DIRECT 任务只允许 ERROR 或 PRIMARY_OR_ERROR");
            }
            return;
        }
        if ("CANDIDATE".equals(mode)) {
            if (multipleMatchPolicy != MultipleMatchPolicy.ALL) {
                throw invalid("CANDIDATE 任务 multipleMatchPolicy 只允许 ALL");
            }
            return;
        }
        throw invalid("assignmentMode 只支持 DIRECT 或 CANDIDATE");
    }

    private static Hierarchy parseHierarchy(
            Map<String, Object> source,
            Anchor anchor) {
        LookupMode mode = enumValue(
                LookupMode.class,
                requiredText(source, "mode", "hierarchy"),
                "hierarchy.mode");
        Set<String> allowed = switch (mode) {
            case SELF -> Set.of("mode", "eligibleUnitTypes");
            case FIXED_ANCESTOR -> Set.of(
                    "mode", "ancestorHops", "eligibleUnitTypes");
            case NEAREST_WITH_HOLDER -> Set.of(
                    "mode", "startLevel", "maxHops", "eligibleUnitTypes");
            case BUSINESS_LEVEL -> Set.of(
                    "mode", "businessLevelCode", "eligibleUnitTypes");
        };
        rejectUnknownFields(source, allowed, "hierarchy." + mode.name());
        Set<String> eligibleUnitTypes = unitTypes(
                source.get("eligibleUnitTypes"), anchor);

        Integer ancestorHops = null;
        Integer startLevel = null;
        Integer maxHops = null;
        String businessLevelCode = null;
        switch (mode) {
            case SELF -> {
                // SELF 没有可隐式上溯的参数。
            }
            case FIXED_ANCESTOR -> ancestorHops = boundedInteger(
                    source,
                    "ancestorHops",
                    1,
                    MAX_CHAIN_DEPTH,
                    "hierarchy");
            case NEAREST_WITH_HOLDER -> {
                startLevel = boundedInteger(
                        source, "startLevel", 0, MAX_CHAIN_DEPTH, "hierarchy");
                maxHops = boundedInteger(
                        source, "maxHops", 1, MAX_CHAIN_DEPTH, "hierarchy");
                if (startLevel > maxHops) {
                    throw invalid("hierarchy.startLevel 不能大于 maxHops");
                }
            }
            case BUSINESS_LEVEL -> {
                businessLevelCode = requiredText(
                        source, "businessLevelCode", "hierarchy")
                        .toUpperCase(Locale.ROOT);
                // BUSINESS_LEVEL 按冻结链查找最近匹配，对外不暴露另一个截断参数；
                // 链长由快照捕获边界统一限制为 32 层。
                maxHops = MAX_CHAIN_DEPTH;
            }
        }
        return new Hierarchy(
                mode,
                ancestorHops,
                startLevel,
                maxHops,
                businessLevelCode,
                eligibleUnitTypes);
    }

    private static Set<String> unitTypes(Object raw, Anchor anchor) {
        if (raw == null) {
            return Set.of(anchor == Anchor.DEPARTMENT ? "dept" : "org");
        }
        if (!(raw instanceof Collection<?> values) || values.isEmpty()) {
            throw invalid("hierarchy.eligibleUnitTypes 必须是非空数组");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (Object value : values) {
            String type = value == null
                    ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
            if (!Set.of("dept", "org").contains(type)) {
                throw invalid("eligibleUnitTypes 只支持 dept/org: " + value);
            }
            normalized.add(type);
        }
        return Set.copyOf(normalized);
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> raw) {
        java.util.LinkedHashMap<String, Object> result =
                new java.util.LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static void rejectUnknownFields(
            Map<String, Object> source,
            Set<String> allowed,
            String path) {
        for (String field : source.keySet()) {
            if (!allowed.contains(field)) {
                throw invalid(path + " 包含不支持的字段: " + field);
            }
        }
    }

    private static String requiredText(
            Map<String, Object> source,
            String field,
            String path) {
        Object value = source.get(field);
        if (value == null || !StringUtils.hasText(String.valueOf(value))) {
            throw invalid(path + "." + field + " 不能为空");
        }
        return String.valueOf(value).trim();
    }

    private static int requiredInteger(
            Map<String, Object> source,
            String field,
            String path) {
        Object value = source.get(field);
        if (!(value instanceof Number number)) {
            throw invalid(path + "." + field + " 必须是整数");
        }
        double decimal = number.doubleValue();
        int result = number.intValue();
        if (decimal != result) {
            throw invalid(path + "." + field + " 必须是整数");
        }
        return result;
    }

    private static int boundedInteger(
            Map<String, Object> source,
            String field,
            int min,
            int max,
            String path) {
        int value = requiredInteger(source, field, path);
        if (value < min || value > max) {
            throw invalid(path + "." + field + " 必须介于 "
                    + min + ".." + max);
        }
        return value;
    }

    private static <T extends Enum<T>> T enumValue(
            Class<T> enumType,
            String raw,
            String field) {
        try {
            return Enum.valueOf(
                    enumType,
                    raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw invalid(field + " 不支持: " + raw);
        }
    }

    private static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException(
                "relativeOrgPosition 配置无效: " + message);
    }

    public enum Anchor {
        DEPARTMENT,
        ORGANIZATION
    }

    public enum LookupMode {
        SELF,
        FIXED_ANCESTOR,
        NEAREST_WITH_HOLDER,
        BUSINESS_LEVEL
    }

    public enum MultipleMatchPolicy {
        ERROR,
        PRIMARY_OR_ERROR,
        ALL
    }

    public record Hierarchy(
            LookupMode mode,
            Integer ancestorHops,
            Integer startLevel,
            Integer maxHops,
            String businessLevelCode,
            Set<String> eligibleUnitTypes) {

        public Hierarchy {
            eligibleUnitTypes = eligibleUnitTypes == null
                    ? Set.of() : Set.copyOf(eligibleUnitTypes);
        }
    }
}
