package com.workflow.openapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.openapi.api.request.CreateIntegrationApplicationRequest;
import com.workflow.openapi.api.request.RevokeIntegrationCredentialRequest;
import com.workflow.openapi.api.request.RotateIntegrationCredentialRequest;
import com.workflow.openapi.api.request.UpdateIntegrationStatusRequest;
import com.workflow.openapi.api.response.IssuedIntegrationCredentialView;
import com.workflow.openapi.application.IntegrationApplicationService;
import com.workflow.openapi.application.IntegrationSecretGenerator;
import com.workflow.openapi.application.IntegrationSecretHasher;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Set;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
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
                "spring.flyway.locations=classpath:db/migration",
                "mybatis-plus.configuration.map-underscore-to-camel-case=true"
        })
@AutoConfigureMockMvc
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
    @MapperScan(
            "com.workflow.openapi.infrastructure.persistence.mapper")
    @Import({
            IntegrationApplicationService.class,
            IntegrationSecretGenerator.class,
            IntegrationSecretHasher.class,
            IntegrationRegisteredClientRepository.class,
            IntegrationClientNetworkPolicy.class,
            IntegrationRateLimitService.class,
            IntegrationCredentialUsageService.class,
            OpenIntegrationClientAddressResolver.class,
            OpenIntegrationSecurityConfiguration.class,
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
    }

    @RestController
    static class TestProbeController {

        @GetMapping("/api/open/e2e-probe")
        java.util.Map<String, String> probe(
                @AuthenticationPrincipal Jwt jwt) {
            return java.util.Map.of("subject", jwt.getSubject());
        }
    }
}
