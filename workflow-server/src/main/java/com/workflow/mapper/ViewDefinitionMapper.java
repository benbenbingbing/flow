package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.entity.ViewDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 视图定义 Mapper
 */
@Mapper
public interface ViewDefinitionMapper extends BaseMapper<ViewDefinition> {
    
    /**
     * 根据实体编码查询视图列表
     */
    @Select("SELECT * FROM view_definition WHERE entity_code = #{entityCode} AND status = 'ACTIVE' ORDER BY is_default DESC, created_at DESC")
    List<ViewDefinition> findByEntityCode(@Param("entityCode") String entityCode);
    
    /**
     * 查询默认视图
     */
    @Select("SELECT * FROM view_definition WHERE entity_code = #{entityCode} AND is_default = 1 AND status = 'ACTIVE' LIMIT 1")
    ViewDefinition findDefaultByEntityCode(@Param("entityCode") String entityCode);
    
    /**
     * 分页查询
     */
    Page<ViewDefinition> selectViewList(Page<ViewDefinition> page, 
            @Param("keyword") String keyword, 
            @Param("viewType") String viewType,
            @Param("entityCode") String entityCode);
}
