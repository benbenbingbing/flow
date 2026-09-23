package com.workflow.migration.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.migration.infrastructure.persistence.record.ConfigMigrationAssetDependency;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 配置迁移资产依赖 Mapper。
 *
 * <p>提供 config_migration_asset_dependency 表的基础 CRUD 及按资产ID查询/删除的定制能力。</p>
 */
@Mapper
public interface ConfigMigrationAssetDependencyMapper
        extends BaseMapper<ConfigMigrationAssetDependency> {

    /**
     * 按资产ID查询其全部依赖记录，按依赖类型与编码排序。
     *
     * @param assetId 资产ID
     * @return 依赖记录列表
     */
    default List<ConfigMigrationAssetDependency> findByAssetId(String assetId) {
        return selectList(Wrappers.<ConfigMigrationAssetDependency>lambdaQuery()
                .eq(ConfigMigrationAssetDependency::getAssetId, assetId)
                .orderByAsc(ConfigMigrationAssetDependency::getDependencyType)
                .orderByAsc(ConfigMigrationAssetDependency::getDependencyKey));
    }

    /**
     * 按资产ID删除其全部依赖记录(用于重新保存依赖前的清空)。
     *
     * @param assetId 资产ID
     */
    default void deleteByAssetId(String assetId) {
        // 依赖记录没有逻辑删除字段，清空后由本次分析结果重新建立完整依赖集合。
        delete(Wrappers.<ConfigMigrationAssetDependency>lambdaQuery()
                .eq(ConfigMigrationAssetDependency::getAssetId, assetId));
    }
}
