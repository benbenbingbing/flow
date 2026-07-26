package com.workflow.system.audit.infrastructure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.system.audit.domain.SystemAuditOutbox;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 系统审计 Outbox 持久化适配器。
 */
@Mapper
public interface SystemAuditOutboxMapper extends BaseMapper<SystemAuditOutbox> {

    @Select("SELECT * FROM system_audit_outbox "
            + "WHERE status IN ('PENDING','FAILED') "
            + "AND (next_retry_time IS NULL OR next_retry_time <= NOW()) "
            + "ORDER BY create_time LIMIT #{limit}")
    List<SystemAuditOutbox> findReady(@Param("limit") int limit);

    @Update("UPDATE system_audit_outbox SET status = 'PROCESSING', update_time = NOW() "
            + "WHERE id = #{id} AND status IN ('PENDING','FAILED')")
    int claim(@Param("id") String id);

    @Update("UPDATE system_audit_outbox "
            + "SET status = 'FAILED', next_retry_time = NOW(), "
            + "error_message = 'PROCESSING_TIMEOUT', update_time = NOW() "
            + "WHERE status = 'PROCESSING' AND update_time < #{staleBefore}")
    int recoverStaleProcessing(@Param("staleBefore") LocalDateTime staleBefore);
}
