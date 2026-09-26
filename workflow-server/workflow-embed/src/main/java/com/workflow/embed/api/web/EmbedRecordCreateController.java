package com.workflow.embed.api.web;

import com.workflow.embed.api.request.EmbedRecordCreateRequest;
import com.workflow.embed.api.response.EmbedApiEnvelope;
import com.workflow.embed.api.response.EmbedRecordCreateViews;

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

    /**
     * 初始化嵌入式记录创建控制器，保存构造参数供后续方法使用。
     *
     * @param createFacade 创建{@code facade}依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedRecordCreateController(
            EmbedRecordCreateFacade createFacade) {
        this.createFacade = createFacade;
    }

    /**
     * 幂等创建 Session 固定实体记录；浏览器不能提供实体、表单、Release、用户或流程坐标。
     *
     * @param request 本次请求，后续经校验后用于创建嵌入式记录创建
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param servletRequest Servlet请求，作为 {@code CorrelationContext.businessTraceId} 的输入影响后续处理
     * @return 创建后的嵌入式记录创建结果，供调用方继续处理
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
