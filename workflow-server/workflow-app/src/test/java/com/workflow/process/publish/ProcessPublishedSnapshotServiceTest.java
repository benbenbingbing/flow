package com.workflow.process.publish;

import com.workflow.process.publish.application.ProcessPublishedSnapshotService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.form.application.ProcessFormContextValues;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 流程发布快照服务单元测试。
 *
 * <p>被测对象为 {@link ProcessPublishedSnapshotService}，验证按流程 Key 读取最新发布快照、
 * 未发布时报错，以及按部署 ID 精确读取指定版本的节点表单快照。</p>
 */
class ProcessPublishedSnapshotServiceTest {

    /**
     * 按流程 Key 读取最新发布快照中的节点表单。
     *
     * <p>场景：快照含 task-1 与 task-2，查询 task-1，断言仅返回 task-1 的表单信息。</p>
     */
    @Test
    void getNodeFormsReadsLatestPublishedSnapshotByProcessKey() {
        ProcessVersionHistoryMapper mapper = mock(ProcessVersionHistoryMapper.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        ProcessVersionHistory history = new ProcessVersionHistory();
        history.setId("version-1");
        history.setProcessKey("expense_flow");
        history.setNodeFormsSnapshot("""
                [{"nodeId":"task-1","nodeName":"审批","formId":"form-1","isReadonly":1,"sortOrder":0},
                 {"nodeId":"task-2","nodeName":"复核","formId":"form-2","isReadonly":0,"sortOrder":0}]
                """);
        when(mapper.findLatestByProcessKey("expense_flow")).thenReturn(history);

        ProcessPublishedSnapshotService service =
                new ProcessPublishedSnapshotService(
                        mapper,
                        new ObjectMapper(),
                        repositoryService);

        List<ProcessNodeForm> nodeForms = service.getNodeForms("expense_flow", "task-1");

        assertEquals(1, nodeForms.size());
        assertEquals("task-1", nodeForms.get(0).getNodeId());
        assertEquals("form-1", nodeForms.get(0).getFormId());
        assertEquals(1, nodeForms.get(0).getIsReadonly());
    }

    /**
     * 流程未发布时读取节点表单应抛出异常。
     *
     * <p>场景：mapper 无快照记录，断言抛出 RuntimeException 且消息含"流程未发布"。</p>
     */
    @Test
    void getNodeFormsFailsWhenProcessHasNoPublishedSnapshot() {
        ProcessVersionHistoryMapper mapper = mock(ProcessVersionHistoryMapper.class);
        RepositoryService repositoryService = mock(RepositoryService.class);
        ProcessPublishedSnapshotService service =
                new ProcessPublishedSnapshotService(
                        mapper,
                        new ObjectMapper(),
                        repositoryService);

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> service.getNodeForms("expense_flow", "task-1"));

        assertEquals("流程未发布: expense_flow", exception.getMessage());
    }

    /**
     * 按流程定义 ID 读取节点表单应使用精确部署 ID 对应的快照。
     *
     * <p>场景：流程定义 pd-v2 对应部署 deployment-v2，快照含表单发布版本信息，
     * 断言返回的节点表单含 release-7 与版本 7。</p>
     */
    @Test
    void getNodeFormsUsesExactDeploymentSnapshot() {
        ProcessVersionHistoryMapper mapper =
                mock(ProcessVersionHistoryMapper.class);
        RepositoryService repositoryService =
                mock(RepositoryService.class);
        ProcessDefinition processDefinition =
                mock(ProcessDefinition.class);
        when(repositoryService.getProcessDefinition("pd-v2"))
                .thenReturn(processDefinition);
        when(processDefinition.getDeploymentId()).thenReturn("deployment-v2");

        ProcessVersionHistory history = new ProcessVersionHistory();
        history.setProcessKey("expense_flow");
        history.setNodeFormsSnapshot("""
                [{"nodeId":"task-1","formId":"form-1",
                  "formReleaseId":"release-7","formReleaseVersion":7,
                  "isReadonly":1,"sortOrder":0}]
                """);
        when(mapper.findByDeploymentId("deployment-v2"))
                .thenReturn(Optional.of(history));

        ProcessPublishedSnapshotService service =
                new ProcessPublishedSnapshotService(
                        mapper,
                        new ObjectMapper(),
                        repositoryService);

        List<ProcessNodeForm> nodeForms =
                service.getNodeFormsByProcessDefinitionId(
                        "pd-v2",
                        "task-1");
        ProcessVersionHistory resolved =
                service.getVersionByProcessDefinitionId("pd-v2");

        assertEquals(1, nodeForms.size());
        assertEquals("release-7", nodeForms.get(0).getFormReleaseId());
        assertEquals(7, nodeForms.get(0).getFormReleaseVersion());
        assertEquals(history, resolved);
    }

    @Test
    void formNodeCodeFollowsTheTaskDeploymentAndSupportsDefaultForms() {
        var mapper = mock(ProcessVersionHistoryMapper.class);
        var repository = mock(RepositoryService.class);
        var snapshots = new ProcessPublishedSnapshotService(mapper, new ObjectMapper(), repository);
        var oldDefinition = mock(ProcessDefinition.class);
        var newDefinition = mock(ProcessDefinition.class);
        when(repository.getProcessDefinition("pd-v1")).thenReturn(oldDefinition);
        when(repository.getProcessDefinition("pd-v2")).thenReturn(newDefinition);
        when(oldDefinition.getDeploymentId()).thenReturn("deployment-v1");
        when(newDefinition.getDeploymentId()).thenReturn("deployment-v2");
        var oldHistory = new ProcessVersionHistory();
        oldHistory.setBpmnXml(designWithNodeCode("finance_review"));
        var newHistory = new ProcessVersionHistory();
        newHistory.setBpmnXml(designWithNodeCode("final_review"));
        when(mapper.findByDeploymentId("deployment-v1")).thenReturn(Optional.of(oldHistory));
        when(mapper.findByDeploymentId("deployment-v2")).thenReturn(Optional.of(newHistory));

        var task = mock(Task.class);
        when(task.getTaskDefinitionKey()).thenReturn("Activity_1");
        var binding = new ProcessNodeForm();
        binding.setNodeId("Activity_1");
        var oldValues = ProcessFormContextValues.task(
                snapshots.getVersionByProcessDefinitionId("pd-v1"), binding, task, "task-1", null, null);
        assertEquals("Activity_1", oldValues.get("currentProcessNode.key"));
        assertEquals("finance_review", oldValues.get("currentProcessNode.code"));
        var newValues = ProcessFormContextValues.task(
                snapshots.getVersionByProcessDefinitionId("pd-v2"), null, task, "task-2", null, null);
        assertEquals("final_review", newValues.get("currentProcessNode.code"));
        assertEquals("finance_review", ProcessFormContextValues.start(oldHistory, binding).get("currentProcessNode.code"));
        assertFalse(ProcessFormContextValues.start(oldHistory, null).containsKey("currentProcessNode.code"));
        assertFalse(ProcessFormContextValues.start(new ProcessVersionHistory(), binding)
                .containsKey("currentProcessNode.code"));
    }

    private static String designWithNodeCode(String code) {
        return """
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn">
                  <process id="expense_flow"><userTask id="Activity_1"><extensionElements><flowable:properties>
                    <flowable:property name="nodeCode" value="%s"/>
                  </flowable:properties></extensionElements></userTask></process>
                </definitions>
                """.formatted(code);
    }
}
