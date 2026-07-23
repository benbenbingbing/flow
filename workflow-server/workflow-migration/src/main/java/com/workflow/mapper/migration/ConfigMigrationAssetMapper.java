package com.workflow.mapper.migration;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.migration.ConfigMigrationAsset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置迁移资产 Mapper。
 *
 * <p>提供 config_migration_asset 表的基础 CRUD 能力。</p>
 */
@Mapper
public interface ConfigMigrationAssetMapper extends BaseMapper<ConfigMigrationAsset> {
}
