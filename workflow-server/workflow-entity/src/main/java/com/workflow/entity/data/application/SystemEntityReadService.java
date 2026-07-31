package com.workflow.entity.data.application;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.core.error.ForbiddenException;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.definition.application.SystemEntityFieldPolicy;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 平台系统表的可信只读查询入口。
 */
@Service
@RequiredArgsConstructor
public class SystemEntityReadService {

    private static final Pattern IDENTIFIER =
            Pattern.compile("[a-z][a-z0-9_]{0,127}");
    private static final Set<String> OPERATORS = Set.of(
            "EQ", "NE", "LIKE", "IN", "BETWEEN",
            "GT", "GE", "LT", "LE", "IS_NULL");

    private final JdbcTemplate jdbcTemplate;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper fieldMapper;
    private final SystemEntityFieldPolicy fieldPolicy;

    public boolean isSystemEntity(String entityCode) {
        EntityDefinition definition =
                definitionMapper.findByEntityCode(entityCode)
                        .orElse(null);
        return definition != null
                && definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM;
    }

    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findPage(
            String entityCode,
            Map<String, Object> filters,
            long pageNum,
            long pageSize) {
        return findPage(
                entityCode,
                filters,
                pageNum,
                pageSize,
                null,
                null);
    }

    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findPage(
            String entityCode,
            Map<String, Object> filters,
            long pageNum,
            long pageSize,
            String sortField,
            String sortDirection) {
        EntityDefinition definition = requireSystemEntity(entityCode);
        requirePermissions(entityCode);
        QueryMetadata metadata = metadata(definition);
        return executePage(
                definition,
                metadata,
                buildFilter(metadata, filters),
                pageNum,
                pageSize,
                sortField,
                sortDirection);
    }

    /**
     * 为实体记录选择器提供系统实体的安全分页查询。
     */
    @Transactional(readOnly = true)
    public PageResult<EntityDataDTO> findSelectorPage(
            String entityCode,
            String keyword,
            long pageNum,
            long pageSize) {
        EntityDefinition definition = requireSystemEntity(entityCode);
        requirePermissions(entityCode);
        QueryMetadata metadata = metadata(definition);
        return executePage(
                definition,
                metadata,
                buildSelectorFilter(entityCode, metadata, keyword),
                pageNum,
                pageSize,
                null,
                null);
    }

    private PageResult<EntityDataDTO> executePage(
            EntityDefinition definition,
            QueryMetadata metadata,
            SqlFilter sqlFilter,
            long pageNum,
            long pageSize,
            String sortField,
            String sortDirection) {
        long safePageNum = Math.max(1, pageNum);
        long safePageSize = Math.max(1, Math.min(200, pageSize));
        String selectColumns = metadata.readableColumns().values()
                .stream()
                .map(this::quote)
                .distinct()
                .reduce((left, right) -> left + ", " + right)
                .orElseThrow(() ->
                        new IllegalStateException("系统实体没有可读字段"));
        String table = quote(metadata.tableName());
        String where = sqlFilter.sql().isBlank()
                ? ""
                : " WHERE " + sqlFilter.sql();
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + where,
                Long.class,
                sqlFilter.parameters().toArray());
        List<Object> pageParameters =
                new ArrayList<>(sqlFilter.parameters());
        pageParameters.add((safePageNum - 1) * safePageSize);
        pageParameters.add(safePageSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + selectColumns
                        + " FROM " + table
                        + where
                        + orderBy(
                                metadata,
                                sortField,
                                sortDirection)
                        + " LIMIT ?, ?",
                pageParameters.toArray());
        List<EntityDataDTO> records = rows.stream()
                .map(row -> toDto(definition, metadata, row))
                .toList();
        return new PageResult<>(
                records,
                total == null ? 0 : total,
                safePageNum,
                safePageSize);
    }

    @Transactional(readOnly = true)
    public EntityDataDTO findById(
            String entityCode,
            String id) {
        if (!StringUtils.hasText(id)) {
            throw new IllegalArgumentException("系统表记录ID不能为空");
        }
        EntityDefinition definition = requireSystemEntity(entityCode);
        requirePermissions(entityCode);
        QueryMetadata metadata = metadata(definition);
        String idColumn = metadata.readableColumns().get("id");
        if (!StringUtils.hasText(idColumn)) {
            throw new IllegalStateException("系统实体没有可读主键字段");
        }
        Map<String, Object> filters = Map.of("id", id);
        PageResult<EntityDataDTO> page =
                findPage(entityCode, filters, 1, 1);
        if (page.getRecords().isEmpty()) {
            throw new ForbiddenException("数据不存在或无权访问");
        }
        return page.getRecords().get(0);
    }

    public void requirePermissions(String entityCode) {
        if (!fieldPolicy.isSupportedEntity(entityCode)) {
            throw new ForbiddenException(
                    "平台系统表不在通用只读访问白名单");
        }
        Set<String> current =
                PermissionUtil.getCurrentUserPermissions();
        if (current.contains("*")) {
            return;
        }
        for (String permission :
                fieldPolicy.requiredPermissions(entityCode)) {
            if (!current.contains(permission)) {
                throw new ForbiddenException(
                        "没有权限访问平台系统表：" + permission);
            }
        }
    }

    private EntityDefinition requireSystemEntity(
            String entityCode) {
        EntityDefinition definition =
                definitionMapper.findByEntityCode(entityCode)
                        .orElseThrow(() ->
                                new IllegalArgumentException(
                                        "实体不存在: " + entityCode));
        if (definition.getStorageMode()
                != EntityDefinition.StorageMode.SYSTEM) {
            throw new IllegalArgumentException(
                    "实体不是平台系统表: " + entityCode);
        }
        String tableName = definition.getPhysicalTableName();
        if (!StringUtils.hasText(tableName)
                || !tableName.equals(definition.getEntityCode())
                || !tableName.startsWith("sys_")
                || !IDENTIFIER.matcher(tableName).matches()) {
            throw new IllegalStateException(
                    "平台系统表目录登记不合法: " + entityCode);
        }
        return definition;
    }

    private QueryMetadata metadata(
            EntityDefinition definition) {
        Map<String, String> readableColumns =
                new LinkedHashMap<>();
        for (EntityField field :
                fieldMapper.findByEntityId(definition.getId())) {
            if (!fieldPolicy.isRuntimeReadable(
                    definition, field)) {
                continue;
            }
            String fieldCode = normalize(field.getFieldCode());
            String column = normalize(firstNonBlank(
                    field.getDbColumnName(),
                    field.getFieldCode()));
            if (!IDENTIFIER.matcher(fieldCode).matches()
                    || !IDENTIFIER.matcher(column).matches()) {
                throw new IllegalStateException(
                        "系统实体字段目录登记不合法: "
                                + definition.getEntityCode()
                                + "." + field.getFieldCode());
            }
            readableColumns.put(fieldCode, column);
        }
        return new QueryMetadata(
                definition.getPhysicalTableName(),
                readableColumns);
    }

    private SqlFilter buildFilter(
            QueryMetadata metadata,
            Map<String, Object> filters) {
        Map<String, Object> safeFilters =
                filters == null ? Map.of() : filters;
        Set<String> bases = new LinkedHashSet<>();
        for (String key : safeFilters.keySet()) {
            bases.add(stripSuffix(key));
        }
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        if (metadata.readableColumns().containsKey("deleted")) {
            conditions.add(quote(
                    metadata.readableColumns().get("deleted"))
                    + " = 0");
        }
        for (String base : bases) {
            if ("deleted".equals(base)) {
                continue;
            }
            String column = metadata.readableColumns().get(base);
            if (!StringUtils.hasText(column)) {
                throw new IllegalArgumentException(
                        "系统表字段不可查询: " + base);
            }
            Object value = safeFilters.get(base);
            Object start = safeFilters.get(base + "_start");
            Object end = safeFilters.get(base + "_end");
            String operator = normalizeOperator(
                    safeFilters.get(base + "_op"),
                    start,
                    end,
                    value);
            appendCondition(
                    quote(column),
                    operator,
                    value,
                    start,
                    end,
                    conditions,
                    parameters);
        }
        return new SqlFilter(
                String.join(" AND ", conditions),
                parameters);
    }

    private SqlFilter buildSelectorFilter(
            String entityCode,
            QueryMetadata metadata,
            String keyword) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        if (metadata.readableColumns().containsKey("deleted")) {
            conditions.add(quote(
                    metadata.readableColumns().get("deleted"))
                    + " = 0");
        }
        if (!StringUtils.hasText(keyword)) {
            return new SqlFilter(
                    String.join(" AND ", conditions),
                    parameters);
        }

        Set<String> searchFields = new LinkedHashSet<>();
        searchFields.add(fieldPolicy.displayField(entityCode));
        searchFields.add(codeField(entityCode));
        searchFields.add("name");
        searchFields.add("code");
        searchFields.add("title");
        searchFields.add("id");
        searchFields.removeIf(field ->
                !StringUtils.hasText(field)
                        || !metadata.readableColumns()
                                .containsKey(field));

        if (searchFields.isEmpty()) {
            conditions.add("1 = 0");
        } else {
            List<String> keywordConditions =
                    new ArrayList<>();
            for (String field : searchFields) {
                keywordConditions.add(
                        quote(metadata.readableColumns().get(field))
                                + " LIKE ?");
                parameters.add("%" + keyword.trim() + "%");
            }
            conditions.add("("
                    + String.join(" OR ", keywordConditions)
                    + ")");
        }
        return new SqlFilter(
                String.join(" AND ", conditions),
                parameters);
    }

    private String codeField(String entityCode) {
        return switch (normalize(entityCode)) {
            case "sys_user" -> "username";
            case "sys_role" -> "role_code";
            case "sys_organization" -> "org_code";
            case "sys_group" -> "group_code";
            case "sys_dict" -> "dict_code";
            case "sys_dict_item" -> "item_code";
            default -> null;
        };
    }

    private void appendCondition(
            String column,
            String operator,
            Object value,
            Object start,
            Object end,
            List<String> conditions,
            List<Object> parameters) {
        switch (operator) {
            case "IS_NULL" -> conditions.add(column + " IS NULL");
            case "BETWEEN" -> {
                Object left = start;
                Object right = end;
                if ((left == null || right == null)
                        && value instanceof List<?> values
                        && values.size() >= 2) {
                    left = values.get(0);
                    right = values.get(1);
                }
                if (left == null || right == null) {
                    throw new IllegalArgumentException(
                            "BETWEEN 查询必须同时提供起止值");
                }
                conditions.add(column + " BETWEEN ? AND ?");
                parameters.add(left);
                parameters.add(right);
            }
            case "IN" -> {
                List<?> values = values(value);
                if (values.isEmpty()) {
                    conditions.add("1 = 0");
                    return;
                }
                conditions.add(column + " IN ("
                        + String.join(
                                ", ",
                                java.util.Collections.nCopies(
                                        values.size(), "?"))
                        + ")");
                parameters.addAll(values);
            }
            default -> {
                if (value == null || "".equals(value)) {
                    return;
                }
                String sqlOperator = switch (operator) {
                    case "EQ" -> "=";
                    case "NE" -> "<>";
                    case "LIKE" -> "LIKE";
                    case "GT" -> ">";
                    case "GE" -> ">=";
                    case "LT" -> "<";
                    case "LE" -> "<=";
                    default -> throw new IllegalArgumentException(
                            "不支持的系统表查询运算符: "
                                    + operator);
                };
                conditions.add(column + " " + sqlOperator + " ?");
                parameters.add("LIKE".equals(operator)
                        ? "%" + value + "%"
                        : value);
            }
        }
    }

    private String normalizeOperator(
            Object requested,
            Object start,
            Object end,
            Object value) {
        String operator = requested == null
                ? null
                : String.valueOf(requested)
                        .trim()
                        .toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(operator)) {
            if (start != null || end != null) {
                operator = "BETWEEN";
            } else if (value instanceof Collection<?>) {
                operator = "IN";
            } else {
                operator = "EQ";
            }
        }
        if (!OPERATORS.contains(operator)) {
            throw new IllegalArgumentException(
                    "不支持的系统表查询运算符: " + operator);
        }
        return operator;
    }

    private EntityDataDTO toDto(
            EntityDefinition definition,
            QueryMetadata metadata,
            Map<String, Object> row) {
        Map<String, Object> data =
                new LinkedHashMap<>();
        metadata.readableColumns().forEach(
                (fieldCode, column) ->
                        data.put(fieldCode, value(row, column)));
        enrichReferenceDisplays(
                definition.getEntityCode(), data);
        EntityDataDTO dto = new EntityDataDTO();
        dto.setId(text(data.get("id")));
        dto.setEntityCode(definition.getEntityCode());
        dto.setEntityName(definition.getEntityName());
        dto.setStatus(text(data.get("status")));
        dto.setCode(resolveCode(definition.getEntityCode(), data));
        dto.setName(resolveName(definition.getEntityCode(), data));
        dto.setTitle(dto.getName());
        dto.setCreatedAt(dateTime(data.get("create_time")));
        dto.setUpdatedAt(dateTime(data.get("update_time")));
        dto.setCreatedBy(text(data.get("create_by")));
        dto.setUpdatedBy(text(data.get("update_by")));
        dto.setData(data);
        return dto;
    }

    private void enrichReferenceDisplays(
            String entityCode,
            Map<String, Object> data) {
        Map<String, Object> snapshot =
                new LinkedHashMap<>(data);
        snapshot.forEach((fieldCode, value) -> {
            if (value == null) {
                return;
            }
            ReferenceDisplay reference =
                    referenceDisplay(entityCode, fieldCode);
            if (reference == null) {
                return;
            }
            String display = lookup(
                    reference.table(),
                    "id",
                    value,
                    reference.expression());
            if (StringUtils.hasText(display)) {
                data.put(fieldCode + "_display", display);
            }
        });
    }

    private ReferenceDisplay referenceDisplay(
            String entityCode,
            String fieldCode) {
        if (Set.of(
                "user_id",
                "leader_id",
                "create_by",
                "update_by").contains(fieldCode)) {
            return new ReferenceDisplay(
                    "sys_user",
                    "COALESCE(NULLIF(nickname, ''), username)");
        }
        if ("role_id".equals(fieldCode)) {
            return new ReferenceDisplay(
                    "sys_role", "role_name");
        }
        if ("group_id".equals(fieldCode)) {
            return new ReferenceDisplay(
                    "sys_group", "group_name");
        }
        if (Set.of("org_id", "dept_id")
                .contains(fieldCode)) {
            return new ReferenceDisplay(
                    "sys_organization", "org_name");
        }
        if ("menu_id".equals(fieldCode)) {
            return new ReferenceDisplay(
                    "sys_menu", "menu_name");
        }
        if ("dict_id".equals(fieldCode)) {
            return new ReferenceDisplay(
                    "sys_dict", "dict_name");
        }
        if (!"parent_id".equals(fieldCode)) {
            return null;
        }
        return switch (entityCode) {
            case "sys_organization" ->
                    new ReferenceDisplay(
                            "sys_organization", "org_name");
            case "sys_menu" ->
                    new ReferenceDisplay(
                            "sys_menu", "menu_name");
            case "sys_dict_item" ->
                    new ReferenceDisplay(
                            "sys_dict_item", "item_label");
            default -> null;
        };
    }

    private String resolveName(
            String entityCode,
            Map<String, Object> data) {
        String displayField = fieldPolicy.displayField(entityCode);
        String name = text(data.get(displayField));
        if ("sys_user".equals(entityCode)
                && !StringUtils.hasText(name)) {
            name = text(data.get("username"));
        }
        if (StringUtils.hasText(name)) {
            return name;
        }
        return switch (entityCode) {
            case "sys_user_role" -> relationName(
                    lookup(
                            "sys_user",
                            "id",
                            data.get("user_id"),
                            "COALESCE(NULLIF(nickname, ''), username)"),
                    lookup(
                            "sys_role",
                            "id",
                            data.get("role_id"),
                            "role_name"));
            case "sys_role_menu" -> relationName(
                    lookup(
                            "sys_role",
                            "id",
                            data.get("role_id"),
                            "role_name"),
                    lookup(
                            "sys_menu",
                            "id",
                            data.get("menu_id"),
                            "menu_name"));
            case "sys_user_group" -> relationName(
                    lookup(
                            "sys_user",
                            "id",
                            data.get("user_id"),
                            "COALESCE(NULLIF(nickname, ''), username)"),
                    lookup(
                            "sys_group",
                            "id",
                            data.get("group_id"),
                            "group_name"));
            default -> firstNonBlank(
                    text(data.get("name")),
                    text(data.get("id")));
        };
    }

    private String resolveCode(
            String entityCode,
            Map<String, Object> data) {
        return switch (entityCode) {
            case "sys_user" -> text(data.get("username"));
            case "sys_role" -> text(data.get("role_code"));
            case "sys_organization" -> text(data.get("org_code"));
            case "sys_group" -> text(data.get("group_code"));
            case "sys_dict" -> text(data.get("dict_code"));
            case "sys_dict_item" -> text(data.get("item_code"));
            default -> text(data.get("id"));
        };
    }

    private String lookup(
            String table,
            String idColumn,
            Object id,
            String expression) {
        if (id == null) {
            return null;
        }
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT " + expression
                            + " FROM " + quote(table)
                            + " WHERE " + quote(idColumn)
                            + " = ? LIMIT 1",
                    String.class,
                    id);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private String defaultOrder(QueryMetadata metadata) {
        if (metadata.readableColumns()
                .containsKey("create_time")) {
            return " ORDER BY "
                    + quote(metadata.readableColumns()
                            .get("create_time"))
                    + " DESC";
        }
        if (metadata.readableColumns().containsKey("id")) {
            return " ORDER BY "
                    + quote(metadata.readableColumns().get("id"))
                    + " ASC";
        }
        return "";
    }

    private String orderBy(
            QueryMetadata metadata,
            String sortField,
            String sortDirection) {
        if (!StringUtils.hasText(sortField)) {
            return defaultOrder(metadata);
        }
        String normalizedField = normalize(sortField);
        String column =
                metadata.readableColumns().get(normalizedField);
        if (!StringUtils.hasText(column)) {
            throw new IllegalArgumentException(
                    "系统表字段不可排序: " + sortField);
        }
        String direction = StringUtils.hasText(sortDirection)
                ? sortDirection.trim().toUpperCase(Locale.ROOT)
                : "ASC";
        if (!Set.of("ASC", "DESC").contains(direction)) {
            throw new IllegalArgumentException(
                    "系统表排序方向只能是 ASC 或 DESC");
        }
        return " ORDER BY " + quote(column)
                + " " + direction;
    }

    private Object value(
            Map<String, Object> row,
            String column) {
        if (row.containsKey(column)) {
            return row.get(column);
        }
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(column)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    private List<?> values(Object value) {
        if (value instanceof Collection<?> collection) {
            return new ArrayList<>(collection);
        }
        if (value instanceof String text) {
            return java.util.Arrays.stream(text.split(","))
                    .map(String::trim)
                    .filter(item -> !item.isEmpty())
                    .toList();
        }
        return value == null ? List.of() : List.of(value);
    }

    private String relationName(String left, String right) {
        if (StringUtils.hasText(left)
                && StringUtils.hasText(right)) {
            return left + " - " + right;
        }
        return firstNonBlank(left, right, "关系记录");
    }

    private String stripSuffix(String key) {
        for (String suffix :
                List.of("_start", "_end", "_op")) {
            if (key.endsWith(suffix)) {
                return key.substring(
                        0,
                        key.length() - suffix.length());
            }
        }
        return key;
    }

    private String quote(String identifier) {
        if (!StringUtils.hasText(identifier)
                || !IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    "非法系统表标识符: " + identifier);
        }
        return "`" + identifier + "`";
    }

    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private record QueryMetadata(
            String tableName,
            Map<String, String> readableColumns) {
    }

    private record SqlFilter(
            String sql,
            List<Object> parameters) {
    }

    private record ReferenceDisplay(
            String table,
            String expression) {
    }
}
