package com.workflow.process.definition;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.action.FlowActionDesignPort;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.entity.UiConfigRelease;
import com.workflow.mapper.ProcessNodeFormMapper;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import com.workflow.service.UiConfigReleaseService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程发布历史服务单元测试。
 *
 * <p>被测对象为 {@link ProcessPublishHistoryService}，验证版本号递增规则、
 * 发布记录创建流程(含节点表单快照写入与动作发布)，以及节点表单缺少活动发布版本时的拒绝逻辑。</p>
 */
class ProcessPublishHistoryServiceTest {

    /**
     * 下一版本号应在当前最大版本基础上递增。
     *
     * <p>场景：最大版本为 2，断言 nextVersion 返回 3。</p>
     */
    @Test
    void nextVersionIncrementsLatestVersion() {
        ProcessVersionHistoryMapper versionHistoryMapper = mock(ProcessVersionHistoryMapper.class);
        FlowActionDesignPort flowActionDesignPort = mock(FlowActionDesignPort.class);
        ProcessNodeFormMapper nodeFormMapper = mock(ProcessNodeFormMapper.class);
        UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);
        when(versionHistoryMapper.findMaxVersionByProcessConfigId("process-1")).thenReturn(2);

        ProcessPublishHistoryService service = new ProcessPublishHistoryService(
                versionHistoryMapper,
                flowActionDesignPort,
                nodeFormMapper,
                releaseService,
                new ObjectMapper());

        assertEquals(3, service.nextVersion("process-1"));
    }

    /**
     * 发布记录应创建为活动状态，并写入节点表单快照与发布动作。
     *
     * <p>场景：配置流程并 mock 节点表单与表单发布版本，调用 recordPublish，
     * 断言历史记录字段(版本、描述、BPMN、快照 JSON、部署 ID、状态)均正确，
     * 且 flowActionDesignPort 发布动作被调用。</p>
     */
    @Test
    void recordPublishCreatesActiveHistoryAndPublishesActions() {
        ProcessVersionHistoryMapper versionHistoryMapper = mock(ProcessVersionHistoryMapper.class);
        FlowActionDesignPort flowActionDesignPort = mock(FlowActionDesignPort.class);
        ProcessNodeFormMapper nodeFormMapper = mock(ProcessNodeFormMapper.class);
        UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);
        doAnswer(invocation -> {
            ProcessVersionHistory history = invocation.getArgument(0);
            history.setId("version-1");
            return 1;
        }).when(versionHistoryMapper).insert(org.mockito.Mockito.any(ProcessVersionHistory.class));
        when(nodeFormMapper.selectByProcessConfigId("process-1")).thenReturn(List.of(nodeForm("task-1", "form-1", 0)));
        UiConfigRelease release = new UiConfigRelease();
        release.setId("form-release-3");
        release.setVersion(3);
        when(releaseService.active(UiConfigReleaseService.FORM, "form-1"))
                .thenReturn(release);

        ProcessDefinitionConfig config = new ProcessDefinitionConfig();
        config.setId("process-1");
        config.setProcessKey("expense_flow");
        config.setProcessName("费用流程");
        ProcessPublishHistoryService service = new ProcessPublishHistoryService(
                versionHistoryMapper,
                flowActionDesignPort,
                nodeFormMapper,
                releaseService,
                new ObjectMapper());

        ProcessVersionHistory result = service.recordPublish(config, "<xml />", "deployment-1", 3, "首版");

        ArgumentCaptor<ProcessVersionHistory> captor = ArgumentCaptor.forClass(ProcessVersionHistory.class);
        verify(versionHistoryMapper).insert(captor.capture());
        ProcessVersionHistory history = captor.getValue();
        assertSame(history, result);
        assertEquals("process-1", history.getProcessConfigId());
        assertEquals("expense_flow", history.getProcessKey());
        assertEquals("费用流程", history.getProcessName());
        assertEquals(3, history.getVersion());
        assertEquals("首版", history.getVersionDescription());
        assertEquals("<xml />", history.getBpmnXml());
        assertEquals("[{\"nodeId\":\"task-1\",\"nodeName\":\"审批\",\"formId\":\"form-1\",\"formReleaseId\":\"form-release-3\",\"formReleaseVersion\":3,\"isReadonly\":0,\"sortOrder\":0}]",
                history.getNodeFormsSnapshot());
        assertEquals("deployment-1", history.getDeploymentId());
        assertEquals(ProcessVersionHistory.Status.ACTIVE.name(), history.getStatus());
        verify(flowActionDesignPort).publishActions("process-1", "version-1");
    }

    /**
     * 无历史记录时下一版本号应从 1 开始。
     *
     * <p>场景：mapper 未返回最大版本，断言 nextVersion 返回 1。</p>
     */
    @Test
    void nextVersionStartsAtOneWhenNoHistoryExists() {
        ProcessVersionHistoryMapper versionHistoryMapper = mock(ProcessVersionHistoryMapper.class);
        FlowActionDesignPort flowActionDesignPort = mock(FlowActionDesignPort.class);
        ProcessNodeFormMapper nodeFormMapper = mock(ProcessNodeFormMapper.class);
        UiConfigReleaseService releaseService = mock(UiConfigReleaseService.class);

        ProcessPublishHistoryService service = new ProcessPublishHistoryService(
                versionHistoryMapper,
                flowActionDesignPort,
                nodeFormMapper,
                releaseService,
                new ObjectMapper());

        assertEquals(1, service.nextVersion("process-1"));
    }

    /**
     * 节点表单缺少活动发布版本时发布应被拒绝并抛出 IllegalStateException。
     *
     * <p>场景：节点表单引用未发布的表单，断言异常消息包含节点 ID 与表单 ID。</p>
     */
    @Test
    void recordPublishRejectsNodeFormWithoutActiveRelease() {
        ProcessVersionHistoryMapper versionHistoryMapper =
                mock(ProcessVersionHistoryMapper.class);
        FlowActionDesignPort flowActionDesignPort =
                mock(FlowActionDesignPort.class);
        ProcessNodeFormMapper nodeFormMapper =
                mock(ProcessNodeFormMapper.class);
        UiConfigReleaseService releaseService =
                mock(UiConfigReleaseService.class);
        when(nodeFormMapper.selectByProcessConfigId("process-1"))
                .thenReturn(List.of(nodeForm("task-1", "form-1", 0)));

        ProcessDefinitionConfig config = new ProcessDefinitionConfig();
        config.setId("process-1");
        config.setProcessKey("expense_flow");
        ProcessPublishHistoryService service =
                new ProcessPublishHistoryService(
                        versionHistoryMapper,
                        flowActionDesignPort,
                        nodeFormMapper,
                        releaseService,
                        new ObjectMapper());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> service.recordPublish(
                        config,
                        "<xml />",
                        "deployment-1",
                        1,
                        null));

        assertEquals(
                "流程节点引用的表单尚未发布: nodeId=task-1, formId=form-1",
                exception.getMessage());
    }

    /**
     * 构造测试用流程节点表单对象。
     *
     * @param nodeId 节点 ID
     * @param formId 表单 ID
     * @param sortOrder 排序序号
     * @return 已填充字段的 ProcessNodeForm 实例
     */
    private static ProcessNodeForm nodeForm(String nodeId, String formId, int sortOrder) {
        ProcessNodeForm nodeForm = new ProcessNodeForm();
        nodeForm.setProcessConfigId("process-1");
        nodeForm.setNodeId(nodeId);
        nodeForm.setNodeName("审批");
        nodeForm.setFormId(formId);
        nodeForm.setIsReadonly(0);
        nodeForm.setSortOrder(sortOrder);
        return nodeForm;
    }
}
