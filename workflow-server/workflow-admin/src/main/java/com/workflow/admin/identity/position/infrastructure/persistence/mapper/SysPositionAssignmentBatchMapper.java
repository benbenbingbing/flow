package com.workflow.admin.identity.position.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPositionAssignmentBatch;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 批量任命幂等结果 Mapper。
 */
@Mapper
public interface SysPositionAssignmentBatchMapper
        extends BaseMapper<SysPositionAssignmentBatch> {

    @Select("""
            SELECT * FROM sys_position_assignment_batch
            WHERE created_by = #{actorId} AND idempotency_key = #{key}
            LIMIT 1
            """)
    SysPositionAssignmentBatch selectByKey(
            @Param("actorId") String actorId,
            @Param("key") String key);
}
