package com.workflow.entity.form.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.logging.LogValue;
import com.workflow.contracts.entity.form.model.EntityFormBinding;
import com.workflow.contracts.entity.form.model.EntityFormRuntimeContext;
import com.workflow.contracts.entity.form.port.EntityFormRuntimePort;
import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 将实体表单模型转换为不暴露持久化类型的运行时快照。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EntityFormRuntimeAdapter implements EntityFormRuntimePort {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final EntityDefinitionMapper definitionMapper;
    private final EntityFormMapper formMapper;
    private final UiConfigReleaseService releaseService;
    private final ObjectMapper objectMapper;

    /**
     * 查询上下文；查询结果供调用方展示或继续处理。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 匹配的上下文；未找到时为空
     */
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

    /**
     * 按ID查询{@code map<string,}{@code object>}；结果供后续展示或处理。
     *
     * @param formId 表单ID，后续用于查询表单ID时定位或关联目标
     * @return 表单ID键值结果，供调用方继续处理
     */
    @Override
    public Map<String, Object> findFormById(String formId) {
        return toMap(resolveStandaloneForm(formId));
    }

    /**
     * 按绑定查询{@code map<string,}{@code object>}；结果供后续展示或处理。
     *
     * @param binding 绑定，供本方法查询表单绑定时使用
     * @param processVersionHistoryId 流程版本历史ID，后续用于查询表单绑定时定位或关联目标
     * @param purpose 用途，供本方法查询表单绑定时使用
     * @return 表单绑定键值结果，供调用方继续处理
     */
    @Override
    public Map<String, Object> findFormByBinding(
            EntityFormBinding binding,
            String processVersionHistoryId,
            UiRuntimePurpose purpose) {
        return findFormByBinding(
                binding,
                new UiRuntimeResolutionContext(
                        purpose,
                        processVersionHistoryId,
                        binding == null ? null : binding.nodeId()));
    }

    /**
     * 按绑定查询{@code map<string,}{@code object>}；结果供后续展示或处理。
     *
     * @param binding 绑定，作为 {@code LogValue.safe} 的输入影响后续处理
     * @param context 执行上下文，向后续表单绑定步骤传递身份、配置或状态
     * @return 表单绑定键值结果，供调用方继续处理
     */
    @Override
    public Map<String, Object> findFormByBinding(
            EntityFormBinding binding,
            UiRuntimeResolutionContext context) {
        String processVersionHistoryId = context == null
                ? null : context.processVersionHistoryId();
        UiRuntimePurpose purpose = context == null
                ? UiRuntimePurpose.HISTORICAL : context.purpose();
        if (binding == null || !StringUtils.hasText(binding.formId())) {
            log.info(
                    "流程表单绑定解析跳过: historyId={}, purpose={}, reason=EMPTY_BINDING",
                    LogValue.safe(processVersionHistoryId),
                    LogValue.safe(purpose));
            return null;
        }
        log.info(
                "开始解析流程表单绑定: formId={}, pinnedReleaseId={}, pinnedVersion={}, historyId={}, nodeId={}, purpose={}",
                LogValue.safe(binding.formId()),
                LogValue.safe(binding.formReleaseId()),
                binding.formReleaseVersion(),
                LogValue.safe(processVersionHistoryId),
                LogValue.safe(binding.nodeId()),
                LogValue.safe(purpose));
        UiRuntimeResolutionContext effectiveContext = context == null
                ? new UiRuntimeResolutionContext(
                        purpose,
                        processVersionHistoryId,
                        binding.nodeId())
                : context;
        ResolvedEntityFormRelease resolved =
                releaseService.resolveRuntimeFormRelease(
                        binding.formId(),
                        binding.formReleaseId(),
                        binding.formReleaseVersion(),
                        effectiveContext);
        EntityForm form = resolved.form();
        if (form == null) {
            log.info(
                    "流程表单绑定解析无结果: formId={}, historyId={}, nodeId={}, purpose={}",
                    LogValue.safe(binding.formId()),
                    LogValue.safe(processVersionHistoryId),
                    LogValue.safe(binding.nodeId()),
                    LogValue.safe(purpose));
            return null;
        }
        form.setRuntimeReleaseId(resolved.releaseId());
        form.setRuntimeReleaseVersion(resolved.releaseVersion());
        form.setEffectiveReleaseId(resolved.effectiveReleaseId());
        form.setHotfixApplied(resolved.hotfixApplied());
        form.setConfigHash(releaseService.formConfigHash(form.getId()));
        Map<String, Object> runtime = new java.util.LinkedHashMap<>();
        if (StringUtils.hasText(effectiveContext.taskId())) runtime.put("taskId", effectiveContext.taskId());
        if (StringUtils.hasText(effectiveContext.processInstanceId())) {
            runtime.put("processInstanceId", effectiveContext.processInstanceId());
            runtime.put("nodeId", effectiveContext.nodeId());
            runtime.put("recordId", effectiveContext.recordId());
            runtime.put("entityCode", effectiveContext.entityCode());
        }
        form.setRuntimeContext(runtime);
        log.info(
                "流程表单绑定解析完成: formId={}, releaseId={}, releaseVersion={}, effectiveReleaseId={}, hotfixApplied={}, historyId={}, nodeId={}, purpose={}",
                LogValue.safe(form.getId()),
                LogValue.safe(resolved.releaseId()),
                resolved.releaseVersion(),
                LogValue.safe(resolved.effectiveReleaseId()),
                resolved.hotfixApplied(),
                LogValue.safe(processVersionHistoryId),
                LogValue.safe(binding.nodeId()),
                LogValue.safe(purpose));
        return toMap(form);
    }

    /**
     * 校验并获取当前绑定新数据；不满足约束时阻止后续处理。
     *
     * @param binding 绑定，作为 {@code releaseService.active} 的输入影响后续处理
     * @param processVersionHistoryId 流程版本历史ID，后续用于校验并获取当前绑定新数据时定位或关联目标
     * @throws BusinessConflictException 目标状态已被其他操作改变时抛出
     */
    @Override
    public void requireCurrentBindingForNewData(
            EntityFormBinding binding,
            String processVersionHistoryId) {
        if (binding == null || !StringUtils.hasText(binding.formId())) return;
        // 流程历史仅保存稳定表单身份；新建数据同样读取当前配置，不能再用旧 pin
        // 判断表单是否过期，也不需要通过已退役的热修复目标表放行。
        UiConfigRelease current = releaseService.active(UiConfigReleaseService.FORM, binding.formId());
        if (current == null) {
            throw new BusinessConflictException(
                    "PROCESS_NODE_FORM_NOT_PUBLISHED", "流程节点表单尚未发布，请先发布表单");
        }
        log.info("新增流程数据使用当前表单配置: formId={}, contentHash={}, historyId={}",
                LogValue.safe(binding.formId()), LogValue.safe(current.getContentHash()),
                LogValue.safe(processVersionHistoryId));
    }

    /**
     * 读取默认表单；查询结果供调用方展示或继续处理。
     *
     * @param entityId 实体ID，后续用于读取默认表单时定位或关联目标
     * @return 符合条件的实体表单结果，供调用方继续处理
     */
    private EntityForm getDefaultForm(String entityId) {
        EntityForm form = releaseService.currentDefaultForm(entityId);
        return form == null
                ? null
                : resolveStandaloneForm(form.getId());
    }

    /**
     * 解析{@code standalone}表单；输出作为后续校验或处理的输入。
     *
     * @param formId 表单ID，后续用于解析{@code standalone}表单时定位或关联目标
     * @return 解析后的{@code standalone}表单结果，供调用方继续处理
     */
    private EntityForm resolveStandaloneForm(String formId) {
        ResolvedEntityFormRelease resolved =
                releaseService.resolveRuntimeFormRelease(formId);
        EntityForm form = resolved.form();
        if (form == null
                || !StringUtils.hasText(resolved.releaseId())
                || resolved.releaseVersion() == null) {
            return form;
        }
        UiRuntimeResolutionContext context =
                UiRuntimeResolutionContext.standalone();
        form.setRuntimeReleaseId(resolved.releaseId());
        form.setRuntimeReleaseVersion(resolved.releaseVersion());
        form.setEffectiveReleaseId(resolved.effectiveReleaseId());
        form.setHotfixApplied(resolved.hotfixApplied());
        form.setConfigHash(releaseService.formConfigHash(form.getId()));
        form.setRuntimeContext(Map.of());
        log.info(
                "独立表单运行时解析完成: formId={}, releaseId={}, releaseVersion={}, effectiveReleaseId={}, hotfixApplied={}",
                LogValue.safe(form.getId()),
                LogValue.safe(resolved.releaseId()),
                resolved.releaseVersion(),
                LogValue.safe(resolved.effectiveReleaseId()),
                resolved.hotfixApplied());
        return form;
    }

    /**
     * 转换为映射；输出作为后续校验或处理的输入。
     *
     * @param form 表单，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @return 映射键值结果，供调用方继续处理
     */
    private Map<String, Object> toMap(EntityForm form) {
        return form == null ? null : objectMapper.convertValue(form, MAP_TYPE);
    }
}
