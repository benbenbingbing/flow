package com.workflow.entity.ui.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.ui.api.request.UiEventExecuteRequest;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import com.workflow.entity.version.application.EntityMutationReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * 表单自定义按钮事件的持久化幂等门。
 *
 * <p>客户端 requestId 只参与摘要，最终唯一键同时绑定租户、操作者、精确发布、
 * 表单、按钮和记录。请求输入仅进入命令摘要，不写入回执；成功响应为支持精确
 * 重放而写入现有 entity_mutation_receipt，访问和保留边界与实体变更回执一致。</p>
 */
@Service
@RequiredArgsConstructor
public class UiEventExecutionReceiptService {

    private static final int MAX_REQUEST_ID_LENGTH = 128;
    private static final String REQUEST_ID_PATTERN =
            "[A-Za-z0-9][A-Za-z0-9._:-]*";

    private final EntityMutationReceiptService receiptService;
    private final SysUserService userService;
    private final ObjectMapper objectMapper;

    /**
     * 在表单按钮专用事务内完成占位、事件执行和结果回执。
     *
     * <p>回调抛出异常时 PENDING 插入会随事务回滚，避免请求永久停留在执行中。
     * 发布解析和按钮权限校验应由调用方在进入本方法前完成。</p>
     *
     * @param request 已通过权限校验的表单按钮请求
     * @param chain 服务端解析出的精确发布事件链
     * @param action 仅执行该发布事件链的回调
     * @return 首次执行结果，或已完成请求的原始结果重放
     */
    @Transactional(rollbackFor = Exception.class)
    public UiEventExecutionResult execute(
            UiEventExecuteRequest request,
            UiEventBindingService.ResolvedEventChain chain,
            Supplier<UiEventExecutionResult> action) {
        Objects.requireNonNull(action, "表单按钮事件执行回调不能为空");
        AcquireResult receipt = acquire(request, chain);
        request.setServerIdempotencyKey(
                receipt.receiptCommand().context().idempotencyKey());
        if (receipt.replayedResult() != null) {
            return receipt.replayedResult();
        }
        UiEventExecutionResult result = Objects.requireNonNull(
                action.get(), "表单按钮事件执行结果不能为空");
        result.setRequestId(request.getRequestId().trim());
        result.setReplayed(false);
        complete(receipt.receiptCommand(), result);
        return result;
    }

    /**
     * 首次请求占用 PENDING 回执；已成功的精确重放恢复原始执行结果。
     *
     * @param request 已通过发布来源解析的事件请求
     * @param chain 服务端解析出的精确发布事件链
     * @return 回执命令和可选的历史执行结果
     */
    public AcquireResult acquire(
            UiEventExecuteRequest request,
            UiEventBindingService.ResolvedEventChain chain) {
        requireFormButtonRequest(request);
        requirePublishedChain(chain);
        String requestId = request.getRequestId().trim();
        String userId = requireUserId();
        String tenantId = currentTenantId(userId);
        String stableKey = "ui-form-button:" + sha256(String.join("|",
                tenantId,
                userId,
                UiDataSourceUsages.FORM_BUTTON_CLICK,
                text(request.getConfigId()),
                text(chain.releaseId()),
                String.valueOf(chain.releaseVersion()),
                text(chain.effectiveReleaseId()),
                text(chain.effectiveContentHash()),
                text(request.getTargetKey()),
                text(request.getRecordId()),
                text(request.getServerTaskId()),
                requestId));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventCode", UiDataSourceUsages.FORM_BUTTON_CLICK);
        payload.put("configType", "FORM");
        payload.put("configId", request.getConfigId());
        payload.put("releaseId", chain.releaseId());
        payload.put("releaseVersion", chain.releaseVersion());
        payload.put("effectiveReleaseId", chain.effectiveReleaseId());
        payload.put("effectiveContentHash", chain.effectiveContentHash());
        payload.put("targetType", normalize(request.getTargetType()));
        payload.put("targetKey", request.getTargetKey());
        payload.put("recordId", request.getRecordId());
        // 审批任务身份来自执行前的服务端回查；绝不摘要客户端原始 taskId。
        payload.put("authorizedTaskId", request.getServerTaskId());
        payload.put("selectedIds", request.getSelectedIds() == null
                ? List.of() : request.getSelectedIds());
        payload.put("selection", request.getSelection());
        payload.put("input", request.getInput() == null
                ? Map.of() : request.getInput());
        payload.put("context", request.getContext() == null
                ? Map.of() : request.getContext());

        EntityMutationContext context = EntityMutationContext.builder(
                        EntityMutationSourceType.FORM,
                        "FORM_BUTTON_CLICK",
                        "表单自定义按钮事件")
                .sourceId(request.getConfigId())
                .sourceRecord(chain.entityCode(), request.getRecordId())
                // 展示名可变且不属于请求语义；回执摘要只绑定稳定 userId。
                .operator(userId, null)
                .trace(requestId, stableKey)
                .extraParams(Map.of(
                        "tenantId", tenantId,
                        "releaseId", text(chain.releaseId()),
                        "releaseVersion", chain.releaseVersion(),
                        "effectiveReleaseId", chain.effectiveReleaseId(),
                        "effectiveContentHash", chain.effectiveContentHash(),
                        "authorizedTaskId", text(
                                request.getServerTaskId()),
                        "buttonKey", text(request.getTargetKey())))
                .build();
        // 该命令只承载回执摘要，绝不会交给实体变更管道；合成 recordId
        // 可覆盖新增态表单按钮，同时避免把任意客户端记录标识扩展到回执主键字段。
        EntityMutationCommand receiptCommand = new EntityMutationCommand(
                requestId,
                chain.entityCode(),
                "event:" + sha256(stableKey).substring(0, 48),
                EntityMutationOperationType.UPDATE,
                payload,
                context);
        try {
            EntityMutationResult replayed =
                    receiptService.acquire(receiptCommand);
            return replayed == null
                    ? new AcquireResult(receiptCommand, null)
                    : new AcquireResult(
                            receiptCommand,
                            restoreResult(replayed.record(), requestId));
        } catch (BusinessConflictException exception) {
            if ("ENTITY_MUTATION_IN_PROGRESS".equals(
                    exception.getErrorCode())) {
                throw new BusinessConflictException(
                        "UI_EVENT_REQUEST_IN_PROGRESS",
                        "相同表单按钮请求正在执行，请稍后重试");
            }
            if ("ENTITY_MUTATION_IDEMPOTENCY_CONFLICT".equals(
                    exception.getErrorCode())) {
                throw new BusinessConflictException(
                        "UI_EVENT_REQUEST_CONFLICT",
                        "同一 requestId 不能用于不同的表单按钮请求");
            }
            throw exception;
        }
    }

    /** 成功完成整条事件链后持久化可重放响应。 */
    public void complete(
            EntityMutationCommand receiptCommand,
            UiEventExecutionResult result) {
        Map<String, Object> record = objectMapper.convertValue(
                result,
                new TypeReference<Map<String, Object>>() {});
        receiptService.complete(
                receiptCommand,
                new EntityMutationResult(
                        receiptCommand.operationId(),
                        receiptCommand.entityCode(),
                        receiptCommand.recordId(),
                        receiptCommand.operationType(),
                        record,
                        null,
                        null,
                        true,
                        false));
    }

    private UiEventExecutionResult restoreResult(
            Map<String, Object> stored,
            String requestId) {
        UiEventExecutionResult result = objectMapper.convertValue(
                stored == null ? Map.of() : stored,
                UiEventExecutionResult.class);
        result.setRequestId(requestId);
        result.setReplayed(true);
        return result;
    }

    private void requireFormButtonRequest(UiEventExecuteRequest request) {
        if (request == null
                || !UiDataSourceUsages.FORM_BUTTON_CLICK.equals(
                        normalize(request.getEventCode()))
                || !"FORM".equals(normalize(request.getConfigType()))) {
            throw new IllegalArgumentException(
                    "事件幂等回执只支持 FORM_BUTTON_CLICK");
        }
        if (validRequestIdOrNull(request.getRequestId()) == null) {
            throw new IllegalArgumentException(
                    "FORM_BUTTON_CLICK 必须提供不超过 128 位的合法 requestId");
        }
    }

    private void requirePublishedChain(
            UiEventBindingService.ResolvedEventChain chain) {
        if (chain == null
                || !StringUtils.hasText(chain.releaseId())
                || chain.releaseVersion() == null
                || chain.releaseVersion() < 1
                || !StringUtils.hasText(chain.entityCode())
                || !StringUtils.hasText(chain.effectiveReleaseId())
                || !StringUtils.hasText(chain.effectiveContentHash())
                || chain.snapshot() == null
                || chain.snapshot().isEmpty()) {
            throw new IllegalStateException(
                    "表单按钮事件幂等缺少可验证的发布身份");
        }
    }

    /** 返回可安全进入日志/审计的 requestId；非法输入返回 null。 */
    static String validRequestIdOrNull(String requestId) {
        return StringUtils.hasText(requestId)
                && requestId.length() <= MAX_REQUEST_ID_LENGTH
                && requestId.matches(REQUEST_ID_PATTERN)
                ? requestId.trim() : null;
    }

    private String currentTenantId(String userId) {
        SysUser user = userService.getById(userId);
        return user == null || !StringUtils.hasText(user.getOrgId())
                ? "_" : user.getOrgId().trim();
    }

    private String requireUserId() {
        if (!StringUtils.hasText(UserContext.getUserId())) {
            throw new IllegalStateException(
                    "表单按钮事件幂等缺少当前用户");
        }
        return UserContext.getUserId().trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "表单按钮事件幂等摘要生成失败", exception);
        }
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(java.util.Locale.ROOT) : "";
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record AcquireResult(
            EntityMutationCommand receiptCommand,
            UiEventExecutionResult replayedResult) {
    }
}
