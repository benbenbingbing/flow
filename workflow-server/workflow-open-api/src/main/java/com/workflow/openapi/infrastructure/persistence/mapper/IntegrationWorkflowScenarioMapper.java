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

    @Select("""
            SELECT s.id, s.application_id, s.scenario_key,
                   r.display_name, r.process_key,
                   r.process_definition_version, s.status,
                   r.input_schema_json, r.outcome_mapping_json,
                   r.identity_mapping_json, r.event_types_json,
                   s.revision, s.published_revision, s.draft_revision,
                   r.config_hash, s.created_by, s.updated_by,
                   s.create_time, s.update_time
              FROM integration_workflow_scenario s
              JOIN integration_workflow_scenario_revision r
                ON r.scenario_id = s.id
               AND r.revision = s.published_revision
               AND r.status = 'PUBLISHED'
             WHERE s.application_id = #{applicationId}
               AND s.scenario_key = #{scenarioKey}
               AND s.status = 'ACTIVE'
             LIMIT 1
            """)
    IntegrationWorkflowScenarioRecord findPublishedByApplicationAndKey(
            @Param("applicationId") String applicationId,
            @Param("scenarioKey") String scenarioKey);

    @Insert("""
            INSERT INTO integration_workflow_scenario (
              id, application_id, scenario_key, display_name, process_key,
              process_definition_version, status, input_schema_json,
              outcome_mapping_json, identity_mapping_json, event_types_json,
              revision, published_revision, draft_revision, config_hash,
              created_by, updated_by, create_time, update_time
            ) VALUES (
              #{record.id}, #{record.applicationId}, #{record.scenarioKey},
              #{record.displayName}, #{record.processKey},
              #{record.processDefinitionVersion}, #{record.status},
              #{record.inputSchemaJson}, #{record.outcomeMappingJson},
              #{record.identityMappingJson}, #{record.eventTypesJson}, 1,
              NULL, 1, #{record.configHash},
              #{operatorId}, #{operatorId}, #{now}, #{now}
            )
            """)
    int insert(@Param("record") IntegrationWorkflowScenarioRecord record,
               @Param("operatorId") String operatorId,
               @Param("now") LocalDateTime now);

    @Update("""
            UPDATE integration_workflow_scenario
               SET display_name = #{displayName}, process_key = #{processKey},
                   process_definition_version = #{processDefinitionVersion},
                   input_schema_json = #{inputSchemaJson},
                   outcome_mapping_json = #{outcomeMappingJson},
                   identity_mapping_json = #{identityMappingJson},
                   event_types_json = #{eventTypesJson},
                   config_hash = #{configHash},
                   revision = #{revision}, draft_revision = #{revision},
                   updated_by = #{operatorId}, update_time = #{now}
             WHERE application_id = #{applicationId}
               AND scenario_key = #{scenarioKey}
               AND revision = #{expectedRevision}
               AND status IN ('DRAFT', 'ACTIVE')
            """)
    int advanceDraft(
            @Param("applicationId") String applicationId,
            @Param("scenarioKey") String scenarioKey,
            @Param("revision") long revision,
            @Param("expectedRevision") long expectedRevision,
            @Param("displayName") String displayName,
            @Param("processKey") String processKey,
            @Param("processDefinitionVersion") Integer processDefinitionVersion,
            @Param("inputSchemaJson") String inputSchemaJson,
            @Param("outcomeMappingJson") String outcomeMappingJson,
            @Param("identityMappingJson") String identityMappingJson,
            @Param("eventTypesJson") String eventTypesJson,
            @Param("configHash") String configHash,
            @Param("operatorId") String operatorId,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE integration_workflow_scenario
               SET display_name = #{displayName}, process_key = #{processKey},
                   process_definition_version = #{processDefinitionVersion},
                   input_schema_json = #{inputSchemaJson},
                   outcome_mapping_json = #{outcomeMappingJson},
                   identity_mapping_json = #{identityMappingJson},
                   event_types_json = #{eventTypesJson},
                   config_hash = #{configHash}, status = 'ACTIVE',
                   published_revision = #{revision}, draft_revision = NULL,
                   revision = #{revision}, updated_by = #{operatorId},
                   update_time = #{now}
             WHERE application_id = #{applicationId}
               AND scenario_key = #{scenarioKey}
               AND draft_revision = #{revision}
               AND status IN ('DRAFT', 'ACTIVE')
            """)
    int publish(
            @Param("applicationId") String applicationId,
            @Param("scenarioKey") String scenarioKey,
            @Param("revision") long revision,
            @Param("displayName") String displayName,
            @Param("processKey") String processKey,
            @Param("processDefinitionVersion") Integer processDefinitionVersion,
            @Param("inputSchemaJson") String inputSchemaJson,
            @Param("outcomeMappingJson") String outcomeMappingJson,
            @Param("identityMappingJson") String identityMappingJson,
            @Param("eventTypesJson") String eventTypesJson,
            @Param("configHash") String configHash,
            @Param("operatorId") String operatorId,
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
