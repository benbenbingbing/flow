package com.workflow.process.task.application;

import com.workflow.port.process.task.model.AddSignType;
import com.workflow.port.process.task.model.TaskInterventionModels.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/** 复用页面的任务干预服务，使 Java 调用与现有配置、锁和幂等规则一致。 */
@Component
@RequiredArgsConstructor
public class ProcessTaskInterventionAdapter {
    private final TaskHistoryReturnService history;
    private final TaskAddSignService addSign;

    public List<HistoryReturnTarget> listHistoryReturnTargets(String taskId) {
        return history.targets(taskId).stream().map(target -> new HistoryReturnTarget(target.activityInstanceId(),
                target.nodeId(), target.nodeName(), target.resubmit())).toList();
    }

    public HistoryReturnResult returnToHistory(HistoryReturnCommand command) {
        var form = command.formReference();
        var result = history.execute(command.taskId(), new TaskHistoryReturnService.Request(
                command.meta().requestId(), command.targetActivityInstanceId(), command.comment(),
                form == null ? null : form.formId(), form == null ? null : form.configHash()));
        return new HistoryReturnResult(result.processInstanceId(), result.targetNodeId(), result.requestId());
    }

    public AddSignPreview previewAddSign(String taskId, AddSignType type, List<String> userIds) {
        return addSign.previewForCaller(taskId, userIds, type == null ? null : type.name());
    }
}
