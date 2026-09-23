package com.workflow.entity.data;

import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeBindingMapper;
import com.workflow.entity.permission.infrastructure.persistence.mapper.EntityListScopeDelegationMapper;
import com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopeDelegation;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListActionMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListFieldMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;
import com.workflow.entity.data.MySqlWriteAttemptDatabaseTest.Harness;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

/** 用真实 MyBatis-Plus 执行器验证 Wrapper 分组、逻辑删除与原 MySQL 时间边界。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlScopeWrapperDatabaseTest {
    @Test void delegationWrapperKeepsScopeGuardsAndInclusiveDatabaseTimeBoundaries() {
        try (var f = new Fixture()) {
            delegationTable(f);
            var h = new Harness(f, EntityListScopeDelegationMapper.class);
            h.tx.executeWithoutResult(status -> {
                // 固定测试连接的数据库时间，精确覆盖相等和相差一微秒的边界，避免跨秒抖动。
                h.jdbc.execute("SET timestamp = 1700000000");
                for (String zone : List.of("+00:00", "+09:00", "-07:00")) {
                    h.jdbc.execute("SET time_zone = '" + zone + "'");
                    h.jdbc.update("DELETE FROM entity_list_scope_delegation");
                    LocalDateTime now = h.jdbc.queryForObject("SELECT CURRENT_TIMESTAMP", LocalDateTime.class);
                    delegation(h, "global-null", "user", null, 1, 0, null, null);
                    delegation(h, "global-empty", "user", "", 1, 0, null, null);
                    delegation(h, "entity", "user", "asset", 1, 0, null, null);
                    delegation(h, "other-entity", "user", "other", 1, 0, null, null);
                    delegation(h, "disabled", "user", null, 0, 0, null, null);
                    delegation(h, "deleted", "user", "", 1, 1, null, null);
                    delegation(h, "other-user", "another", null, 1, 0, null, null);
                    delegation(h, "start-boundary", "user", "asset", 1, 0, now, null);
                    delegation(h, "end-boundary", "user", "asset", 1, 0, null, now);
                    delegation(h, "future", "user", null, 1, 0, now.plusNanos(1000), null);
                    delegation(h, "expired", "user", "", 1, 0, null, now.minusNanos(1000));
                    delegation(h, "quoted", "user", "asset' OR 1=1 --", 1, 0, null, null);
                    h.session.clearCache();
                    var mapper = h.mapper(EntityListScopeDelegationMapper.class);
                    assertEquals(Set.of("global-null", "global-empty", "entity", "start-boundary", "end-boundary"),
                            ids(mapper.findActiveByToUserId("user", "asset")));
                    assertEquals(Set.of("global-null", "global-empty"), ids(mapper.findActiveByToUserId("user", null)));
                    assertEquals(Set.of("global-null", "global-empty"), ids(mapper.findActiveByToUserId("user", "")));
                    assertEquals(Set.of("global-null", "global-empty", "quoted"),
                            ids(mapper.findActiveByToUserId("user", "asset' OR 1=1 --")));
                    assertTrue(mapper.findActiveByToUserId("user' OR 1=1 --", "asset").isEmpty());
                    // 以原生产查询为对照，验证 Wrapper 自动追加的逻辑删除也参与整个 OR 分组。
                    var legacy = h.jdbc.queryForList("SELECT id FROM entity_list_scope_delegation "
                            + "WHERE to_user_id=? AND enabled=1 AND deleted=0 "
                            + "AND (entity_code IS NULL OR entity_code='' OR entity_code=?) "
                            + "AND (start_time IS NULL OR start_time<=NOW()) "
                            + "AND (end_time IS NULL OR end_time>=NOW())", String.class, "user", "asset");
                    assertEquals(Set.copyOf(legacy), ids(mapper.findActiveByToUserId("user", "asset")));
                }
                status.setRollbackOnly();
            });
        }
    }

    @Test void bindingWrapperPreservesOrderingAliasesAndPhysicalDraftCleanup() {
        try (var f = new Fixture()) {
            f.table("entity_list_scope_binding", "id VARCHAR(64) PRIMARY KEY,entity_code VARCHAR(64),policy_id VARCHAR(64),"
                    + "list_key VARCHAR(64),match_config TEXT,rule_effect VARCHAR(32),enabled INT,effective_start_time DATETIME,"
                    + "effective_end_time DATETIME,created_by VARCHAR(64),create_time DATETIME,update_time DATETIME,deleted INT");
            var h = new Harness(f, EntityListScopeBindingMapper.class);
            h.jdbc.update("INSERT INTO entity_list_scope_binding(id,entity_code,create_time,deleted) VALUES "
                    + "('late','asset','2026-01-02',0),('early','asset','2026-01-01',0),"
                    + "('deleted','asset','2025-01-01',1),('other','other','2024-01-01',0)");
            var mapper = h.mapper(EntityListScopeBindingMapper.class);
            var rows = mapper.findByEntityCode("asset");
            assertEquals(List.of("early", "late"), rows.stream().map(row -> row.getId()).toList());
            assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), rows.get(0).getCreatedAt());
            assertTrue(mapper.findByEntityCode("asset' OR 1=1 --").isEmpty());
            assertEquals(1, mapper.purgeDeletedByEntityCode("asset"));
            assertEquals(3, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_list_scope_binding", Integer.class));
        }
    }

    @Test void listFieldAndActionQueriesKeepOrderingAndDeletedDraftLocking() {
        try (var f = new Fixture()) {
            f.table("entity_list_field", "id VARCHAR(64) PRIMARY KEY,list_config_id VARCHAR(64),field_id VARCHAR(64),"
                    + "field_code VARCHAR(64),field_name VARCHAR(64),sort_order INT,order_key BIGINT,revision INT,width INT,"
                    + "show_in_list INT,is_query INT,query_type VARCHAR(64),align VARCHAR(64),data_source_type VARCHAR(64),"
                    + "data_source_config TEXT,interface_extension_id VARCHAR(64),render_component VARCHAR(64),formatter TEXT,"
                    + "column_config TEXT,query_config TEXT,render_config TEXT,template_id VARCHAR(64),template_version INT,"
                    + "local_overrides_document TEXT,deleted INT,create_time DATETIME,update_time DATETIME");
            f.table("entity_list_action", "id VARCHAR(64) PRIMARY KEY,list_config_id VARCHAR(64),position VARCHAR(32),"
                    + "button_key VARCHAR(64),button_type VARCHAR(64),button_label VARCHAR(64),icon VARCHAR(64),style_type VARCHAR(64),"
                    + "link_mode INT,custom_mode VARCHAR(64),handler_code VARCHAR(64),permission_code VARCHAR(64),"
                    + "sort_order INT,order_key BIGINT,revision INT,enabled INT,action_params_document TEXT,availability_rule_document TEXT,"
                    + "template_id VARCHAR(64),template_version INT,local_overrides_document TEXT,create_time DATETIME,update_time DATETIME,deleted INT");
            var h = new Harness(f, EntityListFieldMapper.class, EntityListActionMapper.class);
            h.tx.executeWithoutResult(status -> {
                h.jdbc.update("INSERT INTO entity_list_field(id,list_config_id,order_key,sort_order,deleted) VALUES "
                        + "('f1','list',10,2,0),('f2','list',10,1,0),('f3','list',5,0,0),"
                        + "('deleted','list',1,0,1),('other','other',0,0,0)");
                h.jdbc.update("INSERT INTO entity_list_action(id,list_config_id,position,order_key,sort_order,create_time,deleted) VALUES "
                        + "('a1','list','toolbar',10,0,'2026-01-02',0),('a2','list','toolbar',10,0,'2026-01-01',0),"
                        + "('row','list','row',1,0,'2025-01-01',0),('deleted','list','toolbar',0,0,'2024-01-01',1),"
                        + "('other','other','toolbar',0,0,'2024-01-01',0)");
                var fields = h.mapper(EntityListFieldMapper.class);
                var actions = h.mapper(EntityListActionMapper.class);
                assertEquals(List.of("f3", "f2", "f1"), fields.findByListConfigId("list").stream().map(row -> row.getId()).toList());
                assertEquals(List.of("a2", "a1"), actions.findByListAndPosition("list", "toolbar").stream().map(row -> row.getId()).toList());
                assertEquals(LocalDateTime.of(2026, 1, 1, 0, 0), actions.findByListAndPosition("list", "toolbar").get(0).getCreatedAt());
                assertEquals(List.of("deleted", "f1", "f2", "f3"), fields.findAllByListConfigIdForUpdate("list").stream().map(row -> row.getId()).toList());
                assertEquals(List.of("a1", "a2", "deleted", "row"), actions.findAllByListConfigIdForUpdate("list").stream().map(row -> row.getId()).toList());

                var query = Wrappers.<EntityListField>lambdaQuery().eq(EntityListField::getListConfigId, "list")
                        .orderByAsc(EntityListField::getOrderKey).orderByAsc(EntityListField::getSortOrder);
                var page = new OffsetPage<EntityListField>(1, 2);
                assertFalse(page.searchCount());
                assertEquals(List.of("f2", "f1"), fields.selectList(page, query).stream().map(row -> row.getId()).toList());
                assertTrue(fields.selectList(new OffsetPage<>(1, 0), query).isEmpty());
                assertTrue(fields.selectList(new OffsetPage<>(3, 2), query).isEmpty());

                fields.deleteByListConfigId("list"); actions.deleteByListConfigId("list");
                assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_list_field", Integer.class));
                assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_list_action", Integer.class));
                status.setRollbackOnly();
            });
        }
    }

    /** BaseMapper 按实体映射选择所有列，测试表必须包含完整记录字段。 */
    static void delegationTable(Fixture f) {
        f.table("entity_list_scope_delegation", "id VARCHAR(64) PRIMARY KEY,to_user_id VARCHAR(64),entity_code VARCHAR(64),"
                + "enabled INT,deleted INT,start_time DATETIME(6),end_time DATETIME(6),from_user_id VARCHAR(64),"
                + "delegate_scope VARCHAR(32),policy_id VARCHAR(64),delegate_config TEXT,created_by VARCHAR(64),"
                + "create_time DATETIME,update_time DATETIME");
    }

    private static void delegation(Harness h, String id, String user, String entity, int enabled, int deleted,
                                   LocalDateTime start, LocalDateTime end) {
        h.jdbc.update("INSERT INTO entity_list_scope_delegation(id,to_user_id,entity_code,enabled,deleted,start_time,end_time) "
                + "VALUES (?,?,?,?,?,?,?)", id, user, entity, enabled, deleted, start, end);
    }

    private static Set<String> ids(List<EntityListScopeDelegation> rows) {
        return rows.stream().map(EntityListScopeDelegation::getId).collect(Collectors.toSet());
    }
}
