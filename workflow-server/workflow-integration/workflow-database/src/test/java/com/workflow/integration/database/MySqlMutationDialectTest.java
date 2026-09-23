package com.workflow.integration.database;

import com.workflow.integration.database.api.DatabaseDialects;
import com.workflow.integration.database.api.DatabaseVendor;
import com.workflow.integration.database.api.query.DatabaseSort;
import com.workflow.integration.database.api.runtime.DatabaseRuntimeSql;
import com.workflow.integration.database.api.write.DatabaseMutationDialect;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MySqlMutationDialectTest {
    private final DatabaseMutationDialect dialect = DatabaseDialects.mutations(DatabaseVendor.MYSQL);
    private final List<DatabaseSort> order = List.of(new DatabaseSort("expires_at", false), new DatabaseSort("id", false));

    @Test void mysqlKeepsAtomicOrderedDmlWithBoundValues() {
        assertEquals("UPDATE `leases` SET status = #{status} WHERE (expires_at <= #{now}) ORDER BY `expires_at` ASC, `id` ASC LIMIT #{limit}",
                dialect.updateLimited("leases", "status = #{status}", "expires_at <= #{now}", order, List.of("id"), "#{limit}"));
        assertEquals("DELETE FROM `leases` WHERE (expires_at <= :now) ORDER BY `expires_at` ASC, `id` ASC LIMIT :limit",
                dialect.deleteLimited("leases", "expires_at <= :now", order, List.of("id"), ":limit"));
        assertEquals(" FORCE INDEX (PRIMARY)", dialect.primaryKeyUpdateHint());
    }

    @Test void rejectsUnstableKeysAndUnboundOrInjectedLimits() {
        for (String limit : new String[]{null, "?", "-1", "1; DELETE FROM leases", "#{limit} OR 1=1"}) {
            assertThrows(IllegalArgumentException.class, () -> dialect.deleteLimited("leases", "id = #{id}", order, List.of("id"), limit));
        }
        assertThrows(IllegalArgumentException.class, () -> dialect.deleteLimited("leases", "id = ?", order, List.of("id"), "1"));
        assertThrows(IllegalArgumentException.class, () -> dialect.deleteLimited("leases; DROP TABLE x", "id = #{id}", order, List.of("id"), "1"));
        assertThrows(IllegalArgumentException.class, () -> dialect.deleteLimited("leases", "id = #{id}", order, List.of("id", "tenant_id"), "1"));
        assertThrows(IllegalArgumentException.class, () -> dialect.deleteLimited("leases", "id = #{id}", order, List.of("id", "ID"), "1"));
        assertThrows(IllegalArgumentException.class, () -> dialect.deleteLimited("leases", "id = #{id}", List.of(), List.of("id"), "1"));
    }

    @Test void compositeKeysAndDescendingSortKeepTheirExplicitOrder() {
        assertEquals("DELETE FROM `replays` WHERE (expires_at <= #{now}) ORDER BY `provider_id` ASC, `jti_digest` DESC LIMIT 0",
                dialect.deleteLimited("replays", "expires_at <= #{now}",
                        List.of(new DatabaseSort("provider_id", false), new DatabaseSort("jti_digest", true)),
                        List.of("provider_id", "jti_digest"), "0"));
    }

    @Test void utcDmlUsesDatabaseClockAndBindsSecondsWithoutFormattingValues() {
        assertEquals("UTC_TIMESTAMP(6)", DatabaseRuntimeSql.utcNow("MYSQL"));
        assertEquals("TIMESTAMPADD(SECOND, #{leaseSeconds}, UTC_TIMESTAMP(6))", DatabaseRuntimeSql.utcAfterSeconds("MYSQL", "leaseSeconds"));
        assertThrows(IllegalArgumentException.class, () -> DatabaseRuntimeSql.utcAfterSeconds("MYSQL", "1); DROP TABLE leases"));
        assertThrows(IllegalStateException.class, () -> DatabaseRuntimeSql.utcNow(null));
        assertThrows(IllegalStateException.class, () -> DatabaseDialects.mutationsForDatabaseId(null));
        assertThrows(IllegalArgumentException.class, () -> DatabaseDialects.mutationsForDatabaseId("UNKNOWN"));
    }

    @Test void localWallClockOffsetsKeepJdbcBindingAndDoNotBecomeUtc() {
        var clock = DatabaseDialects.runtime(DatabaseVendor.MYSQL);
        assertEquals("CURRENT_TIMESTAMP", DatabaseRuntimeSql.currentNow("MYSQL"));
        assertEquals("TIMESTAMPADD(SECOND, ?, CURRENT_TIMESTAMP)", clock.currentAfterSeconds("?"));
        assertEquals("TIMESTAMPADD(SECOND, -2592000, CURRENT_TIMESTAMP)", clock.currentAfterSeconds("-2592000"));
        assertThrows(IllegalArgumentException.class, () -> clock.currentAfterSeconds("1; DELETE FROM jobs"));
        assertThrows(IllegalArgumentException.class, () -> clock.currentAfterSeconds("?, ?"));
    }
}
