package com.workflow.core.database;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.core.database.port.DatabaseClockPort;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.LocalDateTime;
import java.util.Objects;

/** 将数据库实际当前时间转换为 UTC 无时区值；租约不受应用机器时钟及会话时区影响。 */
public final class JdbcDatabaseClock implements DatabaseClockPort {
    private final JdbcTemplate jdbc;
    private final String query;

    public JdbcDatabaseClock(JdbcTemplate jdbc, DatabaseVendor vendor) {
        this.jdbc = jdbc;
        this.query = DatabaseDialects.runtime(vendor).utcNowSql();
    }

    @Override
    public LocalDateTime utcNow() {
        return Objects.requireNonNull(jdbc.queryForObject(query,
                (row, index) -> row.getObject(1, LocalDateTime.class)), "Database clock returned NULL");
    }
}
