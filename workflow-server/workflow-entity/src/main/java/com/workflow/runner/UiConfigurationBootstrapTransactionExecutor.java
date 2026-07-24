package com.workflow.runner;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * UI 配置启动迁移事务执行器。
 * <p>封装 {@link TransactionTemplate} 以 REQUIRES_NEW 传播级别独立执行迁移/发布操作，
 * 使单次表单或列表的迁移失败不影响其他项。
 */
@Component
@RequiredArgsConstructor
public class UiConfigurationBootstrapTransactionExecutor {

    private final PlatformTransactionManager transactionManager;

    /**
     * 在新事务中执行给定操作。
     *
     * @param operation 需要在独立事务中执行的业务逻辑
     * @param <T>       返回值类型
     * @return 操作返回值
     */
    public <T> T execute(Supplier<T> operation) {
        TransactionTemplate transaction = new TransactionTemplate(
                transactionManager);
        transaction.setPropagationBehavior(
                TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction.execute(status -> operation.get());
    }
}
