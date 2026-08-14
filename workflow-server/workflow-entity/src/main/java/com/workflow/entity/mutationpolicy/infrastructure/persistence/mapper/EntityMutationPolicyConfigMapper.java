package com.workflow.entity.mutationpolicy.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.mutationpolicy.infrastructure.persistence.record.EntityMutationPolicyConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityMutationPolicyConfigMapper
        extends BaseMapper<EntityMutationPolicyConfig> {

    @Select("""
            SELECT * FROM entity_mutation_policy_config
            WHERE entity_code = #{entityCode} AND deleted = 0
            LIMIT 1
            """)
    EntityMutationPolicyConfig findByEntityCode(
            @Param("entityCode") String entityCode);

    @Select("""
            SELECT * FROM entity_mutation_policy_config
            WHERE entity_code = #{entityCode} AND deleted = 0
            LIMIT 1 FOR UPDATE
            """)
    EntityMutationPolicyConfig findByEntityCodeForUpdate(
            @Param("entityCode") String entityCode);

    @Select("""
            SELECT * FROM entity_mutation_policy_config
            WHERE active_release_id IS NOT NULL AND deleted = 0
            ORDER BY entity_code
            """)
    List<EntityMutationPolicyConfig> findAllPublished();
}
