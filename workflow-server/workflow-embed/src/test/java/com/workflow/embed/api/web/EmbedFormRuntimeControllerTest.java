package com.workflow.embed.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.workflow.embed.application.form.EmbedRuntimeFormFacade;
import com.workflow.embed.application.record.EmbedRecordCreateFacade;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.security.EmbedRequestGuardFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EmbedFormRuntimeControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private EmbedRuntimeFormFacade facade;
    private EmbedRecordCreateFacade createFacade;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        facade = mock(EmbedRuntimeFormFacade.class);
        createFacade = mock(EmbedRecordCreateFacade.class);
        mvc = MockMvcBuilders
                .standaloneSetup(new EmbedFormRuntimeController(
                        facade, createFacade))
                .setControllerAdvice(new EmbedApiExceptionHandler())
                .addFilters(new EmbedRequestGuardFilter(objectMapper, 1_048_576))
                .build();
    }

    @Test
    void formUsesStableNoStoreEnvelopeAndDoesNotRequireBrowserRecordId() throws Exception {
        when(facade.form("VIEW", null)).thenReturn(new EmbedRuntimeFormViews.FormResult(
                "VIEW",
                new EmbedRuntimeFormViews.RecordView(
                        "record-1", null, Map.of("title", "工单"),
                        new EmbedRuntimeFormViews.RecordMeta(null, null)),
                new EmbedRuntimeFormViews.FormView(
                        "工单详情", new EmbedRuntimeFormViews.FormLayout("GRID"),
                        List.of(), List.of(), List.of())));

        mvc.perform(get("/api/embed/v1/runtime/form")
                        .queryParam("mode", "VIEW")
                        .header("X-Trace-Id", "trace-form"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.mode").value("VIEW"))
                .andExpect(jsonPath("$.data.record.id").value("record-1"))
                .andExpect(jsonPath("$.traceId").value("trace-form"));
    }

    @Test
    void createEvaluationAcceptsOnlyDraftDataAndReturnsNoStoreProjection()
            throws Exception {
        when(facade.evaluateCreate(Map.of("status", "CLOSED")))
                .thenReturn(new EmbedRuntimeFormViews.FormResult(
                        "CREATE", null,
                        new EmbedRuntimeFormViews.FormView(
                                "新建工单",
                                new EmbedRuntimeFormViews.FormLayout("GRID"),
                                List.of(), List.of(), List.of())));

        mvc.perform(post("/api/embed/v1/runtime/form/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Trace-Id", "trace-evaluation")
                        .content("{\"data\":{\"status\":\"CLOSED\"}}"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.mode").value("CREATE"))
                .andExpect(jsonPath("$.traceId").value("trace-evaluation"));
    }

    @Test
    void createEvaluationRejectsMissingDataAndBrowserTargetCoordinates()
            throws Exception {
        mvc.perform(post("/api/embed/v1/runtime/form/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mvc.perform(post("/api/embed/v1/runtime/form/evaluations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "data":{"status":"OPEN"},
                                  "entityCode":"work_order",
                                  "formId":"form-1",
                                  "releaseId":"release-1",
                                  "recordId":"record-1"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verifyNoInteractions(facade);
    }

    @Test
    void optionQueryRejectsProviderAndReleaseCoordinatesBeforeFacade() throws Exception {
        mvc.perform(post("/api/embed/v1/runtime/form/fields/category/options/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode":"CREATE",
                                  "keyword":"维修",
                                  "dependencies":{},
                                  "pageNum":1,
                                  "pageSize":20,
                                  "provider":"http",
                                  "serviceId":"internal-service",
                                  "releaseId":"attacker-release",
                                  "url":"https://attacker.example"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verifyNoInteractions(facade);
    }

    @Test
    void missingModeUsesStableInvalidRequestEnvelope() throws Exception {
        mvc.perform(get("/api/embed/v1/runtime/form"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verifyNoInteractions(facade);
    }

    @Test
    void lookupQueryRejectsOversizedPageAtBeanValidationBoundary() throws Exception {
        mvc.perform(post("/api/embed/v1/runtime/form/fields/lineId/lookups/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"CREATE","keyword":"产线","filters":{},"pageNum":1,"pageSize":51}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verifyNoInteractions(facade);
    }

    @Test
    void choiceQueriesRejectMissingAndEmptyBodiesBeforeFacade() throws Exception {
        for (String path : List.of(
                "/api/embed/v1/runtime/form/fields/category/options/query",
                "/api/embed/v1/runtime/form/fields/lineId/lookups/query")) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
            mvc.perform(post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(""))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
            mvc.perform(post(path)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("null"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        }
        verifyNoInteractions(facade);
    }

    @Test
    void choiceQueriesRequireACompleteCreateOrViewNavigationCoordinate() throws Exception {
        mvc.perform(post("/api/embed/v1/runtime/form/fields/category/options/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"keyword":"维修","dependencies":{},"pageNum":1,"pageSize":20}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));
        mvc.perform(post("/api/embed/v1/runtime/form/fields/lineId/lookups/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mode":"VIEW","filters":{},"pageNum":1,"pageSize":20}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verifyNoInteractions(facade);
    }

    @Test
    void recordNeverReturnsEtagOrRecordVersionInV1() throws Exception {
        EmbedRuntimeFormViews.RecordResult versioned = new EmbedRuntimeFormViews.RecordResult(
                new EmbedRuntimeFormViews.RecordView(
                        "record-1", null, Map.of("title", "工单"),
                        new EmbedRuntimeFormViews.RecordMeta(null, null)),
                Map.of(), Map.of());
        when(facade.record("record-1")).thenReturn(versioned);

        mvc.perform(get("/api/embed/v1/runtime/records/record-1"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(jsonPath("$.data.record.recordVersion").isEmpty());
    }

    @Test
    void missingAndUnauthorizedRecordsUseSameStable404Envelope() throws Exception {
        when(facade.record("record-404")).thenThrow(new EmbedException(
                404, EmbedErrorCode.EMBED_RESOURCE_NOT_FOUND,
                "Embed resource was not found"));

        mvc.perform(get("/api/embed/v1/runtime/records/record-404"))
                .andExpect(status().isNotFound())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.errorCode")
                        .value("EMBED_RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void createReturnsLocationAndExplicitReplayHeader() throws Exception {
        EmbedRuntimeFormViews.CreateResult result = createResult();
        when(createFacade.create(
                any(EmbedRecordCreateRequest.class), eq("key-first"), any()))
                .thenReturn(new EmbedRecordCreateFacade.CreateOutcome(result, false));
        when(createFacade.create(
                any(EmbedRecordCreateRequest.class), eq("key-replay"), any()))
                .thenReturn(new EmbedRecordCreateFacade.CreateOutcome(result, true));

        mvc.perform(post("/api/embed/v1/runtime/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-first")
                        .header("X-Trace-Id", "trace-first")
                        .content("""
                                {"data":{"title":"新工单"},"clientMutationId":"client-1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "/api/embed/v1/runtime/records/record-created"))
                .andExpect(header().string("Idempotent-Replay", "false"))
                .andExpect(header().doesNotExist(HttpHeaders.ETAG))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.receiptId").value("eor-1"));

        mvc.perform(post("/api/embed/v1/runtime/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-replay")
                        .header("X-Trace-Id", "trace-replay")
                        .content("""
                                {"data":{"title":"新工单"},"clientMutationId":"client-1"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotent-Replay", "true"));

        verify(createFacade).create(
                any(EmbedRecordCreateRequest.class),
                eq("key-first"),
                eq("trace-first"));
        verify(createFacade).create(
                any(EmbedRecordCreateRequest.class),
                eq("key-replay"),
                eq("trace-replay"));
    }

    @Test
    void createUsesOneNormalizedTraceForHeaderEnvelopeAndAuditCommand()
            throws Exception {
        when(createFacade.create(
                any(EmbedRecordCreateRequest.class), eq("key-trace"), any()))
                .thenReturn(new EmbedRecordCreateFacade.CreateOutcome(
                        createResult(), false));

        var result = mvc.perform(post("/api/embed/v1/runtime/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-trace")
                        .header("X-Trace-Id", "unsafe trace")
                        .content("{\"data\":{\"title\":\"A\"}}"))
                .andExpect(status().isCreated())
                .andReturn();

        String headerTrace = result.getResponse().getHeader("X-Trace-Id");
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsByteArray());
        ArgumentCaptor<String> auditTrace = ArgumentCaptor.forClass(String.class);
        verify(createFacade).create(
                any(EmbedRecordCreateRequest.class),
                eq("key-trace"),
                auditTrace.capture());
        assertEquals(headerTrace, body.path("traceId").asText());
        assertEquals(headerTrace, auditTrace.getValue());
        assertFalse("unsafe trace".equals(headerTrace));
        assertTrue(headerTrace.matches("[A-Za-z0-9._-]{1,64}"));
    }

    @Test
    void createRequiresIdempotencyKeyAndRejectsBrowserTargetCoordinates()
            throws Exception {
        mvc.perform(post("/api/embed/v1/runtime/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"data\":{\"title\":\"A\"}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        mvc.perform(post("/api/embed/v1/runtime/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-1")
                        .content("""
                                {
                                  "data":{"title":"A"},
                                  "entityCode":"work_order",
                                  "formId":"form-1",
                                  "releaseId":"release-1",
                                  "flowUserId":"attacker",
                                  "startProcess":true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REQUEST"));

        verifyNoInteractions(createFacade);
    }

    @Test
    void processingConflictReturnsRetryAfter() throws Exception {
        when(createFacade.create(
                any(EmbedRecordCreateRequest.class), eq("key-1"), any()))
                .thenThrow(new EmbedException(
                        409, EmbedErrorCode.EMBED_REQUEST_IN_PROGRESS,
                        "processing", 2L));

        mvc.perform(post("/api/embed/v1/runtime/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-1")
                        .content("{\"data\":{\"title\":\"A\"}}"))
                .andExpect(status().isConflict())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "2"))
                .andExpect(jsonPath("$.errorCode")
                        .value("EMBED_REQUEST_IN_PROGRESS"));
    }

    @Test
    void formValidationReturnsSafeStructuredViolations() throws Exception {
        Map<String, Object> data = Map.of(
                "violations", List.of(Map.of(
                        "path", "data.title",
                        "code", "REQUIRED",
                        "message", "标题不能为空")),
                "relaunchRequired", false);
        when(createFacade.create(
                any(EmbedRecordCreateRequest.class), eq("key-1"), any()))
                .thenThrow(new EmbedException(
                        422, EmbedErrorCode.FORM_VALIDATION_FAILED,
                        "Form validation failed", null, null, data));

        mvc.perform(post("/api/embed/v1/runtime/records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "key-1")
                        .content("{\"data\":{\"title\":\"\"}}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.errorCode").value("FORM_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.data.violations[0].path")
                        .value("data.title"))
                .andExpect(jsonPath("$.data.relaunchRequired").value(false));
    }

    private static EmbedRuntimeFormViews.CreateResult createResult() {
        return new EmbedRuntimeFormViews.CreateResult(
                "eor-1",
                new EmbedRuntimeFormViews.RecordView(
                        "record-created", null, Map.of("title", "新工单"),
                        new EmbedRuntimeFormViews.RecordMeta(null, null)),
                List.of(), "client-1");
    }
}
