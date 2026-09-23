package com.workflow.entity.data;

import com.workflow.config.database.DatabaseMybatisConfiguration;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.LinkedHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.mapping.Environment;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import static org.junit.jupiter.api.Assertions.*;

/** MySQL 执行真实读写；第二个场景模拟 LOB 元数据和国家字符读取接口，不声称验证了其他数据库。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlLargeTextMappingDatabaseTest {
    @Test void configuredDynamicMapperPreservesNativeLongTextNullsAndScalarTypes() throws Exception {
        try (var f = fixture(); var session = session(f.isolatedDataSource())) {
            var mapper = session.getMapper(EntityDataDynamicMapper.class);
            String body = "中文A\n".repeat(8000) + "尾部";
            var data = new LinkedHashMap<String, Object>();
            data.put("id", "native"); data.put("body", body); data.put("amount", 42); data.put("deleted", 0);
            assertEquals(1, mapper.insert("biz_large_text", data));
            var row = mapper.selectById("biz_large_text", "native");
            assertEquals(body, row.get("body")); assertEquals(42, row.get("amount"));
            data.put("body", null); assertEquals(1, mapper.update("biz_large_text", data));
            assertNull(mapper.selectById("biz_large_text", "native").get("body"));
            session.rollback(true);
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + f.tables.get("biz_large_text"), Integer.class));
        }
    }

    @Test void dynamicMapUsesFrameworkStringHandlersForLargeTextMetadata() throws Exception {
        try (var f = fixture()) {
            String body = "Ā中文\t".repeat(6000) + "last";
            f.jdbc.update("INSERT INTO " + f.tables.get("biz_large_text") + " (id,body,amount,deleted) VALUES (?,?,42,0)", "clob", body);
            f.jdbc.update("INSERT INTO " + f.tables.get("biz_large_text") + " (id,body,amount,deleted) VALUES ('null',NULL,0,0)");
            for (JdbcType type : new JdbcType[]{JdbcType.CLOB, JdbcType.NCLOB, JdbcType.LONGVARCHAR, JdbcType.LONGNVARCHAR}) {
                var reads = new AtomicInteger();
                try (var session = session(clobSource(f, type.TYPE_CODE, reads))) {
                    var registry = session.getConfiguration().getTypeHandlerRegistry();
                    assertSame(registry.getTypeHandler(String.class, type), registry.getTypeHandler(Object.class, type));
                    var mapper = session.getMapper(EntityDataDynamicMapper.class);
                    var row = mapper.selectById("biz_large_text", "clob");
                    assertEquals(body, row.get("body"), type.name());
                    assertEquals(42, row.get("amount"));
                    assertNull(mapper.selectById("biz_large_text", "null").get("body"));
                    assertEquals(2, reads.get());
                }
            }
        }
    }

    @Test void frameworkClobAndNClobBindingsPreserveLargeStringsAndNull() throws Exception {
        try (var f = fixture(); var session = session(f.isolatedDataSource())) {
            var writer = session.getMapper(LargeTextWriter.class);
            String body = "中文🙂\\\n".repeat(12000) + "完整尾部";
            assertEquals(1, writer.insertClob("clob", body));
            assertEquals(1, writer.insertNClob("nclob", body));
            assertEquals(1, writer.insertClob("null-clob", null));
            assertEquals(1, writer.insertNClob("null-nclob", null));
            var reader = session.getMapper(EntityDataDynamicMapper.class);
            assertEquals(body, reader.selectById("biz_large_text", "clob").get("body"));
            assertEquals(body, reader.selectById("biz_large_text", "nclob").get("body"));
            assertNull(reader.selectById("biz_large_text", "null-clob").get("body"));
            assertNull(reader.selectById("biz_large_text", "null-nclob").get("body"));
            session.rollback(true);
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM " + f.tables.get("biz_large_text"), Integer.class));
        }
    }

    interface LargeTextWriter {
        @Insert("INSERT INTO biz_large_text(id,body,deleted) VALUES (#{id},#{body,jdbcType=CLOB},0)")
        int insertClob(@Param("id") String id, @Param("body") String body);
        @Insert("INSERT INTO biz_large_text(id,body,deleted) VALUES (#{id},#{body,jdbcType=NCLOB},0)")
        int insertNClob(@Param("id") String id, @Param("body") String body);
    }

    private static Fixture fixture() {
        var f = new Fixture();
        f.table("biz_large_text", "id VARCHAR(64) PRIMARY KEY,body LONGTEXT,amount INT,deleted INT,create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        return f;
    }
    private static SqlSession session(javax.sql.DataSource source) {
        var configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("mysql-large-text", new JdbcTransactionFactory(), source));
        configuration.setDatabaseId("MYSQL"); configuration.setMapUnderscoreToCamelCase(true);
        new DatabaseMybatisConfiguration().largeTextBindings().customize(configuration);
        configuration.addMapper(EntityDataDynamicMapper.class);
        configuration.addMapper(LargeTextWriter.class);
        return new SqlSessionFactoryBuilder().build(configuration).openSession(false);
    }
    private static javax.sql.DataSource clobSource(Fixture f, int type, AtomicInteger reads) {
        return new DelegatingDataSource(f.isolatedDataSource()) {
            @Override public Connection getConnection() throws SQLException {
                var connection = super.getConnection();
                return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                    Object result = invoke(connection, method, args);
                    if (!(result instanceof Statement statement)) return result;
                    Class<?> contract = result instanceof PreparedStatement ? PreparedStatement.class : Statement.class;
                    return Proxy.newProxyInstance(Statement.class.getClassLoader(), new Class<?>[]{contract}, (p, m, a) -> {
                        Object value = invoke(statement, m, a);
                        return value instanceof ResultSet rows ? clobRows(rows, type, reads) : value;
                    });
                });
            }
        };
    }
    private static ResultSet clobRows(ResultSet rows, int type, AtomicInteger reads) {
        return (ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(), new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
            if (method.getName().equals("getMetaData")) {
                var metadata = rows.getMetaData();
                return Proxy.newProxyInstance(ResultSetMetaData.class.getClassLoader(), new Class<?>[]{ResultSetMetaData.class}, (p, m, a) -> {
                    if (a != null && a.length == 1 && a[0] instanceof Integer index && "body".equalsIgnoreCase(metadata.getColumnLabel(index))) {
                        if (m.getName().equals("getColumnType")) return type;
                        if (m.getName().equals("getColumnClassName")) return Clob.class.getName();
                    }
                    return invoke(metadata, m, a);
                });
            }
            if (method.getName().startsWith("get") && args != null && args.length == 1
                    && (args[0] instanceof Integer || args[0] instanceof String)) {
                int index = args[0] instanceof Integer number ? number : rows.findColumn((String) args[0]);
                if ("body".equalsIgnoreCase(rows.getMetaData().getColumnLabel(index))) {
                    // 动态 Object 列必须选择 String 处理器，不能把厂商 LOB 对象泄漏给业务层。
                    assertNotEquals("getObject", method.getName());
                    if (java.util.Set.of("getClob", "getNClob", "getString", "getNString").contains(method.getName())) {
                        reads.incrementAndGet();
                    }
                    // MySQL LONGTEXT 不是真正的 NCLOB；模拟元数据时一并提供对应 JDBC 读取接口。
                    // 内容仍来自真实结果集，getObject 兜底仍被禁止，完整读取由框架处理器负责。
                    if (method.getName().equals("getNClob")) {
                        Clob clob = rows.getClob(index);
                        return clob == null ? null : Proxy.newProxyInstance(NClob.class.getClassLoader(),
                                new Class<?>[]{NClob.class}, (p, m, a) -> invoke(clob, m, a));
                    }
                    if (method.getName().equals("getNString")) return rows.getString(index);
                }
            }
            return invoke(rows, method, args);
        });
    }
    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException failure) { throw failure.getCause(); }
    }
}
