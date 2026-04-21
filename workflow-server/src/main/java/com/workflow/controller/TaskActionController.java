package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.common.UserContext;
import com.workflow.service.TaskActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务动作控制器
 * 处理任务完成、流程撤回、历史查询等操作
 */
@RestController
@RequestMapping("/api/task-action")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TaskActionController {

    private final TaskActionService taskActionService;

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
            userId = "admin"; // 默认用户，用于测试
        }

        String action = requestBody != null ? (String) requestBody.get("action") : "approve";
        String comment = requestBody != null ? (String) requestBody.get("comment") : null;
        String transferTo = requestBody != null ? (String) requestBody.get("transferTo") : null;

        try {
            taskActionService.completeTask(taskId, userId, action, comment, transferTo);
            return Result.success(null);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
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
        String userId = UserContext.getUsername();
        if (userId == null || userId.isEmpty()) {
            userId = "admin"; // 默认用户，用于测试
        }

        String reason = requestBody != null ? requestBody.get("reason") : null;

        try {
            taskActionService.withdrawProcess(processInstanceId, userId, reason);
            return Result.success(null);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
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
        try {
            List<?> historyList = taskActionService.getProcessHistory(processInstanceId);
            return Result.success((List<Map<String, Object>>) (Object) historyList);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
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
            userId = "admin"; // 默认用户，用于测试
        }

        try {
            Map<String, Object> statistics = taskActionService.getTaskStatistics(userId);
            return Result.success(statistics);
        } catch (Exception e) {
            return Result.error(500, e.getMessage());
        }
    }
}
