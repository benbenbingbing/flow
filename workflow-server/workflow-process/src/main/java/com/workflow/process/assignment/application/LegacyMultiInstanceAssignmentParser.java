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

    /**
     * 初始化旧版多实例分配解析器，保存构造参数供后续方法使用。
     */
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
     *
     * @param primary 主要，供本方法解析旧版多实例分配解析器时使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 解析后的旧版多实例分配解析器结果，供调用方继续处理
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
     *
     * @param primary 主要，供本方法合并{@code configs}时使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return {@code configs}键值结果，供调用方继续处理
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

    /**
     * 按运行时实际优先级读取人员解析器编码。
     *
     * <p>v2 只读取基础办理人配置；无 v2 标记时，只要存在有效的历史
     * 多实例来源，就必须完全以历史来源为准。尤其是历史静态人员不能因
     * 残留的基础 resolver 字段被误判为动态解析器。</p>
     *
     * @param config 节点办理人配置
     * @return 生效的 resolver 编码；当前来源不是 resolver 时返回空串
     */
    public static String effectiveResolverCode(Map<String, ?> config) {
        return effectiveResolver(config, true).resolverCode();
    }

    /**
     * 按规则源是否真实为多实例，读取运行时实际生效的解析器及参数。
     *
     * <p>历史 multiInstance* 字段只属于多实例规则源；普通任务即使残留
     * 这些字段，运行时仍读取基础 assigneeType。将节点上下文纳入统一投影，
     * 避免发布器、预览和运行时分别猜测 legacy 是否生效。</p>
     *
     * @param config 人员配置
     * @param multiInstanceSource 规则源 UserTask 是否真实包含多实例循环
     * @return 生效解析器投影；静态来源或非解析器来源返回空编码
     */
    public static EffectiveResolver effectiveResolver(
            Map<String, ?> config,
            boolean multiInstanceSource) {
        Map<String, ?> source = config == null ? Map.of() : config;
        if (usesLegacyMultiInstanceAssignment(
                source, multiInstanceSource)) {
            LegacyAssignment legacy = parse(source);
            return legacy.resolver()
                    ? new EffectiveResolver(
                    nullToEmpty(legacy.resolverCode()),
                    legacy.resolverExtraParams(),
                    true)
                    : EffectiveResolver.legacyStatic();
        }
        String type = nullToEmpty(firstText(source.get("assigneeType")))
                .toLowerCase(java.util.Locale.ROOT);
        if (!"interface".equals(type) && !"resolver".equals(type)) {
            return EffectiveResolver.baseStatic();
        }
        return new EffectiveResolver(
                nullToEmpty(firstText(
                        source.get("resolverCode"),
                        source.get("interfaceName"))),
                mapValue(source.get("extraParams")),
                false);
    }

    /**
     * 历史独立多实例来源仅在真实多实例 UserTask 上具有运行时优先级。
     *
     * @param config 配置内容，决定后续使用旧版多实例分配的处理规则
     * @param multiInstanceSource 多实例来源，供本方法处理使用旧版多实例分配时使用
     * @return 使用旧版多实例分配条件成立时为 true，否则为 false
     */
    public static boolean usesLegacyMultiInstanceAssignment(
            Map<String, ?> config,
            boolean multiInstanceSource) {
        Map<String, ?> source = config == null ? Map.of() : config;
        return multiInstanceSource
                && !isVersionTwo(source.get("assignmentConfigVersion"))
                && parse(source).effective();
    }

    /**
     * 写入条件非空；后续读取或执行将使用更新后的状态。
     *
     * @param target 目标，供本方法写入条件非空时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     */
    private static void putIfNotEmpty(
            Map<String, Object> target,
            String key,
            List<String> values) {
        if (!values.isEmpty()) {
            target.put(key, values);
        }
    }

    /**
     * 判断是否版本{@code two}；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否版本{@code two}的原始输入，结果供调用方继续使用
     * @return 版本{@code two}条件成立时为 true，否则为 false
     */
    private static boolean isVersionTwo(Object value) {
        return value != null
                && "2".equals(String.valueOf(value).trim());
    }

    /**
     * 添加值集合；结果供后续流程传递或持久化。
     *
     * @param target 目标，供本方法添加值集合时使用
     * @param raw 待添加值集合的原始输入，结果供调用方继续使用
     */
    private static void addValues(
            Collection<String> target,
            Object raw) {
        target.addAll(values(raw));
    }

    /**
     * 添加角色集合；结果供后续流程传递或持久化。
     *
     * @param target 目标，作为 {@code addRole} 的输入影响后续处理
     * @param raw 待添加角色集合的原始输入，结果供调用方继续使用
     */
    private static void addRoles(
            Collection<String> target,
            Object raw) {
        for (String value : values(raw)) {
            addRole(target, value);
        }
    }

    /**
     * 添加角色；结果供后续流程传递或持久化。
     *
     * @param target 目标，供本方法添加角色时使用
     * @param value 待添加角色的原始输入，结果供调用方继续使用
     */
    private static void addRole(
            Collection<String> target,
            String value) {
        String normalized = value.startsWith("ROLE_")
                ? value.substring(5).trim() : value;
        if (StringUtils.hasText(normalized)) {
            target.add(normalized);
        }
    }

    /**
     * 整理值集合数据，供调用方遍历或继续处理。
     *
     * @param raw 待处理值集合的原始输入，结果供调用方继续使用
     * @return 旧版多实例分配解析器集合，供调用方遍历或展示
     */
    private static List<String> values(Object raw) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        collectValues(values, raw);
        return List.copyOf(values);
    }

    /**
     * 收集值集合；结果供调用方的后续步骤使用。
     *
     * @param target 目标，作为 {@code collection.forEach} 的输入影响后续处理
     * @param raw 待收集值集合的原始输入，结果供调用方继续使用
     */
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

    /**
     * 将动态值转换为键值映射，供后续字段读取和校验。
     *
     * @param value 待处理映射值的原始输入，结果供调用方继续使用
     * @return 映射值键值结果，供调用方继续处理
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        return Collections.unmodifiableMap(
                (Map<String, Object>) new LinkedHashMap<>(map));
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private static String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    /**
     * 生成空值截止空文本，供后续匹配或展示。
     *
     * @param value 待处理空值截止空的原始输入，结果供调用方继续使用
     * @return 处理后的空值截止空文本，供调用方比较或展示
     */
    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    /**
     * 规范化后的旧人员来源。仅存在空白历史字段时 {@link #effective()} 为
     * false，调用方应回退到基础办理人配置，避免空透传字段改变部署语义。
     *
     * @param declared {@code declared}，保存在对象中供后续校验、查询或展示
     * @param resolver 解析器，保存在对象中供后续校验、查询或展示
     * @param resolverCode 解析器编码，后续用于处理旧版分配时定位或关联目标
     * @param resolverExtraParams 解析器附加参数，保存在对象中供后续校验、查询或展示
     * @param userKeys 用户键集合，保存在对象中供后续校验、查询或展示
     * @param groupKeys 分组键集合，保存在对象中供后续校验、查询或展示
     * @param roleKeys 角色键集合，保存在对象中供后续校验、查询或展示
     */
    public record LegacyAssignment(
            boolean declared,
            boolean resolver,
            String resolverCode,
            Map<String, Object> resolverExtraParams,
            List<String> userKeys,
            List<String> groupKeys,
            List<String> roleKeys) {

        /**
         * 处理空，并将结果传给后续步骤。
         *
         * @return 处理后的空结果，供调用方继续处理
         */
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

        /**
         * 是否存在会实际参与分配的解析器或静态人员。
         *
         * @return 有效条件成立时为 true，否则为 false
         */
        public boolean effective() {
            return resolver
                    || !userKeys.isEmpty()
                    || !groupKeys.isEmpty()
                    || !roleKeys.isEmpty();
        }

        /**
         * 历史静态键中是否包含无法安全预览的表达式。
         *
         * @return 表达式条件成立时为 true，否则为 false
         */
        public boolean containsExpression() {
            return containsExpression(userKeys)
                    || containsExpression(groupKeys)
                    || containsExpression(roleKeys);
        }

        /**
         * 判断是否包含表达式；判断结果决定调用方的后续分支。
         *
         * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
         * @return 表达式条件成立时为 true，否则为 false
         */
        private boolean containsExpression(Collection<String> values) {
            return values.stream().anyMatch(value -> value.contains("${")
                    || value.contains("#{"));
        }
    }

    /**
     * 运行时生效的 resolver 坐标及来源代际。
     *
     * @param resolverCode 解析器编码，后续用于处理有效解析器时定位或关联目标
     * @param extraParams 附加参数，后续传给解析器或执行器
     * @param legacy 旧版，保存在对象中供后续校验、查询或展示
     */
    public record EffectiveResolver(
            String resolverCode,
            Map<String, Object> extraParams,
            boolean legacy) {

        /**
         * 处理旧版{@code static}，并将结果传给后续步骤。
         *
         * @return 处理后的旧版{@code static}结果，供调用方继续处理
         */
        private static EffectiveResolver legacyStatic() {
            return new EffectiveResolver("", Map.of(), true);
        }

        /**
         * 处理基础{@code static}，并将结果传给后续步骤。
         *
         * @return 处理后的基础{@code static}结果，供调用方继续处理
         */
        private static EffectiveResolver baseStatic() {
            return new EffectiveResolver("", Map.of(), false);
        }

        /**
         * 判断已配置条件是否成立，供调用方选择后续分支。
         *
         * @return 已配置条件成立时为 true，否则为 false
         */
        public boolean configured() {
            return StringUtils.hasText(resolverCode);
        }
    }
}
