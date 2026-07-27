package com.workflow.service;

import com.workflow.process.definition.application.ProcessDefinitionService;

import com.workflow.contracts.action.FlowActionDesignPort;
import com.workflow.contracts.migration.MigrationAssetHandler;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessDefinitionConfigMapper;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.definition.application.ProcessBpmnPublishSanitizer;
import com.workflow.process.definition.application.ProcessDefinitionNodeSyncService;
import com.workflow.process.definition.application.ProcessFlowableDeploymentService;
import com.workflow.process.definition.application.ProcessPublishHistoryService;
import org.flowable.engine.repository.Deployment;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 流程发布设计快照与运行快照分离测试。
 */
class ProcessDefinitionServicePublishTest {

    @Test
    void publishDeploysRuntimeXmlButStoresDesignXmlForVersionRollback() {
        ProcessDefinitionConfigMapper processMapper =
                mock(ProcessDefinitionConfigMapper.class);
        ProcessVersionHistoryMapper versionHistoryMapper =
                mock(ProcessVersionHistoryMapper.class);
        ProcessFlowableDeploymentService deploymentService =
                mock(ProcessFlowableDeploymentService.class);
        ProcessPublishHistoryService historyService =
                mock(ProcessPublishHistoryService.class);
        ProcessDefinitionNodeSyncService nodeSyncService =
                mock(ProcessDefinitionNodeSyncService.class);
        ProcessBpmnPublishSanitizer sanitizer =
                mock(ProcessBpmnPublishSanitizer.class);
        FlowActionDesignPort actionDesignPort =
                mock(FlowActionDesignPort.class);
        MigrationAssetHandler migrationAssetHandler =
                mock(MigrationAssetHandler.class);
        ProcessDefinitionService service = new ProcessDefinitionService(
                processMapper,
                versionHistoryMapper,
                deploymentService,
                historyService,
                nodeSyncService,
                sanitizer,
                actionDesignPort,
                migrationAssetHandler);

        String designXml = "<bpmn:scriptTask id=\"script\" />";
        String sanitizedXml = "<bpmn:serviceTask id=\"script\" />";
        String deployedXml = sanitizedXml + "<!-- actions -->";
        ProcessDefinitionConfig config = new ProcessDefinitionConfig();
        config.setId("process-1");
        config.setProcessKey("expense_flow");
        config.setProcessName("费用流程");
        config.setBpmnXml(designXml);
        when(processMapper.selectById("process-1")).thenReturn(config);
        when(historyService.nextVersion("process-1")).thenReturn(2);
        when(sanitizer.sanitize(designXml, "expense_flow"))
                .thenReturn(sanitizedXml);
        when(actionDesignPort.prepareBpmnForPublish(
                "process-1",
                sanitizedXml)).thenReturn(deployedXml);
        ProcessPublishHistoryService.PublishedNodeForms nodeForms =
                new ProcessPublishHistoryService.PublishedNodeForms(
                        "[]",
                        List.of());
        when(historyService.prepareNodeFormsSnapshot("process-1"))
                .thenReturn(nodeForms);
        Deployment deployment = mock(Deployment.class);
        when(deployment.getId()).thenReturn("deployment-2");
        when(deploymentService.deploy(config, deployedXml, 2))
                .thenReturn(deployment);
        ProcessVersionHistory history = new ProcessVersionHistory();
        history.setId("version-2");
        when(historyService.recordPublish(
                config,
                designXml,
                "deployment-2",
                2,
                "脚本节点版本",
                nodeForms)).thenReturn(history);

        service.publish("process-1", "脚本节点版本");

        verify(deploymentService).deploy(config, deployedXml, 2);
        verify(historyService).recordPublish(
                config,
                designXml,
                "deployment-2",
                2,
                "脚本节点版本",
                nodeForms);
        verify(migrationAssetHandler).recordProcess(
                eq("process-1"),
                eq("version-2"),
                org.mockito.ArgumentMatchers.any());
    }
}
