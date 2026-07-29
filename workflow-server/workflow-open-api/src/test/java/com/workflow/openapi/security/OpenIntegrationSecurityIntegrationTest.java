package com.workflow.openapi.security;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.openapi.application.IntegrationSecretHasher;
import com.workflow.contracts.audit.SystemAuditPort;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.Base64;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest(
        classes = OpenIntegrationSecurityIntegrationTest.TestApplication.class,
        properties = {
                "workflow.open-api.enabled=true",
                "workflow.open-api.issuer=https://flow.test",
                "workflow.open-api.audience=flow-open-api",
                "workflow.open-api.access-token-ttl=10m",
                "workflow.open-api.key-id=integration-test-key",
                "workflow.open-api.token-client-limit-per-minute=30",
                "workflow.open-api.token-address-limit-per-minute=300"
        })
@AutoConfigureMockMvc
class OpenIntegrationSecurityIntegrationTest {

    private static final String CLIENT_ID = "flow_test_client";
    private static final String CLIENT_SECRET =
            "test-client-secret-with-at-least-32-bytes";

    private static Path privateKeyFile;
    private static Path publicKeyFile;
    private static Path previousPublicKeyFile;
    private static RSAPrivateKey previousPrivateKey;

    @org.springframework.beans.factory.annotation.Autowired
    private MockMvc mockMvc;

    @org.springframework.beans.factory.annotation.Autowired
    private ObjectMapper objectMapper;

    @BeforeAll
    static void createKeys() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        privateKeyFile = Files.createTempFile(
                "flow-open-api-private-", ".pem");
        publicKeyFile = Files.createTempFile(
                "flow-open-api-public-", ".pem");
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
        var previousPair = generator.generateKeyPair();
        previousPrivateKey =
                (RSAPrivateKey) previousPair.getPrivate();
        previousPublicKeyFile = Files.createTempFile(
                "flow-open-api-previous-public-", ".pem");
        Files.writeString(
                previousPublicKeyFile,
                pem(
                        "PUBLIC KEY",
                        ((RSAPublicKey) previousPair.getPublic())
                                .getEncoded()),
                StandardCharsets.US_ASCII);
        privateKeyFile.toFile().deleteOnExit();
        publicKeyFile.toFile().deleteOnExit();
        previousPublicKeyFile.toFile().deleteOnExit();
    }

    @DynamicPropertySource
    static void keyProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "workflow.open-api.private-key-location",
                () -> privateKeyFile.toUri().toString());
        registry.add(
                "workflow.open-api.public-key-location",
                () -> publicKeyFile.toUri().toString());
        registry.add(
                "workflow.open-api.previous-public-keys",
                () -> "previous-test-key="
                        + previousPublicKeyFile.toUri());
    }

    @Test
    void clientCredentialsIssuesIsolatedRs256AccessToken()
            throws Exception {
        String token = issueToken(
                "process.instance.start process.instance.read");

        String[] parts = token.split("\\.");
        JsonNode header = objectMapper.readTree(
                Base64.getUrlDecoder().decode(parts[0]));
        JsonNode claims = objectMapper.readTree(
                Base64.getUrlDecoder().decode(parts[1]));
        org.junit.jupiter.api.Assertions.assertEquals(
                "RS256",
                header.get("alg").asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                "integration-test-key",
                header.get("kid").asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                "https://flow.test",
                claims.get("iss").asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                CLIENT_ID,
                claims.get("sub").asText());
        JsonNode audience = claims.get("aud");
        org.junit.jupiter.api.Assertions.assertEquals(
                "flow-open-api",
                audience.isArray()
                        ? audience.get(0).asText()
                        : audience.asText());
        org.junit.jupiter.api.Assertions.assertEquals(
                "app-test-1",
                claims.get("application_id").asText());
        org.junit.jupiter.api.Assertions.assertTrue(claims.hasNonNull("jti"));
        org.junit.jupiter.api.Assertions.assertTrue(claims.hasNonNull("iat"));
        org.junit.jupiter.api.Assertions.assertTrue(claims.hasNonNull("exp"));

        mockMvc.perform(get("/api/open/test-probe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject", is(CLIENT_ID)))
                .andExpect(jsonPath(
                        "$.scopes",
                        containsInAnyOrder(
                                "process.instance.start",
                                "process.instance.read")));
    }

    @Test
    void scopeEscalationAndWrongSecretUseOauthErrors()
            throws Exception {
        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .contentType(
                                MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", "system.admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("invalid_scope")));

        mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, "wrong-secret"))
                        .contentType(
                                MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        "WWW-Authenticate",
                        org.hamcrest.Matchers.containsString(
                                "invalid_client")))
                .andExpect(jsonPath("$.error", is("invalid_client")));
    }

    @Test
    void nonMachineBearerTokenCannotEnterOpenApiDomain()
            throws Exception {
        mockMvc.perform(get("/api/open/test-probe")
                        .header(
                                "Authorization",
                                "Bearer not-a-machine-jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validUserStyleHmacTokenCannotEnterOpenApiDomain()
            throws Exception {
        Instant now = Instant.now();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.HS512)
                        .type(JOSEObjectType.JWT)
                        .build(),
                new JWTClaimsSet.Builder()
                        .issuer("https://flow.test")
                        .subject("user-1")
                        .audience("flow-open-api")
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(
                                now.plusSeconds(300)))
                        .claim("username", "alice")
                        .build());
        jwt.sign(new MACSigner(
                "user-jwt-test-key-with-at-least-sixty-four-bytes-"
                        + "of-entropy-material-123456"));

        mockMvc.perform(get("/api/open/test-probe")
                        .header(
                                "Authorization",
                                "Bearer " + jwt.serialize()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenSignedByPreviousRotationKeyRemainsValid()
            throws Exception {
        Instant now = Instant.now();
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("https://flow.test")
                .subject(CLIENT_ID)
                .audience("flow-open-api")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(
                        now.plusSeconds(300)))
                .jwtID("previous-key-token")
                .claim("application_id", "app-test-1")
                .claim(
                        "scope",
                        Set.of("process.instance.read"))
                .build();
        SignedJWT jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID("previous-test-key")
                        .build(),
                claims);
        jwt.sign(new RSASSASigner(previousPrivateKey));

        mockMvc.perform(get("/api/open/test-probe")
                        .header(
                                "Authorization",
                                "Bearer " + jwt.serialize()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject", is(CLIENT_ID)));
    }

    private String issueToken(String scopes) throws Exception {
        MvcResult result = mockMvc.perform(post("/oauth2/token")
                        .with(httpBasic(CLIENT_ID, CLIENT_SECRET))
                        .contentType(
                                MediaType.APPLICATION_FORM_URLENCODED)
                        .param("grant_type", "client_credentials")
                        .param("scope", scopes))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token_type", is("Bearer")))
                .andExpect(jsonPath("$.expires_in", is(599)))
                .andReturn();
        return objectMapper.readTree(
                        result.getResponse().getContentAsByteArray())
                .get("access_token")
                .asText();
    }

    private static String pem(String type, byte[] value) {
        return "-----BEGIN " + type + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'})
                .encodeToString(value)
                + "\n-----END " + type + "-----\n";
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration(exclude = {
            DataSourceAutoConfiguration.class,
            MybatisPlusAutoConfiguration.class
    })
    @Import({
            OpenIntegrationSecurityConfiguration.class,
            TestBeans.class,
            TestProbeController.class
    })
    static class TestApplication {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        RegisteredClientRepository registeredClientRepository() {
            String secretHash =
                    new IntegrationSecretHasher().hash(CLIENT_SECRET);
            RegisteredClient client = RegisteredClient
                    .withId("app-test-1")
                    .clientId(CLIENT_ID)
                    .clientSecret(secretHash)
                    .clientAuthenticationMethod(
                            ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                    .authorizationGrantType(
                            AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .scopes(scopes -> scopes.addAll(Set.of(
                            "process.instance.start",
                            "process.instance.read")))
                    .tokenSettings(TokenSettings.builder()
                            .accessTokenTimeToLive(
                                    java.time.Duration.ofMinutes(10))
                            .build())
                    .build();
            return new org.springframework.security.oauth2.server
                    .authorization.client
                    .InMemoryRegisteredClientRepository(client);
        }

        @Bean
        IntegrationRateLimitService integrationRateLimitService() {
            return Mockito.mock(IntegrationRateLimitService.class);
        }

        @Bean
        IntegrationCredentialUsageService
                integrationCredentialUsageService() {
            return Mockito.mock(
                    IntegrationCredentialUsageService.class);
        }

        @Bean
        IntegrationClientNetworkPolicy integrationClientNetworkPolicy() {
            IntegrationClientNetworkPolicy policy =
                    Mockito.mock(IntegrationClientNetworkPolicy.class);
            Mockito.when(policy.evaluate(
                            Mockito.anyString(),
                            Mockito.anyString()))
                    .thenReturn(
                            new IntegrationClientNetworkPolicy.Decision(
                                    "app-test-1",
                                    true));
            return policy;
        }

        @Bean
        OpenIntegrationClientAddressResolver clientAddressResolver() {
            OpenIntegrationClientAddressResolver resolver =
                    Mockito.mock(
                            OpenIntegrationClientAddressResolver.class);
            Mockito.when(resolver.resolve(Mockito.any()))
                    .thenReturn("127.0.0.1");
            return resolver;
        }

        @Bean
        IntegrationSecretHasher integrationSecretHasher() {
            return new IntegrationSecretHasher();
        }

        @Bean
        SystemAuditPort systemAuditPort() {
            return Mockito.mock(SystemAuditPort.class);
        }
    }

    @RestController
    static class TestProbeController {

        @GetMapping("/api/open/test-probe")
        java.util.Map<String, Object> probe(
                @org.springframework.security.core.annotation.AuthenticationPrincipal
                org.springframework.security.oauth2.jwt.Jwt jwt) {
            return java.util.Map.of(
                    "subject", jwt.getSubject(),
                    "scopes", jwt.getClaimAsStringList("scope"));
        }
    }
}
