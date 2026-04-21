package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ReportDataset;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 报表数据集 Mapper
 */
@Mapper
public interface ReportDatasetMapper extends BaseMapper<ReportDataset> {
    
    /**
     * 根据报表ID查询数据集
     */
    @Select("SELECT * FROM report_dataset WHERE report_id = #{reportId}")
    List<ReportDataset> findByReportId(@Param("reportId") String reportId);
    
    /**
     * 根据报表ID删除
     */
    @Select("DELETE FROM report_dataset WHERE report_id = #{reportId}")
    void deleteByReportId(@Param("reportId") String reportId);
}
