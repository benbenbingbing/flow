package com.workflow.process.task.application.nextapproval.model;

import com.workflow.process.task.api.response.NextApprovalPreviewStatus;
import org.flowable.task.api.Task;

import java.util.List;
import java.util.Map;

/**
 * 下一审批节点路由的内部强类型结果。
 *
 * @param task 任务，保存在对象中供后续校验、查询或展示
 * @param status 状态标识，决定后续下一步审批解析采用的处理分支
 * @param message 消息，保存在对象中供后续校验、查询或展示
 * @param scopeKey 作用域键，后续用于授权校验、关联或幂等去重
 * @param targets 目标集合，保存在对象中供后续校验、查询或展示
 * @param variables 流程变量，后续传给流程引擎或规则求值器使用
 */
public record NextApprovalResolution(
        Task task,
        NextApprovalPreviewStatus status,
        String message,
        String scopeKey,
        List<NextApprovalTarget> targets,
        Map<String, Object> variables) {

    /**
     * 判断就绪条件是否成立，供调用方选择后续分支。
     *
     * @return 就绪条件成立时为 true，否则为 false
     */
    public boolean ready() {
        return status == NextApprovalPreviewStatus.READY;
    }
}
