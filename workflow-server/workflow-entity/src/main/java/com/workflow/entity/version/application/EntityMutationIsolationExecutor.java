package com.workflow.entity.version.application;

import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationResult;
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

    /**
     * 执行实体变更{@code isolation}执行器，并将结果传给后续步骤。
     *
     * @param command 本次命令，后续经校验后用于执行实体变更{@code isolation}执行器
     * @return 执行后的实体变更{@code isolation}执行器结果，供调用方继续处理
     */
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
