package com.workflow.process.task.application;

import com.workflow.core.logging.LogValue;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.result.PageResult;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.process.instance.application.WorkflowReservedVariables;
import com.workflow.process.workbench.api.response.TaskStatisticsVO;
import com.workflow.process.task.api.response.TaskVO;
import com.workflow.process.task.infrastructure.persistence.record.ProcessTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
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
public class TaskServiceImpl implements com.workflow.process.task.application.TaskService {

    private final org.flowable.engine.TaskService flowableTaskService;
    private final HistoryService historyService;
    private final RuntimeService runtimeService;
    private final com.workflow.process.task.application.ProcessTaskService processTaskService;
    private final com.workflow.entity.form.application.EntityFormService entityFormService;
    private final com.workflow.entity.data.application.EntityDataDynamicService entityDataDynamicService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    /** 办理权威入口。本类不再维护第二套会签完成逻辑。 */
    private final TaskActionService taskActionService;
    private final TaskListQueryService taskListQueryService;

    /**
     * 读取{@code statistics}；查询结果供调用方展示或继续处理。
     *
     * @return 符合条件的任务{@code statistics}结果，供调用方继续处理
     */
    @Override
    public TaskStatisticsVO getStatistics() {
        TaskStatisticsVO statistics = new TaskStatisticsVO();
        
        // 与待办页共用业务用户组/角色候选范围，Flowable IDM 并未维护这些成员关系。
        long todoCount = processTaskService.countTodo(UserContext.requireUsernameOrId());
        statistics.setTodoCount(todoCount);
        
        // 已办任务数（本月）
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        long doneCount = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(UserContext.requireUsernameOrId())
                .finished()
                .taskCompletedAfter(Date.from(monthStart.atZone(ZoneId.systemDefault()).toInstant()))
                .count();
        statistics.setDoneCount(doneCount);
        
        // 我发起的流程数
        long processCount = runtimeService.createProcessInstanceQuery()
                .startedBy(UserContext.requireUsernameOrId())
                .active()
                .count();
        statistics.setProcessCount(processCount);
        
        // 平均处理时长（小时）
        List<HistoricTaskInstance> completedTasks = historyService.createHistoricTaskInstanceQuery()
                .taskAssignee(UserContext.requireUsernameOrId())
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

    /**
     * 按当前用户的真实办理/候选范围查询待办，先过滤再分页以保证总数准确。
     *
     * <p>业务用户组和角色由 ProcessTaskService 统一匹配，不能使用未同步成员
     * 关系的 Flowable IDM，也不能让旧版接口查询其他用户的全部活跃任务。</p>
     *
     * @param pageNum 分页数量参数，用于限制后续查询范围和返回数量
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param processName 流程名称，后续用于读取待办列表时匹配或展示
     * @param taskName 任务名称，后续用于读取待办列表时匹配或展示
     * @param timeRange 时间范围，供本方法读取待办列表时使用
     * @return 符合条件的任务结果，供调用方继续处理
     */
    @Override
    public PageResult<TaskVO> getTodoList(Integer pageNum, Integer pageSize, String processName, String taskName, String timeRange) {
        return taskListQueryService.findLegacyTodo(pageNum, pageSize, processName, taskName, timeRange);
    }

    /**
     * 处理完成任务，并将结果传给后续步骤。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param action 动作标识，决定后续完成任务采用的处理分支
     * @param comment 注释，供本方法处理完成任务时使用
     * @param transferTo 转办截止，供本方法处理完成任务时使用
     * @param actionLabel 动作标签，后续用于处理完成任务时匹配或展示
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.APPROVE,
            operation = "办理流程任务",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "PROCESS_TASK",
            targetIdArg = 0)
    /**
     * 完成任务。办理语义统一走 {@link TaskActionService}，避免会签/或签出现第二套实现。
     */
    public void completeTask(String taskId, String action, String comment, String transferTo, String actionLabel) {
        taskActionService.completeTask(
                taskId,
                UserContext.requireUsernameOrId(),
                action,
                comment,
                transferTo,
                actionLabel);
    }

    /**
     * 读取任务详情；查询结果供调用方展示或继续处理。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @return 符合条件的任务结果，供调用方继续处理
     */
    @Override
    public TaskVO getTaskDetail(String taskId) {
        org.flowable.task.api.Task task = flowableTaskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        
        if (task == null) {
            return null;
        }
        
        TaskVO vo = taskListQueryService.convertToTodoVO(task);
        
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
                com.workflow.entity.data.api.response.EntityDataDTO entityData =
                        entityDataDynamicService.findById(
                                entityCode,
                                entityDataId);
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
                com.workflow.process.task.infrastructure.persistence.record.ProcessTask localTask = processTaskService.getTaskByTaskId(taskId);
                if (localTask != null) {
                    formKey = localTask.getFormKey();
                }
            }
            
            if (formKey != null && entityCode != null) {
                // 查询实体表单配置
                com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition entityDef = entityFormService
                        .getEntityByCode(entityCode);
                if (entityDef != null) {
                    com.workflow.entity.form.infrastructure.persistence.record.EntityForm form = entityFormService
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
            log.warn("获取表单配置失败: taskId={}, failureType={}", LogValue.safe(taskId), LogValue.failureType(e));
        }
        
        return vo;
    }

    /**
     * 兼容旧 TaskService 调用，统一委托发起人撤回入口。
     * 当前节点开关决定是否允许，不再按已有审批记录限制撤回。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param reason 原因，供本方法处理{@code withdraw}流程时使用
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.WITHDRAW,
            operation = "撤回流程",
            risk = AuditRiskLevel.HIGH,
            targetType = "PROCESS_INSTANCE",
            targetIdArg = 0)
    public void withdrawProcess(String processInstanceId, String reason) {
        // 所有入口使用同一发起人、运行状态和当前节点开关校验，不再按“是否已审批”限制撤回。
        String userId = UserContext.getUserId();
        if (userId == null || userId.isBlank()) userId = UserContext.getUsername();
        taskActionService.withdrawProcess(processInstanceId, userId, reason);
    }

    /**
     * 读取流程历史；查询结果供调用方展示或继续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @return 任务集合，供调用方遍历或展示
     */
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
     *
     * @param task 任务，作为 {@code vo.setTaskId} 的输入影响后续处理
     * @return 转换后的截止历史VO结果，供调用方继续处理
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

    /**
     * 处理{@code resubmit}任务，并将结果传给后续步骤。
     *
     * @param taskId 任务 ID，用于定位目标待办并关联后续状态或操作
     * @param comment 注释，作为 {@code flowableTaskService.addComment} 的输入影响后续处理
     * @param formData 表单数据，作为 {@code WorkflowReservedVariables.sanitizeRuntimeMutation} 的输入影响后续处理
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.PROCESS,
            action = AuditAction.RESUBMIT,
            operation = "重新提交任务",
            risk = AuditRiskLevel.HIGH,
            targetType = "PROCESS_TASK",
            targetIdArg = 0)
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
        Map<String, Object> safeFormData =
                WorkflowReservedVariables.sanitizeRuntimeMutation(formData);
        if (!safeFormData.isEmpty()) {
            safeFormData.forEach((key, value) -> {
                runtimeService.setVariable(processInstanceId, key, value);
            });
        }
        
        // 3. 设置审批结果变量
        flowableTaskService.setVariable(taskId, "approved", "approve");
        flowableTaskService.setVariable(taskId, "resubmitted", true);
        
        // 4. 完成任务
        flowableTaskService.complete(taskId);
        
        // 5. 更新本地待办状态
        processTaskService.completeTask(taskId, "resubmit", comment);
        
        // 6. 同步创建下一节点的待办
        processTaskService.syncTasksFromFlowable(processInstanceId);
        
        log.info("任务重新提交成功: taskId={}, user={}", taskId, UserContext.requireUsernameOrId());
    }
}
