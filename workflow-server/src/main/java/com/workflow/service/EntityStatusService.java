package com.workflow.service;

import com.workflow.entity.EntityStatus;
import com.workflow.mapper.EntityStatusMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 实体状态服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityStatusService {
    
    private final EntityStatusMapper entityStatusMapper;
    
    /**
     * 查询实体的状态列表
     */
    public List<EntityStatus> findByEntityCode(String entityCode) {
        return entityStatusMapper.findByEntityCode(entityCode);
    }
    
    /**
     * 保存实体状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveStatus(EntityStatus status) {
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
    public void saveStatusList(String entityCode, List<EntityStatus> statuses) {
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
