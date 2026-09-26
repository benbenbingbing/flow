package com.workflow.embed.api.web;

import com.workflow.embed.api.request.EmbedRuntimeListQueryRequest;
import com.workflow.embed.api.response.EmbedApiEnvelope;
import com.workflow.embed.api.response.EmbedRuntimeViews;

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

    /**
     * 初始化嵌入式运行时控制器，保存构造参数供后续方法使用。
     *
     * @param facade {@code facade}依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedRuntimeController(EmbedRuntimeReadFacade facade) {
        this.facade = facade;
    }

    /**
     * Returns the minimum actor, immutable target, capabilities, UI policy and limits.
     *
     * @param protocol {@code protocol}，作为 {@code EmbedException} 的输入影响后续处理
     * @param request 本次请求，后续经校验后用于处理初始化
     * @return 处理后的初始化结果，供调用方继续处理
     */
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

    /**
     * Returns a strict External Projection, never the internal Entity list schema DTO.
     *
     * @param request 本次请求，后续经校验后用于处理结构
     * @return 处理后的结构结果，供调用方继续处理
     */
    @GetMapping("/schema")
    public ResponseEntity<EmbedApiEnvelope<EmbedRuntimeViews.Schema>> schema(
            HttpServletRequest request) {
        return ok(facade.schema(), CorrelationContext.businessTraceId(request));
    }

    /**
     * Executes a list query whose target release, context and defaults all come from the Session.
     *
     * @param request 本次请求，后续经校验后用于查询嵌入式运行时
     * @param servletRequest Servlet请求，供本方法查询嵌入式运行时时使用
     * @return 查询后的嵌入式运行时结果，供调用方继续处理
     */
    @PostMapping("/list/query")
    public ResponseEntity<EmbedApiEnvelope<EmbedRuntimeViews.ListResult>> query(
            @Valid @RequestBody(required = false) EmbedRuntimeListQueryRequest request,
            HttpServletRequest servletRequest) {
        return ok(
                facade.query(request),
                CorrelationContext.businessTraceId(servletRequest));
    }

    /**
     * 处理{@code ok}，并将结果传给后续步骤。
     *
     * @param data 数据，后续用于处理{@code ok}并传递处理结果
     * @param traceId 追踪ID，后续用于处理{@code ok}时定位或关联目标
     * @return 处理后的{@code ok}结果，供调用方继续处理
     */
    private static <T> ResponseEntity<EmbedApiEnvelope<T>> ok(T data, String traceId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(EmbedApiEnvelope.ok(data, traceId));
    }
}
