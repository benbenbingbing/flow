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

    /**
     * 读取当前配置。N 版混部期间旧 Pod 发布只会切 active release，因此有效
     * release 优先于 config_document；legacy draft 永远不参与当前运行语义。
     */
    @Select("""
            SELECT c.id,
                   c.entity_id,
                   c.entity_code,
                   c.enabled,
                   CASE
                       WHEN r.id IS NOT NULL
                        AND JSON_VALID(r.config_document) = 1
                       THEN JSON_REMOVE(
                           JSON_SET(
                               CAST(r.config_document AS JSON),
                               '$.schemaVersion',
                               COALESCE(r.contract_version, 1)),
                           '$.status',
                           '$.migrationState',
                           '$.activeReleaseId',
                           '$.activeReleaseVersion')
                       ELSE c.config_document
                   END AS config_document,
                   c.revision,
                   c.create_by,
                   c.create_time,
                   c.update_by,
                   c.update_time,
                   c.deleted
            FROM entity_version_config c
            LEFT JOIN entity_version_config_release r
                   ON r.id = c.active_release_id
                  AND r.config_id = c.id
            WHERE c.entity_code = #{entityCode}
              AND c.deleted = 0
            LIMIT 1
            """)
    EntityVersionConfig findByEntityCode(
            @Param("entityCode") String entityCode);

    /** 批量查询与单条查询使用完全相同的 active release 优先级。 */
    @Select("""
            SELECT c.id,
                   c.entity_id,
                   c.entity_code,
                   c.enabled,
                   CASE
                       WHEN r.id IS NOT NULL
                        AND JSON_VALID(r.config_document) = 1
                       THEN JSON_REMOVE(
                           JSON_SET(
                               CAST(r.config_document AS JSON),
                               '$.schemaVersion',
                               COALESCE(r.contract_version, 1)),
                           '$.status',
                           '$.migrationState',
                           '$.activeReleaseId',
                           '$.activeReleaseVersion')
                       ELSE c.config_document
                   END AS config_document,
                   c.revision,
                   c.create_by,
                   c.create_time,
                   c.update_by,
                   c.update_time,
                   c.deleted
            FROM entity_version_config c
            LEFT JOIN entity_version_config_release r
                   ON r.id = c.active_release_id
                  AND r.config_id = c.id
            WHERE c.deleted = 0
              AND (c.config_document IS NOT NULL
                   OR (r.id IS NOT NULL
                       AND JSON_VALID(r.config_document) = 1))
            ORDER BY c.entity_code ASC
            """)
    List<EntityVersionConfig> findAllCurrent();

    @Update("""
            UPDATE entity_version_config
            SET enabled = #{enabled},
                config_document = #{configDocument},
                revision = revision + 1,
                update_by = #{updateBy},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
              AND revision = #{expectedRevision}
              AND deleted = 0
            """)
    int updateCurrentIfRevision(
            @Param("id") String id,
            @Param("expectedRevision") Integer expectedRevision,
            @Param("enabled") Boolean enabled,
            @Param("configDocument") String configDocument,
            @Param("updateBy") String updateBy);
}
