package com.workflow.embed.infrastructure.persistence.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertTrue(sql.contains("ui_form_presentation"));
        assertTrue(sql.contains("#{uiFormPresentation}"));
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
        assertTrue(insert.contains("ui_form_presentation"));
        assertTrue(insert.contains("#{plan.candidate.launch.uiFormPresentation}"));
        assertFalse(insert.contains("access_token"));
        assertTrue(insert.contains("slot_released"));
    }

    @Test
    void runtimeBootstrapReadsFormPresentationFromThePinnedSession() {
        String sql = selectSqlByName(EmbedRuntimeReleaseMapper.class, "find");

        assertTrue(sql.contains("s.ui_form_presentation"));
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
        var bound = boundSelect(EmbedSessionPersistenceMapper.class, "findExpiredTokenDigests",
                java.util.Map.of("now", LocalDateTime.of(2026, 1, 1, 0, 0), "limit", 37));
        String scan = bound.getSql().replaceAll("\\s+", " ").trim();

        assertTrue(scan.contains("status = 'ACTIVE'"));
        assertTrue(scan.contains("slot_released = 0"));
        assertTrue(scan.contains("idle_expires_at <= ?"));
        assertTrue(scan.contains("absolute_expires_at <= ?"));
        assertTrue(scan.endsWith("LIMIT ?"));
        assertEquals(java.util.List.of("now", "now"), bound.getParameterMappings().subList(0, 2).stream()
                .map(org.apache.ibatis.mapping.ParameterMapping::getProperty).toList());
        assertEquals(3, bound.getParameterMappings().size());
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
        String replay = maintenanceSql(EmbedMaintenanceMapper.class,
                "deleteExpiredAssertionReplays");
        String launchExpiry = maintenanceSql(EmbedMaintenanceMapper.class,
                "expireIssuedLaunches");
        String erase = maintenanceSql(EmbedMaintenanceMapper.class,
                "eraseTerminalSessionContexts");
        String receipts = maintenanceSql(EmbedMaintenanceMapper.class,
                "deleteOrphanOperationReceipts");
        String sessions = maintenanceSql(EmbedMaintenanceMapper.class,
                "deleteTerminalSessions");
        String launches = maintenanceSql(EmbedMaintenanceMapper.class,
                "deleteUnreferencedTerminalLaunches");

        for (String statement : new String[]{
                replay, launchExpiry, erase, receipts, sessions, launches}) {
            assertTrue(statement.contains("LIMIT ?"));
        }
        assertTrue(replay.contains("expires_at <= ?"));
        assertTrue(launchExpiry.contains("status = 'ISSUED'"));
        assertTrue(launchExpiry.contains("expires_at <= ?"));
        assertTrue(erase.contains("context_ciphertext = '{}'"));
        assertTrue(erase.contains("context_cipher_key_version = 'embed-erased-v1'"));
        assertTrue(erase.contains("context_digest = '0000000000000000000000000000000000000000000000000000000000000000'"));
        assertTrue(erase.contains("slot_released = 1"));
        assertTrue(receipts.contains("NOT EXISTS"));
        assertTrue(receipts.contains("integration_idempotency_record"));
        assertTrue(sessions.contains("slot_released = 1"));
        assertTrue(launches.contains("NOT EXISTS"));
        assertTrue(launches.contains("update_time <= ?"));
        assertTrue(launches.contains("s.launch_id = embed_launch.id"));
    }

    /** Provider 先生成方言 SQL，再由 MyBatis 绑定值；检查最终 SQL 而非注解源码。 */
    private String maintenanceSql(Class<?> mapper, String method) {
        var bound = boundSelect(mapper, method, java.util.Map.of(
                "now", java.time.LocalDateTime.of(2026, 9, 22, 0, 0),
                "cutoff", java.time.LocalDateTime.of(2026, 9, 1, 0, 0), "limit", 37));
        assertEquals("limit", bound.getParameterMappings().get(bound.getParameterMappings().size() - 1).getProperty());
        return bound.getSql().replaceAll("\\s+", " ").trim();
    }

    @Test
    void counterReconciliationIsBoundedKeysetReadOnlyAndNeverRepairsRows() {
        var parameters = java.util.Map.of("afterGrantId", "grant-1", "afterFlowUserId", "user-1", "limit", 37);
        var counterBound = boundSelect(EmbedMaintenanceMapper.class, "selectStoredCounterPage", parameters);
        var activeBound = boundSelect(EmbedMaintenanceMapper.class, "inspectActiveSessionPairPage", parameters);
        String counters = counterBound.getSql().replaceAll("\\s+", " ").trim();
        String activePairs = activeBound.getSql().replaceAll("\\s+", " ").trim();
        assertEquals(java.util.List.of("afterGrantId", "afterGrantId", "afterFlowUserId"),
                counterBound.getParameterMappings().subList(0, 3).stream().map(org.apache.ibatis.mapping.ParameterMapping::getProperty).toList());
        assertEquals(4, counterBound.getParameterMappings().size());
        assertTrue(counters.endsWith("LIMIT ?"));
        assertEquals(java.util.List.of("afterGrantId", "afterGrantId", "afterFlowUserId", "limit"),
                activeBound.getParameterMappings().stream().map(org.apache.ibatis.mapping.ParameterMapping::getProperty).toList());
        assertTrue(com.baomidou.mybatisplus.core.metadata.IPage.class.isAssignableFrom(
                findByName(EmbedMaintenanceMapper.class, "selectStoredCounterPage").getParameterTypes()[0]));
        assertTrue(activePairs.contains("LIMIT 0, ?"));
        for (String statement : new String[]{counters, activePairs}) {
            assertFalse(statement.contains("FOR UPDATE"));
            assertFalse(statement.startsWith("UPDATE"));
        }
        assertTrue(counters.contains("s.status = 'ACTIVE'"));
        assertTrue(counters.contains("s.slot_released = 0"));
        assertTrue(activePairs.contains("GROUP BY s.grant_id, s.flow_user_id"));
        assertTrue(activePairs.contains("LEFT JOIN embed_session_counter"));
    }

    /** 检查 MyBatis 渲染后的语句与绑定参数，避免把源码中的 XML/方言表达式误当 SQL。 */
    private org.apache.ibatis.mapping.BoundSql boundSelect(Class<?> mapper, String method, java.util.Map<String, ?> parameters) {
        var configuration = new org.apache.ibatis.session.Configuration();
        configuration.setDatabaseId("MYSQL");
        configuration.addMapper(mapper);
        String statementId = mapper.getName() + "." + method;
        if (!configuration.hasStatement(statementId)) {
            statementId += "Page";
        }
        var statement = configuration.getMappedStatement(statementId);
        var arguments = new java.util.HashMap<String, Object>(parameters);
        boolean frameworkPage = java.util.Arrays.stream(findByName(mapper,
                        statementId.substring(statementId.lastIndexOf('.') + 1)).getParameterTypes())
                .anyMatch(com.baomidou.mybatisplus.core.metadata.IPage.class::isAssignableFrom);
        if (frameworkPage) {
            Object limit = parameters.get("limit");
            arguments.put("page", new com.workflow.core.database.mybatis.OffsetPage<>(0,
                    limit == null ? 1 : ((Number) limit).longValue()));
        }
        var bound = statement.getBoundSql(arguments);
        if (frameworkPage) {
            // 执行与运行时相同的分页拦截器，验证数量限制实际进入 SQL。
            new com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor(
                    com.baomidou.mybatisplus.annotation.DbType.MYSQL)
                    .beforeQuery(null, statement, arguments, org.apache.ibatis.session.RowBounds.DEFAULT, null, bound);
        }
        return bound;
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
                .getAnnotation(Update.class).value());
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
        assertTrue(increment.contains("request_count = request_count + 1"));
        assertTrue(increment.contains("bucket_key = #{bucketKey} AND window_epoch = #{windowEpoch}"));
        assertTrue(active.contains("application_id = #{applicationId}"));
        assertTrue(active.contains("scope_key = #{scopeKey}"));
        assertTrue(cleanup.contains("scope_key = #{scopeKey}"));
        assertTrue(insert.contains("application_id, scope_key"));
        assertTrue(release.contains("scope_key LIKE '" + EmbedTrafficControlMapper.RUNTIME_SCOPE_PREFIX + "%'"));
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
                .filter(method -> method.getName().equals(name) || method.getName().equals(name + "Page"))
                .filter(method -> !method.isDefault())
                .findFirst()
                .orElseThrow();
    }

    private static String sql(String[] fragments) {
        return String.join(" ", fragments).replaceAll("\\s+", " ").trim();
    }
}
