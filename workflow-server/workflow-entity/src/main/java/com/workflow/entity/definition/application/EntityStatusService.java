package com.workflow.entity.definition.application;

import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.annotation.SystemAudit;
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
    private final com.workflow.contracts.process.port.ProcessCatalogPort processCatalogPort;
    
    /**
     * 查询实体的状态列表
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 实体状态集合，供调用方遍历或展示
     */
    public List<EntityStatus> findByEntityCode(String entityCode) {
        return entityStatusMapper.findByEntityCode(entityCode);
    }

    /**
     * 返回实体自定义状态的显示名称；列表调用方可在单次请求内按实体缓存，避免逐行查询配置。
     * 未配置名称的状态不加入映射，由客户端按内置状态或原始编码回退。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 状态名称映射键值结果，供调用方继续处理
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
     *
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
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
        var entity = entityAccessPolicy.requireDynamicByCodeForUpdate(status.getEntityCode());
        if (status.getId() != null && !status.getId().isBlank()) {
            EntityStatus existing = entityStatusMapper.selectById(status.getId());
            if (existing == null || !java.util.Objects.equals(existing.getEntityCode(), status.getEntityCode())) {
                throw new IllegalArgumentException("状态不存在或不属于当前实体");
            }
        }
        List<EntityStatus> candidates = new java.util.ArrayList<>(findByEntityCode(status.getEntityCode()));
        candidates.removeIf(existing -> java.util.Objects.equals(existing.getId(), status.getId()));
        candidates.add(status);
        EntitySpecialStatusPolicy.validate(candidates, requiredCategories(entity));
        if (status.getId() == null || status.getId().isEmpty()) {
            entityStatusMapper.insert(status);
        } else {
            entityStatusMapper.updateById(status);
        }
    }
    
    /**
     * 批量保存实体状态
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param statuses {@code statuses}，供本方法保存状态列表时使用
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
        var entity = entityAccessPolicy.requireDynamicByCodeForUpdate(entityCode);
        // 与同实体的单项保存共用定义行锁，避免两个并发请求各自通过唯一性检查。
        EntitySpecialStatusPolicy.validate(statuses, requiredCategories(entity));
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
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
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
            var entity = entityAccessPolicy.requireDynamicByCodeForUpdate(status.getEntityCode());
            var candidates = new java.util.ArrayList<>(findByEntityCode(status.getEntityCode()));
            candidates.removeIf(value -> id.equals(value.getId()));
            EntitySpecialStatusPolicy.validate(candidates, requiredCategories(entity));
            status.setDeleted(1);
            entityStatusMapper.updateById(status);
        }
    }
    
    /**
     * 根据分类查询
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param category 类别，决定后续状态或结果的归类
     * @return 实体状态集合，供调用方遍历或展示
     */
    public List<EntityStatus> findByCategory(String entityCode, String category) {
        return entityStatusMapper.findByCategory(entityCode, category);
    }
    /** 特殊操作调用方使用此方法选择业务状态；不得依赖排序或名称猜测。 */
    public String requireSpecialTarget(String entityCode, String category) {
        return EntitySpecialStatusPolicy.requireTarget(category, findByCategory(entityCode, category));
    }

    /** 实体发布也校验存量状态，防止重复配置绕过新的保存入口。 */
    public void validateForPublish(com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition entity) {
        EntitySpecialStatusPolicy.validate(findByEntityCode(entity.getEntityCode()), requiredCategories(entity));
    }

    private java.util.Set<String> requiredCategories(
            com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition entity) {
        if (entity.getLifecycleMode() != com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition.LifecycleMode.WORKFLOW
                || entity.getProcessDefinitionId() == null || entity.getProcessDefinitionId().isBlank()) return java.util.Set.of();
        return processCatalogPort.requiredEndStatusCategories(entity.getProcessDefinitionId());
    }

}
