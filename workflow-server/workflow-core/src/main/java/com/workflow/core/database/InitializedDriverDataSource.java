package com.workflow.core.database;

import com.workflow.integration.database.api.DatabaseJdbcProfiles;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/** 独立物理连接，建连后按产品初始化；初始化失败时必须关闭连接，不能泄露会话。 */
public final class InitializedDriverDataSource extends DriverManagerDataSource {
    private final String initializationSql;

    public InitializedDriverDataSource(String url, String username, String password, String driver, String initializationSql) {
        if (url == null || url.isBlank()) throw new IllegalStateException("数据库 URL 不能为空");
        if (username == null || username.isBlank()) throw new IllegalStateException("数据库用户名不能为空");
        if (password == null) throw new IllegalStateException("数据库密码未配置");
        setUrl(url);
        setUsername(username);
        setPassword(password);
        setDriverClassName(DatabaseJdbcProfiles.driver(url, driver));
        this.initializationSql = initializationSql;
    }

    @Override
    protected Connection getConnectionFromDriver(Properties properties) throws SQLException {
        Connection connection = super.getConnectionFromDriver(properties);
        try {
            if (initializationSql != null && !initializationSql.isBlank()) {
                try (var statement = connection.createStatement()) { statement.execute(initializationSql); }
            }
            return connection;
        } catch (SQLException | RuntimeException exception) {
            try { connection.close(); } catch (SQLException closeFailure) { exception.addSuppressed(closeFailure); }
            throw exception;
        }
    }
}
