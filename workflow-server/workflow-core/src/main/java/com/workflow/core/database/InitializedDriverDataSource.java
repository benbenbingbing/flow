package com.workflow.core.database;

import com.workflow.integration.database.api.runtime.DatabaseJdbcProfiles;

import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Properties;

/** 独立物理连接，建连后按产品初始化；初始化失败时必须关闭连接，不能泄露会话。 */
public final class InitializedDriverDataSource extends DriverManagerDataSource {
    private final String initializationSql;

    /**
     * 初始化{@code initialized}{@code driver}数据来源，保存构造参数供后续方法使用。
     *
     * @param url URL，保存在对象中供后续校验、查询或展示
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @param password 密码，保存在对象中供后续校验、查询或展示
     * @param driver {@code driver}，保存在对象中供后续校验、查询或展示
     * @param initializationSql {@code initialization}SQL依赖，保存到当前对象供后续业务方法调用
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
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

    /**
     * 读取连接起始{@code driver}；查询结果供调用方展示或继续处理。
     *
     * @param properties 属性集合，供本方法读取连接起始{@code driver}时使用
     * @return 符合条件的连接结果，供调用方继续处理
     * @throws SQLException 数据库访问或结构检查失败时抛出
     */
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
