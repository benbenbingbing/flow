package com.workflow.integration.database;

import com.workflow.integration.database.api.query.DatabaseQuerySql;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.query.DatabaseSort;
import com.workflow.integration.database.api.schema.SchemaType;
import java.util.List;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.scripting.xmltags.XMLLanguageDriver;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

/** 验证标准 MyBatis 脚本先生成方言片段，再绑定值；不使用自定义 SQL 重写器。 */
class MySqlQueryFragmentTest {
    @Test
    void patternValuesRequireTrustedColumnTypesAndLeaveFullTextUnconverted() {
        var dialect = DatabaseQueryDialects.forDatabaseId("MYSQL");
        for (var type : List.of(SchemaType.of(SchemaType.Kind.LONG), SchemaType.decimal(65, 30),
                SchemaType.of(SchemaType.Kind.TIMESTAMP), SchemaType.of(SchemaType.Kind.BOOLEAN))) {
            assertEquals("CAST(`row`.`order` AS CHAR)", dialect.patternValueExpression("row.order", type));
        }
        for (var type : List.of(SchemaType.string(4096), SchemaType.of(SchemaType.Kind.TEXT), SchemaType.of(SchemaType.Kind.LARGE_TEXT))) {
            assertEquals("`body`", dialect.patternValueExpression("body", type));
        }
        assertThrows(IllegalArgumentException.class, () -> dialect.patternValueExpression("id", null));
        for (String unsafe : List.of("id;--", "a..b", "a.b.c", "CAST(id AS CHAR)", "`id`", "id + 1")) {
            assertThrows(IllegalArgumentException.class, () -> dialect.patternValueExpression(unsafe, SchemaType.of(SchemaType.Kind.LONG)));
        }
    }

    @Test
    void integerIdentifierTextQuotesOnlyColumnsAndKeepsValuesBound() {
        assertEquals("CAST(`e`.`id` AS CHAR)", DatabaseQuerySql.integerIdentifierText("MYSQL", "e.id"));
        assertEquals("CAST(`id` AS CHAR)", DatabaseQuerySql.integerIdentifierText("MYSQL", "id"));
        for (String unsafe : new String[]{"e.id;--", "e..id", "e.", ".id", "a.b.c", "CAST(id AS CHAR)", "id OR 1=1", "`id`"}) {
            assertThrows(IllegalArgumentException.class, () -> DatabaseQuerySql.integerIdentifierText("MYSQL", unsafe));
        }
        assertThrows(IllegalArgumentException.class, () -> DatabaseQuerySql.integerIdentifierText("MYSQL", null));
        var bound = render("""
                <script>SELECT ${@com.workflow.integration.database.api.query.DatabaseQuerySql@integerIdentifierText(_databaseId, 'e.id')}
                FROM entity_definition e WHERE entity_code=#{code}</script>
                """, Map.of("code", "x' OR 1=1 --"));
        assertEquals("SELECT CAST(`e`.`id` AS CHAR) FROM entity_definition e WHERE entity_code=?", normalize(bound.getSql()));
        assertEquals(List.of("code"), bound.getParameterMappings().stream().map(value -> value.getProperty()).toList());
    }

    @Test
    void firstRowLockKeepsPredicatesBoundAndRequiresATieBreaker() {
        var dialect = DatabaseQueryDialects.forDatabaseId("MYSQL");
        var sql = dialect.firstForUpdate("order", "owner = #{owner} AND status = 'OPEN'",
                List.of(new DatabaseSort("create_time", true), new DatabaseSort("id", true)), "id");
        var bound = render(sql, Map.of("owner", "bad' OR 1=1 --"));
        assertEquals("SELECT * FROM `order` WHERE (owner = ? AND status = 'OPEN') ORDER BY `create_time` DESC, `id` DESC LIMIT 1 FOR UPDATE",
                normalize(bound.getSql()));
        assertEquals(List.of("owner"), bound.getParameterMappings().stream().map(value -> value.getProperty()).toList());
        assertThrows(IllegalArgumentException.class, () -> dialect.firstForUpdate("t", "x = #{x}", List.of(new DatabaseSort("time", true)), "id"));
    }

    @Test
    void firstRowLockRejectsUnsafeNamesAnonymousBindsAndAmbiguousOrder() {
        var dialect = DatabaseQueryDialects.forDatabaseId("MYSQL");
        var order = List.of(new DatabaseSort("id", false));
        assertThrows(IllegalArgumentException.class, () -> dialect.firstForUpdate("t; DROP TABLE x", "a = #{a}", order, "id"));
        assertThrows(IllegalArgumentException.class, () -> dialect.firstForUpdate("t", "a = ?", order, "id"));
        assertThrows(IllegalArgumentException.class, () -> dialect.firstForUpdate("t", "a = #{a}", List.of(new DatabaseSort("id DESC;--", true)), "id"));
        assertThrows(IllegalArgumentException.class, () -> dialect.firstForUpdate("t", "a = #{a}", List.of(new DatabaseSort("id", true), new DatabaseSort("ID", false)), "id"));
    }

    @Test
    void dynamicClauseKeepsPaginationValuesBoundInTheRenderedOrder() {
        var params = Map.of("owner", "a' OR 1=1 --", "offset", 7, "limit", 3);
        var bound = render("""
                <script>SELECT id FROM sample WHERE owner=#{owner} ORDER BY id
                ${@com.workflow.integration.database.api.query.DatabaseQuerySql@page(_databaseId, 'offset', 'limit')}
                </script>
                """, params);
        assertEquals("SELECT id FROM sample WHERE owner=? ORDER BY id LIMIT ?, ?", normalize(bound.getSql()));
        assertEquals(java.util.List.of("owner", "offset", "limit"),
                bound.getParameterMappings().stream().map(mapping -> mapping.getProperty()).toList());
        assertFalse(bound.getSql().contains("a' OR"));
    }

    @Test
    void singleCharacterConstantsAndNestedQueriesAreRenderedWithoutRuntimeParameters() {
        var bound = render("""
                <script>SELECT id FROM sample WHERE id=(SELECT id FROM other WHERE owner=#{owner}
                ${@com.workflow.integration.database.api.query.DatabaseQuerySql@page(_databaseId, '0', '1')})</script>
                """, Map.of("owner", "reader"));
        assertEquals("SELECT id FROM sample WHERE id=(SELECT id FROM other WHERE owner=? LIMIT 0, 1)", normalize(bound.getSql()));
        assertEquals(1, bound.getParameterMappings().size());
    }

    @Test
    void conditionsAndForeachStillBindBeforeThePageClause() {
        var bound = render("""
                <script>SELECT id FROM sample WHERE id IN
                <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
                <if test="end != null">AND create_time &lt; #{end}</if> ORDER BY id
                ${@com.workflow.integration.database.api.query.DatabaseQuerySql@page(_databaseId, '0', 'limit')}</script>
                """, Map.of("ids", java.util.List.of("a", "b"), "end", "2026-01-01", "limit", 2));
        assertTrue(bound.getSql().contains("create_time < ?"));
        assertTrue(normalize(bound.getSql()).endsWith("ORDER BY id LIMIT 0, ?"));
        assertEquals(4, bound.getParameterMappings().size());
        assertEquals("limit", bound.getParameterMappings().get(3).getProperty());
    }

    @Test
    void fragmentRejectsRawSqlAndMissingDatabaseSelection() {
        assertThrows(IllegalArgumentException.class, () -> DatabaseQuerySql.page("MYSQL", "0", "1; DELETE FROM x"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseQuerySql.page("MYSQL", "-1", "limit"));
        assertThrows(IllegalStateException.class, () -> DatabaseQuerySql.page(null, "0", "1"));
    }

    private BoundSql render(String script, Map<String, ?> parameters) {
        var configuration = new Configuration();
        configuration.setDatabaseId("MYSQL");
        return new XMLLanguageDriver().createSqlSource(configuration, script, Map.class).getBoundSql(parameters);
    }

    private String normalize(String sql) { return sql.replaceAll("\\s+", " ").trim(); }
}
