package com.workflow.embed.security;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        classes = EmbedRuntimeSecurityConfigurationTest.TestApplication.class,
        properties = {
                "workflow.embed.enabled=false",
                "spring.autoconfigure.exclude="
                        + "org.springframework.boot.autoconfigure.jdbc."
                        + "DataSourceAutoConfiguration"
        })
@AutoConfigureMockMvc
class EmbedRuntimeSecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void rejectsEmbedRoutesEvenWhenFeatureIsDisabled() throws Exception {
        mockMvc.perform(get("/api/embed/v1/runtime/bootstrap")
                        .header("X-Trace-Id", "trace-embed-denied"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode")
                        .value("EMBED_SESSION_INVALID"))
                .andExpect(jsonPath("$.traceId")
                        .value("trace-embed-denied"));
    }

    @Test
    void rejectedRequestUsesOneGeneratedTraceInHeaderAndBody() throws Exception {
        mockMvc.perform(get("/api/embed/v1/runtime/bootstrap")
                        .header("X-Trace-Id", "invalid trace"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(
                        "X-Trace-Id",
                        matchesPattern("[A-Za-z0-9._-]{1,64}")))
                .andExpect(jsonPath("$.traceId").value(
                        matchesPattern("[A-Za-z0-9._-]{1,64}")))
                .andExpect(result -> {
                    String responseTrace = result.getResponse()
                            .getHeader("X-Trace-Id");
                    String body = result.getResponse().getContentAsString();
                    org.junit.jupiter.api.Assertions.assertTrue(
                            body.contains("\"traceId\":\"" + responseTrace + "\""));
                });
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(EmbedRuntimeSecurityConfiguration.class)
    static class TestApplication {
    }
}
