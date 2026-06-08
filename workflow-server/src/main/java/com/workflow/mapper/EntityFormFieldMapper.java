package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityFormField;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 表单字段Mapper
 */
@Mapper
public interface EntityFormFieldMapper extends BaseMapper<EntityFormField> {
    
    /**
     * 查询表单的字段列表
     */
    @Select("SELECT * FROM entity_form_field WHERE form_id = #{formId} ORDER BY sort_order")
    List<EntityFormField> selectByFormId(@Param("formId") String formId);
    
    /**
     * 删除表单的所有字段
     */
    @Delete("DELETE FROM entity_form_field WHERE form_id = #{formId}")
    void deleteByFormId(@Param("formId") String formId);
}
