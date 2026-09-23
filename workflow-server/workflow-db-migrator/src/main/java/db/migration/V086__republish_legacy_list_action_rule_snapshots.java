package db.migration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 为仍引用 v1 按钮条件的 ACTIVE 列表发布不可变的 v2 后继版本。
 *
 * <p>V085 只升级了可编辑草稿，已发布快照必须保留原文。这里从当前 ACTIVE
 * 快照派生语义等价的新版本，将旧版本降为 INACTIVE 后原子切换列表配置指针。
 * 新快照使用与 {@code JsonDocumentCodec} 相同的递归 key 排序和 SHA-256 规则，
 * 避免运行时完整性校验把数据库 JSON 重排误判为快照损坏。</p>
 */
public class V086__republish_legacy_list_action_rule_snapshots
        extends BaseJavaMigration {

    private static final TypeReference<LinkedHashMap<String, Object>>
            OBJECT_TYPE = new TypeReference<>() { };
    private static final String MIGRATION_DESCRIPTION =
            "系统迁移：列表按钮条件升级为 v2";
    private static final String DEFAULT_DISABLED_MESSAGE =
            "当前条件不满足，按钮不可操作";
    private static final int MAX_DISABLED_MESSAGE_CODE_POINTS = 300;

    private final ObjectMapper json = new ObjectMapper();
    private final ObjectMapper canonicalJson = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    /**
     * 先完整解析并校验所有候选快照，再执行任何写入，防止坏快照造成半批迁移。
     *
     * @param context 执行上下文，向后续迁移步骤传递身份、配置或状态
     * @throws Exception 下游操作失败时向调用方传递
     */
    @Override
    public void migrate(Context context) throws Exception {
        Connection connection = context.getConnection();
        List<ReleasePlan> plans = buildPlans(connection);
        for (ReleasePlan plan : plans) {
            applyPlan(connection, plan);
        }
    }

    /**
     * MySQL 表均使用 InnoDB；三步状态切换必须处于 Flyway 的同一事务中。
     *
     * @return {@code execute}事务条件成立时为 true，否则为 false
     */
    @Override
    public boolean canExecuteInTransaction() {
        return true;
    }

    /**
     * 读取列表配置实际指向的 ACTIVE 发布，生成不可变的新版本写入计划。
     *
     * @param connection 连接，作为 {@code try} 的输入影响后续处理
     * @return 发布版本方案集合，供调用方遍历或展示
     * @throws Exception 下游操作失败时向调用方传递
     */
    private List<ReleasePlan> buildPlans(Connection connection)
            throws Exception {
        List<ReleasePlan> plans = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT c.id AS config_id,
                       r.id AS release_id,
                       r.version AS release_version,
                       r.snapshot_document,
                       r.content_hash,
                       (SELECT COALESCE(MAX(history.version), 0)
                          FROM ui_config_release history
                         WHERE history.config_type = 'LIST'
                           AND history.config_id = c.id) AS max_version
                  FROM entity_list_config c
                  JOIN ui_config_release r
                    ON r.id = c.active_release_id
                   AND r.config_type = 'LIST'
                   AND r.config_id = c.id
                   AND r.status = 'ACTIVE'
                 WHERE COALESCE(c.deleted, 0) = 0
                 ORDER BY c.id
                """)) {
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String sourceDocument = rows.getString(
                            "snapshot_document");
                    Map<String, Object> snapshot = readSnapshot(
                            sourceDocument,
                            rows.getString("release_id"));
                    String sourceHash = sha256(canonical(snapshot));
                    if (!upgradeLegacyRules(snapshot)) {
                        continue;
                    }

                    // 只从完整性有效的不可变发布派生后继版本；源记录绝不改写。
                    if (!Objects.equals(
                            rows.getString("content_hash"),
                            sourceHash)) {
                        throw new IllegalStateException(
                                "列表发布快照完整性校验失败: "
                                        + rows.getString("release_id"));
                    }

                    String migratedDocument = canonical(snapshot);
                    int nextVersion = Math.max(
                            rows.getInt("release_version"),
                            rows.getInt("max_version")) + 1;
                    plans.add(new ReleasePlan(
                            rows.getString("config_id"),
                            rows.getString("release_id"),
                            rows.getInt("release_version"),
                            uuid(),
                            nextVersion,
                            migratedDocument,
                            sha256(migratedDocument)));
                }
            }
        }
        return plans;
    }

    /**
     * 旧发布降级、新发布创建、所有者指针切换必须全部成功，否则抛错回滚。
     *
     * @param connection 连接，作为 {@code try} 的输入影响后续处理
     * @param plan 执行方案，后续决定操作步骤和校验约束
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
    private void applyPlan(Connection connection, ReleasePlan plan)
            throws SQLException {
        try (PreparedStatement deactivate = connection.prepareStatement(
                """
                UPDATE ui_config_release
                   SET status = 'INACTIVE'
                 WHERE id = ?
                   AND config_type = 'LIST'
                   AND config_id = ?
                   AND version = ?
                   AND status = 'ACTIVE'
                """)) {
            deactivate.setString(1, plan.sourceReleaseId());
            deactivate.setString(2, plan.configId());
            deactivate.setInt(3, plan.sourceVersion());
            requireSingleRow(
                    deactivate.executeUpdate(),
                    "待升级的 ACTIVE 列表发布已发生变化: "
                            + plan.sourceReleaseId());
        }

        try (PreparedStatement insert = connection.prepareStatement(
                """
                INSERT INTO ui_config_release
                    (id, config_type, config_id, version,
                     snapshot_document, content_hash, status,
                     description, published_by, published_at,
                     release_mode, base_release_id, risk_level,
                     rollout_scope, patch_document,
                     override_risk, override_reason)
                VALUES
                    (?, 'LIST', ?, ?, ?, ?, 'ACTIVE', ?, NULL,
                     CURRENT_TIMESTAMP, 'STANDARD', NULL, 'SAFE',
                     NULL, NULL, 0, NULL)
                """)) {
            insert.setString(1, plan.targetReleaseId());
            insert.setString(2, plan.configId());
            insert.setInt(3, plan.targetVersion());
            insert.setString(4, plan.snapshotDocument());
            insert.setString(5, plan.contentHash());
            insert.setString(6, MIGRATION_DESCRIPTION);
            requireSingleRow(
                    insert.executeUpdate(),
                    "无法创建列表按钮条件 v2 发布: " + plan.configId());
        }

        try (PreparedStatement activate = connection.prepareStatement(
                """
                UPDATE entity_list_config
                   SET active_release_id = ?,
                       published_version = ?,
                       update_time = CURRENT_TIMESTAMP
                 WHERE id = ?
                   AND active_release_id = ?
                """)) {
            activate.setString(1, plan.targetReleaseId());
            activate.setInt(2, plan.targetVersion());
            activate.setString(3, plan.configId());
            activate.setString(4, plan.sourceReleaseId());
            requireSingleRow(
                    activate.executeUpdate(),
                    "列表发布指针已发生变化: " + plan.configId());
        }
    }

    /**
     * 升级快照中列表工具栏和行操作按钮；返回是否发现并修改了 v1 规则。
     *
     * @param snapshot 快照，供本方法处理{@code upgrade}旧版规则集合时使用
     * @return {@code upgrade}旧版规则集合条件成立时为 true，否则为 false
     */
    private boolean upgradeLegacyRules(Map<String, Object> snapshot) {
        Object listValue = snapshot.get("list");
        if (!(listValue instanceof Map<?, ?> list)) {
            return false;
        }
        return upgradeButtonArray(list.get("toolbarConfig"))
                | upgradeButtonArray(list.get("rowActionConfig"));
    }

    /**
     * v1 HIDE 迁到 visibleWhen，v1 DISABLE 迁到 enabledWhen；没有 root 的
     * 旧占位对象直接移除，行为与 V085 对草稿的升级保持一致。
     *
     * @param value 待处理{@code upgrade}按钮数组的原始输入，结果供调用方继续使用
     * @return {@code upgrade}按钮数组条件成立时为 true，否则为 false
     */
    private boolean upgradeButtonArray(Object value) {
        if (!(value instanceof List<?> buttons)) {
            return false;
        }
        boolean changed = false;
        for (Object item : buttons) {
            if (!(item instanceof Map<?, ?> rawButton)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> button =
                    (Map<String, Object>) rawButton;
            if (!button.containsKey("availabilityRule")) {
                continue;
            }
            Object ruleValue = button.get("availabilityRule");
            if (!(ruleValue instanceof Map<?, ?> rawRule)) {
                button.remove("availabilityRule");
                changed = true;
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> rule = (Map<String, Object>) rawRule;
            if (!isLegacyVersion(rule.get("version"))) {
                continue;
            }
            Object root = rule.get("root");
            if (root == null) {
                button.remove("availabilityRule");
                changed = true;
                continue;
            }

            Map<String, Object> migrated = new LinkedHashMap<>();
            migrated.put("version", 2);
            String behavior = normalizedText(
                    rule.get("unavailableBehavior"), "HIDE")
                    .toUpperCase(Locale.ROOT);
            if ("DISABLE".equals(behavior)) {
                migrated.put("visibleWhen", null);
                migrated.put("enabledWhen", root);
                migrated.put("disabledMessage", truncateCodePoints(
                        normalizedText(
                                rule.get("message"),
                                DEFAULT_DISABLED_MESSAGE),
                        MAX_DISABLED_MESSAGE_CODE_POINTS));
            } else {
                migrated.put("visibleWhen", root);
                migrated.put("enabledWhen", null);
                migrated.put("disabledMessage", "");
            }
            button.put("availabilityRule", migrated);
            changed = true;
        }
        return changed;
    }

    /**
     * 判断是否旧版版本；判断结果决定调用方的后续分支。
     *
     * @param value 待判断是否旧版版本的原始输入，结果供调用方继续使用
     * @return 旧版版本条件成立时为 true，否则为 false
     */
    private boolean isLegacyVersion(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof Number number) {
            return number.doubleValue() == 1D;
        }
        return "1".equals(String.valueOf(value).trim());
    }

    /**
     * 读取快照；查询结果供调用方展示或继续处理。
     *
     * @param document 文档，作为 {@code json.readValue} 的输入影响后续处理
     * @param releaseId 发布版本ID，后续用于读取快照时定位或关联目标
     * @return 快照键值结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     * @throws Exception 下游操作失败时向调用方传递
     */
    private Map<String, Object> readSnapshot(
            String document,
            String releaseId) throws Exception {
        if (document == null || document.isBlank()) {
            throw new IllegalStateException(
                    "列表发布快照为空: " + releaseId);
        }
        return json.readValue(document, OBJECT_TYPE);
    }

    /**
     * 生成规范文本，供后续匹配或展示。
     *
     * @param value 待处理规范的原始输入，结果供调用方继续使用
     * @return 处理后的规范文本，供调用方比较或展示
     * @throws Exception 下游操作失败时向调用方传递
     */
    private String canonical(Map<String, Object> value) throws Exception {
        return canonicalJson.writeValueAsString(value);
    }

    /**
     * 计算输入内容的 SHA-256 摘要，供后续签名或幂等键使用。
     *
     * @param value 待处理{@code sha256}的原始输入，结果供调用方继续使用
     * @return 处理后的{@code sha256}文本，供调用方比较或展示
     * @throws Exception 下游操作失败时向调用方传递
     */
    private String sha256(String value) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }

    /**
     * 生成规范化文本文本，供后续匹配或展示。
     *
     * @param value 待处理规范化文本的原始输入，结果供调用方继续使用
     * @param defaultValue 首选值不可用时采用的兜底值，保证后续处理有稳定输入
     * @return 处理后的规范化文本文本，供调用方比较或展示
     */
    private String normalizedText(Object value, String defaultValue) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    /**
     * 生成{@code truncate}编码{@code points}文本，供后续匹配或展示。
     *
     * @param value 待处理{@code truncate}编码{@code points}的原始输入，结果供调用方继续使用
     * @param maxCodePoints 最大编码{@code points}，作为 {@code value.offsetByCodePoints} 的输入影响后续处理
     * @return 处理后的{@code truncate}编码{@code points}文本，供调用方比较或展示
     */
    private String truncateCodePoints(String value, int maxCodePoints) {
        if (value.codePointCount(0, value.length()) <= maxCodePoints) {
            return value;
        }
        int end = value.offsetByCodePoints(0, maxCodePoints);
        return value.substring(0, end);
    }

    /**
     * 校验并获取{@code single}行；不满足约束时阻止后续处理。
     *
     * @param rows 行，供本方法校验并获取{@code single}行时使用
     * @param message 消息，作为 {@code IllegalStateException} 的输入影响后续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private void requireSingleRow(int rows, String message) {
        if (rows != 1) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * 生成{@code uuid}文本，供后续匹配或展示。
     *
     * @return 处理后的{@code uuid}文本，供调用方比较或展示
     */
    private String uuid() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 封装发布版本方案的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param configId 配置ID，后续用于处理发布版本方案时定位或关联目标
     * @param sourceReleaseId 来源发布版本ID，后续用于处理发布版本方案时定位或关联目标
     * @param sourceVersion 来源版本，保存在对象中供后续校验、查询或展示
     * @param targetReleaseId 目标发布版本ID，后续用于处理发布版本方案时定位或关联目标
     * @param targetVersion 目标版本，保存在对象中供后续校验、查询或展示
     * @param snapshotDocument 快照文档，保存在对象中供后续校验、查询或展示
     * @param contentHash 内容哈希，保存在对象中供后续校验、查询或展示
     */
    private record ReleasePlan(
            String configId,
            String sourceReleaseId,
            int sourceVersion,
            String targetReleaseId,
            int targetVersion,
            String snapshotDocument,
            String contentHash) {
    }
}
