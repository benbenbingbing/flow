package com.workflow.listener;

import com.workflow.process.engine.infrastructure.flowable.ProcessEndListener;

import com.workflow.contracts.entity.mutation.EntityChangeTargetPort;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.entity.data.domain.policy.EntityProcessStatusPolicy;
import com.workflow.process.status.application.ProcessStatusSyncPublisher;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.HistoryService;
import org.flowable.engine.delegate.event.impl.FlowableEntityEventImpl;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.variable.api.history.HistoricVariableInstance;
import org.flowable.variable.api.history.HistoricVariableInstanceQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程结束监听器单元测试。
 *
 * <p>被测对象为 {@link ProcessEndListener}，验证实体状态在流程结束时
 * 是否需要保留(取决于状态分类是否匹配流程结束分类)。</p>
 */
class ProcessEndListenerTest {

    /**
     * 实体状态分类与流程结束分类匹配时应保留显式状态。
     *
     * <p>场景：状态分类为 COMPLETED，断言 shouldPreserveStatus 返回 true。</p>
     */
    @Test
    void preservesExplicitStatusWhenCategoryMatchesProcessEnd() {
        assertTrue(EntityProcessStatusPolicy.shouldPreserve("COMPLETED", "COMPLETED"));
    }

    /**
     * 实体状态分类与流程结束分类不匹配时应替换状态。
     *
     * <p>场景：状态分类为 PROCESSING，断言 shouldPreserveStatus 返回 false。</p>
     */
    @Test
    void replacesStatusWhenCategoryDoesNotMatchProcessEnd() {
        assertFalse(EntityProcessStatusPolicy.shouldPreserve("PROCESSING", "COMPLETED"));
    }

    @Test
    void processCompletionPublishesDurableEndEvent() {
        HistoryService historyService = mock(HistoryService.class);
        ProcessStatusSyncPublisher publisher =
                mock(ProcessStatusSyncPublisher.class);
        EntityMutationPort mutationPort = mock(EntityMutationPort.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<EntityChangeTargetPort> changeTargetPortProvider =
                mock(ObjectProvider.class);
        HistoricVariableInstanceQuery entityCodeQuery =
                variableQuery("expense");
        HistoricVariableInstanceQuery entityIdQuery =
                variableQuery("record-1");
        when(historyService.createHistoricVariableInstanceQuery())
                .thenReturn(entityCodeQuery, entityIdQuery);
        HistoricProcessInstanceQuery processQuery =
                mock(HistoricProcessInstanceQuery.class);
        HistoricProcessInstance historic =
                mock(HistoricProcessInstance.class);
        when(historyService.createHistoricProcessInstanceQuery())
                .thenReturn(processQuery);
        when(processQuery.processInstanceId("process-1"))
                .thenReturn(processQuery);
        when(processQuery.singleResult()).thenReturn(historic);

        ProcessInstance processInstance = mock(ProcessInstance.class);
        when(processInstance.getId()).thenReturn("process-1");
        FlowableEntityEventImpl event =
                mock(FlowableEntityEventImpl.class);
        when(event.getType())
                .thenReturn(FlowableEngineEventType.PROCESS_COMPLETED);
        when(event.getEntity()).thenReturn(processInstance);
        ProcessEndListener listener =
                new ProcessEndListener(
                        historyService,
                        mutationPort,
                        changeTargetPortProvider,
                        publisher);

        listener.onEvent(event);

        verify(publisher).publishProcessEnd(
                "process-1",
                "expense",
                "record-1",
                "COMPLETED",
                "APPROVED");
        assertTrue(listener.isFailOnException());
    }

    private HistoricVariableInstanceQuery variableQuery(Object value) {
        HistoricVariableInstanceQuery query =
                mock(HistoricVariableInstanceQuery.class);
        HistoricVariableInstance variable =
                mock(HistoricVariableInstance.class);
        when(query.processInstanceId("process-1")).thenReturn(query);
        when(query.variableName("entityCode")).thenReturn(query);
        when(query.variableName("entityDataId")).thenReturn(query);
        when(query.singleResult()).thenReturn(variable);
        when(variable.getValue()).thenReturn(value);
        return query;
    }
}
