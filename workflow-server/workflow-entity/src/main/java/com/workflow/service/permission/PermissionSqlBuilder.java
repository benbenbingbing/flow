package com.workflow.service.permission;

import com.workflow.dto.permission.EntityActionRuleDTO;
import com.workflow.dto.permission.FilterConfigDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.EntityStatus;
import com.workflow.entity.SysUser;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.EntityStatusMapper;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
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

    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper fieldMapper;
    private final EntityStatusMapper statusMapper;
    private final List<EntityDataPermissionFilterProvider> filterProviders;

    public PermissionSqlBuilder(
            EntityDefinitionMapper definitionMapper,
            EntityFieldMapper fieldMapper,
            EntityStatusMapper statusMapper,
            List<EntityDataPermissionFilterProvider> filterProviders) {
        this.definitionMapper = definitionMapper;
        this.fieldMapper = fieldMapper;
        this.statusMapper = statusMapper;
        this.filterProviders = filterProviders == null ? List.of() : filterProviders;
    }

    /**
     * 编译数据过滤配置为 SQL 条件片段，不带实体编码。
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
     * <p>根据过滤类型（全部、本人、提交人、当前办理人、部门、部门树、结构化规则）
     * 生成基础范围 SQL，再叠加状态限制条件。</p>
     *
     * @param entityCode 实体编码，可为 null（不解析实体字段）
     * @param filter     数据过滤配置，为空返回 "1=0"
     * @param user       当前用户，为空返回 "1=0"
     * @return SQL 条件片段
     */
    public String buildFilterSql(String entityCode, FilterConfigDTO filter, SysUser user) {
        if (filter == null || user == null) {
            return "1=0";
        }
        FilterConfigDTO.FieldMappingDTO mapping = filter.getFieldMapping() == null
                ? new FilterConfigDTO.FieldMappingDTO()
                : filter.getFieldMapping();
        String deptField = safeField(mapping.getDeptField(), "dept_id");
        String userField = safeField(mapping.getUserField(), "create_by");
        String statusField = safeField(mapping.getStatusField(), "status");
        if ("created_by".equalsIgnoreCase(userField)) {
            userField = "create_by";
        }
        if (deptField == null || userField == null || statusField == null) {
            return "1=0";
        }

        String type = normalized(filter.getType(), "PERSONAL");
        String baseSql = switch (type) {
            case "ALL" -> "1=1";
            case "PERSONAL" -> matchesUserSql(userField, user);
            case "SUBMITTER" -> matchesUserSql("submitter_id", user);
            case "CURRENT_ASSIGNEE" -> matchesUserSql("current_task_assignee", user);
            case "DEPT" -> equalsSql(deptField, user.getDeptId());
            case "DEPT_TREE" -> buildDeptTreeSql(deptField, user.getDeptId());
            case "RULE" -> buildRuleSql(
                    entityCode,
                    filter.getRoot(),
                    user,
                    resolveFieldColumns(entityCode),
                    1,
                    new int[]{0});
            case "EXPRESSION", "CUSTOM_SQL" -> "1=0";
            default -> matchesUserSql(userField, user);
        };
        if (!StringUtils.hasText(baseSql)) {
            baseSql = "1=0";
        }

        String statusSql = buildStatusSql(filter.getStatusLimit(), statusField);
        if (statusSql == null) {
            return baseSql;
        }
        return "(" + baseSql + ") AND (" + statusSql + ")";
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
                || StringUtils.hasText(filter.getCustomSql())) {
            throw new IllegalArgumentException("数据权限不再支持表达式或自定义 SQL，请改用结构化条件");
        }
        if (!Set.of(
                "ALL", "PERSONAL", "SUBMITTER", "CURRENT_ASSIGNEE",
                "DEPT", "DEPT_TREE", "RULE").contains(type)) {
            throw new IllegalArgumentException("不支持的数据范围类型: " + type);
        }
        FilterConfigDTO.FieldMappingDTO mapping = filter.getFieldMapping();
        if (mapping != null) {
            requireSafeField(mapping.getUserField(), "用户字段");
            requireSafeField(mapping.getDeptField(), "部门字段");
            requireSafeField(mapping.getStatusField(), "状态字段");
        }
        FilterConfigDTO.StatusLimitDTO statusLimit = filter.getStatusLimit();
        if (statusLimit != null && Boolean.TRUE.equals(statusLimit.getEnabled())
                && !Set.of("IN", "NOT_IN").contains(normalized(statusLimit.getMode(), "IN"))) {
            throw new IllegalArgumentException("状态限制仅支持 IN 或 NOT_IN");
        }
        if ("RULE".equals(type)) {
            if (filter.getRoot() == null) {
                throw new IllegalArgumentException("结构化条件不能为空");
            }
            validateRuleNode(
                    entityCode,
                    filter.getRoot(),
                    resolveFieldColumns(entityCode),
                    1,
                    new int[]{0});
        }
    }

    /**
     * 转义 SQL 字符串字面量中的单引号，防止注入。
     *
     * @param input 原始输入
     * @return 转义后的字符串，null 返回空串
     */
    public String escapeLiteral(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("'", "''");
    }

    private String buildRuleSql(
            String entityCode,
            EntityActionRuleDTO.RuleNode node,
            SysUser user,
            Map<String, String> fieldColumns,
            int depth,
            int[] count) {
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
                    count);
            case "RELATION" -> buildRelationSql(node.getRelation(), user);
            case "PROCESS_STATE" -> buildProcessStateComparison(
                    entityCode,
                    node.getOperator(),
                    node.getValue());
            case "STATUS_CODE" -> buildComparisonSql(
                    "status",
                    node.getOperator(),
                    node.getValue());
            case "STATUS_CATEGORY" -> buildStatusCategorySql(
                    entityCode,
                    node.getOperator(),
                    node.getValue());
            case "FIELD" -> {
                String column = resolveFieldColumn(fieldColumns, node.getField());
                yield column == null
                        ? "1=0"
                        : buildComparisonSql(column, node.getOperator(), node.getValue());
            }
            case "USER_FIELD" -> evaluateUserField(node, user) ? "1=1" : "1=0";
            default -> buildCustomSql(entityCode, node, user);
        };
    }

    private String buildGroupSql(
            String entityCode,
            EntityActionRuleDTO.RuleNode node,
            SysUser user,
            Map<String, String> fieldColumns,
            int depth,
            int[] count) {
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
                        count))
                .filter(StringUtils::hasText)
                .map(part -> "(" + part + ")")
                .toList();
        return parts.isEmpty() ? "1=0" : String.join(joiner, parts);
    }

    private String buildRelationSql(String relation, SysUser user) {
        if (!StringUtils.hasText(relation)) {
            return "1=0";
        }
        return switch (relation.toUpperCase(Locale.ROOT)) {
            case "CURRENT_USER_IS_CREATOR" -> matchesUserSql("create_by", user);
            case "CURRENT_USER_IS_SUBMITTER" -> matchesUserSql("submitter_id", user);
            case "CURRENT_USER_IS_ASSIGNEE" -> matchesUserSql("current_task_assignee", user);
            case "CURRENT_USER_SAME_DEPT" -> equalsSql("dept_id", user.getDeptId());
            default -> "1=0";
        };
    }

    private String buildProcessStateComparison(
            String entityCode,
            String operator,
            Object value) {
        List<Object> states = toValues(value);
        String op = normalized(operator, "EQ");
        if (states.isEmpty()) {
            return "NOT_IN".equals(op) || "NE".equals(op) ? "1=1" : "1=0";
        }
        List<String> stateSql = states.stream()
                .map(state -> processStateSql(entityCode, String.valueOf(state)))
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

    private String processStateSql(String entityCode, String state) {
        return switch (normalized(state, "")) {
            case "NOT_STARTED" ->
                    "(process_instance_id IS NULL OR process_instance_id = '')";
            case "RUNNING" ->
                    "(process_instance_id IS NOT NULL AND process_instance_id <> '' AND process_end_time IS NULL)";
            case "WITHDRAWN" ->
                    buildStatusCategorySql(entityCode, "EQ", "WITHDRAWN");
            case "TERMINATED" ->
                    buildStatusCategorySql(entityCode, "EQ", "TERMINATED");
            case "COMPLETED" -> {
                String excluded = buildStatusCategorySql(
                        entityCode,
                        "IN",
                        List.of("WITHDRAWN", "TERMINATED"));
                yield "(process_instance_id IS NOT NULL AND process_instance_id <> '' "
                        + "AND process_end_time IS NOT NULL AND NOT (" + excluded + "))";
            }
            default -> "1=0";
        };
    }

    private String buildStatusCategorySql(
            String entityCode,
            String operator,
            Object value) {
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
                new ArrayList<>(statusCodes));
    }

    private String buildComparisonSql(
            String column,
            String operator,
            Object value) {
        String op = normalized(operator, "EQ");
        if (!FIELD_OPERATORS.contains(op)) {
            return "1=0";
        }
        return switch (op) {
            case "EMPTY" -> "(" + column + " IS NULL OR " + column + " = '')";
            case "NOT_EMPTY" -> "(" + column + " IS NOT NULL AND " + column + " <> '')";
            case "EQ" -> equalitySql(column, value, false);
            case "NE" -> equalitySql(column, value, true);
            case "IN" -> inSql(column, toValues(value), false);
            case "NOT_IN" -> inSql(column, toValues(value), true);
            case "CONTAINS" -> likeSql(column, value, false);
            case "NOT_CONTAINS" -> likeSql(column, value, true);
            case "GT" -> orderedSql(column, ">", value);
            case "GTE" -> orderedSql(column, ">=", value);
            case "LT" -> orderedSql(column, "<", value);
            case "LTE" -> orderedSql(column, "<=", value);
            default -> "1=0";
        };
    }

    private String equalitySql(String column, Object value, boolean negate) {
        if (value == null) {
            return column + (negate ? " IS NOT NULL" : " IS NULL");
        }
        return column + (negate ? " <> " : " = ") + literal(value);
    }

    private String inSql(String column, List<Object> values, boolean negate) {
        if (values.isEmpty()) {
            return negate ? "1=1" : "1=0";
        }
        String joined = values.stream().map(this::literal).collect(java.util.stream.Collectors.joining(","));
        return column + (negate ? " NOT IN (" : " IN (") + joined + ")";
    }

    private String likeSql(String column, Object value, boolean negate) {
        if (value == null) {
            return negate ? "1=1" : "1=0";
        }
        String escaped = escapeLiteral(String.valueOf(value))
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return column + (negate ? " NOT LIKE " : " LIKE ")
                + "'%" + escaped + "%' ESCAPE '\\\\'";
    }

    private String orderedSql(String column, String operator, Object value) {
        return value == null ? "1=0" : column + " " + operator + " " + literal(value);
    }

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
        return compare(actual, node.getOperator(), node.getValue());
    }

    private boolean compare(Object actual, String operator, Object expected) {
        String op = normalized(operator, "EQ");
        return switch (op) {
            case "EMPTY" -> isEmpty(actual);
            case "NOT_EMPTY" -> !isEmpty(actual);
            case "EQ" -> equalsValue(actual, expected);
            case "NE" -> !equalsValue(actual, expected);
            case "IN" -> toValues(expected).stream().anyMatch(value -> equalsValue(actual, value));
            case "NOT_IN" -> toValues(expected).stream().noneMatch(value -> equalsValue(actual, value));
            case "CONTAINS" -> contains(actual, expected);
            case "NOT_CONTAINS" -> !contains(actual, expected);
            case "GT" -> compareOrdered(actual, expected) > 0;
            case "GTE" -> compareOrdered(actual, expected) >= 0;
            case "LT" -> compareOrdered(actual, expected) < 0;
            case "LTE" -> compareOrdered(actual, expected) <= 0;
            default -> false;
        };
    }

    private String buildCustomSql(
            String entityCode,
            EntityActionRuleDTO.RuleNode node,
            SysUser user) {
        return filterProviders.stream()
                .filter(provider -> provider.getType().equalsIgnoreCase(node.getType()))
                .findFirst()
                .map(provider -> provider.toSql(entityCode, node, user))
                .filter(StringUtils::hasText)
                .orElse("1=0");
    }

    private void validateRuleNode(
            String entityCode,
            EntityActionRuleDTO.RuleNode node,
            Map<String, String> fieldColumns,
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
                requireAllowedValues(node.getValue(), PROCESS_STATES, "流程状态");
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
                if (resolveFieldColumn(fieldColumns, node.getField()) == null) {
                    throw new IllegalArgumentException("字段不存在或不可用于数据权限: " + node.getField());
                }
                requireOperator(node.getOperator(), FIELD_OPERATORS);
                requireValues(node.getOperator(), node.getValue(), "字段条件");
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

    private Map<String, String> resolveFieldColumns(String entityCode) {
        Map<String, String> columns = new LinkedHashMap<>(SYSTEM_FIELD_COLUMNS);
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
                    : toColumnName(field.getFieldCode());
            if (SQL_IDENTIFIER.matcher(column).matches()) {
                columns.put(field.getFieldCode(), column);
                columns.put(column, column);
            }
        }
        return columns;
    }

    private String resolveFieldColumn(Map<String, String> columns, String field) {
        if (!StringUtils.hasText(field)) {
            return null;
        }
        String column = columns.get(field);
        if (column == null) {
            column = columns.get(toColumnName(field));
        }
        return column != null && SQL_IDENTIFIER.matcher(column).matches() ? column : null;
    }

    private String buildDeptTreeSql(String deptField, String deptId) {
        if (!StringUtils.hasText(deptId)) {
            return "1=0";
        }
        String escapedDeptId = escapeLiteral(deptId);
        return deptField + " IN ("
                + "SELECT id FROM sys_organization "
                + "WHERE id = '" + escapedDeptId + "' "
                + "OR path LIKE '%/" + escapedDeptId + "/%')";
    }

    private String buildStatusSql(
            FilterConfigDTO.StatusLimitDTO statusLimit,
            String statusField) {
        if (statusLimit == null || !Boolean.TRUE.equals(statusLimit.getEnabled())) {
            return null;
        }
        List<String> values = statusLimit.getValues();
        if (values == null || values.isEmpty()) {
            return null;
        }
        return inSql(
                statusField,
                new ArrayList<>(values),
                "NOT_IN".equalsIgnoreCase(statusLimit.getMode()));
    }

    private String matchesUserSql(String field, SysUser user) {
        if (user == null) {
            return "1=0";
        }
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        if (StringUtils.hasText(user.getId())) {
            identities.add(user.getId());
        }
        if (StringUtils.hasText(user.getUsername())) {
            identities.add(user.getUsername());
        }
        return inSql(field, new ArrayList<>(identities), false);
    }

    private String equalsSql(String field, String value) {
        return StringUtils.hasText(value) ? field + " = '" + escapeLiteral(value) + "'" : "1=0";
    }

    private String safeField(String fieldName, String fallback) {
        String value = StringUtils.hasText(fieldName) ? fieldName : fallback;
        return SQL_IDENTIFIER.matcher(value).matches() ? value : null;
    }

    private void requireSafeField(String field, String label) {
        if (StringUtils.hasText(field) && !SQL_IDENTIFIER.matcher(field).matches()) {
            throw new IllegalArgumentException(label + "包含非法字段名: " + field);
        }
    }

    private void requireOperator(String operator, Set<String> allowed) {
        String normalized = normalized(operator, "EQ");
        if (!allowed.contains(normalized)) {
            throw new IllegalArgumentException("不支持的比较操作符: " + operator);
        }
    }

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

    private String literal(Object value) {
        if (value == null) {
            return "NULL";
        }
        if (value instanceof Number number) {
            return new BigDecimal(String.valueOf(number)).toPlainString();
        }
        if (value instanceof Boolean bool) {
            return bool ? "1" : "0";
        }
        return "'" + escapeLiteral(String.valueOf(value)) + "'";
    }

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

    private boolean equalsValue(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        if (actual instanceof Number || expected instanceof Number) {
            try {
                return new BigDecimal(String.valueOf(actual))
                        .compareTo(new BigDecimal(String.valueOf(expected))) == 0;
            } catch (NumberFormatException ignored) {
            }
        }
        return String.valueOf(actual).equals(String.valueOf(expected));
    }

    private boolean contains(Object actual, Object expected) {
        if (actual instanceof Collection<?> collection) {
            return collection.stream().anyMatch(value -> equalsValue(value, expected));
        }
        return actual != null && expected != null
                && String.valueOf(actual).contains(String.valueOf(expected));
    }

    private int compareOrdered(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return -1;
        }
        try {
            return new BigDecimal(String.valueOf(actual))
                    .compareTo(new BigDecimal(String.valueOf(expected)));
        } catch (NumberFormatException ignored) {
            return String.valueOf(actual).compareTo(String.valueOf(expected));
        }
    }

    private boolean isEmpty(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof String text) {
            return text.isBlank();
        }
        if (value instanceof Collection<?> collection) {
            return collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return map.isEmpty();
        }
        return false;
    }

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

    private String normalized(String value, String fallback) {
        return StringUtils.hasText(value)
                ? value.toUpperCase(Locale.ROOT)
                : fallback;
    }

    private static Map<String, String> systemFieldColumns() {
        Map<String, String> columns = new LinkedHashMap<>();
        columns.put("id", "id");
        columns.put("dataNo", "data_no");
        columns.put("data_no", "data_no");
        columns.put("title", "title");
        columns.put("name", "name");
        columns.put("code", "code");
        columns.put("status", "status");
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
        columns.put("createdAt", "create_time");
        columns.put("create_time", "create_time");
        columns.put("updatedAt", "update_time");
        columns.put("update_time", "update_time");
        columns.put("createdBy", "create_by");
        columns.put("createBy", "create_by");
        columns.put("create_by", "create_by");
        columns.put("updatedBy", "update_by");
        columns.put("updateBy", "update_by");
        columns.put("update_by", "update_by");
        return columns;
    }
}
