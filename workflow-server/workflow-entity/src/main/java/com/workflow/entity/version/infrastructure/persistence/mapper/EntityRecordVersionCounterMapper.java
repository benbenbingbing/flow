package com.workflow.entity.version.infrastructure.persistence.mapper;

import com.workflow.entity.version.infrastructure.persistence.record.EntityRecordVersionCounter;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 版本号计数器 Mapper；调用方必须处于事务内。 */
@Mapper
public interface EntityRecordVersionCounterMapper {

    /** 在调用方已持有计数器行锁后追平历史最大值，绝不覆盖更大的版本号。 */
    @Update("""
            UPDATE entity_record_version_counter
            SET last_version_no = CASE WHEN last_version_no < #{initialVersion}
                                       THEN #{initialVersion} ELSE last_version_no END,
                update_time = CURRENT_TIMESTAMP
            WHERE entity_code = #{entityCode} AND record_id = #{recordId}
            """)
    int raiseMinimum(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId,
            @Param("initialVersion") Integer initialVersion);

    @Select("""
            SELECT entity_code, record_id, last_version_no, update_time
            FROM entity_record_version_counter
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
            FOR UPDATE
            """)
    EntityRecordVersionCounter lock(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId);

    @Update("""
            UPDATE entity_record_version_counter
            SET last_version_no = #{versionNo},
                update_time = CURRENT_TIMESTAMP
            WHERE entity_code = #{entityCode}
              AND record_id = #{recordId}
            """)
    int update(
            @Param("entityCode") String entityCode,
            @Param("recordId") String recordId,
            @Param("versionNo") Integer versionNo);
}
