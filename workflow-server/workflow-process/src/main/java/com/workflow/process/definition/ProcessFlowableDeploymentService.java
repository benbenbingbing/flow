package com.workflow.process.definition;

import com.workflow.entity.ProcessDefinitionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;

/**
 * Flowable 流程部署服务
 * 负责将流程定义配置和 BPMN XML 部署到 Flowable 引擎，返回部署对象。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessFlowableDeploymentService {

    /** Flowable 仓库服务，用于部署流程定义 */
    private final RepositoryService repositoryService;

    /**
     * 部署流程定义到 Flowable 引擎。
     *
     * @param config  流程定义配置，提供流程Key与名称
     * @param bpmnXml BPMN 2.0 XML 内容
     * @param version 版本号，用于命名部署
     * @return Flowable 部署对象
     */
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
