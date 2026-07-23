package com.workflow.mapper.migration;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.migration.ConfigAssetBaseline;
import org.apache.ibatis.annotations.Mapper;

/**
 * 配置资产迁移基线 Mapper。
 *
 * <p>提供 config_asset_baseline 表的基础 CRUD 能力。</p>
 */
@Mapper
public interface ConfigAssetBaselineMapper extends BaseMapper<ConfigAssetBaseline> {
}
