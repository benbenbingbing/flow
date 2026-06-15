package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityFieldFileItem;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体字段附件项配置 Mapper
 */
@Mapper
public interface EntityFieldFileItemMapper extends BaseMapper<EntityFieldFileItem> {

    /**
     * 根据字段ID查询附件项列表
     */
    @Select("SELECT * FROM entity_field_file_item WHERE field_id = #{fieldId} ORDER BY sort_order ASC, create_time ASC")
    List<EntityFieldFileItem> findByFieldId(@Param("fieldId") String fieldId);

    /**
     * 根据字段ID删除附件项
     */
    @Delete("DELETE FROM entity_field_file_item WHERE field_id = #{fieldId}")
    void deleteByFieldId(@Param("fieldId") String fieldId);
}
