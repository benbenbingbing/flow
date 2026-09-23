package com.workflow.entity.definition.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityFieldOption;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 实体字段选项 Mapper
 *
 * 提供按字段 ID 查询选项列表及按字段 ID 删除选项的能力。
 */
@Mapper
public interface EntityFieldOptionMapper extends BaseMapper<EntityFieldOption> {

    /**
     * 根据字段 ID 查询选项列表，按 sort_order 排序。
     *
     * @param fieldId 字段 ID
     * @return 选项列表
     */
    default List<EntityFieldOption> findByFieldId(String fieldId) {
        return selectList(Wrappers.<EntityFieldOption>lambdaQuery()
                .eq(EntityFieldOption::getFieldId, fieldId)
                .orderByAsc(EntityFieldOption::getSortOrder));
    }

    /**
     * 根据字段 ID 物理删除所有选项（用于批量保存前清理旧数据）。
     *
     * @param fieldId 字段 ID
     */
    default void deleteByFieldId(String fieldId) {
        delete(Wrappers.<EntityFieldOption>lambdaQuery().eq(EntityFieldOption::getFieldId, fieldId));
    }
}
