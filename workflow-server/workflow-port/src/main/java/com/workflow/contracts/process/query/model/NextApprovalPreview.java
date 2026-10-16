package com.workflow.contracts.process.query.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 提交前的下一审批节点预览，执行时必须重验作用域。
 * @param taskId 触发任务
 * @param engineDefinitionId 精确部署定义
 * @param status 预览状态
 * @param scopeKey 提交时回传的预览作用域
 * @param nodes 命中的后续节点
 * @param message 延期或阻断说明
 */
public record NextApprovalPreview(
        String taskId,
        String engineDefinitionId,
        ApprovalPreviewStatus status,
        String scopeKey,
        List<NextApprovalNode> nodes,
        String message) {
    public NextApprovalPreview {
        nodes = List.copyOf(nodes);

    }

}
