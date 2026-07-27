package com.workflow.listener;

import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;

import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import org.flowable.common.engine.api.delegate.event.FlowableEngineEventType;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.delegate.event.FlowableActivityEvent;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MultiInstanceCollectionListenerTest {

    @Test
    void ignoresActivityEventsOtherThanStarted() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        RuntimeService runtimeService = mock(RuntimeService.class);
        ReflectionTestUtils.setField(
                listener,
                "runtimeService",
                runtimeService);
        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        when(event.getType())
                .thenReturn(FlowableEngineEventType.ACTIVITY_COMPLETED);

        listener.onEvent(event);

        verifyNoInteractions(runtimeService);
    }

    @Test
    void resolvesOpaqueDefinitionIdThroughRepositoryService() {
        MultiInstanceCollectionListener listener =
                new MultiInstanceCollectionListener();
        RuntimeService runtimeService = mock(RuntimeService.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        ProcessDefinitionQuery definitionQuery =
                mock(ProcessDefinitionQuery.class);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        ProcessDefinitionConfigMapper processMapper =
                mock(ProcessDefinitionConfigMapper.class);
        ReflectionTestUtils.setField(
                listener,
                "runtimeService",
                runtimeService);
        ReflectionTestUtils.setField(
                listener,
                "repositoryService",
                repositoryService);
        ReflectionTestUtils.setField(
                listener,
                "processMapper",
                processMapper);

        when(repositoryService.createProcessDefinitionQuery())
                .thenReturn(definitionQuery);
        when(definitionQuery.processDefinitionId("opaque-definition-id"))
                .thenReturn(definitionQuery);
        when(definitionQuery.singleResult()).thenReturn(definition);
        when(definition.getKey()).thenReturn("expense_flow");
        when(processMapper.findByProcessKey("expense_flow"))
                .thenReturn(Optional.empty());

        FlowableActivityEvent event = mock(FlowableActivityEvent.class);
        when(event.getType())
                .thenReturn(FlowableEngineEventType.ACTIVITY_STARTED);
        when(event.getProcessInstanceId()).thenReturn("instance-1");
        when(event.getProcessDefinitionId())
                .thenReturn("opaque-definition-id");
        when(event.getActivityId()).thenReturn("Task_Review");

        assertDoesNotThrow(() -> listener.onEvent(event));
        verify(processMapper).findByProcessKey("expense_flow");
        verifyNoInteractions(runtimeService);
    }
}
