package com.workflow.openapi.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.openapi.api.request.CreateIntegrationApplicationRequest;
import com.workflow.openapi.api.request.RotateIntegrationCredentialRequest;
import com.workflow.openapi.api.response.IssuedIntegrationCredentialView;
import com.workflow.openapi.application.IntegrationApplicationService;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class OpenIntegrationTwoNodeEndToEndTest {

    @Container
    private static final MySQLContainer<?> MYSQL =
            new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName("workflow")
                    .withUsername("workflow_two_node")
                    .withPassword("workflow_two_node_password");

    private final HttpClient httpClient =
            HttpClient.newHttpClient();
    private final ObjectMapper objectMapper =
            new ObjectMapper().findAndRegisterModules();

    @Test
    void twoIndependentNodesShareAuthenticationAndRevocationState()
            throws Exception {
        Path privateKey = Files.createTempFile(
                "flow-two-node-private-", ".pem");
        Path publicKey = Files.createTempFile(
                "flow-two-node-public-", ".pem");
        writeKeyPair(privateKey, publicKey);

        try (ConfigurableApplicationContext first =
                        startNode(privateKey, publicKey);
                ConfigurableApplicationContext second =
                        startNode(privateKey, publicKey)) {
            int firstPort = port(first);
            int secondPort = port(second);
            IntegrationApplicationService firstService =
                    first.getBean(
                            IntegrationApplicationService.class);
            IntegrationApplicationService secondService =
                    second.getBean(
                            IntegrationApplicationService.class);

            IssuedIntegrationCredentialView issued =
                    firstService.create(
                            new CreateIntegrationApplicationRequest(
                                    "Two node consumer",
                                    null,
                                    "organization-two-node",
                                    Set.of(
                                            "process.instance.start",
                                            "process.instance.read"),
                                    Set.of("expense-approval"),
                                    60,
                                    10,
                                    List.of("127.0.0.1/32"),
                                    null));

            String tokenFromFirst = issueToken(
                    firstPort,
                    issued.application().clientId(),
                    issued.clientSecret(),
                    200);
            assertEquals(
                    200,
                    callProbe(secondPort, tokenFromFirst));

            String tokenFromSecond = issueToken(
                    secondPort,
                    issued.application().clientId(),
                    issued.clientSecret(),
                    200);
            assertEquals(
                    200,
                    callProbe(firstPort, tokenFromSecond));

            IssuedIntegrationCredentialView rotated =
                    secondService.rotateCredential(
                            issued.application().id(),
                            new RotateIntegrationCredentialRequest(
                                    null,
                                    0L));
            assertFalse(rotated.clientSecret()
                    .equals(issued.clientSecret()));

            issueToken(
                    firstPort,
                    issued.application().clientId(),
                    issued.clientSecret(),
                    401);
            issueToken(
                    secondPort,
                    issued.application().clientId(),
                    issued.clientSecret(),
                    401);
            issueToken(
                    firstPort,
                    issued.application().clientId(),
                    rotated.clientSecret(),
                    200);
            issueToken(
                    secondPort,
                    issued.application().clientId(),
                    rotated.clientSecret(),
                    200);
        } finally {
            Files.deleteIfExists(privateKey);
            Files.deleteIfExists(publicKey);
        }
    }

    private ConfigurableApplicationContext startNode(
            Path privateKey,
            Path publicKey) {
        return new SpringApplicationBuilder(
                OpenIntegrationDatabaseEndToEndTest
                        .TestApplication.class)
                .web(WebApplicationType.SERVLET)
                .properties(
                        "server.port=0",
                        "spring.datasource.url="
                                + MYSQL.getJdbcUrl(),
                        "spring.datasource.username="
                                + MYSQL.getUsername(),
                        "spring.datasource.password="
                                + MYSQL.getPassword(),
                        "spring.flyway.locations=classpath:db/migration",
                        "mybatis-plus.configuration."
                                + "map-underscore-to-camel-case=true",
                        "workflow.open-api.enabled=true",
                        "workflow.open-api.issuer="
                                + "https://flow.two-node.test",
                        "workflow.open-api.audience=flow-open-api",
                        "workflow.open-api.access-token-ttl=10m",
                        "workflow.open-api.key-id=two-node-key",
                        "workflow.open-api.private-key-location="
                                + privateKey.toUri(),
                        "workflow.open-api.public-key-location="
                                + publicKey.toUri(),
                        "workflow.open-api."
                                + "token-client-limit-per-minute=30",
                        "workflow.open-api."
                                + "token-address-limit-per-minute=300")
                .run();
    }

    private int port(ConfigurableApplicationContext context) {
        return ((WebServerApplicationContext) context)
                .getWebServer()
                .getPort();
    }

    private String issueToken(
            int port,
            String clientId,
            String clientSecret,
            int expectedStatus) throws Exception {
        String basic = Base64.getEncoder().encodeToString(
                (clientId + ":" + clientSecret)
                        .getBytes(StandardCharsets.ISO_8859_1));
        String form = "grant_type=client_credentials&scope="
                + URLEncoder.encode(
                        "process.instance.start "
                                + "process.instance.read",
                        StandardCharsets.UTF_8);
        HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder(
                                URI.create(
                                        "http://127.0.0.1:"
                                                + port
                                                + "/oauth2/token"))
                        .header(
                                "Authorization",
                                "Basic " + basic)
                        .header(
                                "Content-Type",
                                "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers
                                .ofString(form))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(expectedStatus, response.statusCode());
        return expectedStatus == 200
                ? objectMapper.readTree(response.body())
                .path("access_token")
                .asText()
                : "";
    }

    private int callProbe(int port, String token)
            throws Exception {
        return httpClient.send(
                        HttpRequest.newBuilder(
                                        URI.create(
                                                "http://127.0.0.1:"
                                                        + port
                                                        + "/api/open/e2e-probe"))
                                .header(
                                        "Authorization",
                                        "Bearer " + token)
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.discarding())
                .statusCode();
    }

    private void writeKeyPair(
            Path privateKey,
            Path publicKey) throws Exception {
        KeyPairGenerator generator =
                KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        Files.writeString(
                privateKey,
                pem(
                        "PRIVATE KEY",
                        ((RSAPrivateKey) pair.getPrivate())
                                .getEncoded()),
                StandardCharsets.US_ASCII);
        Files.writeString(
                publicKey,
                pem(
                        "PUBLIC KEY",
                        ((RSAPublicKey) pair.getPublic())
                                .getEncoded()),
                StandardCharsets.US_ASCII);
    }

    private String pem(String type, byte[] value) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(
                                64,
                                new byte[]{'\n'})
                .encodeToString(value)
                + "\n-----END " + type + "-----\n";
    }
}
