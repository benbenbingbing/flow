package com.workflow.embed.infrastructure.persistence.mapper;

import com.workflow.embed.infrastructure.persistence.record.EmbedOperationReceiptRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** Embed Operation Receipt 的有界 SQL。 */
@Mapper
public interface EmbedOperationReceiptMapper {

    @Insert("""
            INSERT INTO embed_operation_receipt (
              id, idempotency_record_id, application_id, operation,
              actor_scope_digest, view_key, target_type, target_id,
              outcome_code, record_version, result_summary_json
            ) VALUES (
              #{id}, #{idempotencyRecordId}, #{applicationId}, #{operation},
              #{actorScopeDigest}, #{viewKey}, #{targetType}, #{targetId},
              #{outcomeCode}, #{recordVersion}, #{resultSummaryJson}
            )
            """)
    int insert(EmbedOperationReceiptRow row);

    @Select("""
            SELECT id, idempotency_record_id, application_id, operation,
                   actor_scope_digest, view_key, target_type, target_id,
                   outcome_code, record_version, result_summary_json
              FROM embed_operation_receipt
             WHERE id = #{receiptId}
             LIMIT 1
            """)
    EmbedOperationReceiptRow findById(
            @Param("receiptId") String receiptId);
}
