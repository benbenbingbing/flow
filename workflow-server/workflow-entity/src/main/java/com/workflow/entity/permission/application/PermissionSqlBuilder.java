package com.workflow.entity.permission.application;

import com.workflow.contracts.process.port.ProcessTaskAccessPort;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.integration.database.api.schema.SchemaType;
import com.workflow.entity.data.application.EntityTableDefinitionFactory;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.application.EntityPhysicalTableResolver;
import com.workflow.entity.data.application.EntityRecordTeamService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 结构化数据权限 SQL 编译器。
 */
@Component
public class PermissionSqlBuilder {

    /** 规则树最大嵌套深度。 */
    private static final int MAX_RULE_DEPTH = 6;
    /** 规则树最大节点数。 */
    private static final int MAX_RULE_NODES = 100;
    /** 与动态表命名策略一致，侧表追加 _multi 后仍需满足跨数据库的 63 字符限制。 */
    private static final int MAX_MULTI_TABLE_NAME_LENGTH = 63;
    /** 合法 SQL 标识符正则，用于字段名白名单校验。 */
    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");
    /** 支持的字段比较操作符。 */
    private static final Set<String> FIELD_OPERATORS = Set.of(
            "EQ", "NE", "IN", "NOT_IN", "CONTAINS", "NOT_CONTAINS",
            "EMPTY", "NOT_EMPTY", "GT", "GTE", "LT", "LTE");
    /** 仅支持单值或集合的操作符集合。 */
    private static final Set<String> SIMPLE_OPERATORS = Set.of("EQ", "NE", "IN", "NOT_IN");
    /** 内置的当前用户关系类型。 */
    private static final Set<String> RELATIONS = Set.of(
            "CURRENT_USER_IS_CREATOR",
            "CURRENT_USER_IS_SUBMITTER",
            "CURRENT_USER_IS_ASSIGNEE",
            "CURRENT_USER_SAME_DEPT");
    /** 流程状态枚举值。 */
    private static final Set<String> PROCESS_STATES = Set.of(
            "NOT_STARTED", "RUNNING", "COMPLETED", "TERMINATED", "WITHDRAWN");
    /** 状态分类枚举值。 */
    private static final Set<String> STATUS_CATEGORIES = Set.of(
            "NEW", "PROCESSING", "COMPLETED", "TERMINATED", "WITHDRAWN");
    /** 系统字段到数据库列名的映射（含驼峰和下划线两种形式）。 */
    private static final Map<String, String> SYSTEM_FIELD_COLUMNS = systemFieldColumns();

    private static final Set<String> FILTER_TYPES = Set.of(
            "ALL", "PERSONAL", "SUBMITTER", "CURRENT_ASSIGNEE", "HAS_TODO",
            "DEPT", "DEPT_TREE", "RULE", "TEAM", "SQL");

    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityStatusMapper statusMapper;
    private final List<EntityDataPermissionFilterProvider> filterProviders;
    private final EntityRecordTeamService teamService;
    private final EntityPhysicalTableResolver tableResolver;
    private final PermissionSqlFragmentCompiler sqlFragmentCompiler;
    private final ProcessTaskAccessPort taskAccessPort;
    private final DatabaseQueryDialect queryDialect;

    /**
     * 初始化权限SQL构建器，保存构造参数供后续方法使用。
     *
     * @param definitionMapper 定义映射器，保存在对象中供后续校验、查询或展示
     * @param fieldMapper 字段映射器，保存在对象中供后续校验、查询或展示
     * @param statusMapper 状态映射器，保存在对象中供后续校验、查询或展示
     * @param filterProviders 过滤提供者集合，保存在对象中供后续校验、查询或展示
     * @param queryDialect 查询方言，保存在对象中供后续校验、查询或展示
     */
    public PermissionSqlBuilder(
            EntityDefinitionMapper definitionMapper,
            EntityFieldMapper fieldMapper,
            EntityStatusMapper statusMapper,
            List<EntityDataPermissionFilterProvider> filterProviders,
            DatabaseQueryDialect queryDialect) {
        this(definitionMapper, fieldMapper, statusMapper, filterProviders, null, null, null, queryDialect);
    }

    /**
     * 初始化权限SQL构建器，保存构造参数供后续方法使用。
     *
     * @param definitionMapper 定义映射器，保存在对象中供后续校验、查询或展示
     * @param fieldMapper 字段映射器，保存在对象中供后续校验、查询或展示
     * @param statusMapper 状态映射器，保存在对象中供后续校验、查询或展示
     * @param filterProviders 过滤提供者集合，保存在对象中供后续校验、查询或展示
     * @param teamService 团队服务，保存在对象中供后续校验、查询或展示
     * @param queryDialect 查询方言，保存在对象中供后续校验、查询或展示
     */
    public PermissionSqlBuilder(
            EntityDefinitionMapper definitionMapper,
            EntityFieldMapper fieldMapper,
            EntityStatusMapper statusMapper,
            List<EntityDataPermissionFilterProvider> filterProviders,
            EntityRecordTeamService teamService,
            DatabaseQueryDialect queryDialect) {
        this(definitionMapper, fieldMapper, statusMapper, filterProviders, teamService, null, null, queryDialect);
    }

    /**
     * 初始化权限SQL构建器，保存构造参数供后续方法使用。
     *
     * @param definitionMapper 定义映射器，保存在对象中供后续校验、查询或展示
     * @param fieldMapper 字段映射器，保存在对象中供后续校验、查询或展示
     * @param statusMapper 状态映射器，保存在对象中供后续校验、查询或展示
     * @param filterProviders 过滤提供者集合，保存在对象中供后续校验、查询或展示
     * @param teamService 团队服务，保存在对象中供后续校验、查询或展示
     * @param tableResolver 表解析器，保存在对象中供后续校验、查询或展示
     * @param queryDialect 查询方言，保存在对象中供后续校验、查询或展示
     */
    public PermissionSqlBuilder(
            EntityDefinitionMapper definitionMapper,
            EntityFieldMapper fieldMapper,
            EntityStatusMapper statusMapper,
            List<EntityDataPermissionFilterProvider> filterProviders,
            EntityRecordTeamService teamService,
            EntityPhysicalTableResolver tableResolver,
            DatabaseQueryDialect queryDialect) {
        this(definitionMapper, fieldMapper, statusMapper, filterProviders, teamService, tableResolver, null, queryDialect);
    }

    /**
     * 初始化权限SQL构建器，保存构造参数供后续方法使用。
     *
     * @param definitionMapper 定义映射器，保存在对象中供后续校验、查询或展示
     * @param fieldMapper 字段映射器，保存在对象中供后续校验、查询或展示
     * @param statusMapper 状态映射器，保存在对象中供后续校验、查询或展示
     * @param filterProviders 过滤提供者集合，保存在对象中供后续校验、查询或展示
     * @param teamService 团队服务，保存在对象中供后续校验、查询或展示
     * @param tableResolver 表解析器，保存在对象中供后续校验、查询或展示
     * @param sqlFragmentCompiler SQL{@code fragment}{@code compiler}，保存在对象中供后续校验、查询或展示
     * @param queryDialect 查询方言，保存在对象中供后续校验、查询或展示
     */
    public PermissionSqlBuilder(
            EntityDefinitionMapper definitionMapper,
            EntityFieldMapper fieldMapper,
            EntityStatusMapper statusMapper,
            List<EntityDataPermissionFilterProvider> filterProviders,
            EntityRecordTeamService teamService,
            EntityPhysicalTableResolver tableResolver,
            PermissionSqlFragmentCompiler sqlFragmentCompiler,
            DatabaseQueryDialect queryDialect) {
        this(definitionMapper, fieldMapper, statusMapper, filterProviders,
                teamService, tableResolver, sqlFragmentCompiler, null, queryDialect);
    }

    /**
     * 初始化权限SQL构建器，保存构造参数供后续方法使用。
     *
     * @param definitionMapper 定义映射器依赖，保存到当前对象供后续业务方法调用
     * @param fieldMapper 字段映射器依赖，保存到当前对象供后续业务方法调用
     * @param statusMapper 状态映射器依赖，保存到当前对象供后续业务方法调用
     * @param filterProviders 过滤提供者集合依赖，保存到当前对象供后续业务方法调用
     * @param teamService 团队服务依赖，保存到当前对象供后续业务方法调用
     * @param tableResolver 表解析器依赖，保存到当前对象供后续业务方法调用
     * @param sqlFragmentCompiler SQL{@code fragment}{@code compiler}依赖，保存到当前对象供后续业务方法调用
     * @param taskAccessPort 任务访问端口依赖，保存到当前对象供后续业务方法调用
     * @param queryDialect 查询方言，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public PermissionSqlBuilder(
            EntityDefinitionMapper definitionMapper,
            EntityFieldMapper fieldMapper,
            EntityStatusMapper statusMapper,
            List<EntityDataPermissionFilterProvider> filterProviders,
            EntityRecordTeamService teamService,
            EntityPhysicalTableResolver tableResolver,
            PermissionSqlFragmentCompiler sqlFragmentCompiler,
            ProcessTaskAccessPort taskAccessPort,
            DatabaseQueryDialect queryDialect) {
        this.definitionMapper = definitionMapper;
        this.fieldMapper = fieldMapper;
        this.statusMapper = statusMapper;
        this.filterProviders = filterProviders == null ? List.of() : filterProviders;
        this.teamService = teamService;
        this.tableResolver = tableResolver;
        this.sqlFragmentCompiler = sqlFragmentCompiler;
        this.taskAccessPort = taskAccessPort;
        this.queryDialect = Objects.requireNonNull(queryDialect, "queryDialect");
    }

    /**
     * 预览数据过滤 SQL，不带实体编码；返回值包含绑定占位符，不可单独用于执行查询。
     *
     * @param filter 数据过滤配置
     * @param user   当前用户
     * @return SQL 条件片段，非法或为空时返回 "1=0"
     */
    public String buildFilterSql(FilterConfigDTO filter, SysUser user) {
        return buildFilterSql(null, filter, user);
    }

    /**
     * 编译数据过滤配置为 SQL 条件片段。
     *
     * <p>根据过滤类型（全部、本人、提交人、当前办理人、存在待办、相关人、部门、部门树、结构化规则、受控 SQL）
     * 生成基础范围 SQL，再叠加状态限制条件。</p>
     *
     * @param entityCode 实体编码，可为 null（不解析实体字段）
     * @param filter     数据过滤配置，为空返回 "1=0"
     * @param user       当前用户，为空返回 "1=0"
     * @return 预览条件片段；实际查询必须使用带 parameters 的重载，同时保留绑定值
     */
    public String buildFilterSql(String entityCode, FilterConfigDTO filter, SysUser user) {
        // 无参数容器的入口仅供条件预览；执行查询须使用携带参数容器的重载。
        return buildFilterSql(entityCode, filter, user, new LinkedHashMap<>());
    }

    /**
     * 编译权限条件，并将规则值和当前用户身份作为绑定参数写入查询参数容器。
     *
     * @param entityCode 实体编码，用于解析业务表
     * @param filter 数据范围配置
     * @param user 当前用户或委托人
     * @param parameters 当前查询共享的可变参数容器，多条允许、拒绝及委托规则须复用同一容器
     * @return SQL 条件片段；占位符由 Mapper 的 permissionParameters 参数提供值
     */
    public String buildFilterSql(
            String entityCode, FilterConfigDTO filter, SysUser user,
            Map<String, Object> parameters) {
        Objects.requireNonNull(parameters, "权限 SQL 参数容器不能为空");
        if (filter == null || user == null) {
            return "1=0";
        }
        FilterConfigDTO.FieldMappingDTO mapping = filter.getFieldMapping() == null
                ? new FilterConfigDTO.FieldMappingDTO()
                : filter.getFieldMapping();
        String deptField = safeField(mapping.getDeptField(), "dept_id");
        String userField = safeField(mapping.getUserField(), "create_by");
        String statusField = safeField(mapping.getStatusField(), "status");
        if (deptField == null || userField == null || statusField == null) {
            return "1=0";
        }

        String type = normalized(filter.getType(), "PERSONAL");
        boolean mappedStatus = filter.getStatusLimit() != null && Boolean.TRUE.equals(filter.getStatusLimit().getEnabled());
        Map<String, RuleFieldColumn> fields = Set.of("PERSONAL", "DEPT", "DEPT_TREE", "RULE").contains(type) || mappedStatus
                ? resolveFieldColumns(entityCode) : Map.of();
        List<String> validityGuards = new ArrayList<>();
        String baseSql = switch (type) {
            case "ALL" -> "1=1";
            case "PERSONAL" -> matchesMappedUserSql(requireMappedField(fields, userField), user, parameters, validityGuards);
            case "SUBMITTER" -> matchesUserSql("submitter_id", user, parameters);
            case "CURRENT_ASSIGNEE" -> matchesUserSql("current_task_assignee", user, parameters);
            case "HAS_TODO" -> currentProcessTaskSql(entityCode, user, parameters);
            case "TEAM" -> buildTeamSql(entityCode, user, parameters);
            case "SQL" -> buildConfiguredSql(entityCode, filter, user, parameters);
            case "DEPT" -> StringUtils.hasText(user.getDeptId())
                    ? buildFieldComparison(requireMappedField(fields, deptField), "EQ", user.getDeptId(), parameters, validityGuards) : "1=0";
            case "DEPT_TREE" -> buildDeptTreeSql(requireMappedField(fields, deptField), user.getDeptId(), parameters);
            case "RULE" -> buildRuleSql(
                    entityCode,
                    filter.getRoot(),
                    user,
                    fields,
                    1,
                    new int[]{0}, parameters, validityGuards);
            case "EXPRESSION", "CUSTOM_SQL" -> "1=0";
            default -> matchesUserSql(userField, user, parameters);
        };
        if (!StringUtils.hasText(baseSql)) {
            baseSql = "1=0";
        }

        String statusSql = mappedStatus
                ? buildStatusSql(filter.getStatusLimit(), requireMappedField(fields, statusField), parameters, validityGuards) : null;
        String predicate = statusSql == null ? baseSql : "(" + baseSql + ") AND (" + statusSql + ")";
        if (validityGuards.isEmpty()) return predicate;
        // 失效字典值使整条规则 UNKNOWN；不能只保护叶子，否则 UNKNOWN AND FALSE 会变 FALSE，外层 DENY 取反可能放行。
        // 内层同时保留原规则的 UNKNOWN，避免 CASE 的 ELSE 0 把含 NULL 的标量拒绝条件改成不命中。
        return "(CASE WHEN " + String.join(" AND ", validityGuards)
                + " THEN CASE WHEN (" + predicate + ") THEN 1 WHEN NOT (" + predicate + ") THEN 0 ELSE NULL END"
                + " ELSE NULL END = 1)";
    }

    /**
     * 校验数据过滤配置的合法性，拒绝表达式或自定义 SQL 等不安全配置。
     *
     * @param entityCode 实体编码
     * @param filter     数据过滤配置
     * @throws IllegalArgumentException 配置为空、类型非法、字段名非法或规则结构错误时抛出
     */
    public void validateFilter(String entityCode, FilterConfigDTO filter) {
        if (filter == null) {
            throw new IllegalArgumentException("数据过滤配置不能为空");
        }
        String type = normalized(filter.getType(), "PERSONAL");
        if ("EXPRESSION".equals(type) || "CUSTOM_SQL".equals(type)
                || StringUtils.hasText(filter.getExpression())
                || (StringUtils.hasText(filter.getCustomSql()) && !"SQL".equals(type))) {
            throw new IllegalArgumentException("数据权限不再支持表达式或未校验的自定义 SQL，请改用结构化条件或受控 SQL");
        }
        if (!FILTER_TYPES.contains(type)) {
            throw new IllegalArgumentException("不支持的数据范围类型: " + type);
        }
        FilterConfigDTO.FieldMappingDTO mapping = filter.getFieldMapping();
        if (mapping != null) {
            requireSafeField(mapping.getUserField(), "用户字段");
            requireSafeField(mapping.getDeptField(), "部门字段");
            requireSafeField(mapping.getStatusField(), "状态字段");
        }
        Map<String, RuleFieldColumn> fields = null;
        if (Set.of("PERSONAL", "DEPT", "DEPT_TREE", "RULE").contains(type)
                || filter.getStatusLimit() != null && Boolean.TRUE.equals(filter.getStatusLimit().getEnabled())) {
            fields = resolveFieldColumns(entityCode);
        }
        if ("PERSONAL".equals(type)) {
            requireMappedField(fields, safeField(mapping == null ? null : mapping.getUserField(), "create_by"));
        } else if (Set.of("DEPT", "DEPT_TREE").contains(type)) {
            RuleFieldColumn department = requireMappedField(fields, safeField(mapping == null ? null : mapping.getDeptField(), "dept_id"));
            if ("DEPT_TREE".equals(type) && department.multiValue() != null && department.multiValue().dictCode() != null) {
                throw new IllegalArgumentException("部门树字段必须存储部门 ID，不能使用代码表多选字段");
            }
        }
        FilterConfigDTO.StatusLimitDTO statusLimit = filter.getStatusLimit();
        if (statusLimit != null && Boolean.TRUE.equals(statusLimit.getEnabled())) {
            requireMappedField(fields, safeField(mapping == null ? null : mapping.getStatusField(), "status"));
        }
        if (statusLimit != null && Boolean.TRUE.equals(statusLimit.getEnabled())
                && !Set.of("IN", "NOT_IN").contains(normalized(statusLimit.getMode(), "IN"))) {
            throw new IllegalArgumentException("状态限制仅支持 IN 或 NOT_IN");
        }
        if ("SQL".equals(type)) {
            if (sqlFragmentCompiler == null) {
                throw new IllegalArgumentException("未配置 SQL 条件编译器");
            }
            sqlFragmentCompiler.validate(firstSql(filter), true);
        }
        if ("RULE".equals(type)) {
            if (filter.getRoot() == null) {
                throw new IllegalArgumentException("结构化条件不能为空");
            }
            validateRuleNode(
                    entityCode,
                    filter.getRoot(),
                    fields,
                    1,
                    new int[]{0});
        }
    }

    /**
     * 构建规则SQL；结果供后续流程传递或持久化。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param node 节点，作为 {@code normalized} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param fieldColumns 字段列集合，作为 {@code buildGroupSql} 的输入影响后续处理
     * @param depth 深度，作为 {@code buildGroupSql} 的输入影响后续处理
     * @param count 数量，供本方法构建规则SQL时使用
     * @param parameters 参数集合，作为 {@code buildCustomSql} 的输入影响后续处理
     * @param validityGuards {@code validity}{@code guards}，供本方法构建规则SQL时使用
     * @return 构建后的规则SQL文本，供调用方比较或展示
     */
    private String buildRuleSql(
            String entityCode,
            EntityActionRuleDTO.RuleNode node,
            SysUser user,
            Map<String, RuleFieldColumn> fieldColumns,
            int depth,
            int[] count, Map<String, Object> parameters, List<String> validityGuards) {
        if (node == null || depth > MAX_RULE_DEPTH || ++count[0] > MAX_RULE_NODES) {
            return "1=0";
        }
        String type = normalized(node.getType(), "");
        return switch (type) {
            case "GROUP" -> buildGroupSql(
                    entityCode,
                    node,
                    user,
                    fieldColumns,
                    depth,
                    count, parameters, validityGuards);
            case "RELATION" -> buildRelationSql(entityCode, node.getRelation(), user, parameters);
            case "PROCESS_STATE" -> Integer.valueOf(1).equals(node.getLifecycleVersion())
                    ? buildComparisonSql("process_status", node.getOperator(), node.getValue(), parameters)
                    : buildProcessStateComparison(
                    entityCode,
                    node.getOperator(),
                    node.getValue(), parameters);
            case "STATUS_CODE" -> buildComparisonSql(
                    "status",
                    node.getOperator(),
                    node.getValue(), parameters);
            case "STATUS_CATEGORY" -> buildStatusCategorySql(
                    entityCode,
                    node.getOperator(),
                    node.getValue(), parameters);
            case "FIELD" -> {
                RuleFieldColumn column = resolveFieldColumn(fieldColumns, node.getField());
                yield column == null
                        ? "1=0"
                        : buildFieldComparison(column, node.getOperator(), node.getValue(), parameters, validityGuards);
            }
            case "USER_FIELD" -> evaluateUserField(node, user) ? "1=1" : "1=0";
            default -> buildCustomSql(entityCode, node, user, parameters);
        };
    }

    /**
     * 构建分组SQL；结果供后续流程传递或持久化。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param node 节点，作为 {@code equalsIgnoreCase} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param fieldColumns 字段列集合，供本方法构建分组SQL时使用
     * @param depth 深度，供本方法构建分组SQL时使用
     * @param count 数量，供本方法构建分组SQL时使用
     * @param parameters 参数集合，供本方法构建分组SQL时使用
     * @param validityGuards {@code validity}{@code guards}，供本方法构建分组SQL时使用
     * @return 构建后的分组SQL文本，供调用方比较或展示
     */
    private String buildGroupSql(
            String entityCode,
            EntityActionRuleDTO.RuleNode node,
            SysUser user,
            Map<String, RuleFieldColumn> fieldColumns,
            int depth,
            int[] count, Map<String, Object> parameters, List<String> validityGuards) {
        List<EntityActionRuleDTO.RuleNode> children = node.getChildren();
        if (children == null || children.isEmpty()) {
            return "1=0";
        }
        String joiner = "OR".equalsIgnoreCase(node.getLogic()) ? " OR " : " AND ";
        List<String> parts = children.stream()
                .map(child -> buildRuleSql(
                        entityCode,
                        child,
                        user,
                        fieldColumns,
                        depth + 1,
                        count, parameters, validityGuards))
                .filter(StringUtils::hasText)
                .map(part -> "(" + part + ")")
                .toList();
        return parts.isEmpty() ? "1=0" : String.join(joiner, parts);
    }

    /**
     * 构建关系SQL；结果供后续流程传递或持久化。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param relation 关系，供本方法构建关系SQL时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param parameters 参数集合，作为 {@code matchesUserSql} 的输入影响后续处理
     * @return 构建后的关系SQL文本，供调用方比较或展示
     */
    private String buildRelationSql(String entityCode, String relation, SysUser user, Map<String, Object> parameters) {
        if (!StringUtils.hasText(relation)) {
            return "1=0";
        }
        return switch (relation.toUpperCase(Locale.ROOT)) {
            case "CURRENT_USER_IS_CREATOR" -> matchesUserSql("create_by", user, parameters);
            case "CURRENT_USER_IS_SUBMITTER" -> matchesUserSql("submitter_id", user, parameters);
            case "CURRENT_USER_IS_ASSIGNEE" -> matchesUserSql("current_task_assignee", user, parameters);
            case "CURRENT_USER_SAME_DEPT" -> equalsSql("dept_id", user.getDeptId(), parameters);
            default -> "1=0";
        };
    }

    /**
     * 构建流程状态比较；结果供后续流程传递或持久化。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param operator 操作人，作为 {@code normalized} 的输入影响后续处理
     * @param value 待构建流程状态比较的原始输入，结果供调用方继续使用
     * @param parameters 参数集合，供本方法构建流程状态比较时使用
     * @return 构建后的流程状态比较文本，供调用方比较或展示
     */
    private String buildProcessStateComparison(
            String entityCode,
            String operator,
            Object value, Map<String, Object> parameters) {
        List<Object> states = toValues(value);
        String op = normalized(operator, "EQ");
        if (states.isEmpty()) {
            return "NOT_IN".equals(op) || "NE".equals(op) ? "1=1" : "1=0";
        }
        List<String> stateSql = states.stream()
                .map(state -> processStateSql(entityCode, String.valueOf(state), parameters))
                .filter(StringUtils::hasText)
                .map(sql -> "(" + sql + ")")
                .toList();
        if (stateSql.isEmpty()) {
            return "NOT_IN".equals(op) || "NE".equals(op) ? "1=1" : "1=0";
        }
        String union = String.join(" OR ", stateSql);
        return switch (op) {
            case "NE", "NOT_IN" -> "NOT (" + union + ")";
            default -> union;
        };
    }

    /**
     * 流程实例号为字符列；空串先归为 NULL，使状态判断不依赖厂商对空串的存储方式。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param state 状态标识，决定后续状态SQL采用的处理分支
     * @param parameters 参数集合，作为 {@code buildStatusCategorySql} 的输入影响后续处理
     * @return 处理后的状态SQL文本，供调用方比较或展示
     */
    private String processStateSql(String entityCode, String state, Map<String, Object> parameters) {
        return switch (normalized(state, "")) {
            case "NOT_STARTED" ->
                    "(NULLIF(process_instance_id, '') IS NULL)";
            case "RUNNING" ->
                    "(NULLIF(process_instance_id, '') IS NOT NULL AND process_end_time IS NULL)";
            case "WITHDRAWN" ->
                    buildStatusCategorySql(entityCode, "EQ", "WITHDRAWN", parameters);
            case "TERMINATED" ->
                    buildStatusCategorySql(entityCode, "EQ", "TERMINATED", parameters);
            case "COMPLETED" -> {
                String excluded = buildStatusCategorySql(
                        entityCode,
                        "IN",
                        List.of("WITHDRAWN", "TERMINATED"), parameters);
                yield "(NULLIF(process_instance_id, '') IS NOT NULL "
                        + "AND process_end_time IS NOT NULL AND NOT (" + excluded + "))";
            }
            default -> "1=0";
        };
    }

    /**
     * 构建状态类别SQL；结果供后续流程传递或持久化。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param operator 操作人，作为 {@code normalized} 的输入影响后续处理
     * @param value 待构建状态类别SQL的原始输入，结果供调用方继续使用
     * @param parameters 参数集合，供本方法构建状态类别SQL时使用
     * @return 构建后的状态类别SQL文本，供调用方比较或展示
     */
    private String buildStatusCategorySql(
            String entityCode,
            String operator,
            Object value, Map<String, Object> parameters) {
        List<Object> categories = toValues(value);
        LinkedHashSet<String> statusCodes = new LinkedHashSet<>();
        if (statusMapper != null && StringUtils.hasText(entityCode)) {
            for (Object category : categories) {
                List<EntityStatus> statuses = statusMapper.findByCategory(
                        entityCode,
                        String.valueOf(category));
                if (statuses != null) {
                    statuses.stream()
                            .map(EntityStatus::getStatusCode)
                            .filter(StringUtils::hasText)
                            .forEach(statusCodes::add);
                }
            }
        }
        String normalizedOperator = normalized(operator, "EQ");
        String setOperator = switch (normalizedOperator) {
            case "NE", "NOT_IN" -> "NOT_IN";
            default -> "IN";
        };
        return buildComparisonSql(
                "status",
                setOperator,
                new ArrayList<>(statusCodes), parameters);
    }

    /**
     * 构建比较SQL；结果供后续流程传递或持久化。
     *
     * @param column 列，供本方法构建比较SQL时使用
     * @param operator 操作人，供本方法构建比较SQL时使用
     * @param value 待构建比较SQL的原始输入，结果供调用方继续使用
     * @param parameters 参数集合，供本方法构建比较SQL时使用
     * @return 构建后的比较SQL文本，供调用方比较或展示
     */
    private String buildComparisonSql(String column, String operator, Object value, Map<String, Object> parameters) {
        return buildComparisonSql(column, operator, value, SchemaType.string(4096), parameters);
    }

    /**
     * 标量走物理列比较，多值走侧表；组件或不完整存储描述必须抛错，不能让 DENY 被误当作未命中。
     *
     * @param field 字段，作为 {@code PermissionMultiValueSql.compare} 的输入影响后续处理
     * @param operator 操作人，供本方法构建字段比较时使用
     * @param value 待构建字段比较的原始输入，结果供调用方继续使用
     * @param parameters 参数集合，供本方法构建字段比较时使用
     * @param validityGuards {@code validity}{@code guards}，供本方法构建字段比较时使用
     * @return 构建后的字段比较文本，供调用方比较或展示
     */
    private String buildFieldComparison(RuleFieldColumn field, String operator, Object value, Map<String, Object> parameters,
                                       List<String> validityGuards) {
        field.requireSupported();
        if (field.multiValue() != null) {
            return PermissionMultiValueSql.compare(field.multiValue(), normalized(operator, "EQ"), toValues(value), queryDialect, parameters, validityGuards);
        }
        return buildComparisonSql(field.column(), operator, value, field.type(), parameters);
    }

    /**
     * 字段名按目标库引用；比较值按存储类型绑定，避免依赖 MySQL 的隐式转换与字面量转义。
     *
     * @param column 列，作为 {@code queryDialect.emptyValuePredicate} 的输入影响后续处理
     * @param operator 操作人，作为 {@code normalized} 的输入影响后续处理
     * @param value 待构建比较SQL的原始输入，结果供调用方继续使用
     * @param type 类型标识，决定后续比较SQL采用的处理分支
     * @param parameters 参数集合，作为 {@code equalitySql} 的输入影响后续处理
     * @return 构建后的比较SQL文本，供调用方比较或展示
     */
    private String buildComparisonSql(String column, String operator, Object value, SchemaType type,
                                      Map<String, Object> parameters) {
        String op = normalized(operator, "EQ");
        if (!FIELD_OPERATORS.contains(op) || type == null) return "1=0";
        SchemaType.Kind kind = type.kind();
        return switch (op) {
            case "EMPTY" -> queryDialect.emptyValuePredicate(column, kind, true);
            case "NOT_EMPTY" -> queryDialect.emptyValuePredicate(column, kind, false);
            case "EQ" -> equalitySql(column, value, false, kind, parameters);
            case "NE" -> equalitySql(column, value, true, kind, parameters);
            case "IN" -> inSql(column, toValues(value), false, kind, parameters);
            case "NOT_IN" -> inSql(column, toValues(value), true, kind, parameters);
            case "CONTAINS" -> likeSql(queryDialect.patternValueExpression(column, type), value, false, parameters);
            case "NOT_CONTAINS" -> likeSql(queryDialect.patternValueExpression(column, type), value, true, parameters);
            case "GT" -> orderedSql(column, ">", value, kind, parameters);
            case "GTE" -> orderedSql(column, ">=", value, kind, parameters);
            case "LT" -> orderedSql(column, "<", value, kind, parameters);
            case "LTE" -> orderedSql(column, "<=", value, kind, parameters);
            default -> "1=0";
        };
    }

    /**
     * 生成{@code equality}SQL文本，供后续匹配或展示。
     *
     * @param column 列，作为 {@code queryDialect.comparisonPredicate} 的输入影响后续处理
     * @param value 待处理{@code equality}SQL的原始输入，结果供调用方继续使用
     * @param negate {@code negate}，作为 {@code queryDialect.comparisonPredicate} 的输入影响后续处理
     * @param kind 类型，作为 {@code queryDialect.comparisonPredicate} 的输入影响后续处理
     * @param parameters 参数集合，作为 {@code queryDialect.comparisonPredicate} 的输入影响后续处理
     * @return 处理后的{@code equality}SQL文本，供调用方比较或展示
     */
    private String equalitySql(String column, Object value, boolean negate, SchemaType.Kind kind,
                               Map<String, Object> parameters) {
        if (value == null) return queryDialect.quoteIdentifier(column) + (negate ? " IS NOT NULL" : " IS NULL");
        return queryDialect.comparisonPredicate(column, kind, negate ? "<>" : "=",
                PermissionSqlParameters.bindScalar(parameters, value, kind, queryDialect));
    }

    /**
     * 生成SQL文本，供后续匹配或展示。
     *
     * @param column 列，作为 {@code queryDialect.quoteIdentifier} 的输入影响后续处理
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param negate {@code negate}，作为 {@code collect} 的输入影响后续处理
     * @param kind 类型，供本方法处理SQL时使用
     * @param parameters 参数集合，供本方法处理SQL时使用
     * @return 处理后的SQL文本，供调用方比较或展示
     */
    private String inSql(String column, List<Object> values, boolean negate, SchemaType.Kind kind,
                         Map<String, Object> parameters) {
        if (values.isEmpty()) return negate ? "1=1" : "1=0";
        if ("CLOB".equals(queryDialect.comparisonJdbcType(kind))) {
            // CLOB 不能直接 IN；逐项全文比较以 OR/AND 组合，NULL 参数保持三值逻辑，尤其不能从 NOT IN 中删掉。
            return values.stream().map(value -> queryDialect.comparisonPredicate(column, kind, negate ? "<>" : "=",
                            PermissionSqlParameters.bindScalar(parameters, value, kind, queryDialect)))
                    .collect(java.util.stream.Collectors.joining(negate ? " AND " : " OR ", "(", ")"));
        }
        // NULL 元素保留 SQL 三值逻辑，不能过滤后扩大 NOT IN 或拒绝条件的范围。
        String joined = values.stream().map(value -> PermissionSqlParameters.bindScalar(parameters, value, kind, queryDialect))
                .collect(java.util.stream.Collectors.joining(","));
        return queryDialect.quoteIdentifier(column) + (negate ? " NOT IN (" : " IN (") + joined + ")";
    }

    /**
     * 生成{@code like}SQL文本，供后续匹配或展示。
     *
     * @param quoted {@code quoted}，供本方法处理{@code like}SQL时使用
     * @param value 待处理{@code like}SQL的原始输入，结果供调用方继续使用
     * @param negate {@code negate}，供本方法处理{@code like}SQL时使用
     * @param parameters 参数集合，作为 {@code PermissionSqlParameters.bindText} 的输入影响后续处理
     * @return 处理后的{@code like}SQL文本，供调用方比较或展示
     */
    private String likeSql(String quoted, Object value, boolean negate, Map<String, Object> parameters) {
        if (value == null) return negate ? "1=1" : "1=0";
        return quoted + (negate ? " NOT LIKE " : " LIKE ")
                + PermissionSqlParameters.bindText(parameters, "%" + escapeLike(String.valueOf(value)) + "%")
                + " ESCAPE '!'";
    }

    /**
     * 使用固定的普通字符作 LIKE 转义符，绑定模式不随 MySQL NO_BACKSLASH_ESCAPES 改变含义。
     *
     * @param value 待处理{@code escape}{@code like}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code escape}{@code like}文本，供调用方比较或展示
     */
    private String escapeLike(String value) {
        return value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    /**
     * 生成{@code ordered}SQL文本，供后续匹配或展示。
     *
     * @param column 列，作为 {@code queryDialect.comparisonPredicate} 的输入影响后续处理
     * @param operator 操作人，作为 {@code queryDialect.comparisonPredicate} 的输入影响后续处理
     * @param value 待处理{@code ordered}SQL的原始输入，结果供调用方继续使用
     * @param kind 类型，作为 {@code queryDialect.comparisonPredicate} 的输入影响后续处理
     * @param parameters 参数集合，作为 {@code queryDialect.comparisonPredicate} 的输入影响后续处理
     * @return 处理后的{@code ordered}SQL文本，供调用方比较或展示
     */
    private String orderedSql(String column, String operator, Object value, SchemaType.Kind kind,
                              Map<String, Object> parameters) {
        return value == null ? "1=0" : queryDialect.comparisonPredicate(column, kind, operator,
                PermissionSqlParameters.bindScalar(parameters, value, kind, queryDialect));
    }

    /**
     * 求值用户字段，并将结果传给后续步骤。
     *
     * @param node 节点，作为 {@code compare} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 用户字段条件成立时为 true，否则为 false
     */
    private boolean evaluateUserField(EntityActionRuleDTO.RuleNode node, SysUser user) {
        if (user == null || !StringUtils.hasText(node.getField())) {
            return false;
        }
        Object actual = switch (node.getField()) {
            case "id" -> user.getId();
            case "username" -> user.getUsername();
            case "deptId" -> user.getDeptId();
            case "orgId" -> user.getOrgId();
            case "roleIds" -> user.getRoleIds();
            default -> null;
        };
        // 遗留 SQL 配置允许逗号分隔的集合输入；先按原协议归一化，再共用比较语义。
        String operator = normalized(node.getOperator(), "EQ");
        Object expected = node.getValue() != null && Set.of("IN", "NOT_IN").contains(operator)
                ? toValues(node.getValue()) : node.getValue();
        return PermissionConditionComparison.compare(actual, operator, expected);
    }

    /**
     * 构建自定义SQL；结果供后续流程传递或持久化。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param node 节点，作为 {@code toSql} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param parameters 参数集合，作为 {@code toSql} 的输入影响后续处理
     * @return 构建后的自定义SQL文本，供调用方比较或展示
     */
    private String buildCustomSql(
            String entityCode,
            EntityActionRuleDTO.RuleNode node,
            SysUser user, Map<String, Object> parameters) {
        return filterProviders.stream()
                .filter(provider -> provider.getType().equalsIgnoreCase(node.getType()))
                .findFirst()
                .map(provider -> provider.toSql(entityCode, node, user, parameters))
                .filter(StringUtils::hasText)
                .orElse("1=0");
    }

    /**
     * 校验规则节点；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param node 节点，作为 {@code normalized} 的输入影响后续处理
     * @param fieldColumns 字段列集合，作为 {@code resolveFieldColumn} 的输入影响后续处理
     * @param depth 深度，供本方法校验规则节点时使用
     * @param count 数量，供本方法校验规则节点时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateRuleNode(
            String entityCode,
            EntityActionRuleDTO.RuleNode node,
            Map<String, RuleFieldColumn> fieldColumns,
            int depth,
            int[] count) {
        if (node == null) {
            throw new IllegalArgumentException("条件节点不能为空");
        }
        if (depth > MAX_RULE_DEPTH || ++count[0] > MAX_RULE_NODES) {
            throw new IllegalArgumentException("数据权限条件过于复杂");
        }
        String type = normalized(node.getType(), "");
        switch (type) {
            case "GROUP" -> {
                if (!Set.of("AND", "OR").contains(normalized(node.getLogic(), ""))) {
                    throw new IllegalArgumentException("条件组逻辑只能是 AND 或 OR");
                }
                if (node.getChildren() == null || node.getChildren().isEmpty()) {
                    throw new IllegalArgumentException("条件组不能为空");
                }
                for (EntityActionRuleDTO.RuleNode child : node.getChildren()) {
                    validateRuleNode(entityCode, child, fieldColumns, depth + 1, count);
                }
            }
            case "RELATION" -> {
                if (!RELATIONS.contains(normalized(node.getRelation(), ""))) {
                    throw new IllegalArgumentException("不支持的当前用户关系: " + node.getRelation());
                }
            }
            case "PROCESS_STATE" -> {
                requireOperator(node.getOperator(), SIMPLE_OPERATORS);
                requireValues(node.getOperator(), node.getValue(), "流程状态");
                if (node.getLifecycleVersion() != null && node.getLifecycleVersion() != 1) {
                    throw new IllegalArgumentException("不支持的流程状态规则版本");
                }
                requireAllowedValues(node.getValue(), Integer.valueOf(1).equals(node.getLifecycleVersion())
                        ? Set.of("NOT_STARTED", "RUNNING", "COMPLETED") : PROCESS_STATES, "流程状态");
            }
            case "STATUS_CODE" -> {
                requireOperator(node.getOperator(), SIMPLE_OPERATORS);
                requireValues(node.getOperator(), node.getValue(), "状态编码");
                requireExistingStatusCodes(entityCode, node.getValue());
            }
            case "STATUS_CATEGORY" -> {
                requireOperator(node.getOperator(), SIMPLE_OPERATORS);
                requireValues(node.getOperator(), node.getValue(), "状态分类");
                requireAllowedValues(node.getValue(), STATUS_CATEGORIES, "状态分类");
            }
            case "FIELD" -> {
                RuleFieldColumn column = resolveFieldColumn(fieldColumns, node.getField());
                if (column == null) {
                    throw new IllegalArgumentException("字段不存在或不可用于数据权限: " + node.getField());
                }
                column.requireSupported();
                if (column.kind() == null) {
                    throw new IllegalArgumentException("字段缺少存储类型: " + node.getField());
                }
                requireOperator(node.getOperator(), FIELD_OPERATORS);
                requireValues(node.getOperator(), node.getValue(), "字段条件");
                if (column.multiValue() != null) {
                    PermissionMultiValueSql.validate(normalized(node.getOperator(), "EQ"), toValues(node.getValue()));
                }
                // 文本包含与判空没有数值转换，其余操作必须在配置发布前确认比较值合法。
                if (!Set.of("EMPTY", "NOT_EMPTY", "CONTAINS", "NOT_CONTAINS").contains(normalized(node.getOperator(), "EQ"))) {
                    for (Object value : toValues(node.getValue())) PermissionSqlParameters.scalarValue(value, column.kind());
                }
            }
            case "USER_FIELD" -> {
                if (!Set.of("id", "username", "deptId", "orgId", "roleIds")
                        .contains(node.getField())) {
                    throw new IllegalArgumentException("不支持的当前用户属性: " + node.getField());
                }
                requireOperator(node.getOperator(), FIELD_OPERATORS);
                requireValues(node.getOperator(), node.getValue(), "当前用户属性条件");
            }
            default -> filterProviders.stream()
                    .filter(provider -> provider.getType().equalsIgnoreCase(node.getType()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("不支持的数据权限条件类型: " + node.getType()))
                    .validate(entityCode, node);
        }
    }

    /**
     * 同时解析字段白名单与存储类型，避免 SQL 判空依赖数据库的隐式文本转换。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 字段列集合键值结果，供调用方继续处理
     */
    private Map<String, RuleFieldColumn> resolveFieldColumns(String entityCode) {
        Map<String, RuleFieldColumn> columns = new LinkedHashMap<>();
        Set<String> timestamps = Set.of("process_start_time", "process_end_time", "submit_time", "create_time", "update_time");
        SYSTEM_FIELD_COLUMNS.forEach((code, column) -> columns.put(code, new RuleFieldColumn(column,
                timestamps.contains(column) ? SchemaType.of(SchemaType.Kind.TIMESTAMP) : SchemaType.string(4096))));
        if (!StringUtils.hasText(entityCode)
                || definitionMapper == null || fieldMapper == null) {
            return columns;
        }
        EntityDefinition definition = definitionMapper.findByEntityCode(entityCode).orElse(null);
        if (definition == null) {
            return columns;
        }
        List<EntityField> fields = fieldMapper.findByEntityId(definition.getId());
        if (fields == null) {
            return columns;
        }
        for (EntityField field : fields) {
            if (!StringUtils.hasText(field.getFieldCode())) {
                continue;
            }
            String column = StringUtils.hasText(field.getDbColumnName())
                    ? field.getDbColumnName()
                    : field.getFieldCode();
            if (SQL_IDENTIFIER.matcher(column).matches()) {
                // 缺少类型的历史元数据不能猜成文本；编译规则时关闭访问，配置校验时明确报错。
                SchemaType type = field.getFieldType() == null ? null : EntityTableDefinitionFactory.fieldType(field);
                RuleFieldColumn resolved = describeField(entityCode, definition, field, column, type);
                // 系统字段的基线列名/类型不可被同名自定义元数据覆盖；长度展示差异不改变其存储身份。
                RuleFieldColumn system = columns.get(field.getFieldCode());
                if (SYSTEM_FIELD_COLUMNS.containsKey(field.getFieldCode()) && system != null) {
                    if (!system.column().equals(column) || system.kind() != resolved.kind() || resolved.multiValue() != null || resolved.failure() != null) {
                        throw new IllegalArgumentException("权限字段元数据与系统列冲突: " + field.getFieldCode());
                    }
                    resolved = system;
                }
                putFieldAlias(columns, field.getFieldCode(), resolved);
                putFieldAlias(columns, column, resolved);
            }
        }
        return columns;
    }

    /**
     * 解析字段列；输出作为后续校验或处理的输入。
     *
     * @param columns 列集合，供本方法解析字段列时使用
     * @param field 字段，作为 {@code columns.get} 的输入影响后续处理
     * @return 解析后的字段列结果，供调用方继续处理
     */
    private RuleFieldColumn resolveFieldColumn(Map<String, RuleFieldColumn> columns, String field) {
        if (!StringUtils.hasText(field)) {
            return null;
        }
        RuleFieldColumn column = columns.get(field);
        if (column == null) {
            String inferredColumn = toColumnName(field);
            // 审计字段只接受正式编码，不能借通用驼峰推算重新引入别名。
            if (Set.of("create_time", "update_time", "create_by", "update_by").contains(inferredColumn)) {
                return null;
            }
            column = columns.get(inferredColumn);
        }
        return column != null && SQL_IDENTIFIER.matcher(column.column()).matches() ? column : null;
    }

    /**
     * 复用建表的存储分类；多值侧表只允许使用登记主表派生名称，不能根据实体编码猜测。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param definition 定义，供本方法处理{@code describe}字段时使用
     * @param field 字段，作为 {@code RuleFieldColumn} 的输入影响后续处理
     * @param column 列，作为 {@code RuleFieldColumn} 的输入影响后续处理
     * @param type 类型标识，决定后续{@code describe}字段采用的处理分支
     * @return 处理后的{@code describe}字段结果，供调用方继续处理
     */
    private RuleFieldColumn describeField(String entityCode, EntityDefinition definition, EntityField field, String column, SchemaType type) {
        if (EntityTableDefinitionFactory.isSubFormField(field)) {
            return new RuleFieldColumn(column, type, null, "页面组件没有可查询的数据存储: " + field.getFieldCode());
        }
        if (field.getFieldType() == EntityField.FieldType.MULTI_REFERENCE || EntityTableDefinitionFactory.isMultiValueField(field)) {
            String table = tableResolver == null ? definition.getPhysicalTableName() : resolvePhysicalTable(entityCode);
            String target = field.getRefEntityId();
            String dict = StringUtils.hasText(field.getDictType()) ? field.getDictType() : null;
            if (dict != null) {
                target = definitionMapper.findByEntityCode("sys_dict_item").map(EntityDefinition::getId).orElse(null);
            }
            if (!StringUtils.hasText(table) || !SQL_IDENTIFIER.matcher(table).matches() || table.length() + 6 > MAX_MULTI_TABLE_NAME_LENGTH
                    || !StringUtils.hasText(target)) {
                return new RuleFieldColumn(column, type, null, "多值权限字段缺少可信物理表或目标实体: " + field.getFieldCode());
            }
            return new RuleFieldColumn(column, SchemaType.string(64),
                    new PermissionMultiValueSql.Field(table, field.getFieldCode(), target, dict), null);
        }
        if ("MULTI_TABLE".equalsIgnoreCase(field.getValueStorage())
                || Boolean.TRUE.equals(field.getIsSystem()) && !SYSTEM_FIELD_COLUMNS.containsKey(field.getFieldCode())) {
            return new RuleFieldColumn(column, type, null, "权限字段没有受支持的存储描述: " + field.getFieldCode());
        }
        return new RuleFieldColumn(column, type);
    }

    /**
     * 写入字段{@code alias}；后续读取或执行将使用更新后的状态。
     *
     * @param columns 列集合，供本方法写入字段{@code alias}时使用
     * @param alias {@code alias}，作为 {@code columns.putIfAbsent} 的输入影响后续处理
     * @param field 字段，作为 {@code columns.putIfAbsent} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void putFieldAlias(Map<String, RuleFieldColumn> columns, String alias, RuleFieldColumn field) {
        RuleFieldColumn previous = columns.putIfAbsent(alias, field);
        if (previous != null && !previous.equals(field)) {
            throw new IllegalArgumentException("权限字段编码与物理列别名冲突: " + alias);
        }
    }

    /**
     * 校验并获取{@code mapped}字段；不满足约束时阻止后续处理。
     *
     * @param fields 字段集合，后续逐项校验、转换或持久化
     * @param alias {@code alias}，作为 {@code resolveFieldColumn} 的输入影响后续处理
     * @return 校验并获取后的{@code mapped}字段结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private RuleFieldColumn requireMappedField(Map<String, RuleFieldColumn> fields, String alias) {
        RuleFieldColumn field = resolveFieldColumn(fields, alias);
        if (field == null || field.kind() == null) throw new IllegalArgumentException("权限映射字段不存在或缺少存储类型: " + alias);
        field.requireSupported();
        return field;
    }

    /**
     * 保留精度和存储位置；只保留字段类型会把独立关系表错误降为主表字符串列。
     *
     * @param column 列，保存在对象中供后续校验、查询或展示
     * @param type 类型标识，决定后续规则字段列采用的处理分支
     * @param multiValue 多实例值，保存在对象中供后续校验、查询或展示
     * @param failure 失败，保存在对象中供后续校验、查询或展示
     */
    private record RuleFieldColumn(String column, SchemaType type, PermissionMultiValueSql.Field multiValue, String failure) {
        /**
         * 初始化规则字段列，保存构造参数供后续方法使用。
         *
         * @param column 列，保存在对象中供后续校验、查询或展示
         * @param type 类型标识，决定后续规则字段列采用的处理分支
         */
        RuleFieldColumn(String column, SchemaType type) { this(column, type, null, null); }
        /**
         * 处理类型，并将结果传给后续步骤。
         *
         * @return 处理后的类型结果，供调用方继续处理
         */
        SchemaType.Kind kind() { return type == null ? null : type.kind(); }
        /**
         * 校验并获取{@code supported}；不满足约束时阻止后续处理。
         *
         * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
         */
        void requireSupported() { if (failure != null) throw new IllegalArgumentException(failure); }
    }

    /**
     * 判断是否匹配{@code mapped}用户SQL；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否匹配{@code mapped}用户SQL时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param parameters 参数集合，供本方法判断是否匹配{@code mapped}用户SQL时使用
     * @param validityGuards {@code validity}{@code guards}，供本方法判断是否匹配{@code mapped}用户SQL时使用
     * @return 判断是否匹配后的{@code mapped}用户SQL文本，供调用方比较或展示
     */
    private String matchesMappedUserSql(RuleFieldColumn field, SysUser user, Map<String, Object> parameters, List<String> validityGuards) {
        List<Object> identities = new ArrayList<>(userIdentities(user));
        return identities.isEmpty() ? "1=0" : buildFieldComparison(field, "IN", identities, parameters, validityGuards);
    }

    /**
     * 构建部门树SQL；结果供后续流程传递或持久化。
     *
     * @param deptField 部门字段，作为 {@code PermissionMultiValueSql.departments} 的输入影响后续处理
     * @param deptId 部门ID，后续用于构建部门树SQL时定位或关联目标
     * @param parameters 参数集合，供本方法构建部门树SQL时使用
     * @return 构建后的部门树SQL文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String buildDeptTreeSql(RuleFieldColumn deptField, String deptId, Map<String, Object> parameters) {
        if (!StringUtils.hasText(deptId)) return "1=0";
        if (deptField.multiValue() != null) {
            if (deptField.multiValue().dictCode() != null) throw new IllegalArgumentException("部门树字段不能使用代码表多选存储");
            return PermissionMultiValueSql.departments(deptField.multiValue(), deptId, queryDialect, parameters);
        }
        return queryDialect.quoteIdentifier(deptField.column()) + " IN ("
                + PermissionMultiValueSql.departmentIds(deptId, queryDialect, parameters) + ")";
    }

    /**
     * 构建状态SQL；结果供后续流程传递或持久化。
     *
     * @param statusLimit 状态上限，作为 {@code buildFieldComparison} 的输入影响后续处理
     * @param statusField 状态字段，作为 {@code buildFieldComparison} 的输入影响后续处理
     * @param parameters 参数集合，供本方法构建状态SQL时使用
     * @param validityGuards {@code validity}{@code guards}，供本方法构建状态SQL时使用
     * @return 构建后的状态SQL文本，供调用方比较或展示
     */
    private String buildStatusSql(
            FilterConfigDTO.StatusLimitDTO statusLimit,
            RuleFieldColumn statusField, Map<String, Object> parameters, List<String> validityGuards) {
        if (statusLimit == null || !Boolean.TRUE.equals(statusLimit.getEnabled())) {
            return null;
        }
        List<String> values = statusLimit.getValues();
        if (values == null || values.isEmpty()) {
            return null;
        }
        return buildFieldComparison(
                statusField,
                "NOT_IN".equalsIgnoreCase(statusLimit.getMode()) ? "NOT_IN" : "IN",
                new ArrayList<>(values), parameters, validityGuards);
    }

    /**
     * 构建已配置SQL；结果供后续流程传递或持久化。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param filter 过滤，作为 {@code sqlFragmentCompiler.compileRecordSql} 的输入影响后续处理
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param parameters 参数集合，供本方法构建已配置SQL时使用
     * @return 构建后的已配置SQL文本，供调用方比较或展示
     */
    private String buildConfiguredSql(
            String entityCode,
            FilterConfigDTO filter,
            SysUser user, Map<String, Object> parameters) {
        if (sqlFragmentCompiler == null) {
            return "1=0";
        }
        return sqlFragmentCompiler.compileRecordSql(entityCode, firstSql(filter), user, parameters);
    }

    /**
     * 生成首个SQL文本，供后续匹配或展示。
     *
     * @param filter 过滤，供本方法处理首个SQL时使用
     * @return 处理后的首个SQL文本，供调用方比较或展示
     */
    private String firstSql(FilterConfigDTO filter) {
        if (filter == null) {
            return null;
        }
        return StringUtils.hasText(filter.getSql())
                ? filter.getSql()
                : filter.getCustomSql();
    }

    /**
     * 相关人只认 _team 已发生的参与事件，不含当前待办。
     * 待办可见性由独立的 HAS_TODO 规则绑定，列表自行选择。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param parameters 参数集合，供本方法构建团队SQL时使用
     * @return 构建后的团队SQL文本，供调用方比较或展示
     */
    private String buildTeamSql(String entityCode, SysUser user, Map<String, Object> parameters) {
        if (teamService == null || user == null) {
            return "1=0";
        }
        return teamService.relatedPeopleSql(
                entityCode, user.getId(), user.getUsername(), parameters);
    }

    /**
     * 当前用户存在真实可审批任务，包括未认领候选任务。
     * 通过流程契约查询当前实体记录 ID，限定外层业务表，不能把任务投影当作权限来源。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param parameters 参数集合，供本方法处理当前流程任务SQL时使用
     * @return 处理后的当前流程任务SQL文本，供调用方比较或展示
     */
    private String currentProcessTaskSql(
            String entityCode, SysUser user, Map<String, Object> parameters) {
        if (!StringUtils.hasText(entityCode) || user == null || taskAccessPort == null) {
            return "1=0";
        }
        String tableName = resolvePhysicalTable(entityCode);
        String userId = StringUtils.hasText(user.getId()) ? user.getId() : user.getUsername();
        if (tableName == null || !StringUtils.hasText(userId)) {
            return "1=0";
        }
        List<String> recordIds = taskAccessPort.findActionableEntityDataIds(userId, entityCode);
        String idList = recordIds.stream()
                .filter(StringUtils::hasText)
                .distinct()
                .map(id -> bindTaskRecordId(id, parameters))
                .collect(java.util.stream.Collectors.joining(","));
        return idList.isEmpty() ? "1=0"
                : queryDialect.quoteIdentifier(tableName) + ".id IN (" + idList + ")";
    }

    /**
     * 将记录 ID 交给 MyBatis/JDBC 绑定，避免手动转义和数据库专有字符转换。
     * 参数名在当前查询中唯一，防止多个权限规则或不同委托人的记录 ID 相互覆盖。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param parameters 参数集合，供本方法处理绑定任务记录ID时使用
     * @return 处理后的绑定任务记录ID文本，供调用方比较或展示
     */
    private String bindTaskRecordId(String id, Map<String, Object> parameters) {
        int index = parameters.size();
        String key = "todoRecordId" + index;
        while (parameters.containsKey(key)) {
            key = "todoRecordId" + ++index;
        }
        parameters.put(key, id);
        return "#{permissionParameters." + key + ",jdbcType=VARCHAR}";
    }

    /**
     * 解析物理表；输出作为后续校验或处理的输入。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 解析后的物理表文本，供调用方比较或展示
     */
    private String resolvePhysicalTable(String entityCode) {
        if (tableResolver == null || !StringUtils.hasText(entityCode)) {
            return null;
        }
        try {
            String tableName = tableResolver.resolve(entityCode);
            return SQL_IDENTIFIER.matcher(tableName).matches() ? tableName : null;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 处理用户{@code identities}，并将结果传给后续步骤。
     *
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @return 处理后的用户{@code identities}结果，供调用方继续处理
     */
    private LinkedHashSet<String> userIdentities(SysUser user) {
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        if (user == null) {
            return identities;
        }
        if (StringUtils.hasText(user.getId())) {
            identities.add(user.getId());
        }
        if (StringUtils.hasText(user.getUsername())) {
            identities.add(user.getUsername());
        }
        return identities;
    }

    /**
     * 判断是否匹配用户SQL；判断结果决定调用方的后续分支。
     *
     * @param field 字段，供本方法判断是否匹配用户SQL时使用
     * @param user 目标用户信息，后续用于权限计算或业务规则判断
     * @param parameters 参数集合，供本方法判断是否匹配用户SQL时使用
     * @return 判断是否匹配后的用户SQL文本，供调用方比较或展示
     */
    private String matchesUserSql(String field, SysUser user, Map<String, Object> parameters) {
        LinkedHashSet<String> identities = userIdentities(user);
        return identities.isEmpty()
                ? "1=0"
                : inSql(field, new ArrayList<>(identities), false, SchemaType.Kind.STRING, parameters);
    }

    /**
     * 生成相等SQL文本，供后续匹配或展示。
     *
     * @param field 字段，供本方法处理相等SQL时使用
     * @param value 待处理相等SQL的原始输入，结果供调用方继续使用
     * @param parameters 参数集合，供本方法处理相等SQL时使用
     * @return 处理后的相等SQL文本，供调用方比较或展示
     */
    private String equalsSql(String field, String value, Map<String, Object> parameters) {
        return StringUtils.hasText(value) ? queryDialect.quoteIdentifier(field) + " = "
                + PermissionSqlParameters.bindText(parameters, value) : "1=0";
    }

    /**
     * 生成安全字段文本，供后续匹配或展示。
     *
     * @param fieldName 字段名称，后续用于处理安全字段时匹配或展示
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的安全字段文本，供调用方比较或展示
     */
    private String safeField(String fieldName, String fallback) {
        String value = StringUtils.hasText(fieldName) ? fieldName : fallback;
        return SQL_IDENTIFIER.matcher(value).matches() ? value : null;
    }

    /**
     * 校验并获取安全字段；不满足约束时阻止后续处理。
     *
     * @param field 字段，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param label 标签，后续用于校验并获取安全字段时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireSafeField(String field, String label) {
        if (StringUtils.hasText(field) && !SQL_IDENTIFIER.matcher(field).matches()) {
            throw new IllegalArgumentException(label + "包含非法字段名: " + field);
        }
    }

    /**
     * 校验并获取操作人；不满足约束时阻止后续处理。
     *
     * @param operator 操作人，作为 {@code normalized} 的输入影响后续处理
     * @param allowed 允许，供本方法校验并获取操作人时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireOperator(String operator, Set<String> allowed) {
        String normalized = normalized(operator, "EQ");
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("不支持的比较操作符: " + operator);
        }
    }

    /**
     * 校验并获取值集合；不满足约束时阻止后续处理。
     *
     * @param operator 操作人，作为 {@code normalized} 的输入影响后续处理
     * @param value 待校验并获取值集合的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于校验并获取值集合时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireValues(String operator, Object value, String label) {
        String normalizedOperator = normalized(operator, "EQ");
        if (Set.of("EMPTY", "NOT_EMPTY").contains(normalizedOperator)) {
            return;
        }
        List<Object> values = toValues(value);
        if (values.isEmpty()
                || values.stream().allMatch(item ->
                item == null || (item instanceof String text && !StringUtils.hasText(text)))) {
            throw new IllegalArgumentException(label + "缺少比较值");
        }
        if (Set.of("IN", "NOT_IN").contains(normalizedOperator)
                && !(value instanceof Collection<?>)
                && !(value != null && value.getClass().isArray())
                && !(value instanceof String text && text.contains(","))) {
            throw new IllegalArgumentException(label + "使用 IN/NOT IN 时必须提供多个值");
        }
        if (!Set.of("IN", "NOT_IN").contains(normalizedOperator)
                && values.size() > 1) {
            throw new IllegalArgumentException(label + "当前操作符只允许一个比较值");
        }
    }

    /**
     * 校验并获取允许值集合；不满足约束时阻止后续处理。
     *
     * @param value 待校验并获取允许值集合的原始输入，结果供调用方继续使用
     * @param allowed 允许，供本方法校验并获取允许值集合时使用
     * @param label 标签，后续用于校验并获取允许值集合时匹配或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireAllowedValues(
            Object value,
            Set<String> allowed,
            String label) {
        List<String> invalid = toValues(value).stream()
                .map(String::valueOf)
                .map(item -> item.toUpperCase(Locale.ROOT))
                .filter(item -> !allowed.contains(item))
                .toList();
        if (!invalid.isEmpty()) {
            throw new IllegalArgumentException(
                    label + "包含不支持的值: " + String.join(",", invalid));
        }
    }

    /**
     * 校验并获取已有状态编码集合；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param value 待校验并获取已有状态编码集合的原始输入，结果供调用方继续使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void requireExistingStatusCodes(String entityCode, Object value) {
        if (statusMapper == null || !StringUtils.hasText(entityCode)) {
            return;
        }
        List<String> missing = toValues(value).stream()
                .map(String::valueOf)
                .filter(StringUtils::hasText)
                .filter(code -> statusMapper.findByEntityAndCode(entityCode, code) == null)
                .toList();
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException(
                    "状态编码不存在: " + String.join(",", missing));
        }
    }

    /**
     * 转换为值集合；输出作为后续校验或处理的输入。
     *
     * @param value 待转换为值集合的原始输入，结果供调用方继续使用
     * @return 权限SQL构建器集合，供调用方遍历或展示
     */
    private List<Object> toValues(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value.getClass().isArray()) {
            return List.of((Object[]) value);
        }
        if (value instanceof String text && text.contains(",")) {
            return java.util.Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(item -> (Object) item)
                    .toList();
        }
        return List.of(value);
    }

    /**
     * 转换为列名称；输出作为后续校验或处理的输入。
     *
     * @param fieldName 字段名称，后续用于转换为列名称时匹配或展示
     * @return 转换为后的列名称文本，供调用方比较或展示
     */
    private String toColumnName(String fieldName) {
        if (!StringUtils.hasText(fieldName)) {
            return fieldName;
        }
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < fieldName.length(); index++) {
            char current = fieldName.charAt(index);
            if (Character.isUpperCase(current)) {
                result.append('_').append(Character.toLowerCase(current));
            } else {
                result.append(current);
            }
        }
        return result.toString();
    }

    /**
     * 生成规范化文本，供后续匹配或展示。
     *
     * @param value 待处理规范化的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的规范化文本，供调用方比较或展示
     */
    private String normalized(String value, String fallback) {
        return StringUtils.hasText(value)
                ? value.toUpperCase(Locale.ROOT)
                : fallback;
    }

    /**
     * 整理系统字段列集合数据，供调用方遍历或继续处理。
     *
     * @return 系统字段列集合键值结果，供调用方继续处理
     */
    private static Map<String, String> systemFieldColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id");
        columns.put("name", "name");
        columns.put("code", "code");
        columns.put("status", "status");
        columns.put("processStatus", "process_status");
        columns.put("process_status", "process_status");
        columns.put("processInstanceId", "process_instance_id");
        columns.put("process_instance_id", "process_instance_id");
        columns.put("processStartTime", "process_start_time");
        columns.put("process_start_time", "process_start_time");
        columns.put("processEndTime", "process_end_time");
        columns.put("process_end_time", "process_end_time");
        columns.put("currentTaskId", "current_task_id");
        columns.put("current_task_id", "current_task_id");
        columns.put("currentTaskName", "current_task_name");
        columns.put("current_task_name", "current_task_name");
        columns.put("currentTaskAssignee", "current_task_assignee");
        columns.put("current_task_assignee", "current_task_assignee");
        columns.put("submitterId", "submitter_id");
        columns.put("submitter_id", "submitter_id");
        columns.put("submitterName", "submitter_name");
        columns.put("submitter_name", "submitter_name");
        columns.put("deptId", "dept_id");
        columns.put("dept_id", "dept_id");
        columns.put("submitTime", "submit_time");
        columns.put("submit_time", "submit_time");
        columns.put("create_time", "create_time");
        columns.put("update_time", "update_time");
        columns.put("create_by", "create_by");
        columns.put("update_by", "update_by");
        return columns;
    }
}
