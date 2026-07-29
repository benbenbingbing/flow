package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityMutationReceipt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 实体变更幂等回执 Mapper。
 */
@Mapper
public interface EntityMutationReceiptMapper
        extends BaseMapper<EntityMutationReceipt> {

    @Select("""
            SELECT * FROM entity_mutation_receipt
            WHERE idempotency_key = #{idempotencyKey}
            LIMIT 1
            """)
    EntityMutationReceipt findByIdempotencyKey(
            @Param("idempotencyKey") String idempotencyKey);

    @Select("""
            SELECT * FROM entity_mutation_receipt
            WHERE idempotency_key = #{idempotencyKey}
            LIMIT 1
            FOR UPDATE
            """)
    EntityMutationReceipt findByIdempotencyKeyForUpdate(
            @Param("idempotencyKey") String idempotencyKey);

    @Update("""
            UPDATE entity_mutation_receipt
            SET record_id = #{recordId},
                status = 'SUCCESS',
                result_document = #{resultDocument},
                version_no = #{versionNo},
                version_scenario_code = #{versionScenarioCode},
                changed = #{changed},
                update_time = CURRENT_TIMESTAMP
            WHERE idempotency_key = #{idempotencyKey}
              AND status = 'PENDING'
            """)
    int complete(
            @Param("idempotencyKey") String idempotencyKey,
            @Param("recordId") String recordId,
            @Param("resultDocument") String resultDocument,
            @Param("versionNo") Integer versionNo,
            @Param("versionScenarioCode") String versionScenarioCode,
            @Param("changed") Boolean changed);
}
