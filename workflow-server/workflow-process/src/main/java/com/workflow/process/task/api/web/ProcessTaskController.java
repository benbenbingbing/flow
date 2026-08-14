package com.workflow.process.task.api.web;

import com.workflow.core.security.AuthenticatedApi;

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
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import com.workflow.process.task.application.ProcessTaskService;
import com.workflow.process.task.application.TaskListFilter;
import com.workflow.process.task.application.TaskDetailService;
import com.workflow.process.task.application.TaskActionService;
import com.workflow.process.task.application.nextapproval.NextApprovalPreviewService;
import com.workflow.process.task.application.nextapproval.NextApproverCandidateService;
import com.workflow.process.instance.application.ProcessInstanceAccessService;
import com.workflow.process.task.api.response.TaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private final com.workflow.entity.data.application.EntityDataDynamicService entityDataDynamicService;
    private final org.flowable.engine.HistoryService historyService;
    private final com.workflow.admin.identity.user.application.SysUserService sysUserService;

    @Autowired
    private NextApprovalPreviewService nextApprovalPreviewService;

    @Autowired
    private NextApproverCandidateService nextApproverCandidateService;

    /**
     * 获取用户待办列表（分页，兼容前端TaskVO格式）
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
        String currentUser = UserContext.getUsername();
        currentUser = requireCurrentUser(currentUser);
        List<ProcessTask> tasks = processTaskService.getTodoList(currentUser);

        List<TaskVO> voList = TaskListFilter.filter(tasks.stream()
                .map(this::convertToTaskVO)
                .collect(Collectors.toList()), keyword, startUserName, priority, startDate, endDate);

        return Result.success(page(voList, pageNum, pageSize));
    }

    /**
     * 获取用户已办列表（分页，兼容前端TaskVO格式）
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
        String currentUser = UserContext.getUsername();
        currentUser = requireCurrentUser(currentUser);
        List<ProcessTask> tasks = processTaskService.getDoneList(currentUser);

        List<TaskVO> voList = TaskListFilter.filter(tasks.stream()
                .map(this::convertToTaskVO)
                .collect(Collectors.toList()), keyword, startUserName, priority, startDate, endDate);

        return Result.success(page(voList, pageNum, pageSize));
    }

    /**
     * 统计待办数量
     */
    @GetMapping("/count/todo")
    public Result<Long> countTodo() {
        String currentUser = UserContext.getUsername();
        currentUser = requireCurrentUser(currentUser);
        return Result.success(processTaskService.countTodo(currentUser));
    }

    /**
     * 统计已办数量
     */
    @GetMapping("/count/done")
    public Result<Long> countDone() {
        String currentUser = UserContext.getUsername();
        currentUser = requireCurrentUser(currentUser);
        return Result.success(processTaskService.countDone(currentUser));
    }

    /**
     * 同步流程实例的任务
     */
    @PostMapping("/sync/{processInstanceId}")
    public Result<Void> syncTasks(@PathVariable String processInstanceId) {
        processInstanceAccessService.requireReadAccess(processInstanceId);
        processTaskService.syncTasksFromFlowable(processInstanceId);
        return Result.success();
    }

    /**
     * 获取任务详情（包含表单和实体数据）
     */
    @GetMapping("/detail/{taskId}")
    public Result<TaskDetailDTO> getTaskDetail(@PathVariable String taskId) {
        taskActionService.requireTaskAccess(taskId);
        return Result.success(taskDetailService.getTaskDetail(taskId));
    }

    /**
     * 候选用户认领任务。
     */
    @PostMapping("/claim/{taskId}")
    public Result<Void> claimTask(@PathVariable String taskId) {
        taskActionService.claimTask(taskId);
        return Result.success();
    }

    /**
     * 获取任务统计信息
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
        } catch (ForbiddenException | BusinessConflictException e) {
            throw e;
        } catch (Exception e) {
            return Result.error("审批失败: " + e.getMessage());
        }
    }

    /**
     * 按当前审批动作和可编辑表单值预览下一人工审批节点。
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
     */
    @GetMapping("/history/{processInstanceId}")
    public Result<List<TaskVO>> getProcessHistory(@PathVariable String processInstanceId) {
        processInstanceAccessService.requireReadAccess(processInstanceId);
        return Result.success(taskActionService.getProcessHistory(processInstanceId));
    }

    /**
     * 撤回流程
     * 发起人可以在流程未完成前撤回
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
     * 将ProcessTask转换为TaskVO
     */
    private TaskVO convertToTaskVO(ProcessTask task) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getTaskId());
        vo.setTaskName(task.getNodeName());
        vo.setNodeType(task.getNodeType());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setProcessName(task.getProcessName());
        vo.setAssignee(task.getAssigneeId());
        vo.setAssigneeName(task.getAssigneeName()); // 执行人姓名
        vo.setAssigneeType(task.getAssigneeType());
        vo.setClaimRequired("group".equalsIgnoreCase(task.getAssigneeType()));
        
        // 发起人名称从流程实例历史记录中查询，不能复用 assigneeName（候选组任务时 assigneeName 是组名）
        String startUserName = null;
        try {
            org.flowable.engine.history.HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();
            if (hpi != null && hpi.getStartUserId() != null) {
                startUserName = sysUserService.getDisplayName(hpi.getStartUserId());
            }
        } catch (Exception e) {
            // ignore
        }
        vo.setStartUserName(startUserName);
        vo.setBusinessKey(task.getBusinessKey());

        // 时间转换
        if (task.getStartTime() != null) {
            vo.setCreateTime(Date.from(task.getStartTime().atZone(ZoneId.systemDefault()).toInstant()));
        }
        if (task.getEndTime() != null) {
            vo.setEndTime(Date.from(task.getEndTime().atZone(ZoneId.systemDefault()).toInstant()));
        }

        vo.setDuration(task.getDuration());
        vo.setPriority(task.getPriority());
        vo.setResult(task.getAction());
        vo.setComment(task.getComment());
        vo.setSlaStatus(task.getSlaStatus());
        vo.setResponseDueTime(toUtcDate(task.getResponseDueTime()));
        vo.setDueTime(toUtcDate(task.getDueTime()));

        // 扩展字段
        vo.setEntityCode(task.getEntityCode());
        vo.setEntityDataId(task.getEntityDataId());
        vo.setFormKey(task.getFormKey());

        // 查询实体数据填充 name、code、currentTaskName
        try {
            String entityCode = task.getEntityCode();
            String entityDataId = task.getEntityDataId();
            if (entityDataId != null) {
                com.workflow.entity.data.api.response.EntityDataDTO entityData = null;
                if (entityCode != null) {
                    try {
                        entityData = entityDataDynamicService.findById(entityCode, entityDataId);
                    } catch (Exception ex) {
                        // fallback
                    }
                }
                if (entityData != null) {
                    if (entityData.getData() != null) {
                        vo.setDataName((String) entityData.getData().get("name"));
                    }
                    vo.setName(entityData.getName());
                    vo.setCode(entityData.getCode());
                    vo.setCurrentTaskName(entityData.getCurrentTaskName());
                }
            }
        } catch (Exception e) {
            // ignore
        }

        return vo;
    }

    private Date toUtcDate(java.time.LocalDateTime value) {
        return value == null
                ? null
                : Date.from(value.toInstant(ZoneOffset.UTC));
    }

    private PageResult<TaskVO> page(List<TaskVO> tasks, Integer requestedPage, Integer requestedSize) {
        int pageNum = requestedPage == null ? 1 : Math.max(1, requestedPage);
        int pageSize = requestedSize == null ? 10 : Math.min(100, Math.max(1, requestedSize));
        int total = tasks.size();
        int start = Math.min((pageNum - 1) * pageSize, total);
        int end = Math.min(start + pageSize, total);
        return new PageResult<>(tasks.subList(start, end), total, pageNum, pageSize);
    }

    private String requireCurrentUser(String username) {
        if (username == null || username.isBlank()) {
            throw new ForbiddenException("用户未登录");
        }
        return username;
    }
}
