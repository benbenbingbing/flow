package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.UiComponentTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * UI 组件模板 Mapper
 * 
 * 提供按主键加锁查询组件模板的能力，用于并发更新场景。
 */
@Mapper
public interface UiComponentTemplateMapper extends BaseMapper<UiComponentTemplate> {

    /**
     * 根据主键 ID 加锁查询未删除的组件模板（FOR UPDATE）。
     *
     * @param id 主键 ID
     * @return 组件模板，无则返回 null
     */
    @Select("SELECT * FROM ui_component_template "
            + "WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    UiComponentTemplate selectByIdForUpdate(@Param("id") String id);
}
