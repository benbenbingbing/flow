package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.UserContext;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityData;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.ProcessTask;
import com.workflow.mapper.EntityDataMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.IdentityService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 实体数据服务
 * 处理实体数据的增删改查和流程关联
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDataService {
    
    private final EntityDataMapper dataMapper;
    private final EntityDefinitionMapper definitionMapper;
    private final ProcessDefinitionConfigMapper processDefinitionConfigMapper;
    private final RuntimeService runtimeService;
    private final RepositoryService repositoryService;
    private final IdentityService identityService;
    private final ObjectMapper objectMapper;
    private final ProcessTaskService processTaskService;
    private final EntityCodeGeneratorService codeGeneratorService;
    private final com.workflow.listener.MultiInstanceCollectionListener multiInstanceCollectionListener;
    private final WorkflowAutoSkipService workflowAutoSkipService;
    
    /**
     * 查询某实体的所有数据
     */
    @Transactional(readOnly = true)
    public List<EntityDataDTO> findByEntityCode(String entityCode) {
        return dataMapper.findByEntityCode(entityCode).stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * 根据ID查询
     */
    @Transactional(readOnly = true)
    public EntityDataDTO findById(String id) {
        EntityData data = dataMapper.selectById(id);
        if (data == null) {
            throw new RuntimeException("数据不存在: " + id);
        }
        return convertToDTO(data);
    }
    
    /**
     * 根据流程实例ID查询
     */
    @Transactional(readOnly = true)
    public EntityDataDTO findByProcessInstanceId(String processInstanceId) {
        EntityData data = dataMapper.findByProcessInstanceId(processInstanceId)
                .orElseThrow(() -> new RuntimeException("数据不存在: " + processInstanceId));
        return convertToDTO(data);
    }
    
    /**
     * 保存实体数据
     * 如果绑定了流程且startProcess为true，则同时发起流程
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityDataDTO save(EntityDataDTO dto) {
        EntityDefinition definition = definitionMapper.findByEntityCode(dto.getEntityCode())
                .orElseThrow(() -> new RuntimeException("实体不存在: " + dto.getEntityCode()));
        
        // 验证实体是否启用流程
        if (Boolean.TRUE.equals(dto.getStartProcess()) && 
            definition.getLifecycleMode() != EntityDefinition.LifecycleMode.WORKFLOW) {
            throw new RuntimeException("该实体未启用流程，无法发起流程");
        }
        
        // 获取当前登录用户作为发起人（如果DTO中没有提供）
        String currentUserId = dto.getSubmitterId();
        String currentUserName = dto.getSubmitterName();
        if (currentUserId == null || currentUserId.isEmpty()) {
            currentUserId = UserContext.getUserId();
            currentUserName = UserContext.getUsername();
            // 如果UserContext也没有，使用默认值
            if (currentUserId == null || currentUserId.isEmpty()) {
                currentUserId = "system";
                currentUserName = "系统用户";
            }
        }
        
        EntityData data = new EntityData();
        data.setEntityCode(dto.getEntityCode());
        data.setDataNo(generateDataNo(dto.getEntityCode()));
        data.setTitle(dto.getTitle());
        data.setSubmitterId(currentUserId);
        data.setSubmitterName(currentUserName);
        
        // 从data中提取name和code到标准字段
        if (dto.getData() != null) {
            data.setName((String) dto.getData().get("name"));
            data.setCode((String) dto.getData().get("code"));
        }
        
        // 设置创建人
        data.setCreatedBy(currentUserId);
        data.setUpdatedBy(currentUserId);
        
        // 将Map转换为JSON存储
        try {
            data.setDataJson(objectMapper.writeValueAsString(dto.getData()));
        } catch (Exception e) {
            throw new RuntimeException("数据序列化失败", e);
        }
        
        // 判断是否发起流程
        if (Boolean.TRUE.equals(dto.getStartProcess()) && 
            definition.getLifecycleMode() == EntityDefinition.LifecycleMode.WORKFLOW &&
            definition.getProcessDefinitionId() != null) {
            
            // 先保存数据获取ID（草稿状态）
            data.setStatus("草稿");
            dataMapper.insert(data);
            log.info("数据已保存为草稿，数据ID：{}", data.getId());
            
            try {
                // 查询流程定义，获取流程key
                com.workflow.entity.ProcessDefinitionConfig processConfig = 
                    processDefinitionConfigMapper.selectById(definition.getProcessDefinitionId());
                if (processConfig == null) {
                    throw new RuntimeException("绑定的流程配置不存在");
                }
                if (processConfig.getStatus() == com.workflow.entity.ProcessDefinitionConfig.ProcessStatus.DISABLED) {
                    throw new RuntimeException("流程已禁用，无法发起: " + processConfig.getProcessName());
                }
                
                // 查询Flowable最新的流程定义
                ProcessDefinition flowableProcess = repositoryService.createProcessDefinitionQuery()
                        .processDefinitionKey(processConfig.getProcessKey())
                        .latestVersion()
                        .singleResult();
                
                if (flowableProcess == null) {
                    throw new RuntimeException("流程未部署或已禁用，请先发布流程: " + processConfig.getProcessKey());
                }
                
                // 发起流程
                Map<String, Object> variables = dto.getData() != null ? dto.getData() : new java.util.HashMap<>();
                variables.put("entityDataId", data.getId());
                variables.put("entityCode", data.getEntityCode());
                // 设置发起人变量（兼容不同的BPMN配置）
                variables.put("submitterId", data.getSubmitterId());
                variables.put("initiator", data.getSubmitterId());
                variables.put("skipNodeEnabled", true);
                
                // 预计算多实例集合变量（根据节点配置的审批人自动计算）
                multiInstanceCollectionListener.prepareVariables(processConfig.getId(), variables);

                // 设置Flowable认证用户，使流程实例记录正确的startUserId
                identityService.setAuthenticatedUserId(data.getSubmitterId());
                
                ProcessInstance processInstance = runtimeService.startProcessInstanceById(
                        flowableProcess.getId(), 
                        variables
                );

                // 兜底：自动完成配置为跳过的节点（防止 flowable:skipExpression 未生效）。
                // 事件监听器负责中途到达的跳过节点，启动时由这里保证第一个跳过节点被处理
                // （ACTIVITY_STARTED 事件触发时任务可能尚未落库，监听器查不到任务）。
                workflowAutoSkipService.autoSkipNodes(processInstance.getId(), processConfig.getId());

                // 更新流程实例ID、状态和流程开始时间
                data.setProcessInstanceId(processInstance.getId());
                data.setStatus("审批中");  // 发起流程后状态为审批中
                data.setProcessStartTime(LocalDateTime.now());
                data.setUpdatedBy(currentUserId);
                dataMapper.updateById(data);
                
                // 同步创建流程待办 - 需要等待Flowable创建任务
                // 由于流程启动后任务可能还未立即创建，尝试多次同步
                syncTasksWithRetry(processInstance.getId(), 3);
                
                log.info("数据保存并发起流程成功，数据ID：{}，流程实例ID：{}，发起人：{}", 
                        data.getId(), processInstance.getId(), data.getSubmitterId());
                        
            } catch (Exception e) {
                // 流程启动失败，删除已保存的数据，保持数据一致性
                log.error("流程启动失败，删除已保存的数据: {}", data.getId(), e);
                dataMapper.deleteById(data.getId());
                throw new RuntimeException("流程启动失败: " + e.getMessage(), e);
            }
        } else {
            data.setStatus("草稿");  // 未发起流程状态为草稿
            dataMapper.insert(data);
            log.info("数据已保存为草稿（未发起流程），数据ID：{}", data.getId());
        }
        
        return convertToDTO(data);
    }
    
    /**
     * 更新实体数据
     */
    @Transactional
    public EntityDataDTO update(String id, EntityDataDTO dto) {
        EntityData existing = dataMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("数据不存在: " + id);
        }
        
        existing.setTitle(dto.getTitle());
        
        try {
            existing.setDataJson(objectMapper.writeValueAsString(dto.getData()));
        } catch (Exception e) {
            throw new RuntimeException("数据序列化失败", e);
        }
        
        dataMapper.updateById(existing);
        return convertToDTO(existing);
    }
    
    /**
     * 删除实体数据
     */
    @Transactional
    public void delete(String id) {
        dataMapper.deleteById(id);
    }
    
    /**
     * 生成数据编号
     * 使用编码规则生成服务
     */
    private String generateDataNo(String entityCode) {
        return codeGeneratorService.generateCode(entityCode);
    }
    
    /**
     * 转换为DTO
     */
    private EntityDataDTO convertToDTO(EntityData data) {
        EntityDataDTO dto = new EntityDataDTO();
        dto.setId(data.getId());
        dto.setEntityCode(data.getEntityCode());
        dto.setDataNo(data.getDataNo());
        dto.setTitle(data.getTitle());
        dto.setName(data.getName());
        dto.setCode(data.getCode());
        dto.setStatus(data.getStatus());
        dto.setProcessInstanceId(data.getProcessInstanceId());
        dto.setProcessStartTime(data.getProcessStartTime());
        dto.setProcessEndTime(data.getProcessEndTime());
        dto.setCurrentTaskId(data.getCurrentTaskId());
        dto.setCurrentTaskName(data.getCurrentTaskName());
        dto.setSubmitterId(data.getSubmitterId());
        dto.setSubmitterName(data.getSubmitterName());
        dto.setSubmitTime(data.getSubmitTime());
        dto.setCreatedAt(data.getCreatedAt());
        dto.setUpdatedAt(data.getUpdatedAt());
        dto.setCreatedBy(data.getCreatedBy());
        dto.setUpdatedBy(data.getUpdatedBy());
        
        // 将JSON转换为Map
        try {
            if (data.getDataJson() != null) {
                dto.setData(objectMapper.readValue(data.getDataJson(), Map.class));
            }
        } catch (Exception e) {
            log.error("数据反序列化失败: {}", data.getId(), e);
        }
        
        // 如果有流程实例ID，查询当前待办任务的审批人
        if (data.getProcessInstanceId() != null) {
            try {
                ProcessTask todoTask = processTaskService.getTodoTaskByProcessInstance(data.getProcessInstanceId());
                if (todoTask != null) {
                    dto.setCurrentTaskId(todoTask.getTaskId());
                    dto.setCurrentTaskName(todoTask.getNodeName());
                    dto.setCurrentTaskAssignee(todoTask.getAssigneeId());
                }
            } catch (Exception e) {
                log.warn("查询流程待办任务失败: {}", data.getProcessInstanceId(), e);
            }
        }
        
        return dto;
    }
    
    /**
     * 带重试的任务同步
     * 流程启动后任务可能不会立即创建，需要重试几次
     */
    private void syncTasksWithRetry(String processInstanceId, int maxRetries) {
        for (int i = 0; i < maxRetries; i++) {
            try {
                // 等待一段时间让Flowable创建任务
                Thread.sleep(300 * (i + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            
            processTaskService.syncTasksFromFlowable(processInstanceId);
            
            // 检查是否成功创建了待办 - 使用ProcessTaskService的方法
            List<ProcessTask> tasks = processTaskService.getTasksByProcessInstance(processInstanceId);
            if (!tasks.isEmpty()) {
                log.info("成功同步 {} 个待办任务", tasks.size());
                return;
            }
            
            if (i < maxRetries - 1) {
                log.warn("第 {} 次同步未找到任务，准备重试...", i + 1);
            } else {
                log.warn("同步任务失败，已达到最大重试次数，流程实例ID: {}", processInstanceId);
            }
        }
    }
}
