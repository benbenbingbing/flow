package com.workflow.process.action;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import com.workflow.contracts.process.action.context.*;
import com.workflow.contracts.process.action.model.*;
import com.workflow.contracts.process.action.port.FlowActionCatalogPort;
import com.workflow.contracts.process.action.spi.*;
import com.workflow.process.action.application.*;
import com.workflow.process.action.domain.FlowActionTriggerEvent;
import com.workflow.process.action.infrastructure.persistence.mapper.*;
import com.workflow.process.action.infrastructure.persistence.record.*;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import org.flowable.engine.RepositoryService;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.interceptor.*;
import java.lang.reflect.Modifier;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 使用真实 JDBC 事务及生产 Mapper，验证主事务、独立审计、预算和重放状态的边界。 */
public class FlowActionCustomFailureTransactionTest {
    @Test void rollbackKeepsFailureAuditAndRemovesBusinessChange() throws Exception {
        var f = new Fixture();
        f.decision.set(FailureDecision.of(FailureDisposition.ROLLBACK, "ROLLED_BACK", "阻断本次审批"));
        var dispatcher = f.dispatcher(false);
        assertThrows(IllegalArgumentException.class, () -> f.operation.run(() -> dispatcher.dispatch(f.event())));
        assertEquals(0, f.count("business_record"));
        assertEquals(1, f.count("process_action_execution"));
        var row = f.only();
        assertEquals("DEAD", row.getStatus());
        assertEquals("原始业务错误", row.getErrorMessage());
        assertTrue(row.getExecutionTraceJson().contains("FAILURE_DECISION"));
        assertTrue(row.getExecutionTraceJson().contains("TRANSACTION_COMPLETED"));
        assertTrue(row.getExecutionTraceJson().contains("\"outcome\":\"ROLLED_BACK\""));
    }

    @Test void continueCommitsHealthyTransactionButCannotReviveRollbackOnly() throws Exception {
        var f = new Fixture();
        f.decision.set(FailureDecision.of(FailureDisposition.CONTINUE, "CONTINUED", "记录后继续"));
        f.operation.run(() -> f.dispatcher(false).dispatch(f.event()));
        assertEquals(1, f.count("business_record"));
        assertEquals("DEAD", f.only().getStatus());
        assertTrue(f.only().getExecutionTraceJson().contains("\"outcome\":\"COMMITTED\""));
        var broken = new Fixture();
        broken.decision.set(f.decision.get());
        assertThrows(IllegalArgumentException.class, () -> broken.operation.run(() -> broken.dispatcher(true).dispatch(broken.event())));
        assertEquals(0, broken.count("business_record"));
        assertEquals("TRANSACTION_UNUSABLE", broken.only().getTerminationReason());
    }

    @Test void additionalRetryBudgetZeroOneAndThreeRunsOneTwoAndFourTimes() throws Exception {
        for (int max : List.of(0, 1, 3)) {
            var f = new Fixture();
            var action = f.action("AFTER_COMMIT", max);
            var row = f.service.create(action, f.event(), UUID.randomUUID().toString(), FlowActionExecution.Status.PENDING);
            f.decision.set(FailureDecision.retry(1, "重试"));
            doThrow(new IllegalArgumentException("原始业务错误")).when(f.executor).executeAction(any(), any(), anyString(), any());
            for (int i = 1; i <= max + 1; i++) {
                f.process(row.getId());
                var stored = f.mapper.selectById(row.getId());
                assertEquals(i, stored.getAttemptNo());
                assertEquals(i - 1, stored.getRetryCount());
                assertEquals(i <= max ? "FAILED" : "DEAD", stored.getStatus());
                f.jdbc.update("UPDATE process_action_execution SET next_retry_time=NULL WHERE id=?", row.getId());
            }
            assertEquals("RETRY_EXHAUSTED", f.mapper.selectById(row.getId()).getTerminationReason());
            verify(f.executor, times(max + 1)).executeAction(any(), any(), anyString(), any());
        }
    }

    @Test void replayRetainsOriginalFailureAndUsesOriginalDownstreamIdempotencyKey() throws Exception {
        var f = new Fixture();
        f.decision.set(FailureDecision.of(FailureDisposition.MANUAL, "MANUAL_REQUIRED", "转人工"));
        var row = f.service.create(f.action("AFTER_COMMIT", 0), f.event(), "downstream-key", FlowActionExecution.Status.PENDING);
        doThrow(new IllegalArgumentException("原始业务错误")).when(f.executor).executeAction(any(), any(), anyString(), any());
        f.process(row.getId());
        f.service.retry(row.getId());
        assertThrows(IllegalStateException.class, () -> f.service.retry(row.getId()));
        String replayId = f.jdbc.queryForObject("SELECT id FROM process_action_execution WHERE replay_of_id=?", String.class, row.getId());
        assertEquals("原始业务错误", f.mapper.selectById(row.getId()).getErrorMessage());
        doReturn(new FlowActionContext()).when(f.executor).executeAction(any(), any(), anyString(), any());
        f.process(replayId);
        verify(f.executor, times(2)).executeAction(any(), any(), eq("downstream-key"), any());
        assertEquals("DEAD", f.mapper.selectById(row.getId()).getStatus());
        assertEquals("RESOLVED", f.mapper.selectById(row.getId()).getResolutionStatus());
        assertEquals("SUCCESS", f.mapper.selectById(replayId).getStatus());
    }

    @Test void expiredOwnerCannotPersistDecisionOrStartAnotherAttempt() throws Exception {
        var f = new Fixture();
        var row = f.service.create(f.action("AFTER_COMMIT", 3), f.event(), "key", FlowActionExecution.Status.PENDING);
        var claimed = f.service.claim(row.getId(), "old", 300);
        f.service.beginCustomAttempt(claimed);
        assertThrows(IllegalStateException.class, () -> f.service.beginCustomAttempt(claimed));
        f.jdbc.update("UPDATE process_action_execution SET lease_token=lease_token+1, owner_id='new' WHERE id=?", row.getId());
        var decision = FailureDecision.retry(1, "重试");
        assertThrows(IllegalStateException.class, () -> f.service.persistCustomFailure(claimed,
                new RuntimeException("失败"), new FlowActionFailureCoordinator.Outcome(decision, decision, null)));
        assertEquals("RUNNING", f.mapper.selectById(row.getId()).getStatus());
        assertFalse(f.mapper.selectById(row.getId()).getExecutionTraceJson().contains("FAILURE_DECISION"));
    }

    @Test void concurrentManualReplayCreatesOnlyOneChild() throws Exception {
        var f = new Fixture();
        var row = f.service.create(f.action("AFTER_COMMIT", 0), f.event(), "key", FlowActionExecution.Status.PENDING);
        f.decision.set(FailureDecision.of(FailureDisposition.MANUAL, "MANUAL_REQUIRED", "人工处理"));
        doThrow(new RuntimeException("失败")).when(f.executor).executeAction(any(), any(), anyString(), any());
        f.process(row.getId());
        var pool = Executors.newFixedThreadPool(2);
        try {
            var tasks = List.<Callable<Boolean>>of(() -> retry(f, row.getId()), () -> retry(f, row.getId()));
            int successes = 0;
            for (var result : pool.invokeAll(tasks)) if (result.get()) successes++;
            assertEquals(1, successes);
            assertEquals(2, f.count("process_action_execution"));
        } finally { pool.shutdownNow(); }
    }

    @Test void expiredLeaseRecoversOnlyCustomAttemptsThatHaveNotStarted() throws Exception {
        // H2 不支持 MySQL 的 FORCE INDEX；仅移除索引提示，执行生产恢复 SQL 的全部业务条件。
        for (boolean started : List.of(false, true)) {
            var f = new Fixture();
            var row = f.service.create(f.action("AFTER_COMMIT", 3), f.event(), "key", FlowActionExecution.Status.PENDING);
            var claimed = f.service.claim(row.getId(), "expired", 300);
            if (started) f.service.beginCustomAttempt(claimed);
            f.jdbc.update("UPDATE process_action_execution SET lease_until=TIMESTAMP '2000-01-01 00:00:00' WHERE id=?", row.getId());
            String recoverySql = f.config.getMappedStatement(FlowActionExecutionMapper.class.getName() + ".recoverExpiredLease")
                    .getBoundSql(Map.of("id", row.getId())).getSql().replace(" FORCE INDEX (PRIMARY)", "");
            assertEquals(1, f.jdbc.update(recoverySql, row.getId()));
            var stored = f.mapper.selectById(row.getId());
            assertEquals(started ? "DEAD" : "FAILED", stored.getStatus());
            assertEquals(started ? 1 : 0, stored.getAttemptNo());
            if (started) {
                assertEquals("EXECUTION_UNCERTAIN", stored.getTerminationReason());
                assertEquals("OPEN", stored.getResolutionStatus());
                assertNull(stored.getNextRetryTime());
                assertNotNull(stored.getFinishedAt());
            } else assertNotNull(stored.getNextRetryTime());
        }
    }

    @Test void snapshotAndHumanResolutionDoNotRewriteExecutionResult() throws Exception {
        var f = new Fixture();
        var action = f.action("AFTER_COMMIT", 0);
        var row = f.service.create(action, f.event(), "key", FlowActionExecution.Status.PENDING);
        action.setFailureStrategyVersion("new-version");
        assertEquals("1", f.service.executionAction(row, action).getFailureStrategyVersion());
        f.decision.set(FailureDecision.of(FailureDisposition.MANUAL, "MANUAL_REQUIRED", "转人工"));
        doThrow(new RuntimeException("失败")).when(f.executor).executeAction(any(), any(), anyString(), any());
        f.process(row.getId());
        f.service.resolve(row.getId(), "已核对下游数据，无需再次执行");
        assertEquals("DEAD", f.mapper.selectById(row.getId()).getStatus());
        assertEquals("RESOLVED", f.mapper.selectById(row.getId()).getResolutionStatus());
        assertThrows(IllegalStateException.class, () -> f.service.resolve(row.getId(), "重复提交"));
        assertEquals("TEST", f.service.findDetailsByProcessInstanceId("process").get(0).getFailureStrategyCode());
    }

    @Test void providerFailureKeepsOriginalBusinessErrorAndRecordsBothFailures() throws Exception {
        var f = new Fixture();
        var row = f.service.create(f.action("AFTER_COMMIT", 3), f.event(), "key", FlowActionExecution.Status.PENDING);
        // 默认返回 null，模拟损坏的策略实现；业务异常必须仍是执行记录的主错误。
        doThrow(new IllegalArgumentException("原始业务错误")).when(f.executor).executeAction(any(), any(), anyString(), any());
        f.process(row.getId());
        var stored = f.mapper.selectById(row.getId());
        assertEquals("原始业务错误", stored.getErrorMessage());
        assertEquals("STRATEGY_ERROR", stored.getTerminationReason());
        assertEquals("OPEN", stored.getResolutionStatus());
        assertTrue(stored.getExecutionTraceJson().contains("strategyError"));
    }

    @Test void invalidExecutionPreparationStopsBeforeHandlerWithoutAnUnboundedRetry() throws Exception {
        for (boolean missingVersion : List.of(true, false)) {
            var f = new Fixture();
            var action = f.action("AFTER_COMMIT", 3);
            if (missingVersion) action.setFailureStrategyVersion("removed-version");
            var row = f.service.create(action, f.event(), "key", FlowActionExecution.Status.PENDING);
            if (!missingVersion) f.jdbc.update("UPDATE process_action_execution SET payload_json='invalid-json' WHERE id=?", row.getId());
            // 即使业务策略总是请求重试，执行准备错误也不能进入不消耗预算的重试循环。
            f.decision.set(FailureDecision.retry(1, "重试"));
            f.process(row.getId());
            var stored = f.mapper.selectById(row.getId());
            assertEquals(0, stored.getAttemptNo());
            assertEquals("DEAD", stored.getStatus());
            assertEquals("STRATEGY_ERROR", stored.getTerminationReason());
            assertEquals("OPEN", stored.getResolutionStatus());
            assertNull(stored.getNextRetryTime());
            verify(f.executor, never()).executeAction(any(), any(), anyString(), any());
        }
    }

    @Test void incrementalMigrationKeepsLegacyPolicyAndInitializesAttemptCounter() {
        var source = new JdbcDataSource();
        source.setURL("jdbc:h2:mem:migration_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE TABLE process_action(id VARCHAR(64), failure_policy VARCHAR(20))");
        jdbc.execute("CREATE TABLE process_action_execution(id VARCHAR(64), status VARCHAR(20))");
        jdbc.update("INSERT INTO process_action VALUES ('old','ROLLBACK')");
        jdbc.update("INSERT INTO process_action_execution VALUES ('old','DEAD')");
        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator(
                new org.springframework.core.io.ClassPathResource("db/migration/V108__flow_action_custom_failure_strategy.sql"))
                .execute(source);
        assertEquals("ROLLBACK", jdbc.queryForObject("SELECT failure_policy FROM process_action WHERE id='old'", String.class));
        assertNull(jdbc.queryForObject("SELECT failure_strategy_code FROM process_action WHERE id='old'", String.class));
        assertEquals(0, jdbc.queryForObject("SELECT attempt_no FROM process_action_execution WHERE id='old'", Integer.class));
        assertEquals("DEAD", jdbc.queryForObject("SELECT status FROM process_action_execution WHERE id='old'", String.class));
    }

    private static boolean retry(Fixture f, String id) {
        try { f.service.retry(id); return true; }
        catch (IllegalStateException expected) { return false; }
    }

    /** H2 测试别名仅补齐 MySQL 的 UTC 时钟函数，业务条件更新仍使用生产 Mapper SQL。 */
    public static Timestamp utcNow(int precision) { return Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC)); }

    public static class Operation {
        @Transactional public void run(Runnable work) { work.run(); }
    }

    private static class Fixture {
        final JdbcTemplate jdbc;
        final DataSourceTransactionManager transactions;
        final FlowActionExecutionMapper mapper;
        final FlowActionExecutionService service;
        final FlowActionExecutor executor = mock(FlowActionExecutor.class);
        final FlowActionExecutionProcessor processor;
        final Operation operation;
        final FlowActionFailureCoordinator coordinator;
        final MybatisConfiguration config = new MybatisConfiguration();
        final AtomicReference<FailureDecision> decision = new AtomicReference<>();

        Fixture() throws Exception {
            var source = new JdbcDataSource();
            source.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=5000");
            jdbc = new JdbcTemplate(source);
            jdbc.execute("CREATE ALIAS UTC_TIMESTAMP FOR 'com.workflow.process.action.FlowActionCustomFailureTransactionTest.utcNow'");
            List<String> columns = new ArrayList<>();
            for (var field : FlowActionExecution.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                var annotation = field.getAnnotation(com.baomidou.mybatisplus.annotation.TableField.class);
                String name = annotation != null && !annotation.value().isBlank() ? annotation.value()
                        : field.getName().replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
                String type = field.getType() == Integer.class ? "INT" : field.getType() == Long.class ? "BIGINT"
                        : field.getType() == LocalDateTime.class ? "TIMESTAMP" : "VARCHAR(1000000)";
                columns.add(name + " " + type + (name.equals("id") ? " PRIMARY KEY" : "")
                        + (name.equals("lease_token") || name.equals("attempt_no") ? " DEFAULT 0" : ""));
            }
            columns.add("attempt_lease_token BIGINT");
            jdbc.execute("CREATE TABLE process_action_execution (" + String.join(",", columns) + ")");
            jdbc.execute("CREATE UNIQUE INDEX execution_key ON process_action_execution(idempotency_key)");
            jdbc.execute("CREATE TABLE business_record(id VARCHAR(64))");
            transactions = new DataSourceTransactionManager(source);
            config.setDatabaseId("MYSQL"); config.setMapUnderscoreToCamelCase(true);
            config.addMapper(FlowActionExecutionMapper.class);
            var factory = new MybatisSqlSessionFactoryBean(); factory.setDataSource(source); factory.setConfiguration(config);
            mapper = new SqlSessionTemplate(Objects.requireNonNull(factory.getObject())).getMapper(FlowActionExecutionMapper.class);
            var beans = mock(ApplicationContext.class);
            var handler = mock(FlowActionProvider.class); when(handler.retryable()).thenReturn(true);
            when(beans.getBean("handler", FlowActionProvider.class)).thenReturn(handler);
            var provider = new FlowActionFailureStrategyProvider() {
                public FailureStrategyDescriptor descriptor() {
                    return new FailureStrategyDescriptor("TEST", "1", "测试策略", "", EnumSet.allOf(FlowActionExecutionMode.class),
                            EnumSet.allOf(FailureDisposition.class), Set.of(), List.of());
                }
                public FailureDecision decide(FlowActionFailureContext context, Map<String, Object> config) { return decision.get(); }
            };
            var json = new ObjectMapper().findAndRegisterModules();
            var catalog = new FlowActionFailureStrategyCatalog(List.of(provider), json, mock(EntityCodeCatalogPort.class), beans);
            coordinator = new FlowActionFailureCoordinator(catalog);
            service = proxy(new FlowActionExecutionService(mapper, mock(FlowActionMapper.class), json,
                    mock(FlowActionCatalogPort.class), mock(SystemAuditPort.class), catalog));
            operation = proxy(new Operation());
            var scheduler = mock(TaskScheduler.class);
            doReturn(mock(ScheduledFuture.class)).when(scheduler).scheduleAtFixedRate(any(Runnable.class), any(Instant.class), any(Duration.class));
            when(executor.retryable(any())).thenReturn(true);
            processor = new FlowActionExecutionProcessor(service, mock(FlowActionMapper.class), executor, scheduler, coordinator);
        }

        @SuppressWarnings("unchecked") <T> T proxy(T target) {
            var interceptor = new TransactionInterceptor(); interceptor.setTransactionManager(transactions);
            interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
            var proxy = new ProxyFactory(target); proxy.setProxyTargetClass(true); proxy.addAdvice(interceptor);
            return (T) proxy.getProxy();
        }

        FlowAction action(String mode, int retries) {
            var action = new FlowAction(); action.setId("action"); action.setActionName("测试动作"); action.setInterfaceName("handler");
            action.setExecutionMode(mode); action.setFailurePolicy("CUSTOM"); action.setFailureStrategyCode("TEST");
            action.setFailureStrategyVersion("1"); action.setRetryConfig("{\"semanticsVersion\":2,\"maxRetries\":" + retries + "}");
            action.setScopeType("PROCESS"); action.setTriggerTiming("PROCESS_COMPLETED"); action.setEnabled(true); return action;
        }

        FlowActionTriggerEvent event() {
            var event = new FlowActionTriggerEvent(); event.setVersionId("v1"); event.setProcessInstanceId("process");
            event.setScopeType("PROCESS"); event.setTriggerTiming("PROCESS_COMPLETED"); return event;
        }

        FlowActionEventDispatcher dispatcher(boolean rollbackOnly) {
            var actions = mock(FlowActionService.class);
            when(actions.findPublishedActionsByBinding(any(), any(), any(), any())).thenReturn(List.of(action("IN_TRANSACTION", 0)));
            var timing = mock(FlowActionTimingCatalog.class); when(timing.find(any())).thenReturn(Optional.of(new FlowActionTimingOption("PROCESS_COMPLETED", "流程完成", "", "PROCESS", false, "IN_TRANSACTION", "ROLLBACK", "", false)));
            doAnswer(call -> {
                jdbc.update("INSERT INTO business_record VALUES ('value')");
                if (rollbackOnly) TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                throw new IllegalArgumentException("原始业务错误");
            }).when(executor).executeAction(any(), any(), anyString(), any());
            return new FlowActionEventDispatcher(actions, executor, service, timing,
                    mock(ProcessVersionHistoryMapper.class), mock(RepositoryService.class), coordinator);
        }

        void process(String id) {
            var claimed = service.claim(id, "worker", 300); assertNotNull(claimed);
            processor.process(id, "worker", claimed.getLeaseToken(), 300);
        }

        int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
        FlowActionExecution only() { return mapper.selectById(jdbc.queryForObject("SELECT id FROM process_action_execution", String.class)); }
    }
}
