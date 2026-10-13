package com.workflow.database;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.workflow.config.database.DatabaseDataSourceConfiguration;
import com.workflow.config.database.DatabaseMybatisConfiguration;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import org.apache.ibatis.mapping.SqlCommandType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.CachingMetadataReaderFactory;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 已导入完整平台结构的 OB Oracle 只读兼容性回归；由 FLOW_OB_ORACLE_TEST_URL/USER/PASSWORD 显式启用。
 * 每个生产 BaseMapper 执行一次完整字段投影、逻辑删除条件和实体映射，每表最多读取一行，不创建或修改业务数据。
 */
@EnabledIfEnvironmentVariable(named = "FLOW_OB_ORACLE_TEST_URL", matches = ".+")
class OceanBaseOracleMapperReadDatabaseTest {
    @Test
    void allProductionBaseMappersReadOneRowWithOracleConfiguration() throws Exception {
        var dialect = DatabaseDialects.forVendor(DatabaseVendor.OCEANBASE_ORACLE);
        var database = new DatabaseMybatisConfiguration();
        var properties = new MybatisPlusProperties();
        properties.getGlobalConfig().getDbConfig().setLogicDeleteField("deleted");
        properties.getGlobalConfig().getDbConfig().setLogicDeleteValue("1");
        properties.getGlobalConfig().getDbConfig().setLogicNotDeleteValue("0");
        database.oracleIdentifierMappings(dialect).customize(properties);
        var configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setDatabaseId(dialect.vendor().name());
        configuration.setDefaultStatementTimeout(30);
        database.nullParameterBindings().customize(configuration);
        database.largeTextBindings().customize(configuration);
        database.numericBooleanBindings().customize(configuration);
        configuration.addInterceptor(database.mybatisPlusInterceptor(dialect));
        // 必须先应用生产全局元数据，再注册 Mapper；否则列名/别名保留字的修复不会进入 TableInfo。
        GlobalConfigUtils.setGlobalConfig(configuration, properties.getGlobalConfig());
        var mappers = productionBaseMappers();
        assertTrue(mappers.size() >= 94, "生产 Mapper 发现数量异常: " + mappers.size());
        var failures = new ArrayList<Throwable>();
        var registered = new ArrayList<Class<?>>();
        for (var mapper : mappers) {
            try {
                configuration.addMapper(mapper);
                registered.add(mapper);
            } catch (Throwable failure) {
                failures.add(new AssertionError("Mapper 注册失败: " + mapper.getName(), failure));
            }
        }
        var data = new DataSourceProperties();
        data.setUrl(System.getenv("FLOW_OB_ORACLE_TEST_URL"));
        data.setUsername(System.getenv("FLOW_OB_ORACLE_TEST_USER"));
        data.setPassword(System.getenv("FLOW_OB_ORACLE_TEST_PASSWORD"));
        int passed = 0;
        try (var pool = new DatabaseDataSourceConfiguration().dataSource(data, dialect)) {
            pool.setMaximumPoolSize(2);
            pool.setMinimumIdle(0);
            pool.setConnectionTimeout(30_000);
            pool.setValidationTimeout(10_000);
            var factoryBean = new MybatisSqlSessionFactoryBean();
            factoryBean.setDataSource(pool);
            factoryBean.setConfiguration(configuration);
            factoryBean.setGlobalConfig(properties.getGlobalConfig());
            var factory = factoryBean.getObject();
            assertNotNull(factory);
            for (var mapper : registered) {
                String statementId = mapper.getName() + ".selectList";
                try (var session = factory.openSession()) {
                    var wrapper = new QueryWrapper<>().last("FETCH FIRST 1 ROWS ONLY");
                    var parameters = Collections.singletonMap(Constants.WRAPPER, wrapper);
                    var mapped = configuration.getMappedStatement(statementId);
                    assertEquals(SqlCommandType.SELECT, mapped.getSqlCommandType(), "拒绝执行非查询语句");
                    String sql = mapped.getBoundSql(parameters).getSql();
                    assertTrue(sql.stripLeading().toUpperCase(Locale.ROOT).startsWith("SELECT "),
                            "只读保护拒绝执行: " + sql);
                    System.out.println("SQL\t" + statementId + "\t" + sql.replaceAll("\\s+", " "));
                    // 只调用已确认的 SELECT 映射，不执行 Mapper 的默认方法或其他自定义业务方法。
                    List<?> rows = session.selectList(statementId, parameters);
                    assertTrue(rows.size() <= 1, "单表读取超过限制: " + statementId);
                    passed++;
                    System.out.println("PASS\t" + statementId + "\trows=" + rows.size());
                } catch (Throwable failure) {
                    failures.add(new AssertionError("生产查询失败: " + statementId, failure));
                    System.out.println("FAIL\t" + statementId);
                    failure.printStackTrace(System.out);
                }
            }
        }
        System.out.println("SUMMARY\tmappers=" + mappers.size() + "\tpassed=" + passed + "\tfailed=" + failures.size());
        // 收集所有失败再统一断言，避免首个错误掩盖其他业务表的不兼容；错误不会被忽略。
        assertAll("OB Oracle 生产 BaseMapper 查询", failures.stream().map(failure -> () -> { throw failure; }));
    }

    /** 自动发现生产接口，排除测试输出目录及内部测试 Mapper；非 BaseMapper 的动态 SQL 由专项测试覆盖。 */
    private List<Class<?>> productionBaseMappers() throws Exception {
        var resources = new PathMatchingResourcePatternResolver();
        var metadata = new CachingMetadataReaderFactory(resources);
        var found = new TreeMap<String, Class<?>>();
        for (var resource : resources.getResources("classpath*:com/workflow/**/*.class")) {
            String location = resource.getURL().toExternalForm();
            if (location.contains("/test-classes/") || location.contains("-tests.jar!")) continue;
            var type = metadata.getMetadataReader(resource).getClassMetadata();
            if (!type.isInterface() || !type.isIndependent() || type.getClassName().contains("$")) continue;
            Class<?> candidate = Class.forName(type.getClassName(), false, getClass().getClassLoader());
            if (BaseMapper.class.isAssignableFrom(candidate)) found.put(candidate.getName(), candidate);
        }
        return List.copyOf(found.values());
    }
}
