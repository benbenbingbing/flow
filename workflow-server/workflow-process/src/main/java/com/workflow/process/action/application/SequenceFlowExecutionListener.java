package com.workflow.process.action.application;

import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;

/**
 * 顺序流执行监听器。
 *
 * <p>Flowable 顺序流被触发时调用，负责找到当前流程版本、补充顺序流源/目标节点信息，并执行对应的流程动作。</p>
 */
@Slf4j
@Component
public class SequenceFlowExecutionListener implements ExecutionListener {

    /**
     * 通知序列流程执行监听器；后续由接收方或异步任务继续处理。
     *
     * @param execution 执行，供本方法通知序列流程执行监听器时使用
     */
    @Override
    public void notify(DelegateExecution execution) {
        log.debug("忽略历史 BPMN 注入的 sequenceFlowExecutionListener，统一事件监听器已接管: processInstanceId={}, sequenceFlowId={}",
                execution.getProcessInstanceId(), execution.getCurrentActivityId());
    }
}
