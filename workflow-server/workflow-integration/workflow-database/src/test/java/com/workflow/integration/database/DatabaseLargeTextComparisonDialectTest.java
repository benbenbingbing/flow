package com.workflow.integration.database;

import com.workflow.integration.database.api.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.SchemaType;
import java.io.Reader;
import java.io.StringWriter;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.scripting.defaults.DefaultParameterHandler;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.ClobTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 验证产品比较模板与 MyBatis 标准大文本绑定，避免只检查 SQL 却遗漏参数被截断或 NULL 改义。 */
class DatabaseLargeTextComparisonDialectTest {
    private static final Set<DatabaseVendor> CLOB_VENDORS = Set.of(
            DatabaseVendor.ORACLE, DatabaseVendor.DM, DatabaseVendor.OCEANBASE_ORACLE);
    private static final List<String> OPERATORS = List.of("=", "<>", ">", ">=", "<", "<=");

    @Test
    void allVendorsKeepCompleteTextAndTheRequestedComparisonRelation() {
        for (var vendor : DatabaseVendor.values()) {
            var dialect = DatabaseQueryDialects.forVendor(vendor);
            String left = dialect.quoteIdentifier("source") + "." + dialect.quoteIdentifier("body");
            String right = dialect.quoteIdentifier("target") + "." + dialect.quoteIdentifier("body");
            for (var kind : List.of(SchemaType.Kind.TEXT, SchemaType.Kind.LARGE_TEXT)) {
                for (String operator : OPERATORS) {
                    String parameterComparison = dialect.comparisonPredicate("source.body", kind, operator, ":value");
                    String columnComparison = dialect.columnComparisonPredicate("source.body", kind, operator, "target.body");
                    if (CLOB_VENDORS.contains(vendor)) {
                        assertEquals("DBMS_LOB.COMPARE(" + left + ", :value) " + operator + " 0", parameterComparison, vendor.name());
                        assertEquals("DBMS_LOB.COMPARE(" + left + ", " + right + ") " + operator + " 0", columnComparison, vendor.name());
                    } else {
                        assertEquals(left + " " + operator + " :value", parameterComparison, vendor.name());
                        assertEquals(left + " " + operator + " " + right, columnComparison, vendor.name());
                    }
                }
                assertEquals(CLOB_VENDORS.contains(vendor) ? "CLOB" : "VARCHAR", dialect.comparisonJdbcType(kind));
            }
        }
    }

    @Test
    void ordinaryScalarColumnsKeepNativeComparisonAndTheirExistingJdbcTypes() {
        Map<SchemaType.Kind, String> jdbcTypes = Map.of(
                SchemaType.Kind.STRING, "VARCHAR", SchemaType.Kind.INTEGER, "INTEGER",
                SchemaType.Kind.BYTE, "INTEGER", SchemaType.Kind.BOOLEAN, "INTEGER",
                SchemaType.Kind.LONG, "BIGINT", SchemaType.Kind.DECIMAL, "DECIMAL",
                SchemaType.Kind.DATE, "DATE", SchemaType.Kind.TIMESTAMP, "TIMESTAMP");
        for (var vendor : DatabaseVendor.values()) {
            var dialect = DatabaseQueryDialects.forVendor(vendor);
            for (var entry : jdbcTypes.entrySet()) {
                assertEquals(entry.getValue(), dialect.comparisonJdbcType(entry.getKey()));
                for (String operator : OPERATORS) {
                    String parameter = "#{values.value,jdbcType=" + entry.getValue() + "}";
                    assertEquals(dialect.quoteIdentifier("value") + " " + operator + " " + parameter,
                            dialect.comparisonPredicate("value", entry.getKey(), operator, parameter));
                    assertEquals(dialect.quoteIdentifier("left_value") + " " + operator + " " + dialect.quoteIdentifier("right_value"),
                            dialect.columnComparisonPredicate("left_value", entry.getKey(), operator, "right_value"));
                }
            }
        }
    }

    @Test
    void comparisonFragmentsRejectExpressionsUntrustedOperatorsAndMalformedBindings() {
        for (var vendor : DatabaseVendor.values()) {
            var dialect = DatabaseQueryDialects.forVendor(vendor);
            for (String column : List.of("body;--", "body OR 1=1", "CAST(body AS VARCHAR)", "a..body", "a.b.body", "a.", "`body`")) {
                assertThrows(IllegalArgumentException.class, () -> dialect.comparisonPredicate(column, SchemaType.Kind.TEXT, "=", "?"));
                assertThrows(IllegalArgumentException.class, () -> dialect.columnComparisonPredicate("body", SchemaType.Kind.TEXT, "=", column));
            }
            for (String operator : List.of("!=", "LIKE", "IN", "BETWEEN", "= 1 OR", " =", "")) {
                assertThrows(IllegalArgumentException.class, () -> dialect.comparisonPredicate("body", SchemaType.Kind.TEXT, operator, "?"));
            }
            for (String parameter : List.of("'literal'", "NULL", "EMPTY_CLOB()", "${value}", "? OR 1=1", ":a.b",
                    "#{a..b}", "#{value,jdbcType=CLOB) OR 1=1}", "#{value,typeHandler=com.example.CustomHandler}", "#{value,mode=OUT}")) {
                assertThrows(IllegalArgumentException.class, () -> dialect.comparisonPredicate("body", SchemaType.Kind.TEXT, "=", parameter));
            }
            assertThrows(IllegalArgumentException.class, () -> dialect.comparisonPredicate("body", null, "=", "?"));
            assertThrows(IllegalArgumentException.class, () -> dialect.comparisonPredicate("body", SchemaType.Kind.TEXT, null, "?"));
            assertThrows(IllegalArgumentException.class, () -> dialect.comparisonPredicate("body", SchemaType.Kind.TEXT, "=", null));
            assertThrows(IllegalArgumentException.class, () -> dialect.comparisonJdbcType(null));
        }
    }

    @Test
    void mybatisStandardClobHandlerStreamsTheEntireJavaStringAndPreservesNull() throws Exception {
        String fullText = "正文' OR 1=1 --".repeat(4000) + "🚀末尾差异";
        assertTrue(fullText.length() > 32767);
        for (var vendor : CLOB_VENDORS) {
            var dialect = DatabaseQueryDialects.forVendor(vendor);
            // 显式处理器用于 Map 参数的稳定映射；仅 jdbcType=CLOB 时也验证框架按 String 选择内置处理器。
            for (String handler : List.of("", ",typeHandler=org.apache.ibatis.type.ClobTypeHandler")) {
                String predicate = dialect.comparisonPredicate("body", SchemaType.Kind.TEXT, "=",
                        "#{value,jdbcType=" + dialect.comparisonJdbcType(SchemaType.Kind.TEXT) + handler + "}");
                var configuration = new Configuration();
                var source = new XMLLanguageDriver().createSqlSource(configuration,
                        "SELECT id FROM sample WHERE " + predicate, Map.class);
                var statement = new MappedStatement.Builder(configuration, "compareBody", source, SqlCommandType.SELECT).build();
                var values = new HashMap<String, Object>();
                values.put("value", fullText);
                var bound = source.getBoundSql(values);
                assertEquals(1, bound.getParameterMappings().size());
                assertEquals(JdbcType.CLOB, bound.getParameterMappings().get(0).getJdbcType());
                if (!handler.isEmpty()) assertInstanceOf(ClobTypeHandler.class, bound.getParameterMappings().get(0).getTypeHandler());
                assertFalse(bound.getSql().contains("正文"));
                var prepared = mock(PreparedStatement.class);
                new DefaultParameterHandler(statement, values, bound).setParameters(prepared);
                var reader = ArgumentCaptor.forClass(Reader.class);
                verify(prepared).setCharacterStream(eq(1), reader.capture(), eq(fullText.length()));
                var received = new StringWriter();
                reader.getValue().transferTo(received);
                assertEquals(fullText, received.toString());
                verify(prepared, never()).setString(anyInt(), anyString());

                values.put("value", null);
                var nullStatement = mock(PreparedStatement.class);
                new DefaultParameterHandler(statement, values, source.getBoundSql(values)).setParameters(nullStatement);
                verify(nullStatement).setNull(1, Types.CLOB);
                verify(nullStatement, never()).setCharacterStream(anyInt(), any(Reader.class), anyInt());

                // 空文本是零长度字符流，不能把它与 NULL 参数混为一谈。
                values.put("value", "");
                var emptyStatement = mock(PreparedStatement.class);
                new DefaultParameterHandler(statement, values, source.getBoundSql(values)).setParameters(emptyStatement);
                verify(emptyStatement).setCharacterStream(eq(1), any(Reader.class), eq(0));
                verify(emptyStatement, never()).setNull(anyInt(), anyInt());
            }
        }
    }
}
