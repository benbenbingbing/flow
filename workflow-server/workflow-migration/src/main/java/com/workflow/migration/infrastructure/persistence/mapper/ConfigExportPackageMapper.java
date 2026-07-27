package com.workflow.migration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigExportPackage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置导出包 Mapper。
 *
 * <p>提供 config_export_package 表的基础 CRUD 能力。</p>
 */
@Mapper
public interface ConfigExportPackageMapper extends BaseMapper<ConfigExportPackage> {
}
