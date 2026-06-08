package com.workflow.process.definition;

import com.workflow.entity.ProcessDefinitionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;

/**
 * Flowable 流程部署。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessFlowableDeploymentService {

    private final RepositoryService repositoryService;

    public Deployment deploy(ProcessDefinitionConfig config, String bpmnXml, int version) {
        Deployment deployment = repositoryService.createDeployment()
                .addString(config.getProcessKey() + ".bpmn20.xml", bpmnXml)
                .name(config.getProcessName() + " - v" + version)
                .deploy();
        log.info("流程已部署: processKey={}, version={}, deploymentId={}",
                config.getProcessKey(), version, deployment.getId());
        return deployment;
    }
}
