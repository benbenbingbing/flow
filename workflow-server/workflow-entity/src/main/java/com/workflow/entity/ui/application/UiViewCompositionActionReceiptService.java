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
import com.workflow.entity.ui.api.response.UiViewCompositionActionCapabilityDTO;
import com.workflow.entity.ui.api.response.UiViewCompositionActionResponse;
import com.workflow.entity.ui.api.response.UiViewCompositionChangedReferenceDTO;
import com.workflow.entity.version.application.EntityMutationReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * “关联内容”关系动作的批次级持久化幂等门。
 *
 * <p>实体变更管道的回执以单条写命令为粒度，不能单独阻止同一个客户端
 * {@code operationId} 改变动作或目标集合后只执行新增的那一部分。本服务在任何
 * 实体写入前先占用一个稳定的批次回执：键绑定租户、操作者、宿主精确发布、
 * 关联内容和来源记录，命令摘要则绑定动作与排序后的完整目标集合。这样完全相同
 * 的请求会恢复整批结果，任何批次形状变化都会在写入前整体冲突。</p>
 */
@Service
@RequiredArgsConstructor
public class UiViewCompositionActionReceiptService {

    private final EntityMutationReceiptService receiptService;
    private final SysUserService userService;
    private final ObjectMapper objectMapper;

    /**
     * 占用批次回执；首次调用返回待完成句柄，精确重放直接返回历史响应。
     */
    public AcquireResult acquire(
            String ownerType,
            String ownerId,
            String releaseId,
            Integer releaseVersion,
            String compositionKey,
            String sourceEntityCode,
            String sourceRecordId,
            String action,
            List<String> targetRecordIds,
            String operationId) {
        String userId = requireUserId();
        String tenantId = currentTenantId(userId);
        List<String> sortedTargets = targetRecordIds == null
                ? List.of()
                : targetRecordIds.stream()
                        .sorted(Comparator.naturalOrder())
                        .toList();
        String stableKey = "uivc-batch:" + sha256(String.join("|",
                tenantId,
                userId,
                text(ownerType),
                text(ownerId),
                text(releaseId),
                String.valueOf(releaseVersion),
                text(compositionKey),
                text(sourceRecordId),
                text(operationId)));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("action", text(action));
        payload.put("targetRecordIds", sortedTargets);
        EntityMutationSourceType sourceType = "FORM".equalsIgnoreCase(ownerType)
                ? EntityMutationSourceType.FORM
                : EntityMutationSourceType.LIST;
        EntityMutationContext context = EntityMutationContext.builder(
                        sourceType,
                        "VIEW_COMPOSITION_ACTION_BATCH",
                        "关联内容关系动作批次")
                .sourceId(ownerId)
                .sourceRecord(sourceEntityCode, sourceRecordId)
                .operator(userId, UserContext.getUsername())
                .trace(operationId, stableKey)
                .extraParams(Map.of(
                        "tenantId", tenantId,
                        "ownerReleaseId", text(releaseId),
                        "ownerReleaseVersion", releaseVersion,
                        "compositionKey", text(compositionKey)))
                .build();
        // 该命令只作为 entity_mutation_receipt 的摘要载体，绝不会交给写入管道。
        EntityMutationCommand receiptCommand = new EntityMutationCommand(
                "batch:" + operationId,
                sourceEntityCode,
                sourceRecordId,
                EntityMutationOperationType.UPDATE,
                payload,
                context);
        EntityMutationResult replayed = receiptService.acquire(receiptCommand);
        return replayed == null
                ? new AcquireResult(receiptCommand, null)
                : new AcquireResult(
                        receiptCommand,
                        restoreResponse(replayed.record()));
    }

    /** 整批实体写入成功后，将受控动作响应写入同一事务中的持久化回执。 */
    public void complete(
            EntityMutationCommand receiptCommand,
            UiViewCompositionActionResponse response) {
        Map<String, Object> record = objectMapper.convertValue(
                response,
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
                        response.getChangedReferences().stream()
                                .anyMatch(UiViewCompositionChangedReferenceDTO::isChanged),
                        false));
    }

    private UiViewCompositionActionResponse restoreResponse(
            Map<String, Object> stored) {
        Map<String, Object> value = stored == null ? Map.of() : stored;
        List<UiViewCompositionChangedReferenceDTO> changed = new ArrayList<>();
        Object rawChanged = value.get("changedReferences");
        if (rawChanged instanceof List<?> rows) {
            for (Object row : rows) {
                Map<String, Object> item = objectMapper.convertValue(
                        row,
                        new TypeReference<Map<String, Object>>() {});
                changed.add(UiViewCompositionChangedReferenceDTO.builder()
                        .sourceRecordId(text(item.get("sourceRecordId")))
                        .targetRecordId(text(item.get("targetRecordId")))
                        .relationType(text(item.get("relationType")))
                        .linked(Boolean.TRUE.equals(item.get("linked")))
                        .changed(Boolean.TRUE.equals(item.get("changed")))
                        .replayed(true)
                        .build());
            }
        }
        Map<String, UiViewCompositionActionCapabilityDTO> capabilities =
                new LinkedHashMap<>();
        Object rawCapabilities = value.get("actionCapabilities");
        if (rawCapabilities instanceof Map<?, ?> entries) {
            entries.forEach((key, rawCapability) -> {
                Map<String, Object> capability = objectMapper.convertValue(
                        rawCapability,
                        new TypeReference<Map<String, Object>>() {});
                Map<String, Object> initialValues = objectMapper.convertValue(
                        capability.getOrDefault("initialValues", Map.of()),
                        new TypeReference<Map<String, Object>>() {});
                capabilities.put(
                        String.valueOf(key),
                        UiViewCompositionActionCapabilityDTO.builder()
                                .available(Boolean.TRUE.equals(
                                        capability.get("available")))
                                .reason(text(capability.get("reason")))
                                .initialValues(initialValues == null
                                        ? Map.of() : initialValues)
                                .build());
            });
        }
        Map<String, Object> sourcePatch = objectMapper.convertValue(
                value.getOrDefault("sourcePatch", Map.of()),
                new TypeReference<Map<String, Object>>() {});
        Map<String, Object> actionResult = objectMapper.convertValue(
                value.getOrDefault("actionResult", Map.of()),
                new TypeReference<Map<String, Object>>() {});
        return UiViewCompositionActionResponse.builder()
                .operationId(text(value.get("operationId")))
                .action(text(value.get("action")))
                .sourceRecordId(text(value.get("sourceRecordId")))
                .sourcePatch(sourcePatch == null ? Map.of() : sourcePatch)
                .changedReferences(List.copyOf(changed))
                .actionCapabilities(Map.copyOf(capabilities))
                .actionResult(actionResult == null ? Map.of() : actionResult)
                .replayed(true)
                .build();
    }

    private String currentTenantId(String userId) {
        SysUser user = userService.getById(userId);
        return user == null || !StringUtils.hasText(user.getOrgId())
                ? "_" : user.getOrgId().trim();
    }

    private String requireUserId() {
        if (!StringUtils.hasText(UserContext.getUserId())) {
            throw new IllegalStateException("关联内容批次幂等缺少当前用户");
        }
        return UserContext.getUserId().trim();
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("关联内容批次幂等摘要生成失败", exception);
        }
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    public record AcquireResult(
            EntityMutationCommand receiptCommand,
            UiViewCompositionActionResponse replayedResponse) {
    }
}
