package com.workflow.mapper.migration;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.migration.ConfigMigrationAssetDependency;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ConfigMigrationAssetDependencyMapper
        extends BaseMapper<ConfigMigrationAssetDependency> {

    @Select("SELECT * FROM config_migration_asset_dependency "
            + "WHERE asset_id = #{assetId} ORDER BY dependency_type, dependency_key")
    List<ConfigMigrationAssetDependency> findByAssetId(@Param("assetId") String assetId);

    @Delete("DELETE FROM config_migration_asset_dependency WHERE asset_id = #{assetId}")
    void deleteByAssetId(@Param("assetId") String assetId);
}
