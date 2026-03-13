package com.workflow.service;

import com.workflow.entity.ProcessTask;
import com.workflow.mapper.ProcessTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 流程待办服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcessTaskService {
    
    private final ProcessTaskMapper taskMapper;
    private final TaskService flowableTaskService;
    private final RuntimeService runtimeService;
    private final org.flowable.engine.RepositoryService repositoryService;
    private final com.workflow.mapper.NodeConfigMapper nodeConfigMapper;
    private final com.workflow.mapper.EntityDefinitionMapper entityDefinitionMapper;
    private final com.workflow.mapper.ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    
    /**
     * 创建流程待办（用于监听器）
     * 当流程启动或流转到新节点时调用
     */
    @Transactional(rollbackFor = Exception.class)
    public ProcessTask createTask(org.flowable.task.service.delegate.DelegateTask delegateTask, Map<String, Object> variables) {
        ProcessTask task = new ProcessTask();
        task.setProcessInstanceId(delegateTask.getProcessInstanceId());
        task.setProcessDefinitionId(delegateTask.getProcessDefinitionId());
        task.setTaskId(delegateTask.getId());
        task.setNodeId(delegateTask.getTaskDefinitionKey());
        task.setNodeName(delegateTask.getName());
        task.setNodeType("USER_TASK");
        
        // 获取流程信息
        ProcessInstance processInstance = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(delegateTask.getProcessInstanceId())
                .singleResult();
        
        if (processInstance != null) {
            task.setProcessKey(processInstance.getProcessDefinitionKey());
            task.setBusinessKey(processInstance.getBusinessKey());
            
            // 获取流程定义名称
            try {
                String processKey = processInstance.getProcessDefinitionKey();
                com.workflow.entity.ProcessDefinitionConfig config = 
                    processDefinitionConfigMapper.findByProcessKey(processKey).orElse(null);
                if (config != null && config.getProcessName() != null) {
                    task.setProcessName(config.getProcessName());
                } else {
                    org.flowable.engine.repository.ProcessDefinition processDef = repositoryService
                            .createProcessDefinitionQuery()
                            .processDefinitionId(processInstance.getProcessDefinitionId())
                            .singleResult();
                    if (processDef != null) {
                        task.setProcessName(processDef.getName());
                    }
                }
            } catch (Exception e) {
                log.warn("获取流程定义名称失败: {}", e.getMessage());
            }
            
            // 从变量中获取业务数据
            task.setEntityCode((String) variables.get("entityCode"));
            task.setEntityDataId((String) variables.get("entityDataId"));
        }
        
        // 设置执行人
        task.setAssigneeId(delegateTask.getAssignee());
        if (task.getAssigneeId() == null) {
            task.setAssigneeType("group");
        } else {
            task.setAssigneeType("user");
        }
        
        task.setStatus(ProcessTask.STATUS_TODO);
        task.setStartTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        
        // 从节点配置获取表单信息
        try {
            String entityCode = task.getEntityCode();
            String nodeId = task.getNodeId();
            if (entityCode != null && nodeId != null) {
                com.workflow.entity.EntityDefinition entityDef = entityDefinitionMapper
                        .findByEntityCode(entityCode).orElse(null);
                if (entityDef != null && entityDef.getProcessDefinitionId() != null) {
                    com.workflow.entity.NodeConfig nodeConfig = nodeConfigMapper
                            .selectByNodeIdAndProcessId(nodeId, entityDef.getProcessDefinitionId());
                    if (nodeConfig != null && nodeConfig.getConfigJson() != null) {
                        com.fasterxml.jackson.databind.JsonNode config = objectMapper
                                .readTree(nodeConfig.getConfigJson());
                        if (config.has("formKey")) {
                            task.setFormKey(config.get("formKey").asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取节点表单配置失败: {}", e.getMessage());
        }
        
        taskMapper.insert(task);
        log.info("创建流程待办: processInstanceId={}, nodeName={}, taskId={}", 
                task.getProcessInstanceId(), task.getNodeName(), task.getId());
        
        return task;
    }
    
    /**
     * 创建流程待办
     * 当流程启动或流转到新节点时调用
     */
    @Transactional(rollbackFor = Exception.class)
    public ProcessTask createTask(Task flowableTask, Map<String, Object> variables) {
        ProcessTask task = new ProcessTask();
        task.setProcessInstanceId(flowableTask.getProcessInstanceId());
        task.setProcessDefinitionId(flowableTask.getProcessDefinitionId());
        task.setTaskId(flowableTask.getId());
        task.setNodeId(flowableTask.getTaskDefinitionKey());
        task.setNodeName(flowableTask.getName());
        task.setNodeType("USER_TASK");
        
        // 获取流程信息
        ProcessInstance processInstance = runtimeService
                .createProcessInstanceQuery()
                .processInstanceId(flowableTask.getProcessInstanceId())
                .singleResult();
        
        if (processInstance != null) {
            task.setProcessKey(processInstance.getProcessDefinitionKey());
            task.setBusinessKey(processInstance.getBusinessKey());
            
            // 获取流程定义名称 - 优先从ProcessDefinitionConfig获取
            try {
                String processKey = processInstance.getProcessDefinitionKey();
                com.workflow.entity.ProcessDefinitionConfig config = 
                    processDefinitionConfigMapper.findByProcessKey(processKey).orElse(null);
                if (config != null && config.getProcessName() != null) {
                    task.setProcessName(config.getProcessName());
                } else {
                    // 从Flowable获取
                    org.flowable.engine.repository.ProcessDefinition processDef = repositoryService
                            .createProcessDefinitionQuery()
                            .processDefinitionId(processInstance.getProcessDefinitionId())
                            .singleResult();
                    if (processDef != null) {
                        task.setProcessName(processDef.getName());
                    }
                }
            } catch (Exception e) {
                log.warn("获取流程定义名称失败: {}", e.getMessage());
            }
            
            // 从变量中获取业务数据
            task.setEntityCode((String) variables.get("entityCode"));
            task.setEntityDataId((String) variables.get("entityDataId"));
        }
        
        // 设置执行人 - 优先使用assignee，否则使用候选人
        String assignee = flowableTask.getAssignee();
        task.setAssigneeId(assignee);
        
        // 从变量中获取发起人信息
        String submitterName = (String) variables.get("submitterName");
        if (submitterName != null) {
            task.setAssigneeName(submitterName);
        }
        
        if (assignee == null || assignee.isEmpty()) {
            // 如果没有指定执行人，检查候选人
            task.setAssigneeType("group");
        } else {
            task.setAssigneeType("user");
        }
        
        task.setStatus(ProcessTask.STATUS_TODO);
        task.setStartTime(LocalDateTime.now());
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        
        // 从节点配置获取表单信息
        try {
            String entityCode = task.getEntityCode();
            String nodeId = task.getNodeId();
            if (entityCode != null && nodeId != null) {
                // 通过entityCode获取流程定义配置ID
                com.workflow.entity.EntityDefinition entityDef = entityDefinitionMapper
                        .findByEntityCode(entityCode).orElse(null);
                if (entityDef != null && entityDef.getProcessDefinitionId() != null) {
                    com.workflow.entity.NodeConfig nodeConfig = nodeConfigMapper
                            .selectByNodeIdAndProcessId(nodeId, entityDef.getProcessDefinitionId());
                    if (nodeConfig != null && nodeConfig.getConfigJson() != null) {
                        com.fasterxml.jackson.databind.JsonNode config = objectMapper
                                .readTree(nodeConfig.getConfigJson());
                        if (config.has("formKey")) {
                            task.setFormKey(config.get("formKey").asText());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取节点表单配置失败: {}", e.getMessage());
        }
        
        taskMapper.insert(task);
        log.info("创建流程待办: processInstanceId={}, processName={}, nodeName={}, taskId={}, assignee={}", 
                task.getProcessInstanceId(), task.getProcessName(), task.getNodeName(), task.getId(), task.getAssigneeId());
        
        return task;
    }
    
    /**
     * 完成流程待办
     * 当任务办理完成时调用
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskId, String action, String comment) {
        ProcessTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            log.warn("待办任务不存在: taskId={}", taskId);
            return;
        }
        
        // 计算处理时长
        Long duration = null;
        if (task.getStartTime() != null) {
            duration = java.time.Duration.between(task.getStartTime(), LocalDateTime.now()).toMillis();
        }
        
        // 更新状态
        Integer status = ProcessTask.STATUS_DONE;
        if ("transfer".equals(action)) {
            status = ProcessTask.STATUS_TRANSFER;
        } else if ("skip".equals(action)) {
            status = ProcessTask.STATUS_SKIP;
        }
        
        task.setStatus(status);
        task.setAction(action);
        task.setComment(comment);
        task.setEndTime(LocalDateTime.now());
        task.setDuration(duration);
        task.setUpdateTime(LocalDateTime.now());
        
        taskMapper.updateById(task);
        
        log.info("完成流程待办: id={}, nodeName={}, action={}, duration={}ms", 
                task.getId(), task.getNodeName(), action, duration);
    }
    
    /**
     * 同步Flowable任务到本地待办
     * 用于流程启动时同步初始任务
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncTasksFromFlowable(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isEmpty()) {
            log.warn("流程实例ID为空，无法同步任务");
            return;
        }
        
        // 查询Flowable的任务
        List<Task> flowableTasks = flowableTaskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .list();
        
        if (flowableTasks.isEmpty()) {
            log.debug("流程实例 {} 没有待同步的任务", processInstanceId);
            return;
        }
        
        Map<String, Object> variables = null;
        for (Task flowableTask : flowableTasks) {
            try {
                // 检查是否已存在
                ProcessTask existing = taskMapper.selectByTaskId(flowableTask.getId());
                if (existing != null) {
                    log.debug("任务 {} 已存在，跳过同步", flowableTask.getId());
                    continue;
                }
                
                // 延迟加载变量，只在第一次使用时获取
                if (variables == null) {
                    variables = runtimeService.getVariables(processInstanceId);
                }
                
                createTask(flowableTask, variables);
            } catch (Exception e) {
                log.error("同步任务 {} 失败: {}", flowableTask.getId(), e.getMessage(), e);
                // 继续同步其他任务
            }
        }
    }
    
    /**
     * 获取用户待办列表
     */
    public List<ProcessTask> getTodoList(String userId) {
        return taskMapper.selectTodoByUser(userId);
    }
    
    /**
     * 获取用户已办列表
     */
    public List<ProcessTask> getDoneList(String userId) {
        return taskMapper.selectDoneByUser(userId);
    }
    
    /**
     * 统计用户待办数
     */
    public Long countTodo(String userId) {
        return taskMapper.countTodoByUser(userId);
    }
    
    /**
     * 统计用户已办数
     */
    public Long countDone(String userId) {
        return taskMapper.countDoneByUser(userId);
    }
    
    /**
     * 删除流程实例的所有待办
     * 用于流程撤回时清理待办
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteTasksByProcessInstance(String processInstanceId) {
        if (processInstanceId == null || processInstanceId.isEmpty()) {
            log.warn("流程实例ID为空，无法删除待办");
            return;
        }
        // 查询该流程实例的所有待办
        List<ProcessTask> tasks = taskMapper.selectByProcessInstance(processInstanceId);
        
        for (ProcessTask task : tasks) {
            // 逻辑删除
            task.setDeleted(1);
            taskMapper.updateById(task);
            log.info("删除流程待办: taskId={}, processInstanceId={}", 
                    task.getTaskId(), processInstanceId);
        }
    }
    
    /**
     * 根据流程实例ID查询所有待办（包括已完成的）
     */
    public List<ProcessTask> getTasksByProcessInstance(String processInstanceId) {
        return taskMapper.selectByProcessInstance(processInstanceId);
    }
    
    /**
     * 根据任务ID查询本地待办
     */
    public ProcessTask getTaskByTaskId(String taskId) {
        return taskMapper.selectByTaskId(taskId);
    }

}
