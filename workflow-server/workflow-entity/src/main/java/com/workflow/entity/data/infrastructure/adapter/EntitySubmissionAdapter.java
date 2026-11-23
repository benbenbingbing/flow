package com.workflow.entity.data.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.port.entity.model.EntityRecordData;
import com.workflow.port.entity.mutation.model.EntityMutationCommand;
import com.workflow.port.entity.mutation.model.EntityMutationContext;
import com.workflow.port.entity.mutation.model.EntityMutationOperationType;
import com.workflow.port.entity.mutation.model.EntityMutationResult;
import com.workflow.port.entity.mutation.model.EntityMutationSourceType;
import com.workflow.port.entity.mutation.model.EntitySubmissionCommand;
import com.workflow.port.system.CurrentActorPort;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataActionService;
import com.workflow.entity.data.application.mapping.EntityRecordProjection;
import com.workflow.entity.mutation.application.EntityMutationReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Java 调用复用标准实体表单编排，回执与实体和流程引擎变更一起提交。 */
@Component
@RequiredArgsConstructor
public class EntitySubmissionAdapter {
    private final EntityDataActionService actions;
    private final EntityMutationReceiptService receipts;
    private final CurrentActorPort actors;
    private final ObjectMapper json;

    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public EntityRecordData submit(EntitySubmissionCommand command) {
        var actor = actors.current();
        if (actor == null || !StringUtils.hasText(actor.userId())) {
            throw new ForbiddenException("实体提交需要当前操作人");
        }
        String trace = submissionTrace(actor.userId(), command.requestId());
        var context = EntityMutationContext.builder(EntityMutationSourceType.CUSTOM_INTERFACE,
                        command.intent().name(), "实体编程提交")
                .sourceId("EntitySubmission").trace(trace, trace + ":receipt").build();
        var operation = command.recordId() == null ? EntityMutationOperationType.CREATE : EntityMutationOperationType.UPDATE;
        Map<String, Object> input = json.convertValue(command, new TypeReference<>() { });
        // 省略空修订标记，已持久化的升级前回执仍可用相同命令重放。
        input.remove("expectedRevision");
        var envelope = new EntityMutationCommand(trace, command.entityCode(), command.recordId(), operation, input,
                context, command.expectedRevision());
        // 回执在表单校验前重放，确保重试已发起或已进入新一轮的记录不会再操作旧状态。
        var replay = receipts.acquire(envelope);
        if (replay != null) return json.convertValue(replay.record(), EntityRecordData.class);

        var result = EntityRecordProjection.project(execute(command, trace));
        requireResult(command, result);
        receipts.complete(envelope, new EntityMutationResult(trace, command.entityCode(), result.getId(), operation,
                json.convertValue(result, new TypeReference<>() { }), null, null, true, false));
        return result;
    }

    private EntityDataDTO execute(EntitySubmissionCommand command, String trace) {
        if (command.recordId() == null) {
            var dto = new EntityDataDTO();
            dto.setEntityCode(command.entityCode());
            dto.setListKey(command.listKey());
            dto.setFormId(command.formId());
            dto.setFormConfigHash(command.formConfigHash());
            dto.setData(command.values());
            dto.setName(Objects.toString(command.values().get("name"), null));
            dto.setStartProcess(command.intent() == EntitySubmissionCommand.Intent.CREATE_AND_START);
            return actions.createFromPort(dto, trace);
        }
        var request = new LinkedHashMap<String, Object>();
        request.put("data", command.values());
        request.put("formId", command.formId());
        request.put("formConfigHash", command.formConfigHash());
        if (command.intent() == EntitySubmissionCommand.Intent.START_EXISTING) request.put("startProcess", true);
        if (command.intent() == EntitySubmissionCommand.Intent.RESTART) {
            request.put("restartProcess", true);
            request.put("previousProcessInstanceId", command.previousProcessInstanceId());
        }
        return actions.updateFromPort(command.entityCode(), command.recordId(), command.listKey(), request, trace,
                command.expectedRevision());
    }

    /** 返回值必须仍指向本次实体和要求的流程轮次，事件替换不应被记成提交成功。 */
    private void requireResult(EntitySubmissionCommand command, EntityRecordData result) {
        if (result == null || !StringUtils.hasText(result.getId()) || !command.entityCode().equals(result.getEntityCode())
                || command.recordId() != null && !command.recordId().equals(result.getId())) {
            throw new BusinessConflictException("ENTITY_SUBMISSION_TARGET_MISMATCH", "提交未返回预期业务记录");
        }
        switch (command.intent()) {
            case CREATE_DRAFT -> {
                if (StringUtils.hasText(result.getProcessInstanceId())) {
                    throw new BusinessConflictException("ENTITY_SUBMISSION_INTENT_CHANGED", "创建草稿产生了流程实例");
                }
            }
            case CREATE_AND_START, START_EXISTING, RESTART -> {
                if (!StringUtils.hasText(result.getProcessInstanceId())
                        || result.getProcessInstanceId().equals(command.previousProcessInstanceId())) {
                    throw new BusinessConflictException("ENTITY_SUBMISSION_PROCESS_NOT_STARTED", "提交未产生预期流程实例");
                }
            }
            default -> { }
        }
    }

    private String submissionTrace(String actorId, String requestId) {
        try {
            byte[] material = (actorId + "\0" + requestId).getBytes(StandardCharsets.UTF_8);
            return "entity_submit_" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("无法生成实体提交回执键", error);
        }
    }
}
