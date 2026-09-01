package com.workflow.embed.api.web;

import com.workflow.core.web.CorrelationContext;
import com.workflow.embed.application.record.EmbedRecordCreateFacade;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 受控记录创建入口；表单渲染与读取均由 Flow 原生运行时负责。 */
@RestController
@RequestMapping("/api/embed/v1/runtime")
public class EmbedRecordCreateController {

    private final EmbedRecordCreateFacade createFacade;

    public EmbedRecordCreateController(
            EmbedRecordCreateFacade createFacade) {
        this.createFacade = createFacade;
    }

    /**
     * 幂等创建 Session 固定实体记录；浏览器不能提供实体、表单、Release、用户或流程坐标。
     */
    @PostMapping("/records")
    public ResponseEntity<EmbedApiEnvelope<EmbedRecordCreateViews.CreateResult>>
            create(
                    @Valid @RequestBody EmbedRecordCreateRequest request,
                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                    HttpServletRequest servletRequest) {
        String traceId = CorrelationContext.businessTraceId(servletRequest);
        EmbedRecordCreateFacade.CreateOutcome outcome = createFacade.create(
                request, idempotencyKey, traceId);
        // 旧的 GET /runtime/records/{id} 投影已取消，因此 201 不再发布一个
        // 不可跟随的 Location；调用方只消费响应中的稳定记录 ID。
        ResponseEntity.BodyBuilder response = ResponseEntity
                .status(HttpStatus.CREATED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Idempotent-Replay",
                        String.valueOf(outcome.replay()));
        return response.body(EmbedApiEnvelope.ok(
                outcome.result(), traceId));
    }
}
