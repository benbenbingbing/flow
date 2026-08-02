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
              business_id, business_version, process_instance_id, process_definition_key,
              create_time, update_time
            ) VALUES (
              #{id}, #{applicationId}, #{externalSystem}, #{businessType},
              #{businessId}, #{businessVersion}, #{processInstanceId}, #{processDefinitionKey},
              #{now}, #{now}
            )
            """)
    int insertWithVersion(
            @Param("id") String id,
            @Param("applicationId") String applicationId,
            @Param("externalSystem") String externalSystem,
            @Param("businessType") String businessType,
            @Param("businessId") String businessId,
            @Param("businessVersion") String businessVersion,
            @Param("processInstanceId") String processInstanceId,
            @Param("processDefinitionKey") String processDefinitionKey,
            @Param("now") LocalDateTime now);

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

    @Insert("""
            INSERT INTO integration_process_binding (
              id, application_id, scenario_id, scenario_key,
              scenario_revision, scenario_config_hash, external_system,
              business_type, business_id, business_version, process_instance_id,
              process_definition_key, input_snapshot_json, input_hash,
              outcome_mapping_snapshot_json, event_types_snapshot_json,
              external_initiator_id, identity_namespace, identity_mapping_snapshot_json,
              create_time, update_time
            ) VALUES (
              #{id}, #{applicationId}, #{scenarioId}, #{scenarioKey},
              #{scenarioRevision}, #{scenarioConfigHash}, #{externalSystem},
              #{businessType}, #{businessId}, #{businessVersion}, #{processInstanceId},
              #{processDefinitionKey}, #{inputSnapshotJson}, #{inputHash},
              #{outcomeMappingSnapshotJson}, #{eventTypesSnapshotJson},
              #{externalInitiatorId}, #{identityNamespace}, #{identityMappingSnapshotJson},
              #{now}, #{now}
            )
            """)
    int insertScenario(
            @Param("id") String id,
            @Param("applicationId") String applicationId,
            @Param("scenarioId") String scenarioId,
            @Param("scenarioKey") String scenarioKey,
            @Param("scenarioRevision") Long scenarioRevision,
            @Param("scenarioConfigHash") String scenarioConfigHash,
            @Param("externalSystem") String externalSystem,
            @Param("businessType") String businessType,
            @Param("businessId") String businessId,
            @Param("businessVersion") String businessVersion,
            @Param("processInstanceId") String processInstanceId,
            @Param("processDefinitionKey") String processDefinitionKey,
            @Param("inputSnapshotJson") String inputSnapshotJson,
            @Param("inputHash") String inputHash,
            @Param("outcomeMappingSnapshotJson") String outcomeMappingSnapshotJson,
            @Param("eventTypesSnapshotJson") String eventTypesSnapshotJson,
            @Param("externalInitiatorId") String externalInitiatorId,
            @Param("identityNamespace") String identityNamespace,
            @Param("identityMappingSnapshotJson") String identityMappingSnapshotJson,
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

    @Select("""
            SELECT *
              FROM integration_process_binding
             WHERE process_instance_id = #{processInstanceId}
             LIMIT 1
            """)
    IntegrationProcessBindingRecord findOwnerByProcessInstance(
            @Param("processInstanceId") String processInstanceId);
}
