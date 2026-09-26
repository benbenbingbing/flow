package com.workflow.core.database.jdbc;

import com.workflow.core.database.jdbc.DatabaseExceptionClassifier;

import java.sql.SQLException;
import java.util.Objects;
import org.springframework.dao.*;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.support.SQLExceptionSubclassTranslator;
import org.springframework.jdbc.support.SQLExceptionTranslator;

/** 将统一方言分类接入 Spring JDBC/MyBatis；事务提交、回滚仍属于原调用方。 */
public final class DatabaseSQLExceptionTranslator implements SQLExceptionTranslator {
    private final DatabaseExceptionClassifier classifier;
    private final SQLExceptionTranslator fallback = new SQLExceptionSubclassTranslator();

    /**
     * 初始化数据库SQL异常{@code translator}，保存构造参数供后续方法使用。
     *
     * @param classifier {@code classifier}，保存在对象中供后续校验、查询或展示
     */
    public DatabaseSQLExceptionTranslator(DatabaseExceptionClassifier classifier) {
        this.classifier = Objects.requireNonNull(classifier);
    }

    /**
     * 构造{@code translate}异常，供调用方区分失败原因。
     *
     * @param task 任务，作为 {@code fallback} 的输入影响后续处理
     * @param sql SQL，作为 {@code fallback} 的输入影响后续处理
     * @param error 错误，作为 {@code DuplicateKeyException} 的输入影响后续处理
     * @return 处理后的{@code translate}结果，供调用方继续处理
     */
    @Override
    public DataAccessException translate(String task, String sql, SQLException error) {
        String detail = task + "; SQL [" + sql + "]; " + error.getMessage();
        return switch (classifier.classify(error)) {
            case UNIQUE -> new DuplicateKeyException(detail, error);
            case NOT_NULL, MISSING_DEFAULT, VALUE_TOO_LONG, FOREIGN_KEY, CHECK, NUMERIC_RANGE ->
                    new DataIntegrityViolationException(detail, error);
            case DEADLOCK -> new PessimisticLockingFailureException(detail, error);
            case LOCK_TIMEOUT -> new CannotAcquireLockException(detail, error);
            case CONNECTION -> new DataAccessResourceFailureException(detail, error);
            case TRANSACTION_ROLLBACK -> new ConcurrencyFailureException(detail, error);
            case UNKNOWN -> fallback(task, sql, error);
        };
    }

    /**
     * 构造兜底异常，供调用方区分失败原因。
     *
     * @param task 任务，作为 {@code fallback.translate} 的输入影响后续处理
     * @param sql SQL，作为 {@code fallback.translate} 的输入影响后续处理
     * @param error 错误，作为 {@code fallback.translate} 的输入影响后续处理
     * @return 处理后的兜底结果，供调用方继续处理
     */
    private DataAccessException fallback(String task, String sql, SQLException error) {
        DataAccessException translated = fallback.translate(task, sql, error);
        // Spring 默认翻译可能只看首个异常，不能推翻完整错误链或产品专属码的保守判断。
        if (translated == null || translated instanceof DuplicateKeyException) {
            return new UncategorizedSQLException(task, sql, error);
        }
        return translated;
    }
}
