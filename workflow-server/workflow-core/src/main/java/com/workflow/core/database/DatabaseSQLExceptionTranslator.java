package com.workflow.core.database;

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

    public DatabaseSQLExceptionTranslator(DatabaseExceptionClassifier classifier) {
        this.classifier = Objects.requireNonNull(classifier);
    }

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

    private DataAccessException fallback(String task, String sql, SQLException error) {
        DataAccessException translated = fallback.translate(task, sql, error);
        // Spring 默认翻译可能只看首个异常，不能推翻完整错误链或产品专属码的保守判断。
        if (translated == null || translated instanceof DuplicateKeyException) {
            return new UncategorizedSQLException(task, sql, error);
        }
        return translated;
    }
}
