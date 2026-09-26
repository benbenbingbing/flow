package com.workflow.process.action.infrastructure.flowable;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 历史 BPMN 顺序流监听器的兼容入口。
 *
 * <p>流程动作已由统一引擎事件监听器派发；这里仅保留旧 Bean 名称并忽略重复通知，
 * 使已部署的历史 BPMN 继续可执行，避免同一动作被重复派发。</p>
 */
@Slf4j
@Component("sequenceFlowExecutionListener")
public class SequenceFlowExecutionListener implements ExecutionListener {

    /**
     * 接收历史监听器调用，仅输出诊断日志，不产生动作执行副作用。
     *
     * @param execution 执行，供本方法通知序列流程执行监听器时使用
     */
    @Override
    public void notify(DelegateExecution execution) {
        log.debug("忽略历史 BPMN 注入的 sequenceFlowExecutionListener，统一事件监听器已接管: processInstanceId={}, sequenceFlowId={}",
                execution.getProcessInstanceId(), execution.getCurrentActivityId());
    }
}
