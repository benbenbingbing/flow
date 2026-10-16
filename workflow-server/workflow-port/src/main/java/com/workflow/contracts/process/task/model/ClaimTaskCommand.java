package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 认领当前身份可办理的任务。
 * @param meta 一次业务意图的标识
 * @param taskId 待认领任务
 */
public record ClaimTaskCommand(
        CommandMeta meta,
        String taskId) {
    public ClaimTaskCommand {
        Objects.requireNonNull(meta, "meta");
        taskId = ProcessValues.required(taskId, "taskId");

    }

}
