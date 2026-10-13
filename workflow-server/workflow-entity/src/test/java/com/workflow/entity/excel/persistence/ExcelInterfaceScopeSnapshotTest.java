package com.workflow.entity.excel.persistence;

import com.workflow.entity.excel.infrastructure.persistence.mapper.ExcelConfigurationMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

import static com.workflow.entity.excel.persistence.ExcelMapperTestSupport.mapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** 验证真实 SQL 查询与 Java 数组匹配共同覆盖 Excel 接口的实体范围提示。 */
class ExcelInterfaceScopeSnapshotTest {
    @Test
    void includesGlobalMultiEntityAndLegacyPageScopesWithoutLeakingOtherEntities() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:excel_scope_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE entity_form_published(form_id VARCHAR(64),entity_id VARCHAR(64),snapshot_document CLOB)");
        jdbc.execute("CREATE TABLE entity_form(id VARCHAR(64),entity_id VARCHAR(64),deleted INT)");
        jdbc.execute("CREATE TABLE entity_list_config(id VARCHAR(64),entity_id VARCHAR(64),deleted INT)");
        jdbc.execute("CREATE TABLE ui_config_release(config_type VARCHAR(32),config_id VARCHAR(64),snapshot_document CLOB)");
        jdbc.execute("CREATE TABLE ui_extension_definition(id VARCHAR(64),extension_type VARCHAR(32),provider_code VARCHAR(64),deleted INT,"
                + "scope_type VARCHAR(32),scope_id VARCHAR(64),scope_entity_ids_document CLOB,implementation_config_document CLOB)");
        jdbc.update("INSERT INTO entity_form VALUES ('form-a','entity-a',0),('form-c','entity-c',0)");
        jdbc.update("INSERT INTO entity_list_config VALUES ('list-a','entity-a',0),('list-c','entity-c',0)");
        insert(jdbc, "global", "GLOBAL", null, null);
        insert(jdbc, "multiple", "ENTITY", null, "[\"entity-a\",\"entity-b\"]");
        insert(jdbc, "legacy-entity", "ENTITY", "entity-a", null);
        insert(jdbc, "empty", "ENTITY", "entity-a", "[]");
        insert(jdbc, "legacy-form", "FORM", "form-a", null);
        insert(jdbc, "legacy-list", "LIST", "list-a", null);
        insert(jdbc, "other-form", "FORM", "form-c", null);
        insert(jdbc, "other-list", "LIST", "list-c", null);
        var repository = new ExcelConfigurationRepository(mapper(source, ExcelConfigurationMapper.class));
        var snapshots = repository.snapshots("entity-a");
        assertEquals(List.of("global", "legacy-entity", "legacy-form", "legacy-list", "multiple"),
                snapshots.stream().map(ExcelConfigurationRepository.Snapshot::ownerId).sorted().toList());
        snapshots.forEach(snapshot -> assertFalse(snapshot.published()));
        assertEquals(List.of("global", "multiple"), repository.snapshots("entity-b").stream()
                .map(ExcelConfigurationRepository.Snapshot::ownerId).sorted().toList());
        assertEquals(List.of("global"), repository.snapshots("entity-unselected").stream()
                .map(ExcelConfigurationRepository.Snapshot::ownerId).toList());
    }

    private void insert(JdbcTemplate jdbc, String id, String scope, String scopeId, String ids) {
        jdbc.update("INSERT INTO ui_extension_definition VALUES(?,'INTERFACE','entity_excel_action',0,?,?,?,?)",
                id, scope, scopeId, ids, "{\"operation\":\"EXPORT\"}");
    }
}
