package com.workflow.contracts.process.query.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 下一审批节点预览输入；字段值仅用于预览，不在查询时落库。
 * @param taskId 当前任务
 * @param actionCode 本次审批动作
 * @param actionLabel 动作显示文本
 * @param comment 审批意见
 * @param formData 提交字段快照
 */
public record NextApprovalQuery(
        String taskId,
        String actionCode,
        String actionLabel,
        String comment,
        Map<String,Object> formData) {
    public NextApprovalQuery {
        taskId = ProcessValues.required(taskId, "taskId");
        formData = ProcessValues.snapshot(formData);

    }

}
