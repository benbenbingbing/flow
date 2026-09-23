package com.workflow.entity.data.application;

import com.workflow.core.logging.LogValue;
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
    
    /**
     * 初始化实体流程状态服务，保存构造参数供后续方法使用。
     *
     * @param statusMappingMapper 状态映射映射器依赖，保存到当前对象供后续业务方法调用
     */
    public EntityFlowStatusService(EntityFlowStatusMappingMapper statusMappingMapper) {
        this.statusMappingMapper = statusMappingMapper;
    }
    
    /**
     * 保存流程状态映射配置
     * 
     * 注意：如果新配置为空，则保留原有配置（因为 BPMN XML 中的 camunda 属性在发布时会被清理，
     * 再次发布时可能无法从 XML 中提取到配置）
     *
     * @param processConfigId 流程配置ID，后续用于保存状态映射集合时定位或关联目标
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param mappings 映射集合，作为 {@code replaceStatusMappings} 的输入影响后续处理
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveStatusMappings(String processConfigId, String processKey, String entityCode, 
                                   List<EntityFlowStatusMapping> mappings) {
        // 如果新配置为空，保留原有配置（避免因 camunda 属性被清理导致数据丢失）
        if (mappings == null || mappings.isEmpty()) {
            log.info("新配置为空，保留原有状态映射: processConfigId={}", LogValue.safe(processConfigId));
            return;
        }

        replaceStatusMappings(processConfigId, processKey, entityCode, mappings);
    }

    /**
     * 以 BPMN 设计稿为准全量替换状态映射。
     *
     * <p>与 {@link #saveStatusMappings(String, String, String, List)} 不同，空列表代表设计稿中
     * 已经删除全部映射，因此必须清除数据库中的旧配置。</p>
     *
     * @param processConfigId 流程配置ID，后续用于处理替换状态映射集合时定位或关联目标
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param mappings 映射集合，供本方法处理替换状态映射集合时使用
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceStatusMappings(String processConfigId, String processKey, String entityCode,
                                      List<EntityFlowStatusMapping> mappings) {
        statusMappingMapper.deleteByProcessConfigId(processConfigId);

        if (mappings == null || mappings.isEmpty()) {
            log.info("清空流程状态映射: processConfigId={}", LogValue.safe(processConfigId));
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

        log.info("替换流程状态映射: processConfigId={}, count={}", LogValue.safe(processConfigId), mappings.size());
    }
    
    /**
     * 查询流程的状态映射配置
     *
     * @param processConfigId 流程配置ID，后续用于读取状态映射集合时定位或关联目标
     * @return 实体流程状态映射集合，供调用方遍历或展示
     */
    public List<EntityFlowStatusMapping> getStatusMappings(String processConfigId) {
        return statusMappingMapper.findByProcessConfigId(processConfigId);
    }
    
    /**
     * 根据源节点查询状态映射
     *
     * @param processConfigId 流程配置ID，后续用于读取状态映射集合来源节点时定位或关联目标
     * @param sourceNodeId 来源节点ID，后续用于读取状态映射集合来源节点时定位或关联目标
     * @return 实体流程状态映射集合，供调用方遍历或展示
     */
    public List<EntityFlowStatusMapping> getStatusMappingsBySourceNode(String processConfigId, String sourceNodeId) {
        return statusMappingMapper.findByProcessAndSourceNode(processConfigId, sourceNodeId);
    }
    
    /**
     * 获取特定流转的状态映射
     *
     * @param processConfigId 流程配置ID，后续用于读取状态映射时定位或关联目标
     * @param sourceNodeId 来源节点ID，后续用于读取状态映射时定位或关联目标
     * @param targetNodeId 目标节点ID，后续用于读取状态映射时定位或关联目标
     * @return 符合条件的实体流程状态映射结果，供调用方继续处理
     */
    public EntityFlowStatusMapping getStatusMapping(String processConfigId, String sourceNodeId, String targetNodeId) {
        return statusMappingMapper.findByProcessAndNodes(processConfigId, sourceNodeId, targetNodeId);
    }
    
    /**
     * 根据流程标识查询
     *
     * @param processKey 流程键，后续用于授权校验、关联或幂等去重
     * @return 实体流程状态映射集合，供调用方遍历或展示
     */
    public List<EntityFlowStatusMapping> getStatusMappingsByProcessKey(String processKey) {
        return statusMappingMapper.findByProcessKey(processKey);
    }
    
    /**
     * 删除流程的状态映射配置
     *
     * @param processConfigId 流程配置ID，后续用于删除流程配置ID时定位或关联目标
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByProcessConfigId(String processConfigId) {
        statusMappingMapper.deleteByProcessConfigId(processConfigId);
        log.info("删除流程状态映射: processConfigId={}", LogValue.safe(processConfigId));
    }
}
