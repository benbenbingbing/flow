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
            SELECT COUNT(*) FROM entity_version_config_release
            WHERE config_id = #{configId}
            """)
    long countByConfigId(@Param("configId") String configId);

    @Select("""
            SELECT * FROM entity_version_config_release
            WHERE config_id = #{configId}
            ORDER BY version DESC
            LIMIT #{pageSize} OFFSET #{offset}
            """)
    List<EntityVersionConfigRelease> findPageByConfigId(
            @Param("configId") String configId,
            @Param("offset") long offset,
            @Param("pageSize") long pageSize);

    @Select("""
            SELECT COALESCE(MAX(version), 0)
            FROM entity_version_config_release
            WHERE config_id = #{configId}
            """)
    Integer findMaxVersion(
            @Param("configId") String configId);

    /**
     * 查询旧版当前生效实体变更配置中可能引用接口服务的发布文档。
     * 应用层还会限定 MANAGED_INTERFACE 并做服务 ID 精确匹配。
     */
    @Select("""
            SELECT r.* FROM entity_version_config_release r
            INNER JOIN entity_version_config c
                    ON c.active_release_id = r.id
                   AND c.deleted = 0
            WHERE r.config_document IS NOT NULL
              AND r.config_document LIKE CONCAT('%', #{serviceId}, '%')
            ORDER BY r.config_id, r.version DESC
            """)
    List<EntityVersionConfigRelease> findActiveDataSourceReferenceCandidates(
            @Param("serviceId") String serviceId);

    /**
     * 判断旧版配置是否已被同实体且发布行真实存在的原生策略遮蔽。
     *
     * <p>仅有 native config 或悬空 active_release_id 不足以遮蔽旧版；
     * 运行时在找不到原生发布行时仍会回退 legacy release。</p>
     */
    @Select("""
            SELECT COUNT(*)
            FROM entity_version_config legacy_config
            INNER JOIN entity_mutation_policy_config native_config
                    ON native_config.entity_code = legacy_config.entity_code
                   AND native_config.deleted = 0
            INNER JOIN entity_mutation_policy_release native_release
                    ON native_release.id = native_config.active_release_id
            WHERE legacy_config.id = #{legacyConfigId}
              AND legacy_config.deleted = 0
            """)
    long countActiveNativePolicyForLegacyConfig(
            @Param("legacyConfigId") String legacyConfigId);
}
