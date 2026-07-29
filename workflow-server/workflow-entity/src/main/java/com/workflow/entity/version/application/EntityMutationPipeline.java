package com.workflow.entity.version.application;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.mutation.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.EntityMutationBatchResult;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.entity.version.application.EntityMutationStepExecutor.ExecutionOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * 所有业务实体新增、修改、删除和状态同步的统一入口。
 */
@Service
@RequiredArgsConstructor
public class EntityMutationPipeline
        implements EntityMutationPort {

    private final EntityMutationStepExecutor stepExecutor;
    private final EntityMutationTransactionExecutor transactionExecutor;

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
        for (EntityMutationCommand value
                : batch.commands()) {
            ExecutionOutcome prepared =
                    prepare(withOperator(value));
            commands.add(prepared.command());
            commands.addAll(
                    prepared.plannedCommands());
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

    private ExecutionOutcome prepare(
            EntityMutationCommand command) {
        return stepExecutor.execute(
                command,
                EntityMutationPhase.PREPARE,
                java.util.Map.of(),
                java.util.Map.of());
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
            stepExecutor.execute(
                    commands.get(index),
                    EntityMutationPhase.AFTER_COMMIT,
                    java.util.Map.of(),
                    results.get(index).record(),
                    results.get(index)
                            .versionScenarioCode());
        }
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
