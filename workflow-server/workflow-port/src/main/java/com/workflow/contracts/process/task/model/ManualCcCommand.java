package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 由有权办理的用户按节点策略添加人工知会。
 * @param meta 一次业务意图的标识
 * @param taskId 源任务
 * @param userIds 知会人员
 * @param comment 知会说明
 */
public record ManualCcCommand(
        CommandMeta meta,
        String taskId,
        List<String> userIds,
        String comment) {
    public ManualCcCommand {
        Objects.requireNonNull(meta, "meta");
        taskId = ProcessValues.required(taskId, "taskId");
        userIds = List.copyOf(userIds);
        if (userIds.isEmpty()) throw new IllegalArgumentException("人员不能为空");

    }

}
