package com.workflow.entity.data.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationSystemFields;
import com.workflow.contracts.entity.mutation.EntityMutationTargetNotFoundException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 动态实体聚合的唯一内部写入器。
 *
 * <p>外部模块只能调用实体变更端口；该写入器负责把标准命令适配到主表、
 * 多值字段、关系和子表单的既有聚合写入实现。</p>
 */
@Component
@RequiredArgsConstructor
public class EntityAggregateWriter {

    private final EntityDataDynamicService queryService;
    private final EntityDataMutationService mutationService;
    private final DynamicTableService dynamicTableService;
    private final EntityDataDynamicMapper dynamicMapper;
    private final ObjectMapper objectMapper;

    public void lock(String entityCode, String recordId) {
        Map<String, Object> value =
                dynamicMapper.selectByIdForUpdate(
                        dynamicTableService.getTableName(
                                entityCode),
                        recordId);
        if (value == null) {
            throw new EntityMutationTargetNotFoundException(
                    entityCode,
                    recordId);
        }
    }

    public WriteResult apply(
            EntityMutationCommand command) {
        return switch (command.operationType()) {
            case CREATE -> create(command);
            case UPDATE, APPLY_CHANGE ->
                    update(command);
            case DELETE -> delete(command);
            case STATUS_CHANGE ->
                    statusChange(command);
            case UPSERT -> upsert(command);
        };
    }

    private WriteResult create(
            EntityMutationCommand command) {
        EntityDataDTO dto = objectMapper.convertValue(
                command.payload(),
                EntityDataDTO.class);
        dto.setEntityCode(command.entityCode());
        if (dto.getData() == null) {
            dto.setData(customPayload(command.payload()));
        }
        EntityDataDTO saved = mutationService.save(dto);
        return new WriteResult(saved.getId(), saved);
    }

    private WriteResult update(
            EntityMutationCommand command) {
        EntityDataDTO saved = mutationService.update(
                command.entityCode(),
                command.recordId(),
                cleanPayload(command.payload()));
        return new WriteResult(command.recordId(), saved);
    }

    private WriteResult delete(
            EntityMutationCommand command) {
        mutationService.delete(
                command.entityCode(),
                command.recordId());
        return new WriteResult(
                command.recordId(),
                null);
    }

    private WriteResult statusChange(
            EntityMutationCommand command) {
        String mode = text(command.payload()
                .get(EntityMutationSystemFields.MODE_KEY));
        if (EntityMutationSystemFields.PROCESS_END.equals(mode)) {
            mutationService.markProcessEnded(
                    command.entityCode(),
                    command.recordId(),
                    text(command.payload()
                            .get("statusCategory")),
                    text(command.payload()
                            .get("fallbackStatus")));
        } else if (EntityMutationSystemFields.CURRENT_TASK.equals(mode)) {
            mutationService.updateCurrentTask(
                    command.entityCode(),
                    command.recordId(),
                    text(command.payload()
                            .get("currentTaskId")),
                    text(command.payload()
                            .get("currentTaskName")),
                    text(command.payload()
                            .get("currentTaskAssignee")));
        } else {
            mutationService.update(
                    command.entityCode(),
                    command.recordId(),
                    cleanPayload(command.payload()));
        }
        return new WriteResult(
                command.recordId(),
                null);
    }

    private WriteResult upsert(
            EntityMutationCommand command) {
        try {
            queryService.findById(
                    command.entityCode(),
                    command.recordId());
            return update(command);
        } catch (RuntimeException ignored) {
            Map<String, Object> payload =
                    new LinkedHashMap<>(command.payload());
            payload.remove("id");
            return create(new EntityMutationCommand(
                    command.operationId(),
                    command.entityCode(),
                    null,
                    EntityMutationOperationType.CREATE,
                    payload,
                    command.context()));
        }
    }

    private Map<String, Object> cleanPayload(
            Map<String, Object> payload) {
        Map<String, Object> result =
                new LinkedHashMap<>(payload);
        result.remove(EntityMutationSystemFields.MODE_KEY);
        result.remove("statusCategory");
        result.remove("fallbackStatus");
        return result;
    }

    private Map<String, Object> customPayload(
            Map<String, Object> payload) {
        Map<String, Object> result =
                new LinkedHashMap<>(payload);
        for (String key : objectMapper.convertValue(
                new EntityDataDTO(),
                new TypeReference<Map<String, Object>>() {
                }).keySet()) {
            result.remove(key);
        }
        return result;
    }

    private String text(Object value) {
        return value == null
                ? null : String.valueOf(value);
    }

    public record WriteResult(
            String recordId,
            EntityDataDTO value) {
    }
}
