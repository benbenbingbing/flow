package com.workflow.process.action;

import com.workflow.dto.FlowActionTimingOptionDTO;
import com.workflow.entity.FlowAction;
import com.workflow.entity.FlowActionExecution;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import com.workflow.service.FlowActionExecutionService;
import com.workflow.service.FlowActionService;
import org.flowable.engine.RepositoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 流程动作事件分发器单元测试。
 *
 * <p>被测对象为 {@link FlowActionEventDispatcher}，验证事务内动作失败时的回滚与继续策略、
 * 事务提交后动作的异步入队逻辑。</p>
 */
class FlowActionEventDispatcherTest {

    /** 模拟的动作查询服务 */
    private FlowActionService actionService;
    /** 模拟的动作执行器 */
    private FlowActionExecutor executor;
    /** 模拟的动作执行记录服务 */
    private FlowActionExecutionService executionService;
    /** 被测分发器实例 */
    private FlowActionEventDispatcher dispatcher;

    /** 初始化 mock 依赖与分发器实例，设置动作时序目录桩数据 */
    @BeforeEach
    void setUp() {
        actionService = mock(FlowActionService.class);
        executor = mock(FlowActionExecutor.class);
        executionService = mock(FlowActionExecutionService.class);
        FlowActionTimingCatalog catalog = mock(FlowActionTimingCatalog.class);
        when(catalog.find(anyString())).thenReturn(Optional.of(new FlowActionTimingOptionDTO(
                "TASK_COMPLETING", "任务提交", "", "NODE", true,
                "IN_TRANSACTION", "ROLLBACK", "", false)));
        dispatcher = new FlowActionEventDispatcher(
                actionService,
                executor,
                executionService,
                catalog,
                mock(ProcessVersionHistoryMapper.class),
                mock(RepositoryService.class));
    }

    /**
     * 事务内动作执行失败且策略为回滚时应标记最终失败并向上抛出异常。
     *
     * <p>场景：动作执行抛出 RuntimeException，断言 dispatch 也抛出异常，
     * 且 executionService.markFinalFailure 被调用。</p>
     */
    @Test
    void shouldRollbackWhenTransactionalActionFails() {
        FlowAction action = action("IN_TRANSACTION", "ROLLBACK");
        FlowActionTriggerEvent event = event();
        when(actionService.findPublishedActionsByBinding(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(action));
        FlowActionExecution execution = new FlowActionExecution();
        when(executionService.createInTransactionAudit(
                eq(action), eq(event), anyString(), eq(FlowActionExecution.Status.RUNNING)))
                .thenReturn(execution);
        doThrow(new RuntimeException("blocked")).when(executor)
                .executeAction(eq(action), eq(event), anyString(), eq(execution));

        assertThrows(RuntimeException.class, () -> dispatcher.dispatch(event));
        verify(executionService).markFinalFailure(eq(execution), any(RuntimeException.class));
    }

    /**
     * 事务内动作执行失败但策略为继续时应标记失败后不中断流程。
     *
     * <p>场景：动作执行抛出 RuntimeException，断言 dispatch 正常返回，
     * 且 executionService.markFinalFailure 被调用。</p>
     */
    @Test
    void shouldContinueWhenTransactionalActionUsesContinuePolicy() {
        FlowAction action = action("IN_TRANSACTION", "CONTINUE");
        FlowActionTriggerEvent event = event();
        FlowActionExecution execution = new FlowActionExecution();
        when(actionService.findPublishedActionsByBinding(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(action));
        when(executionService.createInTransactionAudit(
                eq(action), eq(event), anyString(), eq(FlowActionExecution.Status.RUNNING)))
                .thenReturn(execution);
        doThrow(new RuntimeException("ignored")).when(executor)
                .executeAction(eq(action), eq(event), anyString(), eq(execution));

        dispatcher.dispatch(event);

        verify(executionService).markFinalFailure(eq(execution), any(RuntimeException.class));
    }

    /**
     * 事务提交后动作应仅入队待执行审计记录，不立即调用执行器。
     *
     * <p>场景：动作执行模式为 AFTER_COMMIT，断言 create 以 PENDING 状态被调用，
     * 且 executor 未被交互。</p>
     */
    @Test
    void shouldEnqueueAfterCommitActionWithoutExecutingHandler() {
        FlowAction action = action("AFTER_COMMIT", "RETRY");
        FlowActionTriggerEvent event = event();
        when(actionService.findPublishedActionsByBinding(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(action));

        dispatcher.dispatch(event);

        verify(executionService).create(eq(action), eq(event), anyString(), eq(FlowActionExecution.Status.PENDING));
        verifyNoInteractions(executor);
    }

    /**
     * 构造测试用动作对象。
     *
     * @param mode 执行模式(IN_TRANSACTION/AFTER_COMMIT)
     * @param policy 失败策略(ROLLBACK/CONTINUE/RETRY)
     * @return 已设置基础字段的 FlowAction 实例
     */
    private FlowAction action(String mode, String policy) {
        FlowAction action = new FlowAction();
        action.setId("action-1");
        action.setActionName("测试动作");
        action.setEnabled(true);
        action.setExecutionMode(mode);
        action.setFailurePolicy(policy);
        return action;
    }

    /** 构造测试用动作触发事件，绑定版本 ID、流程定义与实例 ID 等 */
    private FlowActionTriggerEvent event() {
        FlowActionTriggerEvent event = new FlowActionTriggerEvent();
        event.setVersionId("version-1");
        event.setProcessDefinitionId("process:1:def");
        event.setProcessInstanceId("pi-1");
        event.setScopeType("NODE");
        event.setElementId("Task_1");
        event.setTriggerTiming("TASK_COMPLETING");
        return event;
    }
}
