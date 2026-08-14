package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityVersionConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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

    @Update("""
            UPDATE entity_version_config
            SET enabled = #{enabled},
                contract_version = #{contractVersion},
                draft_document = #{draftDocument},
                migration_state = #{migrationState},
                revision = revision + 1,
                status = 'DRAFT',
                update_by = #{updateBy},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND revision = #{expectedRevision}
              AND deleted = 0
            """)
    int updateDraftIfRevision(
            @Param("id") String id,
            @Param("expectedRevision") Integer expectedRevision,
            @Param("enabled") Boolean enabled,
            @Param("contractVersion") Integer contractVersion,
            @Param("draftDocument") String draftDocument,
            @Param("migrationState") String migrationState,
            @Param("updateBy") String updateBy);

    @Update("""
            UPDATE entity_version_config
            SET active_release_id = #{activeReleaseId},
                contract_version = #{contractVersion},
                migration_state = #{migrationState},
                revision = revision + 1,
                status = 'PUBLISHED',
                update_by = #{updateBy},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND revision = #{expectedRevision}
              AND deleted = 0
            """)
    int activateReleaseIfRevision(
            @Param("id") String id,
            @Param("expectedRevision") Integer expectedRevision,
            @Param("activeReleaseId") String activeReleaseId,
            @Param("contractVersion") Integer contractVersion,
            @Param("migrationState") String migrationState,
            @Param("updateBy") String updateBy);
}
