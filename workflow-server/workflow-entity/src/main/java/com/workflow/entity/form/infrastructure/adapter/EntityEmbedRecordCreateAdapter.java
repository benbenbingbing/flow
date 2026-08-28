package com.workflow.entity.form.infrastructure.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.embed.EmbedRecordCreatePort;
import com.workflow.contracts.embed.EmbedRuntimeFormPort;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.contracts.ui.UiDataSourceUsages;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormField;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 把 Embed RECORD_CREATE 适配到固定发布表单和统一实体变更管道。
 *
 * <p>本适配器不能自行开启事务：调用者必须先开启同时覆盖实体写、Embed Operation Receipt
 * 和 integration_idempotency_record fenced complete 的业务事务。</p>
 */
@Component
public class EntityEmbedRecordCreateAdapter implements EmbedRecordCreatePort {

    private static final String OPERATION = "EMBED_RECORD_CREATE";

    private final EntityActionCapabilityService capabilityService;
    private final UiConfigReleaseService releaseService;
    private final EntityDefinitionMapper definitionMapper;
    private final PublishedFormSubmissionService formSubmissionService;
    private final EntityMutationPort mutationPort;
    private final ObjectMapper objectMapper;

    public EntityEmbedRecordCreateAdapter(
            EntityActionCapabilityService capabilityService,
            UiConfigReleaseService releaseService,
            EntityDefinitionMapper definitionMapper,
            PublishedFormSubmissionService formSubmissionService,
            EntityMutationPort mutationPort,
            ObjectMapper objectMapper) {
        this.capabilityService = capabilityService;
        this.releaseService = releaseService;
        this.definitionMapper = definitionMapper;
        this.formSubmissionService = formSubmissionService;
        this.mutationPort = mutationPort;
        this.objectMapper = objectMapper;
    }

    /**
     * 重查实时 CREATE 权限和精确 Form Release，应用发布表单校验后写入统一变更管道。
     */
    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public CreatedRecord create(CreateCommand command) {
        requireCommand(command);
        EmbedRuntimeFormPort.Target target = command.target();
        capabilityService.requireStandardPermission(
                target.entityCode(), EntityPermissionAction.CREATE);

        ResolvedEntityFormRelease pinned = releaseService.resolveRuntimeFormRelease(
                target.formId(), target.formReleaseId(),
                target.formReleaseVersion(),
                UiRuntimeResolutionContext.historical(null, null));
        EntityForm form = requirePinnedForm(target, pinned);
        rejectBeforeSubmitBindings(form);

        String traceKey = "embed_create_" + command.idempotencyRecordId();
        FormSubmissionExecutionContext submissionContext =
                new FormSubmissionExecutionContext(
                        traceKey, OPERATION,
                        Map.of("source", "EMBED"));
        PublishedFormSubmissionService.AuthorizedFormApplication applied =
                formSubmissionService.applyFormWithRelease(
                        target.formId(), target.formReleaseId(),
                        target.formReleaseVersion(), target.entityCode(),
                        null, "create", command.data(), submissionContext,
                        UiRuntimeResolutionContext.historical(null, null));
        if (!Objects.equals(target.formReleaseId(), applied.releaseId())
                || !Objects.equals(target.formReleaseVersion(),
                        applied.releaseVersion())) {
            throw new IllegalStateException(
                    "Embed 创建提交解析到了不同表单发布版本");
        }

        Map<String, Object> releaseIdentity = new LinkedHashMap<>();
        releaseIdentity.put(FormUniqueMutationContext.FORM_ID, target.formId());
        releaseIdentity.put(FormUniqueMutationContext.FORM_RELEASE_ID,
                applied.releaseId());
        releaseIdentity.put(FormUniqueMutationContext.FORM_RELEASE_VERSION,
                applied.releaseVersion());
        putText(releaseIdentity,
                FormUniqueMutationContext.FORM_EFFECTIVE_RELEASE_ID,
                applied.effectiveReleaseId());
        putText(releaseIdentity,
                FormUniqueMutationContext.FORM_EFFECTIVE_CONTENT_HASH,
                applied.effectiveContentHash());
        putText(releaseIdentity,
                FormUniqueMutationContext.FORM_HOTFIX_TARGET_ID,
                applied.hotfixTargetId());

        EntityMutationContext mutationContext = EntityMutationContext.builder(
                        EntityMutationSourceType.FORM,
                        "CREATE_RECORD", "Embed 新增实体数据")
                .sourceId(target.formId())
                .sourceRecord(target.entityCode(), null)
                .operator(UserContext.getUserId(), UserContext.getUsername())
                .trace(traceKey, traceKey)
                .extraParams(releaseIdentity)
                .build();
        EntityMutationResult result = mutationPort.execute(
                new EntityMutationCommand(
                        traceKey, target.entityCode(), null,
                        EntityMutationOperationType.CREATE,
                        Map.of("data", applied.data()), mutationContext));
        if (result == null
                || result.operationType() != EntityMutationOperationType.CREATE
                || !Objects.equals(target.entityCode(), result.entityCode())
                || !StringUtils.hasText(result.recordId())) {
            throw new IllegalStateException("Embed 实体创建结果无效");
        }
        // entity_record_version 是业务审计版本，不是通用乐观锁版本，不能冒充 recordVersion。
        return new CreatedRecord(result.recordId(), null);
    }

    private EntityForm requirePinnedForm(
            EmbedRuntimeFormPort.Target target,
            ResolvedEntityFormRelease resolved) {
        EntityForm form = resolved == null ? null : resolved.form();
        if (form == null || !resolved.pinned()
                || !Objects.equals(target.formId(), form.getId())
                || !Objects.equals(target.formReleaseId(), resolved.releaseId())
                || !Objects.equals(target.formReleaseVersion(),
                        resolved.releaseVersion())) {
            throw new IllegalStateException("Embed 固定表单发布版本无效");
        }
        EntityDefinition definition = definitionMapper
                .findByEntityCode(target.entityCode())
                .orElseThrow(() -> new IllegalStateException(
                        "Embed 目标实体不存在"));
        if (!Objects.equals(definition.getId(), form.getEntityId())
                || definition.getStorageMode()
                == EntityDefinition.StorageMode.SYSTEM) {
            throw new IllegalStateException("Embed 表单与动态实体目标不一致");
        }
        return form;
    }

    /**
     * BEFORE_SUBMIT 可能调用不可回滚的 Provider；CREATE V1 在接入事务 Outbox 前一律拒绝。
     */
    private void rejectBeforeSubmitBindings(EntityForm form) {
        if (hasBeforeSubmit(form.getDataSourceBindingsDocument())) {
            throw new IllegalStateException(
                    "Embed CREATE 不允许 BEFORE_SUBMIT 数据源绑定");
        }
        for (EntityFormNode node : form.getNodes() == null
                ? List.<EntityFormNode>of() : form.getNodes()) {
            if (hasBeforeSubmit(node.getDataSourceBindingsDocument())) {
                throw new IllegalStateException(
                        "Embed CREATE 不允许节点 BEFORE_SUBMIT 数据源绑定");
            }
        }
        for (EntityFormField field : form.getFields() == null
                ? List.<EntityFormField>of() : form.getFields()) {
            if (field.getDataSourceBindings() != null
                    && field.getDataSourceBindings().containsKey(
                            UiDataSourceUsages.BEFORE_SUBMIT)) {
                throw new IllegalStateException(
                        "Embed CREATE 不允许字段 BEFORE_SUBMIT 数据源绑定");
            }
        }
    }

    private boolean hasBeforeSubmit(String document) {
        if (!StringUtils.hasText(document)) {
            return false;
        }
        try {
            JsonNode value = objectMapper.readTree(document);
            if (value == null || !value.isObject()) {
                throw new IllegalStateException("发布表单数据源绑定格式无效");
            }
            return value.has(UiDataSourceUsages.BEFORE_SUBMIT)
                    && !value.path(UiDataSourceUsages.BEFORE_SUBMIT).isNull();
        } catch (IllegalStateException error) {
            throw error;
        } catch (Exception error) {
            throw new IllegalStateException(
                    "发布表单数据源绑定解析失败", error);
        }
    }

    private static void requireCommand(CreateCommand command) {
        if (command == null || command.target() == null
                || command.data() == null
                || !StringUtils.hasText(command.idempotencyRecordId())
                || command.idempotencyRecordId().length() > 64) {
            throw new IllegalArgumentException("Embed 创建命令不完整");
        }
    }

    private static void putText(
            Map<String, Object> target,
            String key,
            String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }
}
