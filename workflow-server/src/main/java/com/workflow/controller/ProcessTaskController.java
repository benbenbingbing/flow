package com.workflow.controller;

import com.workflow.common.UserContext;
import com.workflow.common.PageResult;
import com.workflow.common.Result;
import com.workflow.dto.TaskDetailDTO;
import com.workflow.entity.ProcessTask;
import com.workflow.service.ProcessTaskService;
import com.workflow.service.TaskDetailService;
import com.workflow.service.TaskActionService;
import com.workflow.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 流程待办控制器
 */
@RestController
@RequestMapping("/api/process-task")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProcessTaskController {

    private final ProcessTaskService processTaskService;
    private final TaskDetailService taskDetailService;
    private final TaskActionService taskActionService;
    private final com.workflow.service.EntityDataDynamicService entityDataDynamicService;
    private final com.workflow.service.EntityDataService entityDataService;
    private final org.flowable.engine.HistoryService historyService;
    private final com.workflow.service.SysUserService sysUserService;

    /**
     * 获取用户待办列表（分页，兼容前端TaskVO格式）
     */
    @GetMapping("/todo")
    public Result<PageResult<TaskVO>> getTodoList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        String currentUser = UserContext.getUsername();
        if (currentUser == null) {
            currentUser = "admin";
        }
        List<ProcessTask> tasks = processTaskService.getTodoList(currentUser);

        // 转换为TaskVO格式
        List<TaskVO> voList = tasks.stream()
                .map(this::convertToTaskVO)
                .collect(Collectors.toList());

        // 手动分页
        int total = voList.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
        List<TaskVO> pageList = start < total ? voList.subList(start, end) : List.of();

        return Result.success(new PageResult<>(pageList, total, pageNum, pageSize));
    }

    /**
     * 获取用户已办列表（分页，兼容前端TaskVO格式）
     */
    @GetMapping("/done")
    public Result<PageResult<TaskVO>> getDoneList(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize) {
        String currentUser = UserContext.getUsername();
        if (currentUser == null) {
            currentUser = "admin";
        }
        List<ProcessTask> tasks = processTaskService.getDoneList(currentUser);

        // 转换为TaskVO格式
        List<TaskVO> voList = tasks.stream()
                .map(this::convertToTaskVO)
                .collect(Collectors.toList());

        // 手动分页
        int total = voList.size();
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, total);
        List<TaskVO> pageList = start < total ? voList.subList(start, end) : List.of();

        return Result.success(new PageResult<>(pageList, total, pageNum, pageSize));
    }

    /**
     * 统计待办数量
     */
    @GetMapping("/count/todo")
    public Result<Long> countTodo() {
        String currentUser = UserContext.getUsername();
        if (currentUser == null) {
            currentUser = "admin";
        }
        return Result.success(processTaskService.countTodo(currentUser));
    }

    /**
     * 统计已办数量
     */
    @GetMapping("/count/done")
    public Result<Long> countDone() {
        String currentUser = UserContext.getUsername();
        if (currentUser == null) {
            currentUser = "admin";
        }
        return Result.success(processTaskService.countDone(currentUser));
    }

    /**
     * 同步流程实例的任务
     */
    @PostMapping("/sync/{processInstanceId}")
    public Result<Void> syncTasks(@PathVariable String processInstanceId) {
        processTaskService.syncTasksFromFlowable(processInstanceId);
        return Result.success();
    }

    /**
     * 获取任务详情（包含表单和实体数据）
     */
    @GetMapping("/detail/{taskId}")
    public Result<TaskDetailDTO> getTaskDetail(@PathVariable String taskId) {
        return Result.success(taskDetailService.getTaskDetail(taskId));
    }

    /**
     * 获取任务统计信息
     */
    @GetMapping("/statistics")
    public Result<Map<String, Object>> getStatistics() {
        try {
            String currentUser = UserContext.getUsername();
            if (currentUser == null) {
                currentUser = "admin";
            }
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
    public Result<Void> completeTask(@RequestBody Map<String, Object> params) {
        String taskId = (String) params.get("taskId");
        String action = (String) params.get("action");
        String comment = (String) params.get("comment");
        String transferTo = (String) params.get("transferTo");

        if (taskId == null || taskId.isEmpty()) {
            return Result.error("任务ID不能为空");
        }

        try {
            String currentUser = UserContext.getUsername();
            if (currentUser == null) {
                currentUser = "admin";
            }
            taskActionService.completeTask(taskId, currentUser, action, comment, transferTo);
            return Result.success();
        } catch (Exception e) {
            return Result.error("审批失败: " + e.getMessage());
        }
    }

    /**
     * 获取流程历史记录
     */
    @GetMapping("/history/{processInstanceId}")
    public Result<List<TaskVO>> getProcessHistory(@PathVariable String processInstanceId) {
        try {
            List<TaskVO> history = taskActionService.getProcessHistory(processInstanceId);
            return Result.success(history);
        } catch (Exception e) {
            return Result.error("获取历史失败: " + e.getMessage());
        }
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
            String currentUser = UserContext.getUsername();
            if (currentUser == null) {
                currentUser = "admin";
            }
            taskActionService.withdrawProcess(processInstanceId, currentUser, reason);
            return Result.success();
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
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setProcessName(task.getProcessName());
        vo.setAssignee(task.getAssigneeId());
        vo.setAssigneeName(task.getAssigneeName()); // 执行人姓名
        
        // 发起人名称从流程实例历史记录中查询，不能复用 assigneeName（候选组任务时 assigneeName 是组名）
        String startUserName = null;
        try {
            org.flowable.engine.history.HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .singleResult();
            if (hpi != null && hpi.getStartUserId() != null) {
                startUserName = sysUserService.getNicknameByUsername(hpi.getStartUserId());
                if (startUserName == null) {
                    startUserName = hpi.getStartUserId();
                }
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
        vo.setResult(task.getAction());
        vo.setComment(task.getComment());

        // 扩展字段
        vo.setEntityCode(task.getEntityCode());
        vo.setEntityDataId(task.getEntityDataId());
        vo.setFormKey(task.getFormKey());

        // 查询实体数据填充 name、code、currentTaskName
        try {
            String entityCode = task.getEntityCode();
            String entityDataId = task.getEntityDataId();
            if (entityDataId != null) {
                com.workflow.dto.EntityDataDTO entityData = null;
                if (entityCode != null) {
                    try {
                        entityData = entityDataDynamicService.findById(entityCode, entityDataId);
                    } catch (Exception ex) {
                        // fallback
                    }
                }
                if (entityData == null) {
                    entityData = entityDataService.findById(entityDataId);
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
}
