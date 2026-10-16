package com.workflow.contracts.process.query.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 合并节点策略和运行状态后的操作集合。
 * @param actions 各操作可用性
 * @param addSignTypes 当前允许的加签类型
 * @param returnNodeIds 当前允许的退回目标
 */
public record TaskCapabilities(
        Map<String, ActionCapability> actions,
        List<String> addSignTypes,
        List<String> returnNodeIds) {
    public TaskCapabilities {
        actions = Map.copyOf(actions);
        addSignTypes = List.copyOf(addSignTypes);
        returnNodeIds = List.copyOf(returnNodeIds);

    }

}
