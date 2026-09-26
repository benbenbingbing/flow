package db.migration;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.*;
import java.util.*;

/** 将三个布尔设置按作用域和归属合并为稀疏 JSON，缺失字段继续继承，不预建用户记录。 */
public class V108__merge_user_interface_preferences extends BaseJavaMigration {
    private static final String KEY = "ui.user_preferences";
    private static final Map<String, String> LEGACY_FIELDS = Map.of(
            "ui.entity_design.field_types_collapsed", "fieldTypesCollapsed",
            "ui.layout.sidebar_collapsed", "sidebarCollapsed", "ui.layout.tabs_enabled", "tabsEnabled");
    private static final String REMARK = "字段类型面板收起、左侧主菜单收起和顶部多标签页统一存储；按字段优先使用个人配置，缺失字段继承系统默认值。";
    private final ObjectMapper json = new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    private record Owner(String scope, String id) { }
    private record Row(String id, String key, String type, String value, long version,
                       String createdBy, String updatedBy, Timestamp createdAt, Timestamp updatedAt) { }
    private record Plan(Owner owner, List<Row> legacy, Row current, ObjectNode value) { }

    /**
     * 全部数据校验后才执行写入及旧行删除；非法值保留原数据并中止，禁止猜测用户选择。
     * 只执行 DML；当迁移器未提供事务时主动建立事务，保证插入和删除不可部分提交。
     */
    @Override public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        boolean ownTransaction = connection.getAutoCommit();
        if (ownTransaction) connection.setAutoCommit(false);
        try {
            List<Plan> plans = plan(connection);
            for (Plan plan : plans) apply(connection, plan);
            if (ownTransaction) connection.commit();
        } catch (Exception error) {
            if (ownTransaction) connection.rollback();
            throw error;
        } finally {
            if (ownTransaction) connection.setAutoCommit(true);
        }
    }

    /** 仅聚合实际存在的字段；已存在的新配置优先，支持故障重试而不覆盖后续设置。 */
    private List<Plan> plan(Connection connection) throws Exception {
        Map<Owner, List<Row>> owners = new LinkedHashMap<>();
        try (var query = connection.prepareStatement("SELECT * FROM sys_global_setting WHERE setting_key IN (?, ?, ?, ?)")) {
            query.setString(1, KEY);
            int parameter = 2;
            for (String legacy : LEGACY_FIELDS.keySet()) query.setString(parameter++, legacy);
            try (var rows = query.executeQuery()) {
                while (rows.next()) {
                    Owner owner = new Owner(rows.getString("scope_type"), rows.getString("owner_id"));
                    owners.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(new Row(rows.getString("id"),
                            rows.getString("setting_key"), rows.getString("setting_value_type"), rows.getString("setting_value"),
                            rows.getLong("version"), rows.getString("created_by"), rows.getString("updated_by"),
                            rows.getTimestamp("create_time"), rows.getTimestamp("update_time")));
                }
            }
        }
        List<Plan> plans = new ArrayList<>();
        for (var entry : owners.entrySet()) {
            List<Row> legacy = entry.getValue().stream().filter(row -> LEGACY_FIELDS.containsKey(row.key())).toList();
            if (legacy.isEmpty()) continue;
            Row current = entry.getValue().stream().filter(row -> KEY.equals(row.key())).findFirst().orElse(null);
            ObjectNode merged = json.createObjectNode();
            for (Row row : legacy) {
                JsonNode value = parse(row);
                if (!"BOOLEAN".equals(row.type()) || !value.isBoolean()) throw invalid(row);
                merged.set(LEGACY_FIELDS.get(row.key()), value);
            }
            if (current != null) {
                JsonNode value = parse(current);
                if (!"JSON".equals(current.type()) || !value.isObject()) throw invalid(current);
                for (var fields = value.fields(); fields.hasNext();) {
                    var field = fields.next();
                    if (!LEGACY_FIELDS.containsValue(field.getKey()) || !field.getValue().isBoolean()) throw invalid(current);
                }
                merged.setAll((ObjectNode) value);
            }
            plans.add(new Plan(entry.getKey(), legacy, current, merged));
        }
        return plans;
    }

    private JsonNode parse(Row row) throws SQLException {
        try {
            JsonNode value = json.readTree(row.value());
            if (value == null) throw invalid(row);
            return value;
        } catch (Exception error) {
            // 不在日志中打印设置内容；记录 ID 足以定位需修复的数据。
            throw invalid(row);
        }
    }

    /** 新记录保留原配置的审计时间；更新及删除均检查版本，检测迁移期间的并发写入。 */
    private void apply(Connection connection, Plan plan) throws SQLException {
        if (plan.current() == null) {
            Row first = Collections.min(plan.legacy(), Comparator.comparing(Row::createdAt));
            Row latest = Collections.max(plan.legacy(), Comparator.comparing(Row::updatedAt));
            try (var insert = connection.prepareStatement("INSERT INTO sys_global_setting "
                    + "(id, scope_type, owner_id, setting_key, name, setting_value_type, setting_value, remark, version, created_by, updated_by, create_time, update_time) "
                    + "VALUES (?, ?, ?, ?, ?, 'JSON', ?, ?, 0, ?, ?, ?, ?)")) {
                insert.setString(1, UUID.randomUUID().toString()); insert.setString(2, plan.owner().scope());
                insert.setString(3, plan.owner().id()); insert.setString(4, KEY); insert.setString(5, "用户界面偏好");
                insert.setString(6, plan.value().toString()); insert.setString(7, REMARK);
                insert.setString(8, first.createdBy()); insert.setString(9, latest.updatedBy());
                insert.setTimestamp(10, first.createdAt()); insert.setTimestamp(11, latest.updatedAt());
                insert.executeUpdate();
            }
        } else {
            try (var update = connection.prepareStatement("UPDATE sys_global_setting SET setting_value = ?, version = version + 1 WHERE id = ? AND version = ?")) {
                update.setString(1, plan.value().toString()); update.setString(2, plan.current().id());
                update.setLong(3, plan.current().version());
                if (update.executeUpdate() != 1) throw new SQLException("V108: 合并目标发生并发修改");
            }
        }
        for (Row row : plan.legacy()) {
            try (var delete = connection.prepareStatement("DELETE FROM sys_global_setting WHERE id = ? AND version = ?")) {
                delete.setString(1, row.id()); delete.setLong(2, row.version());
                if (delete.executeUpdate() != 1) throw new SQLException("V108: 旧设置发生并发修改，合并已回滚");
            }
        }
    }

    private SQLException invalid(Row row) {
        return new SQLException("V108: 设置记录 " + row.id() + " 的格式不合法；请核验原值后重试，未删除旧配置");
    }
}
