package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 按节点规则退回，不能用于任意引擎跳转。
 * @param meta 一次业务意图的标识
 * @param taskId 当前任务
 * @param targetNodeId 服务端允许退回的节点
 * @param submission 退回提交信息
 */
public record ReturnTaskCommand(
        CommandMeta meta,
        String taskId,
        String targetNodeId,
        TaskSubmission submission) {
    public ReturnTaskCommand {
        Objects.requireNonNull(meta, "meta");
        taskId = ProcessValues.required(taskId, "taskId");
        targetNodeId = ProcessValues.required(targetNodeId, "targetNodeId");
        submission = submission == null ? TaskSubmission.comment(null) : submission;

    }

}
