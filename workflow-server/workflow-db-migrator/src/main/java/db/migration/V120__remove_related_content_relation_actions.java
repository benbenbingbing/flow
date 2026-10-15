package db.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.serialization.JsonDocumentCodec;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 一次性删除关联内容已退役的选择、关系写入和随主表保存配置。
 * 仅转换设计草稿及仍可运行的发布快照；不提供运行时旧配置兼容，也不修改实体引用字段值。
 */
public class V120__remove_related_content_relation_actions extends BaseJavaMigration {
    private static final Set<String> RETIRED_ACTIONS = Set.of("SELECT", "LINK", "UNLINK", "SAVE_WITH_FORM");
    private final ObjectMapper json = new ObjectMapper();
    private final JsonDocumentCodec codec = new JsonDocumentCodec(json);

    private record Owner(String type, String id) { }
    private record Draft(String id, Owner owner, String original, Map<String, Object> config) { }
    private record Publication(String table, String key, String id, Owner owner,
                               String original, String originalHash, Map<String, Object> snapshot) { }

    /**
     * 先解析全部源文档并核对发布摘要，再按依赖顺序计算新摘要，最后在同一事务内落库。
     * MySQL 的 Flyway 连接可能开启自动提交，因此显式接管这次纯 DML 迁移的事务，避免半清理状态。
     */
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean ownTransaction = connection.getAutoCommit();
        if (ownTransaction) connection.setAutoCommit(false);
        try {
            List<Draft> drafts = readDrafts(connection);
            List<Publication> publications = readPublications(connection);
            Map<String, Publication> lists = new HashMap<>();
            for (Publication publication : publications) {
                if ("LIST".equals(publication.owner().type())) lists.put(publication.id(), publication);
                for (Map<String, Object> config : compositionConfigs(publication.snapshot())) clean(config);
            }
            Map<String, String> hashes = new HashMap<>();
            Set<String> visiting = new HashSet<>();
            for (Publication publication : publications) resolveHash(publication, lists, hashes, visiting);
            for (Draft draft : drafts) {
                clean(draft.config());
                updateTargetHash(draft.config(), lists, hashes, visiting);
            }

            Set<Owner> changedDraftOwners = new LinkedHashSet<>();
            Set<Owner> invalidatedOwners = new LinkedHashSet<>();
            for (Draft draft : drafts) {
                String document = canonical(draft.config());
                if (document.equals(draft.original())) continue;
                try (var update = connection.prepareStatement("UPDATE ui_view_composition SET config_document = ?, "
                        + "revision = revision + 1, update_time = CURRENT_TIMESTAMP WHERE id = ?")) {
                    update.setString(1, document);
                    update.setString(2, draft.id());
                    update.executeUpdate();
                }
                changedDraftOwners.add(draft.owner());
            }
            for (Publication publication : publications) {
                String document = canonical(publication.snapshot());
                if (document.equals(publication.original())) continue;
                String hash = hashes.get(publication.table() + "/" + publication.id());
                try (var update = connection.prepareStatement("UPDATE " + publication.table()
                        + " SET snapshot_document = ?, content_hash = ? WHERE " + publication.key() + " = ?")) {
                    update.setString(1, document);
                    update.setString(2, hash);
                    update.setString(3, publication.id());
                    update.executeUpdate();
                }
                if ("FORM".equals(publication.owner().type())) {
                    try (var update = connection.prepareStatement("UPDATE entity_form SET active_release_id = ? WHERE id = ?")) {
                        update.setString(1, hash);
                        update.setString(2, publication.owner().id());
                        update.executeUpdate();
                    }
                }
                invalidatedOwners.add(publication.owner());
            }
            invalidatedOwners.addAll(changedDraftOwners);
            for (Owner owner : invalidatedOwners) {
                String table = switch (owner.type()) {
                    case "FORM" -> "entity_form";
                    case "LIST" -> "entity_list_config";
                    default -> throw new IllegalStateException("V120 不支持的关联内容宿主: " + owner.type());
                };
                // 已打开的设计器不能用迁移前修订号覆盖清理结果；纯发布摘要变化不伪造草稿编辑。
                String revision = changedDraftOwners.contains(owner) ? ", revision = COALESCE(revision, 0) + 1" : "";
                try (var update = connection.prepareStatement("UPDATE " + table + " SET draft_hash = NULL" + revision + " WHERE id = ?")) {
                    update.setString(1, owner.id());
                    update.executeUpdate();
                }
            }
            if (ownTransaction) connection.commit();
        } catch (Exception error) {
            if (ownTransaction) connection.rollback();
            throw error;
        } finally {
            if (ownTransaction) connection.setAutoCommit(true);
        }
    }

    /** 草稿包含软删除记录，避免恢复旧记录时重新带回已退役字段。 */
    private List<Draft> readDrafts(Connection connection) throws Exception {
        List<Draft> result = new ArrayList<>();
        try (var statement = connection.createStatement(); var rows = statement.executeQuery(
                "SELECT id, owner_type, owner_id, config_document FROM ui_view_composition")) {
            while (rows.next()) {
                String label = "V120 关联内容 " + rows.getString(1);
                String original = codec.canonicalize(rows.getString(4), label);
                result.add(new Draft(rows.getString(1), new Owner(rows.getString(2), rows.getString(3)),
                        original, codec.readObject(original, label)));
            }
        }
        return result;
    }

    /** 历史列表发布仍可能被页面固定引用，必须和当前表单一起清理，而不是只修改草稿。 */
    private List<Publication> readPublications(Connection connection) throws Exception {
        List<Publication> result = new ArrayList<>();
        for (String table : List.of("entity_form_published", "ui_config_release")) {
            boolean form = "entity_form_published".equals(table);
            String query = form
                    ? "SELECT form_id, form_id, snapshot_document, content_hash FROM entity_form_published"
                    : "SELECT id, config_id, snapshot_document, content_hash FROM ui_config_release WHERE config_type = 'LIST'";
            try (var statement = connection.createStatement(); var rows = statement.executeQuery(query)) {
                while (rows.next()) {
                    String label = "V120 " + table + "/" + rows.getString(1);
                    String document = codec.canonicalize(rows.getString(3), label);
                    String originalHash = rows.getString(4);
                    if (!Objects.equals(hash(document), originalHash)) {
                        throw new IllegalStateException(label + " 发布快照哈希不一致，未执行清理");
                    }
                    result.add(new Publication(table, form ? "form_id" : "id", rows.getString(1),
                            new Owner(form ? "FORM" : "LIST", rows.getString(2)), document,
                            originalHash, codec.readObject(document, label)));
                }
            }
        }
        return result;
    }

    /**
     * 先处理被引用的列表，再重算父快照摘要；发布 ID/版本保持不变。
     * 表单引用已使用稳定 formId，只有列表 target.contentHash 需要跟随本次内容清理变化。
     */
    private String resolveHash(Publication publication, Map<String, Publication> lists,
                               Map<String, String> hashes, Set<String> visiting) throws Exception {
        String key = publication.table() + "/" + publication.id();
        if (hashes.containsKey(key)) return hashes.get(key);
        if (!visiting.add(key)) throw new IllegalStateException("V120 关联内容发布存在循环依赖: " + publication.id());
        for (Map<String, Object> config : compositionConfigs(publication.snapshot())) {
            updateTargetHash(config, lists, hashes, visiting);
        }
        String value = hash(canonical(publication.snapshot()));
        visiting.remove(key);
        hashes.put(key, value);
        return value;
    }

    /** 仅替换明确的列表发布坐标，普通字段、接口摘要和组件参数中的同名值不参与替换。 */
    private void updateTargetHash(Map<String, Object> config, Map<String, Publication> lists,
                                  Map<String, String> hashes, Set<String> visiting) throws Exception {
        if (!(config.get("target") instanceof Map<?, ?> rawTarget)) return;
        Map<String, Object> target = mutableMap(rawTarget);
        if (!"LIST".equals(target.get("contentType"))) return;
        Publication dependency = lists.get(target.get("releaseId"));
        if (dependency == null || !Objects.equals(target.get("contentHash"), dependency.originalHash())) return;
        target.put("contentHash", resolveHash(dependency, lists, hashes, visiting));
    }

    /** 只定位发布结构约定的关联内容数组，不递归误删业务字段或接口载荷中的 select/link。 */
    private List<Map<String, Object>> compositionConfigs(Map<String, Object> snapshot) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (snapshot.get("viewCompositions") instanceof List<?> items) {
            for (Object item : items) {
                if (item instanceof Map<?, ?> entry && entry.get("config") instanceof Map<?, ?> config) {
                    result.add(mutableMap(config));
                }
            }
        }
        return result;
    }

    /** 保留新增初值、普通操作和实体关系，只删除本次已下线的配置键与操作值。 */
    private void clean(Map<String, Object> config) {
        if (config.get("actionSettings") instanceof Map<?, ?> rawSettings) {
            Map<String, Object> settings = mutableMap(rawSettings);
            settings.remove("select");
            if (settings.get("create") instanceof Map<?, ?> create) mutableMap(create).remove("associateAfterCreate");
        }
        if (config.get("actions") instanceof List<?> actions) {
            List<Object> kept = new ArrayList<>(actions);
            boolean removed = kept.removeIf(action -> action instanceof String text
                    && RETIRED_ACTIONS.contains(text.trim().toUpperCase(Locale.ROOT)));
            // 只剩退役动作的页面保留查看能力；不会因此授予新增或编辑权限。
            if (removed) config.put("actions", kept.isEmpty() ? List.of("VIEW") : kept);
        }
        if (config.get("presentation") instanceof Map<?, ?> presentation
                && presentation.get("actionBar") instanceof Map<?, ?> actionBar
                && actionBar.get("buttons") instanceof Map<?, ?> buttons) mutableMap(buttons).remove("link");
        removeRetiredActionBindings(config);
    }

    /**
     * 旧标准动作可附加接口处理，动作退役时一起删除，避免它们被识别成同名自定义动作。
     * 真正的自定义接口、取数接口和组件继续保留；只在删除后已无接口时调整展示模式。
     */
    private void removeRetiredActionBindings(Map<String, Object> config) {
        if (!(config.get("specialHandling") instanceof Map<?, ?> rawSpecial)) return;
        Map<String, Object> special = mutableMap(rawSpecial);
        if (!(special.get("actionServices") instanceof List<?> services)) return;
        List<Object> kept = new ArrayList<>(services);
        boolean removed = kept.removeIf(service -> service instanceof Map<?, ?> action
                && action.get("actionKey") instanceof String key
                && RETIRED_ACTIONS.contains(key.trim().toUpperCase(Locale.ROOT)));
        if (!removed) return;
        special.put("actionServices", kept);
        Map<?, ?> readService = special.get("interfaceService") instanceof Map<?, ?> service ? service : Map.of();
        boolean hasReadService = List.of("extensionId", "serviceId", "operationCode").stream()
                .anyMatch(key -> readService.get(key) instanceof String text && !text.isBlank());
        if (kept.isEmpty() && !hasReadService) {
            boolean hasComponent = special.get("customComponent") instanceof Map<?, ?> component
                    && component.get("name") instanceof String name && !name.isBlank();
            special.put("mode", hasComponent ? "CUSTOM_COMPONENT" : "NONE");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableMap(Map<?, ?> value) {
        return (Map<String, Object>) value;
    }

    private String canonical(Map<String, Object> value) throws Exception {
        return codec.canonicalize(json.writeValueAsString(value), "V120 关联内容配置");
    }

    private String hash(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    }
}
