package com.workflow.entity.mutationpolicy.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.record.EntityMutationPolicyRelease;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityMutationPolicyReleaseMapper
        extends BaseMapper<EntityMutationPolicyRelease> {

    @Select("""
            SELECT COALESCE(MAX(version), 0)
            FROM entity_mutation_policy_release
            WHERE config_id = #{configId}
            """)
    Integer findMaxVersion(@Param("configId") String configId);

    @Select("""
            SELECT * FROM entity_mutation_policy_release
            WHERE config_id = #{configId}
            ORDER BY version DESC
            """)
    List<EntityMutationPolicyRelease> findByConfigId(
            @Param("configId") String configId);

    /**
     * 查询当前生效实体变更策略中可能引用接口服务的发布文档。
     * 应用层仍需解析 MANAGED_INTERFACE 步骤并精确匹配服务 ID。
     */
    @Select("""
            SELECT r.* FROM entity_mutation_policy_release r
            INNER JOIN entity_mutation_policy_config c
                    ON c.active_release_id = r.id
                   AND c.deleted = 0
            WHERE r.config_document IS NOT NULL
              AND r.config_document LIKE CONCAT('%', #{serviceId}, '%')
            ORDER BY r.config_id, r.version DESC
            """)
    List<EntityMutationPolicyRelease> findActiveDataSourceReferenceCandidates(
            @Param("serviceId") String serviceId);
}
