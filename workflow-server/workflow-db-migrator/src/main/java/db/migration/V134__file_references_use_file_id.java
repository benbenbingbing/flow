package db.migration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** 一次性将实体附件和流程表单中的存储地址转换为业务文件引用，运行时不保留旧地址协议。 */
public class V134__file_references_use_file_id extends BaseJavaMigration {
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, FileReference> files = new HashMap<>();
    private final Set<String> fileFields = new HashSet<>();
    private final Map<String, Set<String>> entityFileFields = new HashMap<>();
    private final Map<String, String> tables = new HashMap<>();
    private String quote;

    /**
     * 复用文件登记 ID，不移动二进制、不变更所有者或表结构。按主键分批读取，避免加载整张业务表。
     * 未登记地址会中止迁移，须先补齐文件登记，不能凭历史路径猜测所有者或静默丢弃附件。
     */
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        files.clear(); fileFields.clear(); entityFileFields.clear(); tables.clear();
        quote = connection.getMetaData().getIdentifierQuoteString().trim();
        try (var rows = connection.getMetaData().getTables(connection.getCatalog(), connection.getSchema(), null, new String[]{"TABLE"})) {
            while (rows.next()) tables.put(rows.getString("TABLE_NAME").toLowerCase(Locale.ROOT), rows.getString("TABLE_NAME"));
        }
        try (var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT id, storage_url, original_name, content_length FROM storage_file_object")) {
            while (rows.next()) files.put(rows.getString(2), new FileReference(rows.getString(1), rows.getString(3), rows.getLong(4)));
        }
        // 字段坐标来自服务端元数据；只处理真实存在的 FILE / IMAGE 列，未发布字段没有物理列。
        List<Target> targets = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("""
                SELECT e.table_name, f.db_column_name, f.field_code, e.entity_code
                FROM entity_field f JOIN entity_definition e ON e.id = f.entity_id
                WHERE f.field_type IN ('FILE', 'IMAGE')
                """)) {
            while (rows.next()) {
                String field = rows.getString(3);
                fileFields.add(field);
                entityFileFields.computeIfAbsent(rows.getString(4), key -> new HashSet<>()).add(field);
                String column = rows.getString(2);
                targets.add(new Target(rows.getString(1), column == null || column.isBlank() ? field : column, false));
            }
        }
        targets.addAll(List.of(new Target("process_draft", "form_data", true),
                new Target("process_task", "form_data", true),
                new Target("process_task_add_sign", "source_form_data", true),
                new Target("process_task_draft", "form_data", true)));
        for (Target target : new LinkedHashSet<>(targets)) migrateColumn(connection, target);
    }

    /** 仅在已登记的文件字段内转换值；流程文档中的普通文本和非文件字段保持原样。 */
    private JsonNode rewriteDocument(JsonNode node, Set<String> fields) {
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(i, rewriteDocument(node.get(i), fields));
        } else if (node.isObject()) {
            List<String> keys = new ArrayList<>(); node.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) ((ObjectNode) node).set(key,
                    fields.contains(key) ? rewriteFile(node.get(key)) : rewriteDocument(node.get(key), fields));
        }
        return node;
    }

    /** 保留单文件、数组及附件分组形状，统一引用字段；已转换值重复执行时保持不变。 */
    private JsonNode rewriteFile(JsonNode node) {
        if (node == null || node.isNull() || (node.isTextual() && node.asText().isBlank())) return node;
        if (node.isTextual()) {
            String text = node.asText().trim();
            // 表单快照可能把整个附件数组再编码为 JSON 文本，转换时恢复为正常引用值。
            if (text.startsWith("[") || text.startsWith("{")) {
                try { return rewriteFile(json.readTree(text)); }
                catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                    throw new IllegalArgumentException("附件字段包含无效 JSON", exception);
                }
            }
            return reference(node.asText(), null);
        }
        if (node.isArray()) {
            for (int i = 0; i < node.size(); i++) ((com.fasterxml.jackson.databind.node.ArrayNode) node).set(i, rewriteFile(node.get(i)));
            return node;
        }
        if (node.isObject()) {
            if (node.hasNonNull("fileId")) return node;
            for (String key : List.of("url", "path", "fileUrl")) {
                if (node.hasNonNull(key)) return reference(node.get(key).asText(), node);
            }
            List<String> keys = new ArrayList<>(); node.fieldNames().forEachRemaining(keys::add);
            for (String key : keys) ((ObjectNode) node).set(key, rewriteFile(node.get(key)));
            return node;
        }
        throw new IllegalStateException("附件值不是文件引用、数组或分组对象");
    }

    /** 按完整地址精确匹配已登记对象；名称优先保留附件自身的显示名，大小取可信登记值。 */
    private ObjectNode reference(String url, JsonNode original) {
        FileReference file = files.get(url);
        if (file == null) throw new IllegalStateException("历史附件未在 storage_file_object 登记，请先补齐归属后重试迁移");
        ObjectNode result = json.createObjectNode().put("fileId", file.id()).put("size", file.size());
        String name = original == null ? null : original.path("name").asText(null);
        if (name == null && original != null) name = original.path("originalName").asText(null);
        result.put("name", name == null ? Objects.requireNonNullElse(file.name(), "附件") : name);
        return result;
    }

    /** 先关闭读取游标再更新；兼容 JDBC 流式读取限制，并只写入确有变化的行。 */
    private void migrateColumn(Connection connection, Target target) throws Exception {
        String table = target.table() == null ? null : tables.get(target.table().toLowerCase(Locale.ROOT));
        if (table == null) return;
        Map<String, String> columns = new HashMap<>();
        try (var rows = connection.getMetaData().getColumns(connection.getCatalog(), connection.getSchema(), table, null)) {
            while (rows.next()) columns.put(rows.getString("COLUMN_NAME").toLowerCase(Locale.ROOT), rows.getString("COLUMN_NAME"));
        }
        String column = columns.get(target.column().toLowerCase(Locale.ROOT));
        String id = columns.get("id");
        if (column == null || id == null) return;
        String coordinate = target.document() ? columns.get("entity_code") : null;
        boolean sourceTask = target.document() && coordinate == null && columns.containsKey("source_task_id");
        if (sourceTask) coordinate = columns.get("source_task_id");
        Object last = null;
        while (true) {
            List<Row> page = new ArrayList<>();
            String sql = "SELECT " + identifier(id) + ", " + identifier(column) + " FROM " + identifier(table)
                    + (last == null ? "" : " WHERE " + identifier(id) + " > ?") + " ORDER BY " + identifier(id);
            if (coordinate != null) sql = sql.replace(" FROM ", ", " + identifier(coordinate) + " FROM ");
            try (var statement = connection.prepareStatement(sql)) {
                statement.setMaxRows(200);
                if (last != null) statement.setObject(1, last);
                try (var rows = statement.executeQuery()) {
                    while (rows.next()) page.add(new Row(rows.getObject(1), rows.getString(2), coordinate == null ? null : rows.getString(3)));
                }
            }
            if (page.isEmpty()) return;
            for (Row row : page) {
                if (row.value() == null || row.value().isBlank()) continue;
                try {
                    JsonNode before;
                    try { before = json.readTree(row.value()); }
                    catch (com.fasterxml.jackson.core.JsonProcessingException exception) {
                        if (target.document()) throw exception;
                        before = json.getNodeFactory().textNode(row.value());
                    }
                    JsonNode after = target.document() ? rewriteDocument(before.deepCopy(), documentFields(connection, row.entityCode(), sourceTask))
                            : rewriteFile(before.deepCopy());
                    if (before.equals(after)) continue;
                    try (var update = connection.prepareStatement("UPDATE " + identifier(table) + " SET " + identifier(column) + " = ? WHERE " + identifier(id) + " = ?")) {
                        update.setString(1, json.writeValueAsString(after)); update.setObject(2, row.id()); update.executeUpdate();
                    }
                } catch (Exception exception) {
                    throw new SQLException("附件引用迁移失败: " + table + "." + column + ", id=" + row.id(), exception);
                }
            }
            last = page.get(page.size() - 1).id();
        }
    }

    /** 以表单所属实体确定文件字段，避免同名字段在其他实体是普通文本时被改写。 */
    private Set<String> documentFields(Connection connection, String entityCode, boolean sourceTask) throws SQLException {
        if (sourceTask && entityCode != null && tables.containsKey("process_task")) {
            try (var statement = connection.prepareStatement("SELECT entity_code FROM " + identifier(tables.get("process_task")) + " WHERE task_id = ?")) {
                statement.setString(1, entityCode);
                try (var rows = statement.executeQuery()) { entityCode = rows.next() ? rows.getString(1) : null; }
            }
        }
        return entityCode == null ? fileFields : entityFileFields.getOrDefault(entityCode, Set.of());
    }

    /** 表列名只能来自数据库元数据，仍校验并引用，避免动态 SQL 混入路径或表达式。 */
    private String identifier(String name) {
        if (!name.matches("[A-Za-z_][A-Za-z0-9_]*")) throw new IllegalArgumentException("非法数据库标识符");
        return quote + name + quote;
    }

    private record FileReference(String id, String name, long size) {}
    private record Target(String table, String column, boolean document) {}
    private record Row(Object id, String value, String entityCode) {}
}
