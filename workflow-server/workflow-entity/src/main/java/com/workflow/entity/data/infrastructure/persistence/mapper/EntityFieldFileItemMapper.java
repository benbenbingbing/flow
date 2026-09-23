package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.data.infrastructure.persistence.record.EntityFieldFileItem;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 实体字段附件项配置 Mapper
 */
@Mapper
public interface EntityFieldFileItemMapper extends BaseMapper<EntityFieldFileItem> {

    /**
     * 根据字段ID查询附件项列表
     */
    default List<EntityFieldFileItem> findByFieldId(String fieldId) {
        return selectList(Wrappers.<EntityFieldFileItem>lambdaQuery()
                .eq(EntityFieldFileItem::getFieldId, fieldId)
                .orderByAsc(EntityFieldFileItem::getSortOrder)
                .orderByAsc(EntityFieldFileItem::getCreatedAt));
    }

    /**
     * 根据字段ID删除附件项
     */
    default void deleteByFieldId(String fieldId) {
        delete(Wrappers.<EntityFieldFileItem>lambdaQuery().eq(EntityFieldFileItem::getFieldId, fieldId));
    }
}
