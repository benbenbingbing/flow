package com.workflow.process.status.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProcessStatusSyncMapper {

    /** 确认本事务取得的 APPLYING 记录；now 为数据库 UTC 时间，返回 1 才能提交业务结果。 */
    @Update("""
            UPDATE process_status_sync_event
            SET state = 'APPLIED',
                applied_at = #{now},
                update_time = #{now}
            WHERE id = #{id}
              AND state = 'APPLYING'
            """)
    int markApplied(@Param("id") String id, @Param("now") LocalDateTime now);
}
