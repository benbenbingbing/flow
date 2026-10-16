package com.workflow.contracts.process.instance.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 按当前操作人及节点规则结束流程；原因文字不能改变实际结束类型。
 * @param meta 一次业务意图的标识
 * @param processInstanceId 目标实例
 * @param reason 操作说明
 */
public record WithdrawProcessCommand(
        CommandMeta meta,
        String processInstanceId,
        String reason) {
    public WithdrawProcessCommand {
        Objects.requireNonNull(meta, "meta");
        processInstanceId = ProcessValues.required(processInstanceId, "processInstanceId");

    }

}
