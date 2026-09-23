package com.workflow.process.publish.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
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
        return getNodeFormsContext(processKey, nodeId).nodeForms();
    }

    /**
     * 根据流程Key获取节点表单及其发布历史上下文。
     *
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param nodeId 节点ID，后续用于读取节点表单集合上下文时定位或关联目标
     * @return 符合条件的已发布节点表单集合结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public PublishedNodeForms getNodeFormsContext(
            String processKey,
            String nodeId) {
        ProcessVersionHistory history = versionHistoryMapper.findLatestByProcessKey(processKey);
        if (history == null) {
            throw new RuntimeException("流程未发布: " + processKey);
        }
        return new PublishedNodeForms(
                history,
                nodeForms(history, nodeId));
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
        return getNodeFormsContextByProcessDefinitionId(
                processDefinitionId,
                nodeId).nodeForms();
    }

    /**
     * 根据 Flowable 流程定义ID获取节点表单及其发布历史上下文。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @param nodeId 节点ID，后续用于读取节点表单集合上下文流程定义ID时定位或关联目标
     * @return 符合条件的已发布节点表单集合结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public PublishedNodeForms getNodeFormsContextByProcessDefinitionId(
            String processDefinitionId,
            String nodeId) {
        ProcessVersionHistory history = getVersionByProcessDefinitionId(
                processDefinitionId);
        return new PublishedNodeForms(
                history,
                nodeForms(history, nodeId));
    }

    /**
     * 根据 Flowable 流程定义 ID 获取其部署 ID 对应的不可变发布版本。
     *
     * @param processDefinitionId 流程定义 ID，用于读取对应的已发布流程配置
     * @return 符合条件的流程版本历史结果，供调用方继续处理
     */
    @Transactional(readOnly = true)
    public ProcessVersionHistory getVersionByProcessDefinitionId(
            String processDefinitionId) {
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
        return history;
    }

    /**
     * 整理节点表单集合数据，供调用方遍历或继续处理。
     *
     * @param history 历史，作为 {@code parseNodeForms} 的输入影响后续处理
     * @param nodeId 节点ID，后续用于处理节点表单集合时定位或关联目标
     * @return 流程节点表单集合，供调用方遍历或展示
     */
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

    /**
     * 解析节点表单集合；输出作为后续校验或处理的输入。
     *
     * @param history 历史，作为 {@code RuntimeException} 的输入影响后续处理
     * @return 流程节点表单集合，供调用方遍历或展示
     */
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

    /**
     * 节点表单快照及其服务端可信流程发布上下文。
     *
     * @param history 历史，保存在对象中供后续校验、查询或展示
     * @param nodeForms 节点表单集合，保存在对象中供后续校验、查询或展示
     */
    public record PublishedNodeForms(
            ProcessVersionHistory history,
            List<ProcessNodeForm> nodeForms) {
    }
}
