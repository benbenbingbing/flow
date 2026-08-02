package com.workflow.openapi.infrastructure.persistence.mapper;

import com.workflow.openapi.infrastructure.persistence.record.IntegrationWorkflowScenarioRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IntegrationWorkflowScenarioMapper {

    @Select("""
            SELECT * FROM integration_workflow_scenario
             WHERE id = #{id}
             LIMIT 1
            """)
    IntegrationWorkflowScenarioRecord findById(@Param("id") String id);

    @Select("""
            SELECT * FROM integration_workflow_scenario
             WHERE application_id = #{applicationId}
             ORDER BY scenario_key
            """)
    List<IntegrationWorkflowScenarioRecord> findByApplicationId(
            @Param("applicationId") String applicationId);

    @Select("""
            SELECT * FROM integration_workflow_scenario
             WHERE application_id = #{applicationId}
               AND scenario_key = #{scenarioKey}
             LIMIT 1
            """)
    IntegrationWorkflowScenarioRecord findByApplicationAndKey(
            @Param("applicationId") String applicationId,
            @Param("scenarioKey") String scenarioKey);

    @Insert("""
            INSERT INTO integration_workflow_scenario (
              id, application_id, scenario_key, display_name, process_key,
              process_definition_version, status, input_schema_json,
              outcome_mapping_json, identity_mapping_json, event_types_json,
              revision, config_hash, created_by, updated_by, create_time,
              update_time
            ) VALUES (
              #{record.id}, #{record.applicationId}, #{record.scenarioKey},
              #{record.displayName}, #{record.processKey},
              #{record.processDefinitionVersion}, 'ACTIVE',
              #{record.inputSchemaJson}, #{record.outcomeMappingJson},
              #{record.identityMappingJson}, #{record.eventTypesJson}, 1,
              #{record.configHash},
              #{operatorId}, #{operatorId}, #{now}, #{now}
            )
            """)
    int insert(@Param("record") IntegrationWorkflowScenarioRecord record,
               @Param("operatorId") String operatorId,
               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE integration_workflow_scenario
               SET display_name = #{record.displayName},
                   process_key = #{record.processKey},
                   process_definition_version = #{record.processDefinitionVersion},
                   input_schema_json = #{record.inputSchemaJson},
                   outcome_mapping_json = #{record.outcomeMappingJson},
                   identity_mapping_json = #{record.identityMappingJson},
                   event_types_json = #{record.eventTypesJson},
                   config_hash = #{record.configHash},
                   revision = revision + 1,
                   updated_by = #{operatorId},
                   update_time = #{now}
             WHERE application_id = #{record.applicationId}
               AND scenario_key = #{record.scenarioKey}
               AND revision = #{expectedRevision}
               AND status = 'ACTIVE'
            """)
    int update(@Param("record") IntegrationWorkflowScenarioRecord record,
               @Param("operatorId") String operatorId,
               @Param("expectedRevision") long expectedRevision,
               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE integration_workflow_scenario
               SET status = 'DISABLED', revision = revision + 1,
                   updated_by = #{operatorId}, update_time = #{now}
             WHERE application_id = #{applicationId}
               AND scenario_key = #{scenarioKey}
               AND revision = #{expectedRevision}
               AND status = 'ACTIVE'
            """)
    int disable(@Param("applicationId") String applicationId,
                @Param("scenarioKey") String scenarioKey,
                @Param("expectedRevision") long expectedRevision,
                @Param("operatorId") String operatorId,
                @Param("now") LocalDateTime now);
}
