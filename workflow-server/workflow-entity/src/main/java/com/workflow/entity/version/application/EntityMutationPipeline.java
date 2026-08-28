package com.workflow.entity.version.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditEventIds;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.AuditSourcePointer;
import com.workflow.contracts.audit.OperationContext;
import com.workflow.contracts.audit.OperationContextHolder;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.entity.mutation.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.EntityMutationBatchResult;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.entity.version.application.EntityMutationStepExecutor.ExecutionOutcome;
import com.workflow.entity.form.uniqueness.application.TrustedSubFormUniqueReference;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 所有业务实体新增、修改、删除和状态同步的统一入口。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityMutationPipeline
        implements EntityMutationPort {

    private static final String MAX_EXPANDED_COMMANDS =
            "maxExpandedCommands";

    private final EntityMutationStepExecutor stepExecutor;
    private final EntityMutationTransactionExecutor transactionExecutor;
    private final SystemAuditPort auditPort;

    @Override
    public EntityMutationResult execute(
            EntityMutationCommand input) {
        ExecutionOutcome prepared = prepare(
                withOperator(input));
        List<EntityMutationCommand> commands =
                new ArrayList<>();
        commands.add(prepared.command());
        commands.addAll(prepared.plannedCommands());
        List<EntityMutationResult> results =
                commands.size() == 1
                        ? List.of(transactionExecutor.execute(
                                commands.get(0)))
                        : transactionExecutor.executeBatch(
                                commands);
        afterCommit(commands, results);
        return results.get(0);
    }

    @Override
    public EntityMutationBatchResult executeBatch(
            EntityMutationBatchCommand batch) {
        List<EntityMutationCommand> commands =
                new ArrayList<>();
        int expansionBudget = expansionBudget(batch.commands());
        for (EntityMutationCommand value
                : batch.commands()) {
            ExecutionOutcome prepared =
                    prepare(withOperator(value));
            commands.add(prepared.command());
            commands.addAll(
                    prepared.plannedCommands());
            // 受控扩展可以声明整个 PREPARE 展开后的硬预算。检查必须发生在
            // 事务写入前，不能只限制 Provider 返回的顶层命令数量，否则变更
            // 策略继续追加 plannedCommands 后会绕过页面动作的规模边界。
            if (commands.size() > expansionBudget) {
                throw new IllegalArgumentException(
                        "实体变更计划展开后超过单次允许的 "
                                + expansionBudget + " 条命令");
            }
        }
        List<EntityMutationResult> results;
        if (batch.atomic()) {
            results = transactionExecutor
                    .executeBatch(commands);
        } else {
            results = commands.stream()
                    .map(transactionExecutor::execute)
                    .toList();
        }
        afterCommit(commands, results);
        return new EntityMutationBatchResult(
                batch.operationId(),
                results);
    }

    /**
     * 执行不产生变更版本和审计事件的表单唯一终检。
     *
     * <p>该路径不经过变更规则 PREPARE/AFTER_COMMIT，因为它不改变业务
     * 记录；但仍由事务执行器锁定最终记录并维护 claim。</p>
     */
    @Override
    public void reconcileFormUniqueness(
            String entityCode,
            String recordId,
            EntityMutationContext context) {
        transactionExecutor.reconcileFormUniqueness(
                entityCode,
                recordId,
                context);
    }

    private int expansionBudget(
            List<EntityMutationCommand> commands) {
        int result = Integer.MAX_VALUE;
        for (EntityMutationCommand command : commands) {
            Object raw = command == null || command.context() == null
                    ? null
                    : command.context().extraParams().get(
                    MAX_EXPANDED_COMMANDS);
            if (raw == null) {
                continue;
            }
            int value;
            try {
                value = raw instanceof Number number
                        ? number.intValue()
                        : Integer.parseInt(String.valueOf(raw));
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "实体变更展开预算必须为正整数", exception);
            }
            if (value < 1 || value > 10_000) {
                throw new IllegalArgumentException(
                        "实体变更展开预算必须在 1 到 10000 之间");
            }
            result = Math.min(result, value);
        }
        return result;
    }

    private ExecutionOutcome prepare(
            EntityMutationCommand command) {
        TrustedSubFormUniqueReference.PayloadSnapshot trusted =
                TrustedSubFormUniqueReference.snapshot(
                        command.payload());
        ExecutionOutcome result = stepExecutor.execute(
                command,
                EntityMutationPhase.PREPARE,
                java.util.Map.of(),
                java.util.Map.of());
        // PREPARE 可修改普通字段或展开命令，但不能通过 managed-interface
        // round-trip 剥离/替换服务端发布处理器附加的可信子表单身份。
        TrustedSubFormUniqueReference.requireUnchanged(
                trusted,
                result.command().payload());
        return result;
    }

    private void afterCommit(
            List<EntityMutationCommand> commands,
            List<EntityMutationResult> results) {
        for (int index = 0;
                index < commands.size()
                        && index < results.size();
                index++) {
            if (results.get(index).replayed()) {
                continue;
            }
            recordUnifiedAudit(
                    commands.get(index), results.get(index));
            stepExecutor.execute(
                    commands.get(index),
                    EntityMutationPhase.AFTER_COMMIT,
                    java.util.Map.of(),
                    results.get(index).record(),
                    results.get(index)
                            .versionScenarioCode());
        }
    }

    /**
     * 将已提交的实体变更投影到统一审计时间线。事件只包含稳定标识、业务意图
     * 和变更字段名，不复制实体字段值；详细版本内容仍由实体版本接口鉴权读取。
     */
    private void recordUnifiedAudit(
            EntityMutationCommand command,
            EntityMutationResult result) {
        EntityMutationContext context = command.context();
        OperationContext inherited =
                OperationContextHolder.current().orElse(null);
        String operationId = inherited == null
                ? command.operationId()
                : inherited.operationId();
        String traceId = inherited != null
                && StringUtils.hasText(inherited.traceId())
                ? inherited.traceId()
                : context.businessTraceKey();
        AuditSourcePointer source = new AuditSourcePointer(
                "ENTITY_MUTATION",
                context.sourceType().name(),
                firstText(
                        context.sourceId(),
                        joinedRecordId(
                                context.sourceEntityCode(),
                                context.sourceRecordId())),
                command.operationId());
        try {
            auditPort.record(SystemAuditEvent.builder()
                    .eventId(AuditEventIds.stable(
                            "entity-mutation",
                            command.operationId(),
                            result.entityCode(),
                            result.recordId(),
                            result.operationType()))
                    .operationContext(new OperationContext(
                            operationId,
                            traceId,
                            inherited == null
                                    ? null
                                    : inherited.parentOperationId(),
                            source))
                    .module(AuditModule.ENTITY)
                    .action(auditAction(result.operationType()))
                    .operationName(context.businessIntentName())
                    .riskLevel(AuditRiskLevel.MEDIUM)
                    .result(AuditResult.SUCCESS)
                    .operatorId(context.operatorId())
                    .operatorName(context.operatorName())
                    .targetType("ENTITY_RECORD")
                    .targetId(joinedRecordId(
                            result.entityCode(), result.recordId()))
                    .summary("实体变更已提交："
                            + context.businessIntentName())
                    .changedFields(changedFieldNames(command))
                    .build());
        } catch (RuntimeException exception) {
            // 数据事务此时已经提交，普通审计技术故障不能把成功结果伪装成失败。
            // SystemAuditApplicationService 会另行发出技术故障事件；这里仅保底隔离。
            log.warn(
                    "实体变更统一审计投影失败: operationId={}, entityCode={}, recordId={}, exceptionType={}",
                    command.operationId(),
                    command.entityCode(),
                    result.recordId(),
                    exception.getClass().getName());
        }
    }

    private AuditAction auditAction(
            com.workflow.contracts.entity.mutation.EntityMutationOperationType type) {
        return switch (type) {
            case CREATE -> AuditAction.CREATE;
            case UPDATE, STATUS_CHANGE, APPLY_CHANGE ->
                    AuditAction.UPDATE;
            case DELETE -> AuditAction.DELETE;
            case UPSERT -> AuditAction.UPSERT;
        };
    }

    private List<String> changedFieldNames(
            EntityMutationCommand command) {
        Object data = command.payload().get("data");
        if (data instanceof java.util.Map<?, ?> map) {
            return map.keySet().stream()
                    .map(String::valueOf)
                    .sorted()
                    .toList();
        }
        return command.payload().keySet().stream()
                .sorted()
                .toList();
    }

    private String joinedRecordId(
            String entityCode,
            String recordId) {
        if (!StringUtils.hasText(recordId)) {
            return null;
        }
        return StringUtils.hasText(entityCode)
                ? entityCode + ":" + recordId
                : recordId;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private EntityMutationCommand withOperator(
            EntityMutationCommand command) {
        EntityMutationContext context =
                command.context();
        String operatorId = StringUtils.hasText(
                context.operatorId())
                ? context.operatorId()
                : UserContext.getUserId();
        String operatorName = StringUtils.hasText(
                context.operatorName())
                ? context.operatorName()
                : UserContext.getUsername();
        if (StringUtils.hasText(context.operatorId())
                && StringUtils.hasText(
                        context.operatorName())) {
            return command;
        }
        EntityMutationContext enriched =
                new EntityMutationContext(
                        context.sourceType(),
                        context.sourceId(),
                        context.businessIntentCode(),
                        context.businessIntentName(),
                        context.sourceEntityCode(),
                        context.sourceRecordId(),
                        context.processDefinitionId(),
                        context.processInstanceId(),
                        context.taskId(),
                        operatorId,
                        operatorName,
                        context.businessTraceKey(),
                        context.idempotencyKey(),
                        context.extraParams());
        return new EntityMutationCommand(
                command.operationId(),
                command.entityCode(),
                command.recordId(),
                command.operationType(),
                command.payload(),
                enriched);
    }
}
