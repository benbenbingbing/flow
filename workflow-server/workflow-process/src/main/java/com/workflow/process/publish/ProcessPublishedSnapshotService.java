package com.workflow.process.publish;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 流程发布快照读取。
 */
@Service
@RequiredArgsConstructor
public class ProcessPublishedSnapshotService {

    private final ProcessVersionHistoryMapper versionHistoryMapper;
    private final ObjectMapper objectMapper;
    private final RepositoryService repositoryService;

    @Transactional(readOnly = true)
    public List<ProcessNodeForm> getNodeForms(String processKey, String nodeId) {
        ProcessVersionHistory history = versionHistoryMapper.findLatestByProcessKey(processKey);
        if (history == null) {
            throw new RuntimeException("流程未发布: " + processKey);
        }
        return nodeForms(history, nodeId);
    }

    @Transactional(readOnly = true)
    public List<ProcessNodeForm> getNodeFormsByProcessDefinitionId(
            String processDefinitionId,
            String nodeId) {
        if (processDefinitionId == null || processDefinitionId.isBlank()) {
            throw new IllegalArgumentException("流程定义ID不能为空");
        }
        ProcessDefinition processDefinition =
                repositoryService.getProcessDefinition(processDefinitionId);
        if (processDefinition == null
                || processDefinition.getDeploymentId() == null) {
            throw new RuntimeException("Flowable流程定义不存在: " + processDefinitionId);
        }
        ProcessVersionHistory history =
                versionHistoryMapper
                        .findByDeploymentId(processDefinition.getDeploymentId())
                        .orElseThrow(() -> new RuntimeException(
                                "流程发布快照不存在: deploymentId="
                                        + processDefinition.getDeploymentId()));
        return nodeForms(history, nodeId);
    }

    private List<ProcessNodeForm> nodeForms(
            ProcessVersionHistory history,
            String nodeId) {
        return parseNodeForms(history).stream()
                .filter(nodeForm -> Objects.equals(nodeId, nodeForm.getNodeId()))
                .sorted(Comparator.comparing(
                        ProcessNodeForm::getSortOrder,
                        Comparator.nullsLast(Integer::compareTo)))
                .toList();
    }

    private List<ProcessNodeForm> parseNodeForms(ProcessVersionHistory history) {
        String snapshot = history.getNodeFormsSnapshot();
        if (snapshot == null || snapshot.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    snapshot,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, ProcessNodeForm.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException("流程节点表单快照解析失败: " + history.getProcessKey(), e);
        }
    }
}
