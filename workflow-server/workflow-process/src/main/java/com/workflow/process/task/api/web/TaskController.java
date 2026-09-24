package com.workflow.process.task.api.web;

import com.workflow.core.security.AuthenticatedApi;

import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.admin.security.context.UserContext;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.TaskAddSignService;
import com.workflow.process.task.application.TaskService;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.entity.form.api.request.FormActionResolveRequest;
import com.workflow.entity.form.application.EntityFormActionService;
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
    private final EntityFormActionService formActionService;

    /**
     * 获取待办任务统计
     *
     * @return 符合条件的{@code result<task}{@code statistics}{@code vo>}结果，供调用方继续处理
     */
    @GetMapping("/statistics")
    public Result<TaskStatisticsVO> getStatistics() {
        return Result.success(taskService.getStatistics());
    }

    /**
     * 获取待办任务列表
     *
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param processName 流程名称，后续用于读取待办列表时匹配或展示
     * @param taskName 任务名称，后续用于读取待办列表时匹配或展示
     * @param timeRange 时间范围，作为 {@code Result.success} 的输入影响后续处理
     * @return 符合条件的任务结果，供调用方继续处理
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
     * 完成任务审批
     *
     * @param params 参数，作为 {@code requireSubmitApprovalAction} 的输入影响后续处理
     * @return 处理后的完成任务结果，供调用方继续处理
     */
    @PostMapping("/complete")
    public Result<Void> completeTask(@RequestBody Map<String, Object> params) {
        String taskId = (String) params.get("taskId");
        String action = (String) params.get("action");
        String comment = (String) params.get("comment");
        String transferTo = (String) params.get("transferTo");
        String actionLabel = (String) params.get("actionLabel");
        
        String currentUser = UserContext.getUsername();
        requireSubmitApprovalAction(params, taskId);
        if (taskAddSignService.isAddSignTask(taskId)) {
            taskAddSignService.completeAddSignTask(taskId, action, comment);
        } else {
            if (!taskAddSignService.handleSourceCompletion(
                    taskId, currentUser, action, comment, actionLabel, null)) {
                taskActionService.completeTask(
                        taskId, currentUser, action, comment,
                        transferTo, actionLabel);
            }
        }
        return Result.success();
    }

    /**
     * 旧任务入口同样必须携带并校验活动任务表单发布上下文。
     *
     * @param params 参数，作为 {@code approvalRequest} 的输入影响后续处理
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     */
    private void requireSubmitApprovalAction(
            Map<String, Object> params,
            String taskId) {
        FormActionResolveRequest request = approvalRequest(params, taskId);
        formActionService.requireBuiltInMutationAction(
                request, "submitApproval");
    }

    /**
     * 处理审批请求，并将结果传给后续步骤。
     *
     * @param params 参数，作为 {@code request.setFormId} 的输入影响后续处理
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 处理后的审批请求结果，供调用方继续处理
     */
    private FormActionResolveRequest approvalRequest(
            Map<String, Object> params,
            String taskId) {
        FormActionResolveRequest request = new FormActionResolveRequest();
        request.setFormId(text(params.get("formId")));
        request.setReleaseId(text(params.get("formReleaseId")));
        request.setReleaseVersion(integer(params.get(
                "formReleaseVersion")));
        request.setReleaseResolutionToken(text(params.get(
                "formReleaseResolutionToken")));
        request.setEntityCode(text(params.get("entityCode")));
        request.setListKey(text(params.get("listKey")));
        request.setMode("approve");
        request.setRecordId(text(params.get("recordId")));
        request.setTaskId(taskId);
        return request;
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
     * 获取任务详情
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 符合条件的{@code result<task}{@code vo>}结果，供调用方继续处理
     */
    @GetMapping("/{taskId}")
    public Result<TaskVO> getTaskDetail(@PathVariable String taskId) {
        taskActionService.requireTaskAccess(taskId);
        return Result.success(taskService.getTaskDetail(taskId));
    }

    /**
     * 撤回流程
     * 发起人可在流程发起后、第一个审批人审批前撤回
     *
     * @param params 参数，供本方法处理{@code withdraw}流程时使用
     * @return 处理后的{@code withdraw}流程结果，供调用方继续处理
     */
    @PostMapping("/withdraw")
    public Result<Void> withdrawProcess(@RequestBody Map<String, String> params) {
        String processInstanceId = params.get("processInstanceId");
        String reason = params.get("reason");
        String currentUser = UserContext.getUserId();
        if (currentUser == null || currentUser.isBlank()) {
            currentUser = UserContext.getUsername();
        }
        taskActionService.withdrawProcess(
                processInstanceId, currentUser, reason);
        return Result.success();
    }

    /**
     * 获取流程审批历史
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 符合条件的任务结果，供调用方继续处理
     */
    @GetMapping("/history/{processInstanceId}")
    public Result<List<TaskVO>> getProcessHistory(@PathVariable String processInstanceId) {
        processInstanceAccessService.requireReadAccess(processInstanceId);
        return Result.success(taskService.getProcessHistory(processInstanceId));
    }

    /**
     * 驳回到指定节点后重新提交
     *
     * @param params 参数，供本方法处理{@code resubmit}任务时使用
     * @return 处理后的{@code resubmit}任务结果，供调用方继续处理
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
