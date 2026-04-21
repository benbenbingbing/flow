package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ViewButtonConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 视图按钮配置 Mapper
 */
@Mapper
public interface ViewButtonConfigMapper extends BaseMapper<ViewButtonConfig> {
    
    /**
     * 根据视图ID查询按钮配置
     */
    @Select("SELECT * FROM view_button_config WHERE view_id = #{viewId} ORDER BY button_type, sort_order")
    List<ViewButtonConfig> findByViewId(@Param("viewId") String viewId);
    
    /**
     * 根据视图ID和按钮类型查询
     */
    @Select("SELECT * FROM view_button_config WHERE view_id = #{viewId} AND button_type = #{buttonType} ORDER BY sort_order")
    List<ViewButtonConfig> findByViewIdAndType(@Param("viewId") String viewId, @Param("buttonType") String buttonType);
    
    /**
     * 根据视图ID删除
     */
    @Select("DELETE FROM view_button_config WHERE view_id = #{viewId}")
    void deleteByViewId(@Param("viewId") String viewId);
}
