package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityChangeTargetInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 变更目标实例 Mapper。
 */
@Mapper
public interface EntityChangeTargetInstanceMapper
        extends BaseMapper<EntityChangeTargetInstance> {

    @Select("""
            SELECT * FROM entity_change_target_instance
            WHERE source_entity_code = #{sourceEntityCode}
              AND source_record_id = #{sourceRecordId}
              AND (#{processInstanceId} IS NULL
                   OR process_instance_id = #{processInstanceId})
            ORDER BY create_time ASC
            """)
    List<EntityChangeTargetInstance> findTargets(
            @Param("sourceEntityCode") String sourceEntityCode,
            @Param("sourceRecordId") String sourceRecordId,
            @Param("processInstanceId") String processInstanceId);

    @Select("""
            SELECT * FROM entity_change_target_instance
            WHERE source_entity_code = #{sourceEntityCode}
              AND source_record_id = #{sourceRecordId}
              AND process_instance_id = #{processInstanceId}
              AND binding_code = #{bindingCode}
              AND target_entity_code = #{targetEntityCode}
              AND target_record_id = #{targetRecordId}
            LIMIT 1
            """)
    EntityChangeTargetInstance findFrozenTarget(
            @Param("sourceEntityCode") String sourceEntityCode,
            @Param("sourceRecordId") String sourceRecordId,
            @Param("processInstanceId") String processInstanceId,
            @Param("bindingCode") String bindingCode,
            @Param("targetEntityCode") String targetEntityCode,
            @Param("targetRecordId") String targetRecordId);

    @Update("""
            UPDATE entity_change_target_instance
            SET status = #{status},
                update_time = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    void updateStatus(
            @Param("id") String id,
            @Param("status") String status);
}
