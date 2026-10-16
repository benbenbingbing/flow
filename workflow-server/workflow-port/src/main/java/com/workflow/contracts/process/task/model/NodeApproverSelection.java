package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 指定一个下一审批节点的人工候选选择，执行时重新校验。
 * @param nodeId 目标节点 ID
 * @param userKeys 用户身份键
 */
public record NodeApproverSelection(
        String nodeId,
        List<String> userKeys) {
    public NodeApproverSelection {
        nodeId = ProcessValues.required(nodeId, "nodeId");
        userKeys = List.copyOf(userKeys);

    }

}
