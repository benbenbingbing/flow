package com.workflow.core.database;

import com.workflow.integration.database.api.error.DatabaseErrorDialect;
import com.workflow.integration.database.api.error.DatabaseErrorKind;
import com.workflow.integration.database.api.write.DatabaseInsertDialect;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;

/** 遍历 JDBC 异常图，再交给 integration 的纯规则；不会操作连接或恢复事务。 */
public final class DatabaseExceptionClassifier {
    private final DatabaseErrorDialect dialect;

    /**
     * 初始化数据库异常{@code classifier}，保存构造参数供后续方法使用。
     *
     * @param dialect 方言，保存在对象中供后续校验、查询或展示
     */
    public DatabaseExceptionClassifier(DatabaseErrorDialect dialect) { this.dialect = Objects.requireNonNull(dialect); }

    /**
     * 兼容现有插入执行器端口，复用异常图遍历，唯一规则仍由其注入的方言提供。
     *
     * @param dialect 方言，供本方法处理{@code insert}时使用
     * @return 处理后的{@code insert}结果，供调用方继续处理
     */
    static DatabaseExceptionClassifier forInsert(DatabaseInsertDialect dialect) {
        Objects.requireNonNull(dialect);
        return new DatabaseExceptionClassifier((state, code) -> dialect.isUniqueViolation(state, code)
                ? DatabaseErrorKind.UNIQUE : DatabaseErrorKind.UNKNOWN);
    }

    /**
     * 检查 cause、suppressed 和 JDBC nextException 的完整错误链。混合类型或未知错误返回 UNKNOWN，
     * 防止批处理、驱动包装或恢复错误被根异常中的一个重复键掩盖；用身份集合阻止循环链。
     *
     * @param error 错误，作为 {@code pending.add} 的输入影响后续处理
     * @return 处理后的{@code classify}结果，供调用方继续处理
     */
    public DatabaseErrorKind classify(Throwable error) {
        if (error == null) return DatabaseErrorKind.UNKNOWN;
        var visited = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        var pending = new ArrayDeque<Throwable>(); pending.add(error);
        DatabaseErrorKind common = null;
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current.getCause() != null) pending.add(current.getCause());
            Collections.addAll(pending, current.getSuppressed());
            if (current instanceof SQLException sql) {
                if (sql.getNextException() != null) pending.add(sql.getNextException());
                DatabaseErrorKind kind = dialect.classify(sql.getSQLState(), sql.getErrorCode());
                if (common != null && common != kind) return DatabaseErrorKind.UNKNOWN;
                common = kind;
            }
        }
        if (common != null) return common;
        // 无 JDBC 根因时保留上游明确的 Spring 唯一分类；不能覆盖真实 SQL 错误证据。
        return error instanceof DuplicateKeyException ? DatabaseErrorKind.UNIQUE : DatabaseErrorKind.UNKNOWN;
    }
}
