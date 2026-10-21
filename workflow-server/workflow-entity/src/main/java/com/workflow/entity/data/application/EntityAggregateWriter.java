package com.workflow.entity.data.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.error.EntityMutationTargetNotFoundException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.form.uniqueness.application.EntityFormUniqueClaimService.PreparedUniqueClaims;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * 动态实体聚合的唯一内部写入器。
 *
 * <p>外部模块只能调用实体变更端口；该写入器负责把标准命令适配到主表、
 * 多值字段、关系和子表单的既有聚合写入实现。</p>
 */
@Component
@RequiredArgsConstructor
public class EntityAggregateWriter {

    @org.springframework.beans.factory.annotation.Autowired
    private com.workflow.entity.history.EntityHistoryWriteGuard historyWriteGuard;

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
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
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

    /**
     * 锁定业务主记录后同步任务摘要，供交接、认领等只改变办理身份的操作使用。
     * 不展开关系、不写业务字段、不产生业务版本；数据库错误仍由外层事务整体回滚。
     * 返回值只含任务摘要，供幂等回执与审计使用，不冒充完整业务快照。
     *
     * @param command 受信流程运行态的 CURRENT_TASK 命令，三个任务字段均允许置空
     * @throws EntityMutationTargetNotFoundException 业务主记录已不存在
     */
    public CurrentTaskSyncResult synchronizeCurrentTask(EntityMutationCommand command) {
        if (historyWriteGuard != null) historyWriteGuard.requireWritable(command.entityCode(), command.recordId());
        if (!EntityMutationSystemFields.isCurrentTaskSync(command)) {
            throw new IllegalArgumentException("仅支持当前任务摘要同步");
        }
        Map<String, Object> row = dynamicMapper.selectByIdForUpdate(
                dynamicTableService.getTableName(command.entityCode()), command.recordId());
        if (row == null) {
            throw new EntityMutationTargetNotFoundException(command.entityCode(), command.recordId());
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("id", command.recordId());
        summary.put("currentTaskId", text(command.payload().get("currentTaskId")));
        summary.put("currentTaskName", text(command.payload().get("currentTaskName")));
        summary.put("currentTaskAssignee", text(command.payload().get("currentTaskAssignee")));
        boolean changed = !Objects.equals(row.get("current_task_id"), summary.get("currentTaskId"))
                || !Objects.equals(row.get("current_task_name"), summary.get("currentTaskName"))
                || !Objects.equals(row.get("current_task_assignee"), summary.get("currentTaskAssignee"));
        // 即使摘要值未变，也登记任务列表投影刷新，以修复尚未同步的旧投影。
        mutationService.updateCurrentTask(command.entityCode(), command.recordId(),
                (String) summary.get("currentTaskId"), (String) summary.get("currentTaskName"),
                (String) summary.get("currentTaskAssignee"));
        return new CurrentTaskSyncResult(summary, changed);
    }

    /** 任务摘要回执不含子表和业务字段，也不参与业务版本比较。 */
    public record CurrentTaskSyncResult(Map<String, Object> record, boolean changed) {}

    /**
     * 应用实体聚合对象写入器，并将结果传给后续步骤。
     *
     * @param command 本次命令，后续经校验后用于应用实体聚合对象写入器
     * @return 应用后的实体聚合对象写入器结果，供调用方继续处理
     */
    public WriteResult apply(
            EntityMutationCommand command) {
        return apply(command, null);
    }

    /**
     * 携带事务执行器生成的 out-of-band 唯一性计划写入聚合。
     * 计划只沿统一变更入口向关系写层传递，不改变其他直接调用的既有语义。
     *
     * @param command 本次命令，后续经校验后用于应用实体聚合对象写入器
     * @param prepared 已准备，作为 {@code create} 的输入影响后续处理
     * @return 应用后的实体聚合对象写入器结果，供调用方继续处理
     */
    public WriteResult apply(
            EntityMutationCommand command,
            PreparedUniqueClaims prepared) {
        // 保护覆盖 Java Port 和批量写入，不能仅靠前端隐藏编辑按钮。
        if (historyWriteGuard != null && command.operationType() != EntityMutationOperationType.CREATE)
            historyWriteGuard.requireWritable(command.entityCode(), command.recordId());
        Object internalMode = command.payload().get(EntityMutationSystemFields.MODE_KEY);
        if (internalMode != null && command.context().sourceType()
                != com.workflow.contracts.entity.mutation.model.EntityMutationSourceType.PROCESS_RUNTIME) {
            throw new IllegalArgumentException("流程运行态字段只能由流程引擎维护");
        }
        // 作用域只来自可信变更命令，嵌套取号使用同一身份；异常路径也必须恢复线程状态。
        try (var ignored = com.workflow.entity.definition.application.code.EntityCodeGenerationScope.open(command.context())) {
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
    }

    /**
     * 创建实体聚合对象写入器；结果供后续流程传递或持久化。
     *
     * @param command 本次命令，后续经校验后用于创建实体聚合对象写入器
     * @param prepared 已准备，作为 {@code mutationService.save} 的输入影响后续处理
     * @return 创建后的实体聚合对象写入器结果，供调用方继续处理
     */
    private WriteResult create(
            EntityMutationCommand command,
            PreparedUniqueClaims prepared) {
        EntityDataDTO dto = objectMapper.convertValue(
                command.payload(),
                EntityDataDTO.class);
        dto.setEntityCode(command.entityCode());
        // CREATE 命令的 DTO 若携带已有 ID，底层兼容路径会按更新处理，也必须受历史写保护。
        if (historyWriteGuard != null) historyWriteGuard.requireWritable(command.entityCode(), dto.getId());
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

    /**
     * 更新实体聚合对象写入器；后续读取或执行将使用更新后的状态。
     *
     * @param command 本次命令，后续经校验后用于更新实体聚合对象写入器
     * @param prepared 已准备，供本方法更新实体聚合对象写入器时使用
     * @return 更新后的实体聚合对象写入器结果，供调用方继续处理
     */
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

    /**
     * 删除实体聚合对象写入器；后续读取或执行将使用更新后的状态。
     *
     * @param command 本次命令，后续经校验后用于删除实体聚合对象写入器
     * @return 删除后的实体聚合对象写入器结果，供调用方继续处理
     */
    private WriteResult delete(
            EntityMutationCommand command) {
        mutationService.delete(
                command.entityCode(),
                command.recordId());
        return new WriteResult(
                command.recordId(),
                null);
    }

    /**
     * 处理状态变更，并将结果传给后续步骤。
     *
     * @param command 本次命令，后续经校验后用于处理状态变更
     * @param prepared 已准备，供本方法处理状态变更时使用
     * @return 处理后的状态变更结果，供调用方继续处理
     */
    private WriteResult statusChange(
            EntityMutationCommand command,
            PreparedUniqueClaims prepared) {
        String mode = text(command.payload()
                .get(EntityMutationSystemFields.MODE_KEY));
        if (EntityMutationSystemFields.CALL_PROCESS_START.equals(mode)) {
            // 系统模式不能从普通表单/业务动作进入，实例身份必须来自可信引擎上下文。
            if (command.context().sourceType() != com.workflow.contracts.entity.mutation.model.EntityMutationSourceType.PROCESS_RUNTIME
                    || command.context().processInstanceId() == null) {
                throw new IllegalArgumentException("子流程绑定只允许流程引擎调用");
            }
            mutationService.bindCalledProcess(command.entityCode(), command.recordId(),
                    command.context().processInstanceId(), command.context().operatorId(),
                    command.context().operatorName(), text(command.payload().get("processingStatus")));
        } else if (EntityMutationSystemFields.PROCESS_END.equals(mode)) {
            mutationService.markProcessEnded(
                    command.context().processInstanceId(),
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

    /**
     * 处理新增或更新，并将结果传给后续步骤。
     *
     * @param command 本次命令，后续经校验后用于处理新增或更新
     * @param prepared 已准备，作为 {@code update} 的输入影响后续处理
     * @return 处理后的新增或更新结果，供调用方继续处理
     */
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

    /**
     * 整理{@code clean}载荷数据，供调用方遍历或继续处理。
     *
     * @param payload 载荷，后续用于处理{@code clean}载荷并传递处理结果
     * @return {@code clean}载荷键值结果，供调用方继续处理
     */
    private Map<String, Object> cleanPayload(
            Map<String, Object> payload) {
        Map<String, Object> result =
                new LinkedHashMap<>(payload);
        result.remove(EntityMutationSystemFields.MODE_KEY);
        result.remove("statusCategory");
        result.remove("fallbackStatus");
        return result;
    }

    /**
     * 整理自定义载荷数据，供调用方遍历或继续处理。
     *
     * @param payload 载荷，后续用于处理自定义载荷并传递处理结果
     * @return 自定义载荷键值结果，供调用方继续处理
     */
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

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value) {
        return value == null
                ? null : String.valueOf(value);
    }

    /**
     * 封装写入的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param value 待处理写入结果的原始输入，结果供调用方继续使用
     */
    public record WriteResult(
            String recordId,
            EntityDataDTO value) {
    }
}
