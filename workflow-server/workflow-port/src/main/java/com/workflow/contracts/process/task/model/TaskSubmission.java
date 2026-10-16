package com.workflow.contracts.process.task.model;

import java.util.*;
import com.workflow.contracts.process.model.*;

/**
 * 节点提交信息；服务端以真实任务解析发布表单和关联业务记录。
 * @param comment 审批意见
 * @param actionLabel 动作显示文本，不授予审批权
 * @param formData 本次节点表单字段补丁
 * @param formReference 可选的调用方表单版本
 * @param nextApprovers 可选的下一审批人选择
 */
public record TaskSubmission(
        String comment,
        String actionLabel,
        Map<String,Object> formData,
        TaskFormReference formReference,
        NextApproverSelection nextApprovers) {
    public TaskSubmission {
        formData = ProcessValues.snapshot(formData);

    }
    /** 无字段修改的提交；表单和权限校验仍由服务端执行。 */
    public static TaskSubmission comment(String comment) { return new TaskSubmission(comment, null, Map.of(), null, null); }

}
