package com.workflow.entity.form.uniqueness.infrastructure.persistence.mapper;

import com.workflow.entity.form.uniqueness.infrastructure.persistence.record.EntityFormUniqueClaim;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 表单唯一值占位的持久化 Mapper。 */
@Mapper
public interface EntityFormUniqueClaimMapper {

    /**
     * 删除全部记录；后续读取或执行将使用更新后的状态。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 删除后的全部记录结果，供调用方继续处理
     */
    @Delete("DELETE FROM entity_form_unique_claim "
            + "WHERE entity_code = #{entityCode} AND record_id = #{recordId}")
    int deleteAllByRecord(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId);

    /**
     * 插入实体表单唯一认领；后续读取或执行将使用更新后的状态。
     *
     * @param claim 认领，供本方法插入实体表单唯一认领时使用
     * @return 插入后的实体表单唯一认领结果，供调用方继续处理
     */
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
