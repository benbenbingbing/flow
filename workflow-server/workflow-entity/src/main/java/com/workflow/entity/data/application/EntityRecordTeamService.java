package com.workflow.entity.data.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 实体数据参与团队服务，负责维护记录级参与事件表与团队可见性权限范围。
 *
 * <p>为每个动态实体维护 _team 事件表，记录创建、提交、流程操作等参与动作，
 * 并根据发布快照的团队可见性级别生成 SQL 条件，叠加到数据范围权限计算中。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityRecordTeamService {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private final JdbcTemplate jdbcTemplate;
    private final EntityPhysicalTableResolver tableResolver;
    private final EntityPublishedSnapshotService snapshotService;

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
        jdbcTemplate.execute("""
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
        EntityPublishedSnapshot snapshot;
        try {
            snapshot = snapshotService.getLatestByEntityCode(entityCode);
        } catch (RuntimeException exception) {
            return;
        }
        String tableName = teamTableName(entityCode);
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
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                tableName);
        if (count == null || count == 0) {
            log.error("实体参与团队表不存在: entityCode={}, tableName={}", entityCode, tableName);
            return TeamPermission.disabled();
        }
        String escapedUserId = userId.replace("'", "''");
        return new TeamPermission(
                true,
                snapshot.getTeamVisibilityLevel() == null
                        ? EntityDefinition.TeamVisibilityLevel.ADDITIVE
                        : snapshot.getTeamVisibilityLevel(),
                "EXISTS (SELECT 1 FROM `" + tableName + "` team "
                        + "WHERE team.record_id = `" + tableResolver.resolve(entityCode)
                        + "`.id AND team.user_id = '" + escapedUserId + "')");
    }

    private String checkedIdentifier(String value) {
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("非法参与团队表名: " + value);
        }
        return value;
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
            String sqlCondition) {
        public static TeamPermission disabled() {
            return new TeamPermission(
                    false,
                    EntityDefinition.TeamVisibilityLevel.ADDITIVE,
                    null);
        }
    }
}
