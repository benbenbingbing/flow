package com.workflow.embed.infrastructure.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workflow.embed.application.port.EmbedTrafficControlPort;
import com.workflow.embed.domain.EmbedSessionExchangePlan;
import com.workflow.embed.infrastructure.persistence.adapter.MyBatisEmbedSessionExchangeAdapter;
import com.workflow.embed.infrastructure.persistence.adapter.MyBatisEmbedSessionPersistenceAdapter;
import com.workflow.embed.infrastructure.persistence.adapter.MyBatisEmbedMaintenanceAdapter;
import com.workflow.embed.infrastructure.persistence.adapter.MyBatisEmbedTrafficControlAdapter;
import com.workflow.embed.application.session.EmbedSessionTerminationService;
import com.workflow.embed.application.audit.EmbedLifecycleAudit.Operator;
import com.workflow.embed.application.audit.EmbedAuditCorrelation;
import com.workflow.embed.application.audit.EmbedLifecycleMetrics.Surface;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Static mapper contract tests for security-critical SQL that must not regress silently. */
class EmbedPersistenceSqlContractTest {

    @Test
    void launchWriteStoresDigestAndProtectedContextButNoPlaintextCredentialColumns()
            throws Exception {
        Method method = findByName(EmbedLaunchPersistenceMapper.class, "insertLaunch");
        String sql = sql(method.getAnnotation(Insert.class).value());

        assertTrue(sql.contains("launch_code_digest"));
        assertTrue(sql.contains("context_ciphertext"));
        assertTrue(sql.contains("subject_digest"));
        assertFalse(sql.contains("launch_code,"));
        assertFalse(sql.contains("external_user_id"));
        assertFalse(sql.contains("assertion"));

        String configuration = sql(findByName(
                EmbedLaunchPersistenceMapper.class, "findConfiguration")
                .getAnnotation(Select.class).value());
        assertTrue(configuration.contains("g.launch_limit_per_minute"));
        assertTrue(configuration.contains("g.runtime_limit_per_minute"));
        assertTrue(configuration.contains("g.max_concurrency"));
        assertTrue(configuration.contains("v.draft_config_json AS current_config_json"));
        assertFalse(configuration.contains("published_release_id"));
        assertFalse(configuration.contains("revision_mode"));
        assertFalse(configuration.contains("pinned_revision"));
        assertFalse(configuration.contains("FOR UPDATE"),
                "quota preflight must not retain locks before REQUIRES_NEW accounting");

        String lockedConfiguration = sql(findByName(
                EmbedLaunchPersistenceMapper.class, "lockConfiguration")
                .getAnnotation(Select.class).value());
        assertTrue(lockedConfiguration.contains("FOR UPDATE"),
                "final Launch issuance must reload and lock its security configuration");
        assertTrue(lockedConfiguration.contains(
                "v.draft_config_json AS current_config_json"));
    }

    @Test
    void exchangeUsesGuardedCounterAndConditionalOneTimeLaunchConsumption()
            throws Exception {
        String counter = updateSql(
                EmbedSessionExchangeMapper.class,
                "incrementCounter",
                String.class, String.class, int.class, LocalDateTime.class);
        String consume = updateSql(
                EmbedSessionExchangeMapper.class,
                "consumeLaunch",
                String.class, String.class, String.class, String.class,
                String.class, LocalDateTime.class);
        String insert = insertSql(EmbedSessionExchangeMapper.class, "insertSession",
                EmbedSessionExchangePlan.class, LocalDateTime.class,
                LocalDateTime.class, LocalDateTime.class);

        assertTrue(counter.contains("active_count < #{limit}"));
        assertTrue(consume.contains("status = 'ISSUED'"));
        assertTrue(consume.contains("expires_at > #{now}"));
        assertTrue(consume.contains("launch_code_digest = #{launchCodeDigest}"));
        assertTrue(consume.contains("channel_id = #{channelId}"));
        assertTrue(consume.contains("parent_origin = #{parentOrigin}"));
        assertTrue(insert.contains("session_token_digest"));
        assertFalse(insert.contains("access_token"));
        assertTrue(insert.contains("slot_released"));
    }

    @Test
    void allExchangeSecurityRootsAndCounterAndLaunchHaveForUpdateLocks()
            throws Exception {
        for (String method : new String[]{
                "lockApplication", "lockView", "lockGrant", "lockProvider",
                "lockBinding", "lockFlowUser", "lockCounter", "lockLaunch"}) {
            Method reflected = findByName(EmbedSessionExchangeMapper.class, method);
            assertTrue(sql(reflected.getAnnotation(Select.class).value()).contains("FOR UPDATE"),
                    method + " must retain its row lock");
        }
    }

    @Test
    void terminationUsesSlotGuardAndNeverDecrementsBelowZero() throws Exception {
        String terminate = updateSqlByName(
                EmbedSessionPersistenceMapper.class, "terminateActive");
        String decrement = updateSqlByName(
                EmbedSessionPersistenceMapper.class, "decrementCounter");

        assertTrue(terminate.contains("status = 'ACTIVE'"));
        assertTrue(terminate.contains("slot_released = 0"));
        assertTrue(decrement.contains("CASE WHEN active_count > 0 THEN active_count - 1 ELSE 0 END"));
    }

    @Test
    void expiryScanIsBoundedAndOnlySelectsUnreleasedActiveSessions() {
        String scan = sql(findByName(
                EmbedSessionPersistenceMapper.class,
                "findExpiredTokenDigests").getAnnotation(Select.class).value());

        assertTrue(scan.contains("status = 'ACTIVE'"));
        assertTrue(scan.contains("slot_released = 0"));
        assertTrue(scan.contains("idle_expires_at <= #{now}"));
        assertTrue(scan.contains("absolute_expires_at <= #{now}"));
        assertTrue(scan.contains("LIMIT #{limit}"));
    }

    @Test
    void runtimeAuthenticationReloadsAllLiveBindingActorCoordinates() {
        for (String method : new String[]{"findByTokenDigest", "findById"}) {
            String statement = sql(findByName(
                    EmbedSessionPersistenceMapper.class, method)
                    .getAnnotation(Select.class).value());
            assertTrue(statement.contains(
                    "b.application_id AS current_binding_application_id"));
            assertTrue(statement.contains(
                    "b.identity_provider_id AS current_binding_identity_provider_id"));
            assertTrue(statement.contains(
                    "b.flow_user_id AS current_binding_flow_user_id"));
        }
    }

    @Test
    void maintenanceMutationsAreBoundedGuardedAndPreserveSensitiveDataChecks() {
        String replay = deleteSqlByName(EmbedMaintenanceMapper.class,
                "deleteExpiredAssertionReplays");
        String launchExpiry = updateSqlByName(EmbedMaintenanceMapper.class,
                "expireIssuedLaunches");
        String erase = updateSqlByName(EmbedMaintenanceMapper.class,
                "eraseTerminalSessionContexts");
        String receipts = deleteSqlByName(EmbedMaintenanceMapper.class,
                "deleteOrphanOperationReceipts");
        String sessions = deleteSqlByName(EmbedMaintenanceMapper.class,
                "deleteTerminalSessions");
        String launches = deleteSqlByName(EmbedMaintenanceMapper.class,
                "deleteUnreferencedTerminalLaunches");

        for (String statement : new String[]{
                replay, launchExpiry, erase, receipts, sessions, launches}) {
            assertTrue(statement.contains("LIMIT #{limit}"));
        }
        assertTrue(replay.contains("expires_at <= #{now}"));
        assertTrue(launchExpiry.contains("status = 'ISSUED'"));
        assertTrue(launchExpiry.contains("expires_at <= #{now}"));
        assertTrue(erase.contains("context_ciphertext = '{}'"));
        assertTrue(erase.contains("context_cipher_key_version = 'embed-erased-v1'"));
        assertTrue(erase.contains("context_digest = REPEAT('0', 64)"));
        assertTrue(erase.contains("slot_released = 1"));
        assertTrue(receipts.contains("NOT EXISTS"));
        assertTrue(receipts.contains("integration_idempotency_record"));
        assertTrue(sessions.contains("slot_released = 1"));
        assertTrue(launches.contains("NOT EXISTS"));
        assertTrue(launches.contains("update_time <= #{cutoff}"));
        assertTrue(launches.contains("s.launch_id = embed_launch.id"));
    }

    @Test
    void counterReconciliationIsBoundedKeysetReadOnlyAndNeverRepairsRows() {
        String counters = selectSqlByName(EmbedMaintenanceMapper.class,
                "inspectStoredCounterPage");
        String activePairs = selectSqlByName(EmbedMaintenanceMapper.class,
                "inspectActiveSessionPairPage");

        for (String statement : new String[]{counters, activePairs}) {
            assertTrue(statement.contains("LIMIT #{limit}"));
            assertTrue(statement.contains("#{afterGrantId}"));
            assertTrue(statement.contains("#{afterFlowUserId}"));
            assertFalse(statement.contains("FOR UPDATE"));
            assertFalse(statement.startsWith("UPDATE"));
        }
        assertTrue(counters.contains("s.status = 'ACTIVE'"));
        assertTrue(counters.contains("s.slot_released = 0"));
        assertTrue(activePairs.contains("GROUP BY s.grant_id, s.flow_user_id"));
        assertTrue(activePairs.contains("LEFT JOIN embed_session_counter"));
    }

    @Test
    void exchangeAndTerminationAdaptersDeclareRollbackTransactions() throws Exception {
        Method exchange = MyBatisEmbedSessionExchangeAdapter.class.getMethod(
                "exchange", EmbedSessionExchangePlan.class);
        Method terminate = MyBatisEmbedSessionPersistenceAdapter.class.getMethod(
                "terminate", String.class, String.class, String.class, java.time.Instant.class);

        assertTrue(exchange.isAnnotationPresent(Transactional.class));
        assertTrue(terminate.isAnnotationPresent(Transactional.class));
    }

    @Test
    void unifiedTerminationUseCasesAlwaysOpenIndependentSmallTransactions() throws Exception {
        for (Method method : new Method[]{
                EmbedSessionTerminationService.class.getMethod(
                        "terminateByTokenDigest", String.class, String.class, String.class,
                        java.time.Instant.class, Surface.class, EmbedAuditCorrelation.class),
                EmbedSessionTerminationService.class.getMethod(
                        "terminateById", String.class, String.class, String.class,
                        java.time.Instant.class, Surface.class, Operator.class,
                        EmbedAuditCorrelation.class)}) {
            Transactional transaction = method.getAnnotation(Transactional.class);
            assertTrue(transaction != null);
            assertTrue(transaction.propagation() == Propagation.REQUIRES_NEW);
        }
    }

    @Test
    void everyMaintenanceAdapterMethodUsesRequiresNewTransaction() {
        for (Method method : MyBatisEmbedMaintenanceAdapter.class.getMethods()) {
            if (method.getDeclaringClass() != MyBatisEmbedMaintenanceAdapter.class) {
                continue;
            }
            Transactional transaction = method.getAnnotation(Transactional.class);
            assertTrue(transaction != null, method.getName());
            assertTrue(transaction.propagation() == Propagation.REQUIRES_NEW,
                    method.getName());
        }
    }

    @Test
    void trafficSqlKeepsRateAndLeaseScopeCrossPodAtomic() {
        String applicationLock = sql(findByName(
                EmbedTrafficControlMapper.class, "lockApplication")
                .getAnnotation(Select.class).value());
        String lock = sql(findByName(
                EmbedTrafficControlMapper.class, "lockGrant")
                .getAnnotation(Select.class).value());
        String increment = sql(findByName(
                EmbedTrafficControlMapper.class, "incrementRateBucket")
                .getAnnotation(Insert.class).value());
        String active = sql(findByName(
                EmbedTrafficControlMapper.class, "countActiveRuntimeLeases")
                .getAnnotation(Select.class).value());
        String cleanup = sql(findByName(
                EmbedTrafficControlMapper.class, "deleteExpiredRuntimeLeases")
                .getAnnotation(Delete.class).value());
        String insert = sql(findByName(
                EmbedTrafficControlMapper.class, "insertRuntimeLease")
                .getAnnotation(Insert.class).value());
        String release = sql(findByName(
                EmbedTrafficControlMapper.class, "releaseRuntimeLease")
                .getAnnotation(Delete.class).value());

        assertTrue(applicationLock.contains("FOR UPDATE"));
        assertTrue(lock.contains("application_id = #{applicationId}"));
        assertTrue(lock.contains("FOR UPDATE"));
        assertTrue(increment.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(active.contains("application_id = #{applicationId}"));
        assertTrue(active.contains("scope_key = #{scopeKey}"));
        assertTrue(cleanup.contains("scope_key = #{scopeKey}"));
        assertTrue(insert.contains("application_id, scope_key"));
        assertTrue(release.contains("scope_key <> ''"));
        assertFalse(release.contains("&lt;"));
    }

    @Test
    void trafficAcquisitionAndReleaseUseIndependentTransactions() throws Exception {
        for (Method method : new Method[]{
                MyBatisEmbedTrafficControlAdapter.class.getMethod(
                        "consumeLaunch", String.class, String.class),
                MyBatisEmbedTrafficControlAdapter.class.getMethod(
                        "consumeExchange", String.class, String.class),
                MyBatisEmbedTrafficControlAdapter.class.getMethod(
                        "acquireRuntime", String.class, String.class, String.class,
                        EmbedTrafficControlPort.RuntimeRequestClass.class),
                MyBatisEmbedTrafficControlAdapter.class.getMethod(
                        "releaseRuntime", EmbedTrafficControlPort.RuntimeLease.class)}) {
            Transactional transaction = method.getAnnotation(Transactional.class);
            assertTrue(transaction != null, method.getName() + " must be transactional");
            assertTrue(transaction.propagation() == Propagation.REQUIRES_NEW,
                    method.getName() + " must not join a business transaction");
            if (!"releaseRuntime".equals(method.getName())) {
                assertTrue(transaction.noRollbackFor().length == 1,
                        method.getName() + " must retain rejected-attempt rate accounting");
            }
        }
    }

    private static String updateSql(
            Class<?> type,
            String method,
            Class<?>... parameterTypes) throws Exception {
        return sql(type.getMethod(method, parameterTypes).getAnnotation(Update.class).value());
    }

    private static String updateSqlByName(Class<?> type, String method) {
        return sql(findByName(type, method).getAnnotation(Update.class).value());
    }

    private static String deleteSqlByName(Class<?> type, String method) {
        return sql(findByName(type, method).getAnnotation(Delete.class).value());
    }

    private static String selectSqlByName(Class<?> type, String method) {
        return sql(findByName(type, method).getAnnotation(Select.class).value());
    }

    private static String insertSql(
            Class<?> type,
            String method,
            Class<?>... parameterTypes) throws Exception {
        return sql(type.getMethod(method, parameterTypes).getAnnotation(Insert.class).value());
    }

    private static Method findByName(Class<?> type, String name) {
        return java.util.Arrays.stream(type.getMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static String sql(String[] fragments) {
        return String.join(" ", fragments).replaceAll("\\s+", " ").trim();
    }
}
