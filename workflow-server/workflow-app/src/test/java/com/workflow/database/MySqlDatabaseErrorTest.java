package com.workflow.database;

import com.workflow.core.database.jdbc.DatabaseExceptionClassifier;
import com.workflow.core.database.jdbc.DatabaseSQLExceptionTranslator;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.*;
import static com.workflow.integration.database.api.error.DatabaseErrorKind.*;
import static org.junit.jupiter.api.Assertions.*;

/** MySQL 错误分类的反例优先：文本不能覆盖驱动证据，混合或循环异常链不能变成幂等成功。 */
class MySqlDatabaseErrorTest {
    private final DatabaseExceptionClassifier classifier =
            new DatabaseExceptionClassifier(DatabaseDialects.errors(DatabaseVendor.MYSQL));
    private final DatabaseSQLExceptionTranslator translator = new DatabaseSQLExceptionTranslator(classifier);

    @Test void classifiesKnownCodesWithoutReadingEnglishOrChineseMessages() {
        int[] codes = {1062, 1048, 1364, 1406, 1451, 1452, 3819, 1264, 1213, 1205};
        var kinds = new com.workflow.integration.database.api.error.DatabaseErrorKind[] {
                UNIQUE, NOT_NULL, MISSING_DEFAULT, VALUE_TOO_LONG, FOREIGN_KEY, FOREIGN_KEY,
                CHECK, NUMERIC_RANGE, DEADLOCK, LOCK_TIMEOUT};
        for (int i = 0; i < codes.length; i++) {
            assertEquals(kinds[i], classifier.classify(new SQLException("任意文本", "HY000", codes[i])));
        }
        assertEquals(UNKNOWN, classifier.classify(new SQLException("Duplicate entry for key 'uk_any'", "23000", 99999)));
        assertEquals(UNKNOWN, classifier.classify(new SQLException("unique", "23505", 0)));
        assertEquals(CONNECTION, classifier.classify(new SQLException("unique-looking", "08006", 1062)));
        assertEquals(TRANSACTION_ROLLBACK, classifier.classify(new SQLException("unique-looking", "40001", 1062)));
        assertEquals(LOCK_TIMEOUT, classifier.classify(new SQLException("lock wait", "40001", 1205)));
    }

    @Test void allUniqueChainKeepsDuplicateAndOriginalCause() {
        var first = new SQLException("first", "23000", 1062);
        first.setNextException(new SQLException("second", "23000", 1062));
        var translated = translator.translate("insert", "INSERT ...", first);
        assertInstanceOf(DuplicateKeyException.class, translated);
        assertSame(first, translated.getCause());
    }

    @Test void mixedJdbcChainsNeverProduceDuplicate() {
        for (int other : new int[]{1048, 1452, 1213, 1205, 0}) {
            var first = new SQLException("duplicate", "23000", 1062);
            first.setNextException(new SQLException("other", "HY000", other));
            assertEquals(UNKNOWN, classifier.classify(first));
            assertFalse(translator.translate("insert", "INSERT ...", first) instanceof DuplicateKeyException);
            assertEquals(UNKNOWN, classifier.classify(new DuplicateKeyException("wrapper", first)));
        }
    }

    @Test void visitsNestedCausesSuppressedFailuresAndCycles() {
        var root = new SQLException("root", "23000", 1062);
        var nested = new SQLException("nested", "23000", 1062);
        root.initCause(nested); nested.initCause(root);
        assertEquals(UNIQUE, classifier.classify(root));
        nested.addSuppressed(new SQLException("connection recovery failed", "08006", 0));
        assertEquals(UNKNOWN, classifier.classify(root));
        assertEquals(UNIQUE, classifier.classify(new DuplicateKeyException("framework unique without SQL")));
        assertEquals(UNKNOWN, classifier.classify(new RuntimeException("Duplicate entry")));
    }

    @Test void unknownAndTransientErrorsRetainFailureClasses() {
        assertInstanceOf(DataIntegrityViolationException.class,
                translator.translate("insert", "sql", new SQLException("check", "HY000", 3819)));
        assertInstanceOf(PessimisticLockingFailureException.class,
                translator.translate("update", "sql", new SQLException("deadlock", "40001", 1213)));
        assertInstanceOf(CannotAcquireLockException.class,
                translator.translate("update", "sql", new SQLException("timeout", "HY000", 1205)));
        assertInstanceOf(DataAccessResourceFailureException.class,
                translator.translate("update", "sql", new SQLException("broken", "08006", 0)));
        assertInstanceOf(org.springframework.jdbc.BadSqlGrammarException.class,
                translator.translate("query", "sql", new SQLException("syntax", "42000", 1064)));
    }
}
