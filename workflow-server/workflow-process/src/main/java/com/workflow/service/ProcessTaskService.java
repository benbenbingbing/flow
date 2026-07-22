package com.workflow.service;

import com.workflow.entity.ProcessTask;
import com.workflow.mapper.ProcessTaskMapper;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.springframework.context.annotation.Lazy;
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
public class ProcessTaskService {
    
    private final ProcessTaskMapper taskMapper;
    private final TaskService flowableTaskService;
    private final RuntimeService runtimeService;
    private final org.flowable.engine.RepositoryService repositoryService;
    private final com.workflow.mapper.NodeConfigMapper nodeConfigMapper;
    private final com.workflow.mapper.EntityDefinitionMapper entityDefinitionMapper;
    private final com.workflow.mapper.ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.workflow.service.EntityDataDynamicService entityDataDynamicService;
    private final com.workflow.mapper.SysGroupMapper sysGroupMapper;
    private final com.workflow.mapper.SysUserGroupMapper sysUserGroupMapper;
    private final com.workflow.mapper.SysUserMapper sysUserMapper;
    private final com.workflow.service.SysUserService sysUserService;
    
    public ProcessTaskService(ProcessTaskMapper taskMapper,
                              TaskService flowableTaskService,
                              RuntimeService runtimeService,
                              org.flowable.engine.RepositoryService repositoryService,
                              com.workflow.mapper.NodeConfigMapper nodeConfigMapper,
                              com.workflow.mapper.EntityDefinitionMapper entityDefinitionMapper,
                              com.workflow.mapper.ProcessDefinitionConfigMapper processDefinitionConfigMapper,
                              com.fasterxml.jackson.databind.ObjectMapper objectMapper,
                              @Lazy com.workflow.service.EntityDataDynamicService entityDataDynamicService,
                              com.workflow.mapper.SysGroupMapper sysGroupMapper,
                              com.workflow.mapper.SysUserGroupMapper sysUserGroupMapper,
                              com.workflow.mapper.SysUserMapper sysUserMapper,
                              com.workflow.service.SysUserService sysUserService) {
        this.taskMapper = taskMapper;
        this.flowableTaskService = flowableTaskService;
        this.runtimeService = runtimeService;
        this.repositoryService = repositoryService;
        this.nodeConfigMapper = nodeConfigMapper;
        this.entityDefinitionMapper = entityDefinitionMapper;
        this.processDefinitionConfigMapper = processDefinitionConfigMapper;
        this.objectMapper = objectMapper;
        this.entityDataDynamicService = entityDataDynamicService;
        this.sysGroupMapper = sysGroupMapper;
        this.sysUserGroupMapper = sysUserGroupMapper;
        this.sysUserMapper = sysUserMapper;
        this.sysUserService = sysUserService;
    }
    
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
        String assignee = delegateTask.getAssignee();
        task.setAssigneeId(assignee);
        
        if (assignee == null || assignee.isEmpty()) {
            // 如果没有指定执行人，检查候选组和候选人
            task.setAssigneeType("group");
            try {
                List<org.flowable.identitylink.api.IdentityLink> identityLinks = flowableTaskService.getIdentityLinksForTask(delegateTask.getId());
                List<String> groupIds = new java.util.ArrayList<>();
                List<String> groupMemberNames = new java.util.ArrayList<>();
                List<String> candidateUserIds = new java.util.ArrayList<>();
                for (org.flowable.identitylink.api.IdentityLink link : identityLinks) {
                    if (link.getGroupId() != null) {
                        groupIds.add(link.getGroupId());
                        String members = getGroupMemberNames(link.getGroupId());
                        if (members != null && !members.isEmpty()) {
                            for (String m : members.split(",")) {
                                if (!groupMemberNames.contains(m)) {
                                    groupMemberNames.add(m);
                                }
                            }
                        }
                    } else if (link.getUserId() != null) {
                        candidateUserIds.add(link.getUserId());
                    }
                }
                if (!groupIds.isEmpty()) {
                    task.setAssigneeId(String.join(",", groupIds));
                    task.setAssigneeName(groupMemberNames.isEmpty() ? String.join(",", groupIds) : String.join(",", groupMemberNames));
                } else if (!candidateUserIds.isEmpty()) {
                    task.setAssigneeId(String.join(",", candidateUserIds));
                    task.setAssigneeName(getUserNamesFromIds(candidateUserIds));
                }
            } catch (Exception e) {
                log.warn("获取任务候选人失败: {}", e.getMessage());
            }
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
        
        // 设置执行人 - 优先使用assignee，否则使用候选组/候选人
        String assignee = flowableTask.getAssignee();
        task.setAssigneeId(assignee);
        
        // 从变量中获取发起人信息
        String submitterName = (String) variables.get("submitterName");
        if (submitterName != null) {
            task.setAssigneeName(submitterName);
        }
        
        if (assignee == null || assignee.isEmpty()) {
            // 如果没有指定执行人，检查候选组和候选人
            task.setAssigneeType("group");
            try {
                List<org.flowable.identitylink.api.IdentityLink> identityLinks = flowableTaskService.getIdentityLinksForTask(flowableTask.getId());
                List<String> groupIds = new java.util.ArrayList<>();
                List<String> groupMemberNames = new java.util.ArrayList<>();
                List<String> candidateUserIds = new java.util.ArrayList<>();
                for (org.flowable.identitylink.api.IdentityLink link : identityLinks) {
                    if (link.getGroupId() != null) {
                        groupIds.add(link.getGroupId());
                        String members = getGroupMemberNames(link.getGroupId());
                        if (members != null && !members.isEmpty()) {
                            for (String m : members.split(",")) {
                                if (!groupMemberNames.contains(m)) {
                                    groupMemberNames.add(m);
                                }
                            }
                        }
                    } else if (link.getUserId() != null) {
                        candidateUserIds.add(link.getUserId());
                    }
                }
                if (!groupIds.isEmpty()) {
                    task.setAssigneeId(String.join(",", groupIds));
                    task.setAssigneeName(groupMemberNames.isEmpty() ? String.join(",", groupIds) : String.join(",", groupMemberNames));
                } else if (!candidateUserIds.isEmpty()) {
                    task.setAssigneeId(String.join(",", candidateUserIds));
                    task.setAssigneeName(getUserNamesFromIds(candidateUserIds));
                }
            } catch (Exception e) {
                log.warn("获取任务候选人失败: {}", e.getMessage());
            }
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
    public void completeTask(String taskId, String action, String comment, String actionLabel) {
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
        String status = ProcessTask.STATUS_DONE;
        if ("transfer".equals(action)) {
            status = ProcessTask.STATUS_TRANSFER;
        } else if ("skip".equals(action)) {
            status = ProcessTask.STATUS_SKIP;
        }

        task.setStatus(status);
        task.setAction(action);
        if (actionLabel != null && !actionLabel.isBlank()) {
            task.setActionLabel(actionLabel);
        }
        task.setComment(comment);
        task.setEndTime(LocalDateTime.now());
        task.setDuration(duration);
        task.setUpdateTime(LocalDateTime.now());

        taskMapper.updateById(task);

        log.info("完成流程待办: id={}, nodeName={}, action={}, actionLabel={}, duration={}ms",
                task.getId(), task.getNodeName(), action, actionLabel, duration);
    }

    /**
     * 完成流程待办（兼容旧调用，不保存操作显示文本）
     */
    @Transactional(rollbackFor = Exception.class)
    public void completeTask(String taskId, String action, String comment) {
        completeTask(taskId, action, comment, null);
    }
    
    /**
     * 转办任务
     * 更新本地待办的执行人为转办人
     */
    @Transactional(rollbackFor = Exception.class)
    public void transferTask(String taskId, String transferTo, String comment) {
        ProcessTask task = taskMapper.selectByTaskId(taskId);
        if (task == null) {
            log.warn("待办任务不存在: taskId={}", taskId);
            return;
        }
        
        // 更新执行人为转办人，保持待办状态
        task.setAssigneeId(transferTo);
        task.setAssigneeType("user");
        task.setAction("transfer");
        task.setComment(comment);
        task.setUpdateTime(LocalDateTime.now());
        
        taskMapper.updateById(task);
        
        log.info("转办本地待办: id={}, nodeName={}, transferTo={}", 
                task.getId(), task.getNodeName(), transferTo);
    }

    /**
     * 将 Flowable 已认领任务同步到本地待办和实体运行时字段。
     */
    @Transactional(rollbackFor = Exception.class)
    public void synchronizeClaimedTask(String taskId, String processInstanceId, String assignee) {
        ProcessTask task = taskMapper.selectByTaskIdForUpdate(taskId);
        if (task != null) {
            task.setAssigneeId(assignee);
            task.setAssigneeName(sysUserService.getDisplayName(assignee));
            task.setAssigneeType("user");
            task.setUpdateTime(LocalDateTime.now());
            taskMapper.updateById(task);
        } else {
            log.warn("认领任务缺少本地待办记录: taskId={}, processInstanceId={}", taskId, processInstanceId);
        }
        updateEntityCurrentTask(processInstanceId);
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
        } else {
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
        
        // 同步更新实体数据表的当前任务信息
        try {
            updateEntityCurrentTask(processInstanceId);
        } catch (Exception e) {
            log.warn("更新实体当前任务失败: {}", e.getMessage());
        }
    }
    
    /**
     * 更新实体数据表的当前任务ID和名称
     */
    private void updateEntityCurrentTask(String processInstanceId) {
        Map<String, Object> variables = Map.of();
        try {
            variables = runtimeService.getVariables(processInstanceId);
        } catch (Exception e) {
            log.debug("获取流程变量失败，使用本地待办兜底: processInstanceId={}, message={}", processInstanceId, e.getMessage());
        }

        String entityCode = (String) variables.get("entityCode");
        String entityDataId = (String) variables.get("entityDataId");
        if (entityCode == null || entityDataId == null) {
            List<ProcessTask> localTasks = taskMapper.selectByProcessInstance(processInstanceId);
            for (ProcessTask localTask : localTasks) {
                if (localTask.getEntityCode() != null && localTask.getEntityDataId() != null) {
                    entityCode = localTask.getEntityCode();
                    entityDataId = localTask.getEntityDataId();
                    break;
                }
            }
        }
        if (entityCode == null || entityDataId == null) {
            return;
        }
        
        // 查询当前活跃任务（取第一个）
        List<Task> activeTasks = flowableTaskService.createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .list();
        
        String currentTaskId = null;
        String currentTaskName = null;
        String currentTaskAssignee = null;
        if (!activeTasks.isEmpty()) {
            Task task = activeTasks.get(0);
            currentTaskId = task.getId();
            currentTaskName = task.getName();
            currentTaskAssignee = task.getAssignee();
        }
        
        entityDataDynamicService.updateCurrentTask(entityCode, entityDataId, currentTaskId, currentTaskName, currentTaskAssignee);
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
            // 使用 MP 的 deleteById 进行逻辑删除（@TableLogic 字段无法通过 updateById 更新）
            taskMapper.deleteById(task.getId());
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
     * 根据流程实例ID查询当前待办任务（status=0）
     */
    public ProcessTask getTodoTaskByProcessInstance(String processInstanceId) {
        return taskMapper.selectTodoTaskByProcessInstance(processInstanceId);
    }
    
    /**
     * 根据任务ID查询本地待办
     */
    public ProcessTask getTaskByTaskId(String taskId) {
        return taskMapper.selectByTaskId(taskId);
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
            List<String> names = new java.util.ArrayList<>();
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

}
