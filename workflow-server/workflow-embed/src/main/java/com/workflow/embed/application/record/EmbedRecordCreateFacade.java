package com.workflow.embed.application.record;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.embed.api.web.EmbedRecordCreateRequest;
import com.workflow.embed.api.web.EmbedRuntimeFormViews;
import com.workflow.embed.application.form.EmbedRuntimeFormFacade;
import com.workflow.embed.application.form.EmbedRuntimeFormFacade.CreateAuthorization;
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

/** RECORD_CREATE 的授权、canonical 幂等和当前 Output Policy 重放用例。 */
@Service
public class EmbedRecordCreateFacade {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile(
            "[\\x21-\\x7E]{1,128}");
    private static final String TARGET_TYPE = "ENTITY_FORM";
    private static final String RECEIPT_TARGET_TYPE = "RECORD";
    private static final String REPLAY_SCHEMA = "embed-idempotency-replay-v1";

    private final EmbedRuntimeFormFacade formFacade;
    private final EmbedCanonicalRequestHasher hasher;
    private final EmbedIdempotencyPort idempotencyPort;
    private final EmbedOperationReceiptPort receiptPort;
    private final EmbedRecordCreateTransactionService transactionService;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public EmbedRecordCreateFacade(
            EmbedRuntimeFormFacade formFacade,
            EmbedCanonicalRequestHasher hasher,
            EmbedIdempotencyPort idempotencyPort,
            EmbedOperationReceiptPort receiptPort,
            EmbedRecordCreateTransactionService transactionService,
            ObjectMapper objectMapper) {
        this(
                formFacade, hasher, idempotencyPort, receiptPort,
                transactionService, objectMapper, Clock.systemUTC());
    }

    EmbedRecordCreateFacade(
            EmbedRuntimeFormFacade formFacade,
            EmbedCanonicalRequestHasher hasher,
            EmbedIdempotencyPort idempotencyPort,
            EmbedOperationReceiptPort receiptPort,
            EmbedRecordCreateTransactionService transactionService,
            ObjectMapper objectMapper,
            Clock clock) {
        this.formFacade = formFacade;
        this.hasher = hasher;
        this.idempotencyPort = idempotencyPort;
        this.receiptPort = receiptPort;
        this.transactionService = transactionService;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 当前授权先于 Claim/Replay 校验；权限或 Release 已变化时不会返回历史业务字段。
     */
    public CreateOutcome create(
            EmbedRecordCreateRequest request,
            String idempotencyKey,
            String traceId) {
        validateRequest(request, idempotencyKey);
        CreateAuthorization authorization = formFacade.authorizeCreate(
                request.getData());
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
        EmbedRuntimeFormViews.RecordView record =
                formFacade.projectCreatedRecord(
                        authorization, receipt.targetId());
        return new CreateOutcome(
                new EmbedRuntimeFormViews.CreateResult(
                        receipt.id(), record, List.of(),
                        request.getClientMutationId()),
                replay);
    }

    private EmbedOperationReceipt requireReplayReceipt(
            EmbedIdempotencyClaim claim,
            CreateAuthorization authorization,
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

    private Map<String, Object> canonicalBody(
            EmbedRecordCreateRequest request,
            CreateAuthorization authorization) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("data", authorization.clientData());
        if (request.isClientMutationIdPresent()) {
            body.put("clientMutationId", request.getClientMutationId());
        }
        return body;
    }

    /**
     * 幂等目标只含稳定业务坐标；Release/Session 变化后仍可在当前授权下重放首次结果。
     */
    private static Map<String, Object> canonicalTarget(
            CreateAuthorization authorization) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("entityCode", authorization.target().entityCode());
        target.put("formId", authorization.target().formId());
        return target;
    }

    private void markRetryable(
            EmbedIdempotencyClaim claim,
            RuntimeException original) {
        try {
            idempotencyPort.failRetryable(claim, clock.instant());
        } catch (RuntimeException failure) {
            original.addSuppressed(failure);
        }
    }

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

    private static EmbedException corruptedReplay() {
        return new EmbedException(
                503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed runtime is temporarily unavailable");
    }

    public record CreateOutcome(
            EmbedRuntimeFormViews.CreateResult result,
            boolean replay) {
    }
}
