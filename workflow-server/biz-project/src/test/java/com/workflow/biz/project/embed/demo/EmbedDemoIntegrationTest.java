package com.workflow.biz.project.embed.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** 使用真实本地 HTTP 和真实 RSA 签名验证宿主边界，不需要 Flow 数据库或真实凭据。 */
@SpringBootTest(classes = EmbedDemoIntegrationTest.TestApplication.class)
@org.springframework.test.context.ActiveProfiles("partner-embed-demo")
@AutoConfigureMockMvc
class EmbedDemoIntegrationTest {
    @org.springframework.boot.SpringBootConfiguration
    @org.springframework.boot.autoconfigure.EnableAutoConfiguration(exclude = org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class)
    @org.springframework.context.annotation.ComponentScan(basePackageClasses = EmbedDemoConfiguration.class)
    static class TestApplication { }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final AtomicInteger TOKEN_CALLS = new AtomicInteger();
    private static final List<JsonNode> LAUNCH_REQUESTS = new ArrayList<>();
    private static final Queue<Reply> LAUNCH_REPLIES = new ConcurrentLinkedQueue<>();
    private static final Queue<Reply> TOKEN_REPLIES = new ConcurrentLinkedQueue<>();
    private static HttpServer flow;
    private static Path privateKey;
    private static String basicAuthorization;

    @Autowired MockMvc mvc;
    @Autowired FlowMachineTokenService tokens;
    @Autowired PartnerAssertionService assertions;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        privateKey = Files.createTempFile("partner-embed-test-", ".pem");
        Files.writeString(privateKey, "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n");
        flow = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        flow.createContext("/oauth2/token", exchange -> {
            TOKEN_CALLS.incrementAndGet();
            basicAuthorization = exchange.getRequestHeaders().getFirst("Authorization");
            assertThat(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8))
                    .isEqualTo("grant_type=client_credentials");
            Reply reply = TOKEN_REPLIES.poll();
            if (reply == null) {
                reply = new Reply(200, "{\"access_token\":\"machine-token\",\"token_type\":\"Bearer\",\"expires_in\":300}");
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        flow.createContext("/api/open/v1/embed-launches", exchange -> {
            assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer machine-token");
            LAUNCH_REQUESTS.add(JSON.readTree(exchange.getRequestBody()));
            Reply reply = LAUNCH_REPLIES.poll();
            if (reply == null) reply = new Reply(201, validLaunch().toString());
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Retry-After", "7");
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        flow.start();
        registry.add("partner.embed-demo.flow.api-base-url", () -> "http://127.0.0.1:" + flow.getAddress().getPort());
        registry.add("partner.embed-demo.flow.embed-origin", () -> "https://embed.flow.example.com");
        registry.add("partner.embed-demo.flow.parent-origin", () -> "https://portal.partner.example");
        registry.add("partner.embed-demo.flow.view-key", () -> "project-requirements");
        registry.add("partner.embed-demo.flow.surface-type", () -> "LIST");
        registry.add("partner.embed-demo.flow.assertion-issuer", () -> "https://id.partner.example");
        registry.add("partner.embed-demo.flow.assertion-key-id", () -> "partner-demo-rsa-1");
        registry.add("partner.embed-demo.flow.client-id", () -> "client:id");
        registry.add("partner.embed-demo.flow.client-secret", () -> "client secret");
        registry.add("partner.embed-demo.flow.private-key", () -> privateKey.toString());
        registry.add("partner.embed-demo.flow.allowed-entry-modes", () -> "LIST,CREATE,VIEW");
        registry.add("partner.embed-demo.host.password", () -> "test-password");
        registry.add("partner.embed-demo.host.username", () -> "demo");
        registry.add("partner.embed-demo.host.external-subject", () -> "employee-000123");
        registry.add("partner.embed-demo.host.allowed-record-ids", () -> "record-1");
        registry.add("partner.embed-demo.host.context.supplierId", () -> "supplier-from-server");
    }

    @AfterAll
    static void cleanup() throws Exception {
        if (flow != null) flow.stop(0);
        if (privateKey != null) Files.deleteIfExists(privateKey);
    }

    @BeforeEach
    void reset() {
        tokens.invalidate("machine-token");
        TOKEN_CALLS.set(0);
        LAUNCH_REQUESTS.clear();
        LAUNCH_REPLIES.clear();
        TOKEN_REPLIES.clear();
    }

    @Test
    void requiresLoginAndCsrfBeforeCallingFlow() throws Exception {
        mvc.perform(post("/partner-api/flow-embed-launch").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(request("LIST")))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/partner-api/flow-embed-launch").with(user("demo"))
                        .contentType(MediaType.APPLICATION_JSON).content(request("LIST")))
                .andExpect(status().isForbidden());
        assertThat(TOKEN_CALLS.get()).isZero();
    }

    @Test
    void csrfEndpointUsesActualDemoAuthentication() throws Exception {
        mvc.perform(get("/partner-api/csrf").with(httpBasic("demo", "test-password")))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.headerName").value("X-CSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty());
    }

    @Test
    void issuesVerifiedAssertionsAndCachesOnlyMachineToken() throws Exception {
        for (int i = 0; i < 2; i++) {
            send(request("LIST")).andExpect(status().isCreated())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.launchCode").isNotEmpty());
        }
        assertThat(TOKEN_CALLS.get()).isEqualTo(1);
        assertThat(basicAuthorization).isEqualTo("Basic " + Base64.getEncoder()
                .encodeToString("client%3Aid:client+secret".getBytes(StandardCharsets.UTF_8)));
        var publicKey = (RSAKey) JWKSet.parse(assertions.publicJwks()).getKeys().get(0);
        List<String> jtis = new ArrayList<>();
        for (var request : LAUNCH_REQUESTS) {
            assertThat(request.path("parentOrigin").asText()).isEqualTo("https://portal.partner.example");
            assertThat(request.path("context").path("supplierId").asText()).isEqualTo("supplier-from-server");
            assertThat(request.path("subject").size()).isEqualTo(2);
            assertThat(request.path("entry").has("recordId")).isFalse();
            var jwt = SignedJWT.parse(request.path("subject").path("assertion").asText());
            assertThat(jwt.verify(new RSASSAVerifier(publicKey))).isTrue();
            var claims = jwt.getJWTClaimsSet();
            assertThat(claims.getSubject()).isEqualTo("employee-000123");
            assertThat(claims.getIssuer()).isEqualTo("https://id.partner.example");
            assertThat(claims.getAudience()).containsExactly("flow-embed-launch");
            assertThat(claims.getExpirationTime().getTime() - claims.getIssueTime().getTime()).isEqualTo(60_000);
            jtis.add(claims.getJWTID());
        }
        assertThat(jtis).doesNotHaveDuplicates();
    }

    @Test
    void createsAndViewsOnlyAuthorizedRecords() throws Exception {
        ObjectNode create = (ObjectNode) JSON.readTree(request("CREATE"));
        create.put("formPresentation", "dialog");
        send(create.toString()).andExpect(status().isCreated());
        assertThat(LAUNCH_REQUESTS.get(0).path("ui").path("formPresentation").asText()).isEqualTo("dialog");
        ObjectNode view = (ObjectNode) JSON.readTree(request("VIEW"));
        ((ObjectNode) view.path("entry")).put("recordId", "record-1");
        send(view.toString()).andExpect(status().isCreated());
        ((ObjectNode) view.path("entry")).put("recordId", "someone-elses-record");
        send(view.toString()).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("HOST_RECORD_NOT_ALLOWED"));
        assertThat(LAUNCH_REQUESTS).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"viewKey", "parentOrigin", "subject", "context", "flowUserId"})
    void rejectsBrowserControlledAuthorizationCoordinates(String field) throws Exception {
        ObjectNode request = (ObjectNode) JSON.readTree(request("LIST"));
        request.put(field, "forged");
        send(request.toString()).andExpect(status().isBadRequest());
        assertThat(TOKEN_CALLS.get()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"mode\":\"LIST\",\"recordId\":null}", "{\"mode\":\"CREATE\",\"recordId\":\"x\"}",
            "{\"mode\":\"EDIT\"}", "{\"mode\":\"VIEW\"}", "{\"mode\":\"VIEW\",\"recordId\":123}",
            "{\"mode\":\"LIST\",\"entityCode\":\"forged\"}", "[]"})
    void rejectsInvalidEntryShape(String entry) throws Exception {
        ObjectNode request = (ObjectNode) JSON.readTree(request("LIST"));
        request.set("entry", JSON.readTree(entry));
        send(request.toString()).andExpect(status().isBadRequest());
        assertThat(TOKEN_CALLS.get()).isZero();
    }

    @Test
    void rejectsInvalidChannelAndPresentation() throws Exception {
        ObjectNode request = (ObjectNode) JSON.readTree(request("LIST"));
        request.put("channelId", "invalid");
        send(request.toString()).andExpect(status().isBadRequest());
        request.put("channelId", UUID.randomUUID().toString()).put("formPresentation", "Dialog");
        send(request.toString()).andExpect(status().isBadRequest());
        assertThat(TOKEN_CALLS.get()).isZero();
    }

    @Test
    void refreshesOn401OnceWithNewAssertion() throws Exception {
        LAUNCH_REPLIES.add(new Reply(401, "{\"errorCode\":\"INVALID_TOKEN\"}"));
        send(request("LIST")).andExpect(status().isCreated());
        assertThat(TOKEN_CALLS.get()).isEqualTo(2);
        assertThat(LAUNCH_REQUESTS).hasSize(2);
        assertThat(LAUNCH_REQUESTS.get(0).path("subject").path("assertion").asText())
                .isNotEqualTo(LAUNCH_REQUESTS.get(1).path("subject").path("assertion").asText());
    }

    @Test
    void stopsAfterSecond401AndDoesNotExposeResponseText() throws Exception {
        for (int i = 0; i < 2; i++) {
            LAUNCH_REPLIES.add(new Reply(401, "{\"message\":\"secret-value\"}"));
        }
        var result = send(request("LIST")).andExpect(status().isBadGateway()).andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("secret-value");
        assertThat(LAUNCH_REQUESTS).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"origin", "query", "fragment", "path", "view", "surface", "protocol", "expired", "code"})
    void rejectsUnexpectedLaunchResponse(String change) throws Exception {
        ObjectNode envelope = validLaunch();
        ObjectNode data = (ObjectNode) envelope.path("data");
        switch (change) {
            case "origin" -> data.put("embedUrl", "https://attacker.example/embed/v1/launches/" + data.path("launchId").asText());
            case "query" -> data.put("embedUrl", data.path("embedUrl").asText() + "?secret=x");
            case "fragment" -> data.put("embedUrl", data.path("embedUrl").asText() + "#x");
            case "path" -> data.put("embedUrl", "https://embed.flow.example.com/another-path");
            case "view" -> ((ObjectNode) data.path("view")).put("key", "another-view");
            case "surface" -> ((ObjectNode) data.path("view")).put("surfaceType", "FORM");
            case "protocol" -> data.put("protocolVersion", "another-protocol");
            case "expired" -> data.put("expiresAt", Instant.now().minusSeconds(10).toString());
            case "code" -> envelope.put("code", 200);
        }
        LAUNCH_REPLIES.add(new Reply(201, envelope.toString()));
        send(request("LIST")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("FLOW_LAUNCH_RESPONSE_INVALID"));
    }

    @Test
    void preservesRateLimitMetadataWithoutRetrying() throws Exception {
        LAUNCH_REPLIES.add(new Reply(429, "{\"errorCode\":\"EMBED_RATE_LIMITED\"}"));
        send(request("LIST")).andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "7"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        assertThat(LAUNCH_REQUESTS).hasSize(1);
    }

    @Test
    void rejectsTokenFailureBeforeCreatingLaunch() throws Exception {
        TOKEN_REPLIES.add(new Reply(401, "{\"error\":\"invalid_client\"}"));
        send(request("LIST")).andExpect(status().isBadGateway());
        assertThat(LAUNCH_REQUESTS).isEmpty();
    }

    @Test
    void exposesOnlyPublicJwksWithoutLogin() throws Exception {
        String body = mvc.perform(get("/.well-known/jwks.json")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var key = JWKSet.parse(body).getKeys().get(0);
        assertThat(key.isPrivate()).isFalse();
        assertThat(key.getKeyID()).isEqualTo("partner-demo-rsa-1");
        assertThat(body).doesNotContain("\"d\"", "\"p\"", "\"q\"", "PRIVATE KEY");
    }

    private org.springframework.test.web.servlet.ResultActions send(String body) throws Exception {
        return mvc.perform(post("/partner-api/flow-embed-launch").with(user("demo")).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String request(String mode) {
        ObjectNode request = JSON.createObjectNode().put("channelId", UUID.randomUUID().toString());
        request.putObject("entry").put("mode", mode);
        return request.toString();
    }

    private static ObjectNode validLaunch() {
        String launchId = "lch_0123456789abcdefghijkl";
        ObjectNode envelope = JSON.createObjectNode().put("code", 201);
        ObjectNode data = envelope.putObject("data");
        data.put("launchId", launchId).put("launchCode", "a".repeat(43))
                .put("embedUrl", "https://embed.flow.example.com/embed/v1/launches/" + launchId)
                .put("expiresAt", Instant.now().plusSeconds(60).toString()).put("protocolVersion", "flow-embed/1");
        data.putObject("view").put("key", "project-requirements").put("surfaceType", "LIST");
        return envelope;
    }

    private record Reply(int status, String body) { }
}
