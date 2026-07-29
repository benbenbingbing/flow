package com.workflow.openapi.infrastructure.persistence.mapper;

import com.workflow.openapi.infrastructure.persistence.record.IntegrationGrantValueRecord;
import com.workflow.openapi.infrastructure.persistence.record.IntegrationProcessGrantRecord;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IntegrationProcessGrantMapper {

    @Select("""
            SELECT process_key
              FROM integration_process_grant
             WHERE application_id = #{applicationId}
             ORDER BY process_key
            """)
    Set<String> findByApplicationId(
            @Param("applicationId") String applicationId);

    @Select("""
            SELECT application_id, process_key, input_schema_json,
                   allowed_message_keys
              FROM integration_process_grant
             WHERE application_id = #{applicationId}
             ORDER BY process_key
            """)
    List<IntegrationProcessGrantRecord> findContractsByApplicationId(
            @Param("applicationId") String applicationId);

    @Select("""
            SELECT application_id, process_key, input_schema_json,
                   allowed_message_keys
              FROM integration_process_grant
             WHERE application_id = #{applicationId}
               AND process_key = #{processKey}
             LIMIT 1
            """)
    IntegrationProcessGrantRecord findContract(
            @Param("applicationId") String applicationId,
            @Param("processKey") String processKey);

    @Select("""
            <script>
            SELECT application_id, process_key AS grant_value
              FROM integration_process_grant
             WHERE application_id IN
               <foreach collection="applicationIds" item="id"
                        open="(" separator="," close=")">
                 #{id}
               </foreach>
             ORDER BY application_id, process_key
            </script>
            """)
    List<IntegrationGrantValueRecord> findByApplicationIds(
            @Param("applicationIds") List<String> applicationIds);

    @Insert("""
            INSERT INTO integration_process_grant (
              application_id, process_key, granted_by, create_time
            ) VALUES (
              #{applicationId}, #{processKey}, #{operatorId}, #{now}
            )
            """)
    int insertGrant(
            @Param("applicationId") String applicationId,
            @Param("processKey") String processKey,
            @Param("operatorId") String operatorId,
            @Param("now") LocalDateTime now);

    @Delete("""
            DELETE FROM integration_process_grant
             WHERE application_id = #{applicationId}
            """)
    int deleteByApplicationId(
            @Param("applicationId") String applicationId);

    @Update("""
            UPDATE integration_process_grant
               SET input_schema_json = #{inputSchemaJson},
                   allowed_message_keys = #{allowedMessageKeys},
                   granted_by = #{operatorId},
                   update_time = #{now}
             WHERE application_id = #{applicationId}
               AND process_key = #{processKey}
            """)
    int updateContract(
            @Param("applicationId") String applicationId,
            @Param("processKey") String processKey,
            @Param("inputSchemaJson") String inputSchemaJson,
            @Param("allowedMessageKeys") String allowedMessageKeys,
            @Param("operatorId") String operatorId,
            @Param("now") LocalDateTime now);
}
