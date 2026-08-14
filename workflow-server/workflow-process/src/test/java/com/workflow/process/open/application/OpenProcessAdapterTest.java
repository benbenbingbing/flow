package com.workflow.process.open.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.contracts.process.open.OpenApplicationActor;
import com.workflow.contracts.process.open.OpenBusinessReference;
import com.workflow.contracts.process.open.OpenMessageCorrelationCommand;
import com.workflow.contracts.process.open.OpenProcessNotFoundException;
import com.workflow.contracts.process.open.OpenProcessStartCommand;
import com.workflow.contracts.process.open.OpenProcessStateConflictException;
import com.workflow.contracts.process.open.OpenProcessIdentityNotResolvedException;
import com.workflow.contracts.identity.external.ExternalIdentityResolutionRequest;
import com.workflow.contracts.identity.external.ExternalIdentityResolver;
import com.workflow.process.assignment.infrastructure.flowable.MultiInstanceCollectionListener;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.WorkflowAutoSkipService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.engine.runtime.ProcessInstanceQuery;
import org.flowable.eventsubscription.api.EventSubscription;
import org.flowable.eventsubscription.api.EventSubscriptionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OpenProcessAdapterTest {

    private final ProcessDefinitionConfigMapper definitionMapper =
            mock(ProcessDefinitionConfigMapper.class);
    private final ProcessVersionHistoryMapper versionMapper =
            mock(ProcessVersionHistoryMapper.class);
    private final RuntimeService runtimeService =
            mock(RuntimeService.class);
    private final HistoryService historyService =
            mock(HistoryService.class);
    private final org.flowable.engine.TaskService taskService =
            mock(org.flowable.engine.TaskService.class);
    private final MultiInstanceCollectionListener multiInstanceListener =
            mock(MultiInstanceCollectionListener.class);
    private final WorkflowAutoSkipService autoSkipService =
            mock(WorkflowAutoSkipService.class);
    private final ProcessTaskService processTaskService =
            mock(ProcessTaskService.class);
    private final RepositoryService repositoryService =
            mock(RepositoryService.class);

    private OpenProcessAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenProcessAdapter(
                definitionMapper,
                versionMapper,
                runtimeService,
                historyService,
                taskService,
                multiInstanceListener,
                autoSkipService,
                processTaskService,
                repositoryService);
    }

    @Test
    void catalogReturnsOnlyThePublishedDefinitionsProvidedByMapper() {
        ProcessDefinitionConfig definition = definition();
        definition.setUpdatedAt(LocalDateTime.of(
                2026, 7, 29, 8, 30));
        ProcessVersionHistory version = new ProcessVersionHistory();
        version.setVersion(3);
        version.setPublishedAt(LocalDateTime.of(
                2026, 7, 29, 9, 0));
        when(definitionMapper.findPublishedByKeys(
                List.of("change_process")))
                .thenReturn(List.of(definition));
        when(versionMapper.findLatestByProcessKey("change_process"))
                .thenReturn(version);

        var result = adapter.listPublished(
                List.of("change_process"),
                actor());

        assertEquals(1, result.size());
        assertEquals("change_process", result.get(0).processKey());
        assertEquals(3, result.get(0).version());
        assertEquals(
                LocalDateTime.of(2026, 7, 29, 9, 0)
                        .toInstant(ZoneOffset.UTC),
                result.get(0).publishedAt());
    }

    @Test
    void startStripsInternalVariablesAndAddsTrustedMetadata() {
        when(definitionMapper.findByProcessKey("change_process"))
                .thenReturn(Optional.of(definition()));
        ProcessDefinition deployed = deployedDefinition(
                "definition-v3", 3);
        ProcessInstance started = mock(ProcessInstance.class);
        when(started.getId()).thenReturn("process-instance-01");
        when(runtimeService.startProcessInstanceById(
                eq("definition-v3"),
                eq("binding-01"),
                anyMap())).thenReturn(started);
        ProcessInstanceQuery query =
                mock(ProcessInstanceQuery.class, RETURNS_SELF);
        when(runtimeService.createProcessInstanceQuery())
                .thenReturn(query);
        when(query.singleResult()).thenReturn(started);
        when(started.getProcessDefinitionKey())
                .thenReturn("change_process");
        when(started.getStartTime()).thenReturn(new Date(1_000));

        var result = adapter.start(new OpenProcessStartCommand(
                "change_process",
                "binding-01",
                new OpenBusinessReference(
                        "project-system",
                        "change-request",
                        "business-01"),
                "external-user",
                Map.of(
                        "title", "Release",
                        "initiator", "admin",
                        "entityDataId", "private-record"),
                actor()));

        ArgumentCaptor<Map<String, Object>> variables =
                ArgumentCaptor.forClass(Map.class);
        verify(runtimeService).startProcessInstanceById(
                eq("definition-v3"),
                eq("binding-01"),
                variables.capture());
        verify(multiInstanceListener).prepareVariables(
                eq(deployed.getId()), anyMap());
        verify(autoSkipService).autoSkipNodes(
                "process-instance-01", deployed.getId());
        assertEquals("Release", variables.getValue().get("title"));
        assertFalse(variables.getValue().containsKey("initiator"));
        assertFalse(variables.getValue().containsKey("entityDataId"));
        assertEquals(
                "application-01",
                variables.getValue().get("integrationApplicationId"));
        assertEquals(
                "external-user",
                variables.getValue().get(
                        "integrationExternalInitiatorId"));
        assertEquals("process-instance-01", result.processInstanceId());
    }

    @Test
    void resolvesConfiguredExternalIdentityBeforeStartingFlowable() {
        ExternalIdentityResolver resolver = mock(
                ExternalIdentityResolver.class);
        when(resolver.supports("external")).thenReturn(true);
        when(resolver.resolve(any(ExternalIdentityResolutionRequest.class)))
                .thenReturn(Optional.of("flow-user"));
        OpenProcessAdapter resolvingAdapter = new OpenProcessAdapter(
                definitionMapper,
                versionMapper,
                runtimeService,
                historyService,
                taskService,
                multiInstanceListener,
                autoSkipService,
                processTaskService,
                repositoryService,
                List.of(resolver));
        when(definitionMapper.findByProcessKey("change_process"))
                .thenReturn(Optional.of(definition()));
        deployedDefinition("definition-v3", 3);
        ProcessInstance started = mock(ProcessInstance.class);
        when(started.getId()).thenReturn("process-instance-01");
        when(runtimeService.startProcessInstanceById(
                eq("definition-v3"),
                eq("binding-01"),
                anyMap())).thenReturn(started);
        ProcessInstanceQuery query = mock(
                ProcessInstanceQuery.class, RETURNS_SELF);
        when(runtimeService.createProcessInstanceQuery()).thenReturn(query);
        when(query.singleResult()).thenReturn(started);
        when(started.getProcessDefinitionKey()).thenReturn("change_process");
        when(started.getStartTime()).thenReturn(new Date(1_000));

        ArgumentCaptor<Map<String, Object>> variables =
                ArgumentCaptor.forClass(Map.class);
        resolvingAdapter.start(new OpenProcessStartCommand(
                "change_process",
                "binding-01",
                new OpenBusinessReference(
                        "external", "request", "REQ-1"),
                "external-user",
                Map.of("requesterId", "external-user"),
                actor(),
                null,
                "external",
                "{\"outcomeCode\":\"variables.decision\"}"));

        verify(runtimeService).startProcessInstanceById(
                eq("definition-v3"), eq("binding-01"), variables.capture());
        assertEquals("flow-user", variables.getValue().get("startUserId"));
        assertEquals("flow-user", variables.getValue().get("initiator"));
        assertEquals(true,
                variables.getValue().get("integrationEventsDeferred"));
    }

    @Test
    void pinnedStartUsesOnlyTheSelectedDeployedDefinition() {
        ProcessDefinitionConfig currentDraft = definition();
        currentDraft.setId("current-process-config");
        when(definitionMapper.findByProcessKey("change_process"))
                .thenReturn(Optional.of(currentDraft));
        ProcessDefinition deployed = deployedDefinition(
                "definition-v1", 1);
        ProcessInstance started = mock(ProcessInstance.class);
        when(started.getId()).thenReturn("process-instance-v1");
        when(runtimeService.startProcessInstanceById(
                eq("definition-v1"), eq("binding-v1"), anyMap()))
                .thenReturn(started);
        ProcessInstanceQuery query = mock(
                ProcessInstanceQuery.class, RETURNS_SELF);
        when(runtimeService.createProcessInstanceQuery())
                .thenReturn(query);
        when(query.singleResult()).thenReturn(started);
        when(started.getProcessDefinitionKey())
                .thenReturn("change_process");
        when(started.getStartTime()).thenReturn(new Date(1_000));

        adapter.start(new OpenProcessStartCommand(
                "change_process",
                "binding-v1",
                new OpenBusinessReference(
                        "project-system", "change-request", "business-v1"),
                null,
                Map.of(),
                actor(),
                1));

        verify(multiInstanceListener).prepareVariables(
                eq(deployed.getId()), anyMap());
        verify(runtimeService).startProcessInstanceById(
                eq(deployed.getId()), eq("binding-v1"), anyMap());
        verify(autoSkipService).autoSkipNodes(
                "process-instance-v1", deployed.getId());
        verify(multiInstanceListener, never()).prepareVariables(
                eq("current-process-config"), anyMap());
        verify(autoSkipService, never()).autoSkipNodes(
                "process-instance-v1", "current-process-config");
    }

    @Test
    void rejectsConfiguredExternalIdentityWithoutAResolver() {
        when(definitionMapper.findByProcessKey("change_process"))
                .thenReturn(Optional.of(definition()));
        assertThrows(
                OpenProcessIdentityNotResolvedException.class,
                () -> new OpenProcessAdapter(
                        definitionMapper,
                        versionMapper,
                        runtimeService,
                        historyService,
                        taskService,
                        multiInstanceListener,
                        autoSkipService,
                        processTaskService,
                        null,
                        List.of()).start(new OpenProcessStartCommand(
                        "change_process",
                        "binding-01",
                        new OpenBusinessReference(
                                "external", "request", "REQ-1"),
                        "external-user",
                        Map.of(),
                        actor(),
                        null,
                        "external",
                        null)));
    }

    @Test
    void missingRuntimeAndHistoryInstanceReturnsStableNotFound() {
        ProcessInstanceQuery runtimeQuery =
                mock(ProcessInstanceQuery.class, RETURNS_SELF);
        HistoricProcessInstanceQuery historyQuery =
                mock(HistoricProcessInstanceQuery.class, RETURNS_SELF);
        when(runtimeService.createProcessInstanceQuery())
                .thenReturn(runtimeQuery);
        when(historyService.createHistoricProcessInstanceQuery())
                .thenReturn(historyQuery);

        assertThrows(
                OpenProcessNotFoundException.class,
                () -> adapter.get("missing-instance", actor()));
    }

    @Test
    void messageCorrelationRequiresExactlyOneWaitingSubscription() {
        ProcessInstance active = mock(ProcessInstance.class);
        when(active.getId()).thenReturn("process-instance-01");
        when(active.getProcessDefinitionKey())
                .thenReturn("change_process");
        ProcessInstanceQuery processQuery =
                mock(ProcessInstanceQuery.class, RETURNS_SELF);
        when(runtimeService.createProcessInstanceQuery())
                .thenReturn(processQuery);
        when(processQuery.singleResult()).thenReturn(active);
        EventSubscriptionQuery subscriptionQuery =
                mock(EventSubscriptionQuery.class, RETURNS_SELF);
        when(runtimeService.createEventSubscriptionQuery())
                .thenReturn(subscriptionQuery);
        when(subscriptionQuery.listPage(0, 2))
                .thenReturn(List.of(
                        mock(EventSubscription.class),
                        mock(EventSubscription.class)));

        assertThrows(
                OpenProcessStateConflictException.class,
                () -> adapter.correlate(
                        new OpenMessageCorrelationCommand(
                                "process-instance-01",
                                "continue",
                                Map.of(),
                                actor())));
    }

    private ProcessDefinitionConfig definition() {
        ProcessDefinitionConfig definition =
                new ProcessDefinitionConfig();
        definition.setId("process-config-01");
        definition.setProcessKey("change_process");
        definition.setProcessName("Change process");
        definition.setDescription("Published change process");
        definition.setVersion(2);
        definition.setStatus(
                ProcessDefinitionConfig.ProcessStatus.PUBLISHED);
        return definition;
    }

    private ProcessDefinition deployedDefinition(
            String id,
            int version) {
        ProcessDefinitionQuery query = mock(
                ProcessDefinitionQuery.class, RETURNS_SELF);
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(repositoryService.createProcessDefinitionQuery())
                .thenReturn(query);
        when(query.singleResult()).thenReturn(definition);
        when(definition.getId()).thenReturn(id);
        when(definition.getVersion()).thenReturn(version);
        return definition;
    }

    private OpenApplicationActor actor() {
        return new OpenApplicationActor(
                "application-01",
                "flow_client_01",
                "trace-01");
    }
}
