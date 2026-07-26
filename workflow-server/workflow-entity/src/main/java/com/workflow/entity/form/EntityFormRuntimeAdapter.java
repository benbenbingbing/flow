package com.workflow.entity.form;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.BusinessConflictException;
import com.workflow.contracts.entity.EntityFormBinding;
import com.workflow.contracts.entity.EntityFormRuntimeContext;
import com.workflow.contracts.entity.EntityFormRuntimePort;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.entity.EntityDefinition;
import com.workflow.entity.EntityForm;
import com.workflow.entity.UiConfigRelease;
import com.workflow.mapper.EntityDefinitionMapper;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.service.ResolvedEntityFormRelease;
import com.workflow.service.UiConfigReleaseService;
import com.workflow.service.UiReleaseResolutionTokenService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 将实体表单模型转换为不暴露持久化类型的运行时快照。
 */
@Component
@RequiredArgsConstructor
public class EntityFormRuntimeAdapter implements EntityFormRuntimePort {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final EntityDefinitionMapper definitionMapper;
    private final EntityFormMapper formMapper;
    private final UiConfigReleaseService releaseService;
    private final UiReleaseResolutionTokenService resolutionTokenService;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<EntityFormRuntimeContext> findContext(String entityCode) {
        return definitionMapper.findByEntityCode(entityCode)
                .map(definition -> new EntityFormRuntimeContext(
                        definition.getId(),
                        definition.getEntityCode(),
                        definition.getProcessDefinitionId(),
                        definition.getLifecycleMode() == EntityDefinition.LifecycleMode.WORKFLOW,
                        toMap(getDefaultForm(definition.getId()))));
    }

    @Override
    public Map<String, Object> findFormById(String formId) {
        return toMap(releaseService.resolveRuntimeForm(formId));
    }

    @Override
    public Map<String, Object> findFormByBinding(
            EntityFormBinding binding,
            String processVersionHistoryId,
            UiRuntimePurpose purpose) {
        if (binding == null || !StringUtils.hasText(binding.formId())) {
            return null;
        }
        UiRuntimeResolutionContext context = new UiRuntimeResolutionContext(
                purpose,
                processVersionHistoryId,
                binding.nodeId());
        ResolvedEntityFormRelease resolved =
                releaseService.resolveRuntimeFormRelease(
                        binding.formId(),
                        binding.formReleaseId(),
                        binding.formReleaseVersion(),
                        context);
        EntityForm form = resolved.form();
        if (form == null) {
            return null;
        }
        form.setRuntimeReleaseId(resolved.releaseId());
        form.setRuntimeReleaseVersion(resolved.releaseVersion());
        form.setEffectiveReleaseId(resolved.effectiveReleaseId());
        form.setHotfixApplied(resolved.hotfixApplied());
        form.setReleaseResolutionToken(resolutionTokenService.issue(
                context,
                form.getId(),
                resolved.releaseId(),
                resolved.releaseVersion(),
                0));
        return toMap(form);
    }

    @Override
    public void requireCurrentBindingForNewData(
            EntityFormBinding binding,
            String processVersionHistoryId) {
        if (binding == null || !StringUtils.hasText(binding.formId())) {
            return;
        }
        UiConfigRelease active = releaseService.active(
                UiConfigReleaseService.FORM,
                binding.formId());
        if (active == null) {
            throw new BusinessConflictException(
                    "PROCESS_NODE_FORM_NOT_PUBLISHED",
                    "流程节点表单当前没有激活发布版本，请先发布表单并重新发布流程");
        }
        boolean pinned = StringUtils.hasText(binding.formReleaseId())
                || binding.formReleaseVersion() != null;
        if (pinned
                && (!Objects.equals(active.getId(), binding.formReleaseId())
                || !Objects.equals(
                        active.getVersion(),
                        binding.formReleaseVersion()))
                && !releaseService.isApprovedHotfix(
                        binding.formId(),
                        binding.formReleaseId(),
                        binding.formReleaseVersion(),
                        processVersionHistoryId,
                        active.getId())) {
            throw new BusinessConflictException(
                    "PROCESS_FORM_RELEASE_STALE",
                    "流程节点表单已发布新版本，请重新发布流程后再新增数据");
        }
    }

    private EntityForm getDefaultForm(String entityId) {
        EntityForm form = formMapper.selectDefaultByEntityId(entityId);
        return form == null
                ? null
                : releaseService.resolveRuntimeForm(form.getId());
    }

    private Map<String, Object> toMap(EntityForm form) {
        return form == null ? null : objectMapper.convertValue(form, MAP_TYPE);
    }
}
