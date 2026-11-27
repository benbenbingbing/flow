package com.workflow.process.instance.application;

import com.workflow.process.status.application.ProcessEndReason;
import com.workflow.port.entity.model.ui.UiRuntimePurpose;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.process.instance.api.response.ProcessProgressDTO;
import com.workflow.process.instance.api.response.ProcessFormRuntimeDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.audit.application.ProcessTransferLogFormatter;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.definition.application.EntityStatusService;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.process.form.application.EntityFormRuntimeService;
import com.workflow.entity.form.application.EntityFormFieldRuntimeMapper;
import com.workflow.process.task.application.LocalAddSignTaskAccessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 流程进度运行时服务
 * 分别提供首屏实体/表单与流程图/历史两条读取链路，避免查看业务数据时遍历完整历史。
 * 两条入口均由 ProcessInstanceService 校验实例读取范围，互不加载另一条链路的展示负载。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessProgressRuntimeService {
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final TaskService taskService;
    private final SysUserService sysUserService;
    private final EntityDataDynamicService entityDataDynamicService;
    private final EntityFormRuntimeService entityFormRuntimeService;
    private final EntityDefinitionMapper entityDefinitionMapper;
    private final ProcessTaskMapper processTaskMapper;
    private final SysGroupMapper sysGroupMapper;
    private final SysUserGroupMapper sysUserGroupMapper;
    private final SysUserMapper sysUserMapper;
    private final ProcessOperationLogMapper operationLogMapper;
    private final ProcessPublishedSnapshotService processPublishedSnapshotService;
    private final LocalAddSignTaskAccessService localAddSignTaskAccessService;
    private final EntityStatusService entityStatusService;
    private final PublishedBpmnReader publishedBpmnReader;
    /** Flowable 公开 Query.orderBy 扩展使用固定引擎列；不接受请求提供的列名或排序表达式。 */
    private static final org.flowable.common.engine.api.query.QueryProperty HISTORY_TRANSACTION_ORDER =
            () -> "TRANSACTION_ORDER_";
    private static final com.fasterxml.jackson.databind.ObjectMapper RETURN_LOG_JSON =
            new com.fasterxml.jackson.databind.ObjectMapper();
    /** 日期时间格式化器（用于操作日志时间格式化） */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 将日期格式化为 "yyyy-MM-dd HH:mm:ss" 字符串。
     *
     * @param date 日期，为 null 时返回 null
     * @return 格式化后的字符串
     */
    private String formatDate(java.util.Date date) {
        if (date == null) {
            return null;
        }
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }

    /**
     * 获取流程进度视图。
     * <p>
     * 步骤：获取流程实例与状态 -> 解析流程定义与 BPMN XML -> 提取已完成节点/已执行连线/当前活动节点 ->
     * 识别真实取消的执行记录 -> 构建节点审批历史（含转办、终止、撤回记录合并）-> 组装当前任务与处理人映射。
     *
     * @param processInstanceId 流程实例ID
     * @return 流程进度视图对象
     */
    public ProcessProgressDTO getProcessProgress(String processInstanceId) {
        ProcessProgressDTO progress = new ProcessProgressDTO();
        progress.setProcessInstanceId(processInstanceId);
        // 1. 获取流程实例信息
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        final String startUserId;
        HistoricProcessInstance historicInstance = null;
        if (processInstance != null) {
            // 流程正在运行中
            progress.setProcessDefinitionId(processInstance.getProcessDefinitionId());
            progress.setStatus("RUNNING");
            startUserId = processInstance.getStartUserId();
        } else {
            // 流程已结束或不存在，查询历史记录判断是否为终止
            historicInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (historicInstance != null) {
                // 已结束实例的定义 ID 以历史实例为准，避免无活动历史时丢失版本诊断信息。
                progress.setProcessDefinitionId(
                        historicInstance.getProcessDefinitionId());
            }
            startUserId = historicInstance != null ? historicInstance.getStartUserId() : null;
            if (historicInstance == null || historicInstance.getEndTime() == null) {
                throw new IllegalArgumentException("流程实例不存在或历史状态不完整: " + processInstanceId);
            }
            progress.setStatus("COMPLETED");
        }
        // 2. 获取流程定义信息
        String processDefinitionId = progress.getProcessDefinitionId();
        if (processDefinitionId == null) {
            // 从历史记录中获取流程定义ID
            HistoricActivityInstance historicActivity = historyService.createHistoricActivityInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .orderByHistoricActivityInstanceStartTime().asc()
                    .list().stream().findFirst().orElse(null);
            if (historicActivity != null) {
                processDefinitionId = historicActivity.getProcessDefinitionId();
                progress.setProcessDefinitionId(processDefinitionId);
            }
        }
        if (processDefinitionId != null) {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId)
                    .singleResult();
            if (processDefinition != null) {
                progress.setProcessKey(processDefinition.getKey());
                progress.setProcessVersion(processDefinition.getVersion());
                progress.setBpmnXml(publishedBpmnReader.read(processDefinition));
                progress.setProcessName(
                        StringUtils.hasText(processDefinition.getName())
                                ? processDefinition.getName()
                                : processDefinition.getKey());
            }
        }
        // 3. 获取历史活动记录
        List<HistoricActivityInstance> historicActivities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();
        var returnActions = new java.util.HashMap<String, com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog>();
        var reworkActivities = new java.util.HashSet<String>();
        readReturnHistory(processInstanceId, returnActions, reworkActivities);
        // 4. 提取已完成的节点
        List<String> completedNodes = historicActivities.stream()
                .filter(h -> h.getEndTime() != null && !ProcessEndReason.isCancelled(h.getDeleteReason())
                        && !returnActions.containsKey(h.getTaskId())
                        && !reworkActivities.contains(h.getId()))
                .map(HistoricActivityInstance::getActivityId)
                .distinct()
                .collect(Collectors.toList());
        progress.setCompletedNodes(completedNodes);
        // 5. 提取已执行的连线
        List<String> executedFlows = historicActivities.stream()
                .filter(h -> "sequenceFlow".equals(h.getActivityType()))
                .map(HistoricActivityInstance::getActivityId)
                .distinct()
                .collect(Collectors.toList());
        progress.setExecutedSequenceFlows(executedFlows);
        // 6. 获取当前活动节点
        if (processInstance != null) {
            List<Execution> executions = runtimeService.createExecutionQuery()
                    .processInstanceId(processInstanceId)
                    .list();
            List<String> activeNodes = executions.stream()
                    .filter(e -> e.getActivityId() != null)
                    .map(Execution::getActivityId)
                    .distinct()
                    .collect(Collectors.toList());
            progress.setActiveNodes(activeNodes);
        } else {
            progress.setActiveNodes(new ArrayList<>());
        }
        // 引擎为每次活动执行保存取消原因；同一节点循环执行时，以最新一轮判断图上状态。
        // 不再以结束时间接近推断取消，避免将刚审批完成的节点误标为终止。
        Map<String, HistoricActivityInstance> latestActivities = new java.util.LinkedHashMap<>();
        historicActivities.forEach(activity -> latestActivities.put(activity.getActivityId(), activity));
        List<String> cancelledNodes = latestActivities.values().stream()
                .filter(activity -> ProcessEndReason.isCancelled(activity.getDeleteReason())
                        && !returnActions.containsKey(activity.getTaskId()))
                .map(HistoricActivityInstance::getActivityId).distinct().collect(Collectors.toList());
        progress.setCancelledNodes(cancelledNodes);
        progress.setTerminatedNodes(cancelledNodes); // 旧客户端继续识别取消节点；新客户端根据 endType 展示原因。
        if (historicInstance != null && historicInstance.getEndTime() != null) {
            progress.setEndType(new com.workflow.process.status.application.ProcessEntityStatusPolicy(repositoryService).endCategory(historicInstance));
            progress.setEndReason(ProcessEndReason.comment(historicInstance.getDeleteReason()));
        }
        // 历史变量、任务和评论按实例批量读取，节点循环仅做内存索引查找。
        ProcessProgressReadBatch batch = new ProcessProgressReadBatch(processInstanceId, historyService,
                taskService, processTaskMapper, sysGroupMapper, sysUserGroupMapper);
        // 7. 构建节点历史记录
        List<ProcessProgressDTO.NodeHistoryDTO> nodeHistory = historicActivities.stream()
                .filter(h -> !"sequenceFlow".equals(h.getActivityType())) // 排除连线
                .map(h -> {
                    ProcessProgressDTO.NodeHistoryDTO dto = new ProcessProgressDTO.NodeHistoryDTO();
                    String activityId = h.getActivityId();
                    dto.setNodeId(activityId);
                    String activityType = h.getActivityType();
                    dto.setNodeType(activityType);
                    String nodeName = h.getActivityName();
                    String assigneeId = h.getAssignee();
                    // 开始/结束事件特殊处理：补全名称和发起人
                    if ("startEvent".equals(activityType)) {
                        if (!StringUtils.hasText(nodeName) || activityId.equals(nodeName)) {
                            nodeName = "开始";
                        }
                        if (!StringUtils.hasText(assigneeId) && StringUtils.hasText(startUserId)) {
                            assigneeId = startUserId;
                        }
                    } else if ("endEvent".equals(activityType)) {
                        if (!StringUtils.hasText(nodeName) || activityId.equals(nodeName)) {
                            nodeName = "结束";
                        }
                    }
                    dto.setNodeName(nodeName);
                    dto.setAssignee(assigneeId);
                    dto.setStartTime(h.getStartTime() != null ? formatDate(h.getStartTime()) : null);
                    dto.setEndTime(h.getEndTime() != null ? formatDate(h.getEndTime()) : null);
                    dto.setDuration(h.getDurationInMillis());
                    boolean cancelled = ProcessEndReason.isCancelled(h.getDeleteReason());
                    dto.setStatus(cancelled ? "CANCELLED" : h.getEndTime() != null ? "COMPLETED" : "ACTIVE");
                    if (cancelled) {
                        dto.setAction("CANCELLED");
                        dto.setActionLabel("已取消");
                        dto.setComment(ProcessEndReason.comment(h.getDeleteReason()));
                    }
                    // 优先任务局部变量，无任务变量时才回退到同一执行实例。
                    var nodeVars = batch.nodeVariables(h.getTaskId(), h.getExecutionId());
                    if (nodeVars != null && !nodeVars.isEmpty()) {
                        java.util.Map<String, Object> vars = new java.util.HashMap<>();
                        for (var v : nodeVars)
                            vars.put(v.getVariableName(), v.getValue());
                        WorkflowReservedVariables.removeInternalVariables(vars);
                        dto.setVariables(vars);
                    }
                    // 获取任务处理方式
                    if (!cancelled && h.getEndTime() != null && "userTask".equals(h.getActivityType())) {
                        // 查询任务评论判断处理方式
                        try {
                            String commentMsg = batch.latestComment(h.getTaskId());
                            if (commentMsg != null && commentMsg.contains("转办给:")) {
                                dto.setAction("TRANSFERRED");
                            } else {
                                // 优先从本地 process_task 表获取每个任务的实际 action（最准确）
                                String action = null;
                                String actionLabel = null;
                                var localTask = batch.localTasks.get(h.getTaskId());
                                if (localTask != null && localTask.getAction() != null) {
                                    action = localTask.getAction();
                                    actionLabel = localTask.getActionLabel();
                                    if (localTask.getComment() != null) {
                                        dto.setComment(localTask.getComment());
                                    }
                                } else {
                                    action = batch.taskValue(h.getTaskId(), "action");
                                }
                                if (actionLabel == null) actionLabel = batch.actionLabel(h.getTaskId());
                                dto.setAction(normalizeAction(action));
                                dto.setActionLabel(actionLabel);
                            }
                        } catch (Exception e) {
                            // 历史读取失败只确认任务已结束，不能凭兜底结果声称审批通过。
                            dto.setAction("COMPLETED");
                        }
                    }
                    var returnAction = returnActions.get(h.getTaskId());
                    if (returnAction != null && "userTask".equals(activityType)) {
                        dto.setAction("RETURNED");
                        dto.setActionLabel("退回");
                        dto.setStatus("COMPLETED");
                        dto.setAssignee(returnAction.getOperatorId());
                        dto.setAssigneeName(returnAction.getOperatorName());
                        dto.setComment(returnAction.getOperationComment());
                    } else if (reworkActivities.contains(h.getId())) {
                        dto.setNeedsRework(true);
                        String label = dto.getActionLabel();
                        if (!StringUtils.hasText(label)) {
                            label = switch (String.valueOf(dto.getAction())) {
                                case "APPROVED" -> "通过";
                                case "REJECTED" -> "驳回";
                                default -> "已办理";
                            };
                        }
                        dto.setActionLabel(label + "（需重办）");
                    }
                    return dto;
                })
                .collect(Collectors.toList());
        // 7.1 合并转办记录到审批历史中
        try {
            List<com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog> transferLogs = operationLogMapper
                    .selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog>()
                                    .eq(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getProcessInstanceId,
                                            processInstanceId)
                                    .eq(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getOperationType,
                                            "TRANSFER")
                                    .orderByAsc(
                                            com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getOperationTime));
            for (com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog log : transferLogs) {
                // 通过 taskId 查找节点信息
                String nodeId = null;
                String nodeName = null;
                if (log.getTaskId() != null) {
                    var historicTask = batch.historicTaskById.get(log.getTaskId());
                    if (historicTask != null) {
                        nodeId = historicTask.getTaskDefinitionKey();
                        nodeName = historicTask.getName();
                    } else {
                        var task = batch.activeTaskById.get(log.getTaskId());
                        if (task != null) {
                            nodeId = task.getTaskDefinitionKey();
                            nodeName = task.getName();
                        }
                    }
                }
                ProcessProgressDTO.NodeHistoryDTO dto = new ProcessProgressDTO.NodeHistoryDTO();
                dto.setNodeId(nodeId != null ? nodeId : log.getTaskId());
                dto.setNodeName(nodeName != null ? nodeName : "任务转办");
                dto.setNodeType("userTask");
                dto.setAssignee(log.getOperatorId());
                dto.setAssigneeName(log.getOperatorId());
                dto.setAction("TRANSFERRED");
                dto.setComment(ProcessTransferLogFormatter.comment(log));
                String opTime = log.getOperationTime() != null ? log.getOperationTime().format(DATE_FORMATTER) : null;
                dto.setStartTime(opTime);
                dto.setEndTime(opTime);
                dto.setStatus("COMPLETED");
                // 插入到对应节点的最终完成记录之前
                int insertIndex = -1;
                for (int i = 0; i < nodeHistory.size(); i++) {
                    ProcessProgressDTO.NodeHistoryDTO item = nodeHistory.get(i);
                    if (nodeId != null && nodeId.equals(item.getNodeId()) && "COMPLETED".equals(item.getStatus())) {
                        insertIndex = i;
                        break;
                    }
                }
                if (insertIndex >= 0) {
                    nodeHistory.add(insertIndex, dto);
                } else {
                    nodeHistory.add(dto);
                }
            }
        } catch (Exception e) {
            log.warn("合并转办记录失败", e);
        }
        // 7.2 两种结束操作都保留独立时间线，不能由实体的当前业务状态推断历史动作。
        try {
            List<com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog> terminateLogs = operationLogMapper
                    .selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog>()
                                    .eq(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getProcessInstanceId,
                                            processInstanceId)
                                    .in(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getOperationType,
                                            "TERMINATE", "WITHDRAW")
                                    .orderByAsc(
                                            com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getOperationTime));
            for (com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog log : terminateLogs) {
                ProcessProgressDTO.NodeHistoryDTO dto = new ProcessProgressDTO.NodeHistoryDTO();
                boolean withdrawn = "WITHDRAW".equals(log.getOperationType());
                String endType = withdrawn ? "WITHDRAWN" : "TERMINATED";
                dto.setNodeId(log.getOperationType() + "_" + log.getId());
                dto.setNodeName(withdrawn ? "流程撤回" : "流程终止");
                dto.setNodeType(withdrawn ? "withdraw" : "terminate");
                dto.setAssignee(log.getOperatorId());
                dto.setAssigneeName(log.getOperatorId());
                dto.setAction(endType);
                dto.setComment(log.getOperationComment());
                String opTime = log.getOperationTime() != null ? log.getOperationTime().format(DATE_FORMATTER) : null;
                dto.setStartTime(opTime);
                dto.setEndTime(opTime);
                dto.setStatus(endType);
                nodeHistory.add(dto);
            }
        } catch (Exception e) {
            log.warn("合并流程结束操作记录失败", e);
        }
        // 存量撤回没有独立操作日志时，保留可确认的结束事实；未知操作者不猜成发起人。
        if (progress.getEndType() != null && !"COMPLETED".equals(progress.getEndType())
                && nodeHistory.stream().noneMatch(item -> progress.getEndType().equals(item.getAction()))) {
            var end = new ProcessProgressDTO.NodeHistoryDTO();
            boolean withdrawn = "WITHDRAWN".equals(progress.getEndType());
            end.setNodeId("PROCESS_END_" + processInstanceId);
            end.setNodeName(withdrawn ? "流程撤回" : "流程终止");
            end.setNodeType(withdrawn ? "withdraw" : "terminate");
            end.setAction(progress.getEndType());
            end.setStatus(progress.getEndType());
            end.setComment(progress.getEndReason());
            end.setEndTime(formatDate(historicInstance.getEndTime()));
            nodeHistory.add(end);
        }
        progress.setNodeHistory(nodeHistory);
        // 8. 获取当前任务信息
        if (processInstance != null) {
            List<Task> tasks = batch.activeTasks;
            List<ProcessProgressDTO.TaskInfoDTO> taskInfos = tasks.stream()
                    .map(t -> {
                        ProcessProgressDTO.TaskInfoDTO dto = new ProcessProgressDTO.TaskInfoDTO();
                        dto.setTaskId(t.getId());
                        dto.setTaskName(t.getName());
                        dto.setNodeId(t.getTaskDefinitionKey());
                        dto.setAssignee(t.getAssignee());
                        dto.setAssigneeName(t.getAssignee());
                        dto.setCreateTime(t.getCreateTime() != null ? formatDate(t.getCreateTime()) : null);
                        return dto;
                    })
                    .collect(Collectors.toList());
            progress.setTasks(taskInfos);
        }
        // 9. 构建节点处理人映射（用于前端悬停显示）
        buildNodeAssigneeMap(progress, batch);
        batch.fillDisplayNames(progress, sysUserService);
        return progress;
    }

    /**
     * 构建节点处理人映射
     * 包含已完成节点的审批人信息和当前节点的处理人信息
     *
     * @param progress 进度，供本方法构建节点办理人映射时使用
     * @param batch 本次请求已读取的任务和历史数据，不跨请求复用
     */
    private void buildNodeAssigneeMap(ProcessProgressDTO progress, ProcessProgressReadBatch batch) {
        Map<String, ProcessProgressDTO.AssigneeInfoDTO> assigneeMap = new HashMap<>();
        Map<String, List<ProcessProgressDTO.AssigneeInfoDTO>> assigneesMap = new HashMap<>();
        // 1. 查询历史任务（已完成的任务）
        List<HistoricTaskInstance> historicTasks = batch.historicTasks.stream()
                .filter(task -> task.getEndTime() != null).toList();
        for (HistoricTaskInstance task : historicTasks) {
            String nodeId = task.getTaskDefinitionKey();
            ProcessProgressDTO.AssigneeInfoDTO info = new ProcessProgressDTO.AssigneeInfoDTO();
            String userId = task.getAssignee();
            String displayName = userId;
            info.setAssigneeId(userId);
            info.setAssigneeName(displayName);
            info.setHandleTime(task.getEndTime() != null ? formatDate(task.getEndTime()) : null);
            info.setStatus("COMPLETED");
            // 从流程变量/本地待办中读取处理方式与显示文本
            String action = null;
            String actionLabel = null;
            String comment = null;
            var localTask = batch.localTasks.get(task.getId());
            if (localTask != null) {
                action = localTask.getAction();
                actionLabel = localTask.getActionLabel();
                comment = localTask.getComment();
            }
            if (action == null) action = batch.taskValue(task.getId(), "action");
            if (actionLabel == null) actionLabel = batch.actionLabel(task.getId());
            info.setAction(normalizeAction(action));
            info.setActionLabel(actionLabel);
            info.setComment(comment);
            if (ProcessEndReason.isCancelled(task.getDeleteReason())) {
                info.setStatus("CANCELLED");
                info.setAction("CANCELLED");
                info.setActionLabel("已取消");
                info.setComment(ProcessEndReason.comment(task.getDeleteReason()));
            }
            // 单节点处理人映射：保留最新的
            if (!assigneeMap.containsKey(nodeId) ||
                    (task.getEndTime() != null &&
                            (assigneeMap.get(nodeId).getHandleTime() == null ||
                                    formatDate(task.getEndTime())
                                            .compareTo(assigneeMap.get(nodeId).getHandleTime()) > 0))) {
                assigneeMap.put(nodeId, info);
            }
            // 多实例节点处理人列表：保留所有子任务
            assigneesMap.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(info);
        }
        // 2. 查询当前活动任务
        List<Task> activeTasks = batch.activeTasks;
        for (Task task : activeTasks) {
            String nodeId = task.getTaskDefinitionKey();
            ProcessProgressDTO.AssigneeInfoDTO info = new ProcessProgressDTO.AssigneeInfoDTO();
            String userId = task.getAssignee();
            if (userId == null || userId.isEmpty()) {
                batch.prepareCandidateNames(task, info);
            } else {
                String displayName = userId;
                info.setAssigneeId(userId);
                info.setAssigneeName(displayName);
            }
            info.setHandleTime(task.getCreateTime() != null ? formatDate(task.getCreateTime()) : null);
            info.setStatus("PROCESSING");
            info.setAction("PROCESSING"); // 处理中
            info.setComment("待处理");
            assigneeMap.put(nodeId, info);
            assigneesMap.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(info);
        }
        progress.setNodeAssigneeMap(assigneeMap);
        progress.setNodeAssigneesMap(assigneesMap);
    }

    /**
     * 读取查看/审批首屏所需的表单及实体。调用方须先校验实例可读性；不会加载评论、
     * 历史变量全集、办理人历史或流程轮次。指定任务必须仍属于该实例，加签另行校验。
     *
     * @param processInstanceId 已鉴权的流程实例
     * @param requestedTaskId 办理目标，可为空；结束实例不能声明活动任务
     * @return 单个生效表单、业务数据及真实任务坐标
     */
    public ProcessFormRuntimeDTO getProcessFormRuntime(String processInstanceId, String requestedTaskId) {
        ProcessFormRuntimeDTO result = new ProcessFormRuntimeDTO();
        result.setProcessInstanceId(processInstanceId);
        ProcessInstance running = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId).singleResult();
        if (running != null) {
            result.setStatus("RUNNING");
            result.setProcessDefinitionId(running.getProcessDefinitionId());
        } else {
            HistoricProcessInstance historic = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId).singleResult();
            if (historic == null || historic.getEndTime() == null) {
                throw new IllegalArgumentException("流程实例不存在或历史状态不完整: " + processInstanceId);
            }
            result.setStatus("COMPLETED");
            result.setProcessDefinitionId(historic.getProcessDefinitionId());
            if (StringUtils.hasText(requestedTaskId)) throw new IllegalArgumentException("任务不存在、已处理或不属于该流程实例: " + requestedTaskId);
        }
        ProcessDefinition definition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionId(result.getProcessDefinitionId()).singleResult();
        String bpmnXml = null;
        if (definition != null) {
            result.setProcessKey(definition.getKey());
            result.setProcessVersion(definition.getVersion());
            result.setProcessName(StringUtils.hasText(definition.getName()) ? definition.getName() : definition.getKey());
            // BPMN 仅在服务端解析审批配置，不把完整图形文档传回首屏。
            bpmnXml = publishedBpmnReader.read(definition);
        }
        List<ProcessProgressDTO.TaskInfoDTO> taskInfos = new ArrayList<>();
        if (running != null) {
            for (Task task : taskService.createTaskQuery().processInstanceId(processInstanceId).list()) {
                ProcessProgressDTO.TaskInfoDTO info = new ProcessProgressDTO.TaskInfoDTO();
                info.setTaskId(task.getId()); info.setTaskName(task.getName());
                info.setNodeId(task.getTaskDefinitionKey()); info.setAssignee(task.getAssignee());
                info.setAssigneeName(task.getAssignee()); info.setCreateTime(formatDate(task.getCreateTime()));
                taskInfos.add(info);
            }
            if (StringUtils.hasText(requestedTaskId) && taskInfos.stream().noneMatch(t -> requestedTaskId.equals(t.getTaskId()))) {
                if (!requestedTaskId.startsWith("addsign-")) throw new IllegalArgumentException("任务不存在、已处理或不属于该流程实例: " + requestedTaskId);
                var authorized = localAddSignTaskAccessService.requireCurrentUserAccess(requestedTaskId, processInstanceId);
                var local = authorized.localTask();
                ProcessProgressDTO.TaskInfoDTO info = new ProcessProgressDTO.TaskInfoDTO();
                info.setTaskId(local.getTaskId()); info.setTaskName(local.getNodeName());
                info.setNodeId(authorized.sourceTask().getTaskDefinitionKey()); info.setAssignee(local.getAssigneeId());
                info.setCreateTime(local.getCreateTime() == null ? null : local.getCreateTime().format(DATE_FORMATTER));
                taskInfos.add(info);
            }
        }
        result.setTasks(taskInfos);
        String nodeId = taskInfos.stream().filter(t -> !StringUtils.hasText(requestedTaskId) || requestedTaskId.equals(t.getTaskId()))
                .map(ProcessProgressDTO.TaskInfoDTO::getNodeId).filter(StringUtils::hasText).findFirst().orElse(null);
        if (nodeId == null) {
            if (running != null) {
                nodeId = runtimeService.createExecutionQuery().processInstanceId(processInstanceId).list().stream()
                        .map(Execution::getActivityId).filter(StringUtils::hasText).findFirst().orElse(null);
            } else {
                // 直接查询最后完成的用户活动，自动跳过的用户任务同样保留绑定身份；网关不参与表单选择。
                nodeId = historyService.createHistoricActivityInstanceQuery().processInstanceId(processInstanceId)
                        .activityType("userTask").finished().orderByHistoricActivityInstanceEndTime().desc()
                        // 同一事务中的跳过节点可能与前一任务同时结束，按开始时间及引擎事务顺序继续区分。
                        .orderByHistoricActivityInstanceStartTime().desc()
                        .orderBy(HISTORY_TRANSACTION_ORDER).desc()
                        .listPage(0, 1).stream().map(HistoricActivityInstance::getActivityId).findFirst().orElse(null);
            }
        }
        Map<String, Object> variables;
        List<String> names = List.of("entityCode", "entityDataId");
        if (running != null) {
            variables = runtimeService.getVariables(processInstanceId, names);
        } else {
            variables = new HashMap<>();
            // 同名任务局部变量不能覆盖实例根坐标，也无需反序列化其他业务变量。
            for (String name : names) {
                var variable = historyService.createHistoricVariableInstanceQuery().processInstanceId(processInstanceId)
                        .excludeLocalVariables().variableName(name).singleResult();
                if (variable != null) variables.put(name, variable.getValue());
            }
        }
        String entityCode = (String) variables.get("entityCode");
        String recordId = (String) variables.get("entityDataId");
        if (StringUtils.hasText(entityCode) && StringUtils.hasText(recordId)) {
            EntityDataDTO entity = entityDataDynamicService.findById(entityCode, recordId);
            result.setEntityData(toRuntimeFormData(entity));
            if (StringUtils.hasText(entity.getStatus())) putIfNotNull(result.getEntityData(), "_statusText",
                    entityStatusService.getStatusNameMap(entityCode).get(entity.getStatus()));
            loadFormConfig(result, entityCode, recordId, nodeId, requestedTaskId);
        }
        if (nodeId != null) loadApprovalConfig(result, nodeId, bpmnXml);
        return result;
    }

    /**
     * 将实体数据 DTO 转为运行时表单模型，自定义字段与系统字段使用同一层级。
     *
     * @param entityData 实体数据，作为 {@code result.putAll} 的输入影响后续处理
     * @return 运行时表单数据键值结果，供调用方继续处理
     */
    private Map<String, Object> toRuntimeFormData(EntityDataDTO entityData) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (entityData.getData() != null) {
            result.putAll(entityData.getData());
        }
        putIfNotNull(result, "id", entityData.getId());
        putIfNotNull(result, "entityCode", entityData.getEntityCode());
        putIfNotNull(result, "entityName", entityData.getEntityName());
        putIfNotNull(result, "name", entityData.getName());
        putIfNotNull(result, "code", entityData.getCode());
        putIfNotNull(result, "status", entityData.getStatus());
        // 表单标题展示 biz 表的当前生命周期；form-runtime.status 仍描述所查看的流程实例。
        putIfNotNull(result, "processStatus", entityData.getProcessStatus());
        putIfNotNull(
                result,
                "processInstanceId",
                entityData.getProcessInstanceId());
        putIfNotNull(
                result,
                "processStartTime",
                entityData.getProcessStartTime());
        putIfNotNull(
                result,
                "processEndTime",
                entityData.getProcessEndTime());
        putIfNotNull(result, "currentTaskId", entityData.getCurrentTaskId());
        putIfNotNull(result, "currentTaskName", entityData.getCurrentTaskName());
        putIfNotNull(
                result,
                "currentTaskAssignee",
                entityData.getCurrentTaskAssignee());
        putIfNotNull(result, "submitterId", entityData.getSubmitterId());
        putIfNotNull(result, "submitterName", entityData.getSubmitterName());
        putIfNotNull(result, "deptId", entityData.getDeptId());
        putIfNotNull(result, "deptName", entityData.getDeptName());
        putIfNotNull(result, "submitTime", entityData.getSubmitTime());
        putIfNotNull(result, "create_time", entityData.getCreateTime());
        putIfNotNull(result, "update_time", entityData.getUpdateTime());
        putIfNotNull(result, "create_by", entityData.getCreateBy());
        putIfNotNull(result, "update_by", entityData.getUpdateBy());
        return result;
    }

    /**
     * 写入条件非空值；后续读取或执行将使用更新后的状态。
     *
     * @param target 目标，供本方法写入条件非空值时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param value 待写入条件非空值的原始输入，结果供调用方继续使用
     */
    private void putIfNotNull(
            Map<String, Object> target,
            String key,
            Object value) {
        if (value != null) {
            target.put(key, value);
        }
    }

    /**
     * 按实例固定发布版本解析当前节点的单个表单；无节点绑定时使用受流程上下文约束的默认表单。
     * @param progress 首屏结果，接收生效表单及后续请求需要重新鉴权的定位信息
     * @param entityCode 根业务实体编码
     * @param entityDataId 根业务记录 ID
     * @param currentNodeId 活动任务节点或结束实例最后完成的用户活动
     * @param requestedTaskId 指定办理任务，可为空；存在时由表单解析器重新校验办理权限
     * @throws FormConfigResolutionException 发布配置缺失或解析失败，首屏失败关闭而不返回不完整表单
     */
    private void loadFormConfig(ProcessFormRuntimeDTO progress, String entityCode, String entityDataId,
            String currentNodeId, String requestedTaskId) {
        try {
            // 1. 获取实体定义
            com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition entityDef = entityDefinitionMapper
                    .findByEntityCode(entityCode).orElse(null);
            if (entityDef == null) {
                log.warn("加载表单配置失败: 实体不存在, entityCode={}", entityCode);
                return;
            }
            // 2. 确定要加载表单的节点ID
            String targetNodeId = currentNodeId;
            // 3. 加载表单详情（优先级：流程发布快照 > 默认）
            List<ProcessFormRuntimeDTO.FormConfigDTO> formConfigs = new ArrayList<>();
            ProcessPublishedSnapshotService.PublishedNodeForms published = null;
            // 3a. 最高优先级：从流程发布快照查询节点表单绑定
            if (progress.getProcessDefinitionId() != null
                    && !progress.getProcessDefinitionId().isEmpty()
                    && targetNodeId != null
                    && !targetNodeId.isEmpty()) {
                published = processPublishedSnapshotService
                        .getNodeFormsContextByProcessDefinitionId(
                                progress.getProcessDefinitionId(),
                                targetNodeId);
                UiRuntimePurpose purpose = "RUNNING".equals(progress.getStatus())
                        ? UiRuntimePurpose.ACTIVE_TASK
                        : UiRuntimePurpose.HISTORICAL;
                for (com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm nodeForm : published
                        .nodeForms()) {
                    if (nodeForm.getFormId() == null || nodeForm.getFormId().isEmpty()) {
                        continue;
                    }
                    com.workflow.entity.form.infrastructure.persistence.record.EntityForm entityForm = entityFormRuntimeService
                            .getByBinding(
                                    nodeForm,
                                    UiRuntimePurpose.ACTIVE_TASK.equals(purpose)
                                            && StringUtils.hasText(requestedTaskId)
                                            ? com.workflow.port.entity.context.ui.UiRuntimeResolutionContext.activeTask(
                                                            published.history().getId(),
                                                            nodeForm.getNodeId(),
                                                            requestedTaskId,
                                                            progress.getProcessInstanceId(),
                                                            entityCode,
                                                            entityDataId)
                                            : new com.workflow.port.entity.context.ui.UiRuntimeResolutionContext(
                                                            UiRuntimePurpose.HISTORICAL,
                                                            published.history().getId(),
                                                            nodeForm.getNodeId(), null, progress.getProcessInstanceId(), entityCode, entityDataId));
                    if (entityForm == null) {
                        throw new IllegalStateException(
                                "流程发布快照绑定的表单不存在: formId="
                                        + nodeForm.getFormId());
                    }
                    Boolean nodeFormReadonly = Integer.valueOf(1).equals(nodeForm.getIsReadonly()) ? Boolean.TRUE
                            : null;
                    formConfigs.add(buildProgressFormConfig(
                            entityForm,
                            nodeFormReadonly));
                    log.info("从流程发布快照查询到节点表单: nodeId={}, formId={}, formName={}",
                            targetNodeId, nodeForm.getFormId(), entityForm.getFormName());
                    break;
                }
            }
            // 3b. 映射表中没有表单绑定，使用默认表单兜底。
            if (formConfigs.isEmpty()) {
                // 默认表单同样返回任务或实例定位信息，后续请求仍按真实流程身份重新鉴权。
                com.workflow.port.entity.context.ui.UiRuntimeResolutionContext defaultFormContext = null;
                if (published != null) {
                    String historyId = published.history().getId();
                    defaultFormContext = "RUNNING".equals(progress.getStatus())
                            && StringUtils.hasText(requestedTaskId)
                            ? com.workflow.port.entity.context.ui.UiRuntimeResolutionContext.activeTask(
                                    historyId,
                                    targetNodeId,
                                    requestedTaskId,
                                    progress.getProcessInstanceId(),
                                    entityCode,
                                    entityDataId)
                            : new com.workflow.port.entity.context.ui.UiRuntimeResolutionContext(
                                    UiRuntimePurpose.HISTORICAL, historyId, targetNodeId, null,
                                    progress.getProcessInstanceId(), entityCode, entityDataId);
                }
                com.workflow.entity.form.infrastructure.persistence.record.EntityForm entityForm = entityFormRuntimeService
                        .getDefaultForm(entityDef.getId(), defaultFormContext);
                if (entityForm != null) {
                    formConfigs.add(buildProgressFormConfig(entityForm, null));
                    log.debug(
                            "节点未配置表单，回退到默认表单: status={}, nodeId={}, formId={}",
                            progress.getStatus(),
                            targetNodeId,
                            entityForm.getId());
                }
            }
            if (formConfigs.isEmpty()) {
                log.debug("节点未绑定可用表单: entityId={}, nodeId={}", entityDef.getId(), targetNodeId);
                return;
            }

            ProcessFormRuntimeDTO.FormConfigDTO formConfig = formConfigs.get(0);
            progress.setFormConfig(formConfig);
            log.debug("表单配置加载成功: entityCode={}, formKey={}, fieldsCount={}",
                    entityCode, formConfig.getFormKey(),
                    formConfigs.get(0).getFields() != null ? formConfigs.get(0).getFields().size() : 0);
        } catch (Exception e) {
            log.error(
                    "加载流程发布表单配置异常: processDefinitionId={}, nodeId={}",
                    progress.getProcessDefinitionId(),
                    currentNodeId,
                    e);
            throw new FormConfigResolutionException(
                    "加载流程发布表单失败: processDefinitionId="
                            + progress.getProcessDefinitionId()
                            + ", nodeId="
                            + currentNodeId,
                    e);
        }
    }

    /**
     * 负责表单配置解析的业务处理；协调校验、状态变化及后续结果传递。
     */
    private static final class FormConfigResolutionException
            extends RuntimeException {
        /**
         * 初始化表单配置解析异常，保存构造参数供后续方法使用。
         *
         * @param message 消息，保存在对象中供后续校验、查询或展示
         * @param cause 原因，保存在对象中供后续校验、查询或展示
         */
        private FormConfigResolutionException(
                String message,
                Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 根据已解析的实体表单构建进度表单配置 DTO。
     * <p>
     * 合并表单字段、布局节点、节点只读设置与当前运行时发布信息。
     *
     * @param entityForm       实体表单
     * @param readonlyOverride 节点级只读覆盖，为 null 表示不强制只读
     * @return 进度表单配置 DTO
     */
    private ProcessFormRuntimeDTO.FormConfigDTO buildProgressFormConfig(
            com.workflow.entity.form.infrastructure.persistence.record.EntityForm entityForm,
            Boolean readonlyOverride) {
        ProcessFormRuntimeDTO.FormConfigDTO formConfig = new ProcessFormRuntimeDTO.FormConfigDTO();
        formConfig.setFormId(entityForm.getId());
        formConfig.setFormName(entityForm.getFormName());
        formConfig.setFormKey(entityForm.getFormKey());
        formConfig.setLayoutType(entityForm.getLayoutType());
        formConfig.setIsReadonly(Boolean.TRUE.equals(readonlyOverride));
        formConfig.setCustomComponent(entityForm.getCustomComponent());
        formConfig.setViewConfig(entityForm.getViewConfig());
        formConfig.setConfigHash(entityForm.getConfigHash());
        formConfig.setRuntimeContext(entityForm.getRuntimeContext());
        formConfig.setContextValues(entityForm.getContextValues());
        formConfig.setDataSourceBindingsDocument(
                entityForm.getDataSourceBindingsDocument());
        if (entityForm.getFields() != null) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> fields = new ArrayList<>();
            for (com.workflow.entity.form.infrastructure.persistence.record.EntityFormField field : entityForm
                    .getFields()) {
                fields.add(EntityFormFieldRuntimeMapper.toMap(field, readonlyOverride, mapper));
            }
            formConfig.setFields(fields);
        }
        if (entityForm.getNodes() != null) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            formConfig.setNodes(mapper.convertValue(
                    entityForm.getNodes(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {
                    }));
        }
        return formConfig;
    }

    /**
     * 加载审批配置。运行实例只使用其部署 BPMN 或对应发布历史快照；
     * 快照缺失时保持旧版默认，不得回退到当前映射表。
     *
     * @param progress 进度，作为 {@code loadApprovalConfigFromBpmn} 的输入影响后续处理
     * @param currentNodeId 当前节点ID，后续用于加载审批配置时定位或关联目标
     * @param bpmnXml BPMNXML，作为 {@code loadApprovalConfigFromBpmn} 的输入影响后续处理
     */
    private void loadApprovalConfig(
            ProcessFormRuntimeDTO progress,
            String currentNodeId,
            String bpmnXml) {
        if (currentNodeId == null || currentNodeId.isEmpty()) {
            return;
        }
        try {
            if (StringUtils.hasText(bpmnXml)) {
                loadApprovalConfigFromBpmn(
                        progress, currentNodeId, bpmnXml);
                return;
            }
            log.debug(
                    "部署 BPMN 与发布快照均无审批配置，保持旧版默认: nodeId={}",
                    currentNodeId);
        } catch (Exception e) {
            log.warn("加载审批配置失败: {}", e.getMessage());
        }
    }

    /**
     * 从该实例绑定的部署/发布 BPMN XML 解析审批配置。
     *
     * @param progress 进度，供本方法加载审批配置起始BPMN时使用
     * @param currentNodeId 当前节点ID，后续用于加载审批配置起始BPMN时定位或关联目标
     * @param bpmnXml BPMNXML，作为 {@code pattern.matcher} 的输入影响后续处理
     */
    private void loadApprovalConfigFromBpmn(ProcessFormRuntimeDTO progress, String currentNodeId, String bpmnXml) {
        if (bpmnXml == null || currentNodeId == null || currentNodeId.isEmpty()) {
            return;
        }
        try {
            // 匹配当前节点的 userTask 标签内容
            String patternStr = "<(bpmn:)?userTask[^>]*id=\"" + java.util.regex.Pattern.quote(currentNodeId)
                    + "\"[^>]*>(.*?)</(bpmn:)?userTask>";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr,
                    java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher matcher = pattern.matcher(bpmnXml);
            if (!matcher.find()) {
                log.debug("BPMN中未找到当前节点: nodeId={}", currentNodeId);
                return;
            }
            String content = matcher.group(2);
            // 从 flowable:Properties 中解析 approvalConfig
            java.util.regex.Pattern propPattern = java.util.regex.Pattern.compile(
                    "<(?:flowable:|camunda:)?property[^>]*name=\"approvalConfig\"[^>]*value=\"([^\"]*)\"",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
            java.util.regex.Matcher propMatcher = propPattern.matcher(content);
            // 如果失败，尝试 value 在前、name 在后的顺序
            boolean found = propMatcher.find();
            if (!found) {
                propPattern = java.util.regex.Pattern.compile(
                        "<(?:flowable:|camunda:)?property[^>]*value=\"([^\"]*)\"[^>]*name=\"approvalConfig\"",
                        java.util.regex.Pattern.CASE_INSENSITIVE);
                propMatcher = propPattern.matcher(content);
                found = propMatcher.find();
            }
            if (!found) {
                log.debug("BPMN中未找到审批配置: nodeId={}", currentNodeId);
                return;
            }
            String approvalConfigJson = propMatcher.group(1);
            // 处理 XML 命名实体和数字字符引用
            approvalConfigJson = approvalConfigJson.replace("&quot;", "\"")
                    .replace("&#34;", "\"")
                    .replace("&amp;", "&")
                    .replace("&#38;", "&")
                    .replace("&lt;", "<")
                    .replace("&#60;", "<")
                    .replace("&gt;", ">")
                    .replace("&#62;", ">")
                    .replace("&#39;", "'");
            com.fasterxml.jackson.databind.JsonNode config = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readTree(approvalConfigJson);
            ProcessFormRuntimeDTO.ApprovalConfigDTO approvalConfig = new ProcessFormRuntimeDTO.ApprovalConfigDTO();
            approvalConfig.setEnabled(config.has("enabled") ? config.get("enabled").asBoolean() : true);
            approvalConfig.setCommentLabel(config.has("commentLabel") ? config.get("commentLabel").asText() : "审批意见");
            if (config.has("options") && config.get("options").isArray()) {
                List<ProcessFormRuntimeDTO.ApprovalOptionDTO> options = new ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode optNode : config.get("options")) {
                    ProcessFormRuntimeDTO.ApprovalOptionDTO option = new ProcessFormRuntimeDTO.ApprovalOptionDTO();
                    option.setValue(optNode.has("value") ? optNode.get("value").asText() : "");
                    option.setLabel(optNode.has("label") ? optNode.get("label").asText() : "");
                    option.setType(optNode.has("type") ? optNode.get("type").asText() : "primary");
                    option.setShowComment(optNode.has("showComment") ? optNode.get("showComment").asBoolean() : true);
                    options.add(option);
                }
                approvalConfig.setOptions(options);
            }
            progress.setApprovalConfig(approvalConfig);
            log.info("从BPMN加载审批配置成功: nodeId={}, optionsCount={}", currentNodeId,
                    approvalConfig.getOptions() != null ? approvalConfig.getOptions().size() : 0);
        } catch (Exception e) {
            log.warn("从BPMN加载审批配置失败: {}", e.getMessage());
        }
    }

    /** 根据活动实例标记重办，同一个 BPMN 节点后来顺利办结时仍保留旧轮次的历史。 */
    private void readReturnHistory(String processInstanceId,
            Map<String, com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog> actions,
            java.util.Set<String> reworkActivities) {
        var records = operationLogMapper.selectList(com.baomidou.mybatisplus.core.toolkit.Wrappers
                .<com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog>lambdaQuery()
                .eq(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getProcessInstanceId,
                        processInstanceId)
                .eq(com.workflow.process.audit.infrastructure.persistence.record.ProcessOperationLog::getOperationType,
                        com.workflow.process.task.application.TaskHistoryReturnService.OPERATION));
        for (var record : records) {
            if (!com.workflow.process.task.application.TaskHistoryReturnService.OPERATION.equals(record.getOperationType())
                    || !StringUtils.hasText(record.getNewValue())) continue;
            try {
                var change = RETURN_LOG_JSON.readValue(record.getNewValue(),
                        com.workflow.process.task.application.TaskHistoryReturnService.ReturnHistory.class);
                actions.put(record.getTaskId(), record);
                if (change.invalidatedActivityIds() != null) reworkActivities.addAll(change.invalidatedActivityIds());
            } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
                log.warn("无法读取流程回退影响记录", error);
            }
        }
    }

    /**
     * 规范化任务 action 为显示状态码；自定义 action 保留原始值
     *
     * @param action 动作标识，决定后续动作采用的处理分支
     * @return 规范化后的动作文本，供调用方比较或展示
     */
    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "COMPLETED";
        }
        return switch (action.trim().toLowerCase()) {
            case "approve", "approved" -> "APPROVED";
            case "reject", "rejected" -> "REJECTED";
            case "transfer", "transferred" -> "TRANSFERRED";
            default -> action;
        };
    }
}
