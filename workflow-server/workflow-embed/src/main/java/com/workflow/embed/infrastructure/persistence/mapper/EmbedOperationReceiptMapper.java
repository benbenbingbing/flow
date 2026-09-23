package com.workflow.embed.infrastructure.persistence.mapper;

import java.util.List;
import com.workflow.core.database.OffsetPage;
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

    /** 原查询仅取首行；保留数据库中的过滤语义，返回数量由分页插件限制。 */
    default EmbedOperationReceiptRow findById(String receiptId) {
        return findByIdPage(new OffsetPage<>(0, 1), receiptId).stream().findFirst().orElse(null);
    }

    /** 保留原投影和连接，仅将外层首行限制交给 MyBatis-Plus。 */
    @Select("""
            <script>
            SELECT id, idempotency_record_id, application_id, operation,
                   actor_scope_digest, view_key, target_type, target_id,
                   outcome_code, record_version, result_summary_json
              FROM embed_operation_receipt
             WHERE id = #{receiptId}

            </script>
            """)
    List<EmbedOperationReceiptRow> findByIdPage(
            @Param("page") OffsetPage<EmbedOperationReceiptRow> page,
            @Param("receiptId") String receiptId);
}
