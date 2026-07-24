package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityFieldOption;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select("SELECT * FROM entity_field_option WHERE field_id = #{fieldId} ORDER BY sort_order")
    List<EntityFieldOption> findByFieldId(@Param("fieldId") String fieldId);

    /**
     * 根据字段 ID 物理删除所有选项（用于批量保存前清理旧数据）。
     *
     * @param fieldId 字段 ID
     */
    @Delete("DELETE FROM entity_field_option WHERE field_id = #{fieldId}")
    void deleteByFieldId(@Param("fieldId") String fieldId);
}
