package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/** 将动态实体的四个审计字段编码统一为现有物理列名，不改动业务数据及发布快照。 */
public class V098__align_entity_audit_field_codes extends BaseJavaMigration {
    private static final List<AuditField> FIELDS = List.of(
            new AuditField("createdAt", "create_time", "DATETIME", "datetime", null),
            new AuditField("updatedAt", "update_time", "DATETIME", "datetime", null),
            new AuditField("createdBy", "create_by", "STRING", "varchar(64)", 64),
            new AuditField("updatedBy", "update_by", "STRING", "varchar(64)", 64));

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        for (AuditField field : FIELDS) {
            // 同一实体同时存在新旧编码时保留正式编码的字段 ID，并把直接引用归并到该字段。
            mergeDuplicateReferences(connection, field);
            try (var statement = connection.prepareStatement("""
                    DELETE old_field FROM entity_field old_field
                    JOIN entity_definition e ON e.id=old_field.entity_id AND e.storage_mode='DYNAMIC'
                    JOIN entity_field canonical ON canonical.entity_id=old_field.entity_id AND canonical.field_code=?
                    WHERE old_field.field_code=?
                    """)) {
                statement.setString(1, field.column());
                statement.setString(2, field.oldCode());
                statement.executeUpdate();
            }
            // 通常只有旧编码：直接更名以保留字段 ID，列表等按 ID 绑定的配置不丢失。
            try (var statement = connection.prepareStatement("""
                    UPDATE entity_field f
                    JOIN entity_definition e ON e.id=f.entity_id AND e.storage_mode='DYNAMIC'
                    SET f.field_code=?, f.db_column_name=?, f.field_type=?, f.db_type=?, f.field_length=?,
                        f.is_system=1, f.editable=0, f.is_required=0, f.is_unique=0,
                        f.default_value=NULL, f.validate_rules=NULL, f.value_storage='SCALAR', f.deleted=0
                    WHERE f.field_code IN (?,?)
                    """)) {
                statement.setString(1, field.column());
                statement.setString(2, field.column());
                statement.setString(3, field.type());
                statement.setString(4, field.dbType());
                if (field.length() == null) statement.setNull(5, Types.INTEGER);
                else statement.setInt(5, field.length());
                statement.setString(6, field.oldCode());
                statement.setString(7, field.column());
                statement.executeUpdate();
            }
        }
    }

    /** 仅处理固定的字段关系表；字符串字段 ID 显式比较，避免把虚拟列表字段误转为数值。 */
    private void mergeDuplicateReferences(Connection connection, AuditField field) throws SQLException {
        for (String table : List.of("entity_field_option", "entity_field_file_item", "entity_list_field")) {
            String sql = """
                    UPDATE %s child
                    JOIN entity_field old_field ON child.field_id =
                        CAST(old_field.id AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci
                    JOIN entity_definition e ON e.id=old_field.entity_id AND e.storage_mode='DYNAMIC'
                    JOIN entity_field canonical ON canonical.entity_id=old_field.entity_id AND canonical.field_code=?
                    SET child.field_id=CAST(canonical.id AS CHAR CHARACTER SET utf8mb4)
                    WHERE old_field.field_code=?
                    """.formatted(table);
            try (var statement = connection.prepareStatement(sql)) {
                statement.setString(1, field.column());
                statement.setString(2, field.oldCode());
                statement.executeUpdate();
            }
        }
    }

    private record AuditField(String oldCode, String column, String type, String dbType, Integer length) {}
}
