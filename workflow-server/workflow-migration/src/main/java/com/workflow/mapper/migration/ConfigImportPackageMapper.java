package com.workflow.mapper.migration;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.migration.ConfigImportPackage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置导入批次 Mapper。
 *
 * <p>提供 config_import_package 表的基础 CRUD 能力。</p>
 */
@Mapper
public interface ConfigImportPackageMapper extends BaseMapper<ConfigImportPackage> {
}
