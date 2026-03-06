package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.dto.EntityDefinitionDTO;
import com.workflow.dto.EntityFieldDTO;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityField;
import com.workflow.entity.ProcessDefinitionConfig;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFieldMapper;
import com.workflow.mapper.ProcessDefinitionConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 实体定义服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityDefinitionService {
    
    private final EntityDefinitionMapper entityMapper;
    private final EntityFieldMapper fieldMapper;
    private final ProcessDefinitionConfigMapper processMapper;
    private final ObjectMapper objectMapper;
    
    /**
     * 查询所有实体定义
     */
    @Transactional(readOnly = true)
    public List<EntityDefinitionDTO> findAll() {
        List<EntityDefinition> list = entityMapper.selectList(null);
        
        // 批量查询流程信息，避免N+1问题
        List<String> processIds = list.stream()
                .map(EntityDefinition::getProcessDefinitionId)
                .filter(id -> id != null && !id.isEmpty())
                .distinct()
                .collect(Collectors.toList());
        
        Map<String, String> processNameMap = processIds.stream()
                .map(id -> processMapper.selectById(id))
                .filter(process -> process != null)
                .collect(Collectors.toMap(
                    ProcessDefinitionConfig::getId,
                    ProcessDefinitionConfig::getProcessName,
                    (v1, v2) -> v1
                ));
        
        return list.stream()
                .map(entity -> convertToDTO(entity, processNameMap.get(entity.getProcessDefinitionId())))
                .collect(Collectors.toList());
    }
    
    /**
     * 根据ID查询
     */
    @Transactional(readOnly = true)
    public EntityDefinitionDTO findById(String id) {
        EntityDefinition entity = entityMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("实体不存在: " + id);
        }
        // 加载字段
        List<EntityField> fields = fieldMapper.findByEntityId(id);
        entity.setFields(fields);
        // 查询流程名称
        String processName = getProcessName(entity.getProcessDefinitionId());
        return convertToDTO(entity, processName);
    }
    
    /**
     * 根据编码查询
     */
    @Transactional(readOnly = true)
    public EntityDefinitionDTO findByCode(String code) {
        EntityDefinition entity = entityMapper.findByEntityCode(code)
                .orElseThrow(() -> new RuntimeException("实体不存在: " + code));
        // 加载字段
        List<EntityField> fields = fieldMapper.findByEntityId(entity.getId());
        entity.setFields(fields);
        // 查询流程名称
        String processName = getProcessName(entity.getProcessDefinitionId());
        return convertToDTO(entity, processName);
    }
    
    private String getProcessName(String processId) {
        if (processId == null || processId.isEmpty()) {
            return null;
        }
        ProcessDefinitionConfig process = processMapper.selectById(processId);
        return process != null ? process.getProcessName() : null;
    }
    
    /**
     * 保存实体定义
     */
    @Transactional
    public EntityDefinitionDTO save(EntityDefinitionDTO dto) {
        // 校验字段编码唯一性
        validateFieldCodeUnique(dto.getFields());
        
        EntityDefinition entity = convertToEntity(dto);
        entityMapper.insert(entity);
        
        // 保存字段（新建时确保字段ID为空，避免重复使用旧ID）
        if (dto.getFields() != null) {
            for (EntityFieldDTO fieldDTO : dto.getFields()) {
                EntityField field = convertToEntity(fieldDTO);
                field.setId(null); // 新建实体时，字段ID必须为空
                field.setEntityId(entity.getId());
                fieldMapper.insert(field);
            }
        }
        
        return convertToDTO(entity);
    }
    
    /**
     * 校验字段编码唯一性（同一实体内字段编码不能重复）
     */
    private void validateFieldCodeUnique(List<EntityFieldDTO> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        
        java.util.Set<String> fieldCodes = new java.util.HashSet<>();
        for (EntityFieldDTO field : fields) {
            if (field.getFieldCode() == null || field.getFieldCode().trim().isEmpty()) {
                continue;
            }
            String code = field.getFieldCode().trim();
            if (!fieldCodes.add(code)) {
                throw new RuntimeException("字段编码 [" + code + "] 已存在，同一实体内字段编码不能重复，请修改后重试");
            }
        }
    }
    
    /**
     * 更新实体定义
     */
    @Transactional
    public EntityDefinitionDTO update(String id, EntityDefinitionDTO dto) {
        // 校验字段编码唯一性
        validateFieldCodeUnique(dto.getFields());
        
        EntityDefinition existing = entityMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("实体不存在: " + id);
        }
        
        existing.setEntityName(dto.getEntityName());
        existing.setDescription(dto.getDescription());
        existing.setProcessDefinitionId(dto.getProcessDefinitionId());
        existing.setEnableProcess(dto.getEnableProcess());
        
        entityMapper.updateById(existing);
        
        // 更新字段：先删除旧字段，再保存新字段（ID置空确保创建新记录）
        if (dto.getFields() != null) {
            fieldMapper.deleteByEntityId(id);
            for (EntityFieldDTO fieldDTO : dto.getFields()) {
                EntityField field = convertToEntity(fieldDTO);
                field.setId(null); // 更新时重新创建字段，避免ID冲突
                field.setEntityId(id);
                fieldMapper.insert(field);
            }
        }
        
        return convertToDTO(existing);
    }
    
    /**
     * 删除实体定义
     */
    @Transactional
    public void delete(String id) {
        fieldMapper.deleteByEntityId(id);
        entityMapper.deleteById(id);
    }
    
    /**
     * 发布实体
     */
    @Transactional
    public EntityDefinitionDTO publish(String id) {
        EntityDefinition entity = entityMapper.selectById(id);
        if (entity == null) {
            throw new RuntimeException("实体不存在: " + id);
        }
        entity.setStatus(EntityDefinition.Status.PUBLISHED);
        entityMapper.updateById(entity);
        return convertToDTO(entity);
    }
    
    /**
     * 绑定流程
     */
    @Transactional
    public EntityDefinitionDTO bindProcess(String entityId, String processId) {
        EntityDefinition entity = entityMapper.selectById(entityId);
        if (entity == null) {
            throw new RuntimeException("实体不存在: " + entityId);
        }
        entity.setProcessDefinitionId(processId);
        entity.setEnableProcess(true);
        entityMapper.updateById(entity);
        return convertToDTO(entity);
    }
    
    // 转换方法
    private EntityDefinitionDTO convertToDTO(EntityDefinition entity) {
        return convertToDTO(entity, null);
    }
    
    private EntityDefinitionDTO convertToDTO(EntityDefinition entity, String processName) {
        EntityDefinitionDTO dto = new EntityDefinitionDTO();
        dto.setId(entity.getId());
        dto.setEntityCode(entity.getEntityCode());
        dto.setEntityName(entity.getEntityName());
        dto.setDescription(entity.getDescription());
        dto.setProcessDefinitionId(entity.getProcessDefinitionId());
        dto.setProcessName(processName);
        dto.setEnableProcess(entity.getEnableProcess());
        dto.setStatus(entity.getStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        dto.setCreatedBy(entity.getCreatedBy());
        
        if (entity.getFields() != null) {
            dto.setFields(entity.getFields().stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList()));
        }
        
        return dto;
    }
    
    private EntityFieldDTO convertToDTO(EntityField field) {
        EntityFieldDTO dto = new EntityFieldDTO();
        dto.setId(field.getId());
        dto.setFieldCode(field.getFieldCode());
        dto.setFieldName(field.getFieldName());
        dto.setFieldType(field.getFieldType());
        dto.setDbType(field.getDbType());
        dto.setFieldLength(field.getFieldLength());
        dto.setIsRequired(field.getIsRequired());
        dto.setIsUnique(field.getIsUnique());
        dto.setDefaultValue(field.getDefaultValue());
        dto.setOptionsJson(field.getOptionsJson());
        dto.setValidateRules(field.getValidateRules());
        dto.setSortOrder(field.getSortOrder());
        dto.setShowInList(field.getShowInList());
        dto.setShowInForm(field.getShowInForm());
        dto.setIsQuery(field.getIsQuery());
        return dto;
    }
    
    private EntityDefinition convertToEntity(EntityDefinitionDTO dto) {
        EntityDefinition entity = new EntityDefinition();
        entity.setId(dto.getId());
        entity.setEntityCode(dto.getEntityCode());
        entity.setEntityName(dto.getEntityName());
        entity.setDescription(dto.getDescription());
        entity.setProcessDefinitionId(dto.getProcessDefinitionId());
        entity.setEnableProcess(dto.getEnableProcess());
        entity.setStatus(dto.getStatus());
        entity.setCreatedBy(dto.getCreatedBy());
        return entity;
    }
    
    private EntityField convertToEntity(EntityFieldDTO dto) {
        EntityField field = new EntityField();
        field.setId(dto.getId());
        field.setFieldCode(dto.getFieldCode());
        field.setFieldName(dto.getFieldName());
        field.setFieldType(dto.getFieldType());
        field.setDbType(dto.getDbType());
        field.setFieldLength(dto.getFieldLength());
        field.setIsRequired(dto.getIsRequired());
        field.setIsUnique(dto.getIsUnique());
        field.setDefaultValue(dto.getDefaultValue());
        field.setOptionsJson(dto.getOptionsJson());
        field.setValidateRules(dto.getValidateRules());
        field.setSortOrder(dto.getSortOrder());
        field.setShowInList(dto.getShowInList());
        field.setShowInForm(dto.getShowInForm());
        field.setIsQuery(dto.getIsQuery());
        return field;
    }
}
