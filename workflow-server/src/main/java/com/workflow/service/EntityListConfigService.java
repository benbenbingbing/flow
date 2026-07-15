package com.workflow.service;

import com.workflow.dto.EntityListConfigDTO;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListFieldMapper;
import com.workflow.service.config.EntityListConfigurationValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 实体列表配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityListConfigService {

    private final EntityListConfigMapper configMapper;
    private final EntityListFieldMapper fieldMapper;
    private final com.workflow.service.permission.EntityListActionConfigService actionConfigService;
    private final com.workflow.service.permission.EntityPermissionCatalogService permissionCatalogService;
    private final com.workflow.service.permission.EntityActionCapabilityService actionCapabilityService;
    private final EntityListConfigurationValidator configurationValidator;

    /**
     * 查询实体的所有列表配置
     */
    public List<EntityListConfigDTO> findByEntityId(String entityId) {
        List<EntityListConfig> configs = configMapper.findByEntityId(entityId);
        return configs.stream().map(this::convertToDTO).collect(Collectors.toList());
    }

    /**
     * 根据ID查询配置（含字段）
     */
    public EntityListConfigDTO findById(String id) {
        EntityListConfig config = configMapper.selectById(id);
        if (config == null) {
            return null;
        }
        return convertToDTOWithFields(config);
    }

    /**
     * 保存/更新列表配置（含字段）
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListConfigDTO saveConfig(EntityListConfigDTO dto) {
        configurationValidator.validate(dto);
        EntityListConfig config = new EntityListConfig();
        BeanUtils.copyProperties(dto, config);
        actionConfigService.normalizeForSave(config);

        boolean isNew = !StringUtils.hasText(config.getId());

        if (isNew) {
            configMapper.insert(config);
        } else {
            configMapper.updateById(config);
            // 删除旧字段
            fieldMapper.deleteByListConfigId(config.getId());
        }

        // 保存字段
        if (dto.getFields() != null) {
            for (int i = 0; i < dto.getFields().size(); i++) {
                EntityListField field = dto.getFields().get(i);
                field.setId(null);
                field.setListConfigId(config.getId());
                field.setSortOrder(i);
                field.setDeleted(0);
                fieldMapper.insert(field);
            }
        }

        permissionCatalogService.synchronizeCustomPermissions(config);

        return findById(config.getId());
    }

    /**
     * 删除列表配置（逻辑删除，级联删除字段）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConfig(String id) {
        // 逻辑删除配置
        configMapper.deleteById(id);
        // 物理删除字段
        fieldMapper.deleteByListConfigId(id);
    }

    private EntityListConfigDTO convertToDTO(EntityListConfig config) {
        EntityListConfigDTO dto = new EntityListConfigDTO();
        BeanUtils.copyProperties(config, dto);
        if (config != null && StringUtils.hasText(config.getEntityCode())) {
            dto.setToolbarCapabilities(actionCapabilityService.evaluateToolbarActions(
                    config.getEntityCode(),
                    config));
        }
        return dto;
    }

    private EntityListConfigDTO convertToDTOWithFields(EntityListConfig config) {
        EntityListConfigDTO dto = convertToDTO(config);
        List<EntityListField> fields = fieldMapper.findByListConfigId(config.getId());
        dto.setFields(fields);
        return dto;
    }
}
