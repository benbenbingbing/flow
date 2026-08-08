package com.workflow.process.instance.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.Result;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.ForbiddenException;
import com.workflow.process.instance.application.ProcessRollbackService;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.process.task.application.TaskActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 流程退回控制器
 * 处理驳回、重新提交等操作
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/process-rollback")
@RequiredArgsConstructor
public class ProcessRollbackController {

    private final ProcessRollbackService rollbackService;
    private final TaskActionService taskActionService;
    private final ProcessInstanceAccessService processInstanceAccessService;

    /**
     * 驳回任务（驳回到发起人）
     *
     * @param taskId 任务ID
     * @param requestBody 请求体 {comment: "驳回原因"}
     * @return 操作结果
     */
    @PostMapping("/reject/{taskId}")
    public Result<Void> rejectTask(
            @PathVariable String taskId,
            @RequestBody Map<String, String> requestBody) {
        String userId = UserContext.getUsername();
        if (userId == null || userId.isEmpty()) {
            throw new ForbiddenException("用户未登录");
        }
        taskActionService.requireTaskAccess(taskId);

        String comment = requestBody != null ? requestBody.get("comment") : null;
        String targetNodeId = requestBody != null ? requestBody.get("targetNodeId") : null;

        return rollbackService.rejectTask(taskId, userId, comment, targetNodeId);
    }

    /**
     * 重新提交流程（发起人在被驳回后使用）
     *
     * @param processInstanceId 流程实例ID
     * @param requestBody 请求体 {formData: {}, comment: "重新提交备注"}
     * @return 操作结果
     */
    @PostMapping("/resubmit/{processInstanceId}")
    public Result<Void> resubmitProcess(
            @PathVariable String processInstanceId,
            @RequestBody Map<String, Object> requestBody) {
        String userId = UserContext.getUsername();
        if (userId == null || userId.isEmpty()) {
            throw new ForbiddenException("用户未登录");
        }
        processInstanceAccessService.requireReadAccess(processInstanceId);

        Map<String, Object> formData = requestBody != null ? 
                (Map<String, Object>) requestBody.get("formData") : null;
        String comment = requestBody != null ? (String) requestBody.get("comment") : null;

        return rollbackService.resubmitProcess(processInstanceId, userId, formData, comment);
    }

    /**
     * 检查流程是否被驳回（发起人使用）
     *
     * @param processInstanceId 流程实例ID
     * @return 驳回信息
     */
    @GetMapping("/rejected-status/{processInstanceId}")
    public Result<Map<String, Object>> checkRejectedStatus(
            @PathVariable String processInstanceId) {
        processInstanceAccessService.requireReadAccess(processInstanceId);
        Map<String, Object> status = rollbackService.checkRejectedStatus(processInstanceId);
        return Result.success(status);
    }
}
