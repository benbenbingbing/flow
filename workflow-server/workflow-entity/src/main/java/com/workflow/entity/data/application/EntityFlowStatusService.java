package com.workflow.entity.data.application;

import com.workflow.entity.data.infrastructure.persistence.record.EntityFlowStatusMapping;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityFlowStatusMappingMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 实体流程状态服务
 */
@Service
public class EntityFlowStatusService {
    
    private final EntityFlowStatusMappingMapper statusMappingMapper;
    
    private static final Logger log = LoggerFactory.getLogger(EntityFlowStatusService.class);
    
    public EntityFlowStatusService(EntityFlowStatusMappingMapper statusMappingMapper) {
        this.statusMappingMapper = statusMappingMapper;
    }
    
    /**
     * 保存流程状态映射配置
     * 
     * 注意：如果新配置为空，则保留原有配置（因为 BPMN XML 中的 camunda 属性在发布时会被清理，
     * 再次发布时可能无法从 XML 中提取到配置）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveStatusMappings(String processConfigId, String processKey, String entityCode, 
                                   List<EntityFlowStatusMapping> mappings) {
        // 如果新配置为空，保留原有配置（避免因 camunda 属性被清理导致数据丢失）
        if (mappings == null || mappings.isEmpty()) {
            log.info("新配置为空，保留原有状态映射: processConfigId={}", processConfigId);
            return;
        }

        replaceStatusMappings(processConfigId, processKey, entityCode, mappings);
    }

    /**
     * 以 BPMN 设计稿为准全量替换状态映射。
     *
     * <p>与 {@link #saveStatusMappings(String, String, String, List)} 不同，空列表代表设计稿中
     * 已经删除全部映射，因此必须清除数据库中的旧配置。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceStatusMappings(String processConfigId, String processKey, String entityCode,
                                      List<EntityFlowStatusMapping> mappings) {
        statusMappingMapper.deleteByProcessConfigId(processConfigId);

        if (mappings == null || mappings.isEmpty()) {
            log.info("清空流程状态映射: processConfigId={}", processConfigId);
            return;
        }

        for (EntityFlowStatusMapping mapping : mappings) {
            mapping.setProcessConfigId(processConfigId);
            mapping.setProcessKey(processKey);
            mapping.setEntityCode(entityCode);
            if (mapping.getEntityStatus() == null || mapping.getEntityStatus().isBlank()) {
                mapping.setEntityStatus(mapping.getEntityStatusCode());
            }
            mapping.setDeleted(0);
            statusMappingMapper.insert(mapping);
        }

        log.info("替换流程状态映射: processConfigId={}, count={}", processConfigId, mappings.size());
    }
    
    /**
     * 查询流程的状态映射配置
     */
    public List<EntityFlowStatusMapping> getStatusMappings(String processConfigId) {
        return statusMappingMapper.findByProcessConfigId(processConfigId);
    }
    
    /**
     * 根据源节点查询状态映射
     */
    public List<EntityFlowStatusMapping> getStatusMappingsBySourceNode(String processConfigId, String sourceNodeId) {
        return statusMappingMapper.findByProcessAndSourceNode(processConfigId, sourceNodeId);
    }
    
    /**
     * 获取特定流转的状态映射
     */
    public EntityFlowStatusMapping getStatusMapping(String processConfigId, String sourceNodeId, String targetNodeId) {
        return statusMappingMapper.findByProcessAndNodes(processConfigId, sourceNodeId, targetNodeId);
    }
    
    /**
     * 根据流程标识查询
     */
    public List<EntityFlowStatusMapping> getStatusMappingsByProcessKey(String processKey) {
        return statusMappingMapper.findByProcessKey(processKey);
    }
    
    /**
     * 删除流程的状态映射配置
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByProcessConfigId(String processConfigId) {
        statusMappingMapper.deleteByProcessConfigId(processConfigId);
        log.info("删除流程状态映射: processConfigId={}", processConfigId);
    }
}
