package com.workflow.embed.application.record;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.embed.api.request.EmbedRecordCreateRequest;
import com.workflow.embed.api.response.EmbedRecordCreateViews;
import com.workflow.embed.application.record.EmbedNativeRecordCreateAuthorizationService.Authorization;
import com.workflow.embed.application.port.EmbedIdempotencyPort;
import com.workflow.embed.application.port.EmbedOperationReceiptPort;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedIdempotencyClaim;
import com.workflow.embed.domain.EmbedOperationReceipt;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** RECORD_CREATE 的原生授权、canonical 幂等和 ID-only 回执重放用例。 */
@Service
public class EmbedRecordCreateFacade {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile(
            "[\\x21-\\x7E]{1,128}");
    private static final String TARGET_TYPE = "ENTITY_FORM";
    private static final String RECEIPT_TARGET_TYPE = "RECORD";
    private static final String REPLAY_SCHEMA = "embed-idempotency-replay-v1";

    private final EmbedNativeRecordCreateAuthorizationService authorizationService;
    private final EmbedCanonicalRequestHasher hasher;
    private final EmbedIdempotencyPort idempotencyPort;
    private final EmbedOperationReceiptPort receiptPort;
    private final EmbedRecordCreateTransactionService transactionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 初始化嵌入式记录创建{@code facade}，保存构造参数供后续方法使用。
     *
     * @param authorizationService 授权服务，保存在对象中供后续校验、查询或展示
     * @param hasher {@code hasher}，保存在对象中供后续校验、查询或展示
     * @param idempotencyPort 幂等端口，保存在对象中供后续校验、查询或展示
     * @param receiptPort 回执端口，保存在对象中供后续校验、查询或展示
     * @param transactionService 事务服务，保存在对象中供后续校验、查询或展示
     * @param objectMapper 对象映射器，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public EmbedRecordCreateFacade(
            EmbedNativeRecordCreateAuthorizationService authorizationService,
            EmbedCanonicalRequestHasher hasher,
            EmbedIdempotencyPort idempotencyPort,
            EmbedOperationReceiptPort receiptPort,
            EmbedRecordCreateTransactionService transactionService,
            ObjectMapper objectMapper) {
        this(
                authorizationService, hasher, idempotencyPort, receiptPort,
                transactionService, objectMapper, Clock.systemUTC());
    }

    /**
     * 初始化嵌入式记录创建{@code facade}，保存构造参数供后续方法使用。
     *
     * @param authorizationService 授权服务依赖，保存到当前对象供后续业务方法调用
     * @param hasher {@code hasher}依赖，保存到当前对象供后续业务方法调用
     * @param idempotencyPort 幂等端口依赖，保存到当前对象供后续业务方法调用
     * @param receiptPort 回执端口依赖，保存到当前对象供后续业务方法调用
     * @param transactionService 事务服务依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
    EmbedRecordCreateFacade(
            EmbedNativeRecordCreateAuthorizationService authorizationService,
            EmbedCanonicalRequestHasher hasher,
            EmbedIdempotencyPort idempotencyPort,
            EmbedOperationReceiptPort receiptPort,
            EmbedRecordCreateTransactionService transactionService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.authorizationService = authorizationService;
        this.hasher = hasher;
        this.idempotencyPort = idempotencyPort;
        this.receiptPort = receiptPort;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 当前授权先于 Claim/Replay 校验；权限或 Release 已变化时不会返回历史业务字段。
     *
     * @param request 本次请求，后续经校验后用于创建嵌入式记录创建{@code facade}
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     * @param traceId 追踪ID，后续用于创建嵌入式记录创建{@code facade}时定位或关联目标
     * @return 创建后的嵌入式记录创建{@code facade}结果，供调用方继续处理
     */
    public CreateOutcome create(
            EmbedRecordCreateRequest request,
            String idempotencyKey,
            String traceId) {
        validateRequest(request, idempotencyKey);
        Authorization authorization = authorizationService.authorize(
                request.getData(), request.getActionKey());
        String actorScopeDigest = hasher.actorScopeDigest(
                authorization.session());
        Map<String, Object> body = canonicalBody(request, authorization);
        String requestHash = hasher.requestHash(
                authorization.session().applicationId(), actorScopeDigest,
                authorization.viewKey(),
                EmbedRecordCreateTransactionService.OPERATION,
                TARGET_TYPE, canonicalTarget(authorization), body);
        Instant now = clock.instant();
        EmbedIdempotencyClaim claim = idempotencyPort.claim(
                authorization.session().applicationId(),
                EmbedRecordCreateTransactionService.OPERATION,
                idempotencyKey, requestHash, now);
        if (claim.processing()) {
            throw new EmbedException(
                    409, EmbedErrorCode.EMBED_REQUEST_IN_PROGRESS,
                    "The same idempotent request is still processing", 2L);
        }

        EmbedOperationReceipt receipt;
        boolean replay;
        if (claim.replay()) {
            receipt = requireReplayReceipt(
                    claim, authorization, actorScopeDigest);
            replay = true;
        } else {
            try {
                receipt = transactionService.create(
                        claim, authorization, actorScopeDigest, now, traceId).receipt();
                replay = false;
            } catch (RuntimeException error) {
                markRetryable(claim, error);
                throw externalFailure(error);
            }
        }
        return new CreateOutcome(
                new EmbedRecordCreateViews.CreateResult(
                        receipt.id(), idOnlyRecord(receipt.targetId()), List.of(),
                        request.getClientMutationId()),
                replay);
    }

    /**
     * 创建结果固定为 ID-only；记录详情继续由 Flow 原生页面按当前 DataScope 读取。
     *
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @return 处理后的ID仅记录结果，供调用方继续处理
     */
    private static EmbedRecordCreateViews.CreatedRecord idOnlyRecord(
            String recordId) {
        return new EmbedRecordCreateViews.CreatedRecord(recordId, null);
    }

    /**
     * 校验并获取重放回执；不满足约束时阻止后续处理。
     *
     * @param claim 认领，作为 {@code readObject} 的输入影响后续处理
     * @param authorization 授权，供本方法校验并获取重放回执时使用
     * @param actorScopeDigest 操作人作用域摘要，供本方法校验并获取重放回执时使用
     * @return 校验并获取后的重放回执结果，供调用方继续处理
     */
    private EmbedOperationReceipt requireReplayReceipt(
            EmbedIdempotencyClaim claim,
            Authorization authorization,
            String actorScopeDigest) {
        if (!Objects.equals(
                EmbedRecordCreateTransactionService.RESOURCE_TYPE,
                claim.resourceType())
                || !StringUtils.hasText(claim.resourceId())
                || claim.responseStatus() == null
                || claim.responseStatus() != 201) {
            throw corruptedReplay();
        }
        JsonNode envelope = readObject(claim.responseBody());
        if (!REPLAY_SCHEMA.equals(envelope.path("schema").asText())
                || !Objects.equals(claim.resourceId(),
                        envelope.path("receiptId").asText(null))
                || !EmbedRecordCreateTransactionService.OUTCOME.equals(
                        envelope.path("outcomeCode").asText(null))) {
            throw corruptedReplay();
        }
        EmbedOperationReceipt receipt = receiptPort.findById(
                        claim.resourceId())
                .orElseThrow(EmbedRecordCreateFacade::corruptedReplay);
        boolean sameScope = Objects.equals(claim.id(),
                        receipt.idempotencyRecordId())
                && Objects.equals(authorization.session().applicationId(),
                        receipt.applicationId())
                && Objects.equals(actorScopeDigest,
                        receipt.actorScopeDigest())
                && Objects.equals(authorization.viewKey(), receipt.viewKey())
                && Objects.equals(
                        EmbedRecordCreateTransactionService.OPERATION,
                        receipt.operation())
                && Objects.equals(RECEIPT_TARGET_TYPE,
                        receipt.targetType())
                && Objects.equals(
                        EmbedRecordCreateTransactionService.OUTCOME,
                        receipt.outcomeCode());
        JsonNode summary = readObject(receipt.resultSummaryJson());
        if (!sameScope || !Objects.equals(receipt.targetId(),
                summary.path("recordId").asText(null))
                || !Objects.equals(receipt.outcomeCode(),
                summary.path("outcomeCode").asText(null))) {
            // Application + Operation 的同键若来自另一 Actor/View，不能暴露原回执。
            throw new EmbedException(
                    409, EmbedErrorCode.EMBED_IDEMPOTENCY_KEY_REUSED,
                    "Idempotency key belongs to a different Embed scope");
        }
        return receipt;
    }

    /**
     * 整理规范请求体数据，供调用方遍历或继续处理。
     *
     * @param request 本次请求，后续经校验后用于处理规范请求体
     * @param authorization 授权，作为 {@code body.put} 的输入影响后续处理
     * @return 规范请求体键值结果，供调用方继续处理
     */
    private Map<String, Object> canonicalBody(
            EmbedRecordCreateRequest request,
            Authorization authorization) {
        Map<String, Object> body = new LinkedHashMap<>();
        // 幂等语义必须绑定服务端最终求得的写入值和数据范围，而不是只绑定浏览器原始值。
        // 这样同一用户在不同强制 Context 下复用同一个 Key 时会得到 409；Session/Release
        // 坐标仍不进入摘要，因此相同业务语义可以跨 Launch 安全重放。
        body.put("data", authorization.effectiveData());
        body.put("contextFilters", authorization.contextFilters());
        // save 与 saveAndStart 的业务副作用不同，必须属于不同幂等语义。
        body.put("actionKey", authorization.actionKey());
        if (request.isClientMutationIdPresent()) {
            body.put("clientMutationId", request.getClientMutationId());
        }
        return body;
    }

    /**
     * 幂等目标只含稳定业务坐标；Release/Session 变化后仍可在当前授权下重放首次结果。
     *
     * @param authorization 授权，作为 {@code target.put} 的输入影响后续处理
     * @return 规范目标键值结果，供调用方继续处理
     */
    private static Map<String, Object> canonicalTarget(
            Authorization authorization) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("entityCode", authorization.target().entityCode());
        target.put("formId", authorization.target().formId());
        return target;
    }

    /**
     * 标记可重试；后续读取或执行将使用更新后的状态。
     *
     * @param claim 认领，作为 {@code idempotencyPort.failRetryable} 的输入影响后续处理
     * @param original 原始，供本方法标记可重试时使用
     */
    private void markRetryable(
            EmbedIdempotencyClaim claim,
            RuntimeException original) {
        try {
            idempotencyPort.failRetryable(claim, clock.instant());
        } catch (RuntimeException failure) {
            original.addSuppressed(failure);
        }
    }

    /**
     * 构造外部失败异常，供调用方区分失败原因。
     *
     * @param error 错误，作为 {@code EmbedException} 的输入影响后续处理
     * @return 处理后的外部失败结果，供调用方继续处理
     */
    private static RuntimeException externalFailure(RuntimeException error) {
        if (error instanceof EmbedException embed) {
            return embed;
        }
        if (error instanceof ForbiddenException) {
            return new EmbedException(
                    403, EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED,
                    "Embed operation is not allowed");
        }
        if (error instanceof BusinessConflictException conflict
                && "FORM_REQUIRED_VALIDATION_FAILED".equals(
                        conflict.getErrorCode())) {
            Map<String, Object> violation = Map.of(
                    "path", "data",
                    "code", "FORM_RULE",
                    "message", "Submitted form data is invalid");
            Map<String, Object> data = Map.of(
                    "violations", List.of(violation),
                    "relaunchRequired", false);
            return new EmbedException(
                    422, EmbedErrorCode.FORM_VALIDATION_FAILED,
                    "Form validation failed", null, null, data);
        }
        if (error instanceof BusinessConflictException) {
            return new EmbedException(
                    409, EmbedErrorCode.EMBED_RECORD_CONFLICT,
                    "Embed record could not be created because of a conflict");
        }
        if (error instanceof IllegalArgumentException) {
            return new EmbedException(
                    400, EmbedErrorCode.INVALID_REQUEST,
                    "Embed request is invalid");
        }
        return new EmbedException(
                503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed runtime is temporarily unavailable", null, error);
    }

    /**
     * 读取对象；查询结果供调用方展示或继续处理。
     *
     * @param value 待读取对象的原始输入，结果供调用方继续使用
     * @return 读取后的对象结果，供调用方继续处理
     */
    private JsonNode readObject(String value) {
        try {
            JsonNode result = objectMapper.readTree(value);
            if (result == null || !result.isObject()) {
                throw corruptedReplay();
            }
            return result;
        } catch (EmbedException error) {
            throw error;
        } catch (Exception error) {
            throw corruptedReplay();
        }
    }

    /**
     * 校验请求；不满足约束时阻止后续处理。
     *
     * @param request 本次请求，后续经校验后用于校验请求
     * @param idempotencyKey 幂等键，后续用于授权校验、关联或幂等去重
     */
    private static void validateRequest(
            EmbedRecordCreateRequest request,
            String idempotencyKey) {
        if (request == null || request.getData() == null
                || idempotencyKey == null
                || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()
                || request.isClientMutationIdPresent()
                && request.getClientMutationId() != null
                && request.getClientMutationId().isBlank()) {
            throw new EmbedException(
                    400, EmbedErrorCode.INVALID_REQUEST,
                    "Embed request is invalid");
        }
    }

    /**
     * 构造{@code corrupted}重放异常，供调用方区分失败原因。
     *
     * @return 处理后的{@code corrupted}重放结果，供调用方继续处理
     */
    private static EmbedException corruptedReplay() {
        return new EmbedException(
                503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed runtime is temporarily unavailable");
    }

    /**
     * 封装创建结果的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param result 结果，保存在对象中供后续校验、查询或展示
     * @param replay 重放，保存在对象中供后续校验、查询或展示
     */
    public record CreateOutcome(
            EmbedRecordCreateViews.CreateResult result,
            boolean replay) {
    }
}
