package com.workflow.process.engine.infrastructure.flowable;

import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.process.status.application.ProcessEntityStatusPolicy;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.common.engine.api.delegate.event.*;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.FlowableSequenceFlowTakenEvent;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.UUID;

/** 实际经过连线才改变业务状态；与引擎推进共用事务，失败必须回滚。 */
@Component
@RequiredArgsConstructor
public class EntityTransitionStatusListener implements FlowableEventListener {
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final ProcessEntityStatusPolicy statusPolicy;
    private final EntityMutationPort mutationPort;

    /**
     * 响应事件阶段的回调，并将结果传给后续处理。
     *
     * @param event 事件，供本方法处理事件时使用
     */
    @Override
    public void onEvent(FlowableEvent event) {
        if (event.getType() != FlowableEngineEventType.SEQUENCEFLOW_TAKEN
                || !(event instanceof FlowableSequenceFlowTakenEvent flow)) return;
        if (!statusPolicy.usesTransitions(flow.getProcessDefinitionId())) return;
        var element = repositoryService.getBpmnModel(flow.getProcessDefinitionId())
                .getFlowElement(flow.getId());
        if (!(element instanceof SequenceFlow)) return;
        String status = ProcessEntityStatusPolicy.property(element, "entityStatusCode");
        if (status == null || status.isBlank()) return;
        String instanceId = flow.getProcessInstanceId();
        Object code = runtimeService.getVariable(instanceId, "entityCode");
        Object recordId = runtimeService.getVariable(instanceId, "entityDataId");
        if (code == null || recordId == null) return;
        // 每次经过都是一次独立变更；不能用连线 ID 永久去重，否则循环第二次经过会丢失。
        // 本监听同步运行，事务回滚同时撤销变更与回执，不需要异步重放同一个事件。
        String operationId = "transition-status:" + UUID.randomUUID();
        mutationPort.execute(new EntityMutationCommand(operationId, code.toString(), recordId.toString(),
                EntityMutationOperationType.STATUS_CHANGE, Map.of("status", status.trim()),
                EntityMutationContext.builder(EntityMutationSourceType.PROCESS_RUNTIME,
                                "PROCESS_TRANSITION_STATUS", "流程连线业务状态")
                        .sourceId(flow.getId()).sourceRecord(code.toString(), recordId.toString())
                        .process(flow.getProcessDefinitionId(), instanceId, null)
                        .operator("system", "流程引擎").trace(instanceId, operationId).build()));
    }
    /**
     * 判断是否失败异常；判断结果决定调用方的后续分支。
     *
     * @return 失败异常条件成立时为 true，否则为 false
     */
    @Override public boolean isFailOnException() { return true; }
    /**
     * 判断是否{@code fire}事务生命周期事件；判断结果决定调用方的后续分支。
     *
     * @return {@code fire}事务生命周期事件条件成立时为 true，否则为 false
     */
    @Override public boolean isFireOnTransactionLifecycleEvent() { return false; }
    /**
     * 读取事务；查询结果供调用方展示或继续处理。
     *
     * @return 读取后的事务文本，供调用方比较或展示
     */
    @Override public String getOnTransaction() { return null; }
}
