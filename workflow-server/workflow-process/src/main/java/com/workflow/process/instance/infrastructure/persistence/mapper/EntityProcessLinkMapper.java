package com.workflow.process.instance.infrastructure.persistence.mapper;

import com.workflow.process.instance.infrastructure.persistence.record.EntityProcessLink;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface EntityProcessLinkMapper {

    @Insert("""
            INSERT IGNORE INTO entity_process_link (
              id, entity_code, entity_record_id, generation,
              process_definition_key, state, request_id, entity_status,
              version, create_time, update_time
            ) VALUES (
              #{id}, #{entityCode}, #{entityRecordId}, #{generation},
              #{processDefinitionKey}, 'PENDING', #{requestId}, #{entityStatus},
              0, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6)
            )
            """)
    int insertPending(EntityProcessLink link);

    @Select("""
            SELECT * FROM entity_process_link
            WHERE entity_code = #{entityCode}
              AND entity_record_id = #{entityRecordId}
              AND generation = #{generation}
            FOR UPDATE
            """)
    EntityProcessLink selectForUpdate(
            @Param("entityCode") String entityCode,
            @Param("entityRecordId") String entityRecordId,
            @Param("generation") int generation);

    @Update("""
            UPDATE entity_process_link
            SET process_instance_id = #{processInstanceId},
                state = 'ACTIVE',
                version = version + 1,
                update_time = UTC_TIMESTAMP(6)
            WHERE id = #{id}
              AND request_id = #{requestId}
              AND state = 'PENDING'
            """)
    int activate(
            @Param("id") String id,
            @Param("requestId") String requestId,
            @Param("processInstanceId") String processInstanceId);

    @Update("""
            UPDATE entity_process_link
            SET state = 'ENDED',
                entity_status = #{entityStatus},
                ended_at = UTC_TIMESTAMP(6),
                version = version + 1,
                update_time = UTC_TIMESTAMP(6)
            WHERE process_instance_id = #{processInstanceId}
              AND state = 'ACTIVE'
            """)
    int closeActive(
            @Param("processInstanceId") String processInstanceId,
            @Param("entityStatus") String entityStatus);

    @Update("""
            UPDATE entity_process_link
            SET entity_status = #{entityStatus},
                version = version + 1,
                update_time = UTC_TIMESTAMP(6)
            WHERE process_instance_id = #{processInstanceId}
              AND state = 'ACTIVE'
              AND (entity_status IS NULL OR entity_status <> #{entityStatus})
            """)
    int updateActiveStatus(
            @Param("processInstanceId") String processInstanceId,
            @Param("entityStatus") String entityStatus);

    @Select("""
            SELECT * FROM entity_process_link
            WHERE process_instance_id = #{processInstanceId}
            LIMIT 1
            """)
    EntityProcessLink findByProcessInstanceId(
            @Param("processInstanceId") String processInstanceId);

    @Select("""
            SELECT link.* FROM entity_process_link link
            WHERE link.state = 'ACTIVE'
              AND link.process_instance_id IS NOT NULL
              AND EXISTS (
                SELECT 1
                FROM ACT_HI_PROCINST historic
                WHERE historic.PROC_INST_ID_ = link.process_instance_id
                  AND historic.END_TIME_ IS NOT NULL
              )
            ORDER BY link.update_time
            LIMIT #{limit}
            """)
    List<EntityProcessLink> findEndedActiveForReconciliation(
            @Param("limit") int limit);
}
