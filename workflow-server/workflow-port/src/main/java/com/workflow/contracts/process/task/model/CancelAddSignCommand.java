package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 撤销指定加签编排并按原规则恢复源任务。
 * @param meta 一次业务意图的标识
 * @param addSignId 加签编排 ID
 * @param reason 取消说明
 */
public record CancelAddSignCommand(
        CommandMeta meta,
        String addSignId,
        String reason) {
    public CancelAddSignCommand {
        Objects.requireNonNull(meta, "meta");
        addSignId = ProcessValues.required(addSignId, "addSignId");

    }

}
