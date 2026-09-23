package com.workflow.config;

import com.workflow.contracts.bootstrap.port.BootstrapJobCoordinator;
import com.workflow.core.database.JdbcLockedRow;
import com.workflow.core.database.port.DatabaseClockPort;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseBootstrapJobCoordinator
        implements BootstrapJobCoordinator {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcLockedRow lockedRows;
    private final DatabaseClockPort databaseClock;
    private final String ownerId =
            "bootstrap-" + UUID.randomUUID();

    public DatabaseBootstrapJobCoordinator(
            JdbcTemplate jdbcTemplate, JdbcLockedRow lockedRows, DatabaseClockPort databaseClock) {
        this.jdbcTemplate = jdbcTemplate;
        this.lockedRows = lockedRows;
        this.databaseClock = databaseClock;
    }

    /** 持有任务行锁执行未完成的版本；动作和完成记录一起提交，失败可由后续节点重试。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public <T> Optional<T> executeOnce(
            String jobName,
            int requiredVersion,
            Supplier<T> action) {
        if (jobName == null || jobName.isBlank()) {
            throw new IllegalArgumentException(
                    "Bootstrap jobName 不能为空");
        }
        if (requiredVersion < 1) {
            throw new IllegalArgumentException(
                    "Bootstrap requiredVersion 必须大于 0");
        }
        var startedAt = databaseClock.utcNow();
        lockedRows.ensureAndLock("workflow_bootstrap_job", Map.of(
                "job_name", jobName, "completed_version", 0,
                "create_time", startedAt, "update_time", startedAt), List.of("job_name"));
        Integer completedVersion = jdbcTemplate.queryForObject(
                "SELECT completed_version FROM workflow_bootstrap_job "
                        + "WHERE job_name = ? FOR UPDATE",
                Integer.class,
                jobName);
        if (completedVersion != null
                && completedVersion >= requiredVersion) {
            return Optional.empty();
        }
        T result = action.get();
        var completedAt = databaseClock.utcNow();
        int updated = jdbcTemplate.update(
                "UPDATE workflow_bootstrap_job "
                        + "SET completed_version = ?, owner_id = ?, "
                        + "completed_at = ?, update_time = ? "
                        + "WHERE job_name = ?",
                requiredVersion,
                ownerId,
                completedAt,
                completedAt,
                jobName);
        if (updated != 1) {
            throw new IllegalStateException(
                    "Bootstrap job 状态确认失败: " + jobName);
        }
        return Optional.ofNullable(result);
    }
}
