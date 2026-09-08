package com.workflow.embed.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workflow.embed.application.runtime.EmbedRuntimeReadFacade;
import com.workflow.embed.security.EmbedRequestGuardFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmbedRuntimeControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private EmbedRuntimeReadFacade facade;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        facade = mock(EmbedRuntimeReadFacade.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new EmbedRuntimeController(facade))
                .setControllerAdvice(new EmbedApiExceptionHandler())
                .addFilters(new EmbedRequestGuardFilter(objectMapper, 1_048_576))
                .build();
    }

    @Test
    void bootstrapRequiresExactProtocolAndUsesNoStoreEnvelope() throws Exception {
        when(facade.bootstrap()).thenReturn(new EmbedRuntimeViews.Bootstrap(
                new EmbedRuntimeViews.Session(
                        "ems_1", Instant.parse("2026-08-27T10:00:00Z"),
                        Instant.parse("2026-08-27T09:30:00Z")),
                new EmbedRuntimeViews.Actor("张三"),
                new EmbedRuntimeViews.View(
                        "orders", "工单", "LIST", "LIST"),
                List.of("LIST_QUERY"),
                new EmbedRuntimeViews.Ui(
                        "zh-CN", "light", "seamless", true, true, true, 20, "AUTO"),
                new EmbedRuntimeViews.Limits(100, 1_048_576, 100)));

        mvc.perform(get("/api/embed/v1/runtime/bootstrap")
                        .header("X-Flow-Embed-Protocol", "1")
                        .header("X-Trace-Id", "trace-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.data.session.id").value("ems_1"))
                .andExpect(jsonPath("$.data.ui.formPresentation").value("seamless"))
                .andExpect(jsonPath("$.data.view.revision").doesNotExist())
                .andExpect(jsonPath("$.traceId").value("trace-1"));
    }

    @Test
    void bootstrapRejectsWrongProtocolBeforeFacade() throws Exception {
        mvc.perform(get("/api/embed/v1/runtime/bootstrap")
                        .header("X-Flow-Embed-Protocol", "2"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verifyNoInteractions(facade);
    }

    @Test
    void successfulResponseUsesTheGuardNormalizedTraceInHeaderAndEnvelope()
            throws Exception {
        when(facade.query(any())).thenReturn(new EmbedRuntimeViews.ListResult(
                List.of(), false, 1, 20, null));

        var result = mvc.perform(post("/api/embed/v1/runtime/list/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "unsafe trace")
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();

        String headerTrace = result.getResponse().getHeader("X-Trace-Id");
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        assertEquals(headerTrace, body.path("traceId").asText());
        assertFalse("unsafe trace".equals(headerTrace));
        assertTrue(headerTrace.matches("[A-Za-z0-9._-]{1,64}"));
    }

    @Test
    void listQueryRejectsBrowserSuppliedReleaseContextAndSortFields() throws Exception {
        mvc.perform(post("/api/embed/v1/runtime/list/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageNum":1,
                                  "pageSize":20,
                                  "filters":[],
                                  "releaseId":"attacker-release",
                                  "context":{"tenant":"other"},
                                  "fixedFilters":{"status":"ANY"},
                                  "sorts":[{"field":"secret","direction":"ASC"}]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verifyNoInteractions(facade);
    }

    @Test
    void listQueryRejectsLegacyMapAndBrowserSuppliedOperator() throws Exception {
        mvc.perform(post("/api/embed/v1/runtime/list/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pageNum":1,"pageSize":20,"filters":{"title":"pump"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mvc.perform(post("/api/embed/v1/runtime/list/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageNum":1,
                                  "pageSize":20,
                                  "filters":[
                                    {"field":"title","value":"pump","operator":"EQ"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verifyNoInteractions(facade);
    }

    @Test
    void listQueryBindsOnlyTheThreePublishedValueShapes() throws Exception {
        when(facade.query(any())).thenReturn(new EmbedRuntimeViews.ListResult(
                List.of(), false, 1, 20, null));

        mvc.perform(post("/api/embed/v1/runtime/list/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "pageNum":1,
                                  "pageSize":20,
                                  "filters":[
                                    {"field":"title","value":"pump"},
                                    {"field":"status","values":["OPEN","DONE"]},
                                    {"field":"createdAt","range":{"start":"2026-01-01","end":"2026-12-31"}}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));

        verify(facade).query(argThat(request -> request.getFilters().size() == 3
                && request.getFilters().get(0).hasValue()
                && request.getFilters().get(1).hasValues()
                && request.getFilters().get(2).hasRange()));
    }

    @Test
    void unexpectedRuntimeFailureUsesGenericNoStoreEnvelope() throws Exception {
        doThrow(new IllegalStateException("jdbc:mysql://secret-host/internal"))
                .when(facade).schema();

        mvc.perform(get("/api/embed/v1/runtime/schema")
                        .header("X-Trace-Id", "trace-safe"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.errorCode")
                        .value("EMBED_RUNTIME_UNAVAILABLE"))
                .andExpect(jsonPath("$.message")
                        .value("Embed runtime is temporarily unavailable"))
                .andExpect(header().string("X-Trace-Id", "trace-safe"))
                .andExpect(jsonPath("$.traceId").value("trace-safe"));
    }
}
