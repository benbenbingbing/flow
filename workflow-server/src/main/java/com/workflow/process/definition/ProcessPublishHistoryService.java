package com.workflow.process.definition;

import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.entity.ProcessVersionHistory;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import com.workflow.service.FlowActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 流程发布历史。
 */
@Service
@RequiredArgsConstructor
public class ProcessPublishHistoryService {

    private final ProcessVersionHistoryMapper versionHistoryMapper;
    private final FlowActionService flowActionService;

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
        versionHistory.setPublishedAt(LocalDateTime.now());
        versionHistory.setDeploymentId(deploymentId);
        versionHistory.setStatus(ProcessVersionHistory.Status.ACTIVE.name());
        versionHistoryMapper.insert(versionHistory);

        flowActionService.publishActions(config.getId(), versionHistory.getId());
        return versionHistory;
    }
}
