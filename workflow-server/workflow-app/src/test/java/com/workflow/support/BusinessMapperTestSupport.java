package com.workflow.support;

import javax.sql.DataSource;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.workflow.config.database.DatabaseMybatisConfiguration;
import org.apache.ibatis.mapping.Environment;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;

/** 业务持久化测试走真实 Mapper，并与 JDBC 建表夹具共享 Spring 事务连接。 */
public final class BusinessMapperTestSupport {
    private BusinessMapperTestSupport() {}

    /** 默认 MySQL 语法；所有业务参数和结果处理复用生产配置。 */
    public static <T> T mapper(DataSource source, Class<T> type) {
        return mapper(source, "MYSQL", type);
    }

    /** 多数据库夹具可指定 MyBatis databaseId，不能通过第二个连接绕过事务与保存点。 */
    public static <T> T mapper(DataSource source, String databaseId, Class<T> type) {
        var configuration = new MybatisConfiguration();
        configuration.setDatabaseId(databaseId);
        configuration.setMapUnderscoreToCamelCase(true);
        var production = new DatabaseMybatisConfiguration();
        production.statementScopedReads().customize(configuration);
        production.nullParameterBindings().customize(configuration);
        production.largeTextBindings().customize(configuration);
        production.numericBooleanBindings().customize(configuration);
        configuration.setEnvironment(new Environment("business-test", new SpringManagedTransactionFactory(), source));
        configuration.addMapper(type);
        return new SqlSessionTemplate(new MybatisSqlSessionFactoryBuilder().build(configuration)).getMapper(type);
    }
}
