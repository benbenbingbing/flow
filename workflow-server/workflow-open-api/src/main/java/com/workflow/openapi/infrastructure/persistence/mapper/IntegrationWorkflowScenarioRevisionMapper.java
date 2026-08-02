package com.workflow.openapi.infrastructure.persistence.mapper;

import com.workflow.openapi.infrastructure.persistence.record.IntegrationWorkflowScenarioRevisionRecord;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface IntegrationWorkflowScenarioRevisionMapper {

    @Select("""
            SELECT * FROM integration_workflow_scenario_revision
             WHERE scenario_id = #{scenarioId}
             ORDER BY revision DESC
            """)
    List<IntegrationWorkflowScenarioRevisionRecord> findByScenarioId(
            @Param("scenarioId") String scenarioId);

    @Select("""
            SELECT * FROM integration_workflow_scenario_revision
             WHERE scenario_id = #{scenarioId}
               AND revision = #{revision}
             LIMIT 1
            """)
    IntegrationWorkflowScenarioRevisionRecord findByScenarioAndRevision(
            @Param("scenarioId") String scenarioId,
            @Param("revision") long revision);

    @Select("""
            SELECT * FROM integration_workflow_scenario_revision
             WHERE scenario_id = #{scenarioId}
               AND status = 'DRAFT'
             ORDER BY revision DESC
             LIMIT 1
            """)
    IntegrationWorkflowScenarioRevisionRecord findDraft(
            @Param("scenarioId") String scenarioId);

    @Insert("""
            INSERT INTO integration_workflow_scenario_revision (
              id, scenario_id, revision, status, display_name, process_key,
              process_definition_version, input_schema_json, outcome_mapping_json,
              identity_mapping_json, event_types_json, config_hash, created_by,
              create_time
            ) VALUES (
              #{record.id}, #{record.scenarioId}, #{record.revision}, 'DRAFT',
              #{record.displayName}, #{record.processKey},
              #{record.processDefinitionVersion}, #{record.inputSchemaJson},
              #{record.outcomeMappingJson}, #{record.identityMappingJson},
              #{record.eventTypesJson}, #{record.configHash}, #{operatorId}, #{now}
            )
            """)
    int insertDraft(
            @Param("record") IntegrationWorkflowScenarioRevisionRecord record,
            @Param("operatorId") String operatorId,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE integration_workflow_scenario_revision
               SET status = 'PUBLISHED', published_by = #{operatorId},
                   published_time = #{now}
             WHERE scenario_id = #{scenarioId}
               AND revision = #{revision}
               AND status = 'DRAFT'
            """)
    int publish(
            @Param("scenarioId") String scenarioId,
            @Param("revision") long revision,
            @Param("operatorId") String operatorId,
            @Param("now") LocalDateTime now);

    @Update("""
            UPDATE integration_workflow_scenario_revision
               SET status = 'RETIRED'
             WHERE scenario_id = #{scenarioId}
               AND status = 'PUBLISHED'
               AND revision <> #{revision}
            """)
    int retireOtherPublished(
            @Param("scenarioId") String scenarioId,
            @Param("revision") long revision);

    @Update("""
            UPDATE integration_workflow_scenario_revision
               SET status = 'RETIRED'
             WHERE scenario_id = #{scenarioId}
               AND status = 'DRAFT'
               AND revision <> #{revision}
            """)
    int retireOtherDrafts(
            @Param("scenarioId") String scenarioId,
            @Param("revision") long revision);
}
