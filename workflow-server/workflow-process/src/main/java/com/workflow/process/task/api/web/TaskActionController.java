package com.workflow.process.task.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.Result;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.TaskAddSignService;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.entity.form.api.request.FormActionResolveRequest;
import com.workflow.entity.form.application.EntityFormActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务动作控制器
 * 处理任务完成、流程撤回、历史查询等操作
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/task-action")
@RequiredArgsConstructor
public class TaskActionController {

    private final TaskActionService taskActionService;
    private final TaskAddSignService taskAddSignService;
    private final ProcessInstanceAccessService processInstanceAccessService;
    private final EntityFormActionService formActionService;

    /**
     * 完成任务
     *
     * @param taskId 任务ID
     * @param requestBody 请求体 {action: "approve/reject/transfer", comment: "", transferTo: ""}
     * @return 操作结果
     */
    @PostMapping("/complete/{taskId}")
    public Result<Void> completeTask(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> requestBody) {
        String userId = UserContext.getUsername();
        if (userId == null || userId.isEmpty()) {
            throw new ForbiddenException("用户未登录");
        }

        String action = requestBody != null ? (String) requestBody.get("action") : "approve";
        String comment = requestBody != null ? (String) requestBody.get("comment") : null;
        String transferTo = requestBody != null ? (String) requestBody.get("transferTo") : null;
        String actionLabel = requestBody != null ? (String) requestBody.get("actionLabel") : null;

        requireSubmitApprovalAction(requestBody, taskId);
        if (taskAddSignService.isAddSignTask(taskId)) {
            taskAddSignService.completeAddSignTask(taskId, action, comment);
        } else {
            if (!taskAddSignService.handleSourceCompletion(
                    taskId, userId, action, comment, actionLabel, null)) {
                taskActionService.completeTask(taskId, userId, action, comment, transferTo, actionLabel);
            }
        }
        return Result.success(null);
    }

    /**
     * 路径式兼容入口不能绕过发布表单内置审批按钮。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     */
    private void requireSubmitApprovalAction(
            Map<String, Object> values,
            String taskId) {
        Map<String, Object> source = values == null ? Map.of() : values;
        FormActionResolveRequest request = new FormActionResolveRequest();
        request.setFormId(text(source.get("formId")));
        request.setReleaseId(text(source.get("formReleaseId")));
        request.setReleaseVersion(integer(source.get(
                "formReleaseVersion")));
        request.setReleaseResolutionToken(text(source.get(
                "formReleaseResolutionToken")));
        request.setEntityCode(text(source.get("entityCode")));
        request.setListKey(text(source.get("listKey")));
        request.setMode("approve");
        request.setRecordId(text(source.get("recordId")));
        request.setTaskId(taskId);
        formActionService.requireBuiltInMutationAction(
                request, "submitApproval");
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private Integer integer(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "表单发布版本必须是整数");
        }
    }

    /**
     * 撤回流程
     * 发起人可以在流程未完成前撤回
     *
     * @param processInstanceId 流程实例ID
     * @param requestBody 请求体 {reason: "撤回原因"}
     * @return 操作结果
     */
    @PostMapping("/withdraw/{processInstanceId}")
    public Result<Void> withdrawProcess(
            @PathVariable String processInstanceId,
            @RequestBody(required = false) Map<String, String> requestBody) {
        String userId = UserContext.getUserId();
        if (userId == null || userId.isEmpty()) {
            userId = UserContext.getUsername();
        }
        if (userId == null || userId.isEmpty()) {
            throw new ForbiddenException("用户未登录");
        }

        String reason = requestBody != null ? requestBody.get("reason") : null;

        taskActionService.withdrawProcess(processInstanceId, userId, reason);
        return Result.success(null);
    }

    /**
     * 获取流程历史记录
     *
     * @param processInstanceId 流程实例ID
     * @return 历史任务列表
     */
    @GetMapping("/history/{processInstanceId}")
    public Result<List<Map<String, Object>>> getProcessHistory(
            @PathVariable String processInstanceId) {
        processInstanceAccessService.requireReadAccess(processInstanceId);
        List<?> historyList = taskActionService.getProcessHistory(processInstanceId);
        return Result.success((List<Map<String, Object>>) (Object) historyList);
    }

    /**
     * 获取任务统计信息
     *
     * @return 统计信息
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getTaskStatistics() {
        String userId = UserContext.getUsername();
        if (userId == null || userId.isEmpty()) {
            throw new ForbiddenException("用户未登录");
        }

        Map<String, Object> statistics = taskActionService.getTaskStatistics(userId);
        return Result.success(statistics);
    }
}
