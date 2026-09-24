package com.workflow.process.task.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.contracts.embed.runtime.annotation.EmbedDelegatedRuntimeApi;

import com.workflow.core.error.ForbiddenException;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.result.PageResult;
import com.workflow.core.result.Result;
import com.workflow.process.task.api.response.TaskDetailDTO;
import com.workflow.process.task.api.request.NextApprovalPreviewRequest;
import com.workflow.process.task.api.request.NextApproverOptionsRequest;
import com.workflow.process.task.api.request.TaskCompleteRequest;
import com.workflow.process.task.api.response.NextApprovalPreviewResponse;
import com.workflow.process.task.api.response.NextApproverCandidateDTO;
import com.workflow.entity.form.api.request.FormActionResolveRequest;
import com.workflow.entity.form.application.EntityFormActionService;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskDetailService;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.nextapproval.NextApprovalPreviewService;
import com.workflow.process.task.application.nextapproval.NextApproverCandidateService;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.process.task.api.response.TaskVO;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.common.engine.api.FlowableOptimisticLockingException;
import org.flowable.common.engine.api.FlowableTaskAlreadyClaimedException;
import org.flowable.task.api.Task;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 流程待办控制器
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/process-task")
@RequiredArgsConstructor
public class ProcessTaskController {

    private final ProcessTaskService processTaskService;
    private final TaskDetailService taskDetailService;
    private final TaskActionService taskActionService;
    private final ProcessInstanceAccessService processInstanceAccessService;
    private final com.workflow.process.task.application.TaskAddSignService taskAddSignService;
    private final EntityFormActionService formActionService;

    @Autowired
    private NextApprovalPreviewService nextApprovalPreviewService;

    @Autowired
    private NextApproverCandidateService nextApproverCandidateService;

    private final com.workflow.process.task.application.TaskListQueryService taskListQueryService;

    /**
     * 获取用户待办列表（分页，兼容前端TaskVO格式）
     *
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param keyword 关键字，供本方法读取待办列表时使用
     * @param startUserName 启动用户名称，后续用于读取待办列表时匹配或展示
     * @param priority 优先级，供本方法读取待办列表时使用
     * @param startDate 启动日期，后续用于判断有效期或展示该事件的发生时间
     * @param endDate 结束日期，后续用于判断有效期或展示该事件的发生时间
     * @return 符合条件的任务结果，供调用方继续处理
     */
    @GetMapping("/todo")
    public Result<PageResult<TaskVO>> getTodoList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startUserName,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String currentUser = requireCurrentUser(UserContext.getUsername());
        return Result.success(taskListQueryService.findInbox(currentUser, "todo", pageNum, pageSize,
                keyword, startUserName, priority, startDate, endDate));
    }

    /**
     * 获取用户已办列表（分页，兼容前端TaskVO格式）
     *
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param keyword 关键字，供本方法读取{@code done}列表时使用
     * @param startUserName 启动用户名称，后续用于读取{@code done}列表时匹配或展示
     * @param priority 优先级，供本方法读取{@code done}列表时使用
     * @param startDate 启动日期，后续用于判断有效期或展示该事件的发生时间
     * @param endDate 结束日期，后续用于判断有效期或展示该事件的发生时间
     * @return 符合条件的任务结果，供调用方继续处理
     */
    @GetMapping("/done")
    public Result<PageResult<TaskVO>> getDoneList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String startUserName,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        String currentUser = requireCurrentUser(UserContext.getUsername());
        return Result.success(taskListQueryService.findInbox(currentUser, "done", pageNum, pageSize,
                keyword, startUserName, priority, startDate, endDate));
    }

    /**
     * 统计待办数量
     *
     * @return 统计后的待办结果，供调用方继续处理
     */
    @GetMapping("/count/todo")
    public Result<Long> countTodo() {
        String currentUser = UserContext.getUsername();
        currentUser = requireCurrentUser(currentUser);
        return Result.success(processTaskService.countTodo(currentUser));
    }

    /**
     * 统计已办数量
     *
     * @return 统计后的{@code done}结果，供调用方继续处理
     */
    @GetMapping("/count/done")
    public Result<Long> countDone() {
        String currentUser = UserContext.getUsername();
        currentUser = requireCurrentUser(currentUser);
        return Result.success(processTaskService.countDone(currentUser));
    }

    /**
     * 同步流程实例的任务
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 处理后的同步任务集合结果，供调用方继续处理
     */
    @PostMapping("/sync/{processInstanceId}")
    public Result<Void> syncTasks(@PathVariable String processInstanceId) {
        processInstanceAccessService.requireReadAccess(processInstanceId);
        processTaskService.syncTasksFromFlowable(processInstanceId);
        return Result.success();
    }

    /**
     * 获取任务详情（包含表单和实体数据）
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 符合条件的{@code result<task}详情{@code dto>}结果，供调用方继续处理
     */
    @GetMapping("/detail/{taskId}")
    public Result<TaskDetailDTO> getTaskDetail(@PathVariable String taskId) {
        if (taskAddSignService.isAddSignTask(taskId)) {
            // 本地加签没有独立引擎任务，必须验证有效编排和本人办理权后再读取表单。
            taskDetailService.requireLocalAddSignTaskAccess(taskId);
        } else {
            taskActionService.requireTaskAccess(taskId);
        }
        return Result.success(taskDetailService.getTaskDetail(taskId));
    }

    /**
     * 候选用户认领任务。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 认领后的任务结果，供调用方继续处理
     */
    @PostMapping("/claim/{taskId}")
    public Result<Void> claimTask(@PathVariable String taskId) {
        try {
            taskActionService.claimTask(taskId);
        } catch (FlowableOptimisticLockingException | FlowableTaskAlreadyClaimedException exception) {
            throw taskStateChanged();
        } catch (FlowableObjectNotFoundException exception) {
            if (Task.class.equals(exception.getObjectClass())) {
                throw taskAlreadyCompleted();
            }
            throw exception;
        }
        return Result.success();
    }

    /**
     * 获取任务统计信息
     *
     * @return 符合条件的{@code result<map<string,}{@code object>>}结果，供调用方继续处理
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        try {
            String currentUser = UserContext.getUserId();
            if (currentUser == null || currentUser.isEmpty()) {
                currentUser = UserContext.getUsername();
            }
            currentUser = requireCurrentUser(currentUser);
            Map<String, Object> stats = taskActionService.getTaskStatistics(currentUser);
            return Result.success(stats);
        } catch (Exception e) {
            return Result.error("获取统计信息失败: " + e.getMessage());
        }
    }

    /**
     * 完成任务
     *
     * @param params 参数，作为 {@code requireSubmitApprovalAction} 的输入影响后续处理
     * @return 处理后的完成任务结果，供调用方继续处理
     */
    @PostMapping("/complete")
    public Result<Void> completeTask(@RequestBody TaskCompleteRequest params) {
        String taskId = params.getTaskId();
        String action = params.getAction();
        String comment = params.getComment();
        String transferTo = params.getTransferTo();
        String actionLabel = params.getActionLabel();
        Map<String, Object> formData = params.getFormData();

        if (taskId == null || taskId.isEmpty()) {
            return Result.error("任务ID不能为空");
        }

        try {
            String currentUser = UserContext.getUsername();
            if (currentUser == null || currentUser.isBlank()) {
                throw new ForbiddenException("用户未登录");
            }
            boolean hasNextSelections = params.getNextApproverSelections() != null
                    && !params.getNextApproverSelections().isEmpty();
            // 加签子任务同样由审批表单的 submitApproval 触发，不能在
            // early-return 分支绕过已发布按钮条件。
            requireSubmitApprovalAction(params);
            if (taskAddSignService.requireAddSignTaskAccess(taskId)) {
                if (hasNextSelections) {
                    throw new IllegalArgumentException(
                            "加签子任务不能指定下一节点审批人");
                }
                taskAddSignService.completeAddSignTask(taskId, action, comment);
                return Result.success();
            }
            if (hasNextSelections
                    && taskAddSignService.isAddSignSourceTask(taskId)) {
                throw new IllegalArgumentException(
                        "加签编排中的原任务不能指定下一节点审批人");
            }
            if (taskAddSignService.handleSourceCompletion(
                    taskId, currentUser, action, comment, actionLabel, formData)) {
                return Result.success();
            }
            taskActionService.completeTask(
                    taskId,
                    currentUser,
                    action,
                    comment,
                    transferTo,
                    actionLabel,
                    formData,
                    params.getNextApprovalScopeKey(),
                    params.getNextApproverSelections());
            return Result.success();
        } catch (FlowableOptimisticLockingException | FlowableTaskAlreadyClaimedException e) {
            // 事务代理提交时也可能才检测出并发修改，必须在服务事务退出后转换为可识别的冲突。
            throw taskStateChanged();
        } catch (FlowableObjectNotFoundException e) {
            // 另一人可能在读取任务之后、执行认领之前完成审批；仅转换任务消失，保留其他对象缺失的原错误。
            if (Task.class.equals(e.getObjectClass())) {
                throw taskAlreadyCompleted();
            }
            return Result.error("审批失败: " + e.getMessage());
        } catch (ForbiddenException | BusinessConflictException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("审批失败: " + e.getMessage());
        }
    }

    /**
     * 普通审批提交必须重新校验活动任务所绑定发布表单的内置提交按钮。
     *
     * @param params 参数，作为 {@code request.setFormId} 的输入影响后续处理
     */
    private void requireSubmitApprovalAction(TaskCompleteRequest params) {
        FormActionResolveRequest request = new FormActionResolveRequest();
        request.setFormId(params.getFormId());
        request.setReleaseId(params.getFormReleaseId());
        request.setReleaseVersion(params.getFormReleaseVersion());
        request.setReleaseResolutionToken(
                params.getFormReleaseResolutionToken());
        request.setEntityCode(params.getEntityCode());
        request.setListKey(params.getListKey());
        request.setMode("approve");
        request.setRecordId(params.getRecordId());
        request.setTaskId(params.getTaskId());
        formActionService.requireBuiltInMutationAction(
                request, "submitApproval");
    }

    /**
     * 按当前审批动作和可编辑表单值预览下一人工审批节点。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param request 本次请求，后续经校验后用于处理预览下一步审批
     * @return 处理后的预览下一步审批结果，供调用方继续处理
     */
    @PostMapping("/{taskId}/next-approval-preview")
    public Result<NextApprovalPreviewResponse> previewNextApproval(
            @PathVariable String taskId,
            @RequestBody(required = false) NextApprovalPreviewRequest request) {
        RuntimeException taskAccessFailure = null;
        try {
            taskActionService.requireTaskAccess(taskId);
        } catch (RuntimeException exception) {
            taskAccessFailure = exception;
        }
        if (taskAccessFailure != null
                && taskAddSignService.requireAddSignTaskAccess(taskId)) {
            NextApprovalPreviewResponse response =
                    new NextApprovalPreviewResponse();
            response.setTaskId(taskId);
            response.setStatus(
                    com.workflow.process.task.api.response.NextApprovalPreviewStatus.DEFERRED);
            response.setMessage("加签子任务需等待加签编排完成");
            return Result.success(response);
        }
        if (taskAccessFailure != null) {
            throw taskAccessFailure;
        }
        if (taskAddSignService.isAddSignSourceTask(taskId)) {
            NextApprovalPreviewResponse response =
                    new NextApprovalPreviewResponse();
            response.setTaskId(taskId);
            response.setStatus(
                    com.workflow.process.task.api.response.NextApprovalPreviewStatus.DEFERRED);
            response.setMessage("加签编排中的下一节点需等待加签完成");
            return Result.success(response);
        }
        return Result.success(nextApprovalPreviewService.preview(
                taskId,
                request == null ? new NextApprovalPreviewRequest() : request));
    }

    /**
     * 分页查询某个已命中的下一节点允许选择的审批人。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param request 本次请求，后续经校验后用于处理下一步审批人选项
     * @return 处理后的下一步审批人选项结果，供调用方继续处理
     */
    @PostMapping("/{taskId}/next-approver-options")
    public Result<PageResult<NextApproverCandidateDTO>> nextApproverOptions(
            @PathVariable String taskId,
            @RequestBody NextApproverOptionsRequest request) {
        taskActionService.requireTaskAccess(taskId);
        return Result.success(nextApproverCandidateService.options(
                taskId, request));
    }

    /**
     * 获取流程历史记录
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 符合条件的任务结果，供调用方继续处理
     */
    @GetMapping("/history/{processInstanceId}")
    @EmbedDelegatedRuntimeApi(
            value = EmbedDelegatedRuntimeApi.Scope.PROCESS_RECORD_RUNTIME,
            targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                    .PROCESS_INSTANCE_PATH)
    public Result<List<TaskVO>> getProcessHistory(@PathVariable String processInstanceId) {
        processInstanceAccessService.requireReadAccess(processInstanceId);
        return Result.success(taskActionService.getProcessHistory(processInstanceId));
    }

    /**
     * 撤回流程
     * 发起人可以在流程未完成前撤回
     *
     * @param params 参数，供本方法处理{@code withdraw}流程时使用
     * @return 处理后的{@code withdraw}流程结果，供调用方继续处理
     */
    @PostMapping("/withdraw")
    public Result<Void> withdrawProcess(@RequestBody Map<String, String> params) {
        String processInstanceId = params.get("processInstanceId");
        String reason = params.get("reason");

        if (processInstanceId == null || processInstanceId.isEmpty()) {
            return Result.error("流程实例ID不能为空");
        }

        try {
            String currentUser = UserContext.getUserId();
            if (currentUser == null || currentUser.isBlank()) {
                currentUser = UserContext.getUsername();
            }
            if (currentUser == null || currentUser.isBlank()) {
                throw new ForbiddenException("用户未登录");
            }
            taskActionService.withdrawProcess(processInstanceId, currentUser, reason);
            return Result.success();
        } catch (ForbiddenException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("撤回失败: " + e.getMessage());
        }
    }

    /**
     * 并发提交或提前认领导致引擎版本冲突时，让界面保留输入并提示刷新任务状态。
     *
     * @return 处理后的任务状态已变更结果，供调用方继续处理
     */
    private BusinessConflictException taskStateChanged() {
        return new BusinessConflictException("TASK_STATE_CHANGED", "任务状态已变化，可能已被其他人认领或处理，请刷新待办列表");
    }

    /**
     * 引擎已删除被抢先完成的任务时，提示状态冲突并保留尚未提交的审批内容。
     *
     * @return 处理后的任务{@code already}{@code completed}结果，供调用方继续处理
     */
    private BusinessConflictException taskAlreadyCompleted() {
        return new BusinessConflictException("TASK_ALREADY_COMPLETED", "任务不存在或已被处理，请刷新待办列表");
    }

    /**
     * 校验并获取当前用户；不满足约束时阻止后续处理。
     *
     * @param username 用户名称，后续用于身份匹配或操作展示
     * @return 校验并获取后的当前用户文本，供调用方比较或展示
     * @throws ForbiddenException 当前用户缺少所需访问权限时抛出
     */
    private String requireCurrentUser(String username) {
        if (username == null || username.isBlank()) {
            throw new ForbiddenException("用户未登录");
        }
        return username;
    }
}
