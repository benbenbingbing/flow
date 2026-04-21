package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ReportCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 报表分类 Mapper
 */
@Mapper
public interface ReportCategoryMapper extends BaseMapper<ReportCategory> {
    
    /**
     * 查询所有分类
     */
    @Select("SELECT * FROM report_category ORDER BY sort_order")
    List<ReportCategory> findAll();
    
    /**
     * 根据父ID查询
     */
    @Select("SELECT * FROM report_category WHERE parent_id = #{parentId} ORDER BY sort_order")
    List<ReportCategory> findByParentId(@Param("parentId") String parentId);
}
