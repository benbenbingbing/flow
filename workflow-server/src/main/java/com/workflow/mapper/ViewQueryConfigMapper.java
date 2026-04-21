package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ViewQueryConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 视图查询条件配置 Mapper
 */
@Mapper
public interface ViewQueryConfigMapper extends BaseMapper<ViewQueryConfig> {
    
    /**
     * 根据视图ID查询查询条件配置
     */
    @Select("SELECT * FROM view_query_config WHERE view_id = #{viewId} ORDER BY sort_order")
    List<ViewQueryConfig> findByViewId(@Param("viewId") String viewId);
    
    /**
     * 根据视图ID删除
     */
    @Select("DELETE FROM view_query_config WHERE view_id = #{viewId}")
    void deleteByViewId(@Param("viewId") String viewId);
}
