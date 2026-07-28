package com.workflow.process.status.infrastructure.persistence.mapper;

import com.workflow.process.status.infrastructure.persistence.record.ProcessStatusSyncRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProcessStatusSyncMapper {

    @Insert("""
            INSERT IGNORE INTO process_status_sync_event (
              id, process_instance_id, event_type, event_sequence,
              entity_code, entity_record_id, target_status,
              status_category, state, create_time, update_time
            ) VALUES (
              #{id}, #{processInstanceId}, #{eventType}, #{eventSequence},
              #{entityCode}, #{entityRecordId}, #{targetStatus},
              #{statusCategory}, 'APPLYING', UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
            )
            """)
    int insertApplying(ProcessStatusSyncRecord record);

    @Update("""
            UPDATE process_status_sync_event
            SET state = 'APPLIED',
                applied_at = UTC_TIMESTAMP(6),
                update_time = UTC_TIMESTAMP(6)
            WHERE id = #{id}
              AND state = 'APPLYING'
            """)
    int markApplied(@Param("id") String id);
}
