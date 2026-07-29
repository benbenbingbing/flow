package com.workflow.openapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.contracts.process.open.OpenProcessCatalogPort;
import com.workflow.contracts.process.open.OpenProcessRuntimePort;
import com.workflow.contracts.process.open.OpenProcessEventPort;
import com.workflow.contracts.process.open.OpenProcessView;
import com.workflow.openapi.api.error.OpenApiExceptionHandler;
import com.workflow.openapi.api.request.IntegrationProcessContractRequest;
import com.workflow.openapi.api.request.CreateIntegrationApplicationRequest;
import com.workflow.openapi.api.request.RevokeIntegrationCredentialRequest;
import com.workflow.openapi.api.request.RotateIntegrationCredentialRequest;
import com.workflow.openapi.api.request.UpdateIntegrationStatusRequest;
import com.workflow.openapi.api.request.UpdateIntegrationProcessContractsRequest;
import com.workflow.openapi.api.response.IssuedIntegrationCredentialView;
import com.workflow.openapi.api.error.OpenApiException;
import com.workflow.openapi.api.web.OpenProcessController;
import com.workflow.openapi.application.IntegrationApplicationService;
import com.workflow.openapi.application.IntegrationSecretGenerator;
import com.workflow.openapi.application.IntegrationSecretHasher;
import com.workflow.openapi.application.IntegrationVariableSchemaService;
import com.workflow.openapi.application.OpenIdempotencyService;
import com.workflow.openapi.application.OpenCursorCodec;
import com.workflow.openapi.application.OpenProcessService;
import com.workflow.openapi.webhook.infrastructure.persistence.mapper.WebhookDeliveryMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        classes = OpenIntegrationDatabaseEndToEndTest.TestApplication.class,
        properties = {
                "workflow.open-api.enabled=true",
                "workflow.open-api.issuer=https://flow.e2e.test",
                "workflow.open-api.audience=flow-open-api",
                "workflow.open-api.access-token-ttl=10m",
                "workflow.open-api.key-id=database-e2e-key",
                "workflow.open-api.token-client-limit-per-minute=30",
                "workflow.open-api.token-address-limit-per-minute=300",
                "flowable.async-executor-activate=false",
                "spring.flyway.locations=classpath:db/migration",
                "mybatis-plus.configuration.map-underscore-to-camel-case=true"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OpenIntegrationDatabaseEndToEndTest {

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("workflow")
                    .withUsername("workflow_e2e")
                    .withPassword("workflow_e2e_password");

    private static Path privateKeyFile;
    private static Path publicKeyFile;

    @Autowired
    private IntegrationApplicationService applicationService;

    @Autowired
    private IntegrationSecretHasher secretHasher;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OpenIdempotencyService idempotencyService;

    @Autowired
    private OpenApiConcurrencyLeaseService concurrencyLeaseService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OpenProcessRuntimePort processRuntimePort;

    @Autowired
    private WebhookDeliveryMapper webhookDeliveryMapper;

    @BeforeAll
    static void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        privateKeyFile = Files.createTempFile(
                "flow-open-api-db-private-", ".pem");
        publicKeyFile = Files.createTempFile(
                "flow-open-api-db-public-", ".pem");
        Files.writeString(
                privateKeyFile,
                pem(
                        "PRIVATE KEY",
                        ((RSAPrivateKey) pair.getPrivate()).getEncoded()),
                StandardCharsets.US_ASCII);
        Files.writeString(
                publicKeyFile,
                pem(
                        "PUBLIC KEY",
                        ((RSAPublicKey) pair.getPublic()).getEncoded()),
                StandardCharsets.US_ASCII);
        privateKeyFile.toFile().deleteOnExit();
        publicKeyFile.toFile().deleteOnExit();
    }

    @DynamicPropertySource
    static void runtimeProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add(
                "workflow.open-api.private-key-location",
                () -> privateKeyFile.toUri().toString());
        registry.add(
                "workflow.open-api.public-key-location",
                () -> publicKeyFile.toUri().toString());
    }

    @Test
    void createRotateDisableAndRevokeAreEnforcedByDatabaseBackedOauth()
            throws Exception {
        IssuedIntegrationCredentialView issued =
                applicationService.create(
                        new CreateIntegrationApplicationRequest(
                                "Database E2E consumer",
                                "Validates persisted OAuth credentials",
                                "organization-e2e",
                                Set.of(
                                        "process.instance.start",
                                        "process.instance.read"),
                                Set.of("expense-approval"),
                                60,
                                10,
                                List.of("127.0.0.1/32", "::1/128"),
                                null));
        String applicationId = issued.application().id();
        String clientId = issued.application().clientId();
        String firstSecret = issued.clientSecret();

        String persistedHash = jdbcTemplate.queryForObject(
                """
                        SELECT secret_hash
                          FROM integration_application_credential
                         WHERE application_id = ?
                           AND status = 'ACTIVE'
                        """,
                String.class,
                applicationId);
        assertNotEquals(firstSecret, persistedHash);
        assertTrue(persistedHash.startsWith("{argon2}"));
        assertTrue(secretHasher.matches(firstSecret, persistedHash));
        assertEquals(
                0,
                countSecretOccurrences(firstSecret));

        String firstToken = issueToken(clientId, firstSecret);
        assertNotNull(jdbcTemplate.queryForObject(
                """
                        SELECT last_used_at
                          FROM integration_application_credential
                         WHERE application_id = ?
                           AND status = 'ACTIVE'
                        """,
                java.time.LocalDateTime.class,
                applicationId));
        assertNotNull(applicationService.list().stream()
                .filter(application ->
                        application.id().equals(applicationId))
                .findFirst()
                .orElseThrow()
                .activeCredentialLastUsedAt());
        mockMvc.perform(get("/api/open/e2e-probe")
                        .header("Authorization", "Bearer " + firstToken))
                .andExpect(status().isOk());

        IssuedIntegrationCredentialView rotated =
                applicationService.rotateCredential(
                        applicationId,
                        new RotateIntegrationCredentialRequest(
                                null,
                                0L));
        String secondSecret = rotated.clientSecret();
        assertNotEquals(firstSecret, secondSecret);
        assertTokenRejected(clientId, firstSecret);
        issueToken(clientId, secondSecret);
        assertCredentialCounts(applicationId, 1, 1);

        applicationService.revokeCredential(
                applicationId,
                new RevokeIntegrationCredentialRequest(1L));
        assertTokenRejected(clientId, secondSecret);
        assertCredentialCounts(applicationId, 0, 2);

        IssuedIntegrationCredentialView recovered =
                applicationService.rotateCredential(
                        applicationId,
                        new RotateIntegrationCredentialRequest(
                                null,
                                2L));
        String thirdSecret = recovered.clientSecret();
        issueToken(clientId, thirdSecret);
        assertCredentialCounts(applicationId, 1, 2);

        applicationService.updateStatus(
                applicationId,
                new UpdateIntegrationStatusRequest("DISABLED", 3L));
        assertTokenRejected(clientId, thirdSecret);

        applicationService.updateStatus(
                applicationId,
                new UpdateIntegrationStatusRequest("REVOKED", 4L));
        assertTokenRejected(clientId, thirdSecret);
        assertCredentialCounts(applicationId, 0, 3);
    }

    @Test
    void expiredWebhookWorkerIsFencedAfterAnotherNodeTakesOver() {
        String applicationId = createApplication(
                "Webhook fencing",
                2).application().id();
        String suffix = UUID.randomUUID().toString();
        String endpointId = "endpoint-" + suffix;
        String subscriptionId = "subscription-" + suffix;
        String eventId = "event-" + suffix;
        String deliveryId = "delivery-" + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO webhook_endpoint (
                  id, application_id, endpoint_name, endpoint_url,
                  endpoint_hash, status, secret_ciphertext,
                  secret_version, secret_hint, version,
                  created_by, updated_by
                ) VALUES (?, ?, 'E2E', 'https://hooks.example.com/flow',
                  ?, 'ACTIVE', 'ciphertext', 1, 'hint0001', 0,
                  'e2e', 'e2e')
                """,
                endpointId,
                applicationId,
                "a".repeat(64));
        jdbcTemplate.update(
                """
                INSERT INTO webhook_subscription (
                  id, application_id, endpoint_id, event_type,
                  status, created_by, updated_by
                ) VALUES (?, ?, ?, 'com.flow.process.started.v1',
                  'ACTIVE', 'e2e', 'e2e')
                """,
                subscriptionId,
                applicationId,
                endpointId);
        jdbcTemplate.update(
                """
                INSERT INTO webhook_event (
                  event_id, source_event_key, application_id,
                  event_type, subject, process_instance_id,
                  trace_id, payload_document, occurred_at, expires_at
                ) VALUES (?, ?, ?,
                  'com.flow.process.started.v1',
                  'process-instance/process-e2e', 'process-e2e',
                  'trace-e2e', '{}', UTC_TIMESTAMP(6),
                  TIMESTAMPADD(DAY, 30, UTC_TIMESTAMP(6)))
                """,
                eventId,
                "source-" + suffix,
                applicationId);
        webhookDeliveryMapper.insert(
                deliveryId,
                applicationId,
                subscriptionId,
                eventId,
                0,
                8,
                "ciphertext",
                1,
                "e2e",
                java.time.LocalDateTime.now(
                        java.time.ZoneOffset.UTC)
                        .minusSeconds(1));

        assertTrue(webhookDeliveryMapper.findReadyIds(100)
                .contains(deliveryId));
        assertEquals(
                1,
                webhookDeliveryMapper.claim(
                        deliveryId,
                        "worker-a",
                        30));
        long firstToken = webhookDeliveryMapper.selectClaimed(
                deliveryId,
                "worker-a").leaseToken();
        jdbcTemplate.update(
                """
                UPDATE webhook_delivery
                   SET lease_until = TIMESTAMPADD(
                     SECOND, -1, UTC_TIMESTAMP(6))
                 WHERE id = ?
                """,
                deliveryId);

        assertTrue(
                webhookDeliveryMapper.recoverExpiredLeases() >= 1);
        assertEquals(
                1,
                webhookDeliveryMapper.claim(
                        deliveryId,
                        "worker-b",
                        30));
        long secondToken = webhookDeliveryMapper.selectClaimed(
                deliveryId,
                "worker-b").leaseToken();
        assertTrue(secondToken > firstToken);
        assertEquals(
                0,
                webhookDeliveryMapper.markSucceeded(
                        deliveryId,
                        "worker-a",
                        firstToken,
                        1,
                        204,
                        null));
        assertEquals(
                1,
                webhookDeliveryMapper.markSucceeded(
                        deliveryId,
                        "worker-b",
                        secondToken,
                        1,
                        204,
                        null));
        String expiredDeliveryId = "expired-" + suffix;
        webhookDeliveryMapper.insert(
                expiredDeliveryId,
                applicationId,
                subscriptionId,
                eventId,
                1,
                8,
                "ciphertext",
                1,
                "e2e",
                java.time.LocalDateTime.now(
                        java.time.ZoneOffset.UTC)
                        .minusSeconds(1));
        jdbcTemplate.update(
                """
                UPDATE webhook_event
                   SET expires_at = TIMESTAMPADD(
                     SECOND, -1, UTC_TIMESTAMP(6))
                 WHERE event_id = ?
                """,
                eventId);

        assertFalse(webhookDeliveryMapper.findReadyIds(100)
                .contains(expiredDeliveryId));
        assertTrue(
                webhookDeliveryMapper.expireOutstandingDeliveries(
                        java.time.LocalDateTime.now(
                                java.time.ZoneOffset.UTC),
                        500) >= 1);
        assertEquals(
                "DEAD",
                jdbcTemplate.queryForObject(
                        """
                        SELECT status
                          FROM webhook_delivery
                         WHERE id = ?
                        """,
                        String.class,
                        expiredDeliveryId));
    }

    @Test
    void webhookReadyBatchIncludesDifferentApplicationsBeforeOneBacklog()
            throws Exception {
        String firstApplication = createApplication(
                "Webhook fairness A",
                2).application().id();
        String secondApplication = createApplication(
                "Webhook fairness B",
                2).application().id();
        String firstA = insertReadyWebhook(
                firstApplication,
                UUID.randomUUID().toString());
        String secondA = insertReadyWebhook(
                firstApplication,
                UUID.randomUUID().toString());
        String firstB = insertReadyWebhook(
                secondApplication,
                UUID.randomUUID().toString());

        List<String> ready =
                webhookDeliveryMapper.findReadyIds(2);

        assertEquals(2, ready.size());
        assertTrue(ready.contains(firstB));
        assertEquals(
                1,
                ready.stream()
                        .filter(id -> id.equals(firstA)
                                || id.equals(secondA))
                        .count());
    }

    @Test
    void concurrentNodesClaimOneIdempotencyOwnerAndReplayItsResult()
            throws Exception {
        String applicationId = createApplication(
                "Idempotency",
                10).application().id();
        int workerCount = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor =
                Executors.newFixedThreadPool(workerCount);
        List<Future<OpenIdempotencyService.Claim>> futures =
                new ArrayList<>();
        try {
            for (int index = 0; index < workerCount; index++) {
                futures.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return idempotencyService.claim(
                            applicationId,
                            "PROCESS_START",
                            "shared-request-key",
                            Map.of(
                                    "processKey",
                                    "expense-approval",
                                    "amount",
                                    1200));
                }));
            }
            start.countDown();
            List<OpenIdempotencyService.Claim> claims =
                    awaitClaims(futures);

            assertEquals(
                    1,
                    claims.stream()
                            .filter(OpenIdempotencyService.Claim::acquired)
                            .count());
            assertEquals(
                    workerCount - 1,
                    claims.stream()
                            .filter(OpenIdempotencyService.Claim::processing)
                            .count());
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            """
                                    SELECT COUNT(*)
                                      FROM integration_idempotency_record
                                     WHERE application_id = ?
                                       AND operation = 'PROCESS_START'
                                       AND idempotency_key = ?
                                    """,
                            Integer.class,
                            applicationId,
                            "shared-request-key"));

            OpenIdempotencyService.Claim owner = claims.stream()
                    .filter(OpenIdempotencyService.Claim::acquired)
                    .findFirst()
                    .orElseThrow();
            new TransactionTemplate(transactionManager)
                    .executeWithoutResult(status ->
                            idempotencyService
                                    .completeInBusinessTransaction(
                                            owner,
                                            "PROCESS_INSTANCE",
                                            "process-instance-01",
                                            201,
                                            new ReplayResult(
                                                    "process-instance-01")));

            OpenIdempotencyService.Claim replay =
                    idempotencyService.claim(
                            applicationId,
                            "PROCESS_START",
                            "shared-request-key",
                            Map.of(
                                    "amount",
                                    1200,
                                    "processKey",
                                    "expense-approval"));
            assertTrue(replay.replay());
            assertEquals(
                    "process-instance-01",
                    idempotencyService.readReplay(
                                    replay,
                                    ReplayResult.class)
                            .processInstanceId());

            OpenApiException reused = assertThrows(
                    OpenApiException.class,
                    () -> idempotencyService.claim(
                            applicationId,
                            "PROCESS_START",
                            "shared-request-key",
                            Map.of(
                                    "processKey",
                                    "expense-approval",
                                    "amount",
                                    1201)));
            assertEquals(
                    "IDEMPOTENCY_KEY_REUSED",
                    reused.getErrorCode());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentNodesShareTheDatabaseConcurrencyQuota()
            throws Exception {
        String applicationId = createApplication(
                "Concurrency",
                1).application().id();
        int workerCount = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor =
                Executors.newFixedThreadPool(workerCount);
        List<Future<OpenApiConcurrencyLeaseService.Lease>> futures =
                new ArrayList<>();
        try {
            for (int index = 0; index < workerCount; index++) {
                futures.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        return concurrencyLeaseService.acquire(
                                applicationId,
                                1);
                    } catch (OpenApiConcurrencyLeaseService
                            .ConcurrencyRejectedException exception) {
                        return null;
                    }
                }));
            }
            start.countDown();
            List<OpenApiConcurrencyLeaseService.Lease> acquired =
                    new ArrayList<>();
            for (Future<OpenApiConcurrencyLeaseService.Lease> future
                    : futures) {
                var lease = future.get(10, TimeUnit.SECONDS);
                if (lease != null) {
                    acquired.add(lease);
                }
            }

            assertEquals(1, acquired.size());
            assertEquals(
                    1,
                    jdbcTemplate.queryForObject(
                            """
                                    SELECT COUNT(*)
                                      FROM integration_api_request_lease
                                     WHERE application_id = ?
                                       AND expires_at > UTC_TIMESTAMP(6)
                                    """,
                            Integer.class,
                            applicationId));
            concurrencyLeaseService.release(acquired.get(0));
            assertEquals(
                    0,
                    jdbcTemplate.queryForObject(
                            """
                                    SELECT COUNT(*)
                                      FROM integration_api_request_lease
                                     WHERE application_id = ?
                                    """,
                            Integer.class,
                            applicationId));

            var next = concurrencyLeaseService.acquire(
                    applicationId,
                    1);
            assertNotNull(next.id());
            concurrencyLeaseService.release(next);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void machineClientStartsReplaysReadsAndCannotCrossApplications()
            throws Exception {
        Mockito.reset(processRuntimePort);
        IssuedIntegrationCredentialView first =
                createApplication("Open process HTTP", 10);
        String applicationId = first.application().id();
        applicationService.updateProcessContracts(
                applicationId,
                new UpdateIntegrationProcessContractsRequest(
                        List.of(
                                new IntegrationProcessContractRequest(
                                        "expense-approval",
                                        objectMapper.readTree("""
                                                {
                                                  "type":"object",
                                                  "maxProperties":1,
                                                  "additionalProperties":false,
                                                  "required":["amount"],
                                                  "properties":{
                                                    "amount":{
                                                      "type":"integer",
                                                      "minimum":1,
                                                      "maximum":1000000
                                                    }
                                                  }
                                                }
                                                """),
                                        Set.of())),
                        0L));
        OpenProcessView process = new OpenProcessView(
                "process-instance-0001",
                "expense-approval",
                "RUNNING",
                java.time.Instant.parse(
                        "2026-07-29T08:30:00Z"),
                null);
        Mockito.when(processRuntimePort.start(Mockito.any()))
                .thenReturn(process);
        Mockito.when(processRuntimePort.get(
                        Mockito.eq("process-instance-0001"),
                        Mockito.any()))
                .thenReturn(process);
        String firstToken = issueToken(
                first.application().clientId(),
                first.clientSecret());
        String requestBody = """
                {
                  "processKey":"expense-approval",
                  "businessReference":{
                    "system":"billing-system",
                    "type":"expense",
                    "id":"expense-2026-0001"
                  },
                  "variables":{"amount":1200}
                }
                """;

        mockMvc.perform(post("/api/open/v1/process-instances")
                        .header(
                                "Authorization",
                                "Bearer " + firstToken)
                        .header(
                                "Idempotency-Key",
                                "expense-request-0001")
                        .header(
                                "X-Trace-Id",
                                "trace-process-start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(header().string(
                        "X-Trace-Id",
                        "trace-process-start"))
                .andExpect(jsonPath(
                        "$.data.processInstanceId")
                        .value("process-instance-0001"))
                .andExpect(jsonPath("$.code").value(201));

        mockMvc.perform(post("/api/open/v1/process-instances")
                        .header(
                                "Authorization",
                                "Bearer " + firstToken)
                        .header(
                                "Idempotency-Key",
                                "expense-request-0001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Idempotent-Replay",
                        "true"))
                .andExpect(jsonPath(
                        "$.data.processInstanceId")
                        .value("process-instance-0001"));
        Mockito.verify(
                        processRuntimePort,
                        Mockito.times(1))
                .start(Mockito.any());
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*)
                                  FROM integration_process_binding
                                 WHERE application_id = ?
                                   AND process_instance_id = ?
                                """,
                        Integer.class,
                        applicationId,
                        "process-instance-0001"));
        assertEquals(
                "SUCCEEDED",
                jdbcTemplate.queryForObject(
                        """
                                SELECT status
                                  FROM integration_idempotency_record
                                 WHERE application_id = ?
                                   AND operation = 'PROCESS_START'
                                   AND idempotency_key = ?
                                """,
                        String.class,
                        applicationId,
                        "expense-request-0001"));

        mockMvc.perform(get(
                        "/api/open/v1/process-instances/"
                                + "process-instance-0001")
                        .header(
                                "Authorization",
                                "Bearer " + firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status")
                        .value("RUNNING"));

        IssuedIntegrationCredentialView second =
                createApplication("Isolated application", 10);
        String secondToken = issueToken(
                second.application().clientId(),
                second.clientSecret());
        mockMvc.perform(get(
                        "/api/open/v1/process-instances/"
                                + "process-instance-0001")
                        .header(
                                "Authorization",
                                "Bearer " + secondToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode")
                        .value("RESOURCE_NOT_FOUND"));
        Mockito.verify(
                        processRuntimePort,
                        Mockito.times(1))
                .get(
                        Mockito.eq("process-instance-0001"),
                        Mockito.any());
    }

    private IssuedIntegrationCredentialView createApplication(
            String purpose,
            int maxConcurrency) {
        return applicationService.create(
                new CreateIntegrationApplicationRequest(
                        purpose + " "
                                + UUID.randomUUID(),
                        "Database-backed distributed behavior test",
                        "organization-e2e",
                        Set.of(
                                "process.instance.start",
                                "process.instance.read"),
                        Set.of("expense-approval"),
                        60,
                        maxConcurrency,
                        List.of("127.0.0.1/32", "::1/128"),
                        null));
    }

    private String insertReadyWebhook(
            String applicationId,
            String suffix) {
        String compact = suffix.replace("-", "");
        String endpointId = "endpoint-" + suffix;
        String subscriptionId = "subscription-" + suffix;
        String eventId = "event-" + suffix;
        String deliveryId = "delivery-" + suffix;
        jdbcTemplate.update(
                """
                INSERT INTO webhook_endpoint (
                  id, application_id, endpoint_name, endpoint_url,
                  endpoint_hash, status, secret_ciphertext,
                  secret_version, secret_hint, version,
                  created_by, updated_by
                ) VALUES (?, ?, 'Fairness',
                  'https://hooks.example.com/flow',
                  ?, 'ACTIVE', 'ciphertext', 1, 'hint0001', 0,
                  'e2e', 'e2e')
                """,
                endpointId,
                applicationId,
                compact + compact);
        jdbcTemplate.update(
                """
                INSERT INTO webhook_subscription (
                  id, application_id, endpoint_id, event_type,
                  status, created_by, updated_by
                ) VALUES (?, ?, ?, 'com.flow.process.started.v1',
                  'ACTIVE', 'e2e', 'e2e')
                """,
                subscriptionId,
                applicationId,
                endpointId);
        jdbcTemplate.update(
                """
                INSERT INTO webhook_event (
                  event_id, source_event_key, application_id,
                  event_type, subject, process_instance_id,
                  trace_id, payload_document, occurred_at, expires_at
                ) VALUES (?, ?, ?,
                  'com.flow.process.started.v1',
                  'process-instance/process-e2e', 'process-e2e',
                  'trace-e2e', '{}', UTC_TIMESTAMP(6),
                  TIMESTAMPADD(DAY, 30, UTC_TIMESTAMP(6)))
                """,
                eventId,
                "source-" + suffix,
                applicationId);
        webhookDeliveryMapper.insert(
                deliveryId,
                applicationId,
                subscriptionId,
                eventId,
                0,
                8,
                "ciphertext",
                1,
                "e2e",
                java.time.LocalDateTime.now(
                        java.time.ZoneOffset.UTC)
                        .minusSeconds(1));
        return deliveryId;
    }

    private List<OpenIdempotencyService.Claim> awaitClaims(
            List<Future<OpenIdempotencyService.Claim>> futures)
            throws InterruptedException,
            ExecutionException,
            java.util.concurrent.TimeoutException {
        List<OpenIdempotencyService.Claim> claims =
                new ArrayList<>();
        for (Future<OpenIdempotencyService.Claim> future : futures) {
            claims.add(future.get(10, TimeUnit.SECONDS));
        }
        return claims;
    }

    private String issueToken(String clientId, String clientSecret)
            throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(clientId, clientSecret))
                        .contentType(
                                MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param(
                                "scope",
                                "process.instance.start "
                                        + "process.instance.read"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(
                        result.getResponse().getContentAsByteArray())
                .path("access_token")
                .asText();
    }

    private void assertTokenRejected(
            String clientId,
            String clientSecret) throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(clientId, clientSecret))
                        .contentType(
                                MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized());
    }

    private int countSecretOccurrences(String secret) {
        return jdbcTemplate.queryForObject(
                """
                        SELECT COUNT(*)
                          FROM integration_application_credential
                         WHERE secret_hash = ?
                            OR credential_hint = ?
                        """,
                Integer.class,
                secret,
                secret);
    }

    private void assertCredentialCounts(
            String applicationId,
            int active,
            int revoked) {
        assertEquals(
                active,
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*)
                                  FROM integration_application_credential
                                 WHERE application_id = ?
                                   AND status = 'ACTIVE'
                                """,
                        Integer.class,
                        applicationId));
        assertEquals(
                revoked,
                jdbcTemplate.queryForObject(
                        """
                                SELECT COUNT(*)
                                  FROM integration_application_credential
                                 WHERE application_id = ?
                                   AND status = 'REVOKED'
                                """,
                        Integer.class,
                        applicationId));
    }

    private static String pem(String type, byte[] value) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(value)
                + "\n-----END " + type + "-----\n";
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan({
            "com.workflow.openapi.infrastructure.persistence.mapper",
            "com.workflow.openapi.webhook.infrastructure.persistence.mapper"
    })
    @Import({
            IntegrationApplicationService.class,
            IntegrationSecretGenerator.class,
            IntegrationSecretHasher.class,
            IntegrationVariableSchemaService.class,
            OpenIdempotencyService.class,
            OpenCursorCodec.class,
            OpenProcessService.class,
            IntegrationRegisteredClientRepository.class,
            IntegrationClientNetworkPolicy.class,
            IntegrationRateLimitService.class,
            IntegrationCredentialUsageService.class,
            OpenIntegrationClientAddressResolver.class,
            OpenApiConcurrencyLeaseService.class,
            OpenApplicationActorResolver.class,
            OpenIntegrationSecurityConfiguration.class,
            OpenProcessController.class,
            OpenApiExceptionHandler.class,
            TestBeans.class,
            TestProbeController.class
    })
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        CurrentActorProvider currentActorProvider() {
            return () -> new CurrentActor(
                    "integration-e2e-admin",
                    "Integration E2E Admin");
        }

        @Bean
        SystemAuditPort systemAuditPort() {
            return Mockito.mock(SystemAuditPort.class);
        }

        @Bean
        OpenProcessCatalogPort openProcessCatalogPort() {
            return Mockito.mock(OpenProcessCatalogPort.class);
        }

        @Bean
        OpenProcessRuntimePort openProcessRuntimePort() {
            return Mockito.mock(OpenProcessRuntimePort.class);
        }

        @Bean
        OpenProcessEventPort openProcessEventPort() {
            return Mockito.mock(OpenProcessEventPort.class);
        }
    }

    @RestController
    static class TestProbeController {

        @GetMapping("/api/open/e2e-probe")
        java.util.Map<String, String> probe(
                @AuthenticationPrincipal Jwt jwt) {
            return java.util.Map.of("subject", jwt.getSubject());
        }
    }

    private record ReplayResult(String processInstanceId) {
    }
}
