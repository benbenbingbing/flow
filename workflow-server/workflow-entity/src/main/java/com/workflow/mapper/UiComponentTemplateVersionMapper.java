package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.UiComponentTemplateVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface UiComponentTemplateVersionMapper extends BaseMapper<UiComponentTemplateVersion> {

    @Select("SELECT * FROM ui_component_template_version "
            + "WHERE template_id = #{templateId} ORDER BY version DESC")
    List<UiComponentTemplateVersion> findByTemplateId(
            @Param("templateId") String templateId);
}
