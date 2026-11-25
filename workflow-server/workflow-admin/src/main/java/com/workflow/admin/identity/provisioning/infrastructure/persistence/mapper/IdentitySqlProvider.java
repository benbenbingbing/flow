package com.workflow.admin.identity.provisioning.infrastructure.persistence.mapper;

import com.workflow.port.system.model.identity.IdentityObjectType;
import java.util.*;

/** Mapper SQL 生成器：只插入服务端白名单标识符，所有业务值仍使用 MyBatis 参数绑定。 */
public final class IdentitySqlProvider {
    public String findObject(Map<String, Object> args) {
        return "SELECT * FROM " + objectTable(args) + " WHERE id = #{id} AND deleted = 0";
    }

    /** 表名由对象类型决定，哈希键避免依赖外部 ID 列的数据库排序规则。 */
    public String findSourceIds(Map<String, Object> args) {
        return """
                <script>
                SELECT b.source_key, b.local_id FROM sys_identity_source_binding b
                JOIN %s o ON o.id = b.local_id AND o.deleted = 0
                WHERE b.object_type = #{type} AND b.binding_status = 'ACTIVE' AND b.source_key IN
                <foreach collection="keys" item="key" open="(" close=")" separator=",">#{key}</foreach>
                </script>
                """.formatted(objectTable(args));
    }

    private String objectTable(Map<String, Object> args) {
        return args.get("type") == IdentityObjectType.USER ? "sys_user" : "sys_organization";
    }
    /** 用唯一索引按数据库排序规则定位候选，实际编码交给应用复核大小写和空格。 */
    public String selectObjectByCode(Map<String, Object> args) {
        if (args.get("type") == IdentityObjectType.USER) {
            return "SELECT id, username AS identity_code, status, deleted, authentication_mode FROM sys_user WHERE username = #{code}";
        }
        return "SELECT id, org_code AS identity_code, status, deleted FROM sys_organization WHERE org_code = #{code}";
    }
    public String readExtension(Map<String, Object> args) {
        validate(args);
        return "SELECT * FROM " + args.get("table") + " WHERE " + args.get("owner") + " = #{id}";
    }
    public String insertExtension(Map<String, Object> args) {
        List<String> columns = columns(args);
        return "INSERT INTO " + args.get("table") + " (" + String.join(", ", columns) + ", " + args.get("owner")
                + ", row_version) VALUES (" + String.join(", ", columns.stream().map(c -> "#{values." + c + "}").toList()) + ", #{id}, 1)";
    }
    public String updateExtension(Map<String, Object> args) {
        List<String> columns = columns(args);
        return "UPDATE " + args.get("table") + " SET "
                + String.join(", ", columns.stream().map(c -> c + " = #{values." + c + "}").toList())
                + ", row_version = row_version + 1 WHERE " + args.get("owner") + " = #{id} AND row_version = #{expected}";
    }
    @SuppressWarnings("unchecked")
    private List<String> columns(Map<String, Object> args) {
        validate(args);
        var columns = new ArrayList<>(((Map<String, Object>) args.get("values")).keySet());
        if (columns.isEmpty()) throw new IllegalArgumentException("扩展字段不能为空");
        for (String column : columns) {
            identifier(column);
            if (Set.of("user_id", "organization_id", "row_version").contains(column)) throw new IllegalArgumentException("扩展字段占用系统列");
        }
        return columns;
    }
    private void validate(Map<String, Object> args) {
        String table = (String) args.get("table");
        identifier(table);
        if (!table.startsWith("biz_") || !Set.of("user_id", "organization_id").contains(args.get("owner"))) {
            throw new IllegalArgumentException("非法扩展表标识符");
        }
    }
    private void identifier(String identifier) {
        if (identifier == null || !identifier.matches("[a-z][a-z0-9_]{0,62}")) throw new IllegalArgumentException("非法 SQL 标识符");
    }
}
