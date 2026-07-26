package com.workflow.system.audit.infrastructure;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * 保证事务拦截器包裹审计切面，使必需审计可以和业务写入共同提交或回滚。
 */
@Configuration
@EnableTransactionManagement(order = Ordered.HIGHEST_PRECEDENCE)
public class SystemAuditTransactionConfiguration {
}
