package com.workflow.entity.data;

import com.workflow.contracts.process.ProcessStartRequest;
import com.workflow.core.database.JdbcDatabaseClock;
import com.workflow.core.database.JdbcLockedRow;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.instance.application.ProcessRuntimeService;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.task.application.ProcessTaskService;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.task.api.TaskQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static com.workflow.entity.data.MySqlCoordinatedOperationDatabaseTest.transactional;
import static com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.concurrent;
import com.workflow.entity.data.MySqlIdempotentInsertDatabaseTest.Harness;
import com.workflow.entity.data.MySqlRuntimePaginationDatabaseTest.Fixture;

/** 流程关联真实落库，引擎启动替身在同一事务写入可观测副作用，验证原子占用和重放。 */
@EnabledIfEnvironmentVariable(named = "FLOW_MYSQL_TEST_URL", matches = ".+")
class MySqlProcessReservationDatabaseTest {
    @Test
    void concurrentEntityWritesStartOneInstanceAndNextGenerationOnlyAfterEnding() throws Exception {
        try (var f = new Fixture()) {
            tables(f); var h = new Harness(f, EntityProcessLinkMapper.class); var flow = new Flow(h);
            var results = concurrent(6, index -> flow.start());
            assertEquals(1, results.stream().map(result -> result.processInstanceId()).distinct().count());
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_process_link", Integer.class));
            assertEquals(1, h.jdbc.queryForObject("SELECT hits FROM process_engine_effect", Integer.class));
            assertEquals(6, h.jdbc.queryForObject("SELECT touches FROM process_root", Integer.class));
            String first = results.get(0).processInstanceId();
            h.tx.executeWithoutResult(status -> assertEquals(1, h.mapper(EntityProcessLinkMapper.class).closeActive(first, "APPROVED")));
            var second = flow.start();
            assertNotEquals(first, second.processInstanceId());
            assertEquals(second.processInstanceId(), flow.start().processInstanceId());
            assertEquals(List.of(1, 2), h.jdbc.queryForList("SELECT generation FROM entity_process_link ORDER BY generation", Integer.class));
            assertEquals(2, h.jdbc.queryForObject("SELECT hits FROM process_engine_effect", Integer.class));
            assertEquals(List.of("ENDED", "ACTIVE"), h.jdbc.queryForList("SELECT state FROM entity_process_link ORDER BY generation", String.class));
        }
    }

    @Test
    void engineFailureRollsBackTheLinkAndEarlierEntityWriteSoRetryCanStart() {
        try (var f = new Fixture()) {
            tables(f); var h = new Harness(f, EntityProcessLinkMapper.class); var flow = new Flow(h);
            flow.fail.set(true);
            assertThrows(IllegalStateException.class, flow::start);
            assertEquals(0, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_process_link", Integer.class));
            assertEquals(0, h.jdbc.queryForObject("SELECT hits FROM process_engine_effect", Integer.class));
            assertEquals(0, h.jdbc.queryForObject("SELECT touches FROM process_root", Integer.class));
            flow.fail.set(false);
            assertNotNull(flow.start().processInstanceId());
            assertEquals(1, h.jdbc.queryForObject("SELECT COUNT(*) FROM entity_process_link", Integer.class));
            assertEquals(1, h.jdbc.queryForObject("SELECT hits FROM process_engine_effect", Integer.class));
        }
    }

    @Test
    void existingPendingOrDifferentActiveDefinitionCannotBeTakenOver() {
        try (var f = new Fixture()) {
            tables(f); var h = new Harness(f, EntityProcessLinkMapper.class); var flow = new Flow(h);
            var first = flow.start();
            String originalId = h.jdbc.queryForObject("SELECT id FROM entity_process_link", String.class);
            flow.definition.setProcessKey("another-flow");
            var active = assertThrows(BusinessConflictException.class, flow::start);
            assertEquals("ENTITY_PROCESS_ALREADY_ACTIVE", active.getErrorCode());
            flow.definition.setProcessKey("expense_flow");
            h.jdbc.update("UPDATE entity_process_link SET state='PENDING' WHERE id=?", originalId);
            var pending = assertThrows(BusinessConflictException.class, flow::start);
            assertEquals("ENTITY_PROCESS_START_IN_PROGRESS", pending.getErrorCode());
            assertEquals(originalId, h.jdbc.queryForObject("SELECT id FROM entity_process_link", String.class));
            assertEquals(first.processInstanceId(), h.jdbc.queryForObject("SELECT process_instance_id FROM entity_process_link", String.class));
            assertEquals(1, h.jdbc.queryForObject("SELECT hits FROM process_engine_effect", Integer.class));
        }
    }

    private static final class Flow {
        final Harness h;
        final AtomicBoolean fail = new AtomicBoolean();
        final ProcessDefinitionConfig definition = new ProcessDefinitionConfig();
        final ProcessRuntimeService service;

        Flow(Harness h) {
            this.h = h;
            h.jdbc.update("INSERT INTO process_root VALUES ('record',0)");
            h.jdbc.update("INSERT INTO process_engine_effect VALUES (1,0)");
            definition.setId("config"); definition.setProcessKey("expense_flow"); definition.setProcessName("审批");
            definition.setStatus(ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
            var config = mock(ProcessDefinitionConfigMapper.class); when(config.selectById("config")).thenReturn(definition);
            var repository = mock(RepositoryService.class); var query = mock(ProcessDefinitionQuery.class, RETURNS_SELF);
            var deployed = mock(ProcessDefinition.class); when(deployed.getId()).thenReturn("definition");
            when(repository.createProcessDefinitionQuery()).thenReturn(query); when(query.singleResult()).thenReturn(deployed);
            var model = new BpmnModel(); model.addProcess(new org.flowable.bpmn.model.Process());
            when(repository.getBpmnModel("definition")).thenReturn(model);
            var runtime = mock(RuntimeService.class); var sequence = new AtomicInteger();
            when(runtime.startProcessInstanceById(eq("definition"), eq("record"), anyMap())).thenAnswer(call -> {
                h.jdbc.update("UPDATE process_engine_effect SET hits=hits+1 WHERE id=1");
                if (fail.get()) throw new IllegalStateException("engine startup failed");
                var instance = mock(ProcessInstance.class); when(instance.getId()).thenReturn("engine-" + sequence.incrementAndGet()); return instance;
            });
            var running = mock(ProcessInstanceQuery.class, RETURNS_SELF);
            when(runtime.createProcessInstanceQuery()).thenReturn(running); when(running.singleResult()).thenReturn(mock(ProcessInstance.class));
            var tasks = mock(org.flowable.engine.TaskService.class); var taskQuery = mock(TaskQuery.class, RETURNS_SELF);
            when(tasks.createTaskQuery()).thenReturn(taskQuery); when(taskQuery.listPage(0, 1)).thenReturn(List.of());
            service = transactional(new ProcessRuntimeService(config, repository, runtime, mock(IdentityService.class), tasks,
                    mock(ProcessTaskService.class), mock(MultiInstanceCollectionListener.class), h.mapper(EntityProcessLinkMapper.class),
                    new JdbcLockedRow(h.jdbc, DatabaseDialects.insert(DatabaseVendor.MYSQL)), new JdbcDatabaseClock(h.jdbc, DatabaseVendor.MYSQL)), h);
        }

        /** 与生产 EntityDataMutationService 一致：先写实体根行，再在同一事务发起流程。 */
        com.workflow.contracts.process.ProcessStartResult start() {
            return h.tx.execute(status -> {
                h.jdbc.update("UPDATE process_root SET touches=touches+1 WHERE id='record'");
                return service.start(new ProcessStartRequest("config", "expense", "record", "EXP-1", "admin", "管理员", null, Map.of(), Map.of()));
            });
        }
    }

    private static void tables(Fixture f) {
        f.table("entity_process_link", "id VARCHAR(64) PRIMARY KEY, entity_code VARCHAR(63) NOT NULL, entity_record_id VARCHAR(64) NOT NULL, "
                + "generation INT NOT NULL, process_definition_key VARCHAR(255) NOT NULL, process_instance_id VARCHAR(64), state VARCHAR(20) NOT NULL, "
                + "request_id VARCHAR(64) NOT NULL UNIQUE, entity_status VARCHAR(50), version BIGINT NOT NULL, create_time DATETIME(6), update_time DATETIME(6), "
                + "ended_at DATETIME(6), end_type VARCHAR(50), UNIQUE(entity_code,entity_record_id,generation)");
        f.table("process_root", "id VARCHAR(64) PRIMARY KEY, touches INT NOT NULL");
        f.table("process_engine_effect", "id INT PRIMARY KEY, hits INT NOT NULL");
    }
}
