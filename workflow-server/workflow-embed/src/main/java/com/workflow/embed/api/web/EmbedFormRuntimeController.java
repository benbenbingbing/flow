package com.workflow.embed.api.web;

import com.workflow.embed.application.form.EmbedRuntimeFormFacade;
import com.workflow.embed.application.record.EmbedRecordCreateFacade;
import com.workflow.core.web.CorrelationContext;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** HTTP adapter for the strict read-only Embed form and record projection. */
@RestController
@RequestMapping("/api/embed/v1/runtime")
public class EmbedFormRuntimeController {

    private final EmbedRuntimeFormFacade facade;
    private final EmbedRecordCreateFacade createFacade;

    public EmbedFormRuntimeController(
            EmbedRuntimeFormFacade facade,
            EmbedRecordCreateFacade createFacade) {
        this.facade = facade;
        this.createFacade = createFacade;
    }

    /** 解析 Session 固定 Form Release，不接受 formId/entityCode/releaseId。 */
    @GetMapping("/form")
    public ResponseEntity<EmbedApiEnvelope<EmbedRuntimeFormViews.FormResult>> form(
            @RequestParam String mode,
            @RequestParam(required = false) String recordId,
            HttpServletRequest request) {
        return ok(
                facade.form(mode, recordId),
                CorrelationContext.businessTraceId(request));
    }

    /**
     * 按当前 CREATE 草稿重新计算固定 Form Release 的字段状态；不执行最终提交校验，
     * 也不接受任何目标坐标。
     */
    @PostMapping("/form/evaluations")
    public ResponseEntity<EmbedApiEnvelope<EmbedRuntimeFormViews.FormResult>> evaluateCreate(
            @Valid @RequestBody EmbedCreateFormEvaluationRequest request,
            HttpServletRequest servletRequest) {
        return ok(
                facade.evaluateCreate(request.getData()),
                CorrelationContext.businessTraceId(servletRequest));
    }

    /** 查询当前表单已声明的只读选项来源。 */
    @PostMapping("/form/fields/{fieldCode}/options/query")
    public ResponseEntity<EmbedApiEnvelope<EmbedRuntimeFormViews.OptionPage>> options(
            @PathVariable String fieldCode,
            @Valid @RequestBody EmbedRuntimeOptionQueryRequest request,
            HttpServletRequest servletRequest) {
        return ok(
                facade.queryOptions(fieldCode, request),
                CorrelationContext.businessTraceId(servletRequest));
    }

    /** 查询当前表单已固定引用列表的可访问候选项。 */
    @PostMapping("/form/fields/{fieldCode}/lookups/query")
    public ResponseEntity<EmbedApiEnvelope<EmbedRuntimeFormViews.LookupPage>> lookups(
            @PathVariable String fieldCode,
            @Valid @RequestBody EmbedRuntimeLookupQueryRequest request,
            HttpServletRequest servletRequest) {
        return ok(
                facade.queryLookups(fieldCode, request),
                CorrelationContext.businessTraceId(servletRequest));
    }

    /** 读取可见字段、字段状态和只读动作；无权与不存在统一 404。 */
    @GetMapping("/records/{recordId}")
    public ResponseEntity<EmbedApiEnvelope<EmbedRuntimeFormViews.RecordResult>> record(
            @PathVariable String recordId,
            HttpServletRequest request) {
        EmbedRuntimeFormViews.RecordResult result = facade.record(recordId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(EmbedApiEnvelope.ok(
                        result,
                        CorrelationContext.businessTraceId(request)));
    }

    /**
     * 创建 Session 固定实体记录；浏览器不能提供实体、表单、Release、用户或流程坐标。
     */
    @PostMapping("/records")
    public ResponseEntity<EmbedApiEnvelope<EmbedRuntimeFormViews.CreateResult>> create(
            @Valid @RequestBody EmbedRecordCreateRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest servletRequest) {
        String traceId = CorrelationContext.businessTraceId(servletRequest);
        EmbedRecordCreateFacade.CreateOutcome outcome =
                createFacade.create(
                        request,
                        idempotencyKey,
                        traceId);
        ResponseEntity.BodyBuilder response = ResponseEntity.created(
                        URI.create("/api/embed/v1/runtime/records/"
                                + outcome.result().record().id()))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Idempotent-Replay",
                        String.valueOf(outcome.replay()));
        return response.body(EmbedApiEnvelope.ok(
                outcome.result(), traceId));
    }

    private static <T> ResponseEntity<EmbedApiEnvelope<T>> ok(T data, String traceId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(EmbedApiEnvelope.ok(data, traceId));
    }
}
