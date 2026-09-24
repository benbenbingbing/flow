package com.workflow.process.instance.application;

import com.workflow.process.status.application.ProcessEndReason;
import com.workflow.entity.form.api.response.FormConfigDTO;
import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.process.instance.api.response.ProcessProgressDTO;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.process.audit.infrastructure.persistence.mapper.ProcessOperationLogMapper;
import com.workflow.process.task.infrastructure.persistence.mapper.ProcessTaskMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.process.publish.application.ProcessPublishedSnapshotService;
import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
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
 * 负责组装流程进度视图，包含流程状态、BPMN XML、已完成节点、已执行连线、
 * 当前活动节点、节点审批历史、节点处理人映射、当前任务、实体数据与表单/审批配置，
 * 供前端流程进度图与审批弹窗展示。
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
     * 识别真实取消的执行记录 -> 构建节点审批历史（含转办、终止、撤回记录合并）-> 组装当前任务 -> 构建节点处理人映射 ->
     * 加载实体数据与表单/审批配置。
     *
     * @param processInstanceId 流程实例ID
     * @return 流程进度视图对象
     */
    public ProcessProgressDTO getProcessProgress(String processInstanceId) {
        return getProcessProgress(processInstanceId, null);
    }

    /**
     * 读取流程进度；查询结果供调用方展示或继续处理。
     *
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param requestedTaskId 请求任务ID，后续用于读取流程进度时定位或关联目标
     * @return 符合条件的流程进度结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public ProcessProgressDTO getProcessProgress(
            String processInstanceId,
            String requestedTaskId) {
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
                // 获取 BPMN XML（从 Flowable 获取完整的 XML，包含 DI 图形信息）
                try {
                    org.flowable.engine.repository.Model model = repositoryService.getModel(processDefinition.getId());
                    if (model != null) {
                        byte[] modelBytes = repositoryService.getModelEditorSource(model.getId());
                        if (modelBytes != null) {
                            progress.setBpmnXml(new String(modelBytes, java.nio.charset.StandardCharsets.UTF_8));
                        }
                    }
                } catch (Exception e) {
                    log.debug("无法从 Model 获取 BPMN XML，尝试从资源获取", e);
                }
                // 如果无法从 Model 获取，尝试从部署资源获取
                if (progress.getBpmnXml() == null) {
                    try {
                        String resourceName = processDefinition.getResourceName();
                        if (resourceName != null) {
                            org.flowable.engine.repository.Deployment deployment = repositoryService
                                    .createDeploymentQuery()
                                    .deploymentId(processDefinition.getDeploymentId())
                                    .singleResult();
                            if (deployment != null) {
                                java.io.InputStream resourceStream = repositoryService.getResourceAsStream(
                                        deployment.getId(), resourceName);
                                if (resourceStream != null) {
                                    progress.setBpmnXml(new String(resourceStream.readAllBytes(),
                                            java.nio.charset.StandardCharsets.UTF_8));
                                }
                            }
                        }
                    } catch (Exception e) {
                        log.warn("从 Flowable 获取 BPMN XML 失败", e);
                    }
                }
                // 部署资源缺失时只能回退到该部署 ID 对应的发布历史快照，
                // 绝不能读取当前流程草稿的 BPMN。
                if (!StringUtils.hasText(progress.getBpmnXml())) {
                    try {
                        ProcessVersionHistory publishedVersion =
                                processPublishedSnapshotService
                                        .getVersionByProcessDefinitionId(
                                                processDefinitionId);
                        if (publishedVersion != null
                                && StringUtils.hasText(
                                        publishedVersion.getBpmnXml())) {
                            progress.setBpmnXml(
                                    publishedVersion.getBpmnXml());
                        }
                    } catch (RuntimeException exception) {
                        log.warn(
                                "无法读取流程部署对应的发布 BPMN 快照: processDefinitionId={}",
                                processDefinitionId,
                                exception);
                    }
                }
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
        // 4. 提取已完成的节点
        List<String> completedNodes = historicActivities.stream()
                .filter(h -> h.getEndTime() != null && !ProcessEndReason.isCancelled(h.getDeleteReason()))
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
                .filter(activity -> ProcessEndReason.isCancelled(activity.getDeleteReason()))
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
                dto.setComment(log.getNewValue() != null ? "转办给: " + log.getNewValue() : log.getOperationComment());
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
            if (StringUtils.hasText(requestedTaskId)
                    && taskInfos.stream().noneMatch(item ->
                            requestedTaskId.equals(item.getTaskId()))) {
                if (!requestedTaskId.startsWith("addsign-")) {
                    throw new IllegalArgumentException(
                            "任务不存在、已处理或不属于该流程实例: " + requestedTaskId);
                }
                // 加签子任务不在 Flowable 中；只加入当前授权的请求任务，源节点取自实际引擎任务。
                // 前缀仅用于路由，真正访问权由 ACTIVE/TODO 加签关联及当前办理人共同校验。
                var authorized = localAddSignTaskAccessService
                        .requireCurrentUserAccess(requestedTaskId, processInstanceId);
                var localTask = authorized.localTask();
                ProcessProgressDTO.TaskInfoDTO localInfo = new ProcessProgressDTO.TaskInfoDTO();
                localInfo.setTaskId(localTask.getTaskId());
                localInfo.setTaskName(localTask.getNodeName());
                localInfo.setNodeId(authorized.sourceTask().getTaskDefinitionKey());
                localInfo.setAssignee(localTask.getAssigneeId());
                localInfo.setAssigneeName(localTask.getAssigneeId());
                localInfo.setCreateTime(localTask.getCreateTime() == null
                        ? null : localTask.getCreateTime().format(DATE_FORMATTER));
                taskInfos.add(localInfo);
            }
        } else if (StringUtils.hasText(requestedTaskId)) {
            throw new IllegalArgumentException(
                    "任务不存在、已处理或不属于该流程实例: "
                            + requestedTaskId);
        }
        // 9. 构建节点处理人映射（用于前端悬停显示）
        buildNodeAssigneeMap(progress, batch);
        batch.fillDisplayNames(progress, sysUserService);
        // 10. 获取实体数据和表单配置
        loadEntityDataAndFormConfig(
                progress,
                processInstanceId,
                progress.getProcessKey(),
                requestedTaskId);
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
     * 加载实体数据和表单配置
     *
     * @param progress 进度，作为 {@code putIfNotNull} 的输入影响后续处理
     * @param processInstanceId 流程实例 ID，用于定位流程及其关联任务或业务记录
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param requestedTaskId 请求任务ID，后续用于加载实体数据与表单配置时定位或关联目标
     * @return 符合条件的流程进度结果，供调用方继续处理
     */
    private void loadEntityDataAndFormConfig(
            ProcessProgressDTO progress,
            String processInstanceId,
            String processKey,
            String requestedTaskId) {
        try {
            // 1. 获取流程变量中的实体信息
            String entityCode = null;
            String entityDataId = null;
            String formKey = null;
            String currentNodeId = null;
            // 优先从已加载的任务信息中获取当前节点ID（比execution查询更准确）
            if (StringUtils.hasText(requestedTaskId)
                    && progress.getTasks() != null) {
                currentNodeId = progress.getTasks().stream()
                        .filter(item -> requestedTaskId.equals(
                                item.getTaskId()))
                        .map(ProcessProgressDTO.TaskInfoDTO::getNodeId)
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "任务不存在、已处理或不属于该流程实例: "
                                        + requestedTaskId));
                log.debug(
                        "按任务ID选择当前节点: taskId={}, nodeId={}",
                        requestedTaskId,
                        currentNodeId);
            } else if (progress.getTasks() != null
                    && !progress.getTasks().isEmpty()) {
                currentNodeId = progress.getTasks().get(0).getNodeId();
                log.debug("从任务信息获取当前节点: nodeId={}", currentNodeId);
            }
            // 从流程变量获取
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (processInstance != null) {
                entityCode = (String) runtimeService.getVariable(processInstanceId, "entityCode");
                entityDataId = (String) runtimeService.getVariable(processInstanceId, "entityDataId");
                formKey = (String) runtimeService.getVariable(processInstanceId, "formKey");
                // 如果任务信息中没有获取到当前节点，再从execution查询
                if (currentNodeId == null) {
                    List<Execution> executions = runtimeService.createExecutionQuery()
                            .processInstanceId(processInstanceId)
                            .list();
                    for (Execution execution : executions) {
                        if (execution.getActivityId() != null) {
                            currentNodeId = execution.getActivityId();
                            break;
                        }
                    }
                }
            } else {
                // 从历史变量获取
                var entityCodeVar = historyService.createHistoricVariableInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .variableName("entityCode")
                        .singleResult();
                if (entityCodeVar != null)
                    entityCode = (String) entityCodeVar.getValue();
                var entityDataIdVar = historyService.createHistoricVariableInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .variableName("entityDataId")
                        .singleResult();
                if (entityDataIdVar != null)
                    entityDataId = (String) entityDataIdVar.getValue();
                var formKeyVar = historyService.createHistoricVariableInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .variableName("formKey")
                        .singleResult();
                if (formKeyVar != null)
                    formKey = (String) formKeyVar.getValue();
            }
            // 2. 加载实体数据
            if (entityDataId != null && entityCode != null) {
                try {
                    EntityDataDTO entityData = entityDataDynamicService.findById(entityCode, entityDataId);
                    if (entityData != null) {
                        progress.setEntityData(toRuntimeFormData(entityData));
                        // 随已授权的详情返回业务状态名称，知会/审批用户无需额外申请实体元数据权限。
                        if (StringUtils.hasText(entityData.getStatus())) {
                            putIfNotNull(progress.getEntityData(), "_statusText",
                                    entityStatusService.getStatusNameMap(entityCode).get(entityData.getStatus()));
                        }
                    }
                } catch (Exception e) {
                    log.debug("获取实体数据失败: {}", e.getMessage());
                }
            }
            // 3. 加载表单配置
            if (entityCode != null && entityDataId != null) {
                // 只使用部署资源或同一部署的发布历史快照。当前草稿不得作为 fallback。
                String bpmnXml = progress.getBpmnXml();
                loadFormConfig(
                        progress,
                        entityCode,
                        entityDataId,
                        currentNodeId,
                        formKey,
                        bpmnXml,
                        null,
                        processKey,
                        requestedTaskId);
            }
            // 4. 加载审批配置
            if (currentNodeId != null) {
                loadApprovalConfig(
                        progress, currentNodeId, progress.getBpmnXml());
            }
        } catch (FormConfigResolutionException exception) {
            throw exception;
        } catch (Exception e) {
            log.warn("加载实体数据和表单配置失败: {}", e.getMessage());
        }
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
        // 表单标题展示 biz 表的当前生命周期；progress.status 仍描述所查看的流程实例。
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
     * 加载表单配置
     *
     * @param progress 进度，作为 {@code getNodeFormsContextByProcessDefinitionId} 的输入影响后续处理
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param entityDataId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param currentNodeId 当前节点ID，后续用于加载表单配置时定位或关联目标
     * @param formKeyFromVariable 表单键起始变量，供本方法加载表单配置时使用
     * @param bpmnXml BPMNXML，作为 {@code resolveLastCompletedUserTaskId} 的输入影响后续处理
     * @param fallbackBpmnXml 兜底BPMNXML，主值不可用时供后续处理兜底
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param requestedTaskId 请求任务ID，后续用于加载表单配置时定位或关联目标
     * @return 符合条件的流程进度结果，供调用方继续处理
     */
    private void loadFormConfig(ProcessProgressDTO progress, String entityCode, String entityDataId,
            String currentNodeId, String formKeyFromVariable, String bpmnXml, String fallbackBpmnXml,
            String processKey, String requestedTaskId) {
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
            // 流程已结束：优先取最后一个完成的用户任务，避免网关被误当成表单节点。
            if (targetNodeId == null && progress.getCompletedNodes() != null
                    && !progress.getCompletedNodes().isEmpty()) {
                List<String> completed = progress.getCompletedNodes();
                targetNodeId = resolveLastCompletedUserTaskId(
                        completed,
                        bpmnXml,
                        fallbackBpmnXml);
            }
            // 3. 加载表单详情（优先级：流程发布快照 > 默认）
            List<ProcessProgressDTO.FormConfigDTO> formConfigs = new ArrayList<>();
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
                                            ? com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext.activeTask(
                                                            published.history().getId(),
                                                            nodeForm.getNodeId(),
                                                            requestedTaskId,
                                                            progress.getProcessInstanceId(),
                                                            entityCode,
                                                            entityDataId)
                                            : new com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext(
                                                            purpose,
                                                            published.history().getId(),
                                                            nodeForm.getNodeId()));
                    if (entityForm == null) {
                        throw new IllegalStateException(
                                "流程发布快照绑定的表单不存在: formId="
                                        + nodeForm.getFormId());
                    }
                    Boolean nodeFormReadonly = Integer.valueOf(1).equals(nodeForm.getIsReadonly()) ? Boolean.TRUE
                            : null;
                    formConfigs.add(buildProgressFormConfig(
                            entityForm,
                            nodeFormReadonly,
                            nodeForm));
                    log.info("从流程发布快照查询到节点表单: nodeId={}, formId={}, formName={}",
                            targetNodeId, nodeForm.getFormId(), entityForm.getFormName());
                    break;
                }
            }
            // 3b. 映射表中没有表单绑定，使用默认表单兜底。
            if (formConfigs.isEmpty()) {
                // 默认表单虽未钉在节点上，审批仍需与实际发布历史和活动任务绑定，
                // 否则页面能显示按钮，但提交时因缺少发布令牌被安全校验拒绝。
                com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext defaultFormContext = null;
                if (published != null) {
                    String historyId = published.history().getId();
                    defaultFormContext = "RUNNING".equals(progress.getStatus())
                            && StringUtils.hasText(requestedTaskId)
                            ? com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext.activeTask(
                                    historyId,
                                    targetNodeId,
                                    requestedTaskId,
                                    progress.getProcessInstanceId(),
                                    entityCode,
                                    entityDataId)
                            : com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext.historical(
                                    historyId,
                                    targetNodeId);
                }
                com.workflow.entity.form.infrastructure.persistence.record.EntityForm entityForm = entityFormRuntimeService
                        .getDefaultForm(entityDef.getId(), defaultFormContext);
                if (entityForm != null) {
                    formConfigs.add(buildProgressFormConfig(entityForm, null, null));
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

            ProcessProgressDTO.FormConfigDTO formConfig = formConfigs.get(0);
            progress.setFormConfigs(List.of(formConfig));
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
     * 解析最后{@code completed}用户任务ID；输出作为后续校验或处理的输入。
     *
     * @param completedNodeIds {@code completed}节点ID 集合，作为 {@code completedNodeIds.get} 的输入影响后续处理
     * @param bpmnXml BPMNXML，供本方法解析最后{@code completed}用户任务ID时使用
     * @param fallbackBpmnXml 兜底BPMNXML，主值不可用时供后续处理兜底
     * @return 解析后的最后{@code completed}用户任务ID文本，供调用方比较或展示
     */
    private String resolveLastCompletedUserTaskId(
            List<String> completedNodeIds,
            String bpmnXml,
            String fallbackBpmnXml) {
        String effectiveBpmnXml = StringUtils.hasText(bpmnXml)
                ? bpmnXml
                : fallbackBpmnXml;
        Set<String> userTaskIds = new HashSet<>();
        if (StringUtils.hasText(effectiveBpmnXml)) {
            try {
                javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory
                        .newInstance();
                factory.setNamespaceAware(true);
                org.w3c.dom.Document document = factory.newDocumentBuilder()
                        .parse(new java.io.ByteArrayInputStream(
                                effectiveBpmnXml.getBytes(
                                        java.nio.charset.StandardCharsets.UTF_8)));
                org.w3c.dom.NodeList tasks = document.getElementsByTagNameNS("*", "userTask");
                for (int index = 0; index < tasks.getLength(); index++) {
                    org.w3c.dom.Element task = (org.w3c.dom.Element) tasks.item(index);
                    if (StringUtils.hasText(task.getAttribute("id"))) {
                        userTaskIds.add(task.getAttribute("id"));
                    }
                }
            } catch (Exception exception) {
                log.warn(
                        "解析完成态用户任务失败，将使用节点名称兜底: {}",
                        exception.getMessage());
            }
        }
        for (int index = completedNodeIds.size() - 1; index >= 0; index--) {
            String nodeId = completedNodeIds.get(index);
            if (userTaskIds.contains(nodeId)) {
                return nodeId;
            }
        }
        for (int index = completedNodeIds.size() - 1; index >= 0; index--) {
            String nodeId = completedNodeIds.get(index);
            if (nodeId != null
                    && !nodeId.toLowerCase().contains("start")
                    && !nodeId.toLowerCase().contains("end")) {
                return nodeId;
            }
        }
        return completedNodeIds.get(completedNodeIds.size() - 1);
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
     * 根据实体表单与节点表单绑定构建进度表单配置 DTO。
     * <p>
     * 合并表单字段、布局节点、只读设置与发布版本信息。
     *
     * @param entityForm       实体表单
     * @param readonlyOverride 节点级只读覆盖，为 null 表示不强制只读
     * @param nodeForm         节点表单绑定，其发布版本优先于表单运行时版本，可为 null
     * @return 进度表单配置 DTO
     */
    private ProcessProgressDTO.FormConfigDTO buildProgressFormConfig(
            com.workflow.entity.form.infrastructure.persistence.record.EntityForm entityForm,
            Boolean readonlyOverride,
            com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm nodeForm) {
        ProcessProgressDTO.FormConfigDTO formConfig = new ProcessProgressDTO.FormConfigDTO();
        formConfig.setFormId(entityForm.getId());
        if (nodeForm != null) {
            formConfig.setFormReleaseId(nodeForm.getFormReleaseId());
            formConfig.setFormReleaseVersion(nodeForm.getFormReleaseVersion());
        } else {
            // 默认表单没有流程节点钉版，诊断信息应反映本次实际解析的发布版本。
            formConfig.setFormReleaseId(entityForm.getRuntimeReleaseId());
            formConfig.setFormReleaseVersion(
                    entityForm.getRuntimeReleaseVersion());
        }
        formConfig.setFormName(entityForm.getFormName());
        formConfig.setFormKey(entityForm.getFormKey());
        formConfig.setLayoutType(entityForm.getLayoutType());
        formConfig.setIsReadonly(Boolean.TRUE.equals(readonlyOverride));
        formConfig.setCustomComponent(entityForm.getCustomComponent());
        formConfig.setViewConfig(entityForm.getViewConfig());
        formConfig.setDataSourceBindingsDocument(
                entityForm.getDataSourceBindingsDocument());
        formConfig.setEffectiveFormReleaseId(
                entityForm.getEffectiveReleaseId());
        formConfig.setHotfixApplied(
                entityForm.getHotfixApplied());
        formConfig.setReleaseResolutionToken(
                entityForm.getReleaseResolutionToken());
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
     * 从 BPMN XML 解析表单绑定
     * 支持格式：
     * 1. extensionElements -> properties -> property name="entityFormId"
     * value="xxx"
     * 2. userTask 标签上的 flowable:formKey="xxx" 属性
     *
     * @param nodeId 节点ID，后续用于解析表单键起始BPMN时定位或关联目标
     * @param bpmnXml BPMNXML，作为 {@code builder.parse} 的输入影响后续处理
     * @return 解析后的表单键起始BPMN文本，供调用方比较或展示
     */
    private String resolveFormKeyFromBpmn(String nodeId, String bpmnXml) {
        if (bpmnXml == null || nodeId == null || nodeId.isEmpty()) {
            return null;
        }
        try {
            javax.xml.parsers.DocumentBuilderFactory factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            javax.xml.parsers.DocumentBuilder builder = factory.newDocumentBuilder();
            org.w3c.dom.Document doc = builder.parse(new java.io.ByteArrayInputStream(bpmnXml.getBytes("UTF-8")));
            // 查找指定 id 的 userTask 元素
            org.w3c.dom.NodeList userTasks = doc.getElementsByTagNameNS("*", "userTask");
            for (int i = 0; i < userTasks.getLength(); i++) {
                org.w3c.dom.Element userTask = (org.w3c.dom.Element) userTasks.item(i);
                if (nodeId.equals(userTask.getAttribute("id"))) {
                    // 1. 优先解析 extensionElements -> properties -> property name="entityFormId"
                    org.w3c.dom.NodeList extElements = userTask.getElementsByTagNameNS("*", "extensionElements");
                    for (int j = 0; j < extElements.getLength(); j++) {
                        org.w3c.dom.Element extElement = (org.w3c.dom.Element) extElements.item(j);
                        org.w3c.dom.NodeList properties = extElement.getElementsByTagNameNS("*", "properties");
                        for (int k = 0; k < properties.getLength(); k++) {
                            org.w3c.dom.Element props = (org.w3c.dom.Element) properties.item(k);
                            org.w3c.dom.NodeList propList = props.getElementsByTagNameNS("*", "property");
                            for (int m = 0; m < propList.getLength(); m++) {
                                org.w3c.dom.Element prop = (org.w3c.dom.Element) propList.item(m);
                                String name = prop.getAttribute("name");
                                String value = prop.getAttribute("value");
                                if ("entityFormId".equals(name) && value != null && !value.isEmpty()) {
                                    return value;
                                }
                            }
                        }
                    }
                    // 2. 回退：解析 userTask 标签上的 formKey 属性（flowable:formKey 或 formKey）
                    String formKey = userTask.getAttribute("formKey");
                    if (formKey == null || formKey.isEmpty()) {
                        formKey = userTask.getAttributeNS("http://flowable.org/bpmn", "formKey");
                    }
                    if (formKey != null && !formKey.isEmpty()) {
                        return formKey;
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("从BPMN解析表单绑定失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 加载审批配置。运行实例只使用其部署 BPMN 或对应发布历史快照；
     * 快照缺失时保持旧版默认，不得回退到当前映射表。
     *
     * @param progress 进度，作为 {@code loadApprovalConfigFromBpmn} 的输入影响后续处理
     * @param currentNodeId 当前节点ID，后续用于加载审批配置时定位或关联目标
     * @param bpmnXml BPMNXML，作为 {@code loadApprovalConfigFromBpmn} 的输入影响后续处理
     * @return 符合条件的流程进度结果，供调用方继续处理
     */
    private void loadApprovalConfig(
            ProcessProgressDTO progress,
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
     * @return 符合条件的流程进度结果，供调用方继续处理
     */
    private void loadApprovalConfigFromBpmn(ProcessProgressDTO progress, String currentNodeId, String bpmnXml) {
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
            ProcessProgressDTO.ApprovalConfigDTO approvalConfig = new ProcessProgressDTO.ApprovalConfigDTO();
            approvalConfig.setEnabled(config.has("enabled") ? config.get("enabled").asBoolean() : true);
            approvalConfig.setCommentLabel(config.has("commentLabel") ? config.get("commentLabel").asText() : "审批意见");
            if (config.has("options") && config.get("options").isArray()) {
                List<ProcessProgressDTO.ApprovalOptionDTO> options = new ArrayList<>();
                for (com.fasterxml.jackson.databind.JsonNode optNode : config.get("options")) {
                    ProcessProgressDTO.ApprovalOptionDTO option = new ProcessProgressDTO.ApprovalOptionDTO();
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
