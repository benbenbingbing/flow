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
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.*;

import static com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import static com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 隔离真实 MySQL 验证侧表权限，不给主表补“虚拟列”掩盖存储位置错误。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlMultiValuePermissionDatabaseTest {
    private static final String TABLE = "biz_multi_permission";
    private static final String SPECIAL = "李'\\%_! #{input} ${input}";

    @Test
    void collectionMembersNegationAndEmptyRespectFieldTargetAndDeletionUnderBothModes() {
        for (boolean noBackslash : List.of(false, true)) try (var f = fixture(noBackslash)) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            records(h, "matched", "other", "empty", "old-target", "deleted-link", "wrong-field", "deleted-main");
            h.jdbc.update("UPDATE " + TABLE + " SET deleted=1 WHERE id='deleted-main'");
            link(h, "matched", "reviewers", "users", SPECIAL, 0);
            link(h, "matched", "reviewers", "users", "other", 0);
            link(h, "other", "reviewers", "users", "other", 0);
            link(h, "old-target", "reviewers", "old-users", SPECIAL, 0);
            link(h, "deleted-link", "reviewers", "users", SPECIAL, 1);
            link(h, "wrong-field", "different", "users", SPECIAL, 0);
            link(h, "deleted-main", "reviewers", "users", SPECIAL, 0);
            for (String operation : List.of("EQ", "IN", "CONTAINS")) {
                check(h, builder, rule("reviewerAlias", operation, operation.equals("IN") ? List.of(SPECIAL, "missing") : SPECIAL),
                        user(SPECIAL, null), "matched");
                check(h, builder, rule("reviewerAlias", operation, operation.equals("IN") ? List.of(" \t" + SPECIAL + "\n", SPECIAL) : " \t" + SPECIAL + "\n"),
                        user(SPECIAL, null), "matched");
            }
            for (String operation : List.of("NE", "NOT_IN", "NOT_CONTAINS")) {
                check(h, builder, rule("reviewers", operation, operation.equals("NOT_IN") ? List.of(SPECIAL) : SPECIAL),
                        user(SPECIAL, null), "other", "empty", "old-target", "deleted-link", "wrong-field");
                check(h, builder, rule("reviewers", operation, operation.equals("NOT_IN") ? List.of(" \t" + SPECIAL + "\n", SPECIAL) : " \t" + SPECIAL + "\n"),
                        user(SPECIAL, null), "other", "empty", "old-target", "deleted-link", "wrong-field");
            }
            check(h, builder, rule("reviewers", "EMPTY", null), user(SPECIAL, null), "empty", "old-target", "deleted-link", "wrong-field");
            check(h, builder, rule("reviewers", "NOT_EMPTY", null), user(SPECIAL, null), "matched", "other");
            check(h, builder, mapping("PERSONAL", "reviewerAlias"), user(SPECIAL, null), "matched");
        }
    }

    @Test
    void dictionaryCodesIdsAndValuesUseTheSameFirstActiveTargetAsRuntimeNormalization() {
        try (var f = fixture(false)) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            records(h, "first", "duplicate", "inactive", "removed", "other-dict", "old-target", "empty");
            h.jdbc.update("INSERT INTO sys_dict_item VALUES ('001','colors',?,?, '0',0),('999','colors',?,?, '0',0),"
                            + "('inactive','colors','OFF','OFF','1',0),('removed','colors','GONE','GONE','0',1),"
                            + "('another','other',?,?,'0',0)", SPECIAL, "red-value", SPECIAL, "red-value", SPECIAL, "red-value");
            link(h, "first", "tags", "dict-items", "001", 0);
            link(h, "duplicate", "tags", "dict-items", "999", 0);
            link(h, "inactive", "tags", "dict-items", "inactive", 0);
            link(h, "removed", "tags", "dict-items", "removed", 0);
            link(h, "other-dict", "tags", "dict-items", "another", 0);
            link(h, "old-target", "tags", "old-dict-items", "001", 0);
            for (String value : List.of(SPECIAL, "001", "red-value", " \t" + SPECIAL + "\n", " 001 ", " red-value ")) {
                check(h, builder, rule("tag_alias", "CONTAINS", value), user("u", null), "first");
            }
            check(h, builder, rule("tags", "IN", List.of("001", "999")), user("u", null), "first", "duplicate");
            check(h, builder, rule("tags", "IN", List.of(" 001 ", "\t999\n", "001")), user("u", null), "first", "duplicate");
            check(h, builder, rule("tags", "EQ", "OFF"), user("u", null));
            check(h, builder, rule("tags", "EQ", "GONE"), user("u", null));
            check(h, builder, rule("tags", "NOT_CONTAINS", SPECIAL), user("u", null),
                    "duplicate", "inactive", "removed", "other-dict", "old-target", "empty");
            check(h, builder, rule("tags", "NOT_CONTAINS", " \t" + SPECIAL + "\n"), user("u", null),
                    "duplicate", "inactive", "removed", "other-dict", "old-target", "empty");
            check(h, builder, rule("tags", "NOT_IN", List.of(" \t" + SPECIAL + "\n", SPECIAL)), user("u", null),
                    "duplicate", "inactive", "removed", "other-dict", "old-target", "empty");
            // 判空只问是否仍有当前目标的有效侧表行；不因字典项停用而把已保存的选择伪装成空集合。
            check(h, builder, rule("tags", "EMPTY", null), user("u", null), "old-target", "empty");
            check(h, builder, rule("tags", "NOT_EMPTY", null), user("u", null), "first", "duplicate", "inactive", "removed", "other-dict");
        }
    }

    @Test
    void scalarAndMultiDepartmentMappingsShareExactTreeBoundariesAndAliases() {
        for (boolean noBackslash : List.of(false, true)) try (var f = fixture(noBackslash)) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            String department = "dept!%_\\'";
            h.jdbc.update("INSERT INTO sys_organization VALUES (?,NULL),('child',?),('sibling',?),('parent','/')",
                    department, "/parent/" + department + "/child/", "/parent/dept!anythingX\\'/sibling/");
            records(h, "self", "child", "sibling", "empty");
            for (String id : List.of("self", "child", "sibling")) {
                String dept = id.equals("self") ? department : id;
                h.jdbc.update("UPDATE " + TABLE + " SET owner_ref=?,department_ref=? WHERE id=?", SPECIAL, dept, id);
                link(h, id, "departments", "organizations", dept, 0);
            }
            for (String alias : List.of("departmentCode", "department_ref", "departments", "department_alias")) {
                check(h, builder, mapping("DEPT", alias), user(SPECIAL, department), "self");
                check(h, builder, mapping("DEPT_TREE", alias), user(SPECIAL, department), "self", "child");
            }
            for (String alias : List.of("ownerCode", "owner_ref")) {
                check(h, builder, mapping("PERSONAL", alias), user(SPECIAL, department), "self", "child", "sibling");
            }
        }
    }

    @Test
    void allowsDelegatedMembersAndDeniesShareBindingsWithoutChangingNegation() {
        try (var f = fixture(false)) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            records(h, "own", "delegated", "denied", "other", "empty");
            link(h, "own", "reviewers", "users", SPECIAL, 0);
            link(h, "delegated", "reviewers", "users", "delegator", 0);
            link(h, "denied", "reviewers", "users", "delegator", 0);
            link(h, "other", "reviewers", "users", "someone", 0);
            h.jdbc.update("INSERT INTO sys_dict_item VALUES ('blocked-id','colors','BLOCKED','B','0',0)");
            link(h, "denied", "tags", "dict-items", "blocked-id", 0);
            var parameters = new LinkedHashMap<String, Object>(); parameters.put("permissionValue1", "reserved");
            String own = builder.buildFilterSql("asset", mapping("PERSONAL", "reviewers"), user(SPECIAL, null), parameters);
            String delegated = builder.buildFilterSql("asset", mapping("PERSONAL", "reviewers"), user("delegator", null), parameters);
            String denied = builder.buildFilterSql("asset", rule("tags", "CONTAINS", "BLOCKED"), user(SPECIAL, null), parameters);
            assertScope(h, "((" + own + ") OR (" + delegated + ")) AND NOT (" + denied + ")", parameters, "own", "delegated");
            assertEquals("reserved", parameters.get("permissionValue1"));
            assertTrue(parameters.containsValue(SPECIAL)); assertFalse((own + delegated + denied).contains(SPECIAL));
            // NOT(EMPTY) 必须回到存在语义，不能靠主表 NULL 或字符串比较近似集合非空。
            parameters.clear();
            String empty = builder.buildFilterSql("asset", rule("reviewers", "EMPTY", null), user(SPECIAL, null), parameters);
            assertScope(h, "NOT (" + empty + ")", parameters, "own", "delegated", "denied", "other");
        }
    }

    @Test
    void invalidDictionaryResolutionGuardsTheEntireRuleIncludingAndOrAndOuterDeny() {
        try (var f = fixture(false)) {
            var h = new Harness(f, EntityDataDynamicMapper.class); var builder = builder();
            records(h, "present", "absent");
            h.jdbc.update("UPDATE " + TABLE + " SET owner_ref=? WHERE id='present'", SPECIAL);
            h.jdbc.update("INSERT INTO sys_dict_item VALUES ('active','colors','ACTIVE','A','0',0),('inactive','colors','OFF','OFF','1',0)");
            link(h, "present", "tags", "dict-items", "active", 0);
            for (String missing : List.of("MISSING", "OFF")) {
                for (String operation : List.of("EQ", "NE", "NOT_CONTAINS")) {
                    var invalid = rule("tags", operation, missing);
                    for (var filter : List.of(invalid,
                            group("AND", invalid, rule("ownerCode", "EQ", "never")),
                            group("OR", invalid, rule("ownerCode", "EQ", SPECIAL)))) {
                        builder.validateFilter("asset", filter);
                        var parameters = new LinkedHashMap<String, Object>();
                        String sql = builder.buildFilterSql("asset", filter, user("u", null), parameters);
                        assertTrue(sql.startsWith("(CASE WHEN"));
                        assertScope(h, sql, parameters);
                        assertScope(h, "NOT (" + sql + ")", parameters);
                    }
                }
            }
            // 有效字典守卫也不能把同一规则里原本的 SQL UNKNOWN 变成 FALSE 后被 DENY 取反放行。
            var parameters = new LinkedHashMap<String, Object>();
            var filter = group("AND", rule("tags", "CONTAINS", "ACTIVE"), rule("departmentCode", "EQ", "d"));
            String sql = builder.buildFilterSql("asset", filter, user("u", null), parameters);
            assertScope(h, sql, parameters);
            assertScope(h, "NOT (" + sql + ")", parameters, "absent");
        }
    }

    private static Fixture fixture(boolean noBackslash) {
        var f = new Fixture(noBackslash);
        f.table(TABLE, "id VARCHAR(64) PRIMARY KEY,owner_ref VARCHAR(200),department_ref VARCHAR(64),deleted INT NOT NULL DEFAULT 0,create_time DATETIME DEFAULT CURRENT_TIMESTAMP");
        f.table(TABLE + "_multi", "id BIGINT AUTO_INCREMENT PRIMARY KEY,record_id VARCHAR(64),field_code VARCHAR(100),target_entity_id VARCHAR(64),target_record_id VARCHAR(200),deleted INT");
        f.table("sys_dict_item", "id VARCHAR(64) PRIMARY KEY,dict_code VARCHAR(64),item_code VARCHAR(200),item_value VARCHAR(200),status CHAR(1),deleted INT");
        f.table("sys_organization", "id VARCHAR(64),path VARCHAR(500)");
        return f;
    }

    private static PermissionSqlBuilder builder() {
        var definitions = mock(EntityDefinitionMapper.class); var fields = mock(EntityFieldMapper.class);
        var entity = new EntityDefinition(); entity.setId("entity"); entity.setEntityCode("asset"); entity.setPhysicalTableName(TABLE);
        var dict = new EntityDefinition(); dict.setId("dict-items"); dict.setEntityCode("sys_dict_item");
        when(definitions.findByEntityCode("asset")).thenReturn(Optional.of(entity));
        when(definitions.findByEntityCode("sys_dict_item")).thenReturn(Optional.of(dict));
        var reviewers = field("reviewers", "reviewerAlias", EntityField.FieldType.MULTI_REFERENCE); reviewers.setRefEntityId("users");
        var departments = field("departments", "department_alias", EntityField.FieldType.MULTI_REFERENCE); departments.setRefEntityId("organizations");
        var tags = field("tags", "tag_alias", EntityField.FieldType.MULTI_SELECT); tags.setDictType("colors");
        when(fields.findByEntityId("entity")).thenReturn(List.of(reviewers, departments, tags,
                field("ownerCode", "owner_ref", EntityField.FieldType.USER), field("departmentCode", "department_ref", EntityField.FieldType.DEPT)));
        return new PermissionSqlBuilder(definitions, fields, null, List.of(), DatabaseQueryDialects.forVendor(DatabaseVendor.MYSQL));
    }

    private static void records(Harness h, String... ids) {
        for (String id : ids) h.jdbc.update("INSERT INTO " + TABLE + "(id) VALUES (?)", id);
    }
    private static void link(Harness h, String record, String field, String target, String value, int deleted) {
        h.jdbc.update("INSERT INTO " + TABLE + "_multi(record_id,field_code,target_entity_id,target_record_id,deleted) VALUES (?,?,?,?,?)", record, field, target, value, deleted);
    }
    private static void check(Harness h, PermissionSqlBuilder builder, FilterConfigDTO filter, SysUser user, String... expected) {
        builder.validateFilter("asset", filter); var parameters = new LinkedHashMap<String, Object>();
        String sql = builder.buildFilterSql("asset", filter, user, parameters);
        assertFalse(sql.contains(SPECIAL)); assertScope(h, sql, parameters, expected);
    }
    private static void assertScope(Harness h, String sql, Map<String, Object> parameters, String... expected) {
        var mapper = h.mapper(EntityDataDynamicMapper.class);
        assertEquals(Set.of(expected), new HashSet<>(mapper.selectPageWithPermission(TABLE, sql, parameters, 0, 30).stream().map(row -> String.valueOf(row.get("id"))).toList()), sql);
        assertEquals(expected.length, mapper.countWithPermission(TABLE, sql, parameters), sql);
    }
    private static EntityField field(String code, String column, EntityField.FieldType type) {
        var field = new EntityField(); field.setFieldCode(code); field.setDbColumnName(column); field.setFieldType(type); return field;
    }
    private static SysUser user(String id, String dept) { var user = new SysUser(); user.setId(id); user.setDeptId(dept); return user; }
    private static FilterConfigDTO rule(String field, String operation, Object value) {
        var filter = new FilterConfigDTO(); filter.setType("RULE"); var node = new EntityActionRuleDTO.RuleNode();
        node.setType("FIELD"); node.setField(field); node.setOperator(operation); node.setValue(value); filter.setRoot(node); return filter;
    }
    private static FilterConfigDTO mapping(String type, String field) {
        var filter = new FilterConfigDTO(); filter.setType(type); var mapping = new FilterConfigDTO.FieldMappingDTO();
        mapping.setUserField(field); mapping.setDeptField(field); filter.setFieldMapping(mapping); return filter;
    }
    private static FilterConfigDTO group(String logic, FilterConfigDTO... filters) {
        var filter = new FilterConfigDTO(); filter.setType("RULE"); var node = new EntityActionRuleDTO.RuleNode();
        node.setType("GROUP"); node.setLogic(logic); node.setChildren(Arrays.stream(filters).map(FilterConfigDTO::getRoot).toList());
        filter.setRoot(node); return filter;
    }
}
