package com.workflow.process.definition;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.UserContext;
import com.workflow.contracts.action.FlowActionDesignPort;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.entity.UiConfigRelease;
import com.workflow.mapper.ProcessNodeFormMapper;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import com.workflow.service.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流程发布历史服务
 * 负责生成流程版本号、记录发布历史快照，并同步发布节点表单绑定与操作设计。
 */
@Service
@RequiredArgsConstructor
public class ProcessPublishHistoryService {

    /** 流程版本历史 Mapper */
    private final ProcessVersionHistoryMapper versionHistoryMapper;
    /** 流程操作设计端口，发布动作设计 */
    private final FlowActionDesignPort flowActionDesignPort;
    /** 流程节点表单绑定 Mapper */
    private final ProcessNodeFormMapper nodeFormMapper;
    /** UI 配置发布版本服务，查询表单发布版本 */
    private final UiConfigReleaseService uiConfigReleaseService;
    /** JSON 序列化工具 */
    private final ObjectMapper objectMapper;

    /**
     * 计算流程的下一个版本号。
     * <p>
     * 取当前最大版本号加一，无历史版本时从1开始。
     *
     * @param processConfigId 流程定义配置ID
     * @return 下一个版本号
     */
    public int nextVersion(String processConfigId) {
        Integer maxVersion = versionHistoryMapper.findMaxVersionByProcessConfigId(processConfigId);
        return (maxVersion == null ? 0 : maxVersion) + 1;
    }

    /**
     * 记录一次流程发布历史，并生成节点表单快照。
     * <p>
     * 保存 BPMN XML、表单绑定快照、部署ID、版本等信息，随后同步发布操作设计。
     *
     * @param config            流程定义配置
     * @param bpmnXml           发布的 BPMN XML
     * @param deploymentId       Flowable 部署ID
     * @param version            版本号
     * @param versionDescription 版本描述/发布说明
     * @return 已保存的流程版本历史记录
     * @throws IllegalStateException 当流程节点引用的表单未发布时抛出
     */
    public ProcessVersionHistory recordPublish(ProcessDefinitionConfig config,
                                               String bpmnXml,
                                               String deploymentId,
                                               int version,
                                               String versionDescription) {
        ProcessVersionHistory versionHistory = new ProcessVersionHistory();
        versionHistory.setProcessConfigId(config.getId());
        versionHistory.setProcessKey(config.getProcessKey());
        versionHistory.setProcessName(config.getProcessName());
        versionHistory.setVersion(version);
        versionHistory.setVersionDescription(versionDescription);
        versionHistory.setBpmnXml(bpmnXml);
        versionHistory.setNodeFormsSnapshot(toNodeFormsSnapshot(config.getId()));
        versionHistory.setPublishedAt(LocalDateTime.now());
        versionHistory.setPublishedBy(UserContext.getUsername());
        versionHistory.setDeploymentId(deploymentId);
        versionHistory.setStatus(ProcessVersionHistory.Status.ACTIVE.name());
        versionHistoryMapper.insert(versionHistory);

        flowActionDesignPort.publishActions(config.getId(), versionHistory.getId());
        return versionHistory;
    }

    /**
     * 生成节点表单绑定快照（JSON）。
     * <p>
     * 查询流程下所有节点表单绑定，逐一关联当前生效的表单发布版本，序列化为快照。
     *
     * @param processConfigId 流程定义配置ID
     * @return 节点表单快照 JSON 字符串
     * @throws RuntimeException 当快照序列化失败或表单未发布时抛出
     */
    private String toNodeFormsSnapshot(String processConfigId) {
        List<ProcessNodeForm> nodeForms = nodeFormMapper.selectByProcessConfigId(processConfigId);
        try {
            return objectMapper.writeValueAsString(nodeForms.stream()
                    .map(this::toSnapshot)
                    .toList());
        } catch (JsonProcessingException e) {
            throw new RuntimeException("节点表单快照生成失败: " + processConfigId, e);
        }
    }

    /**
     * 将单个节点表单绑定转换为快照记录，关联当前生效的表单发布版本。
     *
     * @throws IllegalStateException 当表单尚未发布时抛出
     */
    private NodeFormSnapshot toSnapshot(ProcessNodeForm nodeForm) {
        UiConfigRelease release =
                uiConfigReleaseService.active(
                        UiConfigReleaseService.FORM,
                        nodeForm.getFormId());
        if (release == null) {
            throw new IllegalStateException(
                    "流程节点引用的表单尚未发布: nodeId="
                            + nodeForm.getNodeId()
                            + ", formId="
                            + nodeForm.getFormId());
        }
        return new NodeFormSnapshot(
                nodeForm.getNodeId(),
                nodeForm.getNodeName(),
                nodeForm.getFormId(),
                release.getId(),
                release.getVersion(),
                nodeForm.getIsReadonly(),
                nodeForm.getSortOrder());
    }

    private record NodeFormSnapshot(String nodeId,
                                    String nodeName,
                                    String formId,
                                    String formReleaseId,
                                    Integer formReleaseVersion,
                                    Integer isReadonly,
                                    Integer sortOrder) {
    }
}
