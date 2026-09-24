package com.workflow.core.database;

import com.workflow.core.concurrent.ExecutionDeadline;
import java.sql.Statement;
import java.sql.SQLException;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.springframework.stereotype.Component;

/** 只在受执行预算保护的调用中收紧 JDBC 超时，普通业务 SQL 保持原有行为。 */
@Component
@Intercepts({
        @Signature(type = StatementHandler.class, method = "query", args = {Statement.class, ResultHandler.class}),
        @Signature(type = StatementHandler.class, method = "update", args = {Statement.class}),
        @Signature(type = StatementHandler.class, method = "batch", args = {Statement.class})
})
public class ExecutionDeadlineInterceptor implements Interceptor {
    @Override public Object intercept(Invocation invocation) throws Throwable {
        ExecutionDeadline deadline = ExecutionDeadline.current();
        if (deadline == null) return invocation.proceed();
        Statement statement = (Statement) invocation.getArgs()[0];
        int remainingSeconds = (int) Math.max(1, (deadline.remainingMillis() + 999) / 1000);
        int configured = statement.getQueryTimeout();
        int effective = configured > 0 ? Math.min(configured, remainingSeconds) : remainingSeconds;
        if (effective != configured) statement.setQueryTimeout(effective);
        try (var cancellation = deadline.onCancellation(() -> {
            try { statement.cancel(); }
            catch (SQLException failure) { throw new IllegalStateException("取消超时数据库语句失败", failure); }
        })) {
            Object result = invocation.proceed();
            deadline.check();
            return result;
        } finally {
            // REUSE 执行器可能复用同一 Statement，不能把本次的短预算遗留给后续普通业务。
            if (effective != configured) {
                try { if (!statement.isClosed()) statement.setQueryTimeout(configured); }
                catch (SQLException resetFailed) {
                    // 驱动在取消后可能已失效，关闭而不是留下携带旧预算的可复用语句；保留原始执行异常。
                    try { statement.close(); } catch (SQLException ignored) { }
                }
            }
        }
    }
}
