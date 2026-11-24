package com.workflow.port.process.model.task;

import java.util.*;
import com.workflow.port.process.model.*;

/**
 * 与预览一致的下一审批人选择。
 * @param scopeKey 预览产生的作用域键
 * @param nodes 各节点的选择
 */
public record NextApproverSelection(
        String scopeKey,
        List<NodeApproverSelection> nodes) {
    public NextApproverSelection {
        nodes = nodes == null ? List.of() : List.copyOf(nodes);

    }

}
