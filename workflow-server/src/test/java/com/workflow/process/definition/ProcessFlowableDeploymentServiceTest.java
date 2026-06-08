package com.workflow.process.definition;

import com.workflow.entity.ProcessDefinitionConfig;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessFlowableDeploymentServiceTest {

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
}
