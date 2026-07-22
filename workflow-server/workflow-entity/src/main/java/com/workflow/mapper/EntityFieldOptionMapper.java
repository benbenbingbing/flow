package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityFieldOption;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityFieldOptionMapper extends BaseMapper<EntityFieldOption> {

    @Select("SELECT * FROM entity_field_option WHERE field_id = #{fieldId} ORDER BY sort_order")
    List<EntityFieldOption> findByFieldId(@Param("fieldId") String fieldId);

    @Delete("DELETE FROM entity_field_option WHERE field_id = #{fieldId}")
    void deleteByFieldId(@Param("fieldId") String fieldId);
}
