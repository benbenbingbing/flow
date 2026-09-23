package com.workflow.entity.data;

import com.workflow.core.database.JdbcLockedRow;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityFieldMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.form.application.FormUniqueRulePolicy;
import com.workflow.entity.form.application.PublishedFormConditionEvaluator;
import com.workflow.entity.form.application.model.FormUniqueRule;
import com.workflow.entity.form.infrastructure.adapter.DynamicFormUniqueConflictQuery;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueValueGateRepository;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.EntityFormUniqueValueGateRepository.GateKey;
import com.workflow.entity.form.uniqueness.infrastructure.persistence.mapper.EntityFormUniqueValueGateMapper;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import org.apache.ibatis.type.JdbcType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.TransactionDefinition;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 真实候选 SQL + 共享 Java 规则对照，覆盖漏报边界和 gate 后的当前读取；所有表随机隔离。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlFormUniqueCandidatesDatabaseTest {
    private static final String TABLE = "biz_form_unique";

    @Test void candidateSupersetPreservesEveryJavaConflictAcrossCollations() {
        try (var f = new Fixture()) {
            table(f, "VARCHAR(2000)"); var h = harness(f); var mapper = h.mapper(EntityDataDynamicMapper.class);
            var query = query(h, EntityField.FieldType.STRING); var policy = policy(h); var rule = rule("STRING", false, false);
            List<String> values = new ArrayList<>(Arrays.asList(null, "", " ", "\t\n", "Project A", " project a ", "not a candidate"));
            // 所有可打印 ASCII 都参与模式转义验证；所有 trim 控制字符都应保留给 Java 比较。
            for (char c = 32; c <= 126; c++) values.add("p" + c + "q");
            for (char c = 0; c <= 32; c++) values.add(c + "I" + c);
            values.addAll(List.of("İ", "i\u0307", "I", "i", "ı", "K", "K", "k", "ΟΣ", "ος", "οσ", "Straße", "STRASSE",
                    "中文", "😀", "\u2003", "\u00a0", "\u202f", "\ufeff", "\u007f", "a\nb", "O'Reilly\\%_[]{}.*+?^$|()"));
            for (int index = 0; index < values.size(); index++) insert(h, "r" + index, values.get(index), 0, "OPEN");
            insert(h, "deleted", "Project A", 1, "OPEN");
            Set<String> targets = new LinkedHashSet<>();
            for (String value : values) targets.add(normalized(policy, rule, value));
            for (String collation : List.of("utf8mb4_unicode_ci", "utf8mb4_0900_ai_ci", "utf8mb4_bin", "utf8mb4_turkish_ci")) {
                h.jdbc.execute("ALTER TABLE " + TABLE + " MODIFY `order` VARCHAR(2000) COLLATE " + collation);
                List<Map<String, Object>> all = mapper.selectList(TABLE).stream().map(MySqlFormUniqueCandidatesDatabaseTest::fieldRow).toList();
                for (String target : targets) {
                    Set<String> expected = conflicts(policy, rule, target, all);
                    var candidates = query.findCandidates("asset", "displayName", target, null);
                    assertEquals(expected, conflicts(policy, rule, target, candidates), collation + " target=" + target);
                }
                var ordinary = mapper.selectFormUniqueCandidates(TABLE, "order", "project a", null);
                assertTrue(ids(ordinary).containsAll(Set.of("r4", "r5")));
                assertFalse(ids(ordinary).contains("r6"), "常见 ASCII 值保留数据库预筛，不能退化成无条件返回所有行");
                assertFalse(ids(ordinary).contains("deleted"));
            }
        }
    }

    @Test void boundPatternsEscapeMetacharactersAndStayBelowVendorPatternLimit() {
        for (boolean noBackslash : List.of(false, true)) try (var f = new Fixture(noBackslash)) {
            table(f, "VARCHAR(2000)"); var h = harness(f); var mapper = h.mapper(EntityDataDynamicMapper.class);
            var query = query(h, EntityField.FieldType.STRING); var policy = policy(h); var rule = rule("STRING", false, false);
            String special = "' OR 1=1 -- ${value} #{value} \\ [] . ^ $ | ? * + ( ) { } _ %";
            String longValue = "a".repeat(1300) + "final";
            insert(h, "special", special, 0, "OPEN"); insert(h, "different", "different", 0, "OPEN");
            insert(h, "long", longValue, 0, "OPEN"); insert(h, "same-prefix", "a".repeat(1300) + "other", 0, "OPEN");
            h.tx.executeWithoutResult(status -> {
                        String mode = h.jdbc.queryForObject("SELECT @@SESSION.sql_mode", String.class);
                        assertEquals(noBackslash, mode.contains("NO_BACKSLASH_ESCAPES"));
                        assertEquals(special, h.jdbc.queryForObject("SELECT ?", String.class, special));
                        h.session.clearCache();
                        for (String value : List.of(special, longValue)) {
                            String normalized = normalized(policy, rule, value);
                            assertEquals(Set.of(value.equals(special) ? "special" : "long"), conflicts(policy, rule, normalized,
                                    query.findCandidates("asset", "displayName", normalized, null)));
                            assertTrue(query.findCandidates("asset", "displayName", normalized, "special").stream()
                                    .noneMatch(row -> "special".equals(row.get("id"))));
                            var args = new HashMap<String, Object>();
                            args.put("tableName", TABLE); args.put("columnName", "order"); args.put("normalizedValue", normalized);
                            var bound = h.session.getConfiguration().getMappedStatement(EntityDataDynamicMapper.class.getName()
                                    + ".selectFormUniqueCandidates").getBoundSql(args);
                            assertFalse(bound.getSql().contains(normalized));
                            assertFalse(bound.getSql().contains("CAST(`order`"));
                            assertTrue(bound.getSql().contains("CAST(? AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin"));
                            for (var mapping : bound.getParameterMappings()) {
                                assertEquals(JdbcType.VARCHAR, mapping.getJdbcType());
                                String pattern = (String) args.get(mapping.getProperty());
                                assertTrue(pattern.getBytes(StandardCharsets.UTF_8).length < 512);
                            }
                        }
                        // 长值只预筛前缀，但最终规则必须区分尾部；数据本身不能被截断。
                        assertEquals(Set.of("long", "same-prefix"), ids(mapper.selectFormUniqueCandidates(TABLE, "order", longValue, null)));
            });
            var dialect = DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL);
            assertThrows(IllegalArgumentException.class, () -> dialect.regularExpressionPredicate("order;DROP", "?"));
            assertThrows(IllegalArgumentException.class, () -> dialect.regularExpressionPredicate("order", "'x' OR 1=1"));
        }
    }

    @Test void largeTextKeepsFullTailAndConditionalRulesIgnoreDeletedAndSelf() {
        try (var f = new Fixture()) {
            table(f, "LONGTEXT"); var h = harness(f); var query = query(h, EntityField.FieldType.TEXT);
            var policy = policy(h); var rule = rule("TEXT", false, true);
            String longValue = "AbC中文".repeat(3500) + "tail";
            insert(h, "match", "\t " + longValue + "\r", 0, "OPEN");
            insert(h, "different-tail", "AbC中文".repeat(3500) + "other", 0, "OPEN");
            insert(h, "closed", longValue, 0, "CLOSED"); insert(h, "deleted", longValue, 1, "OPEN");
            insert(h, "null", null, 0, "OPEN"); insert(h, "blank", " \t\u2003".repeat(5000), 0, "OPEN");
            String target = normalized(policy, rule, longValue);
            assertEquals(Set.of("match"), conflicts(policy, rule, target, query.findCandidates("asset", "displayName", target, null)));
            assertTrue(conflicts(policy, rule, target, query.findCandidates("asset", "displayName", target, "match")).isEmpty());
            assertEquals(Set.of("blank", "null"), conflicts(policy, rule, "", query.findCandidates("asset", "displayName", "", null)));
            var ignoredBlank = rule("TEXT", true, false);
            assertTrue(conflicts(policy, ignoredBlank, "", query.findCandidates("asset", "displayName", "", null)).isEmpty());
            h.tx.executeWithoutResult(status -> {
                assertEquals(Set.of("match"), conflicts(policy, rule, target,
                        query.findCandidatesForAuthoritativeCheck("asset", "displayName", target, null)));
                h.jdbc.update("UPDATE " + TABLE + " SET `order`='changed' WHERE id='match'");
                status.setRollbackOnly();
            });
            assertEquals(Set.of("match"), conflicts(policy, rule, target, query.findCandidates("asset", "displayName", target, null)));
        }
    }

    @Test void gateThenCurrentReadHasOneWinnerDespiteOldRepeatableReadSnapshots() throws Exception {
        for (var type : List.of(EntityField.FieldType.STRING, EntityField.FieldType.TEXT)) {
            try (var f = new Fixture()) {
                table(f, type == EntityField.FieldType.STRING ? "VARCHAR(2000)" : "LONGTEXT");
                f.table("entity_form_unique_value_gate", "scope_key VARCHAR(255),value_hash CHAR(64),PRIMARY KEY(scope_key,value_hash)");
                var h = harness(f); h.tx.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
                var query = query(h, type); var policy = policy(h); var rule = rule(type.name(), false, false);
                var gates = new EntityFormUniqueValueGateRepository(h.mapper(EntityFormUniqueValueGateMapper.class),
                        new JdbcLockedRow(h.jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL)));
                var gate = new GateKey("ENTITY:asset:displayName", "a".repeat(64));
                var ready = new CyclicBarrier(6);
                String base = type == EntityField.FieldType.STRING ? "I" : "A中文".repeat(3000);
                String target = normalized(policy, rule, base);
                var winners = concurrent(6, index -> h.tx.execute(status -> {
                    assertTrue(query.findCandidates("asset", "displayName", target, null).isEmpty());
                    try { ready.await(10, TimeUnit.SECONDS); }
                    catch (Exception error) { throw new AssertionError(error); }
                    gates.lockAll(List.of(gate));
                    if (!conflicts(policy, rule, target, query.findCandidatesForAuthoritativeCheck(
                            "asset", "displayName", target, null)).isEmpty()) return false;
                    insert(h, "winner-" + index, "\t" + base.toLowerCase(Locale.ROOT) + "\r", 0, "OPEN");
                    return true;
                }));
                assertEquals(1, winners.stream().filter(Boolean::booleanValue).count());
                assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM " + TABLE, Integer.class));
            }
        }
    }

    private static void table(Fixture f, String type) {
        f.table(TABLE, "id VARCHAR(64) PRIMARY KEY,`order` " + type + ",status VARCHAR(32),deleted INT NOT NULL DEFAULT 0,create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP");
    }
    private static Harness harness(Fixture f) { return new Harness(f, EntityDataDynamicMapper.class, EntityFormUniqueValueGateMapper.class); }
    private static void insert(Harness h, String id, String value, int deleted, String state) {
        h.jdbc.update("INSERT INTO " + TABLE + " (id,`order`,status,deleted) VALUES (?,?,?,?)", id, value, state, deleted);
    }
    private static FormUniqueRulePolicy policy(Harness h) {
        return new FormUniqueRulePolicy(h.json, new PublishedFormConditionEvaluator(h.json));
    }
    private static FormUniqueRule rule(String type, boolean ignoreBlank, boolean conditional) {
        Map<String, Object> condition = Map.of("version", 1, "root", Map.of("type", "GROUP", "logic", "AND", "children",
                List.of(Map.of("type", "CONDITION", "property", "status", "operator", "==", "value", "OPEN"))));
        return new FormUniqueRule(1, "uniqueName", "displayName", "名称", type,
                conditional ? FormUniqueRule.Mode.CONDITIONAL : FormUniqueRule.Mode.GLOBAL, ignoreBlank,
                FormUniqueRule.Normalization.TRIM_CASE_INSENSITIVE, condition, "重复", null);
    }
    private static DynamicFormUniqueConflictQuery query(Harness h, EntityField.FieldType type) {
        var definitions = mock(EntityDefinitionMapper.class); var fields = mock(EntityFieldMapper.class);
        var tables = mock(DynamicTableService.class); var definition = new EntityDefinition(); definition.setId("definition");
        var field = new EntityField(); field.setFieldCode("displayName"); field.setDbColumnName("order"); field.setFieldType(type);
        when(definitions.findByEntityCode("asset")).thenReturn(Optional.of(definition));
        when(fields.findByEntityId("definition")).thenReturn(List.of(field)); when(tables.getTableName("asset")).thenReturn(TABLE);
        return new DynamicFormUniqueConflictQuery(definitions, fields, h.mapper(EntityDataDynamicMapper.class), tables, new EntityRuntimeRecordMapper(h.json));
    }
    private static String normalized(FormUniqueRulePolicy policy, FormUniqueRule rule, String value) {
        var record = new HashMap<String, Object>(); record.put("displayName", value); record.put("status", "OPEN");
        return policy.prepare(rule, Map.of(), record).normalizedValue();
    }
    private static Map<String, Object> fieldRow(Map<String, Object> row) {
        var result = new LinkedHashMap<>(row); result.put("displayName", row.get("order")); return result;
    }
    private static Set<String> conflicts(FormUniqueRulePolicy policy, FormUniqueRule rule, String target, List<Map<String, Object>> candidates) {
        return ids(candidates.stream().filter(row -> policy.conflicts(rule, target, row)).toList());
    }
    private static Set<String> ids(List<Map<String, Object>> rows) {
        return new HashSet<>(rows.stream().map(row -> (String) row.get("id")).toList());
    }
}
