package com.workflow.process.action;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.process.action.context.FlowActionContext;
import com.workflow.contracts.process.action.model.FailureDecision;
import com.workflow.process.action.application.*;
import com.workflow.process.action.infrastructure.persistence.record.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;

import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 真实数据库、生产 Mapper 与事务代理验证成功依赖，不使用内存排序模拟持久化调度。 */
class FlowActionDependencyExecutionTest {
    @Test void committedSuccessGatesDependentAndRepeatedTriggersStayIsolated() throws Exception {
        var f = new FlowActionCustomFailureTransactionTest.Fixture();
        var actions = actions(f);
        // 故意让后继排在前面，配置顺序不能代替依赖；每次触发均创建自己的前置执行关联。
        f.service.enqueueActions(actions, f.event());
        f.service.enqueueActions(actions, f.event());
        var all = rows(f);
        var roots = all.stream().filter(row -> row.getPredecessorExecutionId() == null).toList();
        assertEquals(2, f.service.findReady(50).size());
        assertEquals(2, roots.size());
        var root = f.service.claim(roots.get(0).getId(), "pod-a", 300);
        var child = all.stream().filter(row -> root.getId().equals(row.getPredecessorExecutionId())).findFirst().orElseThrow();
        assertNull(f.service.claim(child.getId(), "pod-b", 300));
        var result = new FlowActionContext(); result.setExecutionResult(Map.of("contractNo", "HT-001", "secret", "original"));
        f.service.markSuccess(root, result);
        assertEquals(2, f.service.findReady(50).size()); // 另一次触发的根动作 + 本次后继。
        assertEquals("PENDING", f.mapper.selectById(child.getId()).getStatus());
        assertEquals(1, rows(f).stream().filter(row -> "WAITING".equals(row.getStatus())).count());
        assertEquals("HT-001", f.service.predecessorResult(child, "contractNo"));
        assertEquals("original", f.service.predecessorResult(child, "secret"));
        var detail = f.service.findDetailsByProcessInstanceId("process").stream().filter(row -> row.getId().equals(root.getId())).findFirst().orElseThrow();
        assertEquals("******", ((Map<?, ?>) detail.getResult()).get("secret"));
        assertFalse(new ObjectMapper().findAndRegisterModules().writeValueAsString(detail).contains("original"));
        assertThrows(IllegalArgumentException.class, () -> f.service.predecessorResult(child, "missing"));
    }

    @Test void batchRollsBackAndUncommittedSuccessCannotReleaseChild() throws Exception {
        var f = new FlowActionCustomFailureTransactionTest.Fixture();
        assertThrows(IllegalStateException.class, () -> f.operation.run(() -> {
            f.service.enqueueActions(actions(f), f.event());
            throw new IllegalStateException("主事务回滚");
        }));
        assertTrue(rows(f).isEmpty());
        f.service.enqueueActions(actions(f), f.event());
        var root = rows(f).stream().filter(row -> row.getPredecessorExecutionId() == null).findFirst().orElseThrow();
        f.service.claim(root.getId(), "pod-a", 300);
        f.operation.run(() -> {
            f.jdbc.update("UPDATE process_action_execution SET status='SUCCESS' WHERE id=?", root.getId());
            var read = Executors.newSingleThreadExecutor();
            try { assertTrue(read.submit(() -> f.service.findReady(50)).get(5, TimeUnit.SECONDS).isEmpty()); }
            catch (Exception error) { throw new IllegalStateException(error); }
            finally { read.shutdownNow(); }
        });
        assertEquals(1, f.service.findReady(50).size());
    }

    @Test void leaseFencingBlocksFalseSuccessAndOnlyFailedStepRetries() throws Exception {
        var f = new FlowActionCustomFailureTransactionTest.Fixture();
        f.service.enqueueActions(actions(f), f.event());
        var root = f.service.claim(f.service.findReady(50).get(0).getId(), "pod-a", 300);
        f.jdbc.update("UPDATE process_action_execution SET lease_token=lease_token+1 WHERE id=?", root.getId());
        var result = new FlowActionContext(); result.setExecutionResult(Map.of("contractNo", "HT-001"));
        assertThrows(IllegalStateException.class, () -> f.service.markSuccess(root, result));
        assertTrue(f.service.findReady(50).isEmpty());
        var actual = f.mapper.selectById(root.getId()); f.service.markSuccess(actual, result);
        var child = f.service.claim(f.service.findReady(50).get(0).getId(), "pod-b", 300);
        f.service.markRetryFailure(child, new IllegalStateException("通知失败"));
        f.jdbc.update("UPDATE process_action_execution SET next_retry_time=NULL WHERE id=?", child.getId());
        var ready = f.service.findReady(50);
        assertEquals(List.of(child.getId()), ready.stream().map(FlowActionExecution::getId).toList());
        assertEquals("SUCCESS", f.mapper.selectById(root.getId()).getStatus());
        assertEquals("HT-001", f.service.predecessorResult(child, "contractNo"));
    }

    @Test void onlyOnePodClaimsReleasedChildAndSnapshotSurvivesConfigurationChanges() throws Exception {
        var f = new FlowActionCustomFailureTransactionTest.Fixture();
        var actions = actions(f); f.service.enqueueActions(actions, f.event());
        var root = f.service.claim(f.service.findReady(50).get(0).getId(), "pod-a", 300);
        var result = new FlowActionContext(); result.setExecutionResult(Map.of("contractNo", 123));
        f.service.markSuccess(root, result);
        var child = f.service.findReady(50).get(0);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var claims = executor.invokeAll(List.of(() -> f.service.claim(child.getId(), "pod-b", 300),
                    () -> f.service.claim(child.getId(), "pod-c", 300)));
            int count = 0; for (var claim : claims) if (claim.get() != null) count++;
            assertEquals(1, count);
        } finally { executor.shutdownNow(); }
        actions.get(0).setParamsJson("{\"contractNo\":\"changed\"}");
        var frozen = f.service.executionAction(child, actions.get(0));
        assertTrue(frozen.getParamsJson().contains("$predecessor"));
        var beans = mock(ApplicationContext.class);
        var handler = mock(com.workflow.contracts.process.action.spi.FlowActionProvider.class);
        when(beans.getBean("handler")).thenReturn(handler);
        var actualExecutor = new FlowActionExecutor(beans, null, f.service, new ObjectMapper());
        var context = actualExecutor.executeAction(frozen, f.event(), "stable", f.mapper.selectById(child.getId()));
        assertEquals(123, context.getExtraParams().get("contractNo"));
    }

    @Test void terminalFailureWaitsAndSuccessfulManualReplayUnblocksWithoutChangingFailureFact() throws Exception {
        var f = new FlowActionCustomFailureTransactionTest.Fixture();
        var actions = actions(f); actions.get(1).setFailurePolicy("CUSTOM");
        f.service.enqueueActions(actions, f.event());
        var root = f.service.claim(f.service.findReady(50).get(0).getId(), "pod-a", 300);
        f.service.markFinalFailure(root, new IllegalStateException("失败"));
        assertTrue(f.service.findReady(50).isEmpty());
        f.service.retry(root.getId());
        var replay = f.service.findReady(50).get(0);
        assertEquals(root.getId(), replay.getReplayRootId());
        replay = f.service.claim(replay.getId(), "pod-b", 300);
        var result = new FlowActionContext(); result.setExecutionResult(Map.of("contractNo", "HT-replay"));
        f.service.markSuccess(replay, result);
        var child = f.service.findReady(50).get(0);
        assertEquals("DEAD", f.mapper.selectById(root.getId()).getStatus());
        assertEquals("HT-replay", f.service.predecessorResult(child, "contractNo"));
    }

    private List<FlowAction> actions(FlowActionCustomFailureTransactionTest.Fixture f) {
        var first = f.action("AFTER_COMMIT", 3); first.setId("create"); first.setActionName("创建合同"); first.setFailurePolicy("RETRY");
        var second = f.action("AFTER_COMMIT", 3); second.setId("notify"); second.setActionName("发送通知"); second.setFailurePolicy("RETRY");
        second.setPredecessorActionId("create"); second.setParamsJson("{\"contractNo\":{\"$predecessor\":\"contractNo\"}}");
        return List.of(second, first);
    }
    private List<FlowActionExecution> rows(FlowActionCustomFailureTransactionTest.Fixture f) {
        return f.mapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers.emptyWrapper());
    }
}
