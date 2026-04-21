package com.workflow.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.dto.ApiResponse;
import com.workflow.entity.ReportCategory;
import com.workflow.entity.ReportDataset;
import com.workflow.entity.ReportDefinition;
import com.workflow.service.ReportEngineService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 报表引擎控制器
 */
@RestController
@RequestMapping("/api/report-engine")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ReportEngineController {
    
    private final ReportEngineService reportEngineService;
    
    /**
     * 分页查询报表列表
     */
    @GetMapping("/list")
    public ApiResponse<Page<ReportDefinition>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String reportType,
            @RequestParam(required = false) String categoryId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize) {
        return ApiResponse.success(reportEngineService.getReportList(keyword, reportType, categoryId, pageNum, pageSize));
    }
    
    /**
     * 根据ID查询报表详情
     */
    @GetMapping("/{id}")
    public ApiResponse<ReportDefinition> getById(@PathVariable String id) {
        ReportDefinition report = reportEngineService.getReportDetail(id);
        if (report == null) {
            return ApiResponse.error(404, "报表不存在");
        }
        return ApiResponse.success(report);
    }
    
    /**
     * 查询报表完整配置
     */
    @GetMapping("/{id}/config")
    public ApiResponse<ReportConfigVO> getReportConfig(@PathVariable String id) {
        ReportDefinition report = reportEngineService.getReportDetail(id);
        if (report == null) {
            return ApiResponse.error(404, "报表不存在");
        }
        
        ReportConfigVO config = new ReportConfigVO();
        config.setReport(report);
        config.setDatasets(reportEngineService.getReportDatasets(id));
        
        return ApiResponse.success(config);
    }
    
    /**
     * 保存报表
     */
    @PostMapping("/save")
    public ApiResponse<ReportDefinition> save(@RequestBody ReportConfigDTO dto) {
        ReportDefinition report = reportEngineService.saveReport(dto.getReport(), dto.getDatasets());
        return ApiResponse.success(report);
    }
    
    /**
     * 删除报表
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable String id) {
        reportEngineService.deleteReport(id);
        return ApiResponse.success();
    }
    
    /**
     * 获取报表分类
     */
    @GetMapping("/categories")
    public ApiResponse<List<ReportCategory>> getCategories() {
        return ApiResponse.success(reportEngineService.getCategories());
    }
    
    /**
     * 获取报表数据
     */
    @PostMapping("/{id}/data")
    public ApiResponse<Map<String, List<Map<String, Object>>>> getReportData(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> params) {
        return ApiResponse.success(reportEngineService.getReportData(id, params));
    }
    
    /**
     * 执行SQL查询（用于测试数据集）
     */
    @PostMapping("/execute-sql")
    public ApiResponse<List<Map<String, Object>>> executeSql(
            @RequestBody SqlQueryDTO dto) {
        return ApiResponse.success(reportEngineService.executeSqlQuery(dto.getSql(), dto.getParams()));
    }
    
    // ==================== DTO和VO ====================
    
    @lombok.Data
    public static class ReportConfigDTO {
        private ReportDefinition report;
        private List<ReportDataset> datasets;
    }
    
    @lombok.Data
    public static class ReportConfigVO {
        private ReportDefinition report;
        private List<ReportDataset> datasets;
    }
    
    @lombok.Data
    public static class SqlQueryDTO {
        private String sql;
        private Map<String, Object> params;
    }
}
