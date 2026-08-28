package com.workflow.embed.api.web;

import com.workflow.embed.application.runtime.EmbedRuntimeReadFacade;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.core.web.CorrelationContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the read-only Embed Runtime vertical slice. */
@RestController
@RequestMapping("/api/embed/v1/runtime")
public class EmbedRuntimeController {

    private final EmbedRuntimeReadFacade facade;

    public EmbedRuntimeController(EmbedRuntimeReadFacade facade) {
        this.facade = facade;
    }

    /** Returns the minimum actor, immutable target, capabilities, UI policy and limits. */
    @GetMapping("/bootstrap")
    public ResponseEntity<EmbedApiEnvelope<EmbedRuntimeViews.Bootstrap>> bootstrap(
            @RequestHeader("X-Flow-Embed-Protocol") String protocol,
            HttpServletRequest request) {
        if (!"1".equals(protocol)) {
            throw new EmbedException(
                    400, EmbedErrorCode.INVALID_REQUEST,
                    "Embed protocol version is invalid");
        }
        return ok(facade.bootstrap(), CorrelationContext.businessTraceId(request));
    }

    /** Returns a strict External Projection, never the internal Entity list schema DTO. */
    @GetMapping("/schema")
    public ResponseEntity<EmbedApiEnvelope<EmbedRuntimeViews.Schema>> schema(
            HttpServletRequest request) {
        return ok(facade.schema(), CorrelationContext.businessTraceId(request));
    }

    /** Executes a list query whose target release, context and defaults all come from the Session. */
    @PostMapping("/list/query")
    public ResponseEntity<EmbedApiEnvelope<EmbedRuntimeViews.ListResult>> query(
            @Valid @RequestBody(required = false) EmbedRuntimeListQueryRequest request,
            HttpServletRequest servletRequest) {
        return ok(
                facade.query(request),
                CorrelationContext.businessTraceId(servletRequest));
    }

    private static <T> ResponseEntity<EmbedApiEnvelope<T>> ok(T data, String traceId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(EmbedApiEnvelope.ok(data, traceId));
    }
}
