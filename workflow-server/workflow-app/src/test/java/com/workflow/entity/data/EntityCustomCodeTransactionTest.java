package com.workflow.entity.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.code.*;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.application.EntityCodeReservationService;
import com.workflow.entity.definition.application.EntityCodeGeneratorService;
import com.workflow.entity.definition.application.EntityDefinitionAccessPolicy;
import com.workflow.entity.definition.application.code.*;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityCodeRuleMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityCodeRule;
import com.workflow.entity.ui.application.UiExtensionDefinitionValidator;
import com.workflow.integration.database.api.query.DatabaseQueryDialects;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 使用真实 JDBC 事务和生产事务注解验证：自定义在 INSERT 前执行，唯一占用与业务写入原子提交。 */
class EntityCustomCodeTransactionTest {
    @Test
    void currentRecordDoesNotExistYetButParentInSameTransactionIsVisible() {
        var f = new Fixture();
        var service = f.service((context) -> {
            assertEquals(0, f.jdbc.queryForObject("SELECT COUNT(*) FROM wf_asset WHERE id=?", Integer.class, context.recordId()));
            assertEquals(1, f.jdbc.queryForObject("SELECT COUNT(*) FROM wf_asset WHERE id='parent'", Integer.class));
            return "CUSTOM-001";
        });
        f.tx.executeWithoutResult(status -> {
            f.jdbc.update("INSERT INTO wf_asset VALUES ('parent', 'PARENT')");
            String code = service.generateCode(f.context("child"), "FORGED");
            assertEquals("CUSTOM-001", code);
            f.jdbc.update("INSERT INTO wf_asset VALUES (?, ?)", "child", code);
        });
        assertEquals(2, f.count("wf_asset"));
        assertEquals(1, f.count("entity_unique_value"));
    }

    @Test
    void failedSaveRollsBackBusinessRowAndCodeClaimAndCanRetry() {
        var f = new Fixture();
        var service = f.service(context -> "CUSTOM-001");
        assertThrows(IllegalStateException.class, () -> f.tx.executeWithoutResult(status -> {
            String code = service.generateCode(f.context("record"), null);
            f.jdbc.update("INSERT INTO wf_asset VALUES (?, ?)", "record", code);
            throw new IllegalStateException("模拟后续子表保存失败");
        }));
        assertEquals(0, f.count("wf_asset"));
        assertEquals(0, f.count("entity_unique_value"));
        f.tx.executeWithoutResult(status -> service.generateCode(f.context("retry"), null));
        assertEquals(1, f.count("entity_unique_value"));
    }

    @Test
    void legacyRecordWithoutClaimStillBlocksDuplicateAndRollsBackNewClaim() {
        var f = new Fixture();
        f.jdbc.update("INSERT INTO wf_asset VALUES ('legacy', 'Custom-001')");
        var service = f.service(context -> "CUSTOM-001");
        assertThrows(com.workflow.core.error.BusinessConflictException.class,
                () -> f.tx.executeWithoutResult(status -> service.generateCode(f.context("new"), null)));
        assertEquals(0, f.count("entity_unique_value"));
        assertEquals(1, f.count("wf_asset"));
    }

    @Test
    void parallelSavesCannotCommitTheSameCode() throws Exception {
        var f = new Fixture();
        var barrier = new CyclicBarrier(2);
        var service = f.service(context -> {
            try { barrier.await(5, TimeUnit.SECONDS); }
            catch (Exception exception) { throw new IllegalStateException(exception); }
            return "SAME-CODE";
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            var tasks = List.of("one", "two").stream().map(id -> pool.submit(() -> {
                try {
                    f.tx.executeWithoutResult(status -> {
                        String code = service.generateCode(f.context(id), null);
                        f.jdbc.update("INSERT INTO wf_asset VALUES (?, ?)", id, code);
                    });
                    return true;
                } catch (com.workflow.core.error.BusinessConflictException expected) { return false; }
            })).toList();
            int successes = 0;
            for (var task : tasks) if (task.get(10, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
            assertEquals(1, f.count("wf_asset"));
            assertEquals(1, f.count("entity_unique_value"));
        } finally { pool.shutdownNow(); }
    }

    @Test
    void generationRequiresBusinessTransactionAndPreviewNeverInvokesGenerator() {
        var f = new Fixture();
        var calls = new AtomicInteger();
        var service = f.service(context -> { calls.incrementAndGet(); return "NUMBER"; });
        assertThrows(org.springframework.transaction.IllegalTransactionStateException.class,
                () -> service.generateCode(f.context("record"), null));
        assertEquals("", service.previewCode(f.rule));
        assertEquals(0, calls.get());
        assertEquals(0, f.count("entity_unique_value"));
    }

    @Test
    void ordinaryFieldUpdatesAndDeletionCannotReleaseCommittedBusinessCode() {
        var f = new Fixture();
        var service = f.service(context -> "CUSTOM-001");
        f.tx.executeWithoutResult(status -> service.generateCode(f.context("record"), null));
        var fields = new com.workflow.entity.data.application.EntityUniqueValueService(f.jdbc);
        f.tx.executeWithoutResult(status -> fields.replace("asset", "record", Map.of("name", "New name")));
        assertEquals(2, f.count("entity_unique_value"));
        f.tx.executeWithoutResult(status -> fields.release("asset", "record"));
        assertEquals(1, f.count("entity_unique_value"));
        assertThrows(com.workflow.core.error.BusinessConflictException.class,
                () -> f.tx.executeWithoutResult(status -> service.generateCode(f.context("another"), null)));
    }

    @Test
    void mapperRoundTripsCustomConfigurationWithoutRewindingSequence() throws Exception {
        var f = new Fixture();
        f.jdbc.execute("CREATE TABLE entity_code_rule (id VARCHAR(64) PRIMARY KEY, entity_code VARCHAR(100) UNIQUE, prefix VARCHAR(20), date_format VARCHAR(20), seq_length INT, seq_type VARCHAR(20), current_seq INT, seq_date VARCHAR(20), example VARCHAR(100), create_time TIMESTAMP, update_time TIMESTAMP, generation_mode VARCHAR(20) DEFAULT 'RULE', generator_code VARCHAR(64), generator_config TEXT)");
        var configuration = new com.baomidou.mybatisplus.core.MybatisConfiguration();
        configuration.setDatabaseId("MYSQL");
        configuration.addMapper(EntityCodeRuleMapper.class);
        var factory = new com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean();
        factory.setDataSource(f.jdbc.getDataSource());
        factory.setConfiguration(configuration);
        var mapper = new org.mybatis.spring.SqlSessionTemplate(factory.getObject()).getMapper(EntityCodeRuleMapper.class);
        var rule = EntityCodeRule.getDefault("asset"); rule.setId("rule");
        mapper.insert(rule);
        rule.setGenerationMode("CUSTOM"); rule.setGeneratorCode("PROJECT");
        rule.setGeneratorConfig(Map.of("prefix", "XM", "nested", Map.of("enabled", false)));
        f.jdbc.update("UPDATE entity_code_rule SET current_seq=42, seq_date='20260924'");
        mapper.updateConfiguration(rule);
        var saved = mapper.findByEntityCode("asset").orElseThrow();
        assertEquals("CUSTOM", saved.getGenerationMode());
        assertEquals(rule.getGeneratorConfig(), saved.getGeneratorConfig());
        assertEquals(42, saved.getCurrentSeq());
        assertEquals("20260924", saved.getSeqDate());

        // 规则分配仍在独立事务中提交，外层业务失败只回滚业务行及编码预留。
        var sequence = f.proxy(new RuleEntityCodeGenerator(mapper));
        saved.setSeqType("NEVER");
        f.tx.executeWithoutResult(status -> {
            assertTrue(sequence.generateCode("asset", saved).endsWith("000043"));
            status.setRollbackOnly();
        });
        assertEquals(43, mapper.findByEntityCode("asset").orElseThrow().getCurrentSeq());
    }

    static class Fixture {
        final JdbcTemplate jdbc;
        final TransactionTemplate tx;
        final EntityCodeRule rule = EntityCodeRule.getDefault("asset");
        final EntityCodeRuleMapper mapper = mock(EntityCodeRuleMapper.class);
        final EntityCodeReservationService reservations;

        Fixture() {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:code_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
            jdbc = new JdbcTemplate(source);
            tx = new TransactionTemplate(new DataSourceTransactionManager(source));
            jdbc.execute("CREATE TABLE wf_asset (id VARCHAR(64) PRIMARY KEY, code VARCHAR(100))");
            jdbc.execute("CREATE TABLE entity_unique_value (entity_code VARCHAR(100), field_code VARCHAR(100), value_hash VARCHAR(64), normalized_value VARCHAR(1000), record_id VARCHAR(64), PRIMARY KEY(entity_code, field_code, value_hash))");
            var tables = mock(DynamicTableService.class);
            when(tables.getTableName("asset")).thenReturn("wf_asset");
            when(tables.tableExists("asset")).thenReturn(true);
            reservations = proxy(new EntityCodeReservationService(jdbc, DatabaseQueryDialects.forDatabaseId("MYSQL"), tables));
            rule.setGenerationMode("CUSTOM"); rule.setGeneratorCode("TEST");
            when(mapper.findByEntityCode("asset")).thenReturn(Optional.of(rule));
        }

        EntityCodeGeneratorService service(java.util.function.Function<EntityCodeGenerationContext, String> generate) {
            EntityCodeGenerator custom = new EntityCodeGenerator() {
                public String getCode() { return "TEST"; }
                public String getDisplayName() { return "Test"; }
                public String generate(EntityCodeGenerationContext context, Map<String, Object> config) { return generate.apply(context); }
            };
            var registry = new EntityCodeGeneratorRegistry(List.of(custom), new UiExtensionDefinitionValidator(new JsonDocumentCodec(new ObjectMapper())));
            return proxy(new EntityCodeGeneratorService(mapper, mock(EntityDefinitionAccessPolicy.class),
                    proxy(new RuleEntityCodeGenerator(mapper)), registry, reservations, mock(EntityCodeContextFactory.class)));
        }

        EntityCodeGenerationContext context(String id) {
            return new EntityCodeGenerationContext("asset", id, Map.of("type", "A"), "actor", null,
                    null, null, Map.of(), LocalDateTime.now(), null);
        }

        int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

        @SuppressWarnings("unchecked")
        <T> T proxy(T target) {
            var interceptor = new TransactionInterceptor();
            interceptor.setTransactionManager(tx.getTransactionManager());
            interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            var factory = new ProxyFactory(target); factory.setProxyTargetClass(true); factory.addAdvice(interceptor);
            return (T) factory.getProxy();
        }
    }
}
