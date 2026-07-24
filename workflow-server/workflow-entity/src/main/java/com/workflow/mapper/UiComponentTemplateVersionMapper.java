package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.UiComponentTemplateVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * UI 组件模板版本 Mapper
 * 
 * 提供按模板 ID 查询历史版本列表的能力。
 */
@Mapper
public interface UiComponentTemplateVersionMapper extends BaseMapper<UiComponentTemplateVersion> {

    /**
     * 根据模板 ID 查询版本列表，按版本号降序排列。
     *
     * @param templateId 模板 ID
     * @return 版本列表
     */
    @Select("SELECT * FROM ui_component_template_version "
            + "WHERE template_id = #{templateId} ORDER BY version DESC")
    List<UiComponentTemplateVersion> findByTemplateId(
            @Param("templateId") String templateId);
}
