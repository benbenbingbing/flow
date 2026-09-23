package com.workflow.process.sla.runtime.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.common.engine.api.delegate.event.FlowableEntityEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEvent;
import org.flowable.common.engine.api.delegate.event.FlowableEventListener;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Component;

/** 监听 Flowable 流程挂起和激活事件，将同一实例下任务 SLA 计时同步暂停或恢复。 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskSlaProcessStateListener
        implements FlowableEventListener {

    private final TaskSlaRuntimeService runtimeService;

    /**
     * 只处理流程实例实体事件，忽略普通任务或无类型事件以免误改 SLA。
     *
     * @param event Flowable 实体事件；只用流程实例的挂起与激活事件同步任务 SLA
     */
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

    /**
     * SLA 同步失败时让引擎事务失败，避免流程状态与计时状态不一致。
     *
     * @return true，使 SLA 同步失败传播到 Flowable 事务
     */
    @Override
    public boolean isFailOnException() {
        return true;
    }

    /**
     * 使用普通事件回调，不延迟到 Flowable 事务生命周期阶段。
     *
     * @return false，要求在普通事件回调阶段执行
     */
    @Override
    public boolean isFireOnTransactionLifecycleEvent() {
        return false;
    }

    /**
     * 不注册事务阶段；与 isFireOnTransactionLifecycleEvent 的选择保持一致。
     *
     * @return null；未注册事务生命周期阶段
     */
    @Override
    public String getOnTransaction() {
        return null;
    }
}
