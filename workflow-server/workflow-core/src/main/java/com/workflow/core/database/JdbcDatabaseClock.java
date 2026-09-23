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

    /**
     * 初始化JDBC数据库时钟，保存构造参数供后续方法使用。
     *
     * @param jdbc JDBC依赖，保存到当前对象供后续业务方法调用
     * @param vendor 供应商，保存在对象中供后续校验、查询或展示
     */
    public JdbcDatabaseClock(JdbcTemplate jdbc, DatabaseVendor vendor) {
        this.jdbc = jdbc;
        this.query = DatabaseDialects.runtime(vendor).utcNowSql();
    }

    /**
     * 处理UTC当前时间，并将结果传给后续步骤。
     *
     * @return 处理后的UTC当前时间结果，供调用方继续处理
     */
    @Override
    public LocalDateTime utcNow() {
        return Objects.requireNonNull(jdbc.queryForObject(query,
                (row, index) -> row.getObject(1, LocalDateTime.class)), "Database clock returned NULL");
    }
}
