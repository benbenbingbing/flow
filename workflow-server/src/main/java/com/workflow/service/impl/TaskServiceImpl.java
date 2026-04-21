package com.workflow.service.impl;

import com.workflow.common.PageResult;
import com.workflow.vo.TaskStatisticsVO;
import com.workflow.vo.TaskVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.task.api.Task;
import org.flowable.task.api.TaskQuery;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.flowable.task.api.history.HistoricTaskInstanceQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务服务实现
 */
@Slf4j
@Service("workflowTaskService")
@RequiredArgsConstructor
public class TaskServiceImpl implements com.workflow.service.TaskService {

    private final org.flowable.engine.TaskService flowableTaskService;
    private final HistoryService historyService;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final com.workflow.service.ProcessTaskService processTaskService;
    private final com.workflow.service.EntityFormService entityFormService;
    private final com.workflow.service.EntityDataService entityDataService;
    private final com.workflow.service.EntityDataDynamicService entityDataDynamicService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // 当前用户（模拟，实际应从安全上下文获取）
    private static final String CURRENT_USER = "admin";

    /**
     * 自动完成标记为跳过的任务
     * 检查所有活跃任务，如果节点配置了 skipNode=true，则自动完成
     */
    private void autoCompleteSkipTasks() {
        try {
            // 查询当前用户的所有待办任务
            List<Task> tasks = flowableTaskService.createTaskQuery()
                    .taskCandidateOrAssigned(CURRENT_USER)
                    .active()
                    .list();
            
            for (Task task : tasks) {
                // 检查任务是否标记为跳过
                Object skipReason = runtimeService.getVariable(task.getProcessInstanceId(), 
                        "skipReason_" + task.getTaskDefinitionKey());
                
                if (skipReason != null) {
                    // 添加审批意见
                    flowableTaskService.addComment(task.getId(), task.getProcessInstanceId(), 
                            skipReason.toString());
                    
                    // 设置审批结果
                    flowableTaskService.setVariable(task.getId(), "approved", true);
                    
                    // 自动完成任务
                    flowableTaskService.complete(task.getId());
                    
                    log.info("自动完成跳过任务: taskName={}, taskId={}", task.getName(), task.getId());
                }
            }
        } catch (Exception e) {
            log.error("自动完成跳过任务失败: {}", e.getMessage(), e);
            // 不抛出异常，避免影响正常查询
        }
    }

    @Override
    public TaskStatisticsVO getStatistics() {
        TaskStatisticsVO statistics = new TaskStatisticsVO();
        
        // 待办任务数
        long todoCount = flowableTaskService.createTaskQuery()
                .taskCandidateOrAssigned(CURRENT_USER)
                .active()
                .count();
        statistics.setTodoCount(todoCount);
        
        // 已办任务数（本月）
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        long doneCount = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(CURRENT_USER)
                .finished()
                .taskCompletedAfter(Date.from(monthStart.atZone(ZoneId.systemDefault()).toInstant()))
                .count();
        statistics.setDoneCount(doneCount);
        
        // 我发起的流程数
        long processCount = runtimeService.createProcessInstanceQuery()
                .startedBy(CURRENT_USER)
                .active()
                .count();
        statistics.setProcessCount(processCount);
        
        // 平均处理时长（小时）
        List<HistoricTaskInstance> completedTasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(CURRENT_USER)
                .finished()
                .list();
        
        if (!completedTasks.isEmpty()) {
            double avgDuration = completedTasks.stream()
                    .mapToLong(HistoricTaskInstance::getDurationInMillis)
                    .average()
                    .orElse(0);
            statistics.setAvgProcessTime(Math.round(avgDuration / 1000 / 60 / 60 * 10.0) / 10.0); // 转换为小时
        } else {
            statistics.setAvgProcessTime(0.0);
        }
        
        return statistics;
    }

    @Override
    public PageResult<TaskVO> getTodoList(Integer pageNum, Integer pageSize, String processName, String taskName, String timeRange) {
        // 先处理所有标记为跳过的任务
        autoCompleteSkipTasks();
        
        // 查询所有活跃任务（不限于当前用户，用于演示）
        TaskQuery query = flowableTaskService.createTaskQuery()
                .active()
                .orderByTaskCreateTime()
                .desc();
        
        // 时间范围过滤
        if (StringUtils.hasText(timeRange)) {
            Date startDate = getStartDateByRange(timeRange);
            if (startDate != null) {
                query.taskCreatedAfter(startDate);
            }
        }
        
        long total = query.count();
        List<Task> tasks = query.listPage((pageNum - 1) * pageSize, pageSize);
        
        List<TaskVO> records = tasks.stream()
                .map(this::convertToTodoVO)
                .filter(vo -> {
                    // 流程名称过滤
                    if (StringUtils.hasText(processName)) {
                        return vo.getProcessName() != null && vo.getProcessName().contains(processName);
                    }
                    return true;
                })
                .filter(vo -> {
                    // 任务名称过滤
                    if (StringUtils.hasText(taskName)) {
                        return vo.getTaskName() != null && vo.getTaskName().contains(taskName);
                    }
                    return true;
                })
                .collect(Collectors.toList());
        
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    @Override
    public PageResult<TaskVO> getDoneList(Integer pageNum, Integer pageSize, String processName, String taskName, String timeRange) {
        HistoricTaskInstanceQuery query = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(CURRENT_USER)
                .finished()
                .orderByHistoricTaskInstanceEndTime()
                .desc();
        
        // 时间范围过滤
        if (StringUtils.hasText(timeRange)) {
            Date startDate = getStartDateByRange(timeRange);
            if (startDate != null) {
                query.taskCompletedAfter(startDate);
            }
        }
        
        long total = query.count();
        List<HistoricTaskInstance> tasks = query.listPage((pageNum - 1) * pageSize, pageSize);
        
        List<TaskVO> records = tasks.stream()
                .map(this::convertToDoneVO)
                .filter(vo -> {
                    if (StringUtils.hasText(processName)) {
                        return vo.getProcessName() != null && vo.getProcessName().contains(processName);
                    }
                    return true;
                })
                .filter(vo -> {
                    if (StringUtils.hasText(taskName)) {
                        return vo.getTaskName() != null && vo.getTaskName().contains(taskName);
                    }
                    return true;
                })
                .collect(Collectors.toList());
        
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskId, String action, String comment, String transferTo) {
        org.flowable.task.api.Task task = flowableTaskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        
        if (task == null) {
            throw new RuntimeException("任务不存在或已处理");
        }
        
        String processInstanceId = task.getProcessInstanceId();
        String taskDefinitionKey = task.getTaskDefinitionKey();
        
        // 添加审批意见
        if (StringUtils.hasText(comment)) {
            flowableTaskService.addComment(taskId, processInstanceId, comment);
        }
        
        switch (action) {
            case "approve":
                // 通过 - 设置流程变量（使用runtimeService设置流程实例变量，供网关条件使用）
                runtimeService.setVariable(processInstanceId, "approved", true);
                
                // 检查是否是多实例任务（会签/或签）
                if (isMultiInstanceTask(task)) {
                    // 多实例任务处理
                    handleMultiInstanceApproval(task, action, comment);
                } else {
                    // 普通任务处理
                    flowableTaskService.complete(taskId);
                }
                
                // 更新本地待办状态
                processTaskService.completeTask(taskId, action, comment);
                
                // 同步创建下一节点的待办（如果不是多实例或已是最后一人）
                processTaskService.syncTasksFromFlowable(processInstanceId);
                
                log.info("任务审批通过: taskId={}, user={}", taskId, CURRENT_USER);
                break;
                
            case "reject":
                // 驳回 - 设置流程变量（使用runtimeService设置流程实例变量，供网关条件使用）
                runtimeService.setVariable(processInstanceId, "approved", false);
                
                // 如果是多实例任务，直接结束整个多实例
                if (isMultiInstanceTask(task)) {
                    // 设置多实例完成条件为true，强制结束
                    runtimeService.setVariable(processInstanceId, 
                            "nrOfCompletedInstances", 
                            getMultiInstanceTotal(task));
                }
                
                flowableTaskService.complete(taskId);
                
                // 更新本地待办状态
                processTaskService.completeTask(taskId, action, comment);
                
                // 驳回时结束该节点的其他待办
                completeOtherTasksInSameNode(processInstanceId, taskDefinitionKey, taskId, action, comment);
                
                log.info("任务审批驳回: taskId={}, user={}", taskId, CURRENT_USER);
                break;
                
            case "transfer":
                // 转办
                if (!StringUtils.hasText(transferTo)) {
                    throw new RuntimeException("转办人不能为空");
                }
                
                // 添加转办评论记录
                String transferComment = "转办给: " + transferTo;
                if (StringUtils.hasText(comment)) {
                    transferComment += "，意见: " + comment;
                }
                flowableTaskService.addComment(taskId, processInstanceId, transferComment);
                
                flowableTaskService.setAssignee(taskId, transferTo);
                
                // 更新本地待办的执行人为转办人，保持待办状态
                processTaskService.transferTask(taskId, transferTo, comment);
                
                log.info("任务转办: taskId={}, from={}, to={}", taskId, CURRENT_USER, transferTo);
                break;
                
            default:
                throw new RuntimeException("不支持的审批操作: " + action);
        }
    }
    
    /**
     * 判断是否是多实例任务（会签/或签）
     */
    private boolean isMultiInstanceTask(org.flowable.task.api.Task task) {
        // 通过检查执行实例是否有父执行实例来判断是否是多实例
        // 多实例任务的执行实例会有特定的父执行实例
        String executionId = task.getExecutionId();
        var execution = runtimeService.createExecutionQuery()
                .executionId(executionId)
                .singleResult();
        
        if (execution != null) {
            // 检查是否是多实例的一部分
            String parentId = execution.getParentId();
            if (parentId != null) {
                var parentExecution = runtimeService.createExecutionQuery()
                        .executionId(parentId)
                        .singleResult();
                // 父执行实例的activityId通常是多实例活动
                if (parentExecution != null && 
                    parentExecution.getActivityId() != null &&
                    parentExecution.getActivityId().contains(task.getTaskDefinitionKey())) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * 处理多实例任务的审批
     * 单签（或签）：一人通过，其他任务自动结束
     * 会签：需所有人审批通过
     */
    private void handleMultiInstanceApproval(org.flowable.task.api.Task task, 
                                              String action, String comment) {
        String processInstanceId = task.getProcessInstanceId();
        String taskDefinitionKey = task.getTaskDefinitionKey();
        
        // 获取多实例类型配置（从流程变量或节点配置）
        // 默认是单签（或签）
        String multiInstanceType = (String) runtimeService.getVariable(
                processInstanceId, 
                taskDefinitionKey + "_multiInstanceType");
        
        if (multiInstanceType == null) {
            multiInstanceType = "single"; // 默认单签
        }
        
        if ("single".equals(multiInstanceType)) {
            // 单签（或签）：当前人通过，自动完成同节点的其他任务
            flowableTaskService.complete(task.getId());
            
            // 自动完成同节点的其他待办
            completeOtherTasksInSameNode(processInstanceId, taskDefinitionKey, 
                    task.getId(), action, comment);
        } else {
            // 会签：正常完成，由Flowable自动判断多实例完成条件
            flowableTaskService.complete(task.getId());
        }
    }
    
    /**
     * 自动完成同节点的其他待办任务
     * 用于单签（或签）场景
     */
    private void completeOtherTasksInSameNode(String processInstanceId, 
                                               String taskDefinitionKey,
                                               String excludeTaskId,
                                               String action,
                                               String comment) {
        // 查询同节点的其他活跃任务
        List<Task> otherTasks = flowableTaskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(taskDefinitionKey)
                .active()
                .list();
        
        for (Task otherTask : otherTasks) {
            if (!otherTask.getId().equals(excludeTaskId)) {
                try {
                    // 添加系统自动审批意见
                    flowableTaskService.addComment(otherTask.getId(), processInstanceId, 
                            "系统自动完成（他人已审批）");
                    
                    // 设置变量
                    flowableTaskService.setVariable(otherTask.getId(), "approved", true);
                    flowableTaskService.setVariable(otherTask.getId(), "autoCompleted", true);
                    
                    // 完成任务
                    flowableTaskService.complete(otherTask.getId());
                    
                    // 更新本地待办状态
                    processTaskService.completeTask(otherTask.getId(), "auto", 
                            "系统自动完成（他人已审批）");
                    
                    log.info("自动完成同节点任务: taskId={}, node={}", 
                            otherTask.getId(), taskDefinitionKey);
                } catch (Exception e) {
                    log.error("自动完成任务失败: taskId={}, error={}", otherTask.getId(), e.getMessage());
                }
            }
        }
    }
    
    /**
     * 获取多实例任务的总数
     */
    private int getMultiInstanceTotal(org.flowable.task.api.Task task) {
        String processInstanceId = task.getProcessInstanceId();
        Object total = runtimeService.getVariable(processInstanceId, "nrOfInstances");
        return total != null ? (Integer) total : 1;
    }

    @Override
    public TaskVO getTaskDetail(String taskId) {
        org.flowable.task.api.Task task = flowableTaskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        
        if (task == null) {
            return null;
        }
        
        TaskVO vo = convertToTodoVO(task);
        
        // 获取流程变量中的实体信息
        String entityCode = (String) runtimeService.getVariable(task.getProcessInstanceId(), "entityCode");
        String entityDataId = (String) runtimeService.getVariable(task.getProcessInstanceId(), "entityDataId");
        
        if (entityCode != null) {
            vo.setEntityCode(entityCode);
        }
        
        // 查询实体数据
        if (entityDataId != null) {
            vo.setEntityDataId(entityDataId);
            try {
                com.workflow.dto.EntityDataDTO entityData = entityDataService.findById(entityDataId);
                if (entityData != null && entityData.getData() != null) {
                    vo.setEntityData(entityData.getData());
                }
            } catch (Exception e) {
                log.warn("获取实体数据失败: entityDataId={}", entityDataId, e);
            }
        }
        
        // 获取节点配置的表单信息
        try {
            String formKey = task.getFormKey();
            if (formKey == null) {
                // 从本地待办中获取formKey
                com.workflow.entity.ProcessTask localTask = processTaskService.getTaskByTaskId(taskId);
                if (localTask != null) {
                    formKey = localTask.getFormKey();
                }
            }
            
            if (formKey != null && entityCode != null) {
                // 查询实体表单配置
                com.workflow.entity.EntityDefinition entityDef = entityFormService
                        .getEntityByCode(entityCode);
                if (entityDef != null) {
                    com.workflow.entity.EntityForm form = entityFormService
                            .getByEntityIdAndFormKey(entityDef.getId(), formKey);
                    if (form != null) {
                        vo.setFormKey(formKey);
                        vo.setEntityFormId(form.getId());
                        vo.setFormReadonly(true); // 审批时表单只读
                        
                        // 构建表单配置对象
                        Map<String, Object> formConfig = new java.util.HashMap<>();
                        formConfig.put("formName", form.getFormName());
                        formConfig.put("layoutType", form.getLayoutType());
                        formConfig.put("isReadonly", true);
                        
                        // 转换字段配置
                        if (form.getFields() != null && !form.getFields().isEmpty()) {
                            List<Map<String, Object>> fields = form.getFields().stream()
                                    .map(f -> {
                                        Map<String, Object> field = new java.util.HashMap<>();
                                        field.put("id", f.getId());
                                        field.put("fieldCode", f.getFieldId()); // 实体字段编码
                                        field.put("fieldName", f.getFieldName());
                                        field.put("fieldLabel", f.getFieldLabel());
                                        field.put("fieldType", f.getFieldType());
                                        field.put("componentType", f.getComponentType());
                                        field.put("isRequired", f.getIsRequired());
                                        field.put("isReadonly", f.getIsReadonly());
                                        field.put("isHidden", f.getIsHidden());
                                        field.put("defaultValue", f.getDefaultValue());
                                        field.put("placeholder", f.getPlaceholder());
                                        field.put("sortOrder", f.getSortOrder());
                                        field.put("gridSpan", f.getGridSpan());
                                        // 解析组件属性JSON
                                        if (f.getComponentProps() != null) {
                                            try {
                                                field.put("componentProps", 
                                                    objectMapper.readValue(f.getComponentProps(), Map.class));
                                            } catch (Exception e) {
                                                field.put("componentProps", new java.util.HashMap<>());
                                            }
                                        }
                                        return field;
                                    })
                                    .collect(Collectors.toList());
                            formConfig.put("fields", fields);
                        }
                        
                        vo.setFormConfig(formConfig);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取表单配置失败: taskId={}", taskId, e);
        }
        
        return vo;
    }

    /**
     * 转换为待办VO
     */
    private TaskVO convertToTodoVO(Task task) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskName(task.getName());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setCreateTime(task.getCreateTime());
        vo.setPriority(task.getPriority());
        vo.setAssignee(task.getAssignee());
        
        // 获取流程定义信息
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(task.getProcessDefinitionId())
                .singleResult();
        if (pd != null) {
            vo.setProcessName(pd.getName());
        }
        
        // 获取流程实例发起人
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
        if (hpi != null) {
            vo.setStartUserName(hpi.getStartUserId());
            vo.setBusinessKey(hpi.getBusinessKey());
        }
        
        // 获取数据标题、编码、当前任务名（从实体数据）
        try {
            String entityCode = (String) runtimeService.getVariable(task.getProcessInstanceId(), "entityCode");
            String entityDataId = (String) runtimeService.getVariable(task.getProcessInstanceId(), "entityDataId");
            if (entityDataId != null) {
                com.workflow.dto.EntityDataDTO entityData = null;
                if (entityCode != null) {
                    try {
                        entityData = entityDataDynamicService.findById(entityCode, entityDataId);
                    } catch (Exception ex) {
                        // fallback to entityDataService
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
            log.debug("获取数据标题失败: {}", e.getMessage());
        }
        
        return vo;
    }

    /**
     * 转换为已办VO
     */
    private TaskVO convertToDoneVO(HistoricTaskInstance task) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskName(task.getName());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setCreateTime(task.getCreateTime());
        vo.setEndTime(task.getEndTime());
        vo.setDuration(task.getDurationInMillis());
        vo.setPriority(task.getPriority());
        vo.setAssignee(task.getAssignee());
        
        // 获取流程定义信息
        ProcessDefinition pd = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(task.getProcessDefinitionId())
                .singleResult();
        if (pd != null) {
            vo.setProcessName(pd.getName());
        }
        
        // 获取流程实例发起人
        HistoricProcessInstance hpi = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(task.getProcessInstanceId())
                .singleResult();
        if (hpi != null) {
            vo.setStartUserName(hpi.getStartUserId());
            vo.setBusinessKey(hpi.getBusinessKey());
        }
        
        // 获取审批意见（简化处理）
        vo.setResult("approve"); // 默认可通过变量判断
        
        // 获取数据标题、编码、当前任务名（从历史变量）
        try {
            var entityCodeVar = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .variableName("entityCode")
                    .singleResult();
            String entityCode = entityCodeVar != null ? (String) entityCodeVar.getValue() : null;
            
            var entityDataVar = historyService.createHistoricVariableInstanceQuery()
                    .processInstanceId(task.getProcessInstanceId())
                    .variableName("entityDataId")
                    .singleResult();
            String entityDataId = entityDataVar != null ? (String) entityDataVar.getValue() : null;
            if (entityDataId != null) {
                com.workflow.dto.EntityDataDTO entityData = null;
                if (entityCode != null) {
                    try {
                        entityData = entityDataDynamicService.findById(entityCode, entityDataId);
                    } catch (Exception ex) {
                        // fallback to entityDataService
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
            log.debug("获取数据标题失败: {}", e.getMessage());
        }
        
        return vo;
    }

    /**
     * 根据时间范围获取开始日期
     */
    private Date getStartDateByRange(String range) {
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        switch (range) {
            case "week":
                return Date.from(now.minusWeeks(1).atZone(ZoneId.systemDefault()).toInstant());
            case "month":
                return Date.from(now.minusMonths(1).atZone(ZoneId.systemDefault()).toInstant());
            case "year":
                return Date.from(now.minusYears(1).atZone(ZoneId.systemDefault()).toInstant());
            default:
                return null;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void withdrawProcess(String processInstanceId, String reason) {
        // 1. 查询流程实例
        HistoricProcessInstance processInstance = historyService
                .createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        
        if (processInstance == null) {
            throw new RuntimeException("流程实例不存在");
        }
        
        // 2. 检查是否是发起人（当前用户）
        if (!CURRENT_USER.equals(processInstance.getStartUserId())) {
            throw new RuntimeException("只有发起人才能撤回流程");
        }
        
        // 3. 检查流程是否已结束
        if (processInstance.getEndTime() != null) {
            throw new RuntimeException("流程已结束，无法撤回");
        }
        
        // 4. 检查是否已有审批记录（第一个节点是否已审批）
        long completedTaskCount = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .count();
        
        if (completedTaskCount > 0) {
            throw new RuntimeException("流程已被审批，无法撤回");
        }
        
        // 5. 删除流程实例（撤回）
        runtimeService.deleteProcessInstance(processInstanceId, 
                "发起人撤回: " + (reason != null ? reason : ""));
        
        // 6. 更新本地待办状态
        processTaskService.deleteTasksByProcessInstance(processInstanceId);
        
        log.info("流程撤回成功: processInstanceId={}, user={}, reason={}", 
                processInstanceId, CURRENT_USER, reason);
    }

    @Override
    public List<TaskVO> getProcessHistory(String processInstanceId) {
        // 查询流程的所有历史任务（包括已完成的和进行中的）
        List<HistoricTaskInstance> historicTasks = historyService
                .createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricTaskInstanceStartTime()
                .asc()
                .list();
        
        return historicTasks.stream()
                .map(this::convertToHistoryVO)
                .collect(Collectors.toList());
    }

    /**
     * 转换为历史记录VO
     */
    private TaskVO convertToHistoryVO(HistoricTaskInstance task) {
        TaskVO vo = new TaskVO();
        vo.setTaskId(task.getId());
        vo.setTaskName(task.getName());
        vo.setProcessInstanceId(task.getProcessInstanceId());
        vo.setProcessDefinitionId(task.getProcessDefinitionId());
        vo.setCreateTime(task.getCreateTime());
        vo.setEndTime(task.getEndTime());
        vo.setDuration(task.getDurationInMillis());
        vo.setAssignee(task.getAssignee());
        
        // 获取审批意见
        List<org.flowable.engine.task.Comment> comments = flowableTaskService
                .getTaskComments(task.getId());
        if (!comments.isEmpty()) {
            vo.setComment(comments.get(0).getFullMessage());
        }
        
        // 判断审批结果
        if (task.getEndTime() != null) {
            // 已完成的任务，根据变量判断结果
            try {
                HistoricProcessInstance hpi = historyService
                        .createHistoricProcessInstanceQuery()
                        .processInstanceId(task.getProcessInstanceId())
                        .singleResult();
                
                // 获取流程变量判断审批结果
                // 这里简化处理，实际应该从历史变量中查询
                vo.setResult("approve"); 
            } catch (Exception e) {
                log.warn("获取任务结果失败: {}", task.getId());
            }
        }
        
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resubmitTask(String taskId, String comment, Map<String, Object> formData) {
        org.flowable.task.api.Task task = flowableTaskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        
        if (task == null) {
            throw new RuntimeException("任务不存在或已处理");
        }
        
        String processInstanceId = task.getProcessInstanceId();
        
        // 1. 添加审批意见
        if (StringUtils.hasText(comment)) {
            flowableTaskService.addComment(taskId, processInstanceId, 
                    "重新提交: " + comment);
        }
        
        // 2. 更新表单数据
        if (formData != null) {
            formData.forEach((key, value) -> {
                runtimeService.setVariable(processInstanceId, key, value);
            });
        }
        
        // 3. 设置审批结果变量
        flowableTaskService.setVariable(taskId, "approved", true);
        flowableTaskService.setVariable(taskId, "resubmitted", true);
        
        // 4. 完成任务
        flowableTaskService.complete(taskId);
        
        // 5. 更新本地待办状态
        processTaskService.completeTask(taskId, "resubmit", comment);
        
        // 6. 同步创建下一节点的待办
        processTaskService.syncTasksFromFlowable(processInstanceId);
        
        log.info("任务重新提交成功: taskId={}, user={}", taskId, CURRENT_USER);
    }
}
