package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.entity.ReportDefinition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 报表定义 Mapper
 */
@Mapper
public interface ReportDefinitionMapper extends BaseMapper<ReportDefinition> {
    
    /**
     * 分页查询报表列表
     */
    Page<ReportDefinition> selectReportList(Page<ReportDefinition> page,
            @Param("keyword") String keyword,
            @Param("reportType") String reportType,
            @Param("categoryId") String categoryId);
    
    /**
     * 根据分类查询
     */
    @Select("SELECT * FROM report_definition WHERE category_id = #{categoryId} AND status = 'ACTIVE' ORDER BY created_at DESC")
    List<ReportDefinition> findByCategoryId(@Param("categoryId") String categoryId);
}
