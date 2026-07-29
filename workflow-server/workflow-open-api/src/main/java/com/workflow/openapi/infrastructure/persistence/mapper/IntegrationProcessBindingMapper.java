package com.workflow.openapi.infrastructure.persistence.mapper;

import com.workflow.openapi.infrastructure.persistence.record.IntegrationProcessBindingRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface IntegrationProcessBindingMapper {

    @Insert("""
            INSERT INTO integration_process_binding (
              id, application_id, external_system, business_type,
              business_id, process_instance_id, process_definition_key,
              create_time, update_time
            ) VALUES (
              #{id}, #{applicationId}, #{externalSystem}, #{businessType},
              #{businessId}, #{processInstanceId}, #{processDefinitionKey},
              #{now}, #{now}
            )
            """)
    int insert(
            @Param("id") String id,
            @Param("applicationId") String applicationId,
            @Param("externalSystem") String externalSystem,
            @Param("businessType") String businessType,
            @Param("businessId") String businessId,
            @Param("processInstanceId") String processInstanceId,
            @Param("processDefinitionKey") String processDefinitionKey,
            @Param("now") LocalDateTime now);

    @Select("""
            SELECT *
              FROM integration_process_binding
             WHERE application_id = #{applicationId}
               AND process_instance_id = #{processInstanceId}
             LIMIT 1
            """)
    IntegrationProcessBindingRecord findByProcessInstance(
            @Param("applicationId") String applicationId,
            @Param("processInstanceId") String processInstanceId);

    @Select("""
            SELECT *
              FROM integration_process_binding
             WHERE application_id = #{applicationId}
               AND external_system = #{externalSystem}
               AND business_type = #{businessType}
               AND business_id = #{businessId}
             LIMIT 1
            """)
    IntegrationProcessBindingRecord findByBusinessReference(
            @Param("applicationId") String applicationId,
            @Param("externalSystem") String externalSystem,
            @Param("businessType") String businessType,
            @Param("businessId") String businessId);
}
