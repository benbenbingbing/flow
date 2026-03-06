package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDataDTO;
import com.workflow.entity.EntityData;
import com.workflow.entity.EntityDefinition;
import com.workflow.mapper.EntityDataMapper;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    private final ObjectMapper objectMapper;
    
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
    @Transactional
    public EntityDataDTO save(EntityDataDTO dto) {
        EntityDefinition definition = definitionMapper.findByEntityCode(dto.getEntityCode())
                .orElseThrow(() -> new RuntimeException("实体不存在: " + dto.getEntityCode()));
        
        EntityData data = new EntityData();
        data.setEntityCode(dto.getEntityCode());
        data.setDataNo(generateDataNo(dto.getEntityCode()));
        data.setTitle(dto.getTitle());
        data.setSubmitterId(dto.getSubmitterId());
        data.setSubmitterName(dto.getSubmitterName());
        
        // 将Map转换为JSON存储
        try {
            data.setDataJson(objectMapper.writeValueAsString(dto.getData()));
        } catch (Exception e) {
            throw new RuntimeException("数据序列化失败", e);
        }
        
        // 判断是否发起流程
        if (Boolean.TRUE.equals(dto.getStartProcess()) && 
            Boolean.TRUE.equals(definition.getEnableProcess()) &&
            definition.getProcessDefinitionId() != null) {
            
            // 先保存数据获取ID
            dataMapper.insert(data);
            
            // 查询流程定义，获取流程key
            com.workflow.entity.ProcessDefinitionConfig processConfig = 
                processDefinitionConfigMapper.selectById(definition.getProcessDefinitionId());
            if (processConfig == null) {
                throw new RuntimeException("绑定的流程不存在");
            }
            
            // 查询Flowable最新的流程定义
            ProcessDefinition flowableProcess = repositoryService.createProcessDefinitionQuery()
                    .processDefinitionKey(processConfig.getProcessKey())
                    .latestVersion()
                    .singleResult();
            
            if (flowableProcess == null) {
                throw new RuntimeException("流程未部署或已禁用，请先发布流程");
            }
            
            // 发起流程
            Map<String, Object> variables = dto.getData();
            variables.put("entityDataId", data.getId());
            variables.put("entityCode", data.getEntityCode());
            variables.put("submitterId", data.getSubmitterId());
            
            ProcessInstance processInstance = runtimeService.startProcessInstanceById(
                    flowableProcess.getId(), 
                    variables
            );
            
            // 更新流程实例ID
            data.setProcessInstanceId(processInstance.getId());
            data.setStatus(EntityData.DataStatus.PENDING);
            dataMapper.updateById(data);
            
            log.info("数据保存并发起流程成功，数据ID：{}，流程实例ID：{}", 
                    data.getId(), processInstance.getId());
        } else {
            data.setStatus(EntityData.DataStatus.DRAFT);
            dataMapper.insert(data);
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
     */
    private String generateDataNo(String entityCode) {
        String timestamp = String.valueOf(System.currentTimeMillis());
        return entityCode.toUpperCase() + "-" + timestamp.substring(timestamp.length() - 10);
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
        dto.setStatus(data.getStatus());
        dto.setProcessInstanceId(data.getProcessInstanceId());
        dto.setCurrentTaskId(data.getCurrentTaskId());
        dto.setCurrentTaskName(data.getCurrentTaskName());
        dto.setSubmitterId(data.getSubmitterId());
        dto.setSubmitterName(data.getSubmitterName());
        dto.setSubmitTime(data.getSubmitTime());
        dto.setCreatedAt(data.getCreatedAt());
        dto.setUpdatedAt(data.getUpdatedAt());
        
        // 将JSON转换为Map
        try {
            if (data.getDataJson() != null) {
                dto.setData(objectMapper.readValue(data.getDataJson(), Map.class));
            }
        } catch (Exception e) {
            log.error("数据反序列化失败: {}", data.getId(), e);
        }
        
        return dto;
    }
}
