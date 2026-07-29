package com.workflow.process.task.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.admin.security.context.UserContext;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.TaskAddSignService;
import com.workflow.process.task.application.TaskService;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.process.workbench.api.response.TaskStatisticsVO;
import com.workflow.process.task.api.response.TaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 任务管理控制器
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskActionService taskActionService;
    private final TaskAddSignService taskAddSignService;
    private final ProcessInstanceAccessService processInstanceAccessService;

    /**
     * 获取待办任务统计
     */
    @GetMapping("/statistics")
    public Result<TaskStatisticsVO> getStatistics() {
        return Result.success(taskService.getStatistics());
    }

    /**
     * 获取待办任务列表
     */
    @GetMapping("/todo")
    public Result<PageResult<TaskVO>> getTodoList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String timeRange) {
        return Result.success(taskService.getTodoList(pageNum, pageSize, processName, taskName, timeRange));
    }

    /**
     * 获取已办任务列表
     */
    @GetMapping("/done")
    public Result<PageResult<TaskVO>> getDoneList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String processName,
            @RequestParam(required = false) String taskName,
            @RequestParam(required = false) String timeRange) {
        return Result.success(taskService.getDoneList(pageNum, pageSize, processName, taskName, timeRange));
    }

    /**
     * 完成任务审批
     */
    @PostMapping("/complete")
    public Result<Void> completeTask(@RequestBody Map<String, Object> params) {
        String taskId = (String) params.get("taskId");
        String action = (String) params.get("action");
        String comment = (String) params.get("comment");
        String transferTo = (String) params.get("transferTo");
        String actionLabel = (String) params.get("actionLabel");
        
        String currentUser = UserContext.getUsername();
        if (taskAddSignService.isAddSignTask(taskId)) {
            taskAddSignService.completeAddSignTask(taskId, action, comment);
        } else if (!taskAddSignService.handleSourceCompletion(
                taskId, currentUser, action, comment, actionLabel, null)) {
            taskActionService.completeTask(
                    taskId, currentUser, action, comment, transferTo, actionLabel);
        }
        return Result.success();
    }

    /**
     * 获取任务详情
     */
    @GetMapping("/{taskId}")
    public Result<TaskVO> getTaskDetail(@PathVariable String taskId) {
        taskActionService.requireTaskAccess(taskId);
        return Result.success(taskService.getTaskDetail(taskId));
    }

    /**
     * 撤回流程
     * 发起人可在流程发起后、第一个审批人审批前撤回
     */
    @PostMapping("/withdraw")
    public Result<Void> withdrawProcess(@RequestBody Map<String, String> params) {
        String processInstanceId = params.get("processInstanceId");
        String reason = params.get("reason");
        taskActionService.withdrawProcess(
                processInstanceId, UserContext.getUsername(), reason);
        return Result.success();
    }

    /**
     * 获取流程审批历史
     */
    @GetMapping("/history/{processInstanceId}")
    public Result<List<TaskVO>> getProcessHistory(@PathVariable String processInstanceId) {
        processInstanceAccessService.requireReadAccess(processInstanceId);
        return Result.success(taskService.getProcessHistory(processInstanceId));
    }

    /**
     * 驳回到指定节点后重新提交
     */
    @PostMapping("/resubmit")
    public Result<Void> resubmitTask(@RequestBody Map<String, Object> params) {
        String taskId = (String) params.get("taskId");
        String comment = (String) params.get("comment");
        Map<String, Object> formData = (Map<String, Object>) params.get("formData");
        taskActionService.completeTask(
                taskId,
                UserContext.getUsername(),
                "resubmit",
                comment,
                null,
                "重新提交",
                formData);
        return Result.success();
    }
}
