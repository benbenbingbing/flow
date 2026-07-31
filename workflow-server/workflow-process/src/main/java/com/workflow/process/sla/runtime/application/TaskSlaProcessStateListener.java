package com.workflow.process.sla.runtime.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskSlaProcessStateListener
        implements FlowableEventListener {

    private final TaskSlaRuntimeService runtimeService;

    @Override
    public void onEvent(FlowableEvent event) {
        if (!(event instanceof FlowableEntityEvent entityEvent)
                || !(entityEvent.getEntity()
                instanceof ProcessInstance processInstance)
                || event.getType() == null) {
            return;
        }
        String eventType = event.getType().name();
        if ("ENTITY_SUSPENDED".equals(eventType)) {
            runtimeService.pauseProcess(processInstance.getId());
            log.info(
                    "流程挂起，已暂停活动任务SLA: processInstanceId={}",
                    processInstance.getId());
        } else if ("ENTITY_ACTIVATED".equals(eventType)) {
            runtimeService.resumeProcess(processInstance.getId());
            log.info(
                    "流程激活，已恢复流程挂起产生的SLA暂停: processInstanceId={}",
                    processInstance.getId());
        }
    }

    @Override
    public boolean isFailOnException() {
        return true;
    }

    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    @Override
    public String getOnTransaction() {
        return null;
    }
}
