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
 * 流程发布快照读取服务
 * 负责从流程版本历史中读取发布时固化的节点表单绑定快照，
 * 用于运行时按流程定义或流程Key获取节点对应的表单配置。
 */
@Service
@RequiredArgsConstructor
public class ProcessPublishedSnapshotService {

    /** 流程版本历史 Mapper */
    private final ProcessVersionHistoryMapper versionHistoryMapper;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper;
    /** Flowable 仓库服务，用于查询流程定义 */
    private final RepositoryService repositoryService;

    /**
     * 根据流程Key获取指定节点的表单绑定列表。
     * <p>
     * 取该流程Key的最新发布版本，从快照中过滤出目标节点的表单绑定。
     *
     * @param processKey 流程标识
     * @param nodeId     节点ID
     * @return 节点表单绑定列表（按排序号升序）
     * @throws RuntimeException 当流程未发布时抛出
     */
    @Transactional(readOnly = true)
    public List<ProcessNodeForm> getNodeForms(String processKey, String nodeId) {
        ProcessVersionHistory history = versionHistoryMapper.findLatestByProcessKey(processKey);
        if (history == null) {
            throw new RuntimeException("流程未发布: " + processKey);
        }
        return nodeForms(history, nodeId);
    }

    /**
     * 根据Flowable流程定义ID获取指定节点的表单绑定列表。
     * <p>
     * 通过流程定义ID定位部署ID，再由部署ID查找发布历史快照。
     *
     * @param processDefinitionId Flowable 流程定义ID
     * @param nodeId              节点ID
     * @return 节点表单绑定列表（按排序号升序）
     * @throws IllegalArgumentException 当流程定义ID为空时抛出
     * @throws RuntimeException         当 Flowable 流程定义或发布快照不存在时抛出
     */
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
