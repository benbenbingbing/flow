package com.workflow.entity.data.application;

import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
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
    /**
     * 运行态选择器对应的平台身份表。只返回公开标识字段，
     * 不能再要求用户/组织/角色后台管理权限。
     */
    private static final Set<String> RUNTIME_SELECTOR_ENTITIES = Set.of(
            "sys_user",
            "sys_organization",
            "sys_role",
            "sys_group");

    private final JdbcTemplate jdbcTemplate;
    private final EntityDefinitionMapper definitionMapper;
    private final EntityFieldMapper fieldMapper;
    private final SystemEntityFieldPolicy fieldPolicy;
    private final DatabaseQueryDialect queryDialect;

    /**
     * 判断是否系统实体；判断结果决定调用方的后续分支。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 系统实体条件成立时为 true，否则为 false
     */
    public boolean isSystemEntity(String entityCode) {
        EntityDefinition definition =
                definitionMapper.findByEntityCode(entityCode)
                        .orElse(null);
        return definition != null
                && definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM;
    }

    /**
     * 按筛选条件分页查询实体数据；结果供列表展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param filters 过滤条件，供本方法查询系统实体读取分页时使用
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
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

    /**
     * 按筛选条件分页查询实体数据；结果供列表展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param filters 过滤条件，作为 {@code executePage} 的输入影响后续处理
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param sortField 排序字段，供本方法查询系统实体读取分页时使用
     * @param sortDirection 排序{@code direction}，供本方法查询系统实体读取分页时使用
     * @return 符合条件的实体数据结果，供调用方继续处理
     */
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
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param keyword 关键字，作为 {@code executePage} 的输入影响后续处理
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @return 符合条件的实体数据结果，供调用方继续处理
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

    /**
     * 执行系统实体读取分页，并将结果传给后续步骤。
     *
     * @param definition 定义，供本方法执行系统实体读取分页时使用
     * @param metadata 元数据，作为 {@code quote} 的输入影响后续处理
     * @param sqlFilter SQL过滤，供本方法执行系统实体读取分页时使用
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param sortField 排序字段，供本方法执行系统实体读取分页时使用
     * @param sortDirection 排序{@code direction}，供本方法执行系统实体读取分页时使用
     * @return 执行后的系统实体读取分页结果，供调用方继续处理
     */
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
        // 方言同时生成 SQL 和参数顺序，避免 PostgreSQL 的 LIMIT/OFFSET 交换绑定值。
        var pageQuery = queryDialect.paginate(
                "SELECT " + selectColumns
                        + " FROM " + table
                        + where
                        + orderBy(
                                metadata,
                                sortField,
                                sortDirection),
                sqlFilter.parameters(),
                Math.multiplyExact(safePageNum - 1, safePageSize), safePageSize);
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(pageQuery.sql(), pageQuery.parameters().toArray());
        List<EntityDataDTO> records = rows.stream()
                .map(row -> toDto(definition, metadata, row))
                .toList();
        return new PageResult<>(
                records,
                total == null ? 0 : total,
                safePageNum,
                safePageSize);
    }

    /**
     * 按ID查询实体数据；结果供后续展示或处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的实体数据结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
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

    /**
     * 校验平台系统表读取权限。
     * USER/DEPT/ROLE/GROUP 对应的身份表供运行态选择器和已授权列表使用，已登录即可；
     * 菜单、字典等配置表仍要求对应后台管理权限。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     */
    public void requirePermissions(String entityCode) {
        if (!fieldPolicy.isSupportedEntity(entityCode)) {
            throw new ForbiddenException(
                    "平台系统表不在通用只读访问白名单");
        }
        if (RUNTIME_SELECTOR_ENTITIES.contains(normalize(entityCode))) {
            return;
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

    /**
     * 校验并获取系统实体；不满足约束时阻止后续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 校验并获取后的系统实体结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 处理元数据，并将结果传给后续步骤。
     *
     * @param definition 定义，作为 {@code IllegalStateException} 的输入影响后续处理
     * @return 处理后的元数据结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 构建过滤；结果供后续流程传递或持久化。
     *
     * @param metadata 元数据，作为 {@code conditions.add} 的输入影响后续处理
     * @param filters 过滤条件，供本方法构建过滤时使用
     * @return 构建后的过滤结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 构建{@code selector}过滤；结果供后续流程传递或持久化。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param metadata 元数据，作为 {@code conditions.add} 的输入影响后续处理
     * @param keyword 关键字，作为 {@code parameters.add} 的输入影响后续处理
     * @return 构建后的{@code selector}过滤结果，供调用方继续处理
     */
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

    /**
     * 生成编码字段文本，供后续匹配或展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 处理后的编码字段文本，供调用方比较或展示
     */
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

    /**
     * 追加条件；结果供后续流程传递或持久化。
     *
     * @param column 列，作为 {@code conditions.add} 的输入影响后续处理
     * @param operator 操作人，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param value 待追加条件的原始输入，结果供调用方继续使用
     * @param start 启动，供本方法追加条件时使用
     * @param end 结束，供本方法追加条件时使用
     * @param conditions {@code conditions}，供本方法追加条件时使用
     * @param parameters 参数集合，供本方法追加条件时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 规范化操作人；输出作为后续校验或处理的输入。
     *
     * @param requested 请求，供本方法规范化操作人时使用
     * @param start 启动，供本方法规范化操作人时使用
     * @param end 结束，供本方法规范化操作人时使用
     * @param value 待规范化操作人的原始输入，结果供调用方继续使用
     * @return 规范化后的操作人文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 转换为DTO；输出作为后续校验或处理的输入。
     *
     * @param definition 定义，作为 {@code enrichReferenceDisplays} 的输入影响后续处理
     * @param metadata 元数据，供本方法转换为DTO时使用
     * @param row 行，供本方法转换为DTO时使用
     * @return 转换为后的DTO结果，供调用方继续处理
     */
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
        dto.setCreateTime(dateTime(data.get("create_time")));
        dto.setUpdateTime(dateTime(data.get("update_time")));
        dto.setCreateBy(text(data.get("create_by")));
        dto.setUpdateBy(text(data.get("update_by")));
        dto.setData(data);
        return dto;
    }

    /**
     * 补充引用{@code displays}；结果供调用方的后续步骤使用。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param data 数据，后续用于补充引用{@code displays}并传递处理结果
     */
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

    /**
     * 处理引用展示，并将结果传给后续步骤。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param fieldCode 字段编码，后续用于处理引用展示时定位或关联目标
     * @return 处理后的引用展示结果，供调用方继续处理
     */
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

    /**
     * 解析名称；输出作为后续校验或处理的输入。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param data 数据，后续用于解析名称并传递处理结果
     * @return 解析后的名称文本，供调用方比较或展示
     */
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

    /**
     * 解析编码；输出作为后续校验或处理的输入。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param data 数据，后续用于解析编码并传递处理结果
     * @return 解析后的编码文本，供调用方比较或展示
     */
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

    /**
     * 查找系统实体读取；结果供调用方的后续步骤使用。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param idColumn ID列，供本方法查找系统实体读取时使用
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param expression 表达式，作为 {@code jdbcTemplate.queryForObject} 的输入影响后续处理
     * @return 查找后的系统实体读取文本，供调用方比较或展示
     */
    private String lookup(
            String table,
            String idColumn,
            Object id,
            String expression) {
        if (id == null) {
            return null;
        }
        try {
            // 调用点均按平台表的唯一主键读取；无需分页来掩盖重复数据，也不依赖 LIMIT。
            return jdbcTemplate.queryForObject(
                    "SELECT " + expression
                            + " FROM " + quote(table)
                            + " WHERE " + quote(idColumn)
                            + " = ?",
                    String.class,
                    id);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * 生成默认顺序文本，供后续匹配或展示。
     *
     * @param metadata 元数据，作为 {@code quote} 的输入影响后续处理
     * @return 处理后的默认顺序文本，供调用方比较或展示
     */
    private String defaultOrder(QueryMetadata metadata) {
        if (metadata.readableColumns()
                .containsKey("create_time")) {
            return " ORDER BY "
                    + quote(metadata.readableColumns()
                            .get("create_time"))
                    + " DESC" + stableIdOrder(metadata, metadata.readableColumns().get("create_time"));
        }
        if (metadata.readableColumns().containsKey("id")) {
            return " ORDER BY "
                    + quote(metadata.readableColumns().get("id"))
                    + " ASC";
        }
        return "";
    }

    /**
     * 生成顺序文本，供后续匹配或展示。
     *
     * @param metadata 元数据，作为 {@code defaultOrder} 的输入影响后续处理
     * @param sortField 排序字段，作为 {@code normalize} 的输入影响后续处理
     * @param sortDirection 排序{@code direction}，供本方法处理顺序时使用
     * @return 处理后的顺序文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
                + " " + direction + stableIdOrder(metadata, column);
    }

    /**
     * 非唯一排序字段可能重复，以主键打破平局，保证同一份数据的各页不重复或漏行。
     *
     * @param metadata 元数据，供本方法处理稳定ID顺序时使用
     * @param primarySortColumn 主要排序列，供本方法处理稳定ID顺序时使用
     * @return 处理后的稳定ID顺序文本，供调用方比较或展示
     */
    private String stableIdOrder(QueryMetadata metadata, String primarySortColumn) {
        String id = metadata.readableColumns().get("id");
        return StringUtils.hasText(id) && !id.equals(primarySortColumn) ? ", " + quote(id) + " ASC" : "";
    }

    /**
     * 读取或规范化输入值，供后续计算与比较使用。
     *
     * @param row 行，供本方法处理值时使用
     * @param column 列，作为 {@code row.get} 的输入影响后续处理
     * @return 处理后的值结果，供调用方继续处理
     */
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

    /**
     * 处理日期时间，并将结果传给后续步骤。
     *
     * @param value 待处理日期时间的原始输入，结果供调用方继续使用
     * @return 处理后的日期时间结果，供调用方继续处理
     */
    private LocalDateTime dateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        return null;
    }

    /**
     * 整理值集合数据，供调用方遍历或继续处理。
     *
     * @param value 待处理值集合的原始输入，结果供调用方继续使用
     * @return {@code list<?>}集合，供调用方遍历或展示
     */
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

    /**
     * 生成关系名称文本，供后续匹配或展示。
     *
     * @param left 左侧，作为 {@code firstNonBlank} 的输入影响后续处理
     * @param right 右侧，作为 {@code firstNonBlank} 的输入影响后续处理
     * @return 处理后的关系名称文本，供调用方比较或展示
     */
    private String relationName(String left, String right) {
        if (StringUtils.hasText(left)
                && StringUtils.hasText(right)) {
            return left + " - " + right;
        }
        return firstNonBlank(left, right, "关系记录");
    }

    /**
     * 生成{@code strip}后缀文本，供后续匹配或展示。
     *
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 处理后的{@code strip}后缀文本，供调用方比较或展示
     */
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

    /**
     * 生成引用文本，供后续匹配或展示。
     *
     * @param identifier 标识符，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @return 处理后的引用文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String quote(String identifier) {
        if (!StringUtils.hasText(identifier)
                || !IDENTIFIER.matcher(identifier).matches()) {
            throw new IllegalArgumentException(
                    "非法系统表标识符: " + identifier);
        }
        return queryDialect.quoteIdentifier(identifier);
    }

    /**
     * 规范化输入值，确保后续比较和持久化使用一致格式。
     *
     * @param value 待规范化系统实体读取的原始输入，结果供调用方继续使用
     * @return 规范化后的系统实体读取文本，供调用方比较或展示
     */
    private String normalize(String value) {
        return value == null
                ? ""
                : value.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 按候选顺序取首个非空白值，供后续处理使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个非空白文本，供调用方比较或展示
     */
    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 封装查询元数据的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param tableName 目标物理表名，后续用于构造查询或表结构操作
     * @param readableColumns 可读列集合，保存在对象中供后续校验、查询或展示
     */
    private record QueryMetadata(
            String tableName,
            Map<String, String> readableColumns) {
    }

    /**
     * 封装SQL过滤的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param sql SQL，保存在对象中供后续校验、查询或展示
     * @param parameters 参数集合，保存在对象中供后续校验、查询或展示
     */
    private record SqlFilter(
            String sql,
            List<Object> parameters) {
    }

    /**
     * 封装引用展示的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param table 服务端确定的目标表名，用于生成 SQL 语句
     * @param expression 表达式，保存在对象中供后续校验、查询或展示
     */
    private record ReferenceDisplay(
            String table,
            String expression) {
    }
}
