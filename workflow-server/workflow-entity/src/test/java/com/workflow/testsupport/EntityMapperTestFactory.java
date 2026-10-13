package com.workflow.testsupport;

import javax.sql.DataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.LocalCacheScope;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.transaction.SpringManagedTransactionFactory;

/** 测试使用真实 MyBatis SQL/绑定/事务；JDBC 仅用于准备和独立核验测试数据。 */
public final class EntityMapperTestFactory {
    private EntityMapperTestFactory() { }
    public static <T> T mapper(DataSource source, Class<T> type) {
        Configuration configuration = new Configuration(new Environment("test", new SpringManagedTransactionFactory(), source));
        configuration.setCallSettersOnNulls(true);
        configuration.setLocalCacheScope(LocalCacheScope.STATEMENT);
        configuration.addMapper(type);
        return new SqlSessionTemplate(new SqlSessionFactoryBuilder().build(configuration)).getMapper(type);
    }
}
