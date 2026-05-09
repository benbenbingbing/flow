package com.workflow.service;

import com.workflow.common.PageResult;
import com.workflow.common.Result;
import com.workflow.dto.ProcessProgressDTO;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.vo.MyStartedProcessVO;
import com.workflow.vo.ProcessDetailVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.engine.history.HistoricProcessInstanceQuery;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 流程实例服务
 * 用于查询流程实例的执行进度、历史记录等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessInstanceService {
    
    private final RuntimeService runtimeService;
    private final HistoryService historyService;
    private final RepositoryService repositoryService;
    private final TaskService taskService;
    private final ProcessDefinitionConfigMapper processConfigMapper;
    private final SysUserService sysUserService;
    private final com.workflow.service.EntityDataService entityDataService;
    private final com.workflow.service.EntityDataDynamicService entityDataDynamicService;
    private final com.workflow.service.DynamicTableService dynamicTableService;
    private final com.workflow.mapper.EntityDataDynamicMapper entityDataDynamicMapper;
    private final com.workflow.mapper.EntityStatusMapper entityStatusMapper;
    private final com.workflow.service.EntityFormService entityFormService;
    private final com.workflow.mapper.NodeConfigMapper nodeConfigMapper;
    private final com.workflow.mapper.EntityDefinitionMapper entityDefinitionMapper;
    private final com.workflow.mapper.FormConfigMapper formConfigMapper;
    private final com.workflow.mapper.ProcessTaskMapper processTaskMapper;
    private final com.workflow.mapper.SysGroupMapper sysGroupMapper;
    private final com.workflow.mapper.SysUserGroupMapper sysUserGroupMapper;
    private final com.workflow.mapper.SysUserMapper sysUserMapper;
    private final com.workflow.mapper.ProcessOperationLogMapper operationLogMapper;
    private final ProcessTaskService processTaskService;
    private final com.workflow.mapper.ProcessNodeFormMapper nodeFormMapper;
    private final com.workflow.mapper.ProcessNodeApprovalMapper nodeApprovalMapper;
    
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * 格式化日期为字符串
     */
    private String formatDate(java.util.Date date) {
        if (date == null) return null;
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date);
    }
    
    /**
     * 获取流程实例的执行进度
     * 
     * @param processInstanceId 流程实例ID
     * @return 流程进度信息
     */
    public ProcessProgressDTO getProcessProgress(String processInstanceId) {
        ProcessProgressDTO progress = new ProcessProgressDTO();
        progress.setProcessInstanceId(processInstanceId);
        
        // 1. 获取流程实例信息
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        
        if (processInstance != null) {
            // 流程正在运行中
            progress.setProcessDefinitionId(processInstance.getProcessDefinitionId());
            progress.setStatus("RUNNING");
        } else {
            // 流程已结束或不存在，查询历史记录判断是否为终止
            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
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
            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
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
                    dto.setNodeId(h.getActivityId());
                    dto.setNodeName(h.getActivityName());
                    dto.setNodeType(h.getActivityType());
                    String assigneeId = h.getAssignee();
                    dto.setAssignee(assigneeId);
                    // 将用户ID转换为用户名称
                    if (assigneeId != null && !assigneeId.isEmpty() && !assigneeId.startsWith("${")) {
                        String nickname = sysUserService.getNicknameByUsername(assigneeId);
                        if (nickname != null && !nickname.equals(assigneeId)) {
                            dto.setAssigneeName(nickname);
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
                                var localTask = processTaskMapper.selectByTaskId(h.getTaskId());
                                if (localTask != null && localTask.getAction() != null) {
                                    action = localTask.getAction();
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
                                if ("approve".equals(action)) {
                                    dto.setAction("APPROVED");
                                } else if ("reject".equals(action)) {
                                    dto.setAction("REJECTED");
                                } else if ("transfer".equals(action)) {
                                    dto.setAction("TRANSFERRED");
                                } else {
                                    dto.setAction("APPROVED");
                                }
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
        
        // 合并多实例会签记录：相同 nodeId 的 userTask 合并为一条，显示所有执行人
        java.util.Map<String, java.util.List<ProcessProgressDTO.NodeHistoryDTO>> nodeGroup = new java.util.LinkedHashMap<>();
        for (ProcessProgressDTO.NodeHistoryDTO dto : nodeHistory) {
            nodeGroup.computeIfAbsent(dto.getNodeId(), k -> new java.util.ArrayList<>()).add(dto);
        }
        java.util.List<ProcessProgressDTO.NodeHistoryDTO> mergedHistory = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.util.List<ProcessProgressDTO.NodeHistoryDTO>> entry : nodeGroup.entrySet()) {
            java.util.List<ProcessProgressDTO.NodeHistoryDTO> list = entry.getValue();
            if (list.size() > 1 && "userTask".equals(list.get(0).getNodeType())) {
                ProcessProgressDTO.NodeHistoryDTO merged = new ProcessProgressDTO.NodeHistoryDTO();
                merged.setNodeId(list.get(0).getNodeId());
                merged.setNodeName(list.get(0).getNodeName());
                merged.setNodeType("userTask");
                
                java.util.Set<String> assignees = new java.util.LinkedHashSet<>();
                java.util.Set<String> assigneeNames = new java.util.LinkedHashSet<>();
                for (ProcessProgressDTO.NodeHistoryDTO d : list) {
                    if (d.getAssignee() != null) assignees.add(d.getAssignee());
                    if (d.getAssigneeName() != null) assigneeNames.add(d.getAssigneeName());
                }
                merged.setAssignee(String.join(",", assignees));
                merged.setAssigneeName(String.join(",", assigneeNames));
                
                boolean hasActive = list.stream().anyMatch(d -> "ACTIVE".equals(d.getStatus()));
                merged.setStatus(hasActive ? "ACTIVE" : "COMPLETED");
                
                boolean hasRejected = list.stream().anyMatch(d -> "REJECTED".equals(d.getAction()));
                boolean allApproved = list.stream().allMatch(d -> "APPROVED".equals(d.getAction()));
                if (hasRejected) merged.setAction("REJECTED");
                else if (allApproved) merged.setAction("APPROVED");
                
                merged.setStartTime(list.get(0).getStartTime());
                String latestEndTime = list.stream()
                    .map(ProcessProgressDTO.NodeHistoryDTO::getEndTime)
                    .filter(java.util.Objects::nonNull)
                    .max(String::compareTo)
                    .orElse(null);
                merged.setEndTime(latestEndTime);
                
                long totalDuration = list.stream()
                    .mapToLong(d -> d.getDuration() != null ? d.getDuration() : 0)
                    .sum();
                merged.setDuration(totalDuration > 0 ? totalDuration : null);
                
                String comments = list.stream()
                    .map(ProcessProgressDTO.NodeHistoryDTO::getComment)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.joining("; "));
                merged.setComment(comments.isEmpty() ? null : comments);
                
                mergedHistory.add(merged);
            } else {
                mergedHistory.addAll(list);
            }
        }
        nodeHistory = mergedHistory;
        
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
                dto.setAssigneeName(log.getOperatorName());
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
     * 根据流程实例ID获取BPMN XML（公共方法）
     * 
     * @param processInstanceId 流程实例ID
     * @return BPMN XML
     */
    public String getBpmnXmlByProcessInstanceId(String processInstanceId) {
        return getBpmnXmlByInstanceId(processInstanceId);
    }
    
    /**
     * 根据流程定义Key获取BPMN XML
     * 
     * @param processKey 流程标识
     * @return BPMN XML
     */
    public String getBpmnXmlByProcessKey(String processKey) {
        ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processKey).orElse(null);
        if (config != null && config.getBpmnXml() != null) {
            return config.getBpmnXml();
        }
        
        // 从 Flowable 获取
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processKey)
                .latestVersion()
                .singleResult();
        
        if (processDefinition != null) {
            try {
                org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinition.getId());
                // 需要转换为 XML，这里先返回 null，实际使用时从配置表获取
                return null;
            } catch (Exception e) {
                log.error("获取 BPMN XML 失败", e);
            }
        }
        return null;
    }
    
    /**
     * 构建节点处理人映射
     * 包含已完成节点的审批人信息和当前节点的处理人信息
     */
    private void buildNodeAssigneeMap(ProcessProgressDTO progress, String processInstanceId) {
        Map<String, ProcessProgressDTO.AssigneeInfoDTO> assigneeMap = new HashMap<>();
        
        // 1. 查询历史任务（已完成的任务）
        List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .finished()
                .list();
        
        for (HistoricTaskInstance task : historicTasks) {
            String nodeId = task.getTaskDefinitionKey();
            // 如果同一个节点有多个任务，保留最新的
            if (!assigneeMap.containsKey(nodeId) || 
                (task.getEndTime() != null && 
                 (assigneeMap.get(nodeId).getHandleTime() == null || 
                  formatDate(task.getEndTime()).compareTo(assigneeMap.get(nodeId).getHandleTime()) > 0))) {
                
                ProcessProgressDTO.AssigneeInfoDTO info = new ProcessProgressDTO.AssigneeInfoDTO();
                String userId = task.getAssignee();
                // 查询用户昵称
                String nickname = sysUserService.getNicknameByUsername(userId);
                info.setAssigneeId(userId);
                info.setAssigneeName(nickname != null ? nickname : userId);
                info.setHandleTime(task.getEndTime() != null ? formatDate(task.getEndTime()) : null);
                // 从流程变量中获取审批意见（如果有）
                info.setAction("APPROVED"); // 默认同意，实际可从变量中读取
                info.setComment(null); // 可从流程变量中读取审批意见
                
                assigneeMap.put(nodeId, info);
            }
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
                // 查询用户昵称
                String nickname = sysUserService.getNicknameByUsername(userId);
                info.setAssigneeId(userId);
                info.setAssigneeName(nickname != null ? nickname : userId);
            }
            
            info.setHandleTime(task.getCreateTime() != null ? formatDate(task.getCreateTime()) : null);
            info.setAction("PROCESSING"); // 处理中
            info.setComment("待处理");
            
            assigneeMap.put(nodeId, info);
        }
        
        progress.setNodeAssigneeMap(assigneeMap);
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
            
            // 3. 加载表单详情（优先级：process_node_form 映射表 > BPMN XML > 流程变量 > 默认）
            com.workflow.entity.EntityForm entityForm = null;
            Boolean nodeFormReadonly = null;
            
            // 3a. 最高优先级：从 process_node_form 映射表查询节点表单绑定
            if (processKey != null && !processKey.isEmpty() && targetNodeId != null && !targetNodeId.isEmpty()) {
                try {
                    ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processKey).orElse(null);
                    if (config != null) {
                        com.workflow.entity.ProcessNodeForm nodeForm = nodeFormMapper.selectByNodeId(config.getId(), targetNodeId);
                        if (nodeForm != null && nodeForm.getFormId() != null && !nodeForm.getFormId().isEmpty()) {
                            entityForm = entityFormService.getById(nodeForm.getFormId());
                            if (entityForm != null) {
                                nodeFormReadonly = nodeForm.getIsReadonly() != null && nodeForm.getIsReadonly() == 1;
                                log.info("从 process_node_form 映射表查询到节点表单: nodeId={}, formId={}, formName={}",
                                    targetNodeId, nodeForm.getFormId(), entityForm.getFormName());
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("从 process_node_form 查询节点表单失败: {}", e.getMessage());
                }
            }
            
            // 3b. 映射表中没有表单绑定，尝试使用默认表单
            if (entityForm == null) {
                entityForm = entityFormService.getDefaultForm(entityDef.getId());
                if (entityForm != null) {
                    log.debug("节点未配置表单，回退到默认表单: nodeId={}, formId={}", targetNodeId, entityForm.getId());
                }
            }
            
            if (entityForm == null) {
                log.warn("无法加载表单: entityId={}, nodeId={}", entityDef.getId(), targetNodeId);
                return;
            }
            
            // 6. 构建 FormConfigDTO
            ProcessProgressDTO.FormConfigDTO formConfig = new ProcessProgressDTO.FormConfigDTO();
            formConfig.setFormId(entityForm.getId());
            formConfig.setFormName(entityForm.getFormName());
            formConfig.setFormKey(entityForm.getFormKey());
            formConfig.setLayoutType(entityForm.getLayoutType());
            
            // 转换字段配置
            if (entityForm.getFields() != null) {
                List<Map<String, Object>> fields = new ArrayList<>();
                for (com.workflow.entity.EntityFormField field : entityForm.getFields()) {
                    Map<String, Object> fieldMap = new HashMap<>();
                    fieldMap.put("id", field.getId());
                    fieldMap.put("fieldCode", field.getFieldCode() != null ? field.getFieldCode() : field.getFieldId());
                    fieldMap.put("fieldName", field.getFieldName());
                    fieldMap.put("fieldLabel", field.getFieldLabel());
                    fieldMap.put("fieldType", field.getFieldType());
                    fieldMap.put("componentType", field.getComponentType());
                    fieldMap.put("isRequired", field.getIsRequired());
                    // 如果节点映射表配置了只读，覆盖字段的 isReadonly
                    if (nodeFormReadonly != null) {
                        fieldMap.put("isReadonly", nodeFormReadonly);
                    } else {
                        fieldMap.put("isReadonly", field.getIsReadonly());
                    }
                    fieldMap.put("isHidden", field.getIsHidden());
                    fieldMap.put("sortOrder", field.getSortOrder());
                    fieldMap.put("gridSpan", field.getGridSpan());
                    
                    // 组件属性保持原始 JSON 字符串，由前端解析
                    if (field.getComponentProps() != null) {
                        fieldMap.put("componentProps", field.getComponentProps());
                    }
                    fields.add(fieldMap);
                }
                formConfig.setFields(fields);
            }
            
            progress.setFormConfig(formConfig);
            log.debug("表单配置加载成功: entityCode={}, formKey={}, fieldsCount={}", 
                entityCode, entityForm.getFormKey(), 
                formConfig.getFields() != null ? formConfig.getFields().size() : 0);
            
        } catch (Exception e) {
            log.warn("加载表单配置失败: {}", e.getMessage(), e);
        }
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
                        
                        if (nodeApproval.getOptionsJson() != null && !nodeApproval.getOptionsJson().isEmpty()) {
                            com.fasterxml.jackson.databind.JsonNode configNode = 
                                new com.fasterxml.jackson.databind.ObjectMapper().readTree(nodeApproval.getOptionsJson());
                            if (configNode.isArray()) {
                                List<ProcessProgressDTO.ApprovalOptionDTO> options = new ArrayList<>();
                                for (com.fasterxml.jackson.databind.JsonNode optNode : configNode) {
                                    ProcessProgressDTO.ApprovalOptionDTO option = new ProcessProgressDTO.ApprovalOptionDTO();
                                    option.setValue(optNode.has("value") ? optNode.get("value").asText() : "");
                                    option.setLabel(optNode.has("label") ? optNode.get("label").asText() : "");
                                    option.setType(optNode.has("type") ? optNode.get("type").asText() : "primary");
                                    option.setShowComment(optNode.has("showComment") ? optNode.get("showComment").asBoolean() : true);
                                    options.add(option);
                                }
                                approvalConfig.setOptions(options);
                            }
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
     * 获取流程实例详情
     * 
     * @param instanceId 流程实例ID
     * @return 流程详情
     */
    public ProcessDetailVO getProcessDetail(String instanceId) {
        ProcessDetailVO detail = new ProcessDetailVO();
        detail.setInstanceId(instanceId);
        
        // 1. 获取流程实例信息
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        
        if (processInstance != null) {
            detail.setStatus("RUNNING");
            detail.setProcessDefinitionId(processInstance.getProcessDefinitionId());
        } else if (historicInstance != null) {
            detail.setStatus(historicInstance.getEndTime() != null ? "COMPLETED" : "SUSPENDED");
            detail.setProcessDefinitionId(historicInstance.getProcessDefinitionId());
        }
        
        // 2. 获取流程定义信息
        String processKey = null;
        if (detail.getProcessDefinitionId() != null) {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(detail.getProcessDefinitionId())
                    .singleResult();
            if (processDefinition != null) {
                // 优先使用流程定义名称，如果没有则从ProcessDefinitionConfig获取
                String processName = processDefinition.getName();
                processKey = processDefinition.getKey();
                if ((processName == null || processName.isEmpty()) && processKey != null) {
                    ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processKey).orElse(null);
                    if (config != null) {
                        processName = config.getProcessName();
                    }
                }
                detail.setProcessName(processName != null ? processName : processKey);
            }
        }
        
        // 3. 获取流程实例基本信息
        if (historicInstance != null) {
            // 获取发起人 - 优先从startUserId获取，如果没有则尝试从变量中获取
            String startUser = historicInstance.getStartUserId();
            if (startUser == null || startUser.isEmpty()) {
                try {
                    startUser = (String) historyService.createHistoricVariableInstanceQuery()
                            .processInstanceId(instanceId)
                            .variableName("initiator")
                            .singleResult()
                            .getValue();
                } catch (Exception e) {
                    // 忽略异常
                }
            }
            // 从流程任务中获取发起人作为后备
            if ((startUser == null || startUser.isEmpty()) && processKey != null) {
                ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processKey).orElse(null);
                if (config != null && config.getCreatedBy() != null) {
                    startUser = config.getCreatedBy();
                }
            }
            detail.setStartUser(startUser != null ? startUser : "系统");
            detail.setStartTime(formatDate(historicInstance.getStartTime()));
            detail.setBusinessKey(historicInstance.getBusinessKey() != null ? historicInstance.getBusinessKey() : "-");
        }
        
        // 4. 获取当前活动节点
        if (processInstance != null) {
            List<Execution> executions = runtimeService.createExecutionQuery()
                    .processInstanceId(instanceId)
                    .list();
            executions.stream()
                    .filter(e -> e.getActivityId() != null)
                    .findFirst()
                    .ifPresent(e -> {
                        detail.setCurrentNodeId(e.getActivityId());
                        // 从流程定义获取节点名称
                        String activityName = getActivityName(e.getActivityId(), detail.getProcessDefinitionId());
                        detail.setCurrentNode(activityName);
                    });
        }
        
        // 5. 获取 BPMN XML
        detail.setBpmnXml(getBpmnXmlByInstanceId(instanceId));
        
        // 6. 获取已完成节点
        List<HistoricActivityInstance> historicActivities = historyService.createHistoricActivityInstanceQuery()
                .processInstanceId(instanceId)
                .orderByHistoricActivityInstanceStartTime().asc()
                .list();
        
        List<String> completedNodes = historicActivities.stream()
                .filter(h -> h.getEndTime() != null && !"sequenceFlow".equals(h.getActivityType()))
                .map(HistoricActivityInstance::getActivityId)
                .distinct()
                .collect(Collectors.toList());
        detail.setCompletedNodes(completedNodes);
        
        // 7. 构建审批历史
        List<ProcessDetailVO.HistoryVO> historyList = new ArrayList<>();
        
        // 添加流程发起记录
        if (historicInstance != null) {
            ProcessDetailVO.HistoryVO startHistory = new ProcessDetailVO.HistoryVO();
            startHistory.setTaskName("流程发起");
            startHistory.setAssignee(historicInstance.getStartUserId());
            startHistory.setAction("发起");
            startHistory.setStartTime(formatDate(historicInstance.getStartTime()));
            startHistory.setEndTime(formatDate(historicInstance.getStartTime()));
            historyList.add(startHistory);
        }
        
        // 添加任务审批记录
        List<HistoricTaskInstance> historicTasks = historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(instanceId)
                .finished()
                .orderByHistoricTaskInstanceEndTime().asc()
                .list();
        
        for (HistoricTaskInstance task : historicTasks) {
            ProcessDetailVO.HistoryVO history = new ProcessDetailVO.HistoryVO();
            history.setTaskName(task.getName());
            String assigneeId = task.getAssignee();
            history.setAssignee(assigneeId);
            String nickname = sysUserService.getNicknameByUsername(assigneeId);
            if (nickname != null && !nickname.equals(assigneeId)) {
                history.setAssigneeName(nickname);
            }
            history.setAction("通过"); // 可根据变量判断实际动作
            history.setStartTime(formatDate(task.getStartTime()));
            history.setEndTime(formatDate(task.getEndTime()));
            history.setDuration(task.getDurationInMillis());
            
            // 查询该任务关联的历史变量（快照）
            try {
                java.util.List<org.flowable.variable.api.history.HistoricVariableInstance> taskVars = historyService.createHistoricVariableInstanceQuery()
                        .taskId(task.getId())
                        .list();
                // 脚本任务等自动节点的变量可能 TASK_ID_ 为空，按 executionId 兜底
                if (taskVars.isEmpty() && task.getExecutionId() != null) {
                    taskVars = historyService.createHistoricVariableInstanceQuery()
                            .executionId(task.getExecutionId())
                            .list();
                }
                if (!taskVars.isEmpty()) {
                    java.util.Map<String, Object> vars = new java.util.HashMap<>();
                    for (org.flowable.variable.api.history.HistoricVariableInstance var : taskVars) {
                        vars.put(var.getVariableName(), var.getValue());
                    }
                    history.setVariables(vars);
                }
            } catch (Exception e) {
                log.warn("查询任务变量失败: taskId={}", task.getId(), e);
            }
            
            historyList.add(history);
        }
        
        // 合并多实例会签记录：相同 taskName 的记录合并为一条，显示所有执行人
        java.util.Map<String, java.util.List<ProcessDetailVO.HistoryVO>> historyGroup = new java.util.LinkedHashMap<>();
        for (ProcessDetailVO.HistoryVO h : historyList) {
            historyGroup.computeIfAbsent(h.getTaskName(), k -> new java.util.ArrayList<>()).add(h);
        }
        java.util.List<ProcessDetailVO.HistoryVO> mergedHistory = new java.util.ArrayList<>();
        for (java.util.Map.Entry<String, java.util.List<ProcessDetailVO.HistoryVO>> entry : historyGroup.entrySet()) {
            java.util.List<ProcessDetailVO.HistoryVO> list = entry.getValue();
            if (list.size() > 1 && !"流程发起".equals(list.get(0).getTaskName())) {
                ProcessDetailVO.HistoryVO merged = new ProcessDetailVO.HistoryVO();
                merged.setTaskName(list.get(0).getTaskName());
                
                java.util.Set<String> assignees = new java.util.LinkedHashSet<>();
                java.util.Set<String> assigneeNames = new java.util.LinkedHashSet<>();
                for (ProcessDetailVO.HistoryVO h : list) {
                    if (h.getAssignee() != null) assignees.add(h.getAssignee());
                    if (h.getAssigneeName() != null) assigneeNames.add(h.getAssigneeName());
                }
                merged.setAssignee(String.join(",", assignees));
                merged.setAssigneeName(String.join(",", assigneeNames));
                
                boolean hasActive = list.stream().anyMatch(h -> h.getEndTime() == null);
                merged.setAction(hasActive ? "进行中" : "通过");
                
                merged.setStartTime(list.get(0).getStartTime());
                String latestEndTime = list.stream()
                    .map(ProcessDetailVO.HistoryVO::getEndTime)
                    .filter(java.util.Objects::nonNull)
                    .max(String::compareTo)
                    .orElse(null);
                merged.setEndTime(latestEndTime);
                
                long totalDuration = list.stream()
                    .mapToLong(h -> h.getDuration() != null ? h.getDuration() : 0)
                    .sum();
                merged.setDuration(totalDuration > 0 ? totalDuration : null);
                
                String comments = list.stream()
                    .map(ProcessDetailVO.HistoryVO::getComment)
                    .filter(java.util.Objects::nonNull)
                    .collect(java.util.stream.Collectors.joining("; "));
                merged.setComment(comments.isEmpty() ? null : comments);
                
                // 合并变量：收集所有子任务的变量
                java.util.Map<String, Object> mergedVars = new java.util.LinkedHashMap<>();
                for (ProcessDetailVO.HistoryVO h : list) {
                    if (h.getVariables() != null) {
                        mergedVars.putAll(h.getVariables());
                    }
                }
                if (!mergedVars.isEmpty()) {
                    merged.setVariables(mergedVars);
                }
                
                mergedHistory.add(merged);
            } else {
                mergedHistory.addAll(list);
            }
        }
        historyList = mergedHistory;
        
        detail.setHistory(historyList);
        
        // 8. 构建节点处理人映射
        Map<String, ProcessDetailVO.AssigneeVO> nodeAssigneeMap = new HashMap<>();
        
        // 添加已完成节点的处理人信息
        for (HistoricTaskInstance task : historicTasks) {
            ProcessDetailVO.AssigneeVO assignee = new ProcessDetailVO.AssigneeVO();
            String userId = task.getAssignee();
            // 查询用户昵称
            String nickname = sysUserService.getNicknameByUsername(userId);
            assignee.setAssigneeId(userId);
            assignee.setAssigneeName(nickname != null ? nickname : userId);
            assignee.setHandleTime(formatDate(task.getEndTime()));
            assignee.setAction("通过");
            assignee.setStatus("completed");
            nodeAssigneeMap.put(task.getTaskDefinitionKey(), assignee);
        }
        
        // 添加当前活动节点的处理人信息
        if (processInstance != null) {
            List<Task> activeTasks = taskService.createTaskQuery()
                    .processInstanceId(instanceId)
                    .list();
            for (Task task : activeTasks) {
                ProcessDetailVO.AssigneeVO assignee = new ProcessDetailVO.AssigneeVO();
                String userId = task.getAssignee();
                if (userId != null && !userId.isEmpty()) {
                    // 查询用户昵称
                    String nickname = sysUserService.getNicknameByUsername(userId);
                    assignee.setAssigneeId(userId);
                    assignee.setAssigneeName(nickname != null ? nickname : userId);
                } else {
                    // 候选组/候选人任务
                    try {
                        List<org.flowable.identitylink.api.IdentityLink> identityLinks = taskService.getIdentityLinksForTask(task.getId());
                        List<String> groupIds = new java.util.ArrayList<>();
                        List<String> groupNames = new java.util.ArrayList<>();
                        List<String> candidateUserIds = new java.util.ArrayList<>();
                        for (org.flowable.identitylink.api.IdentityLink link : identityLinks) {
                            if (link.getGroupId() != null) {
                                groupIds.add(link.getGroupId());
                                com.workflow.entity.SysGroup group = sysGroupMapper.selectByGroupCode(link.getGroupId());
                                groupNames.add(group != null ? group.getGroupName() : link.getGroupId());
                            } else if (link.getUserId() != null) {
                                candidateUserIds.add(link.getUserId());
                            }
                        }
                        if (!groupIds.isEmpty()) {
                            assignee.setAssigneeId(String.join(",", groupIds));
                            assignee.setAssigneeName(String.join(",", groupNames) + "（组任务）");
                        } else if (!candidateUserIds.isEmpty()) {
                            assignee.setAssigneeId(String.join(",", candidateUserIds));
                            assignee.setAssigneeName(String.join(",", candidateUserIds) + "（候选）");
                        } else {
                            assignee.setAssigneeId("");
                            assignee.setAssigneeName("未分配");
                        }
                    } catch (Exception e) {
                        assignee.setAssigneeId("");
                        assignee.setAssigneeName("未分配");
                    }
                }
                assignee.setHandleTime(formatDate(task.getCreateTime()));
                assignee.setAction("待处理");
                assignee.setStatus("processing");
                nodeAssigneeMap.put(task.getTaskDefinitionKey(), assignee);
            }
        }
        
        detail.setNodeAssigneeMap(nodeAssigneeMap);
        
        // 9. 获取表单数据（流程变量）
        if (historicInstance != null) {
            Map<String, Object> variables = historicInstance.getProcessVariables();
            if (variables != null) {
                // 过滤掉系统变量
                Map<String, Object> formData = variables.entrySet().stream()
                        .filter(e -> !e.getKey().startsWith("flowable_") && !e.getKey().startsWith("_"))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                detail.setFormData(formData);
            }
        }
        
        return detail;
    }
    
    /**
     * 获取活动节点名称
     */
    private String getActivityName(String activityId, String processDefinitionId) {
        try {
            org.flowable.bpmn.model.BpmnModel bpmnModel = repositoryService.getBpmnModel(processDefinitionId);
            if (bpmnModel != null) {
                org.flowable.bpmn.model.FlowElement element = bpmnModel.getFlowElement(activityId);
                if (element != null) {
                    return element.getName();
                }
            }
        } catch (Exception e) {
            log.warn("获取节点名称失败: activityId={}", activityId, e);
        }
        return activityId;
    }
    
    /**
     * 根据流程实例ID获取BPMN XML
     */
    private String getBpmnXmlByInstanceId(String instanceId) {
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(instanceId)
                .singleResult();
        
        if (historicInstance == null) {
            return null;
        }
        
        String processDefinitionId = historicInstance.getProcessDefinitionId();
        return getBpmnXmlByProcessDefinitionId(processDefinitionId);
    }
    
    /**
     * 根据流程定义ID获取BPMN XML
     */
    private String getBpmnXmlByProcessDefinitionId(String processDefinitionId) {
        if (processDefinitionId == null) {
            return null;
        }
        
        try {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId)
                    .singleResult();
            
            if (processDefinition == null) {
                return null;
            }
            
            // 先从 Model 获取
            try {
                org.flowable.engine.repository.Model model = repositoryService.getModel(processDefinition.getId());
                if (model != null) {
                    byte[] modelBytes = repositoryService.getModelEditorSource(model.getId());
                    if (modelBytes != null) {
                        return new String(modelBytes, java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            } catch (Exception e) {
                log.debug("无法从 Model 获取 BPMN XML", e);
            }
            
            // 从部署资源获取
            String resourceName = processDefinition.getResourceName();
            if (resourceName != null) {
                org.flowable.engine.repository.Deployment deployment = repositoryService.createDeploymentQuery()
                        .deploymentId(processDefinition.getDeploymentId())
                        .singleResult();
                if (deployment != null) {
                    java.io.InputStream resourceStream = repositoryService.getResourceAsStream(
                            deployment.getId(), resourceName);
                    if (resourceStream != null) {
                        return new String(resourceStream.readAllBytes(), 
                                java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取 BPMN XML 失败", e);
        }
        
        return null;
    }
    
    /**
     * 获取我发起的流程列表
     * 
     * @param userId 用户ID
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @param processName 流程名称（可选筛选）
     * @return 流程列表
     */
    public PageResult<MyStartedProcessVO> getMyStartedList(String userId, Integer pageNum, Integer pageSize, String processName) {
        // 查询历史流程实例（包含运行中和已结束的）
        HistoricProcessInstanceQuery query = historyService.createHistoricProcessInstanceQuery()
                .startedBy(userId)
                .orderByProcessInstanceStartTime()
                .desc();
        
        // 获取总数
        long total = query.count();
        
        // 分页查询
        int firstResult = (pageNum - 1) * pageSize;
        List<HistoricProcessInstance> historicInstances = query.listPage(firstResult, pageSize);
        
        // 转换为VO
        List<MyStartedProcessVO> list = new ArrayList<>();
        for (HistoricProcessInstance historicInstance : historicInstances) {
            MyStartedProcessVO vo = new MyStartedProcessVO();
            vo.setProcessInstanceId(historicInstance.getId());
            vo.setProcessDefinitionId(historicInstance.getProcessDefinitionId());
            vo.setBusinessKey(historicInstance.getBusinessKey());
            vo.setStartUser(historicInstance.getStartUserId());
            vo.setStartTime(formatDate(historicInstance.getStartTime()));
            vo.setEndTime(formatDate(historicInstance.getEndTime()));
            
            // 获取流程名称
            String processDefinitionId = historicInstance.getProcessDefinitionId();
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(processDefinitionId)
                    .singleResult();
            if (processDefinition != null) {
                vo.setProcessKey(processDefinition.getKey());
                String procName = processDefinition.getName();
                if (procName == null || procName.isEmpty()) {
                    ProcessDefinitionConfig config = processConfigMapper.findByProcessKey(processDefinition.getKey()).orElse(null);
                    if (config != null) {
                        procName = config.getProcessName();
                    }
                }
                vo.setProcessName(procName != null ? procName : processDefinition.getKey());
                
                // 流程名称筛选
                if (processName != null && !processName.isEmpty() && 
                    (vo.getProcessName() == null || !vo.getProcessName().contains(processName))) {
                    continue;
                }
            }
            
            // 获取数据标题（从实体数据）
            try {
                String entityDataId = (String) historicInstance.getProcessVariables().get("entityDataId");
                String entityCode = (String) historicInstance.getProcessVariables().get("entityCode");
                if (entityDataId == null) {
                    // 从历史变量查询
                    var varInstance = historyService.createHistoricVariableInstanceQuery()
                            .processInstanceId(historicInstance.getId())
                            .variableName("entityDataId")
                            .singleResult();
                    if (varInstance != null) {
                        entityDataId = (String) varInstance.getValue();
                    }
                }
                if (entityCode == null) {
                    var codeVar = historyService.createHistoricVariableInstanceQuery()
                            .processInstanceId(historicInstance.getId())
                            .variableName("entityCode")
                            .singleResult();
                    if (codeVar != null) {
                        entityCode = (String) codeVar.getValue();
                    }
                }
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
                    }
                }
            } catch (Exception e) {
                log.debug("获取数据标题失败: {}", e.getMessage());
            }
            
            // 判断流程状态
            ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(historicInstance.getId())
                    .singleResult();
            
            if (processInstance != null) {
                // 流程还在运行中
                if (processInstance.isSuspended()) {
                    vo.setStatus("SUSPENDED");
                    vo.setStatusText("已挂起");
                } else {
                    vo.setStatus("RUNNING");
                    vo.setStatusText("运行中");
                }
                
                // 获取当前节点
                List<Execution> executions = runtimeService.createExecutionQuery()
                        .processInstanceId(historicInstance.getId())
                        .list();
                String currentNode = executions.stream()
                        .filter(e -> e.getActivityId() != null)
                        .map(e -> getActivityName(e.getActivityId(), processDefinitionId))
                        .findFirst()
                        .orElse("处理中");
                vo.setCurrentNodeName(currentNode);
            } else {
                // 流程已结束
                if (historicInstance.getEndTime() != null) {
                    // 检查是否是终止（通过检查删除原因）
                    String deleteReason = historicInstance.getDeleteReason();
                    if (deleteReason != null && (deleteReason.contains("终止") || deleteReason.contains("terminated"))) {
                        vo.setStatus("TERMINATED");
                        vo.setStatusText("已终止");
                    } else {
                        vo.setStatus("COMPLETED");
                        vo.setStatusText("已完成");
                    }
                    vo.setCurrentNodeName("-");
                } else {
                    vo.setStatus("UNKNOWN");
                    vo.setStatusText("未知");
                }
            }
            
            list.add(vo);
        }
        
        // 由于可能在循环中过滤，需要重新计算分页
        // 为了简化，这里不做精确分页，如果需要精确分页需要在外层查询后统一过滤
        return new PageResult<>(list, total, pageNum, pageSize);
    }
    
    /**
     * 终止流程实例
     * 
     * @param processInstanceId 流程实例ID
     * @param userId 操作用户ID
     * @param reason 终止原因
     * @return 是否成功
     */
    public Result<Void> terminateProcess(String processInstanceId, String userId, String reason) {
        // 1. 验证流程实例是否存在
        ProcessInstance processInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        
        if (processInstance == null) {
            // 检查是否已结束
            HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                    .processInstanceId(processInstanceId)
                    .singleResult();
            if (historicInstance == null) {
                return Result.error(404, "流程实例不存在");
            }
            if (historicInstance.getEndTime() != null) {
                return Result.error(400, "流程已结束，无法终止");
            }
        }
        
        // 2. 验证是否是发起人（可选，根据业务需求）
        HistoricProcessInstance historicInstance = historyService.createHistoricProcessInstanceQuery()
                .processInstanceId(processInstanceId)
                .singleResult();
        if (historicInstance != null && !userId.equals(historicInstance.getStartUserId())) {
            return Result.error(403, "只有发起人可以终止流程");
        }
        
        // 3. 终止流程
        try {
            String deleteReason = "发起人主动终止";
            if (reason != null && !reason.isEmpty()) {
                deleteReason = reason;
            }
            runtimeService.deleteProcessInstance(processInstanceId, deleteReason);
            // 清理本地待办
            processTaskService.deleteTasksByProcessInstance(processInstanceId);
            
            // 记录终止日志到 process_operation_log
            try {
                com.workflow.entity.ProcessOperationLog log = new com.workflow.entity.ProcessOperationLog();
                log.setProcessInstanceId(processInstanceId);
                log.setOperationType("TERMINATE");
                log.setOperatorId(userId);
                String operatorName = sysUserService.getNicknameByUsername(userId);
                log.setOperatorName(operatorName != null ? operatorName : userId);
                log.setOperationTime(LocalDateTime.now());
                log.setOperationComment(deleteReason);
                operationLogMapper.insert(log);
            } catch (Exception e) {
                log.warn("记录终止日志失败", e);
            }
            
            // 4. 更新实体数据状态为终止
            try {
                String entityCode = (String) runtimeService.getVariable(processInstanceId, "entityCode");
                String entityDataId = (String) runtimeService.getVariable(processInstanceId, "entityDataId");
                if (entityCode != null && entityDataId != null) {
                    String tableName = dynamicTableService.getTableName(entityCode);
                    java.util.Map<String, Object> updateData = new java.util.HashMap<>();
                    updateData.put("id", entityDataId);
                    updateData.put("process_end_time", LocalDateTime.now());
                    updateData.put("updated_at", LocalDateTime.now());
                    // 获取终止状态码
                    java.util.List<com.workflow.entity.EntityStatus> statuses = entityStatusMapper.findByCategory(entityCode, "TERMINATED");
                    String terminatedStatus = (statuses != null && !statuses.isEmpty()) ? statuses.get(0).getStatusCode() : "REJECTED";
                    updateData.put("status", terminatedStatus);
                    updateData.put("current_task_id", null);
                    updateData.put("current_task_name", null);
                    updateData.put("current_task_assignee", null);
                    entityDataDynamicMapper.update(tableName, updateData);
                    log.info("流程终止，已更新实体数据状态: entityCode={}, entityDataId={}, status={}", entityCode, entityDataId, terminatedStatus);
                }
            } catch (Exception ex) {
                log.warn("终止流程后更新实体数据状态失败: processInstanceId={}", processInstanceId, ex);
            }
            
            log.info("流程终止成功: processInstanceId={}, userId={}, reason={}", processInstanceId, userId, deleteReason);
            return Result.success(null);
        } catch (Exception e) {
            log.error("流程终止失败: processInstanceId={}, userId={}", processInstanceId, userId, e);
            return Result.error(500, "流程终止失败: " + e.getMessage());
        }
    }

    /**
     * 获取组成员昵称列表（去重）
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
            List<String> nicknames = new ArrayList<>();
            for (String userId : userIds) {
                com.workflow.entity.SysUser user = sysUserMapper.selectById(userId);
                if (user != null) {
                    String name = user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : user.getUsername();
                    if (!nicknames.contains(name)) {
                        nicknames.add(name);
                    }
                }
            }
            return nicknames.isEmpty() ? group.getGroupName() : String.join(",", nicknames);
        } catch (Exception e) {
            log.warn("获取组成员失败: {}", groupCode, e);
            return groupCode;
        }
    }

    /**
     * 根据用户ID/用户名列表获取昵称列表
     */
    private String getUserNamesFromIds(List<String> idsOrNames) {
        List<String> names = new ArrayList<>();
        for (String value : idsOrNames) {
            try {
                com.workflow.entity.SysUser user = sysUserMapper.selectByUsername(value);
                if (user == null) {
                    user = sysUserMapper.selectById(value);
                }
                String name = user != null && user.getNickname() != null && !user.getNickname().isEmpty() ? user.getNickname() : value;
                if (!names.contains(name)) {
                    names.add(name);
                }
            } catch (Exception e) {
                names.add(value);
            }
        }
        return String.join(",", names);
    }
}
