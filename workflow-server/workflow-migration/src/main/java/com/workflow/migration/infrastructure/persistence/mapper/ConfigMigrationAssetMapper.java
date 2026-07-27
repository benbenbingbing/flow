package com.workflow.migration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAsset;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置迁移资产 Mapper。
 *
 * <p>提供 config_migration_asset 表的基础 CRUD 能力。</p>
 */
@Mapper
public interface ConfigMigrationAssetMapper extends BaseMapper<ConfigMigrationAsset> {
}
