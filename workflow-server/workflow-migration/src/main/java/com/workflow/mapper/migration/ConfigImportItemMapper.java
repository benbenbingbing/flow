package com.workflow.mapper.migration;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.migration.ConfigImportItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置导入条目 Mapper。
 *
 * <p>提供 config_import_item 表的基础 CRUD 能力。</p>
 */
@Mapper
public interface ConfigImportItemMapper extends BaseMapper<ConfigImportItem> {
}
