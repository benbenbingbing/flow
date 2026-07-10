package com.workflow.service.permission;

import com.workflow.dto.permission.FilterConfigDTO;
import com.workflow.entity.SysUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 数据权限过滤 SQL 构建器。
 */
@Component
@RequiredArgsConstructor
public class PermissionSqlBuilder {

    private static final Pattern SQL_IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private final PermissionVariableResolver variableResolver;

    public String buildFilterSql(FilterConfigDTO filter, SysUser user) {
        if (filter == null) {
            return null;
        }

        String type = filter.getType();
        FilterConfigDTO.FieldMappingDTO mapping = filter.getFieldMapping();
        if (mapping == null) {
            mapping = new FilterConfigDTO.FieldMappingDTO();
        }

        String deptField = safeField(mapping.getDeptField(), "dept_id");
        // 与实体表 entity_data 的字段名保持一致（create_by），避免 fallback 时拼出非法字段
        String userField = safeField(mapping.getUserField(), "create_by");
        // 兼容旧配置中保存的 created_by，统一映射为 create_by
        if ("created_by".equalsIgnoreCase(userField)) {
            userField = "create_by";
        }
        String statusField = safeField(mapping.getStatusField(), "status");
        if (deptField == null || userField == null || statusField == null) {
            return "1=0";
        }

        StringBuilder sql = new StringBuilder();
        switch (type) {
            case "PERSONAL":
                sql.append(userField).append(" = '").append(escapeLiteral(user.getId())).append("'");
                break;
            case "DEPT":
                if (user.getDeptId() != null) {
                    sql.append(deptField).append(" = '").append(escapeLiteral(user.getDeptId())).append("'");
                } else {
                    sql.append("1=0");
                }
                break;
            case "DEPT_TREE":
                sql.append(buildDeptTreeSql(deptField, user.getDeptId()));
                break;
            case "ALL":
                return "1=1";
            case "EXPRESSION":
                sql.append(userField).append(" = '").append(escapeLiteral(user.getId())).append("'");
                break;
            case "CUSTOM_SQL":
                String customSql = filter.getCustomSql();
                if (customSql == null || customSql.isBlank()) {
                    return "1=0";
                }
                return variableResolver.resolve(customSql, user);
            default:
                sql.append(userField).append(" = '").append(escapeLiteral(user.getId())).append("'");
        }

        String statusSql = buildStatusSql(filter.getStatusLimit(), statusField);
        if (statusSql != null) {
            sql.append(" AND ").append(statusSql);
        }

        return sql.toString();
    }

    public String escapeLiteral(String input) {
        if (input == null) {
            return "";
        }
        return input.replace("'", "''");
    }

    private String buildDeptTreeSql(String deptField, String deptId) {
        if (deptId == null || deptId.isEmpty()) {
            return "1=0";
        }
        String escapedDeptId = escapeLiteral(deptId);
        return deptField + " IN (" +
                "SELECT id FROM sys_organization " +
                "WHERE id = '" + escapedDeptId + "' " +
                "OR path LIKE '%/" + escapedDeptId + "/%')";
    }

    private String buildStatusSql(FilterConfigDTO.StatusLimitDTO statusLimit, String statusField) {
        if (statusLimit == null || !Boolean.TRUE.equals(statusLimit.getEnabled())) {
            return null;
        }

        List<String> values = statusLimit.getValues();
        if (values == null || values.isEmpty()) {
            return null;
        }

        List<String> escaped = values.stream()
                .map(this::escapeLiteral)
                .collect(Collectors.toList());

        if ("NOT_IN".equalsIgnoreCase(statusLimit.getMode())) {
            return statusField + " NOT IN ('" + String.join("','", escaped) + "')";
        }
        return statusField + " IN ('" + String.join("','", escaped) + "')";
    }

    private String safeField(String fieldName, String fallback) {
        String value = fieldName == null || fieldName.isBlank() ? fallback : fieldName;
        return SQL_IDENTIFIER.matcher(value).matches() ? value : null;
    }
}
