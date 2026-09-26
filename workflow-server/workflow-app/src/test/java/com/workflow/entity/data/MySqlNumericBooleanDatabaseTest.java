package com.workflow.entity.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.workflow.config.database.DatabaseMybatisConfiguration;
import com.workflow.core.database.jdbc.JdbcIdempotentInsert;
import com.workflow.core.database.jdbc.JdbcLockedRow;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import javax.sql.DataSource;

import com.workflow.integration.database.schema.dialect.MySqlSchemaDdlDialect;
import lombok.Data;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.session.ExecutorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** 真实 MySQL 执行并观察 JDBC setter；禁止仅因 MySQL 容忍 setBoolean 而把错误绑定测成通过。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlNumericBooleanDatabaseTest {
    @Test void baseMapperAndTypedNullsUseNumericParametersAndKeepJsonText() throws Exception {
        try (var f = new Fixture()) {
            f.table("biz_boolean", "id VARCHAR(64) PRIMARY KEY,flag SMALLINT,nullable_flag DECIMAL(1),payload TEXT,deleted INT DEFAULT 0");
            var h = new BindingHarness(f, ExecutorType.SIMPLE, BooleanMapper.class);
            var mapper = h.session.getMapper(BooleanMapper.class);
            var row = row("a", true); row.setNullableFlag(false); row.setPayload("{\"enabled\":true,\"nested\":[false]}");
            assertEquals(1, mapper.insert(row));
            assertEquals(List.of(1, 0), h.audit.numericBooleans());
            var loaded = mapper.selectById("a");
            assertTrue(loaded.getFlag()); assertFalse(loaded.getNullableFlag()); assertEquals(row.getPayload(), loaded.getPayload());
            h.audit.clear(); row.setFlag(false); row.setNullableFlag(true);
            assertEquals(1, mapper.updateById(row));
            assertEquals(List.of(0, 1), h.audit.numericBooleans());
            assertFalse(mapper.selectById("a").getFlag());
            row.setFlag(null); row.setNullableFlag(null); h.audit.clear();
            assertEquals(1, mapper.updateIncludingNull(row));
            assertEquals(List.of(Types.INTEGER, Types.INTEGER), h.audit.nullTypes());
            assertNull(mapper.selectById("a").getFlag()); assertNull(mapper.selectById("a").getNullableFlag());
            assertEquals(row.getPayload(), h.jdbc.queryForObject("SELECT payload FROM biz_boolean WHERE id='a'", String.class));
        }
    }

    @Test void dynamicMapWritesAndPredicateBindingsKeepFalseDistinctFromNull() throws Exception {
        try (var f = new Fixture()) {
            f.table("biz_boolean", "id VARCHAR(64) PRIMARY KEY,flag TINYINT(1),payload TEXT,deleted INT DEFAULT 0,create_time DATETIME DEFAULT CURRENT_TIMESTAMP,update_time DATETIME");
            var h = new BindingHarness(f, ExecutorType.SIMPLE, EntityDataDynamicMapper.class);
            var mapper = h.session.getMapper(EntityDataDynamicMapper.class);
            assertEquals(1, mapper.insert("biz_boolean", Map.of("id", "on", "flag", true)));
            assertEquals(1, mapper.insert("biz_boolean", Map.of("id", "off", "flag", false)));
            assertEquals(List.of(1, 0), h.audit.numericBooleans());
            assertEquals(1, mapper.countByCondition("biz_boolean", Map.of("flag", true, "flag_op", "EQ")));
            assertEquals(1, mapper.countByCondition("biz_boolean", Map.of("flag", false, "flag_op", "EQ")));
            assertEquals(1, mapper.update("biz_boolean", Map.of("id", "on", "flag", false)));
            var cleared = new LinkedHashMap<String, Object>(); cleared.put("id", "off"); cleared.put("flag", null);
            assertEquals(1, mapper.update("biz_boolean", cleared));
            assertEquals(1, mapper.countByCondition("biz_boolean", Map.of("flag", false, "flag_op", "EQ")));
            assertNull(h.jdbc.queryForObject("SELECT flag FROM biz_boolean WHERE id='off'", Integer.class));
        }
    }

    @Test void batchMapperBindingKeepsTransactionRollbackAndPrimitiveBitFlags() throws Exception {
        try (var f = new Fixture()) {
            f.table("biz_boolean", "id VARCHAR(64) PRIMARY KEY,flag TINYINT(1),nullable_flag SMALLINT,payload TEXT,deleted INT DEFAULT 0");
            var h = new BindingHarness(f, ExecutorType.BATCH, BooleanMapper.class); var mapper = h.session.getMapper(BooleanMapper.class);
            h.tx.executeWithoutResult(status -> {
                mapper.insert(row("a", true)); mapper.insert(row("b", false)); h.session.flushStatements();
                assertEquals(List.of(1, 0), h.jdbc.queryForList("SELECT flag FROM biz_boolean ORDER BY id", Integer.class));
                mapper.updatePrimitive("a", false); mapper.updatePrimitive("b", true); h.session.flushStatements();
                assertEquals(List.of(0, 1), h.jdbc.queryForList("SELECT flag FROM biz_boolean ORDER BY id", Integer.class));
                status.setRollbackOnly();
            });
            assertEquals(0, h.jdbc.queryForObject("SELECT COUNT(*) FROM biz_boolean", Integer.class));
            assertEquals(List.of(1, 0, 0, 1), h.audit.numericBooleans());
        }
    }

    @Test void jdbcIdempotentAndLockedRowsShareNumericEncodingAndRollback() throws Exception {
        try (var f = new Fixture()) {
            f.table("biz_boolean", "id VARCHAR(64) PRIMARY KEY,flag SMALLINT,nullable_flag DECIMAL(1),payload TEXT,deleted INT DEFAULT 0");
            var h = new BindingHarness(f, ExecutorType.SIMPLE, BooleanMapper.class);
            var dialect = DatabaseDialects.insert(DatabaseVendor.MYSQL);
            var inserts = new JdbcIdempotentInsert(h.jdbc, dialect); var locks = new JdbcLockedRow(h.jdbc, dialect);
            var values = new LinkedHashMap<String, Object>(); values.put("id", "a"); values.put("flag", true); values.put("nullable_flag", false);
            values.put("payload", "{\"enabled\":false}");
            assertTrue(inserts.insertIfAbsent("biz_boolean", values));
            values.put("flag", false);
            assertFalse(inserts.insertIfAbsent("biz_boolean", values));
            assertEquals(1, h.jdbc.queryForObject("SELECT flag FROM biz_boolean WHERE id='a'", Integer.class));
            h.tx.executeWithoutResult(status -> {
                locks.ensureAndLock("biz_boolean", values, List.of("id"));
                assertEquals(1, h.jdbc.queryForObject("SELECT flag FROM biz_boolean WHERE id='a'", Integer.class));
                var added = new LinkedHashMap<>(values); added.put("id", "b"); added.put("nullable_flag", null);
                locks.ensureAndLock("biz_boolean", added, List.of("id"));
                assertEquals(0, h.jdbc.queryForObject("SELECT flag FROM biz_boolean WHERE id='b'", Integer.class));
                assertNull(h.jdbc.queryForObject("SELECT nullable_flag FROM biz_boolean WHERE id='b'", Integer.class));
                assertTrue(inserts.insertIfAbsent("biz_boolean", Map.of("id", "c", "flag", true)));
                status.setRollbackOnly();
            });
            assertEquals(List.of("a"), h.jdbc.queryForList("SELECT id FROM biz_boolean", String.class));
            assertEquals("{\"enabled\":false}", h.jdbc.queryForObject("SELECT payload FROM biz_boolean WHERE id='a'", String.class));
            assertEquals(List.of(1, 0, 0, 0, 0, 0, 0, 1), h.audit.numericBooleans());
        }
    }

    private static BooleanRow row(String id, boolean value) {
        var row = new BooleanRow(); row.setId(id); row.setFlag(value); return row;
    }

    @Data @TableName("biz_boolean")
    public static class BooleanRow {
        @TableId(type = IdType.INPUT) private String id;
        private Boolean flag;
        private Boolean nullableFlag;
        private String payload;
        private Integer deleted;
    }

    public interface BooleanMapper extends BaseMapper<BooleanRow> {
        @Update("UPDATE biz_boolean SET flag=#{flag},nullable_flag=#{nullableFlag,jdbcType=BOOLEAN} WHERE id=#{id}")
        int updateIncludingNull(BooleanRow row);

        @Update("UPDATE biz_boolean SET flag=#{flag,javaType=boolean,jdbcType=BIT} WHERE id=#{id}")
        int updatePrimitive(@org.apache.ibatis.annotations.Param("id") String id,
                            @org.apache.ibatis.annotations.Param("flag") boolean flag);
    }

    static class BindingHarness {
        final BindingAudit audit;
        final JdbcTemplate jdbc;
        final SqlSessionTemplate session;
        final TransactionTemplate tx;

        BindingHarness(Fixture fixture, ExecutorType executor, Class<?>... mappers) throws Exception {
            audit = new BindingAudit(fixture.isolatedDataSource()); jdbc = new JdbcTemplate(audit);
            tx = new TransactionTemplate(new DataSourceTransactionManager(audit));
            var configuration = new MybatisConfiguration(); configuration.setDatabaseId("MYSQL"); configuration.setMapUnderscoreToCamelCase(true);
            com.baomidou.mybatisplus.core.toolkit.GlobalConfigUtils.getGlobalConfig(configuration)
                    .getDbConfig().setLogicDeleteField("deleted");
            configuration.addInterceptor(new DatabaseMybatisConfiguration()
                    .mybatisPlusInterceptor(new MySqlSchemaDdlDialect()));
            new DatabaseMybatisConfiguration().numericBooleanBindings().customize(configuration);
            new DatabaseMybatisConfiguration().nullParameterBindings().customize(configuration);
            for (var mapper : mappers) configuration.addMapper(mapper);
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(audit); factory.setConfiguration(configuration);
            session = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject()), executor);
        }
    }

    /** 只记录 setter，不改 SQL 或参数；真实连接仍执行每条生产 SQL。 */
    static class BindingAudit extends DelegatingDataSource {
        private final List<Integer> integers = new ArrayList<>();
        private final List<Integer> nulls = new ArrayList<>();
        BindingAudit(DataSource target) { super(target); }
        List<Integer> numericBooleans() { return List.copyOf(integers); }
        List<Integer> nullTypes() { return List.copyOf(nulls); }
        void clear() { integers.clear(); nulls.clear(); }

        @Override public Connection getConnection() throws SQLException { return observe(super.getConnection()); }
        @Override public Connection getConnection(String user, String password) throws SQLException {
            return observe(super.getConnection(user, password));
        }

        private Connection observe(Connection connection) {
            return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                Object result = invoke(connection, method, args);
                if (!method.getName().equals("prepareStatement")) return result;
                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (statement, setter, values) -> {
                    if (setter.getName().equals("setBoolean") || (setter.getName().equals("setObject") && values[1] instanceof Boolean))
                        throw new AssertionError("业务布尔列必须以数值绑定");
                    if (setter.getName().equals("setInt")) integers.add((Integer) values[1]);
                    // Spring 的已知 INTEGER 参数使用 setObject(value, INTEGER)，同样是数值绑定。
                    if (setter.getName().equals("setObject") && values.length >= 3
                            && values[1] instanceof Integer number && Objects.equals(values[2], Types.INTEGER))
                        integers.add(number);
                    if (setter.getName().equals("setNull")) nulls.add((Integer) values[1]);
                    return invoke(result, setter, values);
                });
            });
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try { return method.invoke(target, args); }
            catch (InvocationTargetException error) { throw error.getCause(); }
        }
    }
}
