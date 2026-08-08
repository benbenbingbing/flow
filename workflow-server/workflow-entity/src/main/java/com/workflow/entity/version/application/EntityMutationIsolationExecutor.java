package com.workflow.entity.version.application;

import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 在暂停调用方事务的情况下执行单条实体变更。
 *
 * <p>用于流程结束等跨模块同步：实体变更自行开启并提交事务，失败时不会把
 * 调用方的 Outbox 消费事务标记为 rollback-only。</p>
 */
@Component
@RequiredArgsConstructor
public class EntityMutationIsolationExecutor {

    private final EntityMutationPort mutationPort;
    private final PlatformTransactionManager transactionManager;

    public EntityMutationResult execute(
            EntityMutationCommand command) {
        TransactionTemplate template =
                new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
        return template.execute(
                ignored -> mutationPort.execute(command));
    }
}
