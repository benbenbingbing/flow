package com.workflow.process.runtime;

import com.workflow.dto.ProcessProgressDTO;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.mapper.ProcessNodeApprovalMapper;
import com.workflow.mapper.ProcessOperationLogMapper;
import com.workflow.mapper.ProcessTaskMapper;
import com.workflow.mapper.SysGroupMapper;
import com.workflow.mapper.SysUserGroupMapper;
import com.workflow.mapper.SysUserMapper;
import com.workflow.process.publish.ProcessPublishedSnapshotService;
import com.workflow.service.EntityDataDynamicService;
import com.workflow.service.ProcessNodeApprovalOptionService;
import com.workflow.service.SysUserService;
import com.workflow.service.entity.EntityFormRuntimeService;
import com.workflow.service.form.EntityFormFieldRuntimeMapper;
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
import java.util.List;
import java.util.Map;
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
    private final ProcessDefinitionConfigMapper processConfigMapper;
    private final SysUserService sysUserService;
    private final EntityDataDynamicService entityDataDynamicService;
    private final EntityFormRuntimeService entityFormRuntimeService;
    private final EntityDefinitionMapper entityDefinitionMapper;
    private final ProcessTaskMapper processTaskMapper;
    private final SysGroupMapper sysGroupMapper;
    private final SysUserGroupMapper sysUserGroupMapper;
    private final SysUserMapper sysUserMapper;
    private final ProcessOperationLogMapper operationLogMapper;
    private final ProcessNodeApprovalMapper nodeApprovalMapper;
    private final ProcessNodeApprovalOptionService approvalOptionService;
    private final ProcessPublishedSnapshotService processPublishedSnapshotService;

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
     * 识别被终止节点 -> 构建节点审批历史（含转办、终止记录合并）-> 组装当前任务 -> 构建节点处理人映射 ->
     * 加载实体数据与表单/审批配置。
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
            startUserId = historicInstance != null ? historicInstance.getStartUserId() : null;
            if (historicInstance != null && historicInstance.getDeleteReason() != null
                    && (historicInstance.getDeleteReason().contains("终止") || historicInstance.getDeleteReason().contains("terminated"))) {
                progress.setStatus("TERMINATED");
            } else {
                progress.setStatus("COMPLETED");
            }
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
                            org.flowable.engine.repository.Deployment deployment = repositoryService.createDeploymentQuery()
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
                
                // 获取流程名称
                ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processDefinition.getKey()).orElse(null);
                if (config != null) {
                    progress.setProcessName(config.getProcessName());
                    // 如果从 Flowable 获取 XML 失败，使用数据库中的 XML
                    if (progress.getBpmnXml() == null && config.getBpmnXml() != null) {
                        progress.setBpmnXml(config.getBpmnXml());
                    }
                } else {
                    progress.setProcessName(processDefinition.getName());
                }
            }
        }
        
        // 3. 获取历史活动记录
        List<HistoricActivityInstance> historicActivities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(processInstanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();
        
        // 4. 提取已完成的节点
        List<String> completedNodes = historicActivities.stream()
                .filter(h -> h.getEndTime() != null)
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
        
        // 6.1 终止流程：识别被终止时正在执行的节点
        if ("TERMINATED".equals(progress.getStatus())) {
            historicInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (historicInstance != null && historicInstance.getEndTime() != null) {
                long processEndTime = historicInstance.getEndTime().getTime();
                List<String> terminatedNodes = historicActivities.stream()
                        .filter(h -> h.getEndTime() != null)
                        .filter(h -> Math.abs(h.getEndTime().getTime() - processEndTime) < 2000) // 2秒内视为被终止的节点
                        .map(HistoricActivityInstance::getActivityId)
                        .distinct()
                        .collect(Collectors.toList());
                progress.setTerminatedNodes(terminatedNodes);
                // 从已完成节点中移除被终止的节点，避免显示为绿色"已完成"
                completedNodes.removeAll(terminatedNodes);
            } else {
                progress.setTerminatedNodes(new ArrayList<>());
            }
        } else {
            progress.setTerminatedNodes(new ArrayList<>());
        }
        
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
                    // 将用户ID/用户名转换为统一显示名称：nickname(username)
                    if (assigneeId != null && !assigneeId.isEmpty() && !assigneeId.startsWith("${")) {
                        String displayName = sysUserService.getDisplayName(assigneeId);
                        if (!assigneeId.equals(displayName)) {
                            dto.setAssigneeName(displayName);
                        }
                    }
                    dto.setStartTime(h.getStartTime() != null ? formatDate(h.getStartTime()) : null);
                    dto.setEndTime(h.getEndTime() != null ? formatDate(h.getEndTime()) : null);
                    dto.setDuration(h.getDurationInMillis());
                    dto.setStatus(h.getEndTime() != null ? "COMPLETED" : "ACTIVE");
                    
                    // 查询该节点关联的历史变量（快照）
                    java.util.List<org.flowable.variable.api.history.HistoricVariableInstance> nodeVars = null;
                    if (h.getTaskId() != null) {
                        nodeVars = historyService.createHistoricVariableInstanceQuery()
                                .taskId(h.getTaskId()).list();
                    }
                    if ((nodeVars == null || nodeVars.isEmpty()) && h.getExecutionId() != null) {
                        nodeVars = historyService.createHistoricVariableInstanceQuery()
                                .executionId(h.getExecutionId()).list();
                    }
                    if (nodeVars != null && !nodeVars.isEmpty()) {
                        java.util.Map<String, Object> vars = new java.util.HashMap<>();
                        for (var v : nodeVars) vars.put(v.getVariableName(), v.getValue());
                        dto.setVariables(vars);
                    }
                    
                    // 获取任务处理方式
                    if (h.getEndTime() != null && "userTask".equals(h.getActivityType())) {
                        // 查询任务评论判断处理方式
                        try {
                            List<org.flowable.engine.task.Comment> comments = taskService.getTaskComments(h.getTaskId());
                            String commentMsg = comments.isEmpty() ? null : comments.get(0).getFullMessage();
                            if (commentMsg != null && commentMsg.contains("转办给:")) {
                                dto.setAction("TRANSFERRED");
                            } else {
                                // 优先从本地 process_task 表获取每个任务的实际 action（最准确）
                                String action = null;
                                String actionLabel = null;
                                var localTask = processTaskMapper.selectByTaskId(h.getTaskId());
                                if (localTask != null && localTask.getAction() != null) {
                                    action = localTask.getAction();
                                    actionLabel = localTask.getActionLabel();
                                    if (localTask.getComment() != null) {
                                        dto.setComment(localTask.getComment());
                                    }
                                } else {
                                    // fallback：从历史变量获取（按任务ID查）
                                    var actionVar = historyService.createHistoricVariableInstanceQuery()
                                            .taskId(h.getTaskId())
                                            .variableName("action")
                                            .singleResult();
                                    action = actionVar != null ? (String) actionVar.getValue() : null;
                                }
                                if (actionLabel == null) {
                                    var actionLabelVar = historyService.createHistoricVariableInstanceQuery()
                                            .taskId(h.getTaskId())
                                            .variableName("actionLabel")
                                            .singleResult();
                                    actionLabel = actionLabelVar != null ? (String) actionLabelVar.getValue() : null;
                                }
                                // 兼容旧数据：修复前 actionLabel 曾作为流程实例变量保存
                                if (actionLabel == null) {
                                    var actionLabelVar = historyService.createHistoricVariableInstanceQuery()
                                            .processInstanceId(processInstanceId)
                                            .variableName("actionLabel")
                                            .singleResult();
                                    actionLabel = actionLabelVar != null ? (String) actionLabelVar.getValue() : null;
                                }
                                dto.setAction(normalizeAction(action));
                                dto.setActionLabel(actionLabel);
                            }
                        } catch (Exception e) {
                            dto.setAction("APPROVED");
                        }
                    }
                    
                    return dto;
                })
                .collect(Collectors.toList());
        
        // 7.1 合并转办记录到审批历史中
        try {
            List<com.workflow.entity.ProcessOperationLog> transferLogs = operationLogMapper
                    .selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflow.entity.ProcessOperationLog>()
                            .eq(com.workflow.entity.ProcessOperationLog::getProcessInstanceId, processInstanceId)
                            .eq(com.workflow.entity.ProcessOperationLog::getOperationType, "TRANSFER")
                            .orderByAsc(com.workflow.entity.ProcessOperationLog::getOperationTime));
            
            for (com.workflow.entity.ProcessOperationLog log : transferLogs) {
                // 通过 taskId 查找节点信息
                String nodeId = null;
                String nodeName = null;
                if (log.getTaskId() != null) {
                    var historicTask = historyService.createHistoricTaskInstanceQuery()
                            .taskId(log.getTaskId())
                            .singleResult();
                    if (historicTask != null) {
                        nodeId = historicTask.getTaskDefinitionKey();
                        nodeName = historicTask.getName();
                    } else {
                        var task = taskService.createTaskQuery().taskId(log.getTaskId()).singleResult();
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
                dto.setAssigneeName(sysUserService.getDisplayName(log.getOperatorId()));
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
        
        // 7.2 合并终止记录到审批历史中
        try {
            List<com.workflow.entity.ProcessOperationLog> terminateLogs = operationLogMapper
                    .selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<com.workflow.entity.ProcessOperationLog>()
                            .eq(com.workflow.entity.ProcessOperationLog::getProcessInstanceId, processInstanceId)
                            .eq(com.workflow.entity.ProcessOperationLog::getOperationType, "TERMINATE")
                            .orderByAsc(com.workflow.entity.ProcessOperationLog::getOperationTime));
            
            for (com.workflow.entity.ProcessOperationLog log : terminateLogs) {
                ProcessProgressDTO.NodeHistoryDTO dto = new ProcessProgressDTO.NodeHistoryDTO();
                dto.setNodeId("TERMINATE_" + log.getId());
                dto.setNodeName("流程终止");
                dto.setNodeType("terminate");
                dto.setAssignee(log.getOperatorId());
                dto.setAssigneeName(sysUserService.getDisplayName(log.getOperatorId()));
                dto.setAction("TERMINATED");
                dto.setComment(log.getOperationComment());
                String opTime = log.getOperationTime() != null ? log.getOperationTime().format(DATE_FORMATTER) : null;
                dto.setStartTime(opTime);
                dto.setEndTime(opTime);
                dto.setStatus("TERMINATED");
                nodeHistory.add(dto);
            }
        } catch (Exception e) {
            log.warn("合并终止记录失败", e);
        }
        
        progress.setNodeHistory(nodeHistory);
        
        // 8. 获取当前任务信息
        if (processInstance != null) {
            List<Task> tasks = taskService.createTaskQuery()
                    .processInstanceId(processInstanceId)
                    .list();
            
            List<ProcessProgressDTO.TaskInfoDTO> taskInfos = tasks.stream()
                    .map(t -> {
                        ProcessProgressDTO.TaskInfoDTO dto = new ProcessProgressDTO.TaskInfoDTO();
                        dto.setTaskId(t.getId());
                        dto.setTaskName(t.getName());
                        dto.setNodeId(t.getTaskDefinitionKey());
                        dto.setAssignee(t.getAssignee());
                        dto.setAssigneeName(sysUserService.getDisplayName(t.getAssignee()));
                        dto.setCreateTime(t.getCreateTime() != null ? formatDate(t.getCreateTime()) : null);
                        return dto;
                    })
                    .collect(Collectors.toList());
            progress.setTasks(taskInfos);
        }
        
        // 9. 构建节点处理人映射（用于前端悬停显示）
        buildNodeAssigneeMap(progress, processInstanceId);
        
        // 10. 获取实体数据和表单配置
        loadEntityDataAndFormConfig(progress, processInstanceId, progress.getProcessKey());
        
        return progress;
    }

    /**
     * 构建节点处理人映射
     * 包含已完成节点的审批人信息和当前节点的处理人信息
     */
    private void buildNodeAssigneeMap(ProcessProgressDTO progress, String processInstanceId) {
        Map<String, ProcessProgressDTO.AssigneeInfoDTO> assigneeMap = new HashMap<>();
        Map<String, List<ProcessProgressDTO.AssigneeInfoDTO>> assigneesMap = new HashMap<>();
        
        // 1. 查询历史任务（已完成的任务）
        List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .list();
        
        for (HistoricTaskInstance task : historicTasks) {
            String nodeId = task.getTaskDefinitionKey();
            
            ProcessProgressDTO.AssigneeInfoDTO info = new ProcessProgressDTO.AssigneeInfoDTO();
            String userId = task.getAssignee();
            String displayName = sysUserService.getDisplayName(userId);
            info.setAssigneeId(userId);
            info.setAssigneeName(displayName);
            info.setHandleTime(task.getEndTime() != null ? formatDate(task.getEndTime()) : null);
            info.setStatus("COMPLETED");
            // 从流程变量/本地待办中读取处理方式与显示文本
            String action = null;
            String actionLabel = null;
            String comment = null;
            var localTask = processTaskMapper.selectByTaskId(task.getId());
            if (localTask != null) {
                action = localTask.getAction();
                actionLabel = localTask.getActionLabel();
                comment = localTask.getComment();
            }
            if (action == null) {
                var actionVar = historyService.createHistoricVariableInstanceQuery()
                        .taskId(task.getId()).variableName("action").singleResult();
                action = actionVar != null ? (String) actionVar.getValue() : null;
            }
            if (actionLabel == null) {
                var actionLabelVar = historyService.createHistoricVariableInstanceQuery()
                        .taskId(task.getId()).variableName("actionLabel").singleResult();
                actionLabel = actionLabelVar != null ? (String) actionLabelVar.getValue() : null;
            }
            // 兼容旧数据：修复前 actionLabel 曾作为流程实例变量保存
            if (actionLabel == null) {
                var actionLabelVar = historyService.createHistoricVariableInstanceQuery()
                        .processInstanceId(processInstanceId).variableName("actionLabel").singleResult();
                actionLabel = actionLabelVar != null ? (String) actionLabelVar.getValue() : null;
            }
            info.setAction(normalizeAction(action));
            info.setActionLabel(actionLabel);
            info.setComment(comment);
            
            // 单节点处理人映射：保留最新的
            if (!assigneeMap.containsKey(nodeId) || 
                (task.getEndTime() != null && 
                 (assigneeMap.get(nodeId).getHandleTime() == null || 
                  formatDate(task.getEndTime()).compareTo(assigneeMap.get(nodeId).getHandleTime()) > 0))) {
                assigneeMap.put(nodeId, info);
            }
            
            // 多实例节点处理人列表：保留所有子任务
            assigneesMap.computeIfAbsent(nodeId, k -> new ArrayList<>()).add(info);
        }
        
        // 2. 查询当前活动任务
        List<Task> activeTasks = taskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();
        
        for (Task task : activeTasks) {
            String nodeId = task.getTaskDefinitionKey();
            ProcessProgressDTO.AssigneeInfoDTO info = new ProcessProgressDTO.AssigneeInfoDTO();
            String userId = task.getAssignee();
            
            if (userId == null || userId.isEmpty()) {
                // 尝试从候选组/候选人中解析
                try {
                    List<org.flowable.identitylink.api.IdentityLink> identityLinks = taskService.getIdentityLinksForTask(task.getId());
                    List<String> groupMemberNames = new ArrayList<>();
                    List<String> candidateUsers = new ArrayList<>();
                    for (org.flowable.identitylink.api.IdentityLink link : identityLinks) {
                        if (link.getGroupId() != null) {
                            String members = getGroupMemberNames(link.getGroupId());
                            if (members != null && !members.isEmpty()) {
                                for (String m : members.split(",")) {
                                    if (!groupMemberNames.contains(m)) {
                                        groupMemberNames.add(m);
                                    }
                                }
                            }
                        } else if (link.getUserId() != null) {
                            candidateUsers.add(link.getUserId());
                        }
                    }
                    if (!groupMemberNames.isEmpty()) {
                        info.setAssigneeId(null);
                        info.setAssigneeName(String.join(",", groupMemberNames));
                    } else if (!candidateUsers.isEmpty()) {
                        info.setAssigneeId(null);
                        info.setAssigneeName(getUserNamesFromIds(candidateUsers));
                    } else {
                        info.setAssigneeId(null);
                        info.setAssigneeName("未分配");
                    }
                } catch (Exception e) {
                    info.setAssigneeId(null);
                    info.setAssigneeName("未分配");
                }
            } else {
                String displayName = sysUserService.getDisplayName(userId);
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
     */
    private void loadEntityDataAndFormConfig(ProcessProgressDTO progress, String processInstanceId, String processKey) {
        try {
            // 1. 获取流程变量中的实体信息
            String entityCode = null;
            String entityDataId = null;
            String formKey = null;
            String currentNodeId = null;
            
            // 优先从已加载的任务信息中获取当前节点ID（比execution查询更准确）
            if (progress.getTasks() != null && !progress.getTasks().isEmpty()) {
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
                if (entityCodeVar != null) entityCode = (String) entityCodeVar.getValue();
                
                var entityDataIdVar = historyService.createHistoricVariableInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .variableName("entityDataId")
                        .singleResult();
                if (entityDataIdVar != null) entityDataId = (String) entityDataIdVar.getValue();
                
                var formKeyVar = historyService.createHistoricVariableInstanceQuery()
                        .processInstanceId(processInstanceId)
                        .variableName("formKey")
                        .singleResult();
                if (formKeyVar != null) formKey = (String) formKeyVar.getValue();
            }
            
            // 2. 加载实体数据
            if (entityDataId != null && entityCode != null) {
                try {
                    com.workflow.dto.EntityDataDTO entityData = entityDataDynamicService.findById(entityCode, entityDataId);
                    if (entityData != null) {
                        Map<String, Object> entityDataMap = new java.util.HashMap<>();
                        if (entityData.getData() != null) {
                            entityDataMap.putAll(entityData.getData());
                        }
                        // 将系统标准字段合并进去，确保审批弹窗能正确显示
                        if (entityData.getName() != null) entityDataMap.put("name", entityData.getName());
                        if (entityData.getCode() != null) entityDataMap.put("code", entityData.getCode());
                        if (entityData.getStatus() != null) entityDataMap.put("status", entityData.getStatus());
                        if (entityData.getDataNo() != null) entityDataMap.put("dataNo", entityData.getDataNo());
                        if (entityData.getTitle() != null) entityDataMap.put("title", entityData.getTitle());
                        if (entityData.getDeptId() != null) entityDataMap.put("deptId", entityData.getDeptId());
                        if (entityData.getSubmitterId() != null) entityDataMap.put("submitterId", entityData.getSubmitterId());
                        if (entityData.getSubmitterName() != null) entityDataMap.put("submitterName", entityData.getSubmitterName());
                        progress.setEntityData(entityDataMap);
                    }
                } catch (Exception e) {
                    log.debug("获取实体数据失败: {}", e.getMessage());
                }
            }
            
            // 3. 加载表单配置
            if (entityCode != null && entityDataId != null) {
                // 优先使用已部署的BPMN XML解析，如果解析不到，尝试从数据库获取最新的BPMN XML作为fallback
                String bpmnXml = progress.getBpmnXml();
                String fallbackBpmnXml = null;
                if (bpmnXml == null || bpmnXml.isEmpty()) {
                    if (processKey != null && !processKey.isEmpty()) {
                        ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processKey).orElse(null);
                        if (config != null && config.getBpmnXml() != null) {
                            bpmnXml = config.getBpmnXml();
                        }
                    }
                } else if (processKey != null && !processKey.isEmpty()) {
                    // 即使已部署的BPMN XML有内容，也准备fallback（可能包含最新的节点表单配置）
                    ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processKey).orElse(null);
                    if (config != null && config.getBpmnXml() != null) {
                        fallbackBpmnXml = config.getBpmnXml();
                    }
                }
                loadFormConfig(progress, entityCode, entityDataId, currentNodeId, formKey, bpmnXml, fallbackBpmnXml, processKey);
            }
            
            // 4. 加载审批配置
            if (currentNodeId != null) {
                loadApprovalConfig(progress, currentNodeId, progress.getBpmnXml(), processKey);
            }
            
        } catch (Exception e) {
            log.warn("加载实体数据和表单配置失败: {}", e.getMessage());
        }
    }
    
    /**
     * 加载表单配置
     */
    private void loadFormConfig(ProcessProgressDTO progress, String entityCode, String entityDataId, 
                                  String currentNodeId, String formKeyFromVariable, String bpmnXml, String fallbackBpmnXml, String processKey) {
        try {
            // 1. 获取实体定义
            com.workflow.entity.EntityDefinition entityDef = 
                entityDefinitionMapper.findByEntityCode(entityCode).orElse(null);
            if (entityDef == null) {
                log.warn("加载表单配置失败: 实体不存在, entityCode={}", entityCode);
                return;
            }
            
            // 2. 确定要加载表单的节点ID
            String targetNodeId = currentNodeId;
            
            // 流程已结束：取最后一个完成的节点（排除 startEvent 和 endEvent）
            if (targetNodeId == null && progress.getCompletedNodes() != null && !progress.getCompletedNodes().isEmpty()) {
                List<String> completed = progress.getCompletedNodes();
                for (int i = completed.size() - 1; i >= 0; i--) {
                    String nodeId = completed.get(i);
                    if (nodeId != null && !nodeId.toLowerCase().contains("start") && !nodeId.toLowerCase().contains("end")) {
                        targetNodeId = nodeId;
                        break;
                    }
                }
                if (targetNodeId == null) {
                    targetNodeId = completed.get(completed.size() - 1);
                }
            }
            
            // 3. 加载表单详情（优先级：流程发布快照 > 默认）
            List<ProcessProgressDTO.FormConfigDTO> formConfigs = new ArrayList<>();
            
            // 3a. 最高优先级：从流程发布快照查询节点表单绑定
            if (progress.getProcessDefinitionId() != null
                    && !progress.getProcessDefinitionId().isEmpty()
                    && targetNodeId != null
                    && !targetNodeId.isEmpty()) {
                try {
                    List<com.workflow.entity.ProcessNodeForm> nodeForms =
                            processPublishedSnapshotService
                                    .getNodeFormsByProcessDefinitionId(
                                            progress.getProcessDefinitionId(),
                                            targetNodeId);
                    for (com.workflow.entity.ProcessNodeForm nodeForm : nodeForms) {
                        if (nodeForm.getFormId() == null || nodeForm.getFormId().isEmpty()) {
                            continue;
                        }
                        com.workflow.entity.EntityForm entityForm =
                                entityFormRuntimeService.getByBinding(nodeForm);
                        if (entityForm != null) {
                            Boolean nodeFormReadonly =
                                    Integer.valueOf(1).equals(nodeForm.getIsReadonly()) ? Boolean.TRUE : null;
                            formConfigs.add(buildProgressFormConfig(
                                    entityForm,
                                    nodeFormReadonly,
                                    nodeForm));
                            log.info("从流程发布快照查询到节点表单: nodeId={}, formId={}, formName={}",
                                targetNodeId, nodeForm.getFormId(), entityForm.getFormName());
                        }
                    }
                } catch (Exception e) {
                    log.warn("从流程发布快照查询节点表单失败: {}", e.getMessage());
                }
            }
            
            // 3b. 映射表中没有表单绑定，尝试使用默认表单
            if (formConfigs.isEmpty()) {
                com.workflow.entity.EntityForm entityForm =
                        entityFormRuntimeService.getDefaultForm(entityDef.getId());
                if (entityForm != null) {
                    formConfigs.add(buildProgressFormConfig(entityForm, null, null));
                    log.debug("节点未配置表单，回退到默认表单: nodeId={}, formId={}", targetNodeId, entityForm.getId());
                }
            }
            
            if (formConfigs.isEmpty()) {
                log.warn("无法加载表单: entityId={}, nodeId={}", entityDef.getId(), targetNodeId);
                return;
            }

            progress.setFormConfigs(formConfigs);
            progress.setFormConfig(formConfigs.get(0));
            log.debug("表单配置加载成功: entityCode={}, formCount={}, firstFormKey={}, firstFieldsCount={}",
                entityCode, formConfigs.size(), formConfigs.get(0).getFormKey(),
                formConfigs.get(0).getFields() != null ? formConfigs.get(0).getFields().size() : 0);
            
        } catch (Exception e) {
            log.warn("加载表单配置失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 根据实体表单与节点表单绑定构建进度表单配置 DTO。
     * <p>
     * 合并表单字段、布局节点、只读设置与发布版本信息。
     *
     * @param entityForm       实体表单
     * @param readonlyOverride 节点级只读覆盖，为 null 表示不强制只读
     * @param nodeForm         节点表单绑定，用于补充发布版本ID/版本号，可为 null
     * @return 进度表单配置 DTO
     */
    private ProcessProgressDTO.FormConfigDTO buildProgressFormConfig(
            com.workflow.entity.EntityForm entityForm,
            Boolean readonlyOverride,
            com.workflow.entity.ProcessNodeForm nodeForm) {
        ProcessProgressDTO.FormConfigDTO formConfig = new ProcessProgressDTO.FormConfigDTO();
        formConfig.setFormId(entityForm.getId());
        if (nodeForm != null) {
            formConfig.setFormReleaseId(nodeForm.getFormReleaseId());
            formConfig.setFormReleaseVersion(nodeForm.getFormReleaseVersion());
        }
        formConfig.setFormName(entityForm.getFormName());
        formConfig.setFormKey(entityForm.getFormKey());
        formConfig.setLayoutType(entityForm.getLayoutType());
        formConfig.setIsReadonly(Boolean.TRUE.equals(readonlyOverride));
        formConfig.setCustomComponent(entityForm.getCustomComponent());
        formConfig.setViewConfig(entityForm.getViewConfig());

        if (entityForm.getFields() != null) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            List<Map<String, Object>> fields = new ArrayList<>();
            for (com.workflow.entity.EntityFormField field : entityForm.getFields()) {
                fields.add(EntityFormFieldRuntimeMapper.toMap(field, readonlyOverride, mapper));
            }
            formConfig.setFields(fields);
        }
        if (entityForm.getNodes() != null) {
            com.fasterxml.jackson.databind.ObjectMapper mapper =
                    new com.fasterxml.jackson.databind.ObjectMapper();
            formConfig.setNodes(mapper.convertValue(
                    entityForm.getNodes(),
                    new com.fasterxml.jackson.core.type.TypeReference<
                            List<Map<String, Object>>>() {}));
        }

        return formConfig;
    }
    
    /**
     * 从 BPMN XML 解析表单绑定
     * 支持格式：
     * 1. extensionElements -> properties -> property name="entityFormId" value="xxx"
     * 2. userTask 标签上的 flowable:formKey="xxx" 属性
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
     * 加载审批配置（只从 process_node_approval 映射表读取）
     */
    private void loadApprovalConfig(ProcessProgressDTO progress, String currentNodeId, String bpmnXml, String processKey) {
        if (currentNodeId == null || currentNodeId.isEmpty()) {
            return;
        }
        try {
            // 只从 process_node_approval 映射表查询
            if (processKey != null && !processKey.isEmpty()) {
                ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processKey).orElse(null);
                if (config != null) {
                    com.workflow.entity.ProcessNodeApproval nodeApproval = nodeApprovalMapper.selectByNodeId(config.getId(), currentNodeId);
                    if (nodeApproval != null) {
                        ProcessProgressDTO.ApprovalConfigDTO approvalConfig = new ProcessProgressDTO.ApprovalConfigDTO();
                        approvalConfig.setEnabled(nodeApproval.getEnabled() != null && nodeApproval.getEnabled() == 1);
                        approvalConfig.setCommentLabel(nodeApproval.getCommentLabel() != null ? nodeApproval.getCommentLabel() : "审批意见");
                        
                        List<Map<String, Object>> optionConfigs =
                                approvalOptionService.findOptions(nodeApproval.getId());
                        if (!optionConfigs.isEmpty()) {
                            List<ProcessProgressDTO.ApprovalOptionDTO> options = new ArrayList<>();
                            for (Map<String, Object> optionConfig : optionConfigs) {
                                ProcessProgressDTO.ApprovalOptionDTO option =
                                        new ProcessProgressDTO.ApprovalOptionDTO();
                                option.setValue(String.valueOf(
                                        optionConfig.getOrDefault("value", "")));
                                option.setLabel(String.valueOf(
                                        optionConfig.getOrDefault("label", "")));
                                option.setType(String.valueOf(
                                        optionConfig.getOrDefault("type", "primary")));
                                option.setShowComment(!Boolean.FALSE.equals(
                                        optionConfig.get("showComment")));
                                options.add(option);
                            }
                            approvalConfig.setOptions(options);
                        }
                        
                        progress.setApprovalConfig(approvalConfig);
                        log.debug("从映射表加载审批配置成功: nodeId={}, optionsCount={}", 
                            currentNodeId, approvalConfig.getOptions() != null ? approvalConfig.getOptions().size() : 0);
                        return;
                    }
                }
            }
            
            log.debug("映射表中未找到审批配置: nodeId={}", currentNodeId);
        } catch (Exception e) {
            log.warn("加载审批配置失败: {}", e.getMessage());
        }
    }
    
    /**
     * 从 BPMN XML 解析审批配置（fallback）
     */
    private void loadApprovalConfigFromBpmn(ProcessProgressDTO progress, String currentNodeId, String bpmnXml) {
        if (bpmnXml == null || currentNodeId == null || currentNodeId.isEmpty()) {
            return;
        }
        try {
            // 匹配当前节点的 userTask 标签内容
            String patternStr = "<(bpmn:)?userTask[^>]*id=\"" + java.util.regex.Pattern.quote(currentNodeId) + "\"[^>]*>(.*?)</(bpmn:)?userTask>";
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(patternStr, java.util.regex.Pattern.DOTALL | java.util.regex.Pattern.CASE_INSENSITIVE);
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
            if (!propMatcher.find()) {
                propPattern = java.util.regex.Pattern.compile(
                    "<(?:flowable:|camunda:)?property[^>]*value=\"([^\"]*)\"[^>]*name=\"approvalConfig\"",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
                propMatcher = propPattern.matcher(content);
            }
            
            if (!propMatcher.find()) {
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
            
            com.fasterxml.jackson.databind.JsonNode config = 
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(approvalConfigJson);
            
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
     * 获取组成员显示名称列表（去重）
     */
    private String getGroupMemberNames(String groupCode) {
        try {
            com.workflow.entity.SysGroup group = sysGroupMapper.selectByGroupCode(groupCode);
            if (group == null) {
                return groupCode;
            }
            List<String> userIds = sysUserGroupMapper.selectUserIdsByGroupId(group.getId());
            if (userIds == null || userIds.isEmpty()) {
                return group.getGroupName();
            }
            List<String> names = new ArrayList<>();
            for (String userId : userIds) {
                String displayName = sysUserService.getDisplayName(userId);
                if (!names.contains(displayName)) {
                    names.add(displayName);
                }
            }
            return names.isEmpty() ? group.getGroupName() : String.join(",", names);
        } catch (Exception e) {
            log.warn("获取组成员失败: {}", groupCode, e);
            return groupCode;
        }
    }

    /**
     * 根据用户ID/用户名列表获取统一显示名称列表
     */
    private String getUserNamesFromIds(List<String> idsOrNames) {
        return sysUserService.getDisplayNames(idsOrNames);
    }

    /**
     * 规范化任务 action 为显示状态码；自定义 action 保留原始值
     */
    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "APPROVED";
        }
        return switch (action.trim().toLowerCase()) {
            case "approve", "approved" -> "APPROVED";
            case "reject", "rejected" -> "REJECTED";
            case "transfer", "transferred" -> "TRANSFERRED";
            default -> action;
        };
    }
}
