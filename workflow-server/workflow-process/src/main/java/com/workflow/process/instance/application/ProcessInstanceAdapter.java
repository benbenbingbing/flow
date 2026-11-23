package com.workflow.process.instance.application;

import com.workflow.port.process.ProcessInstancePort;
import com.workflow.port.process.instance.model.*;
import com.workflow.port.process.instance.model.ProcessRuntimeViews.InstanceCapabilities;
import com.workflow.port.process.instance.model.ProcessRuntimeViews.Progress;
import com.workflow.port.process.model.ProcessCommandReceipt;
import com.workflow.port.process.model.ProcessPage;
import com.workflow.port.process.query.model.*;
import com.workflow.port.process.query.model.ActionCapability;
import com.workflow.process.query.application.ProcessQueryAdapter;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** 聚合流程实例入口，写入与预检调用各自的授权和事务实现。 */
@Component
@RequiredArgsConstructor
public class ProcessInstanceAdapter implements ProcessInstancePort {
    private final ProcessQueryAdapter queries;
    private final ProcessInstanceInspectionAdapter inspection;
    private final ProcessInstanceCommandAdapter commands;

    @Override
    public Optional<ProcessInstanceView> findInstance(String processInstanceId) {
        return queries.findInstance(processInstanceId);
    }

    @Override
    public ProcessInstanceView requireInstance(String processInstanceId) {
        return queries.requireInstance(processInstanceId);
    }

    @Override
    public ProcessPage<ProcessInstanceView> pageInstances(ProcessInstanceQuery query) {
        return queries.pageInstances(query);
    }

    @Override
    public List<ProcessRoundView> listRounds(String anchorProcessInstanceId) {
        return queries.listRounds(anchorProcessInstanceId);
    }

    @Override
    public List<ProcessRoundView> listRoundsByRecord(String entityCode, String entityRecordId) {
        return queries.listRoundsByRecord(entityCode, entityRecordId);
    }

    @Override
    public List<ProcessActivityView> listActiveActivities(String processInstanceId) {
        return queries.listActiveActivities(processInstanceId);
    }

    @Override
    public Progress getProgress(String processInstanceId) {
        return inspection.getProgress(processInstanceId);
    }

    @Override
    public InstanceCapabilities getInstanceCapabilities(String processInstanceId) {
        return inspection.getInstanceCapabilities(processInstanceId);
    }

    @Override
    public ActionCapability previewReceive(String processInstanceId, String executionId, String activityId, String messageRef) {
        return inspection.previewReceive(processInstanceId, executionId, activityId, messageRef);
    }

    @Override
    public ProcessCommandReceipt withdraw(WithdrawProcessCommand command) {
        return commands.withdraw(command);
    }

    @Override
    public ProcessCommandReceipt terminate(TerminateProcessCommand command) {
        return commands.terminate(command);
    }

    @Override
    public ReceiveTaskTriggerResult triggerReceiveTask(TriggerReceiveTaskCommand command) {
        return commands.triggerReceiveTask(command);
    }
}
