package com.workflow.process.definition.application;

import com.workflow.process.definition.infrastructure.persistence.record.ProcessDefinitionConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.springframework.stereotype.Service;

/**
 * Flowable 流程部署服务。
 * 负责将流程定义配置和 BPMN XML 强制归一化为同步执行后部署到 Flowable 引擎。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessFlowableDeploymentService {

    /** Flowable 仓库服务，用于部署流程定义 */
    private final RepositoryService repositoryService;

    /**
     * 将流程定义按同步执行方式部署到 Flowable 引擎。
     *
     * @param config  流程定义配置，提供流程Key与名称
     * @param bpmnXml BPMN 2.0 XML 内容；其中的节点异步属性会被忽略并清除
     * @param version 版本号，用于命名部署
     * @return Flowable 部署对象
     */
    public Deployment deploy(ProcessDefinitionConfig config, String bpmnXml, int version) {
        // 部署入口保留最终兜底，防止发布期的后续注入器重新带入异步执行属性。
        String synchronousBpmnXml =
                ProcessBpmnSynchronousExecutionNormalizer.normalize(
                        bpmnXml);
        Deployment deployment = repositoryService.createDeployment()
                .addString(
                        config.getProcessKey() + ".bpmn20.xml",
                        synchronousBpmnXml)
                .name(config.getProcessName() + " - v" + version)
                .deploy();
        log.info("流程已部署: processKey={}, version={}, deploymentId={}",
                config.getProcessKey(), version, deployment.getId());
        return deployment;
    }
}
