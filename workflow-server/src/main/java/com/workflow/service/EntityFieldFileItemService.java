package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.EntityFieldFileItem;
import com.workflow.mapper.EntityFieldFileItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 实体字段附件项配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityFieldFileItemService {

    private final EntityFieldFileItemMapper fileItemMapper;

    /**
     * 根据字段ID查询附件项列表
     */
    public List<EntityFieldFileItem> findByFieldId(String fieldId) {
        return fileItemMapper.findByFieldId(fieldId);
    }

    /**
     * 批量保存附件项（先删除旧数据，再插入新数据）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveFileItems(String fieldId, List<EntityFieldFileItem> items) {
        // 删除旧的附件项
        fileItemMapper.deleteByFieldId(fieldId);

        // 保存新的附件项
        if (items != null && !items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                EntityFieldFileItem item = items.get(i);
                item.setFieldId(fieldId);
                item.setSortOrder(i);
                item.setCreatedAt(LocalDateTime.now());
                item.setUpdatedAt(LocalDateTime.now());
                fileItemMapper.insert(item);
            }
        }
    }

    /**
     * 删除字段的所有附件项
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByFieldId(String fieldId) {
        fileItemMapper.deleteByFieldId(fieldId);
    }
}
