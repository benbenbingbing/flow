package com.workflow.openapi.infrastructure.persistence.mapper;

import com.workflow.openapi.infrastructure.persistence.record.IntegrationGrantValueRecord;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

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
}
