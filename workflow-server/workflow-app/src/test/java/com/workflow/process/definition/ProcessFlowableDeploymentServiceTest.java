package com.workflow.process.definition;

import com.workflow.process.definition.application.ProcessFlowableDeploymentService;

import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程 Flowable 部署服务单元测试。
 *
 * <p>被测对象为 {@link ProcessFlowableDeploymentService}，验证部署时
 * BPMN 资源名使用流程 Key、部署名使用流程名加版本号。</p>
 */
class ProcessFlowableDeploymentServiceTest {

    /**
     * 部署时应使用流程 Key 作为资源名、流程名加版本号作为部署名。
     *
     * <p>场景：流程 Key 为 expense_flow、名称为费用流程、版本 3，
     * 断言资源名为 expense_flow.bpmn20.xml、部署名为"费用流程 - v3"。</p>
     */
    @Test
    void deployUsesProcessKeyAndVersionedName() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        DeploymentBuilder builder = mock(DeploymentBuilder.class);
        Deployment deployment = mock(Deployment.class);
        when(repositoryService.createDeployment()).thenReturn(builder);
        when(builder.addString("expense_flow.bpmn20.xml", "<xml />")).thenReturn(builder);
        when(builder.name("费用流程 - v3")).thenReturn(builder);
        when(builder.deploy()).thenReturn(deployment);

        ProcessDefinitionConfig config = new ProcessDefinitionConfig();
        config.setProcessKey("expense_flow");
        config.setProcessName("费用流程");

        ProcessFlowableDeploymentService service = new ProcessFlowableDeploymentService(repositoryService);
        Deployment result = service.deploy(config, "<xml />", 3);

        assertSame(deployment, result);
        verify(builder).addString("expense_flow.bpmn20.xml", "<xml />");
        verify(builder).name("费用流程 - v3");
    }

    /** 部署入口必须兜底清除发布后注入器可能带入的异步属性。 */
    @Test
    void deployAlwaysUsesSynchronousBpmnXml() {
        RepositoryService repositoryService = mock(RepositoryService.class);
        DeploymentBuilder builder = mock(DeploymentBuilder.class);
        Deployment deployment = mock(Deployment.class);
        when(repositoryService.createDeployment()).thenReturn(builder);
        when(builder.addString(eq("expense_flow.bpmn20.xml"), anyString()))
                .thenReturn(builder);
        when(builder.name("费用流程 - v1")).thenReturn(builder);
        when(builder.deploy()).thenReturn(deployment);
        ProcessDefinitionConfig config = new ProcessDefinitionConfig();
        config.setProcessKey("expense_flow");
        config.setProcessName("费用流程");
        String input = """
                <bpmn:definitions
                    xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                    xmlns:flowable="http://flowable.org/bpmn">
                  <bpmn:process id="expense_flow">
                    <bpmn:userTask id="review"
                        flowable:async="true"
                        flowable:exclusive="false"
                        flowable:assignee="admin" />
                  </bpmn:process>
                </bpmn:definitions>
                """;

        ProcessFlowableDeploymentService service =
                new ProcessFlowableDeploymentService(repositoryService);
        service.deploy(config, input, 1);

        ArgumentCaptor<String> xmlCaptor =
                ArgumentCaptor.forClass(String.class);
        verify(builder).addString(
                eq("expense_flow.bpmn20.xml"),
                xmlCaptor.capture());
        assertFalse(xmlCaptor.getValue().contains("flowable:async="));
        assertFalse(xmlCaptor.getValue().contains("flowable:exclusive="));
        assertTrue(xmlCaptor.getValue().contains(
                "flowable:assignee=\"admin\""));
    }
}
