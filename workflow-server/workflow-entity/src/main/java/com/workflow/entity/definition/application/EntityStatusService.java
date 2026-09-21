package com.workflow.entity.definition.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 实体状态服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityStatusService {
    
    private final EntityStatusMapper entityStatusMapper;
    private final EntityDefinitionAccessPolicy entityAccessPolicy;
    
    /**
     * 查询实体的状态列表
     */
    public List<EntityStatus> findByEntityCode(String entityCode) {
        return entityStatusMapper.findByEntityCode(entityCode);
    }

    /**
     * 返回实体自定义状态的显示名称；列表调用方可在单次请求内按实体缓存，避免逐行查询配置。
     * 未配置名称的状态不加入映射，由客户端按内置状态或原始编码回退。
     */
    public Map<String, String> getStatusNameMap(String entityCode) {
        Map<String, String> names = new LinkedHashMap<>();
        for (EntityStatus status : findByEntityCode(entityCode)) {
            if (status.getStatusCode() != null && status.getStatusName() != null
                    && !status.getStatusName().isBlank()) {
                names.put(status.getStatusCode(), status.getStatusName());
            }
        }
        return names;
    }
    
    /**
     * 保存实体状态
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.UPSERT,
            operation = "保存实体状态",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "ENTITY_STATUS",
            captureArguments = true)
    public void saveStatus(EntityStatus status) {
        entityAccessPolicy.requireDynamicByCode(status.getEntityCode());
        if (status.getId() == null || status.getId().isEmpty()) {
            entityStatusMapper.insert(status);
        } else {
            entityStatusMapper.updateById(status);
        }
    }
    
    /**
     * 批量保存实体状态
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.CONFIGURE,
            operation = "批量保存实体状态",
            risk = AuditRiskLevel.HIGH,
            targetType = "ENTITY_STATUS",
            targetIdArg = 0,
            captureArguments = true)
    public void saveStatusList(String entityCode, List<EntityStatus> statuses) {
        entityAccessPolicy.requireDynamicByCode(entityCode);
        // 先物理删除旧的状态（避免主键冲突；全局逻辑删除配置会使 BaseMapper.delete 变成软删，这里必须物理删除）
        entityStatusMapper.physicalDeleteByEntityCode(entityCode);


        // 插入新的状态
        if (statuses != null) {
            for (int i = 0; i < statuses.size(); i++) {
                EntityStatus status = statuses.get(i);
                // 清除ID，让数据库重新生成
                status.setId(null);
                status.setEntityCode(entityCode);
                status.setSortOrder(i);
                status.setDeleted(0);
                status.setCreatedAt(null);
                status.setUpdatedAt(null);
                entityStatusMapper.insert(status);
            }
        }
    }
    
    /**
     * 删除实体状态
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.ENTITY,
            action = AuditAction.DELETE,
            operation = "删除实体状态",
            risk = AuditRiskLevel.MEDIUM,
            targetType = "ENTITY_STATUS",
            targetIdArg = 0)
    public void deleteStatus(String id) {
        EntityStatus status = entityStatusMapper.selectById(id);
        if (status != null) {
            status.setDeleted(1);
            entityStatusMapper.updateById(status);
        }
    }
    
    /**
     * 根据分类查询
     */
    public List<EntityStatus> findByCategory(String entityCode, String category) {
        return entityStatusMapper.findByCategory(entityCode, category);
    }
}
