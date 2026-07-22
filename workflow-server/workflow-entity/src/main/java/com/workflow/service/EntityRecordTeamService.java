package com.workflow.service;

import com.workflow.common.UserContext;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.publish.EntityPublishedSnapshot;
import com.workflow.entity.publish.EntityPublishedSnapshotService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class EntityRecordTeamService {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z][A-Za-z0-9_]*");

    private final JdbcTemplate jdbcTemplate;
    private final EntityPhysicalTableResolver tableResolver;
    private final EntityPublishedSnapshotService snapshotService;

    public String teamTableName(EntityDefinition definition) {
        return checkedIdentifier(tableResolver.resolve(definition) + "_team");
    }

    public String teamTableName(String entityCode) {
        return checkedIdentifier(tableResolver.resolve(entityCode) + "_team");
    }

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

    @Transactional(rollbackFor = Exception.class)
    public void backfillIfEmpty(EntityDefinition definition) {
        String teamTable = teamTableName(definition);
        Long teamCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `" + teamTable + "`",
                Long.class);
        if (teamCount != null && teamCount > 0) {
            return;
        }
        String entityTable = checkedIdentifier(tableResolver.resolve(definition));
        Integer entityTableCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.TABLES "
                        + "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ?",
                Integer.class,
                entityTable);
        if (entityTableCount == null || entityTableCount == 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO `%s`
                    (id, record_id, user_id, action_type, action_description, create_time)
                SELECT REPLACE(UUID(), '-', ''), data.id, data.create_by,
                       'CREATE', '历史回填：创建数据', COALESCE(data.create_time, NOW())
                FROM `%s` data
                WHERE data.create_by IS NOT NULL
                  AND data.create_by <> ''
                  AND LOWER(data.create_by) <> 'system'
                """.formatted(teamTable, entityTable));
        jdbcTemplate.update("""
                INSERT INTO `%s`
                    (id, record_id, user_id, action_type, action_description,
                     process_instance_id, create_time)
                SELECT REPLACE(UUID(), '-', ''), data.id, data.submitter_id,
                       'SUBMIT', '历史回填：提交数据',
                       data.process_instance_id,
                       COALESCE(data.submit_time, data.process_start_time, data.create_time, NOW())
                FROM `%s` data
                WHERE data.submitter_id IS NOT NULL
                  AND data.submitter_id <> ''
                  AND LOWER(data.submitter_id) <> 'system'
                """.formatted(teamTable, entityTable));
        jdbcTemplate.update("""
                INSERT INTO `%s`
                    (id, record_id, user_id, action_type, action_description,
                     process_instance_id, process_task_id, create_time)
                SELECT REPLACE(UUID(), '-', ''), data.id, operation.operator_id,
                       COALESCE(NULLIF(UPPER(operation.operation_type), ''), 'OPERATE'),
                       COALESCE(operation.operation_comment, '历史回填：流程操作'),
                       operation.process_instance_id, operation.task_id,
                       COALESCE(operation.operation_time, NOW())
                FROM `%s` data
                JOIN process_operation_log operation
                  ON operation.process_instance_id = data.process_instance_id
                WHERE operation.operator_id IS NOT NULL
                  AND operation.operator_id <> ''
                  AND LOWER(operation.operator_id) <> 'system'
                """.formatted(teamTable, entityTable));
    }

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
