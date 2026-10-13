package db.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.*;

/**
 * 将字段组件和节点扩展一次性迁移到 componentType，迁移后删除第二套身份字段。
 * 无注册定义的旧扩展回退到原内置组件；不保留运行时兼容读取路径。
 */
public class V117__unify_form_components extends BaseJavaMigration {
    private final ObjectMapper json = new ObjectMapper();
    private final JsonDocumentCodec codec = new JsonDocumentCodec(json);
    private final Set<String> registrations = new HashSet<>();
    private final Set<String> oldNodeRegistrations = new HashSet<>();
    private static final Set<String> BUILT_INS = Set.of("input", "textarea", "number", "date", "datetime",
            "select", "select_multiple", "radio", "checkbox", "switch", "rich_text", "file",
            "image", "cascader", "reference", "multi_reference", "sub_list", "sub_form", "repeater");

    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        migrateDefinitions(connection);
        migrateDrafts(connection);
        migrateViewDrafts(connection);
        migrateSnapshots(connection);
        if (columnExists(connection, "entity_form", "draft_hash")) {
            try (var statement = connection.createStatement()) { statement.executeUpdate("UPDATE entity_form SET draft_hash = NULL"); }
        }
        // 在所有文档完成转换后才移除列；任何失败都由 Flyway 报告，禁止静默继续保留双轨字段。
        if (columnExists(connection, "entity_form_node", "component_name")) {
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE entity_form_node DROP COLUMN component_name");
            }
        }
    }

    /** 保留组件原注册名和版本；同名同版本已有 FIELD 时使用已有定义并移除重复 NODE。 */
    private void migrateDefinitions(Connection connection) throws Exception {
        if (!columnExists(connection, "ui_extension_definition", "extension_type")) return;
        record Definition(String id, String key, int version, int deleted) { }
        List<Definition> old = new ArrayList<>();
        Set<String> identities = new HashSet<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT id,extension_type,extension_key,version,deleted FROM ui_extension_definition WHERE extension_type IN ('NODE','FIELD')")) {
            while (rows.next()) {
                Definition value = new Definition(rows.getString(1), rows.getString(3), rows.getInt(4), rows.getInt(5));
                if ("NODE".equals(rows.getString(2))) {
                    old.add(value); oldNodeRegistrations.add(value.key() + "@" + value.version());
                }
                else identities.add(value.key() + "@" + value.version() + ":" + value.deleted());
                if (value.deleted() == 0) registrations.add(value.key() + "@" + value.version());
            }
        }
        for (Definition definition : old) {
            boolean duplicate = !identities.add(definition.key() + "@" + definition.version() + ":" + definition.deleted());
            try (var update = connection.prepareStatement(duplicate
                    ? "DELETE FROM ui_extension_definition WHERE id = ?"
                    : "UPDATE ui_extension_definition SET extension_type = 'FIELD' WHERE id = ?")) {
                update.setString(1, definition.id()); update.executeUpdate();
            }
        }
    }

    /** 草稿节点仍保持原 revision；结构迁移不会替用户发布或修改业务字段值。 */
    private void migrateDrafts(Connection connection) throws Exception {
        if (!columnExists(connection, "entity_form_node", "component_name")) return;
        List<Map<String, Object>> nodes = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT id,node_type,component_name,component_version,snapshot_version,props_document FROM entity_form_node")) {
            while (rows.next()) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("id", rows.getString(1)); node.put("nodeType", rows.getString(2));
                node.put("componentName", rows.getString(3)); node.put("componentVersion", rows.getObject(4));
                node.put("snapshotVersion", rows.getObject(5)); node.put("propsDocument", rows.getString(6));
                normalizeNode(node); nodes.add(node);
            }
        }
        for (Map<String, Object> node : nodes) try (var update = connection.prepareStatement(
                "UPDATE entity_form_node SET props_document = ?, component_version = ?, snapshot_version = ? WHERE id = ?")) {
            update.setString(1, (String) node.get("propsDocument"));
            update.setObject(2, node.get("componentVersion")); update.setObject(3, node.get("snapshotVersion"));
            update.setString(4, (String) node.get("id")); update.executeUpdate();
        }
    }

    private record SnapshotTable(String table, String key, String document, String hash, boolean currentForm) { }
    private record Snapshot(SnapshotTable table, String id, String original, String originalHash, Object value) { }

    /**
     * 快照变更后同步重算哈希，并修正其他快照对这些发布内容的哈希引用。
     * 发布版本引用形成有向无环图；按依赖逐轮收敛，避免父配置先迁移导致引用子配置的旧摘要。
     */
    private void migrateSnapshots(Connection connection) throws Exception {
        List<Snapshot> snapshots = new ArrayList<>();
        for (SnapshotTable table : List.of(
                new SnapshotTable("entity_form_published", "form_id", "snapshot_document", "content_hash", true),
                new SnapshotTable("ui_config_release", "id", "snapshot_document", "content_hash", false),
                new SnapshotTable("ui_config_hotfix_target", "id", "effective_snapshot_document", "effective_content_hash", false))) {
            if (!columnExists(connection, table.table(), table.document())) continue;
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                    "SELECT " + table.key() + "," + table.document() + "," + table.hash() + " FROM " + table.table())) {
                while (rows.next()) {
                    String document = rows.getString(2);
                    if (document == null || document.isBlank()) continue;
                    snapshots.add(new Snapshot(table, rows.getString(1), document, rows.getString(3), json.readValue(document, Object.class)));
                }
            }
        }
        Map<String, String> hashes = new HashMap<>();
        for (Snapshot snapshot : snapshots) {
            normalize(snapshot.value());
            String document = canonical(snapshot.value());
            if (!document.equals(snapshot.original())) hashes.put(snapshot.originalHash(), hash(document));
        }
        boolean converged = false;
        for (int pass = 0; pass <= snapshots.size(); pass++) {
            boolean changed = false;
            for (Snapshot snapshot : snapshots) {
                String before = canonical(snapshot.value());
                if (replaceHashReferences(snapshot.value(), hashes)) {
                    hashes.put(hash(before), hash(canonical(snapshot.value())));
                    changed = true;
                }
            }
            if (!changed) { converged = true; break; }
        }
        if (!converged) throw new IllegalStateException("表单组件迁移发现循环的内容哈希引用");
        for (Snapshot snapshot : snapshots) {
            String document = canonical(snapshot.value());
            if (document.equals(snapshot.original())) continue;
            SnapshotTable table = snapshot.table();
            String hash = hash(document);
            try (var update = connection.prepareStatement("UPDATE " + table.table() + " SET " + table.document()
                    + " = ?, " + table.hash() + " = ? WHERE " + table.key() + " = ?")) {
                update.setString(1, document); update.setString(2, hash); update.setString(3, snapshot.id()); update.executeUpdate();
            }
            if (table.currentForm() && columnExists(connection, "entity_form", "active_release_id")) {
                try (var update = connection.prepareStatement("UPDATE entity_form SET active_release_id = ? WHERE id = ?")) {
                    update.setString(1, hash); update.setString(2, snapshot.id()); update.executeUpdate();
                }
            }
        }
    }

    /** 只更新声明为发布坐标的字段；不替换普通业务文本、实体 ID 或接口定义摘要。 */
    @SuppressWarnings("unchecked")
    private boolean replaceHashReferences(Object value, Map<String, String> hashes) throws Exception {
        boolean changed = false;
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            for (var entry : map.entrySet()) {
                Object child = entry.getValue();
                if (child instanceof String text && Set.of("contentHash", "effectiveContentHash", "releaseId",
                        "childFormReleaseId", "refFormReleaseId", "publishedFormReleaseId").contains(entry.getKey())) {
                    Set<String> visited = new HashSet<>();
                    String replacement = text;
                    while (hashes.containsKey(replacement) && visited.add(replacement)) replacement = hashes.get(replacement);
                    if (!text.equals(replacement)) { entry.setValue(replacement); changed = true; }
                } else if (child instanceof String text && Set.of("propsDocument", "configDocument").contains(entry.getKey())) {
                    Object parsed;
                    try { parsed = json.readValue(text, Object.class); } catch (Exception ignored) { continue; }
                    if (replaceHashReferences(parsed, hashes)) { entry.setValue(json.writeValueAsString(parsed)); changed = true; }
                } else changed |= replaceHashReferences(child, hashes);
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) changed |= replaceHashReferences(item, hashes);
        }
        return changed;
    }

    /** 旧 NODE 关联内容制品无法转换制品摘要，移除该展示覆盖，让关联目标使用标准展示。 */
    private void migrateViewDrafts(Connection connection) throws Exception {
        if (!columnExists(connection, "ui_view_composition", "config_document")) return;
        Map<String, String> updates = new LinkedHashMap<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT id,config_document FROM ui_view_composition")) {
            while (rows.next()) {
                Object config = json.readValue(rows.getString(2), Object.class);
                if (normalize(config)) updates.put(rows.getString(1), canonical(config));
            }
        }
        for (var row : updates.entrySet()) try (var update = connection.prepareStatement("UPDATE ui_view_composition SET config_document = ? WHERE id = ?")) {
            update.setString(1, row.getValue()); update.setString(2, row.getKey()); update.executeUpdate();
        }
    }

    private String canonical(Object value) throws Exception {
        return codec.canonicalize(json.writeValueAsString(value), "统一表单组件");
    }

    private String hash(String document) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document.getBytes(StandardCharsets.UTF_8)));
    }

    /** 只转换表单节点及其属性文档，业务数据里恰好同名的 componentName 字段不受影响。 */
    @SuppressWarnings("unchecked")
    private boolean normalize(Object value) throws Exception {
        boolean changed = false;
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            if (map.containsKey("nodeType") && (map.containsKey("propsDocument") || map.containsKey("props") || map.containsKey("componentName"))) {
                String before = json.writeValueAsString(map); normalizeNode(map);
                changed = !before.equals(json.writeValueAsString(map));
            }
            if (map.get("specialHandling") instanceof Map<?, ?> rawSpecial) {
                Map<String, Object> special = (Map<String, Object>) rawSpecial;
                if (special.get("customComponent") instanceof Map<?, ?> component
                        && ("NODE".equals(component.get("extensionType"))
                        || !component.containsKey("extensionType") && oldNodeRegistrations.contains(
                                component.get("name") + "@" + component.get("version")))) {
                    special.remove("customComponent"); changed = true;
                }
            }
            if ("NODE".equals(map.get("type")) && (map.containsKey("extensionKey") || map.containsKey("name"))) {
                map.put("type", "FIELD"); changed = true;
            }
            if ("NODE".equals(map.get("extensionType")) && map.containsKey("extensionKey")) {
                map.put("extensionType", "FIELD"); changed = true;
            }
            for (var entry : map.entrySet()) {
                if (entry.getValue() instanceof String text && Set.of("propsDocument", "snapshotDocument", "legacyPropsDocument", "configDocument", "definitionSnapshot").contains(entry.getKey())) {
                    Object parsed;
                    try { parsed = json.readValue(text, Object.class); } catch (Exception ignored) { continue; }
                    if (normalize(parsed)) {
                        String document = canonical(parsed); entry.setValue(document); changed = true;
                        if ("definitionSnapshot".equals(entry.getKey()) && map.containsKey("definitionHash")) map.put("definitionHash", hash(document));
                    }
                } else changed |= normalize(entry.getValue());
            }
        } else if (value instanceof List<?> list) {
            for (Object item : list) changed |= normalize(item);
        }
        return changed;
    }

    /** 旧 componentName 曾优先渲染，因此可解析时覆盖旧的基础 componentType。 */
    @SuppressWarnings("unchecked")
    private void normalizeNode(Map<String, Object> node) throws Exception {
        Object raw = node.containsKey("props") ? node.get("props") : node.get("propsDocument");
        Map<String, Object> props = new LinkedHashMap<>();
        if (raw instanceof Map<?, ?> map) props.putAll((Map<String, Object>) map);
        else if (raw instanceof String text && !text.isBlank()) {
            try { props.putAll(json.readValue(text, Map.class)); } catch (Exception ignored) { /* 无法读取的旧组件配置按用户要求舍弃。 */ }
        }
        String oldName = Objects.toString(node.remove("componentName"), "").trim();
        props.remove("componentExtensionType");
        props.remove("componentName"); props.remove("nodeComponent");
        String current = Objects.toString(props.get("componentType"), "").trim();
        String version = Objects.toString(node.get("componentVersion"), "");
        boolean oldComponentResolved = !oldName.isBlank()
                && (BUILT_INS.contains(oldName) || registrations.contains(oldName + "@" + version));
        boolean discardedComponent = !oldName.isBlank() && !oldComponentResolved;
        if (oldComponentResolved) current = oldName;
        if (!current.isBlank() && !BUILT_INS.contains(current) && !registrations.contains(current + "@" + version)) {
            current = ""; discardedComponent = true;
        }
        // 原组件无法迁移时，其私有参数也失去含义；不能把它们交给回退控件产生意外行为。
        if (discardedComponent) props.remove("componentProps");
        if (current.isBlank()) props.remove("componentType"); else props.put("componentType", current);
        if (current.isBlank() || BUILT_INS.contains(current)) {
            node.remove("componentVersion"); node.remove("snapshotVersion");
        }
        if (node.containsKey("props")) node.put("props", props);
        if (node.containsKey("propsDocument")) node.put("propsDocument", props.isEmpty() ? null : json.writeValueAsString(props));
    }

    /** JDBC 元数据按大小写兼容 MySQL、H2 和 Oracle；不依赖数据库专用 JSON 函数。 */
    private boolean columnExists(Connection connection, String table, String column) throws Exception {
        for (String candidate : List.of(table, table.toUpperCase(Locale.ROOT))) {
            try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, candidate, null)) {
                while (columns.next()) if (column.equalsIgnoreCase(columns.getString("COLUMN_NAME"))) return true;
            }
        }
        return false;
    }
}
