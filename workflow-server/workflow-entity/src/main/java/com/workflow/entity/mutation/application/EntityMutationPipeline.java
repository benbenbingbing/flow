package com.workflow.entity.mutation.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.AuditEventIds;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditResult;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.model.AuditSourcePointer;
import com.workflow.contracts.audit.context.OperationContext;
import com.workflow.contracts.audit.context.OperationContextHolder;
import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationBatchResult;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 所有业务实体新增、修改、删除和状态同步的统一入口。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityMutationPipeline
        implements EntityMutationPort {

    private final EntityMutationTransactionExecutor transactionExecutor;
    private final SystemAuditPort auditPort;

    /**
     * 执行实体变更{@code pipeline}，并将结果传给后续步骤。
     *
     * @param input 待执行实体变更{@code pipeline}的原始输入，结果供调用方继续使用
     * @return 执行后的实体变更{@code pipeline}结果，供调用方继续处理
     */
    @Override
    public EntityMutationResult execute(
            EntityMutationCommand input) {
        EntityMutationCommand command = withOperator(input);
        EntityMutationResult result = transactionExecutor.execute(command);
        recordCommittedAudits(List.of(command), List.of(result));
        return result;
    }

    /**
     * 执行实体变更{@code pipeline}批次，并将结果传给后续步骤。
     *
     * @param batch 批次，作为 {@code EntityMutationBatchResult} 的输入影响后续处理
     * @return 执行后的实体变更{@code pipeline}批次结果，供调用方继续处理
     */
    @Override
    public EntityMutationBatchResult executeBatch(
            EntityMutationBatchCommand batch) {
        List<EntityMutationCommand> commands = batch.commands().stream()
                .map(this::withOperator)
                .toList();
        List<EntityMutationResult> results;
        if (batch.atomic()) {
            results = transactionExecutor
                    .executeBatch(commands);
        } else {
            results = commands.stream()
                    .map(transactionExecutor::execute)
                    .toList();
        }
        recordCommittedAudits(commands, results);
        return new EntityMutationBatchResult(
                batch.operationId(),
                results);
    }

    /**
     * 执行不产生变更版本和审计事件的表单唯一终检。
     *
     * <p>该路径不改变业务记录，但仍由事务执行器锁定
     * 最终记录并维护 claim。</p>
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param context 执行上下文，向后续表单{@code uniqueness}步骤传递身份、配置或状态
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

    /**
     * 记录{@code committed}{@code audits}；供后续追溯或审计使用。
     *
     * @param commands {@code commands}，作为 {@code recordUnifiedAudit} 的输入影响后续处理
     * @param results {@code results}，供本方法记录{@code committed}{@code audits}时使用
     */
    private void recordCommittedAudits(
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
        }
    }

    /**
     * 将已提交的实体变更投影到统一审计时间线。事件只包含稳定标识、业务意图
     * 和变更字段名，不复制实体字段值；详细版本内容仍由实体版本接口鉴权读取。
     *
     * @param command 本次命令，后续经校验后用于记录统一审计
     * @param result 结果，供本方法记录统一审计时使用
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

    /**
     * 审计动作；供后续追溯或审计使用。
     *
     * @param type 类型标识，决定后续动作采用的处理分支
     * @return 审计后的动作结果，供调用方继续处理
     */
    private AuditAction auditAction(
            com.workflow.contracts.entity.mutation.model.EntityMutationOperationType type) {
        return switch (type) {
            case CREATE -> AuditAction.CREATE;
            case UPDATE, STATUS_CHANGE, APPLY_CHANGE ->
                    AuditAction.UPDATE;
            case DELETE -> AuditAction.DELETE;
            case UPSERT -> AuditAction.UPSERT;
        };
    }

    /**
     * 整理已变更字段名称集合数据，供调用方遍历或继续处理。
     *
     * @param command 本次命令，后续经校验后用于处理已变更字段名称集合
     * @return 实体变更{@code pipeline}集合，供调用方遍历或展示
     */
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

    /**
     * 生成{@code joined}记录ID文本，供后续匹配或展示。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 处理后的{@code joined}记录ID文本，供调用方比较或展示
     */
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

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 处理操作人，并将结果传给后续步骤。
     *
     * @param command 本次命令，后续经校验后用于处理操作人
     * @return 处理后的操作人结果，供调用方继续处理
     */
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
