package com.workflow.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.*;
import com.workflow.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 报表引擎服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReportEngineService extends ServiceImpl<ReportDefinitionMapper, ReportDefinition> {
    
    private final ReportDefinitionMapper reportDefinitionMapper;
    private final ReportCategoryMapper categoryMapper;
    private final ReportDatasetMapper datasetMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * 分页查询报表列表
     */
    public Page<ReportDefinition> getReportList(String keyword, String reportType, 
                                                 String categoryId, int pageNum, int pageSize) {
        Page<ReportDefinition> page = new Page<>(pageNum, pageSize);
        return reportDefinitionMapper.selectReportList(page, keyword, reportType, categoryId);
    }
    
    /**
     * 根据ID查询报表详情
     */
    public ReportDefinition getReportDetail(String id) {
        ReportDefinition report = reportDefinitionMapper.selectById(id);
        if (report != null && StringUtils.hasText(report.getCategoryId())) {
            ReportCategory category = categoryMapper.selectById(report.getCategoryId());
            if (category != null) {
                report.setCategoryName(category.getCategoryName());
            }
        }
        return report;
    }
    
    /**
     * 保存报表
     */
    @Transactional(rollbackFor = Exception.class)
    public ReportDefinition saveReport(ReportDefinition report, List<ReportDataset> datasets) {
        // 生成编码
        if (!StringUtils.hasText(report.getReportCode())) {
            report.setReportCode("RPT_" + System.currentTimeMillis());
        }
        
        // 保存报表定义
        if (report.getId() == null) {
            report.setVersion(1);
            report.setStatus("ACTIVE");
            reportDefinitionMapper.insert(report);
        } else {
            report.setVersion(report.getVersion() + 1);
            reportDefinitionMapper.updateById(report);
            // 删除旧数据集
            datasetMapper.deleteByReportId(report.getId());
        }
        
        // 保存数据集
        if (datasets != null && !datasets.isEmpty()) {
            for (ReportDataset dataset : datasets) {
                dataset.setReportId(report.getId());
                datasetMapper.insert(dataset);
            }
        }
        
        return report;
    }
    
    /**
     * 删除报表
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteReport(String id) {
        datasetMapper.deleteByReportId(id);
        reportDefinitionMapper.deleteById(id);
    }
    
    /**
     * 获取报表数据集
     */
    public List<ReportDataset> getReportDatasets(String reportId) {
        return datasetMapper.findByReportId(reportId);
    }
    
    /**
     * 获取所有报表分类
     */
    public List<ReportCategory> getCategories() {
        return categoryMapper.findAll();
    }
    
    /**
     * 执行报表查询（SQL类型数据集）
     */
    public List<Map<String, Object>> executeSqlQuery(String sql, Map<String, Object> params) {
        // 简单的参数替换（实际应用中需要更安全的方式）
        String finalSql = sql;
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                String placeholder = "${" + entry.getKey() + "}";
                String value = entry.getValue() != null ? entry.getValue().toString() : "";
                finalSql = finalSql.replace(placeholder, value);
            }
        }
        
        log.info("执行报表SQL: {}", finalSql);
        
        try {
            return jdbcTemplate.queryForList(finalSql);
        } catch (Exception e) {
            log.error("报表查询失败: {}", e.getMessage());
            throw new RuntimeException("报表查询失败: " + e.getMessage());
        }
    }
    
    /**
     * 获取报表数据（根据数据集配置）
     */
    public Map<String, List<Map<String, Object>>> getReportData(String reportId, 
                                                                  Map<String, Object> params) {
        Map<String, List<Map<String, Object>>> result = new HashMap<>();
        
        List<ReportDataset> datasets = datasetMapper.findByReportId(reportId);
        for (ReportDataset dataset : datasets) {
            try {
                Map<String, Object> config = objectMapper.readValue(dataset.getSourceConfig(), Map.class);
                String datasetType = dataset.getDatasetType();
                
                List<Map<String, Object>> data;
                if ("SQL".equals(datasetType)) {
                    String sql = (String) config.get("sql");
                    data = executeSqlQuery(sql, params);
                } else if ("ENTITY".equals(datasetType)) {
                    // 实体类型查询，这里简化处理
                    data = new ArrayList<>();
                } else {
                    data = new ArrayList<>();
                }
                
                result.put(dataset.getDatasetCode(), data);
            } catch (Exception e) {
                log.error("数据集 {} 查询失败: {}", dataset.getDatasetCode(), e.getMessage());
                result.put(dataset.getDatasetCode(), new ArrayList<>());
            }
        }
        
        return result;
    }
}
