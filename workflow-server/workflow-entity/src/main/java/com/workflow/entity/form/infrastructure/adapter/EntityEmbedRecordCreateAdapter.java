package com.workflow.entity.form.infrastructure.adapter;

import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.embed.runtime.port.EmbedRecordCreatePort;
import com.workflow.contracts.entity.mutation.model.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.model.EntityMutationContext;
import com.workflow.contracts.entity.mutation.model.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.port.EntityMutationPort;
import com.workflow.contracts.entity.mutation.model.EntityMutationResult;
import com.workflow.contracts.entity.mutation.model.EntityMutationSourceType;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import java.util.LinkedHashMap;
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

    /**
     * 初始化实体嵌入式记录创建适配器，保存构造参数供后续方法使用。
     *
     * @param capabilityService 能力服务依赖，保存到当前对象供后续业务方法调用
     * @param releaseService 发布版本服务依赖，保存到当前对象供后续业务方法调用
     * @param definitionMapper 定义映射器依赖，保存到当前对象供后续业务方法调用
     * @param formSubmissionService 表单提交服务依赖，保存到当前对象供后续业务方法调用
     * @param mutationPort 变更端口依赖，保存到当前对象供后续业务方法调用
     */
    public EntityEmbedRecordCreateAdapter(
            EntityActionCapabilityService capabilityService,
            UiConfigReleaseService releaseService,
            EntityDefinitionMapper definitionMapper,
            PublishedFormSubmissionService formSubmissionService,
            EntityMutationPort mutationPort) {
        this.capabilityService = capabilityService;
        this.releaseService = releaseService;
        this.definitionMapper = definitionMapper;
        this.formSubmissionService = formSubmissionService;
        this.mutationPort = mutationPort;
    }

    /**
     * 重查实时 CREATE 权限和精确 Form Release，应用发布表单校验后写入统一变更管道。
     *
     * @param command 本次命令，后续经校验后用于创建实体嵌入式记录创建
     * @return 创建后的实体嵌入式记录创建结果，供调用方继续处理
     */
    @Override
    @Transactional(
            propagation = Propagation.MANDATORY,
            rollbackFor = Exception.class)
    public CreatedRecord create(CreateCommand command) {
        requireCommand(command);
        EmbedRecordCreatePort.Target target = command.target();
        capabilityService.requireStandardPermission(
                target.entityCode(), EntityPermissionAction.CREATE);

        ResolvedEntityFormRelease pinned = releaseService.resolveRuntimeFormRelease(
                target.formId(), target.formReleaseId(),
                target.formReleaseVersion(),
                UiRuntimeResolutionContext.historical(null, null));
        EntityForm form = requirePinnedForm(target, pinned);

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
        Map<String, Object> mutationPayload = new LinkedHashMap<>();
        mutationPayload.put("data", applied.data());
        // 只能来自服务端已解析的 saveAndStart 按钮，不接受表单 data 中同名字段。
        mutationPayload.put("startProcess", command.startProcess());
        EntityMutationResult result = mutationPort.execute(
                new EntityMutationCommand(
                        traceKey, target.entityCode(), null,
                        EntityMutationOperationType.CREATE,
                        Map.copyOf(mutationPayload), mutationContext));
        if (result == null
                || result.operationType() != EntityMutationOperationType.CREATE
                || !Objects.equals(target.entityCode(), result.entityCode())
                || !StringUtils.hasText(result.recordId())) {
            throw new IllegalStateException("Embed 实体创建结果无效");
        }
        // entity_record_version 是业务审计版本，不是通用乐观锁版本，不能冒充 recordVersion。
        return new CreatedRecord(result.recordId(), null);
    }

    /**
     * 校验并获取固定表单；不满足约束时阻止后续处理。
     *
     * @param target 目标，作为 {@code findByEntityCode} 的输入影响后续处理
     * @param resolved 已解析，供本方法校验并获取固定表单时使用
     * @return 校验并获取后的固定表单结果，供调用方继续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private EntityForm requirePinnedForm(
            EmbedRecordCreatePort.Target target,
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
     * 校验并获取命令；不满足约束时阻止后续处理。
     *
     * @param command 本次命令，后续经校验后用于校验并获取命令
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void requireCommand(CreateCommand command) {
        if (command == null || command.target() == null
                || command.data() == null
                || !StringUtils.hasText(command.idempotencyRecordId())
                || command.idempotencyRecordId().length() > 64) {
            throw new IllegalArgumentException("Embed 创建命令不完整");
        }
    }

    /**
     * 写入文本；后续读取或执行将使用更新后的状态。
     *
     * @param target 目标，供本方法写入文本时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @param value 待写入文本的原始输入，结果供调用方继续使用
     */
    private static void putText(
            Map<String, Object> target,
            String key,
            String value) {
        if (StringUtils.hasText(value)) {
            target.put(key, value);
        }
    }
}
