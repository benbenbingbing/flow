package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.FormFieldConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 表单字段配置 Mapper
 */
@Mapper
public interface FormFieldConfigMapper extends BaseMapper<FormFieldConfig> {

    /**
     * 根据表单配置ID查询字段列表
     */
    @Select("SELECT * FROM process_form_field_config WHERE form_config_id = #{formConfigId} ORDER BY sort_order ASC")
    List<FormFieldConfig> findByFormConfigId(@Param("formConfigId") String formConfigId);
}
