package com.workflow.config;

import com.workflow.contracts.bootstrap.BootstrapJobCoordinator;
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
    private final String ownerId =
            "bootstrap-" + UUID.randomUUID();

    public DatabaseBootstrapJobCoordinator(
            JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

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
        jdbcTemplate.update(
                "INSERT IGNORE INTO workflow_bootstrap_job "
                        + "(job_name, completed_version, create_time, update_time) "
                        + "VALUES (?, 0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))",
                jobName);
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
        int updated = jdbcTemplate.update(
                "UPDATE workflow_bootstrap_job "
                        + "SET completed_version = ?, owner_id = ?, "
                        + "completed_at = UTC_TIMESTAMP(6), "
                        + "update_time = UTC_TIMESTAMP(6) "
                        + "WHERE job_name = ?",
                requiredVersion,
                ownerId,
                jobName);
        if (updated != 1) {
            throw new IllegalStateException(
                    "Bootstrap job 状态确认失败: " + jobName);
        }
        return Optional.ofNullable(result);
    }
}
