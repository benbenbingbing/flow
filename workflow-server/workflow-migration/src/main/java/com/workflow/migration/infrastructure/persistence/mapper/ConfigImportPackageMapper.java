package com.workflow.migration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigImportPackage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 配置导入批次 Mapper。
 *
 * <p>提供 config_import_package 表的基础 CRUD 能力。</p>
 */
@Mapper
public interface ConfigImportPackageMapper extends BaseMapper<ConfigImportPackage> {

    /**
     * 更新批次分析结果，并显式允许清空上一次阻断原因。
     */
    @Update("""
            UPDATE config_import_package
            SET status = #{status},
                validation_report_json = #{validationReportJson},
                error_message = #{errorMessage}
            WHERE id = #{id}
            """)
    int updateAnalysisResult(
            @Param("id") String id,
            @Param("status") String status,
            @Param("validationReportJson") String validationReportJson,
            @Param("errorMessage") String errorMessage);
}
