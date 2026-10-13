package com.workflow.entity.excel.persistence;

import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import javax.sql.DataSource;

/** 真实 MyBatis 与 Spring 事务测试装配；JdbcTemplate 只用于建表、测试数据和独立断言。 */
public final class ExcelMapperTestSupport {
    private ExcelMapperTestSupport() { }

    public static <T> T mapper(DataSource source, Class<T> type) {
        try {
            var configuration = new Configuration();
            configuration.setDatabaseId("MYSQL");
            configuration.setMapUnderscoreToCamelCase(true);
            configuration.setJdbcTypeForNull(JdbcType.NULL);
            configuration.addMapper(type);
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(source);
            factory.setConfiguration(configuration);
            return new SqlSessionTemplate(factory.getObject()).getMapper(type);
        } catch (Exception failure) {
            throw new IllegalStateException("Excel Mapper 测试初始化失败", failure);
        }
    }
}
