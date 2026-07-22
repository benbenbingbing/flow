package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.UiComponentTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UiComponentTemplateMapper extends BaseMapper<UiComponentTemplate> {

    @Select("SELECT * FROM ui_component_template "
            + "WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    UiComponentTemplate selectByIdForUpdate(@Param("id") String id);
}
