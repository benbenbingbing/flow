package com.workflow.entity.form.uniqueness.infrastructure.persistence.mapper;

import com.workflow.entity.form.uniqueness.infrastructure.persistence.record.EntityFormUniqueClaim;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 表单唯一值占位的持久化 Mapper。 */
@Mapper
public interface EntityFormUniqueClaimMapper {

    @Delete("DELETE FROM entity_form_unique_claim "
            + "WHERE entity_code = #{entityCode} AND record_id = #{recordId}")
    int deleteAllByRecord(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId);

    @Insert("INSERT INTO entity_form_unique_claim "
            + "(constraint_key, value_hash, entity_code, form_id, rule_id, field_code, "
            + "normalized_value, record_id, release_id, release_version, "
            + "effective_release_id, effective_content_hash, hotfix_target_id) VALUES "
            + "(#{constraintKey}, #{valueHash}, #{entityCode}, #{formId}, #{ruleId}, "
            + "#{fieldCode}, #{normalizedValue}, #{recordId}, #{releaseId}, "
            + "#{releaseVersion}, #{effectiveReleaseId}, #{effectiveContentHash}, "
            + "#{hotfixTargetId})")
    int insert(EntityFormUniqueClaim claim);

}
