package com.workflow.process.form.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.logging.LogValue;
import com.workflow.contracts.entity.ui.model.UiRuntimePurpose;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 实体表单运行时服务。
 *
 * <p>根据表单ID或节点绑定关系解析出运行时使用的表单（按当前发布配置解析），
 * 供审批办理、详情展示等场景获取实际生效的表单配置。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EntityFormRuntimeService {

    private final UiConfigReleaseService releaseService;
    private final EntityFormMapper formMapper;

    /**
     * 根据表单ID解析运行时表单。
     *
     * @param formId 表单ID
     * @return 运行时表单，不存在时返回 null
     */
    public EntityForm getById(String formId) {
        return releaseService.resolveRuntimeForm(formId);
    }

    /**
     * 根据流程节点表单绑定解析运行时表单（按发布版本）。
     *
     * @param nodeForm 流程节点表单绑定，为空或无 formId 时返回 null
     * @return 运行时表单，不存在时返回 null
     */
    public EntityForm getByBinding(ProcessNodeForm nodeForm) {
        return getByBinding(
                nodeForm,
                null,
                UiRuntimePurpose.HISTORICAL);
    }

    /**
     * 根据流程发布上下文解析节点表单。
     *
     * @param nodeForm 节点表单，供本方法读取绑定时使用
     * @param processVersionHistoryId 流程版本历史ID，后续用于读取绑定时定位或关联目标
     * @param purpose 用途，供本方法读取绑定时使用
     * @return 符合条件的实体表单结果，供调用方继续处理
     */
    public EntityForm getByBinding(
            ProcessNodeForm nodeForm,
            String processVersionHistoryId,
            UiRuntimePurpose purpose) {
        return getByBinding(
                nodeForm,
                new UiRuntimeResolutionContext(
                        purpose,
                        processVersionHistoryId,
                        nodeForm == null ? null : nodeForm.getNodeId()));
    }

    /**
     * 使用包含可选活动任务主体的可信解析上下文读取节点表单。
     * ACTIVE_TASK 页面返回 task/process/record 定位信息，下次调用仍须重新鉴权。
     *
     * @param nodeForm 节点表单，作为 {@code resolveByBinding} 的输入影响后续处理
     * @param context 执行上下文，向后续绑定步骤传递身份、配置或状态
     * @return 符合条件的实体表单结果，供调用方继续处理
     */
    public EntityForm getByBinding(
            ProcessNodeForm nodeForm,
            UiRuntimeResolutionContext context) {
        ResolvedEntityFormRelease resolved = resolveByBinding(
                nodeForm, context);
        return resolved == null ? null : resolved.form();
    }

    /**
     * 返回包含当前配置指纹和可信流程上下文的解析结果。
     *
     * @param nodeForm 节点表单，供本方法解析绑定时使用
     * @param processVersionHistoryId 流程版本历史ID，后续用于解析绑定时定位或关联目标
     * @param purpose 用途，供本方法解析绑定时使用
     * @return 解析后的绑定结果，供调用方继续处理
     */
    public ResolvedEntityFormRelease resolveByBinding(
            ProcessNodeForm nodeForm,
            String processVersionHistoryId,
            UiRuntimePurpose purpose) {
        return resolveByBinding(
                nodeForm,
                new UiRuntimeResolutionContext(
                        purpose,
                        processVersionHistoryId,
                        nodeForm == null ? null : nodeForm.getNodeId()));
    }

    /**
     * 使用调用方已从流程引擎/待办读取的可信上下文解析，并返回后续请求所需的业务定位信息。
     *
     * @param nodeForm 节点表单，作为 {@code LogValue.safe} 的输入影响后续处理
     * @param context 执行上下文，向后续绑定步骤传递身份、配置或状态
     * @return 解析后的绑定结果，供调用方继续处理
     */
    public ResolvedEntityFormRelease resolveByBinding(
            ProcessNodeForm nodeForm,
            UiRuntimeResolutionContext context) {
        String processVersionHistoryId = context == null
                ? null : context.processVersionHistoryId();
        UiRuntimePurpose purpose = context == null
                ? UiRuntimePurpose.HISTORICAL : context.purpose();
        if (nodeForm == null || nodeForm.getFormId() == null) {
            log.info(
                    "流程节点表单运行时解析跳过: historyId={}, purpose={}, reason=EMPTY_BINDING",
                    LogValue.safe(processVersionHistoryId),
                    LogValue.safe(purpose));
            return null;
        }
        log.info(
                "开始解析流程节点表单运行时: formId={}, pinnedReleaseId={}, pinnedVersion={}, historyId={}, nodeId={}, purpose={}",
                LogValue.safe(nodeForm.getFormId()),
                LogValue.safe(nodeForm.getFormReleaseId()),
                nodeForm.getFormReleaseVersion(),
                LogValue.safe(processVersionHistoryId),
                LogValue.safe(nodeForm.getNodeId()),
                LogValue.safe(purpose));
        UiRuntimeResolutionContext effectiveContext = context == null
                ? new UiRuntimeResolutionContext(
                        purpose,
                        processVersionHistoryId,
                        nodeForm.getNodeId())
                : context;
        ResolvedEntityFormRelease resolved =
                releaseService.resolveRuntimeFormRelease(
                nodeForm.getFormId(),
                nodeForm.getFormReleaseId(),
                nodeForm.getFormReleaseVersion(),
                effectiveContext);
        EntityForm form = resolved.form();
        if (form != null) {
            form.setRuntimeReleaseId(resolved.releaseId());
            form.setRuntimeReleaseVersion(
                    resolved.releaseVersion());
            form.setEffectiveReleaseId(
                    resolved.effectiveReleaseId());
            form.setHotfixApplied(resolved.hotfixApplied());
            form.setConfigHash(releaseService.formConfigHash(form.getId()));
            form.setRuntimeContext(businessContext(effectiveContext));
        }
        log.info(
                "流程节点表单运行时解析完成: formId={}, releaseId={}, releaseVersion={}, effectiveReleaseId={}, hotfixApplied={}, historyId={}, nodeId={}, purpose={}, formPresent={}",
                LogValue.safe(nodeForm.getFormId()),
                LogValue.safe(resolved.releaseId()),
                resolved.releaseVersion(),
                LogValue.safe(resolved.effectiveReleaseId()),
                resolved.hotfixApplied(),
                LogValue.safe(processVersionHistoryId),
                LogValue.safe(nodeForm.getNodeId()),
                LogValue.safe(purpose),
                form != null);
        return resolved;
    }

    /**
     * 校验流程节点绑定的稳定表单已发布。
     *
     * <p>新建、活动任务和历史详情统一读取当前已发布表单；流程发布历史仅决定节点绑定身份。</p>
     *
     * @param nodeForm 流程发布快照中的节点表单绑定
     * @throws BusinessConflictException 表单尚未发布时抛出
     */
    public void requireCurrentBindingForNewData(ProcessNodeForm nodeForm) {
        requireCurrentBindingForNewData(nodeForm, null);
    }

    /**
     * 校验节点表单当前发布配置存在，无需因表单更新重新部署流程。
     *
     * @param nodeForm 节点表单，作为 {@code releaseService.active} 的输入影响后续处理
     * @param processVersionHistoryId 流程版本历史ID，后续用于校验并获取当前绑定新数据时定位或关联目标
     */
    public void requireCurrentBindingForNewData(
            ProcessNodeForm nodeForm,
            String processVersionHistoryId) {
        if (nodeForm == null || !StringUtils.hasText(nodeForm.getFormId())) return;
        // 流程只绑定稳定表单身份。表单发布后不再要求重发流程。
        if (releaseService.active(UiConfigReleaseService.FORM, nodeForm.getFormId()) == null)
            throw new BusinessConflictException("PROCESS_NODE_FORM_NOT_PUBLISHED", "流程节点表单尚未发布，请先发布表单");
    }

    /**
     * 获取实体的默认表单（按当前发布配置解析）。
     *
     * @param entityId 实体ID
     * @return 运行时默认表单，不存在时返回 null
     */
    public EntityForm getDefaultForm(String entityId) {
        return getDefaultForm(entityId, null);
    }

    /**
     * 按当前发布配置加载默认表单，并提供任务业务定位信息。
     * 配置摘要用于识别加载与提交期间的配置变更；办理权限由后端实时复核。
     *
     * @param entityId 实体 ID
     * @param context 已由流程进度校验的发布历史、节点和任务上下文；普通独立查看可为空
     * @return 实际生效的默认发布表单，不存在时返回 null
     */
    public EntityForm getDefaultForm(
            String entityId,
            UiRuntimeResolutionContext context) {
        EntityForm form = releaseService.currentDefaultForm(entityId);
        if (form == null) {
            return null;
        }
        ResolvedEntityFormRelease resolved =
                releaseService.resolveRuntimeFormRelease(form.getId());
        EntityForm runtimeForm = resolved.form();
        if (runtimeForm != null) {
            // 默认表单未经过流程节点钉版，仍需保留实际解析的发布坐标供查看端诊断。
            runtimeForm.setRuntimeReleaseId(resolved.releaseId());
            runtimeForm.setRuntimeReleaseVersion(
                    resolved.releaseVersion());
            runtimeForm.setConfigHash(releaseService.formConfigHash(runtimeForm.getId()));
            if (context != null) {
                runtimeForm.setRuntimeContext(businessContext(context));
            }
        }
        return runtimeForm;
    }
    /** 只返回办理校验所需的稳定表单身份及摘要，避免跨模块传递表单持久化实体。 */
    public com.workflow.contracts.process.task.model.TaskFormReference submissionReference(
            ProcessNodeForm binding, UiRuntimeResolutionContext context) {
        EntityForm form = getByBinding(binding, context);
        return form == null ? null : new com.workflow.contracts.process.task.model.TaskFormReference(
                form.getId(), form.getConfigHash());
    }

    /** 默认表单也使用同一发布解析规则；返回 null 时提交入口必须明确拒绝。 */
    public com.workflow.contracts.process.task.model.TaskFormReference defaultSubmissionReference(
            String entityId, UiRuntimeResolutionContext context) {
        EntityForm form = getDefaultForm(entityId, context);
        return form == null ? null : new com.workflow.contracts.process.task.model.TaskFormReference(
                form.getId(), form.getConfigHash());
    }

    /** 只向页面提供稳定业务坐标；流程版本和用户权限不交由浏览器维护。 */
    private java.util.Map<String, Object> businessContext(UiRuntimeResolutionContext context) {
        var values = new java.util.LinkedHashMap<String, Object>();
        if (context != null) {
            if (context.taskId() != null) values.put("taskId", context.taskId());
            if (context.processInstanceId() != null) values.put("processInstanceId", context.processInstanceId());
            if (context.processInstanceId() != null && context.nodeId() != null) values.put("nodeId", context.nodeId());
            if (context.entityCode() != null) values.put("entityCode", context.entityCode());
            if (context.recordId() != null) values.put("recordId", context.recordId());
        }
        return values;
    }
}
