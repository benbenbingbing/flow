package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 在平台支持的模式下创建加签编排，完成策略为 ALL。
 * @param meta 一次业务意图的标识
 * @param taskId 源任务
 * @param type 加签模式
 * @param userIds 加签人员
 * @param comment 加签说明
 */
public record AddSignCommand(
        CommandMeta meta,
        String taskId,
        AddSignType type,
        List<String> userIds,
        String comment) {
    public AddSignCommand {
        Objects.requireNonNull(meta, "meta");
        taskId = ProcessValues.required(taskId, "taskId");
        Objects.requireNonNull(type, "type");
        userIds = List.copyOf(userIds);
        userIds.forEach(user -> ProcessValues.required(user, "userId"));
        if (userIds.isEmpty()) throw new IllegalArgumentException("人员不能为空");

    }

}
