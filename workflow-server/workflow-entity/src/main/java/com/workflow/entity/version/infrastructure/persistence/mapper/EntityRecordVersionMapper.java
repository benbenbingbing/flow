package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体数据版本 Mapper。
 */
@Mapper
public interface EntityRecordVersionMapper
        extends BaseMapper<EntityRecordVersion> {

    @Select("""
            SELECT * FROM entity_record_version
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
            ORDER BY version_no DESC
            """)
    List<EntityRecordVersion> findByRecord(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId);

    @Select("""
            SELECT * FROM entity_record_version
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
              AND version_no = #{versionNo}
            LIMIT 1
            """)
    EntityRecordVersion findVersion(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId,
            @Param("versionNo") Integer versionNo);

    @Select("""
            SELECT COALESCE(MAX(version_no), 0)
            FROM entity_record_version
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
            """)
    Integer findMaxVersionNo(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId);

    @Select("""
            SELECT * FROM entity_record_version
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
              AND idempotency_key = #{idempotencyKey}
              AND scenario_code = #{scenarioCode}
            LIMIT 1
            """)
    EntityRecordVersion findIdempotent(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("scenarioCode") String scenarioCode);
}
