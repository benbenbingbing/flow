package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体版本配置 Mapper。
 */
@Mapper
public interface EntityVersionConfigMapper
        extends BaseMapper<EntityVersionConfig> {

    @Select("""
            SELECT * FROM entity_version_config
            WHERE entity_code = #{entityCode}
              AND deleted = 0
            LIMIT 1
            """)
    EntityVersionConfig findByEntityCode(
            @Param("entityCode") String entityCode);

    @Select("""
            SELECT * FROM entity_version_config
            WHERE active_release_id IS NOT NULL
              AND active_release_id <> ''
              AND deleted = 0
            ORDER BY entity_code ASC
            """)
    List<EntityVersionConfig> findAllPublished();
}
