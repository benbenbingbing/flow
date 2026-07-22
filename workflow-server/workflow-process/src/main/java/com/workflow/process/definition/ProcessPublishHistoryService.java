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
 * 流程发布历史。
 */
@Service
@RequiredArgsConstructor
public class ProcessPublishHistoryService {

    private final ProcessVersionHistoryMapper versionHistoryMapper;
    private final FlowActionDesignPort flowActionDesignPort;
    private final ProcessNodeFormMapper nodeFormMapper;
    private final UiConfigReleaseService uiConfigReleaseService;
    private final ObjectMapper objectMapper;

    public int nextVersion(String processConfigId) {
        Integer maxVersion = versionHistoryMapper.findMaxVersionByProcessConfigId(processConfigId);
        return (maxVersion == null ? 0 : maxVersion) + 1;
    }

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
