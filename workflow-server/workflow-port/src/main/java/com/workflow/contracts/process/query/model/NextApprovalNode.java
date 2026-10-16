package com.workflow.contracts.process.query.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 下一审批节点的候选和人工选择约束。
 * @param nodeId BPMN 目标节点
 * @param nodeName 显示名称
 * @param visible 是否对本次提交可见
 * @param editable 是否可人工选择
 * @param assignmentMode 节点分配策略
 * @param multiple 是否允许多人
 * @param sourceType 候选来源
 * @param assignees 已解析的办理人
 */
public record NextApprovalNode(
        String nodeId,
        String nodeName,
        boolean visible,
        boolean editable,
        String assignmentMode,
        boolean multiple,
        String sourceType,
        List<ApproverOption> assignees) {
    public NextApprovalNode {
        assignees = List.copyOf(assignees);

    }

}
