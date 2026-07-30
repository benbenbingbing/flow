package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.version.infrastructure.persistence.mapper.EntityMutationReceiptMapper;
import com.workflow.entity.version.infrastructure.persistence.record.EntityMutationReceipt;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 在实体写入事务内持久化并恢复幂等执行结果。
 */
@Service
@RequiredArgsConstructor
public class EntityMutationReceiptService {

    private final EntityMutationReceiptMapper receiptMapper;
    private final ObjectMapper objectMapper;

    /**
     * 首次执行插入 PENDING 回执；已成功执行时返回原结果。
     */
    public EntityMutationResult acquire(
            EntityMutationCommand command) {
        String key = command.context().idempotencyKey();
        EntityMutationReceipt existing =
                receiptMapper.findByIdempotencyKey(key);
        if (existing != null) {
            return replay(existing, command);
        }

        EntityMutationReceipt receipt =
                new EntityMutationReceipt();
        receipt.setId(id());
        receipt.setIdempotencyKey(key);
        receipt.setCommandHash(hash(command));
        receipt.setOperationId(command.operationId());
        receipt.setEntityCode(command.entityCode());
        receipt.setRecordId(command.recordId());
        receipt.setOperationType(
                command.operationType().name());
        receipt.setStatus("PENDING");
        receipt.setCreateTime(LocalDateTime.now());
        receipt.setUpdateTime(LocalDateTime.now());
        try {
            receiptMapper.insert(receipt);
            return null;
        } catch (DuplicateKeyException exception) {
            EntityMutationReceipt raced =
                    receiptMapper
                            .findByIdempotencyKeyForUpdate(
                                    key);
            if (raced != null) {
                return replay(raced, command);
            }
            throw exception;
        }
    }

    public void complete(
            EntityMutationCommand command,
            EntityMutationResult result) {
        int updated = receiptMapper.complete(
                command.context().idempotencyKey(),
                result.recordId(),
                write(result.record()),
                result.versionNo(),
                result.versionScenarioCode(),
                result.changed());
        if (updated != 1) {
            throw new IllegalStateException(
                    "实体变更幂等回执完成失败: "
                            + command.context()
                                    .idempotencyKey());
        }
    }

    private EntityMutationResult replay(
            EntityMutationReceipt receipt,
            EntityMutationCommand command) {
        if (!Objects.equals(
                receipt.getCommandHash(),
                hash(command))) {
            throw new BusinessConflictException(
                    "ENTITY_MUTATION_IDEMPOTENCY_CONFLICT",
                    "同一幂等键不能用于不同的实体变更: "
                            + command.context()
                                    .idempotencyKey());
        }
        if (!"SUCCESS".equals(receipt.getStatus())) {
            throw new BusinessConflictException(
                    "ENTITY_MUTATION_IN_PROGRESS",
                    "相同实体变更正在执行，请稍后重试");
        }
        return new EntityMutationResult(
                receipt.getOperationId(),
                receipt.getEntityCode(),
                receipt.getRecordId(),
                command.operationType(),
                read(receipt.getResultDocument()),
                receipt.getVersionNo(),
                receipt.getVersionScenarioCode(),
                Boolean.TRUE.equals(
                        receipt.getChanged()),
                true);
    }

    private String hash(EntityMutationCommand command) {
        Map<String, Object> material =
                new LinkedHashMap<>();
        material.put("entityCode", command.entityCode());
        material.put("recordId", command.recordId());
        material.put("operationType",
                command.operationType().name());
        material.put("payload", command.payload());
        material.put("sourceType",
                command.context().sourceType().name());
        material.put("sourceId",
                command.context().sourceId());
        material.put("businessIntentCode",
                command.context().businessIntentCode());
        material.put("businessIntentName",
                command.context().businessIntentName());
        material.put("sourceEntityCode",
                command.context().sourceEntityCode());
        material.put("sourceRecordId",
                command.context().sourceRecordId());
        material.put("processDefinitionId",
                command.context().processDefinitionId());
        material.put("processInstanceId",
                command.context().processInstanceId());
        material.put("taskId",
                command.context().taskId());
        material.put("operatorId",
                command.context().operatorId());
        material.put("operatorName",
                command.context().operatorName());
        material.put("businessTraceKey",
                command.context().businessTraceKey());
        material.put("extraParams",
                command.context().extraParams());
        try {
            String canonical = objectMapper.writer()
                    .with(SerializationFeature
                            .ORDER_MAP_ENTRIES_BY_KEYS)
                    .writeValueAsString(material);
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(canonical.getBytes(
                                    StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "实体变更幂等摘要生成失败",
                    exception);
        }
    }

    private String write(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(
                    value == null ? Map.of() : value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "实体变更回执序列化失败",
                    exception);
        }
    }

    private Map<String, Object> read(String value) {
        if (!StringUtils.hasText(value)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(
                    value,
                    new TypeReference<>() {
                    });
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "实体变更回执解析失败",
                    exception);
        }
    }

    private String id() {
        return UUID.randomUUID().toString()
                .replace("-", "");
    }
}
