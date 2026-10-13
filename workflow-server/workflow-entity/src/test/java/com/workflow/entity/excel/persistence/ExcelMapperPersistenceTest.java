package com.workflow.entity.excel.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.entity.excel.infrastructure.persistence.mapper.*;
import com.workflow.entity.excel.model.ExcelResolvedScheme;
import com.workflow.entity.excel.runtime.*;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static com.workflow.entity.excel.persistence.ExcelMapperTestSupport.mapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** 在真实 MyBatis/数据库边界验证 CLOB、大小写列名、事务及清理上限，防止替换 JDBC 后仅能通过模拟测试。 */
class ExcelMapperPersistenceTest {
    @Test
    void uppercaseColumnsAndClobDocumentsPreserveOwnershipErrorsAndIndependentClaim() {
        var source = database();
        var jdbc = new JdbcTemplate(source);
        jobsSchema(jdbc);
        var manager = new DataSourceTransactionManager(source);
        var repository = new EntityExcelJobRepository(mapper(source, EntityExcelJobMapper.class), new ObjectMapper(), manager);
        var plan = new EntityExcelRuntimeResolver.Plan(null,
                new ExcelResolvedScheme("entity", "employee", "basic", null, "hash", "", "form"), null, null, null);
        UserContext.setCurrentUser("alice", "alice");
        try {
            String[] id = new String[1];
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                id[0] = repository.claim(plan, "IMPORT", "origin", "request-1", "payload").jobId();
                repository.progress(id[0], Map.of("savedRowCount", 99));
                status.setRollbackOnly();
            });
            assertThat(repository.requireOwned(id[0]).get("status")).isEqualTo("RUNNING");
            assertThat(repository.requireOwned(id[0]).get("counters_json")).isNull();
            assertThat(repository.requireOwned(id[0]).get("config_json")).isInstanceOf(String.class);
            assertThat(repository.claim(plan, "IMPORT", "origin", "request-1", "payload").acquired()).isFalse();
            String message = "中文错误".repeat(3000);
            repository.saveErrors(id[0], List.of(Map.of("sheetNo", 0, "rowNumber", 2, "message", message)));
            assertThat(repository.errors(id[0], 1, 10).get("records"))
                    .isEqualTo(List.of(Map.of("sheetNo", 0, "rowNumber", 2, "message", message)));
            new TransactionTemplate(manager).executeWithoutResult(status -> {
                assertThat(repository.lockIfRunning(id[0])).isTrue();
                // JDBC 独立写入后再次锁定必须重读，不能命中 MyBatis 一级缓存。
                jdbc.update("UPDATE entity_excel_job SET status='FAILED' WHERE id=?", id[0]);
                assertThat(repository.lockIfRunning(id[0])).isFalse();
            });
        } finally { UserContext.clear(); }
    }

    @Test
    void configurationReadsFullClobAndDoesNotFallBackWhenCurrentInterfaceUsesAnotherProvider() {
        var source = database();
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE entity_excel_scheme(id VARCHAR(64) PRIMARY KEY,entity_id VARCHAR(64),scheme_code VARCHAR(64),name VARCHAR(64),draft_json CLOB,revision INT,status VARCHAR(20),created_at TIMESTAMP,updated_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE entity_excel_scheme_published(scheme_id VARCHAR(64) PRIMARY KEY,document_json CLOB,content_hash VARCHAR(64),write_form_id VARCHAR(64),published_by VARCHAR(64),published_at TIMESTAMP)");
        jdbc.execute("CREATE TABLE ui_extension_definition(id VARCHAR(64) PRIMARY KEY,extension_type VARCHAR(32),deleted INT,provider_code VARCHAR(64),implementation_config_document CLOB,legacy_service_id VARCHAR(64),provider_operation_code VARCHAR(64))");
        var repository = new ExcelConfigurationRepository(mapper(source, ExcelConfigurationMapper.class));
        String document = "{\"text\":\"" + "中文".repeat(5000) + "\"}";
        var row = new ExcelConfigurationRepository.SchemeRow();
        row.setId("scheme"); row.setEntityId("entity"); row.setSchemeCode("basic"); row.setName("导入"); row.setDraftJson(document);
        repository.insertScheme(row);
        new TransactionTemplate(new DataSourceTransactionManager(source)).executeWithoutResult(status ->
                repository.publish(row, document, "hash", null, null));
        assertThat(repository.scheme("entity", "basic").getDocumentJson()).isEqualTo(document);
        assertThat(repository.scheme("entity", "basic").getWriteFormId()).isNull();
        assertThat(repository.scheme("entity", "basic").getRevision()).isEqualTo(2);
        jdbc.update("INSERT INTO ui_extension_definition VALUES('current','INTERFACE',0,'other',null,null,null)");
        jdbc.update("INSERT INTO ui_extension_definition VALUES('legacy','INTERFACE',0,'entity_excel_action',?,'current','run')", document);
        assertThat(repository.excelInterfaceConfiguration("current", "run")).isNull();
        jdbc.update("DELETE FROM ui_extension_definition WHERE id='current'");
        assertThat(repository.excelInterfaceConfiguration("current", "run")).isEqualTo(document);
        jdbc.update("INSERT INTO ui_extension_definition VALUES('duplicate','INTERFACE',0,'entity_excel_action',?,'current','run')", document);
        assertThatThrownBy(() -> repository.excelInterfaceConfiguration("current", "run"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Excel历史接口引用不唯一");
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTC", "Asia/Shanghai", "America/New_York"})
    @ResourceLock(Resources.TIME_ZONE)
    void cleanupUsesUtcBoundaryAndProcessesAtMost100Jobs(String zone) {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            var source = database(); var jdbc = new JdbcTemplate(source); jobsSchema(jdbc);
            var mapper = mapper(source, EntityExcelJobMapper.class);
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            for (int index = 0; index < 101; index++) {
                mapper.insert("old-" + index, "entity", "employee", "IMPORT", "alice", "origin", "r-" + index,
                        "hash", "{}", now.minusHours(25));
            }
            mapper.insert("recent", "entity", "employee", "IMPORT", "alice", "origin", "recent", "hash", "{}", now.minusHours(23));
            new EntityExcelJobCleanup(mapper, mock(EntityExcelFileStore.class), new DataSourceTransactionManager(source)).clean();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM entity_excel_job WHERE status='FAILED'", Integer.class)).isEqualTo(100);
            assertThat(mapper.find("recent").get("status")).isEqualTo("RUNNING");
            assertThat(mapper.expired(now.minusHours(24))).hasSize(1);
        } finally { TimeZone.setDefault(previous); }
    }

    private static JdbcDataSource database() {
        var source = new JdbcDataSource();
        // 保留 H2 默认大写物理列名，覆盖原 JdbcTemplate 大小写无关 Map 的兼容行为。
        source.setURL("jdbc:h2:mem:excel_mapper_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        return source;
    }

    private static void jobsSchema(JdbcTemplate jdbc) {
        jdbc.execute("""
                CREATE TABLE entity_excel_job(id VARCHAR(64) PRIMARY KEY,entity_id VARCHAR(64),entity_code VARCHAR(64),
                  operation VARCHAR(20),status VARCHAR(20),user_id VARCHAR(64),origin_key VARCHAR(64),request_id VARCHAR(128),
                  payload_hash VARCHAR(64),config_json CLOB,counters_json CLOB,input_file_id VARCHAR(64),output_file_id VARCHAR(64),
                  file_name VARCHAR(255),content_type VARCHAR(255),error_file_id VARCHAR(64),error_message VARCHAR(1000),
                  created_at TIMESTAMP,updated_at TIMESTAMP,UNIQUE(user_id,origin_key,request_id))
                """);
        jdbc.execute("CREATE TABLE entity_excel_job_row(id VARCHAR(64) PRIMARY KEY,job_id VARCHAR(64),sheet_no INT,excel_row_number INT,errors_json CLOB)");
    }
}
