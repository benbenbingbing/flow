package com.workflow.entity.data.application;

import com.workflow.contracts.process.ProcessRuntimePort;
import com.workflow.contracts.process.ProcessStartRequest;
import com.workflow.contracts.process.ProcessStartResult;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.mapping.EntityRuntimeRecordMapper;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.model.EntityPublishedSnapshot;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityStatusMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Starts entity workflows and maps runtime state back to entity data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EntityWorkflowRuntimeService {

    private final EntityPublishedSnapshotService snapshotService;
    private final ProcessRuntimePort processRuntimePort;
    private final EntityStatusMapper entityStatusMapper;
    private final EntityDataDynamicMapper dynamicMapper;
    private final DynamicTableService dynamicTableService;
    private final EntityRuntimeRecordMapper recordMapper;
    private final EntityRecordTeamService entityRecordTeamService;

    public void start(EntityDataDTO dto) {
        EntityPublishedSnapshot snapshot =
                snapshotService.getLatestByEntityCode(
                        dto.getEntityCode());
        String processDefinitionId =
                snapshot.getProcessDefinitionId();
        if (!StringUtils.hasText(processDefinitionId)) {
            throw new BusinessConflictException(
                    "ENTITY_WORKFLOW_NOT_READY",
                    "实体发布快照未绑定流程定义: "
                            + dto.getEntityCode());
        }
        ProcessStartResult result = processRuntimePort.start(
                new ProcessStartRequest(
                        processDefinitionId,
                        dto.getEntityCode(),
                        dto.getId(),
                        dto.getDataNo(),
                        dto.getSubmitterId(),
                        dto.getSubmitterName(),
                        statusByCategory(
                                dto.getEntityCode(),
                                "PROCESSING",
                                "PENDING"),
                        dto.getData(),
                        dto.getProcessVariables()));

        LocalDateTime startedAt = LocalDateTime.now();
        Map<String, Object> updateData = new HashMap<>();
        updateData.put("id", dto.getId());
        updateData.put(
                "process_instance_id",
                result.processInstanceId());
        updateData.put("process_start_time", startedAt);
        updateData.put("status", result.entityStatus());
        updateData.put("update_time", startedAt);
        updateData.put(
                "current_task_id",
                result.currentTaskId());
        updateData.put(
                "current_task_name",
                result.currentTaskName());
        updateData.put(
                "current_task_assignee",
                result.currentTaskAssignee());
        putPublishedTimestampIfPresent(
                snapshot,
                updateData,
                "submitted_at",
                startedAt);
        dynamicMapper.update(
                dynamicTableService.getTableName(
                        dto.getEntityCode()),
                updateData);

        dto.setProcessInstanceId(result.processInstanceId());
        dto.setStatus(result.entityStatus());
        dto.setCurrentTaskId(result.currentTaskId());
        dto.setCurrentTaskName(result.currentTaskName());
        dto.setCurrentTaskAssignee(
                result.currentTaskAssignee());
        entityRecordTeamService.record(
                dto.getEntityCode(),
                dto.getId(),
                "START_PROCESS",
                "发起流程",
                result.processInstanceId(),
                result.currentTaskId());
    }

    public void putPublishedTimestampIfPresent(
            String entityCode,
            Map<String, Object> updateData,
            String fieldCode,
            LocalDateTime value) {
        putPublishedTimestampIfPresent(
                snapshotService.findLatestByEntityCode(entityCode),
                updateData,
                fieldCode,
                value);
    }

    public String statusByCategory(
            String entityCode,
            String category,
            String fallback) {
        try {
            List<EntityStatus> statuses =
                    entityStatusMapper.findByCategory(
                            entityCode,
                            category);
            if (statuses != null && !statuses.isEmpty()) {
                return statuses.get(0).getStatusCode();
            }
        } catch (Exception exception) {
            log.warn(
                    "获取实体[{}]状态分类[{}]失败: {}",
                    entityCode,
                    category,
                    exception.getMessage());
        }
        return fallback;
    }

    private void putPublishedTimestampIfPresent(
            EntityPublishedSnapshot snapshot,
            Map<String, Object> updateData,
            String fieldCode,
            LocalDateTime value) {
        if (snapshot == null || snapshot.getFields() == null) {
            return;
        }
        snapshot.getFields().stream()
                .filter(field ->
                        fieldCode.equals(field.getFieldCode()))
                .filter(field -> !isRelationField(field))
                .findFirst()
                .ifPresent(field -> updateData.put(
                        columnName(field),
                        value));
    }

    private String columnName(EntityField field) {
        return StringUtils.hasText(field.getDbColumnName())
                ? field.getDbColumnName()
                : recordMapper.toColumnName(
                        field.getFieldCode());
    }

    private boolean isRelationField(EntityField field) {
        return field.getFieldType()
                == EntityField.FieldType.SUB_FORM
                || field.getFieldType()
                == EntityField.FieldType.SUB_FORM_LIST;
    }
}
