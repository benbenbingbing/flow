package com.workflow.mapper.migration;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.migration.ConfigExportPackageItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置导出包条目 Mapper。
 *
 * <p>提供 config_export_package_item 表的基础 CRUD 能力。</p>
 */
@Mapper
public interface ConfigExportPackageItemMapper extends BaseMapper<ConfigExportPackageItem> {
}
