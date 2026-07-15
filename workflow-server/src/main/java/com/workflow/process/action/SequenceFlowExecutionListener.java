package com.workflow.process.action;

import com.workflow.entity.ProcessVersionHistory;
import com.workflow.mapper.ProcessVersionHistoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.bpmn.model.FlowElement;
import org.flowable.bpmn.model.FlowNode;
import org.flowable.bpmn.model.SequenceFlow;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 顺序流执行监听器。
 *
 * <p>Flowable 顺序流被触发时调用，负责找到当前流程版本、补充顺序流源/目标节点信息，并执行对应的流程动作。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SequenceFlowExecutionListener implements ExecutionListener {

    private final FlowActionExecutor flowActionExecutor;
    private final ProcessVersionHistoryMapper versionHistoryMapper;
    private final RepositoryService repositoryService;

    @Override
    public void notify(DelegateExecution execution) {
        String sequenceFlowId = execution.getCurrentActivityId();
        String versionId = resolveVersionId(execution);
        if (!StringUtils.hasText(versionId) || !StringUtils.hasText(sequenceFlowId)) {
            return;
        }

        populateSequenceFlowInfo(execution);
        flowActionExecutor.executeActions(versionId, sequenceFlowId, execution);
    }

    private String resolveVersionId(DelegateExecution execution) {
        String processDefinitionId = execution.getProcessDefinitionId();
        if (!StringUtils.hasText(processDefinitionId)) {
            return null;
        }
        String deploymentId = extractDeploymentId(processDefinitionId);
        if (!StringUtils.hasText(deploymentId)) {
            return null;
        }
        return versionHistoryMapper.findByDeploymentId(deploymentId)
                .map(ProcessVersionHistory::getId)
                .orElse(null);
    }

    private String extractDeploymentId(String processDefinitionId) {
        if (processDefinitionId == null) {
            return null;
        }
        String[] parts = processDefinitionId.split(":");
        return parts.length >= 3 ? parts[parts.length - 1] : null;
    }

    private void populateSequenceFlowInfo(DelegateExecution execution) {
        try {
            String processDefinitionId = execution.getProcessDefinitionId();
            String sequenceFlowId = execution.getCurrentActivityId();
            BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
            if (bpmnModel == null || bpmnModel.getMainProcess() == null) {
                return;
            }
            FlowElement element = bpmnModel.getMainProcess().getFlowElement(sequenceFlowId);
            if (!(element instanceof SequenceFlow sequenceFlow)) {
                return;
            }

            execution.setVariableLocal("_flowActionSourceNodeId_", defaultString(sequenceFlow.getSourceRef()));
            execution.setVariableLocal("_flowActionTargetNodeId_", defaultString(sequenceFlow.getTargetRef()));

            FlowNode sourceNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(sequenceFlow.getSourceRef());
            FlowNode targetNode = (FlowNode) bpmnModel.getMainProcess().getFlowElement(sequenceFlow.getTargetRef());
            if (sourceNode != null) {
                execution.setVariableLocal("_flowActionSourceNodeName_", defaultString(sourceNode.getName()));
            }
            if (targetNode != null) {
                execution.setVariableLocal("_flowActionTargetNodeName_", defaultString(targetNode.getName()));
            }
        } catch (Exception e) {
            log.debug("补充顺序流源/目标节点信息失败: {}", e.getMessage());
        }
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
