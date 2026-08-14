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
}
