package com.workflow.service;

import com.workflow.dto.ProcessProgressDTO;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.history.HistoricActivityInstance;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.Execution;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

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
            // 流程已结束或不存在，查询历史记录
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
        
        // 7. 构建节点历史记录
        List<ProcessProgressDTO.NodeHistoryDTO> nodeHistory = historicActivities.stream()
                .filter(h -> !"sequenceFlow".equals(h.getActivityType())) // 排除连线
                .map(h -> {
                    ProcessProgressDTO.NodeHistoryDTO dto = new ProcessProgressDTO.NodeHistoryDTO();
                    dto.setNodeId(h.getActivityId());
                    dto.setNodeName(h.getActivityName());
                    dto.setNodeType(h.getActivityType());
                    dto.setAssignee(h.getAssignee());
                    dto.setStartTime(h.getStartTime() != null ? formatDate(h.getStartTime()) : null);
                    dto.setEndTime(h.getEndTime() != null ? formatDate(h.getEndTime()) : null);
                    dto.setDuration(h.getDurationInMillis());
                    dto.setStatus(h.getEndTime() != null ? "COMPLETED" : "ACTIVE");
                    return dto;
                })
                .collect(Collectors.toList());
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
        
        return progress;
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
                info.setAssigneeId(task.getAssignee());
                info.setAssigneeName(task.getAssignee()); // 暂时使用ID作为名称，后续可从用户表查询
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
            info.setAssigneeId(task.getAssignee());
            info.setAssigneeName(task.getAssignee()); // 暂时使用ID作为名称
            info.setHandleTime(task.getCreateTime() != null ? formatDate(task.getCreateTime()) : null);
            info.setAction("PROCESSING"); // 处理中
            info.setComment("待处理");
            
            assigneeMap.put(nodeId, info);
        }
        
        progress.setNodeAssigneeMap(assigneeMap);
    }
}
