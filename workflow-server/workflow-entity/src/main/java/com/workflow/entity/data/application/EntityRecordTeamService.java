package com.workflow.entity.data.application;

import com.workflow.core.logging.LogValue;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 实体数据参与团队服务，负责维护记录级参与事件表与团队可见性权限范围。
 *
 * <p>为每个动态实体维护 _team 事件表，记录创建、提交、流程操作等参与动作，
 * 并根据发布快照的团队可见性级别生成 SQL 条件，叠加到数据范围权限计算中。</p>
 */
@Slf4j
@Service
public class EntityRecordTeamService {

    private final JdbcTemplate jdbcTemplate;
    private final EntityPhysicalTableResolver tableResolver;
    private final EntityPublishedSnapshotService snapshotService;
    private final SchemaDdlExecutor schemaDdlExecutor;

    @Autowired
    public EntityRecordTeamService(
            JdbcTemplate jdbcTemplate,
            EntityPhysicalTableResolver tableResolver,
            EntityPublishedSnapshotService snapshotService,
            SchemaDdlExecutor schemaDdlExecutor) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableResolver = tableResolver;
        this.snapshotService = snapshotService;
        this.schemaDdlExecutor = schemaDdlExecutor;
    }

    public EntityRecordTeamService(
            JdbcTemplate jdbcTemplate,
            EntityPhysicalTableResolver tableResolver,
            EntityPublishedSnapshotService snapshotService) {
        this(jdbcTemplate, tableResolver, snapshotService, jdbcTemplate::execute);
    }

    /**
     * 解析实体定义对应的参与团队表名。
     *
     * @param definition 实体定义
     * @return 参与团队表名
     */
    public String teamTableName(EntityDefinition definition) {
        return checkedIdentifier(tableResolver.resolve(definition) + "_team");
    }

    /**
     * 解析实体编码对应的参与团队表名。
     *
     * @param entityCode 实体编码
     * @return 参与团队表名
     */
    public String teamTableName(String entityCode) {
        return checkedIdentifier(tableResolver.resolve(entityCode) + "_team");
    }

    /**
     * 确保实体的参与团队表存在，不存在则创建。
     *
     * @param definition 实体定义
     */
    @Transactional(rollbackFor = Exception.class)
    public void ensureTeamTable(EntityDefinition definition) {
        String tableName = teamTableName(definition);
        schemaDdlExecutor.execute("""
                CREATE TABLE IF NOT EXISTS `%s` (
                  `id` VARCHAR(64) NOT NULL COMMENT '参与事件ID',
                  `record_id` VARCHAR(64) NOT NULL COMMENT '业务记录ID',
                  `user_id` VARCHAR(64) NOT NULL COMMENT '参与用户ID',
                  `action_type` VARCHAR(50) NOT NULL COMMENT '参与动作类型',
                  `action_description` VARCHAR(500) DEFAULT NULL COMMENT '参与动作说明',
                  `process_instance_id` VARCHAR(64) DEFAULT NULL COMMENT '流程实例ID',
                  `process_task_id` VARCHAR(64) DEFAULT NULL COMMENT '流程任务ID',
                  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '参与事件入库时间',
                  PRIMARY KEY (`id`),
                  KEY `idx_team_user_record` (`user_id`, `record_id`),
                  KEY `idx_team_record_time` (`record_id`, `create_time`),
                  KEY `idx_team_process_task` (`process_instance_id`, `process_task_id`)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                  COMMENT='业务数据参与团队事件';
                """.formatted(tableName));
    }

    /**
     * 记录一条参与团队事件，系统用户与空记录ID被忽略。
     *
     * @param entityCode        实体编码
     * @param recordId          业务记录ID
     * @param actionType        参与动作类型
     * @param actionDescription  参与动作说明
     * @param processInstanceId 流程实例ID
     * @param processTaskId     流程任务ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void record(
            String entityCode,
            String recordId,
            String actionType,
            String actionDescription,
            String processInstanceId,
            String processTaskId) {
        String userId = UserContext.getUserId();
        if (!StringUtils.hasText(userId) || "system".equalsIgnoreCase(userId)
                || !StringUtils.hasText(recordId)) {
            return;
        }
        // 参与事件只记录已经发生的动作，不依赖实体发布快照。
        // 旧逻辑在快照缺失时静默跳过，审批人办理后仍不会进入 team 表。
        String tableName = teamTableName(entityCode);
        if (!teamTableExists(tableName)) {
            log.error("实体参与团队表不存在，跳过记录: entityCode={}, tableName={}",
                    LogValue.safe(entityCode), LogValue.safe(tableName));
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO `" + tableName + "` "
                        + "(id, record_id, user_id, action_type, action_description, "
                        + "process_instance_id, process_task_id, create_time) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, NOW())",
                UUID.randomUUID().toString().replace("-", ""),
                recordId,
                userId,
                normalizedAction(actionType),
                trim(actionDescription, 500),
                blankToNull(processInstanceId),
                blankToNull(processTaskId));
    }

    /**
     * 编译「当前用户是相关人」范围 SQL。
     * 只认 _team 已发生的参与事件；流程办理人可能写入用户 ID 或用户名，因此两者都匹配。
     *
     * @param entityCode 实体编码
     * @param userId     当前用户 ID
     * @return EXISTS 条件；表不存在或参数非法时返回 1=0
     */
    public String relatedPeopleSql(String entityCode, String userId) {
        return relatedPeopleSql(entityCode, userId, null);
    }

    /**
     * 编译「当前用户是相关人」范围 SQL，同时匹配用户 ID 与用户名。
     *
     * <p>team.user_id 在创建/编辑时写 UserContext 用户 ID，流程异步动作可能写入用户名。
     * 只比其中一个会漏掉历史参与记录。</p>
     *
     * @param entityCode 实体编码
     * @param userId     当前用户 ID
     * @param username   当前用户名，可为空
     * @return EXISTS 条件；表不存在或身份为空时返回 1=0
     */
    public String relatedPeopleSql(String entityCode, String userId, String username) {
        String identitySql = identityInSql("team.user_id", userId, username);
        if (!StringUtils.hasText(entityCode) || identitySql == null) {
            return "1=0";
        }
        String tableName = teamTableName(entityCode);
        if (!teamTableExists(tableName)) {
            return "1=0";
        }
        return "EXISTS (SELECT 1 FROM `" + tableName + "` team "
                + "WHERE team.record_id = `"
                + checkedIdentifier(tableResolver.resolve(entityCode))
                + "`.id AND " + identitySql + ")";
    }

    /**
     * 计算用户对实体的团队可见性权限，返回是否启用、级别和 SQL 条件。
     *
     * @param entityCode 实体编码
     * @param userId     用户ID
     * @return 团队权限结果，未启用或表不存在时返回 disabled
     */
    public TeamPermission teamPermission(String entityCode, String userId) {
        if (!StringUtils.hasText(userId)) {
            return TeamPermission.disabled();
        }
        EntityPublishedSnapshot snapshot;
        try {
            snapshot = snapshotService.getLatestByEntityCode(entityCode);
        } catch (RuntimeException exception) {
            return TeamPermission.disabled();
        }
        if (!Boolean.TRUE.equals(snapshot.getTeamVisibilityEnabled())) {
            return TeamPermission.disabled();
        }
        String tableName = teamTableName(entityCode);
        if (!teamTableExists(tableName)) {
            log.error("实体参与团队表不存在: entityCode={}, tableName={}",
                    LogValue.safe(entityCode), LogValue.safe(tableName));
            return TeamPermission.disabled();
        }
        return new TeamPermission(
                true,
                snapshot.getTeamVisibilityLevel() == null
                        ? EntityDefinition.TeamVisibilityLevel.ADDITIVE
                        : snapshot.getTeamVisibilityLevel(),
                "EXISTS (SELECT 1 FROM `" + tableName + "` team "
                        + "WHERE team.record_id = `" + tableResolver.resolve(entityCode)
                        + "`.id AND team.user_id = #{permissionParameters.teamUserId})",
                Map.of("teamUserId", userId));
    }

    private String checkedIdentifier(String value) {
        return SqlIdentifierPolicy.validate(value);
    }

    private String normalizedAction(String value) {
        String normalized = StringUtils.hasText(value) ? value.trim().toUpperCase() : "OPERATE";
        return normalized.length() > 50 ? normalized.substring(0, 50) : normalized;
    }

    private String trim(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > maxLength ? trimmed.substring(0, maxLength) : trimmed;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private boolean teamTableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName);
        return count != null && count > 0;
    }

    private String escapeLiteral(String input) {
        return input == null ? "" : input.replace("'", "''");
    }

    /**
     * 把用户 ID、用户名编成 IN 条件。流程任务常用用户名，实体写入常用用户 ID。
     */
    private String identityInSql(String column, String userId, String username) {
        LinkedHashSet<String> identities = new LinkedHashSet<>();
        if (StringUtils.hasText(userId)) {
            identities.add(userId);
        }
        if (StringUtils.hasText(username)) {
            identities.add(username);
        }
        if (identities.isEmpty()) {
            return null;
        }
        return column + " IN (" + identities.stream()
                .map(this::escapeLiteral)
                .map(value -> "'" + value + "'")
                .collect(Collectors.joining(",")) + ")";
    }

    /**
     * 团队可见性权限结果，包含是否启用、可见性级别和生成的 SQL 条件。
     *
     * @param enabled       是否启用团队可见性
     * @param level          可见性级别
     * @param sqlCondition  叠加到数据范围的 SQL 条件，未启用时为 null
     */
    public record TeamPermission(
            boolean enabled,
            EntityDefinition.TeamVisibilityLevel level,
            String sqlCondition,
            Map<String, Object> sqlParameters) {
        public static TeamPermission disabled() {
            return new TeamPermission(
                    false,
                    EntityDefinition.TeamVisibilityLevel.ADDITIVE,
                    null,
                    Map.of());
        }
    }
}
