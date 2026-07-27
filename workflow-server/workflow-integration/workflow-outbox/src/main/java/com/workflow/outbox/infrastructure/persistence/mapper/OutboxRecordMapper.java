package com.workflow.outbox.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.outbox.infrastructure.persistence.record.OutboxRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 通用 Outbox 的就绪查询、乐观认领和维护操作。
 */
@Mapper
public interface OutboxRecordMapper extends BaseMapper<OutboxRecord> {

    @Select("SELECT * FROM workflow_outbox_event "
            + "WHERE status IN ('PENDING','FAILED') "
            + "AND (next_retry_time IS NULL OR next_retry_time <= NOW()) "
            + "ORDER BY create_time LIMIT #{limit}")
    List<OutboxRecord> findReady(@Param("limit") int limit);

    @Update("UPDATE workflow_outbox_event "
            + "SET status = 'PROCESSING', update_time = NOW() "
            + "WHERE id = #{id} AND status IN ('PENDING','FAILED') "
            + "AND (next_retry_time IS NULL OR next_retry_time <= NOW())")
    int claim(@Param("id") String id);

    @Update("UPDATE workflow_outbox_event "
            + "SET status = 'FAILED', next_retry_time = NOW(), "
            + "error_message = 'PROCESSING_TIMEOUT', update_time = NOW() "
            + "WHERE status = 'PROCESSING' AND update_time < #{staleBefore}")
    int recoverStaleProcessing(
            @Param("staleBefore") LocalDateTime staleBefore);

    @Delete("DELETE FROM workflow_outbox_event "
            + "WHERE status = 'PROCESSED' AND processed_time < #{cutoff}")
    int deleteProcessedBefore(@Param("cutoff") LocalDateTime cutoff);
}
