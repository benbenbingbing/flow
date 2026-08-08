package com.workflow.process.task.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.Result;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.TaskAddSignService;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
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
