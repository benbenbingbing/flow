package com.workflow.service;

import com.workflow.dto.ProcessProgressDTO;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import com.workflow.vo.ProcessDetailVO;
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
        if (detail.getProcessDefinitionId() != null) {
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionId(detail.getProcessDefinitionId())
                    .singleResult();
            if (processDefinition != null) {
                detail.setProcessName(processDefinition.getName());
            }
        }
        
        // 3. 获取流程实例基本信息
        if (historicInstance != null) {
            detail.setStartUser(historicInstance.getStartUserId());
            detail.setStartTime(formatDate(historicInstance.getStartTime()));
            detail.setBusinessKey(historicInstance.getBusinessKey());
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
            history.setAssignee(task.getAssignee());
            history.setAction("通过"); // 可根据变量判断实际动作
            history.setStartTime(formatDate(task.getStartTime()));
            history.setEndTime(formatDate(task.getEndTime()));
            history.setDuration(task.getDurationInMillis());
            historyList.add(history);
        }
        
        detail.setHistory(historyList);
        
        // 8. 获取表单数据（流程变量）
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
}
