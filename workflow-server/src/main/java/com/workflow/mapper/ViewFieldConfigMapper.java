package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ViewFieldConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 视图字段配置 Mapper
 */
@Mapper
public interface ViewFieldConfigMapper extends BaseMapper<ViewFieldConfig> {
    
    /**
     * 根据视图ID查询字段配置
     */
    @Select("SELECT * FROM view_field_config WHERE view_id = #{viewId} ORDER BY sort_order")
    List<ViewFieldConfig> findByViewId(@Param("viewId") String viewId);
    
    /**
     * 批量插入
     */
    void batchInsert(@Param("list") List<ViewFieldConfig> list);
    
    /**
     * 根据视图ID删除
     */
    @Select("DELETE FROM view_field_config WHERE view_id = #{viewId}")
    void deleteByViewId(@Param("viewId") String viewId);
}
