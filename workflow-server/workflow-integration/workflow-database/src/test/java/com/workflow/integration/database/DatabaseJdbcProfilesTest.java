package com.workflow.integration.database;

import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.runtime.DatabaseJdbcProfiles;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** OB 两种租户共用驱动 URL；Oracle 修复不得改变 MySQL 的预编译或游标协议。 */
class DatabaseJdbcProfilesTest {
    @Test
    void oracleProtocolDefaultsAreRestrictedToTheExplicitOracleVendor() {
        for (var vendor : DatabaseVendor.values()) {
            var properties = DatabaseJdbcProfiles.connectionProperties(vendor, "jdbc:oceanbase://localhost:2883");
            assertEquals("true", properties.getProperty("remarksReporting"));
            if (vendor == DatabaseVendor.OCEANBASE_ORACLE) {
                assertEquals("true", properties.getProperty("useOraclePrepareExecute"));
                assertEquals("true", properties.getProperty("usePieceData"));
            } else {
                assertNull(properties.getProperty("useOraclePrepareExecute"), vendor.name());
                assertNull(properties.getProperty("usePieceData"), vendor.name());
                assertNull(properties.getProperty("useServerPrepStmts"), vendor.name());
                assertNull(properties.getProperty("useCursorFetch"), vendor.name());
            }
        }
    }

    @Test
    void legacyEntryAndOtherDriversDoNotInferOracleProtocolFromTheUrl() {
        var legacy = DatabaseJdbcProfiles.connectionProperties("jdbc:oceanbase://localhost:2883");
        assertEquals("true", legacy.getProperty("remarksReporting"));
        assertNull(legacy.getProperty("useOraclePrepareExecute"));
        assertNull(legacy.getProperty("usePieceData"));
        assertTrue(DatabaseJdbcProfiles.connectionProperties(DatabaseVendor.OCEANBASE_ORACLE,
                "jdbc:oracle:thin:@localhost:1521/test").isEmpty());
        assertTrue(DatabaseJdbcProfiles.connectionProperties(DatabaseVendor.OCEANBASE_MYSQL,
                "jdbc:mysql://localhost:2881/test").isEmpty());
    }
}
