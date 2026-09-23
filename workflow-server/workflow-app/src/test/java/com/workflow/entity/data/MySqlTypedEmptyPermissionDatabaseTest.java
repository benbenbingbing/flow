package com.workflow.entity.data;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.api.response.FilterConfigDTO;
import com.workflow.entity.permission.application.PermissionSqlBuilder;
import com.workflow.integration.database.api.query.DatabaseQueryDialect;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.schema.SchemaType;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 类型化权限判空与真实 MySQL 结果对照；文本期望来自 Java isBlank，不复用 SQL 生成逻辑。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlTypedEmptyPermissionDatabaseTest {
    private static final DatabaseQueryDialect MYSQL = DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL);

    @Test void textAndLargeTextMatchJavaWhitespaceWithoutTruncatingTheTail() {
        try (var f = new Fixture()) {
            f.table("biz_blank", "id INT PRIMARY KEY,value_text LONGTEXT");
            var h = new Harness(f, EntityDataDynamicMapper.class);
            List<String> values = new ArrayList<>(Arrays.asList(null, "", "   ", "\t\n\r", "0", "false", "a\nb", "中文"));
            // 穷举当前 Java 规范中的空白字符，避免正则字符集遗漏；相似但非空白的字符必须保留。
            for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
                if (Character.isWhitespace(cp)) values.add(new String(Character.toChars(cp)));
            }
            values.addAll(List.of("\u0000", "\u0085", "\u00a0", "\u180e", "\u2007", "\u202f", "\u200b", "\ufeff", "'\\_[]%"));
            values.add(" \t".repeat(8000));
            values.add(" \t".repeat(8000) + "尾");
            values.add("前" + " \t".repeat(8000));
            List<Integer> empty = new ArrayList<>(), nonEmpty = new ArrayList<>();
            for (int i = 0; i < values.size(); i++) {
                String value = values.get(i);
                h.jdbc.update("INSERT INTO biz_blank VALUES (?,?)", i, value);
                (value == null || value.isBlank() ? empty : nonEmpty).add(i);
            }
            for (String collation : List.of("utf8mb4_unicode_ci", "utf8mb4_0900_ai_ci", "utf8mb4_bin")) {
                h.jdbc.execute("ALTER TABLE biz_blank MODIFY value_text LONGTEXT COLLATE " + collation);
                for (var kind : List.of(SchemaType.Kind.STRING, SchemaType.Kind.TEXT, SchemaType.Kind.LARGE_TEXT)) {
                    assertEquals(empty, h.jdbc.queryForList("SELECT id FROM biz_blank b WHERE "
                            + MYSQL.emptyValuePredicate("b.value_text", kind, true) + " ORDER BY id", Integer.class), collation);
                    assertEquals(nonEmpty, h.jdbc.queryForList("SELECT id FROM biz_blank b WHERE "
                            + MYSQL.emptyValuePredicate("b.value_text", kind, false) + " ORDER BY id", Integer.class), collation);
                }
            }
        }
    }

    @Test void scalarZeroFalseAndDatesOnlyTreatSqlNullAsEmpty() {
        try (var f = new Fixture()) {
            f.table("biz_scalar", "id VARCHAR(64),i INT,l BIGINT,d DECIMAL(18,4),b TINYINT(1),by_value TINYINT,day_value DATE,instant_value DATETIME(6)");
            var h = new Harness(f, EntityDataDynamicMapper.class);
            h.jdbc.update("INSERT INTO biz_scalar VALUES ('null',NULL,NULL,NULL,NULL,NULL,NULL,NULL),('zero',0,0,0,0,0,'2026-01-01','2026-01-01'),('value',1,9007199254740993,-1.25,1,1,'2026-09-22','2026-09-22 12:13:14.123456')");
            var columns = Map.of("i", SchemaType.Kind.INTEGER, "l", SchemaType.Kind.LONG, "d", SchemaType.Kind.DECIMAL,
                    "b", SchemaType.Kind.BOOLEAN, "by_value", SchemaType.Kind.BYTE, "day_value", SchemaType.Kind.DATE,
                    "instant_value", SchemaType.Kind.TIMESTAMP);
            for (var entry : columns.entrySet()) {
                assertEquals(List.of("null"), h.jdbc.queryForList("SELECT id FROM biz_scalar WHERE "
                        + MYSQL.emptyValuePredicate(entry.getKey(), entry.getValue(), true) + " ORDER BY id", String.class));
                assertEquals(List.of("value", "zero"), h.jdbc.queryForList("SELECT id FROM biz_scalar WHERE "
                        + MYSQL.emptyValuePredicate(entry.getKey(), entry.getValue(), false) + " ORDER BY id", String.class));
            }
        }
    }

    @Test void publishedFieldTypesAndAliasesApplyToListCountGroupsAndAuditDates() {
        try (var f = new Fixture()) {
            f.table("biz_permission", "id VARCHAR(64),amount DECIMAL(18,2),approved TINYINT(1),starts_at DATETIME,`order` LONGTEXT,deleted INT,create_time DATETIME");
            var h = new Harness(f, EntityDataDynamicMapper.class); var mapper = h.mapper(EntityDataDynamicMapper.class);
            h.jdbc.update("INSERT INTO biz_permission VALUES ('null',NULL,NULL,NULL,NULL,0,NULL),('zero',0,0,'2026-01-01','',0,'2026-01-01'),('value',1,1,'2026-01-01','present',0,'2026-01-01'),('deleted',NULL,NULL,NULL,NULL,1,NULL)");
            var builder = builder(List.of(field("amount", "amount", EntityField.FieldType.DECIMAL),
                    field("approved", "approved", EntityField.FieldType.BOOLEAN), field("startsAt", "starts_at", EntityField.FieldType.DATETIME),
                    field("description", "order", EntityField.FieldType.TEXT)));
            var user = new SysUser(); user.setId("u");
            for (String field : List.of("amount", "approved", "startsAt", "starts_at", "create_time")) {
                for (boolean empty : List.of(true, false)) {
                    var filter = filter(rule(field, empty ? "EMPTY" : "NOT_EMPTY")); builder.validateFilter("expense", filter);
                    String predicate = builder.buildFilterSql("expense", filter, user);
                    List<String> expected = empty ? List.of("null") : List.of("value", "zero");
                    assertEquals(expected, mapper.selectPageWithPermission("biz_permission", predicate, Map.of(), 0, 100)
                            .stream().map(row -> row.get("id").toString()).sorted().toList(), field);
                    assertEquals(expected.size(), mapper.countWithPermission("biz_permission", predicate, Map.of()), field);
                }
            }
            var root = new EntityActionRuleDTO.RuleNode(); root.setType("GROUP"); root.setLogic("AND");
            root.setChildren(List.of(rule("description", "EMPTY"), rule("amount", "NOT_EMPTY")));
            String predicate = builder.buildFilterSql("expense", filter(root), user);
            assertEquals(List.of("zero"), mapper.selectPageWithPermission("biz_permission", predicate, Map.of(), 0, 100)
                    .stream().map(row -> row.get("id").toString()).toList());
            assertEquals(1, mapper.countWithPermission("biz_permission", predicate, Map.of()));
        }
    }

    @Test void missingTypeAndForgedFieldFailClosedForBothEmptyOperators() {
        try (var f = new Fixture()) {
            f.table("biz_guard", "id VARCHAR(64),legacy_value VARCHAR(100),deleted INT");
            var h = new Harness(f, EntityDataDynamicMapper.class);
            h.jdbc.update("INSERT INTO biz_guard VALUES ('existing','value',0)");
            var builder = builder(List.of(field("legacy", "legacy_value", null)));
            var user = new SysUser(); user.setId("u");
            for (String field : List.of("legacy", "unknown", "amount) OR 1=1 --")) {
                for (String operator : List.of("EMPTY", "NOT_EMPTY")) {
                    var filter = filter(rule(field, operator));
                    assertThrows(IllegalArgumentException.class, () -> builder.validateFilter("expense", filter));
                    String predicate = builder.buildFilterSql("expense", filter, user);
                    assertEquals("1=0", predicate);
                    assertEquals(0, h.mapper(EntityDataDynamicMapper.class).countWithPermission("biz_guard", predicate, Map.of()));
                }
            }
            assertEquals(1, h.mapper(EntityDataDynamicMapper.class).count("biz_guard"));
            assertThrows(IllegalArgumentException.class, () -> MYSQL.emptyValuePredicate("x) OR 1=1 --", SchemaType.Kind.STRING, true));
            assertThrows(IllegalArgumentException.class, () -> MYSQL.emptyValuePredicate("x", null, true));
        }
    }

    private static EntityActionRuleDTO.RuleNode rule(String field, String operator) {
        var node = new EntityActionRuleDTO.RuleNode(); node.setType("FIELD"); node.setField(field); node.setOperator(operator); return node;
    }

    private static FilterConfigDTO filter(EntityActionRuleDTO.RuleNode node) {
        var filter = new FilterConfigDTO(); filter.setType("RULE"); filter.setRoot(node); return filter;
    }

    private static EntityField field(String code, String column, EntityField.FieldType type) {
        var field = new EntityField(); field.setFieldCode(code); field.setDbColumnName(column); field.setFieldType(type); return field;
    }

    private static PermissionSqlBuilder builder(List<EntityField> fields) {
        var definitions = mock(EntityDefinitionMapper.class); var fieldMapper = mock(EntityFieldMapper.class);
        var definition = new EntityDefinition(); definition.setId("definition"); definition.setEntityCode("expense");
        when(definitions.findByEntityCode("expense")).thenReturn(Optional.of(definition));
        when(fieldMapper.findByEntityId("definition")).thenReturn(fields);
        return new PermissionSqlBuilder(definitions, fieldMapper, null, List.of(), MYSQL);
    }
}
