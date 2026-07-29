package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfigRelease;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体版本配置发布 Mapper。
 */
@Mapper
public interface EntityVersionConfigReleaseMapper
        extends BaseMapper<EntityVersionConfigRelease> {

    @Select("""
            SELECT * FROM entity_version_config_release
            WHERE config_id = #{configId}
            ORDER BY version DESC
            """)
    List<EntityVersionConfigRelease> findByConfigId(
            @Param("configId") String configId);

    @Select("""
            SELECT COALESCE(MAX(version), 0)
            FROM entity_version_config_release
            WHERE config_id = #{configId}
            """)
    Integer findMaxVersion(
            @Param("configId") String configId);
}
