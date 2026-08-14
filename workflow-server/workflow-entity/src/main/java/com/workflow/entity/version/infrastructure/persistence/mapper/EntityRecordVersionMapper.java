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
            SELECT id, entity_code, record_id, version_no, version_title,
                   scenario_code, scenario_name, operation_type, source_type,
                   business_intent_code, business_intent_name,
                   operator_id, operator_name, process_instance_id,
                   source_entity_code, source_record_id, snapshot_hash,
                   data_hash, schema_version, scope_hash, create_time
            FROM entity_record_version
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
            ORDER BY version_no DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<EntityRecordVersion> findSummaryPage(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId,
            @Param("offset") long offset,
            @Param("limit") long limit);

    @Select("""
            SELECT COUNT(*) FROM entity_record_version
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
            """)
    long countByRecord(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId);

    @Select("""
            SELECT COALESCE(data_hash, snapshot_hash)
            FROM entity_record_version
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
              AND version_no = #{versionNo}
            LIMIT 1
            """)
    String findDataHash(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId,
            @Param("versionNo") Integer versionNo);

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
            LIMIT 1
            """)
    EntityRecordVersion findIdempotent(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId,
            @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM entity_record_version
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
              AND idempotency_key = #{idempotencyKey}
            LIMIT 1
            FOR UPDATE
            """)
    EntityRecordVersion findIdempotentForUpdate(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId,
            @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM entity_record_version
            WHERE id = #{id}
            LIMIT 1
            """)
    EntityRecordVersion findById(@Param("id") String id);
}
