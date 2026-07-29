package com.workflow.openapi.connector.secret;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.contracts.identity.CurrentActor;
import com.workflow.contracts.identity.CurrentActorProvider;
import com.workflow.http.HttpConnectorConfigurationCodec;
import com.workflow.openapi.api.request.CreateIntegrationSecretRequest;
import com.workflow.openapi.api.request.RevokeIntegrationSecretRequest;
import com.workflow.openapi.api.request.RotateIntegrationSecretRequest;
import com.workflow.openapi.application.IntegrationSecretGenerator;
import com.workflow.openapi.connector.config.DatabaseHttpConnectorConfigurationProvider;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(
        classes = IntegrationSecretDatabaseEndToEndTest.TestApplication.class,
        properties = {
                "spring.flyway.locations=classpath:db/migration",
                "mybatis-plus.configuration.map-underscore-to-camel-case=true",
                "workflow.integration.connector.http.enabled=true",
                "workflow.integration.connector.http.master-key-version=master-v1",
                "workflow.integration.connector.http.master-key="
                        + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        })
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class IntegrationSecretDatabaseEndToEndTest {

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("workflow_connector")
                    .withUsername("workflow_connector")
                    .withPassword("workflow_connector_password");

    @Autowired
    private IntegrationSecretAdministrationService secretService;

    @Autowired
    private DatabaseIntegrationSecretResolver secretResolver;

    @Autowired
    private DatabaseHttpConnectorConfigurationProvider configProvider;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Test
    void secretLifecycleUsesEnvelopeEncryptionAndApplicationIsolation() {
        String firstApplication = insertApplication("secret-a", "ACTIVE");
        String secondApplication = insertApplication("secret-b", "ACTIVE");

        var issued = secretService.create(
                firstApplication,
                new CreateIntegrationSecretRequest(
                        "api-token",
                        "first-sensitive-value"));

        assertEquals(
                "first-sensitive-value",
                secretResolver.resolve(issued.secretReference()));
        String ciphertext = jdbcTemplate.queryForObject(
                """
                SELECT secret_ciphertext
                  FROM integration_secret
                 WHERE id = ?
                """,
                String.class,
                issued.secret().id());
        assertFalse(ciphertext.contains("first-sensitive-value"));
        assertEquals(
                0,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                          FROM integration_secret
                         WHERE secret_ciphertext = ?
                            OR encrypted_data_key = ?
                        """,
                        Integer.class,
                        "first-sensitive-value",
                        "first-sensitive-value"));
        assertThrows(
                IllegalArgumentException.class,
                () -> secretResolver.resolve(
                        "secret://integration/"
                                + secondApplication
                                + "/api-token"));

        var rotated = secretService.rotate(
                firstApplication,
                "api-token",
                new RotateIntegrationSecretRequest(
                        1L,
                        "second-sensitive-value"));
        assertEquals(2L, rotated.secret().secretVersion());
        assertEquals(
                "second-sensitive-value",
                secretResolver.resolve(rotated.secretReference()));
        assertEquals(
                1,
                jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                          FROM integration_secret
                         WHERE application_id = ?
                           AND secret_name = 'api-token'
                           AND status = 'REVOKED'
                        """,
                        Integer.class,
                        firstApplication));

        secretService.revoke(
                firstApplication,
                "api-token",
                new RevokeIntegrationSecretRequest(2L));
        assertThrows(
                IllegalArgumentException.class,
                () -> secretResolver.resolve(rotated.secretReference()));
        var destroyed = secretService.destroy(
                firstApplication,
                issued.secret().id());
        assertEquals("DESTROYED", destroyed.status());
        assertNull(jdbcTemplate.queryForObject(
                """
                SELECT secret_ciphertext
                  FROM integration_secret
                 WHERE id = ?
                """,
                String.class,
                issued.secret().id()));
    }

    @Test
    void connectorProviderFailsClosedWhenConfigOrApplicationIsDisabled() {
        String applicationId = insertApplication(
                "connector-provider",
                "ACTIVE");
        String configId = "config-" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO integration_connector_config (
                  id, application_id, config_name, connector_code,
                  status, configuration_document,
                  allowed_hosts_document, version,
                  created_by, updated_by
                ) VALUES (?, ?, 'ERP', 'http-json', 'ACTIVE',
                  ?, '["erp.example.com"]', 0, 'e2e', 'e2e')
                """,
                configId,
                applicationId,
                """
                {"baseUrl":"https://erp.example.com/api",
                 "operations":{"lookup":{"method":"GET",
                 "path":"/orders",
                 "authentication":{"type":"NONE"}}}}
                """);

        assertEquals(
                applicationId,
                configProvider.findActive(configId).applicationId());
        jdbcTemplate.update(
                """
                UPDATE integration_connector_config
                   SET status = 'DISABLED'
                 WHERE id = ?
                """,
                configId);
        assertThrows(
                IllegalArgumentException.class,
                () -> configProvider.findActive(configId));
        jdbcTemplate.update(
                """
                UPDATE integration_connector_config
                   SET status = 'ACTIVE'
                 WHERE id = ?
                """,
                configId);
        jdbcTemplate.update(
                """
                UPDATE integration_application
                   SET status = 'DISABLED'
                 WHERE id = ?
                """,
                applicationId);
        assertThrows(
                IllegalArgumentException.class,
                () -> configProvider.findActive(configId));
    }

    private String insertApplication(String prefix, String status) {
        String id = prefix + "-" + UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO integration_application (
                  id, client_id, application_name, status,
                  rate_limit_per_minute, max_concurrency,
                  created_by, updated_by
                ) VALUES (?, ?, ?, ?, 60, 10, 'e2e', 'e2e')
                """,
                id,
                "client-" + UUID.randomUUID(),
                prefix,
                status);
        return id;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @MapperScan({
            "com.workflow.openapi.infrastructure.persistence.mapper",
            "com.workflow.openapi.connector.secret",
            "com.workflow.openapi.connector.config"
    })
    @Import({
            IntegrationSecretAdministrationService.class,
            IntegrationSecretCipher.class,
            DatabaseIntegrationSecretResolver.class,
            IntegrationSecretGenerator.class,
            HttpConnectorConfigurationCodec.class,
            DatabaseHttpConnectorConfigurationProvider.class,
            TestBeans.class
    })
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        CurrentActorProvider currentActorProvider() {
            return () -> new CurrentActor(
                    "connector-e2e-admin",
                    "Connector E2E Admin");
        }

        @Bean
        SystemAuditPort systemAuditPort() {
            return Mockito.mock(SystemAuditPort.class);
        }
    }
}
