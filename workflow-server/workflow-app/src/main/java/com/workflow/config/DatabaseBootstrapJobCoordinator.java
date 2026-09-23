package com.workflow.config;

import com.workflow.contracts.bootstrap.port.BootstrapJobPort;
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

/**
 * 封装数据库初始化{@code job}{@code coordinator}相关能力和状态；供同一业务流程的后续处理使用。
 */
@Service
public class DatabaseBootstrapJobCoordinator
        implements BootstrapJobPort {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcLockedRow lockedRows;
    private final DatabaseClockPort databaseClock;
    private final String ownerId =
            "bootstrap-" + UUID.randomUUID();

    /**
     * 初始化数据库初始化{@code job}{@code coordinator}，保存构造参数供后续方法使用。
     *
     * @param jdbcTemplate JDBC模板依赖，保存到当前对象供后续业务方法调用
     * @param lockedRows 已锁定行依赖，保存到当前对象供后续业务方法调用
     * @param databaseClock 数据库时钟依赖，保存到当前对象供后续业务方法调用
     */
    public DatabaseBootstrapJobCoordinator(
            JdbcTemplate jdbcTemplate, JdbcLockedRow lockedRows, DatabaseClockPort databaseClock) {
        this.jdbcTemplate = jdbcTemplate;
        this.lockedRows = lockedRows;
        this.databaseClock = databaseClock;
    }

    /**
     * 持有任务行锁执行未完成的版本；动作和完成记录一起提交，失败可由后续节点重试。
     *
     * @param jobName {@code job}名称，后续用于执行{@code once}时匹配或展示
     * @param requiredVersion 必填版本，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @param action 动作标识，决定后续{@code once}采用的处理分支
     * @return 匹配的{@code once}；未找到时为空
     */
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
