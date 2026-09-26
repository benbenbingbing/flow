package com.workflow.process.sla.runtime.application;

import com.workflow.process.sla.runtime.application.model.PublishedTaskSlaConfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** 从已部署 BPMN 节点读取 SLA 发布配置，避免运行时引用可变的流程设计稿。 */
@Component
@RequiredArgsConstructor
public class PublishedTaskSlaConfigReader {

    private final RepositoryService repositoryService;
    private final ObjectMapper objectMapper;

    /**
     * 按流程定义和节点 ID 读取 slaConfig 扩展属性；未配置或已禁用时返回 null，
     * 供任务初始化跳过 SLA，已发布文档损坏时明确失败。
     *
     * @param processDefinitionId 已发布流程定义 ID，用于读取固定版本 BPMN 模型
     * @param nodeId 用户任务节点 ID，用于定位 slaConfig 扩展属性
     * @return 启用的节点 SLA 配置；缺失、禁用或标识为空时为 null
     * @throws IllegalStateException 发布配置 JSON 无法解析
     */
    public PublishedTaskSlaConfig read(
            String processDefinitionId,
            String nodeId) {
        if (!StringUtils.hasText(processDefinitionId)
                || !StringUtils.hasText(nodeId)) {
            return null;
        }
        BpmnModel model = repositoryService.getBpmnModel(processDefinitionId);
        if (model == null || model.getMainProcess() == null) {
            return null;
        }
        FlowElement element =
                model.getMainProcess().getFlowElement(nodeId, true);
        String document =
                ConfiguredTaskPropertyReader.read(element, "slaConfig");
        if (!StringUtils.hasText(document)) {
            return null;
        }
        try {
            PublishedTaskSlaConfig config = objectMapper.readValue(
                    document,
                    PublishedTaskSlaConfig.class);
            return config.enabled() ? config : null;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "已发布用户任务SLA配置无法解析: nodeId=" + nodeId,
                    exception);
        }
    }
}
