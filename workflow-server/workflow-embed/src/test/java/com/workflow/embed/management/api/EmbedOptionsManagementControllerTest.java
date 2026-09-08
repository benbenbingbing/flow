package com.workflow.embed.management.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.embed.management.application.EmbedOptionsQueryService;
import com.workflow.embed.management.domain.EmbedManagementModel.ApplicationOption;
import com.workflow.embed.management.domain.EmbedManagementModel.JwksMode;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderState;
import com.workflow.embed.management.domain.EmbedManagementModel.ProviderType;
import com.workflow.embed.management.domain.EmbedManagementModel.SecurityStatus;
import com.workflow.embed.management.support.InMemoryEmbedManagementRepository;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmbedOptionsManagementControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        InMemoryEmbedManagementRepository repository = new InMemoryEmbedManagementRepository();
        repository.applicationOptions.put("app-expired", new ApplicationOption(
                "app-expired", "Partner", "client-partner", SecurityStatus.ACTIVE,
                LocalDateTime.of(2020, 1, 1, 0, 0), false));
        repository.applicationOptions.put("app-disabled", new ApplicationOption(
                "app-disabled", "Disabled", "client-disabled", SecurityStatus.DISABLED,
                null, false));
        repository.applicationOptions.put("app-ready", new ApplicationOption(
                "app-ready", "Ready", "client-ready", SecurityStatus.ACTIVE,
                null, true));
        repository.providers.put("provider-b", provider("provider-b", "Partner B", SecurityStatus.ACTIVE));
        repository.providers.put("provider-a", provider("provider-a", "Partner A", SecurityStatus.ACTIVE));
        repository.providers.put("provider-r", provider("provider-r", "Partner R", SecurityStatus.REVOKED));
        mockMvc = MockMvcBuilders.standaloneSetup(new EmbedOptionsManagementController(
                        new EmbedOptionsQueryService(repository)))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .setControllerAdvice(new EmbedManagementExceptionHandler())
                .build();
    }

    @Test
    void applicationOptionsExposeOnlyNamesAndAvailabilityWithUtcExpiry() throws Exception {
        String response = mockMvc.perform(get("/api/embed-management/v1/options/applications")
                        .param("keyword", "client-partner").param("status", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.pageNum").value(1))
                .andExpect(jsonPath("$.data.pageSize").value(20))
                .andExpect(jsonPath("$.data.records[0].id").value("app-expired"))
                .andExpect(jsonPath("$.data.records[0].name").value("Partner"))
                .andExpect(jsonPath("$.data.records[0].expiresAt").value("2020-01-01T00:00:00Z"))
                .andExpect(jsonPath("$.data.records[0].embedLaunchReady").value(false))
                .andReturn().getResponse().getContentAsString();

        assertEquals(Set.of("id", "name", "clientId", "status", "expiresAt",
                        "embedLaunchReady"),
                fieldNames(objectMapper.readTree(response).at("/data/records/0")));
        mockMvc.perform(get("/api/embed-management/v1/options/applications")
                        .param("keyword", "app-ready"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].embedLaunchReady").value(true));
        mockMvc.perform(get("/api/embed-management/v1/options/applications")
                        .param("keyword", "app-disabled"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value("DISABLED"));
    }

    @Test
    void providerOptionsFilterAndPageWithoutExposingValidationConfiguration() throws Exception {
        String response = mockMvc.perform(get("/api/embed-management/v1/options/identity-providers")
                        .param("keyword", " Partner ").param("status", "ACTIVE")
                        .param("pageNum", "2").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.pageNum").value(2))
                .andExpect(jsonPath("$.data.records.length()").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value("provider-b"))
                .andExpect(jsonPath("$.data.records[0].type").value("SIGNED_JWT"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(Set.of("id", "name", "type", "status"),
                fieldNames(objectMapper.readTree(response).at("/data/records/0")));
        // 配置回显按已有 ID 查询时允许定位撤销的身份源，不能被默认 ACTIVE 筛选隐藏。
        mockMvc.perform(get("/api/embed-management/v1/options/identity-providers")
                        .param("keyword", "provider-r"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value("REVOKED"));
    }

    @Test
    void bothOptionsRejectInvalidQueriesWithStableManagementEnvelope() throws Exception {
        for (String directory : new String[]{"applications", "identity-providers"}) {
            for (String[] query : new String[][]{
                    {"status", "UNKNOWN"}, {"pageNum", "0"}, {"pageNum", "bad"},
                    {"pageNum", "2147483647"}, {"pageSize", "101"}, {"keyword", "x".repeat(129)}}) {
                mockMvc.perform(get("/api/embed-management/v1/options/" + directory)
                                .param(query[0], query[1]))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.errorCode").value("EMBED_MANAGEMENT_REQUEST_INVALID"));
            }
        }
    }

    @Test
    void bothOptionsAcceptSearchableColumnLengthBoundariesAfterTrimming() throws Exception {
        for (String directory : new String[]{"applications", "identity-providers"}) {
            for (String keyword : new String[]{
                    "x".repeat(101), "x".repeat(128), "  " + "x".repeat(128) + "  "}) {
                mockMvc.perform(get("/api/embed-management/v1/options/" + directory)
                                .param("keyword", keyword))
                        .andExpect(status().isOk());
            }
        }
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static ProviderState provider(String id, String name, SecurityStatus status) {
        return new ProviderState(id, name, ProviderType.SIGNED_JWT, status,
                "https://private.example/issuer", "internal-subject-namespace", "[\"audience\"]",
                "[\"RS256\"]", JwksMode.STATIC_JWK_SET, "{\"keys\":[]}", null,
                30, 300, 1, 1, 1, "admin", LocalDateTime.of(2025, 1, 1, 0, 0),
                "admin", LocalDateTime.of(2025, 1, 1, 0, 0), null, null);
    }
}
