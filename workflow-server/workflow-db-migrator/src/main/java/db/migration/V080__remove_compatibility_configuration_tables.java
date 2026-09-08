package db.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.*;
import java.util.*;

/**
 * 将仍使用的草稿和运行策略转到节点、独立变更策略后，移除四张兼容表。
 * 所有输入先解析并生成写入计划；无效 JSON 或关联会在执行 DROP 前中止迁移。
 * 已发布的数据版本和 UI 快照保持原文，避免改变历史记录的运行依据。
 */
public class V080__remove_compatibility_configuration_tables extends BaseJavaMigration {
    private final ObjectMapper json = new ObjectMapper();
    private final List<Write> writes = new ArrayList<>();

    @Override
    public void migrate(Context context) throws Exception {
        writes.clear();
        Connection connection = context.getConnection();
        migrateForms(connection);
        migrateVersionPolicies(connection);
        for (Write write : writes) {
            try (PreparedStatement statement = connection.prepareStatement(write.sql())) {
                for (int i = 0; i < write.values().length; i++) statement.setObject(i + 1, write.values()[i]);
                statement.executeUpdate();
            }
        }
        // MySQL 的 DDL 会隐式提交；把删除放在数据转换成功之后，禁止先删表再尝试恢复数据。
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE entity_version_step, entity_change_target_binding, entity_version_scenario, entity_form_field");
        }
    }

    /** 节点显式属性优先；只有旧字段尚未形成字段节点时才建立节点。 */
    private void migrateForms(Connection connection) throws Exception {
        Map<String, List<Map<String, Object>>> nodesByForm = group(rows(connection,
                "SELECT * FROM entity_form_node WHERE deleted = 0"), "form_id");
        Map<String, List<Map<String, Object>>> fieldsByForm = group(rows(connection,
                "SELECT f.*, COALESCE(NULLIF(f.field_code, ''), e.field_code) resolved_code "
                        + "FROM entity_form_field f LEFT JOIN entity_field e ON e.id = f.field_id "
                        + "WHERE COALESCE(f.deleted,0)=0 ORDER BY f.form_id, f.sort_order, f.id"), "form_id");
        for (var entry : fieldsByForm.entrySet()) {
            List<Map<String, Object>> nodes = nodesByForm.getOrDefault(entry.getKey(), List.of());
            boolean hasFields = nodes.stream().anyMatch(this::fieldNode);
            long order = 0;
            for (Map<String, Object> field : entry.getValue()) {
                String code = string(field.get("resolved_code"));
                if (code == null || code.isBlank()) throw new IllegalStateException("旧表单字段无法解析编码: " + field.get("id"));
                Map<String, Object> node = null;
                for (Map<String, Object> candidate : nodes) {
                    if (!fieldNode(candidate)) continue;
                    Map<String, Object> props = object(candidate.get("props_document"));
                    if (Objects.equals(candidate.get("id"), field.get("id"))
                            || code.equals(string(props.getOrDefault("fieldCode", candidate.get("node_key"))))) {
                        node = candidate;
                        break;
                    }
                }
                if (node == null && hasFields) continue;
                Map<String, Object> props = new LinkedHashMap<>();
                String[][] columns = {{"field_id","fieldId"},{"resolved_code","fieldCode"},
                        {"field_name","fieldName"},{"field_type","fieldType"},{"component_type","componentType"},
                        {"default_value","defaultValue"},{"placeholder","placeholder"},{"grid_span","gridSpan"}};
                for (String[] column : columns) props.put(column[1], field.get(column[0]));
                props.put("label", field.get("field_label") == null ? field.get("field_name") : field.get("field_label"));
                props.put("required", flag(field.get("is_required")));
                props.put("readonly", flag(field.get("is_readonly")));
                props.put("hidden", flag(field.get("is_hidden")));
                if (present(field.get("component_props"))) props.put("componentProps", parse(field.get("component_props")));
                Map<String, Object> rules = new LinkedHashMap<>();
                if (present(field.get("validation_rules"))) rules.put("validation", parse(field.get("validation_rules")));
                if (present(field.get("extension_config"))) rules.put("extension", parse(field.get("extension_config")));
                if (node == null) {
                    queue("INSERT INTO entity_form_node (id,form_id,node_key,node_type,binding_type,binding_ref,props_document,rules_document,order_key,revision,deleted) VALUES (?,?,?,?,?,?,?,?,?,1,0)",
                            field.get("id"), entry.getKey(), code,
                            "SUB_FORM".equals(field.get("field_type")) ? "SUB_FORM" : "FIELD",
                            "ENTITY_FIELD", code, encode(props), encode(rules), ++order * 1024);
                } else {
                    props.putAll(object(node.get("props_document")));
                    rules.putAll(object(node.get("rules_document")));
                    queue("UPDATE entity_form_node SET props_document=?, rules_document=?, revision=revision+1 WHERE id=?",
                            encode(props), encode(rules), node.get("id"));
                }
            }
            queue("UPDATE entity_form SET draft_hash=NULL WHERE id=?", entry.getKey());
        }
    }

    /** 草稿迁到 V2；旧发布中的执行规则独立发布，不能将未发布草稿激活为运行配置。 */
    private void migrateVersionPolicies(Connection connection) throws Exception {
        Map<String, List<Map<String, Object>>> scenarios = group(rows(connection, "SELECT * FROM entity_version_scenario ORDER BY priority DESC,id"), "config_id");
        Map<String, List<Map<String, Object>>> steps = group(rows(connection, "SELECT * FROM entity_version_step ORDER BY sort_order,id"), "config_id");
        Map<String, List<Map<String, Object>>> targets = group(rows(connection, "SELECT * FROM entity_change_target_binding ORDER BY id"), "config_id");
        Map<String, Map<String, Object>> releases = index(rows(connection, "SELECT * FROM entity_version_config_release"), "id");
        Map<String, Map<String, Object>> nativeConfigs = index(rows(connection, "SELECT * FROM entity_mutation_policy_config WHERE deleted=0"), "entity_code");
        Map<String, List<Map<String, Object>>> nativeReleases = group(rows(connection, "SELECT config_id,version FROM entity_mutation_policy_release"), "config_id");
        for (Map<String, Object> config : rows(connection,
                "SELECT c.*, e.entity_name FROM entity_version_config c LEFT JOIN entity_definition e ON e.id=c.entity_id WHERE c.deleted=0")) {
            String id = string(config.get("id"));
            Map<String, Object> old = legacyDocument(config, scenarios.getOrDefault(id,List.of()), steps.getOrDefault(id,List.of()), targets.getOrDefault(id,List.of()));
            Map<String, Object> draft = object(config.get("draft_document"));
            Map<String, Object> release = releases.get(string(config.get("active_release_id")));
            if (present(config.get("active_release_id")) && release == null) throw new IllegalStateException("数据版本激活发布不存在: " + id);
            Map<String, Object> published = release == null ? Map.of() : object(release.get("config_document"));
            Map<String, Object> policyDraft = hasBehavior(old) ? old : hasBehavior(draft) ? draft : published;
            Map<String, Object> nativeConfig = nativeConfigs.get(string(config.get("entity_code")));
            if (nativeConfig == null && (hasBehavior(policyDraft) || hasBehavior(published))) {
                String policyId = uuid();
                Map<String, Object> document = mutationDocument(config, policyDraft);
                queue("INSERT INTO entity_mutation_policy_config (id,entity_id,entity_code,enabled,draft_document,revision,status,migration_state) VALUES (?,?,?,?,?,1,'DRAFT','MIGRATED')",
                        policyId, config.get("entity_id"), config.get("entity_code"), flag(document.get("enabled")), encode(document));
                nativeConfig = new LinkedHashMap<>();
                nativeConfig.put("id", policyId);
            }
            if (nativeConfig != null && !present(nativeConfig.get("active_release_id")) && hasBehavior(published)) {
                String policyId = string(nativeConfig.get("id"));
                int version = nativeReleases.getOrDefault(policyId, List.of()).stream()
                        .mapToInt(row -> ((Number) row.get("version")).intValue()).max().orElse(0) + 1;
                String releaseId = uuid();
                Map<String, Object> document = mutationDocument(config, published);
                queue("INSERT INTO entity_mutation_policy_release (id,config_id,version,config_document,published_by,published_by_name,publish_time) VALUES (?,?,?,?,?,?,?)",
                        releaseId, policyId, version, encode(document), release.get("published_by"), release.get("published_by_name"), release.get("publish_time"));
                // 已有独立草稿不被覆盖；只接续迁移前实际使用的旧发布。
                queue("UPDATE entity_mutation_policy_config SET active_release_id=?,status='PUBLISHED',migration_state='MIGRATED' WHERE id=?",
                        releaseId, policyId);
            }
            if (draft.isEmpty() || !Objects.equals(((Number) draft.getOrDefault("schemaVersion",1)).intValue(), 2)) {
                Map<String, Object> source = draft.isEmpty() ? old : draft;
                draft = new LinkedHashMap<>(source);
                List<Map<String, Object>> triggers = new ArrayList<>();
                for (Map<String, Object> scenario : list(source.get("scenarios"))) {
                    Map<String, Object> trigger = new LinkedHashMap<>(scenario);
                    trigger.put("triggerCode", trigger.remove("scenarioCode"));
                    trigger.put("triggerName", trigger.remove("scenarioName"));
                    trigger.put("triggerType", "ROOT_MUTATION");
                    triggers.add(trigger);
                }
                draft.put("triggers", triggers);
                draft.put("snapshotScope", Map.of("root", Map.of("entityCode", config.get("entity_code")), "relations", List.of()));
                draft.put("schemaVersion", 2);
                draft.put("migrationState", "REVIEW_REQUIRED");
            }
            draft.put("scenarios", List.of());
            draft.put("steps", List.of());
            draft.put("targetBindings", List.of());
            queue("UPDATE entity_version_config SET contract_version=2,draft_document=?,migration_state=? WHERE id=?",
                    encode(draft), draft.getOrDefault("migrationState",config.get("migration_state")), id);
        }
    }

    private Map<String, Object> legacyDocument(Map<String, Object> config, List<Map<String, Object>> scenarios,
            List<Map<String, Object>> steps, List<Map<String, Object>> targets) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("enabled", flag(config.get("enabled")));
        Map<String, Object> scenarioCodes = new HashMap<>();
        List<Map<String, Object>> rules = new ArrayList<>();
        for (Map<String, Object> row : scenarios) {
            Map<String, Object> rule = columns(row, "id", "scenario_code", "scenario_name", "priority", "version_title_template");
            scenarioCodes.put(string(row.get("id")), row.get("scenario_code"));
            rule.put("sourceTypes", array(row.get("source_types_document")));
            rule.put("operationTypes", array(row.get("operation_types_document")));
            rule.put("businessIntents", array(row.get("business_intents_document")));
            rule.put("condition", object(row.get("condition_document")));
            rule.put("enabled", flag(row.get("enabled")));
            rules.add(rule);
        }
        List<Map<String, Object>> actions = new ArrayList<>();
        for (Map<String, Object> row : steps) {
            Map<String, Object> action = columns(row, "id", "phase", "step_type", "step_name", "provider_code", "sort_order");
            if (present(row.get("scenario_id")) && !scenarioCodes.containsKey(string(row.get("scenario_id")))) throw new IllegalStateException("旧变更步骤的场景不存在: " + row.get("id"));
            action.put("scenarioCode", scenarioCodes.get(string(row.get("scenario_id"))));
            action.put("config", object(row.get("config_document")));
            action.put("enabled", flag(row.get("enabled")));
            actions.add(action);
        }
        List<Map<String, Object>> bindings = new ArrayList<>();
        for (Map<String, Object> row : targets) {
            Map<String, Object> binding = columns(row, "id", "binding_code", "binding_name", "source_entity_code", "target_entity_code", "resolver_type", "resolver_code", "apply_strategy");
            binding.put("resolverConfig", object(row.get("resolver_config_document")));
            binding.put("fieldMapping", object(row.get("mapping_document")));
            binding.put("enabled", flag(row.get("enabled")));
            bindings.add(binding);
        }
        result.put("scenarios", rules);
        result.put("steps", actions);
        result.put("targetBindings", bindings);
        return result;
    }

    private Map<String, Object> mutationDocument(Map<String, Object> config, Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schemaVersion", 1);
        result.put("entityId", config.get("entity_id"));
        result.put("entityCode", config.get("entity_code"));
        result.put("entityName", config.get("entity_name"));
        result.put("enabled", flag(source.get("enabled")));
        result.put("migrationState", "MIGRATED");
        List<Map<String,Object>> steps = list(source.get("steps")).stream().filter(Objects::nonNull).toList();
        Set<String> referencedRules = new HashSet<>();
        for (Map<String,Object> step : steps) {
            if (present(step.get("scenarioCode"))) referencedRules.add(string(step.get("scenarioCode")).trim().toUpperCase(Locale.ROOT));
        }
        // 与原独立策略的转换规则一致：纯版本采集场景不能成为变更规则并抢先匹配。
        result.put("scenarios", list(source.get("scenarios")).stream().filter(Objects::nonNull)
                .filter(rule -> present(rule.get("scenarioCode")) && referencedRules.contains(string(rule.get("scenarioCode")).trim().toUpperCase(Locale.ROOT)))
                .toList());
        result.put("steps", steps);
        result.put("targetBindings", list(source.get("targetBindings")).stream().filter(Objects::nonNull).toList());
        return result;
    }

    private Map<String, Object> columns(Map<String, Object> row, String... names) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String name : names) {
            StringBuilder key = new StringBuilder();
            boolean upper = false;
            for (char c : name.toCharArray()) {
                if (c == '_') { upper = true; continue; }
                key.append(upper ? Character.toUpperCase(c) : c); upper = false;
            }
            result.put(key.toString(), row.get(name));
        }
        return result;
    }
    private List<Map<String, Object>> rows(Connection connection, String sql) throws SQLException {
        List<Map<String, Object>> result = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rs = statement.executeQuery(sql)) {
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i=1; i<=rs.getMetaData().getColumnCount(); i++) row.put(rs.getMetaData().getColumnLabel(i).toLowerCase(Locale.ROOT), rs.getObject(i));
                result.add(row);
            }
        }
        return result;
    }
    private Map<String, List<Map<String, Object>>> group(List<Map<String, Object>> rows, String key) {
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) result.computeIfAbsent(string(row.get(key)), ignored -> new ArrayList<>()).add(row);
        return result;
    }
    private Map<String, Map<String, Object>> index(List<Map<String, Object>> rows, String key) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> row : rows) result.put(string(row.get(key)), row);
        return result;
    }
    private Object parse(Object value) throws Exception { return json.readValue(string(value), Object.class); }
    private Map<String, Object> object(Object value) throws Exception {
        return !present(value) ? new LinkedHashMap<>() : json.readValue(string(value), new TypeReference<LinkedHashMap<String,Object>>() {});
    }
    private Object array(Object value) throws Exception { return !present(value) ? List.of() : json.readValue(string(value), List.class); }
    @SuppressWarnings("unchecked")
    private List<Map<String,Object>> list(Object value) { return value == null ? List.of() : (List<Map<String,Object>>) value; }
    private boolean hasBehavior(Map<String,Object> value) { return !list(value.get("steps")).isEmpty() || !list(value.get("targetBindings")).isEmpty(); }
    private boolean fieldNode(Map<String,Object> value) { return Set.of("FIELD","SUB_FORM","REPEATER").contains(value.get("node_type")); }
    private String encode(Object value) throws Exception { return json.writeValueAsString(value); }
    private String string(Object value) { return value == null ? null : String.valueOf(value); }
    private boolean present(Object value) { return value != null && !String.valueOf(value).isBlank(); }
    private boolean flag(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number n && n.intValue() == 1 || "1".equals(value); }
    private String uuid() { return UUID.randomUUID().toString().replace("-", ""); }
    private void queue(String sql, Object... values) { writes.add(new Write(sql, values)); }
    private record Write(String sql, Object[] values) {}
}
