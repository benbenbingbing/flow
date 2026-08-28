package com.workflow.entity.data.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationSystemFields;
import com.workflow.contracts.entity.mutation.EntityMutationTargetNotFoundException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.PreparedUniqueClaims;
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
    private final EntityRelationRuntimeService relationRuntimeService;
    private final ObjectMapper objectMapper;

    /**
     * 以统一顺序锁定聚合根：先取得可能存在的自关联定义守卫，再锁业务记录。
     * 递归关系写入复用同一守卫，因此不会出现“业务行等待守卫”和“守卫等待
     * 业务行”的反向锁序。
     */
    public void lock(String entityCode, String recordId) {
        relationRuntimeService.lockSelfRelationGuard(entityCode);
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
        return apply(command, null);
    }

    /**
     * 携带事务执行器生成的 out-of-band 唯一性计划写入聚合。
     * 计划只沿统一变更入口向关系写层传递，不改变其他直接调用的既有语义。
     */
    public WriteResult apply(
            EntityMutationCommand command,
            PreparedUniqueClaims prepared) {
        return switch (command.operationType()) {
            case CREATE -> create(command, prepared);
            case UPDATE, APPLY_CHANGE ->
                    update(command, prepared);
            case DELETE -> delete(command);
            case STATUS_CHANGE ->
                    statusChange(command, prepared);
            case UPSERT -> upsert(command, prepared);
        };
    }

    private WriteResult create(
            EntityMutationCommand command,
            PreparedUniqueClaims prepared) {
        EntityDataDTO dto = objectMapper.convertValue(
                command.payload(),
                EntityDataDTO.class);
        dto.setEntityCode(command.entityCode());
        Object rawData = command.payload().get("data");
        if (rawData instanceof Map<?, ?> map) {
            // 子表单发布引用使用不可伪造的 JVM 私有 Marker 跨越聚合
            // 命令。ObjectMapper.convertValue 会将 Marker 按 @JsonValue 变成
            // 字符串；这里只复用已受信处理的原始嵌套 Map，使关系写层
            // 能在 SQL 前取出 Marker。客户端同名字符串仍不会通过类型检查。
            @SuppressWarnings("unchecked")
            Map<String, Object> trustedData =
                    new LinkedHashMap<>((Map<String, Object>) map);
            dto.setData(trustedData);
        } else if (dto.getData() == null) {
            dto.setData(customPayload(command.payload()));
        }
        EntityDataDTO saved = mutationService.save(dto, prepared);
        return new WriteResult(saved.getId(), saved);
    }

    private WriteResult update(
            EntityMutationCommand command,
            PreparedUniqueClaims prepared) {
        EntityDataDTO saved = mutationService.update(
                command.entityCode(),
                command.recordId(),
                cleanPayload(command.payload()),
                prepared);
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
            EntityMutationCommand command,
            PreparedUniqueClaims prepared) {
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
                    cleanPayload(command.payload()),
                    prepared);
        }
        return new WriteResult(
                command.recordId(),
                null);
    }

    private WriteResult upsert(
            EntityMutationCommand command,
            PreparedUniqueClaims prepared) {
        try {
            queryService.findById(
                    command.entityCode(),
                    command.recordId());
            return update(command, prepared);
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
                    command.context()), prepared);
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
