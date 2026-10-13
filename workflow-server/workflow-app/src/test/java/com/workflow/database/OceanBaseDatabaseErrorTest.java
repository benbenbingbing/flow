package com.workflow.database;

import com.workflow.core.database.jdbc.DatabaseExceptionClassifier;
import com.workflow.core.database.jdbc.DatabaseSQLExceptionTranslator;
import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import org.junit.jupiter.api.Test;
import org.springframework.dao.ConcurrencyFailureException;

import java.sql.SQLException;

import static com.workflow.integration.database.api.error.DatabaseErrorKind.*;
import static org.junit.jupiter.api.Assertions.*;

/** 将 OB 实库复现的 RR 序列化冲突保留为并发失败，重试必须由完整业务事务决定。 */
class OceanBaseDatabaseErrorTest {
    @Test
    void serializationConflictRetainsItsCauseAndDoesNotBecomeAnUnknownSqlFailure() {
        var classifier = new DatabaseExceptionClassifier(DatabaseDialects.errors(DatabaseVendor.OCEANBASE_MYSQL));
        var translator = new DatabaseSQLExceptionTranslator(classifier);
        var error = new SQLException("can't serialize access for this transaction", "25000", 6235);

        assertEquals(TRANSACTION_ROLLBACK, classifier.classify(error));
        var translated = translator.translate("update", "UPDATE record SET revision = revision + 1", error);
        assertInstanceOf(ConcurrencyFailureException.class, translated);
        assertSame(error, translated.getCause());
    }

    @Test
    void oceanbaseCodeDoesNotOverrideConnectionFailuresOrOtherVendors() {
        var dialect = DatabaseDialects.errors(DatabaseVendor.OCEANBASE_MYSQL);
        assertEquals(CONNECTION, dialect.classify("08006", 6235));
        assertEquals(UNKNOWN, dialect.classify("25000", 99999));
        assertEquals(UNKNOWN, DatabaseDialects.errors(DatabaseVendor.MYSQL).classify("25000", 6235));
        assertEquals(UNKNOWN, DatabaseDialects.errors(DatabaseVendor.OCEANBASE_ORACLE).classify("25000", 6235));
    }
}
