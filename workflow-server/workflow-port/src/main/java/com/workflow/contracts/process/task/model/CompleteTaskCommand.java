package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 统一办理普通任务与本地加签，完成语义由已发布动作决定。
 * @param meta 一次业务意图的标识
 * @param taskId 明确的办理任务
 * @param actionCode 已发布配置允许的动作
 * @param submission 本次提交信息
 */
public record CompleteTaskCommand(
        CommandMeta meta,
        String taskId,
        String actionCode,
        TaskSubmission submission) {
    public CompleteTaskCommand {
        Objects.requireNonNull(meta, "meta");
        taskId = ProcessValues.required(taskId, "taskId");
        actionCode = ProcessValues.required(actionCode, "actionCode");
        submission = submission == null ? TaskSubmission.comment(null) : submission;

    }

}
