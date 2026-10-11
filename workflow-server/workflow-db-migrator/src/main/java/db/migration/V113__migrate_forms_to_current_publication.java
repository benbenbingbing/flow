package db.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.util.*;

/** 将当前完整表单配置搬到唯一发布表；不发布草稿，不重写流程历史。 */
public class V113__migrate_forms_to_current_publication extends BaseJavaMigration {
    private static final Logger log = LoggerFactory.getLogger(V113__migrate_forms_to_current_publication.class);
    private final ObjectMapper json = new ObjectMapper();
    private final JsonDocumentCodec codec = new JsonDocumentCodec(json);
    private static final Set<String> RETIRED = Set.of("childFormReleaseId", "childFormReleaseVersion",
            "refFormReleaseId", "refFormReleaseVersion", "publishedFormReleaseId", "publishedFormReleaseVersion",
            "targetFormReleaseId", "targetFormReleaseVersion");
    private record Publication(String formId, String entityId, String document, String hash, String actor, Timestamp time) { }

    /** 全部源快照和引用验证通过后才写入；失败保留旧数据，便于定位和修复。 */
    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean ownTransaction = connection.getAutoCommit();
        if (ownTransaction) connection.setAutoCommit(false);
        try {
            List<Publication> publications = plan(connection);
            for (Publication row : publications) {
                try (var insert = connection.prepareStatement("INSERT INTO entity_form_published "
                        + "(form_id, entity_id, snapshot_document, content_hash, published_by, published_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                    insert.setString(1, row.formId()); insert.setString(2, row.entityId());
                    insert.setString(3, row.document()); insert.setString(4, row.hash());
                    insert.setString(5, row.actor()); insert.setTimestamp(6, row.time()); insert.executeUpdate();
                }
                try (var update = connection.prepareStatement("UPDATE entity_form SET active_release_id = ? WHERE id = ?")) {
                    update.setString(1, row.hash()); update.setString(2, row.formId()); update.executeUpdate();
                }
            }
            // 默认表单按实体唯一初始化；先按时间升序覆盖，重复旧默认选择最近一次发布的表单。
            Map<String, Publication> defaults = new LinkedHashMap<>();
            for (Publication row : publications.stream().sorted(Comparator.comparing(Publication::time)).toList()) {
                Map<?, ?> form = (Map<?, ?>) codec.readObject(row.document(), "V113 默认表单").get("form");
                if (Boolean.TRUE.equals(form.get("isDefault")) && !Integer.valueOf(0).equals(form.get("status")))
                    defaults.put(row.entityId(), row);
            }
            for (Publication row : defaults.values()) try (var insert = connection.prepareStatement(
                    "INSERT INTO entity_form_current_default(entity_id,form_id) VALUES(?,?)")) {
                insert.setString(1, row.entityId()); insert.setString(2, row.formId()); insert.executeUpdate();
            }
            // 老版本只读归档；所有新运行请求必须经过唯一当前发布表。
            try (var statement = connection.createStatement()) {
                // 子表单依赖由当前父配置维护；旧展开索引不能永久阻止已解除引用的子表单删除。
                statement.executeUpdate("DELETE FROM process_ui_release_binding WHERE config_type = 'FORM' AND node_id LIKE '%#sub:%'");
                statement.executeUpdate("UPDATE process_ui_release_binding SET pinned_release_id = '', pinned_release_version = 0 WHERE config_type = 'FORM'");
                statement.executeUpdate("UPDATE ui_config_release SET status = 'ARCHIVED' WHERE config_type = 'FORM'");
                statement.executeUpdate("UPDATE ui_config_hotfix_target SET status = 'SUPERSEDED' WHERE config_type = 'FORM' AND status = 'ACTIVE'");
                statement.executeUpdate("UPDATE sys_menu SET deleted = 1 WHERE perm IN "
                        + "('entity:ui-config:hotfix','entity:ui-config:hotfix:observe','entity:ui-config:hotfix:rollback','entity:ui-config:hotfix:override')");
            }
            if (ownTransaction) connection.commit();
        } catch (Exception error) {
            if (ownTransaction) connection.rollback();
            throw error;
        } finally {
            if (ownTransaction) connection.setAutoCommit(true);
        }
    }

    private List<Publication> plan(Connection connection) throws Exception {
        List<Publication> result = new ArrayList<>();
        Set<String> forms = new HashSet<>();
        Map<String, Set<String>> dependencies = new LinkedHashMap<>();
        Map<String, Set<String>> references = new LinkedHashMap<>();
        try (var query = connection.prepareStatement("SELECT r.*, f.entity_id FROM ui_config_release r "
                + "JOIN entity_form f ON f.id = r.config_id AND f.deleted = 0 WHERE r.config_type = 'FORM' AND r.status = 'ACTIVE'")) {
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    String id = rows.getString("config_id");
                    if (!forms.add(id)) throw invalid(id, "存在多个 ACTIVE 发布记录");
                    String original = codec.canonicalize(rows.getString("snapshot_document"), "V113 表单 " + id);
                    if (!hash(original).equals(rows.getString("content_hash"))) throw invalid(id, "发布快照哈希不一致");
                    Map<String, Object> snapshot = codec.readObject(original, "V113 表单 " + id);
                    if (!(snapshot.get("form") instanceof Map<?, ?> form) || !id.equals(String.valueOf(form.get("id"))))
                        throw invalid(id, "发布快照所属表单不一致");
                    if (!Objects.equals(rows.getString("entity_id"), form.get("entityId")))
                        throw invalid(id, "发布快照所属实体不一致");
                    Set<String> children = new LinkedHashSet<>();
                    Set<String> targets = new LinkedHashSet<>();
                    normalize(snapshot, children, targets);
                    dependencies.put(id, children);
                    references.put(id, targets);
                    String document = codec.canonicalize(json.writeValueAsString(snapshot), "V113 当前表单 " + id);
                    result.add(new Publication(id, rows.getString("entity_id"), document, hash(document),
                            rows.getString("published_by"), rows.getTimestamp("published_at")));
                }
            }
        }
        for (var entry : dependencies.entrySet()) {
            for (String child : references.get(entry.getKey())) if (!forms.contains(child)) throw invalid(entry.getKey(), "子表单没有当前发布配置: " + child);
            validateGraph(entry.getKey(), dependencies, new LinkedHashSet<>());
        }
        validateExternalReferences(connection, forms);
        return result;
    }

    /** 固定流程和 Embed 的旧坐标退役前，必须确认稳定身份仍有可运行的当前配置。 */
    private void validateExternalReferences(Connection connection, Set<String> forms) throws Exception {
        for (String query : List.of(
                "SELECT config_id FROM process_ui_release_binding WHERE config_type = 'FORM' AND (node_id IS NULL OR node_id NOT LIKE '%#sub:%')",
                "SELECT default_form_id FROM embed_view_release WHERE default_form_id IS NOT NULL")) {
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
                while (rows.next()) {
                    String id = rows.getString(1);
                    if (id != null && !id.isBlank() && !forms.contains(id))
                        throw invalid(id, "已发布流程或 Embed 引用缺少当前发布配置");
                }
            }
        }
        // V034 曾清空发布记录和依赖索引，但保留流程历史。仅这类已经失效的旧坐标
        // 可以保留为待修复历史；有发布记录、当前指针或规范化绑定的引用仍必须严格校验。
        Set<String> unpublishedForms = new HashSet<>();
        Set<String> existingReleaseIds = new HashSet<>();
        Map<String, Set<String>> unresolvedHistory = new TreeMap<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT f.id FROM entity_form f WHERE f.deleted = 0 "
                        + "AND (f.active_release_id IS NULL OR f.active_release_id = '') "
                        + "AND NOT EXISTS (SELECT 1 FROM ui_config_release r WHERE r.config_type = 'FORM' AND r.config_id = f.id)")) {
            while (rows.next()) unpublishedForms.add(rows.getString(1));
        }
        try (var statement = connection.createStatement(); var rows = statement.executeQuery("SELECT id FROM ui_config_release")) {
            while (rows.next()) existingReleaseIds.add(rows.getString(1));
        }
        // 不能只信任旧依赖索引：早期流程历史可能尚未建立规范化绑定。
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT id, node_forms_snapshot FROM process_version_history WHERE node_forms_snapshot IS NOT NULL")) {
            while (rows.next()) {
                String document = rows.getString(2);
                if (document == null || document.isBlank()) continue;
                Object parsed;
                try { parsed = json.readValue(document, Object.class); }
                catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                    throw invalid("流程历史 " + rows.getString(1), "节点表单快照不是合法 JSON");
                }
                if (!(parsed instanceof List<?> bindings))
                    throw invalid("流程历史 " + rows.getString(1), "节点表单快照不是数组");
                for (Object binding : bindings) if (binding instanceof Map<?, ?> map) {
                    Object id = map.get("formId");
                    if (id == null || String.valueOf(id).isBlank() || forms.contains(String.valueOf(id))) continue;
                    String formId = String.valueOf(id);
                    Object legacyRelease = map.get("formReleaseId");
                    if (unpublishedForms.contains(formId) && legacyRelease instanceof String releaseId
                            && !releaseId.isBlank() && !existingReleaseIds.contains(releaseId)) {
                        unresolvedHistory.computeIfAbsent(formId, ignored -> new TreeSet<>()).add(rows.getString(1));
                    } else throw invalid(formId, "流程历史 " + rows.getString(1) + " 引用缺少当前发布配置");
                }
            }
        }
        // 不重建已清空的版本、不启用草稿。运行入口仍返回 FORM_NOT_PUBLISHED，
        // 管理员重新发布同一 formId 后，所有旧流程自动读取该表单的当前配置。
        unresolvedHistory.forEach((formId, historyIds) -> log.warn(
                "V113: 表单 {} 的旧发布记录已不存在，保留流程历史 {} 的失效引用；未发布草稿，请重新发布该表单后使用相关流程",
                formId, historyIds));
        // 历史列表仍可被签名上下文引用，其表单按钮和关联内容必须能解析当前身份。
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT config_id, snapshot_document FROM ui_config_release WHERE config_type = 'LIST'")) {
            while (rows.next()) {
                Set<String> targets = new LinkedHashSet<>();
                normalize(codec.readObject(rows.getString(2), "V113 列表 " + rows.getString(1)), new LinkedHashSet<>(), targets);
                for (String id : targets) if (!forms.contains(id))
                    throw invalid(id, "列表 " + rows.getString(1) + " 引用缺少当前发布配置");
            }
        }
    }

    /** 清除旧版本坐标；属性中的 JSON 字符串也必须转换，脚本和普通文本保持原文。 */
    @SuppressWarnings("unchecked")
    private void normalize(Object value, Set<String> children, Set<String> references) throws Exception {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = (Map<String, Object>) raw;
            RETIRED.forEach(map::remove);
            for (String key : List.of("childFormId", "refFormId", "publishedFormId")) {
                Object id = map.get(key);
                if (id != null && !String.valueOf(id).isBlank()) { children.add(String.valueOf(id)); references.add(String.valueOf(id)); }
            }
            Object buttonForm = map.get("targetFormId");
            if (buttonForm != null && !String.valueOf(buttonForm).isBlank()) references.add(String.valueOf(buttonForm));
            // 自动包含参与深度和循环检查；弹窗等手工导航可以形成业务导航环。
            if (!Boolean.FALSE.equals(map.get("enabled")) && map.get("presentation") instanceof Map<?, ?> presentation
                    && Set.of("INLINE", "TAB", "ROW_EXPAND").contains(String.valueOf(presentation.get("position")))
                    && map.get("target") instanceof Map<?, ?> target && "FORM".equals(target.get("contentType"))) {
                Object id = target.get("contentId");
                if (id != null && !String.valueOf(id).isBlank()) children.add(String.valueOf(id));
            }
            if ("FORM".equals(map.get("contentType"))) {
                Object id = map.get("contentId");
                if (id != null && !String.valueOf(id).isBlank()) references.add(String.valueOf(id));
                map.remove("releaseId"); map.remove("releaseVersion"); map.remove("contentHash");
            }
            for (var entry : map.entrySet()) {
                if (Set.of("legacyProps", "legacyPropsDocument").contains(entry.getKey())) continue;
                Object child = entry.getValue();
                if (child instanceof String text && (text.stripLeading().startsWith("{") || text.stripLeading().startsWith("["))) {
                    Object parsed;
                    try { parsed = json.readValue(text, Object.class); }
                    catch (com.fasterxml.jackson.core.JsonProcessingException ignored) { continue; }
                    normalize(parsed, children, references); entry.setValue(json.writeValueAsString(parsed));
                } else normalize(child, children, references);
            }
        } else if (value instanceof List<?> list) for (Object child : list) normalize(child, children, references);
    }

    private void validateGraph(String id, Map<String, Set<String>> graph, Set<String> path) throws SQLException {
        if (path.size() >= 8 || !path.add(id)) throw invalid(id, "子表单引用循环或超过八层");
        for (String child : graph.getOrDefault(id, Set.of())) validateGraph(child, graph, path);
        path.remove(id);
    }

    private static String hash(String document) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(document.getBytes(StandardCharsets.UTF_8)));
    }
    private static SQLException invalid(String id, String reason) {
        return new SQLException("V113: 表单 " + id + " " + reason + "；未启用草稿或删除旧配置");
    }
}
