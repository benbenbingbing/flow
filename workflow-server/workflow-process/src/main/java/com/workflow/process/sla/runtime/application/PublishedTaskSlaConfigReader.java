package com.workflow.process.sla.runtime.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.engine.RepositoryService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class PublishedTaskSlaConfigReader {

    private final RepositoryService repositoryService;
    private final ObjectMapper objectMapper;

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
