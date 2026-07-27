package com.workflow.process.task.infrastructure.flowable;

import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;

import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

/**
 * 接收任务超时处理代理。
 *
 * <p>发布时生成的定时边界事件会进入该节点。continue 策略记录超时变量后继续，
 * error 策略抛出异常，由 Flowable 定时作业的重试与失败机制接管。</p>
 */
@Component("receiveTaskTimeoutDelegate")
public class ReceiveTaskTimeoutDelegate implements JavaDelegate {

    @Override
    public void execute(DelegateExecution execution) {
        String receiveTaskId = ConfiguredTaskPropertyReader.read(
                execution.getCurrentFlowElement(),
                "receiveTaskId");
        String action = ConfiguredTaskPropertyReader.read(
                execution.getCurrentFlowElement(),
                "receiveTimeoutAction");
        if (receiveTaskId == null || receiveTaskId.isBlank()) {
            throw new IllegalArgumentException("接收任务超时处理缺少来源节点ID");
        }
        if (action == null || action.isBlank()) {
            action = "error";
        }

        if ("continue".equalsIgnoreCase(action)) {
            execution.setVariable("receiveTaskTimedOut", true);
            execution.setVariable("receiveTaskTimeoutActivityId", receiveTaskId);
            execution.setVariable(receiveTaskId + "_timedOut", true);
            return;
        }
        if (!"error".equalsIgnoreCase(action)) {
            throw new IllegalArgumentException("不支持的接收任务超时处理策略: " + action);
        }
        throw new IllegalStateException("接收任务超时: " + receiveTaskId);
    }
}
