package com.workflow.mapper.migration;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.migration.ConfigMigrationAssetDependency;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
    @Select("SELECT * FROM config_migration_asset_dependency "
            + "WHERE asset_id = #{assetId} ORDER BY dependency_type, dependency_key")
    List<ConfigMigrationAssetDependency> findByAssetId(@Param("assetId") String assetId);

    /**
     * 按资产ID删除其全部依赖记录(用于重新保存依赖前的清空)。
     *
     * @param assetId 资产ID
     */
    @Delete("DELETE FROM config_migration_asset_dependency WHERE asset_id = #{assetId}")
    void deleteByAssetId(@Param("assetId") String assetId);
}
