package com.workflow.process.coordination.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationService;
import com.workflow.entity.data.application.EntityRelationGraphReadService;
import com.workflow.entity.data.application.model.EntityRelationGraph;
import com.workflow.entity.data.application.model.EntityRelationGraph.Node;
import com.workflow.entity.data.application.model.EntityRelationGraph.RecordRef;
import com.workflow.entity.definition.application.PublishedRelationPathResolver;
import com.workflow.entity.definition.application.model.PublishedRelationPath;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Command;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.Operation;
import com.workflow.process.coordination.application.RelatedProcessCoordinationPlan.ProcessState;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.instance.infrastructure.persistence.mapper.EntityProcessLinkMapper;
import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RelatedProcessCoordinationPlanServiceTest {

    private PublishedRelationPathResolver pathResolver;
    private EntityRelationGraphAuthorizationService authorizationService;
    private EntityRelationGraphReadService graphReadService;
    private EntityProcessLinkMapper linkMapper;
    private RuntimeService runtimeService;
    private HistoryService historyService;
    private RepositoryService repositoryService;
    private ProcessPublishedSnapshotService snapshotService;
    private RelatedProcessCoordinationPlanService service;
    private PublishedRelationPath path;

    @BeforeEach
    void setUp() {
        pathResolver = mock(PublishedRelationPathResolver.class);
        authorizationService = mock(
                EntityRelationGraphAuthorizationService.class);
        graphReadService = mock(EntityRelationGraphReadService.class);
        linkMapper = mock(EntityProcessLinkMapper.class);
        runtimeService = mock(RuntimeService.class, RETURNS_DEEP_STUBS);
        historyService = mock(HistoryService.class, RETURNS_DEEP_STUBS);
        repositoryService = mock(RepositoryService.class);
        snapshotService = mock(ProcessPublishedSnapshotService.class);
        service = new RelatedProcessCoordinationPlanService(
                pathResolver,
                authorizationService,
                graphReadService,
                linkMapper,
                runtimeService,
                historyService,
                repositoryService,
                snapshotService);
        path = new PublishedRelationPath(
                "project",
                "project-history-1",
                "project-schema-1",
                List.of());
        when(pathResolver.validate(path)).thenReturn(path);
        ProcessVersionHistory sourceVersion = version(
                "source-version-1", "project-flow", 7);
        when(snapshotService.getVersionByProcessDefinitionId(
                "project-flow:7:def"))
                .thenReturn(sourceVersion);
        UserContext.setCurrentUser("user-1", "测试用户");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void plansCompletedRelatedProcessFromServerResolvedRecord() {
        RecordRef requirement = new RecordRef("requirement", "req-1");
        when(graphReadService.read(
                eq(path),
                eq(List.of("project-1")),
                any(),
                any()))
                .thenReturn(graph(requirement, false, 1));
        EntityProcessLink link = link(
                "link-1", "requirement", "req-1",
                "req-process-1", "requirement-flow", "ENDED");
        when(linkMapper.selectLatest("requirement", "req-1"))
                .thenReturn(link);
        when(runtimeService.createProcessInstanceQuery()
                .processInstanceId("req-process-1")
                .singleResult()).thenReturn(null);
        HistoricProcessInstance historic = mock(
                HistoricProcessInstance.class);
        when(historic.getProcessDefinitionId())
                .thenReturn("requirement-flow:3:def");
        when(historic.getEndTime()).thenReturn(new Date());
        when(historic.getDeleteReason()).thenReturn(null);
        when(historyService.createHistoricProcessInstanceQuery()
                .processInstanceId("req-process-1")
                .singleResult()).thenReturn(historic);
        ProcessDefinition definition = definition(
                "requirement-flow:3:def", "requirement-flow", "dep-3");
        when(repositoryService.getProcessDefinition(
                "requirement-flow:3:def"))
                .thenReturn(definition);
        when(snapshotService.getVersionByProcessDefinitionId(
                "requirement-flow:3:def"))
                .thenReturn(version(
                        "requirement-version-3",
                        "requirement-flow",
                        3));

        RelatedProcessCoordinationPlan plan = service.plan(
                context(),
                new Command(
                        Operation.ASSERT_RELATED_STATE,
                        path,
                        Set.of(ProcessState.COMPLETED),
                        1,
                        null,
                        null));

        assertThat(plan.terminalCount()).isEqualTo(1);
        assertThat(plan.targets()).singleElement()
                .satisfies(target -> {
                    assertThat(target.record()).isEqualTo(requirement);
                    assertThat(target.state())
                            .isEqualTo(ProcessState.COMPLETED);
                    assertThat(target.processDefinitionId())
                            .isEqualTo("requirement-flow:3:def");
                    assertThat(target.processVersionHistoryId())
                            .isEqualTo("requirement-version-3");
                });
    }

    @Test
    void waitFailsClosedWhileAnyRelatedProcessIsActive() {
        RecordRef requirement = new RecordRef("requirement", "req-1");
        when(graphReadService.read(any(), any(), any(), any()))
                .thenReturn(graph(requirement, false, 1));
        when(linkMapper.selectLatest("requirement", "req-1"))
                .thenReturn(link(
                        "link-1", "requirement", "req-1",
                        "req-process-1", "requirement-flow", "ACTIVE"));
        ProcessInstance active = mock(ProcessInstance.class);
        when(active.getProcessDefinitionId())
                .thenReturn("requirement-flow:3:def");
        when(runtimeService.createProcessInstanceQuery()
                .processInstanceId("req-process-1")
                .singleResult()).thenReturn(active);
        when(runtimeService.getActiveActivityIds("req-process-1"))
                .thenReturn(List.of("approveRequirement"));
        ProcessDefinition activeDefinition = definition(
                "requirement-flow:3:def",
                "requirement-flow",
                "dep-3");
        when(repositoryService.getProcessDefinition(
                "requirement-flow:3:def"))
                .thenReturn(activeDefinition);
        when(snapshotService.getVersionByProcessDefinitionId(
                "requirement-flow:3:def"))
                .thenReturn(version(
                        "requirement-version-3",
                        "requirement-flow",
                        3));

        assertThatThrownBy(() -> service.plan(
                context(),
                new Command(
                        Operation.WAIT_RELATED_PROCESSES,
                        path,
                        Set.of(),
                        1,
                        null,
                        null)))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(error -> assertThat(
                        ((BusinessConflictException) error).getErrorCode())
                        .isEqualTo("RELATED_PROCESSES_PENDING"));
    }

    @Test
    void refusesTruncatedGraphBeforeReadingAnyProcessLink() {
        RecordRef requirement = new RecordRef("requirement", "req-1");
        when(graphReadService.read(any(), any(), any(), any()))
                .thenReturn(graph(requirement, true, 2));

        assertThatThrownBy(() -> service.plan(
                context(),
                new Command(
                        Operation.ASSERT_RELATED_STATE,
                        path,
                        Set.of(ProcessState.COMPLETED),
                        1,
                        null,
                        null)))
                .isInstanceOf(BusinessConflictException.class)
                .satisfies(error -> assertThat(
                        ((BusinessConflictException) error).getErrorCode())
                        .isEqualTo("RELATED_PROCESS_GRAPH_INCOMPLETE"));
    }

    private FlowActionContext context() {
        FlowActionContext context = new FlowActionContext();
        context.setActionId("action-1");
        context.setIdempotencyKey("execution-1");
        context.setProcessVersionId("source-version-1");
        context.setProcessDefinitionId("project-flow:7:def");
        context.setProcessInstanceId("project-process-1");
        context.setEntityCode("project");
        context.setEntityDataId("project-1");
        context.setOperatorId("user-1");
        return context;
    }

    private EntityRelationGraph graph(
            RecordRef terminal,
            boolean truncated,
            long total) {
        return new EntityRelationGraph(
                List.of(new Node(
                        new RecordRef("project", "project-1"), 0),
                        new Node(terminal, 1)),
                List.of(),
                List.of(terminal),
                1,
                truncated,
                truncated ? "page" : null,
                total,
                1,
                100);
    }

    private EntityProcessLink link(
            String id,
            String entityCode,
            String recordId,
            String processInstanceId,
            String processKey,
            String state) {
        EntityProcessLink link = new EntityProcessLink();
        link.setId(id);
        link.setEntityCode(entityCode);
        link.setEntityRecordId(recordId);
        link.setProcessInstanceId(processInstanceId);
        link.setProcessDefinitionKey(processKey);
        link.setState(state);
        return link;
    }

    private ProcessDefinition definition(
            String id,
            String key,
            String deploymentId) {
        ProcessDefinition definition = mock(ProcessDefinition.class);
        when(definition.getId()).thenReturn(id);
        when(definition.getKey()).thenReturn(key);
        when(definition.getDeploymentId()).thenReturn(deploymentId);
        return definition;
    }

    private ProcessVersionHistory version(
            String id,
            String key,
            int value) {
        ProcessVersionHistory version = new ProcessVersionHistory();
        version.setId(id);
        version.setProcessKey(key);
        version.setVersion(value);
        return version;
    }
}
