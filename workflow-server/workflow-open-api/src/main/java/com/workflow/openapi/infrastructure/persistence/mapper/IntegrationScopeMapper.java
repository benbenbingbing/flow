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
public interface IntegrationScopeMapper {

    @Select("""
            SELECT scope
              FROM integration_application_scope
             WHERE application_id = #{applicationId}
             ORDER BY scope
            """)
    Set<String> findByApplicationId(
            @Param("applicationId") String applicationId);

    @Select("""
            <script>
            SELECT application_id, scope AS grant_value
              FROM integration_application_scope
             WHERE application_id IN
               <foreach collection="applicationIds" item="id"
                        open="(" separator="," close=")">
                 #{id}
               </foreach>
             ORDER BY application_id, scope
            </script>
            """)
    List<IntegrationGrantValueRecord> findByApplicationIds(
            @Param("applicationIds") List<String> applicationIds);

    @Insert("""
            INSERT INTO integration_application_scope (
              application_id, scope, granted_by, create_time
            ) VALUES (
              #{applicationId}, #{scope}, #{operatorId}, #{now}
            )
            """)
    int insertGrant(
            @Param("applicationId") String applicationId,
            @Param("scope") String scope,
            @Param("operatorId") String operatorId,
            @Param("now") LocalDateTime now);

    @Delete("""
            DELETE FROM integration_application_scope
             WHERE application_id = #{applicationId}
            """)
    int deleteByApplicationId(
            @Param("applicationId") String applicationId);
}
