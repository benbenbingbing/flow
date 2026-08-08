package com.workflow.process.form.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.logging.LogValue;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * 实体表单运行时服务。
 *
 * <p>根据表单ID或节点绑定关系解析出运行时使用的表单（按发布版本解析），
 * 供审批办理、详情展示等场景获取实际生效的表单配置。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EntityFormRuntimeService {

    private final UiConfigReleaseService releaseService;
    private final EntityFormMapper formMapper;
    private final UiReleaseResolutionTokenService resolutionTokenService;

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
     */
    public EntityForm getByBinding(
            ProcessNodeForm nodeForm,
            String processVersionHistoryId,
            UiRuntimePurpose purpose) {
        ResolvedEntityFormRelease resolved = resolveByBinding(
                nodeForm,
                processVersionHistoryId,
                purpose);
        return resolved == null ? null : resolved.form();
    }

    /**
     * 返回包含原始钉版与有效热修复身份的解析结果。
     */
    public ResolvedEntityFormRelease resolveByBinding(
            ProcessNodeForm nodeForm,
            String processVersionHistoryId,
            UiRuntimePurpose purpose) {
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
        UiRuntimeResolutionContext context =
                new UiRuntimeResolutionContext(
                        purpose,
                        processVersionHistoryId,
                        nodeForm.getNodeId());
        ResolvedEntityFormRelease resolved =
                releaseService.resolveRuntimeFormRelease(
                nodeForm.getFormId(),
                nodeForm.getFormReleaseId(),
                nodeForm.getFormReleaseVersion(),
                context);
        EntityForm form = resolved.form();
        if (form != null) {
            form.setRuntimeReleaseId(resolved.releaseId());
            form.setRuntimeReleaseVersion(
                    resolved.releaseVersion());
            form.setEffectiveReleaseId(
                    resolved.effectiveReleaseId());
            form.setHotfixApplied(resolved.hotfixApplied());
            form.setReleaseResolutionToken(
                    resolutionTokenService.issue(
                            context,
                            form.getId(),
                            resolved.releaseId(),
                            resolved.releaseVersion(),
                            0));
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
     * 校验流程发布快照中的节点表单版本仍是当前激活版本。
     *
     * <p>新增流程数据尚未创建流程实例，应使用最新发布流程快照。若表单已经单独发布了
     * 新版本而流程尚未重新发布，则拒绝继续展示旧表单，避免创建数据时的表单与即将启动
     * 的流程版本不一致。历史流程实例仍通过 {@link #getByBinding(ProcessNodeForm)}
     * 精确读取原发布版本。</p>
     *
     * @param nodeForm 流程发布快照中的节点表单绑定
     * @throws BusinessConflictException 表单没有激活版本或流程快照版本已经过期时抛出
     */
    public void requireCurrentBindingForNewData(ProcessNodeForm nodeForm) {
        requireCurrentBindingForNewData(nodeForm, null);
    }

    /**
     * 校验最新流程版本的表单钉版，已批准热修复不视为过期。
     */
    public void requireCurrentBindingForNewData(
            ProcessNodeForm nodeForm,
            String processVersionHistoryId) {
        if (nodeForm == null || !StringUtils.hasText(nodeForm.getFormId())) {
            return;
        }
        UiConfigRelease active = releaseService.active(
                UiConfigReleaseService.FORM,
                nodeForm.getFormId());
        if (active == null) {
            log.info(
                    "新增流程数据节点表单校验失败: formId={}, pinnedReleaseId={}, pinnedVersion={}, historyId={}, reason=NO_ACTIVE_RELEASE",
                    LogValue.safe(nodeForm.getFormId()),
                    LogValue.safe(nodeForm.getFormReleaseId()),
                    nodeForm.getFormReleaseVersion(),
                    LogValue.safe(processVersionHistoryId));
            throw new BusinessConflictException(
                    "PROCESS_NODE_FORM_NOT_PUBLISHED",
                    "流程节点表单当前没有激活发布版本，请先发布表单并重新发布流程");
        }
        boolean pinned = StringUtils.hasText(nodeForm.getFormReleaseId())
                || nodeForm.getFormReleaseVersion() != null;
        if (pinned
                && (!Objects.equals(active.getId(), nodeForm.getFormReleaseId())
                || !Objects.equals(active.getVersion(), nodeForm.getFormReleaseVersion()))
                && !releaseService.isApprovedHotfix(
                        nodeForm.getFormId(),
                        nodeForm.getFormReleaseId(),
                        nodeForm.getFormReleaseVersion(),
                        processVersionHistoryId,
                        active.getId())) {
            log.info(
                    "新增流程数据节点表单校验失败: formId={}, pinnedReleaseId={}, pinnedVersion={}, activeReleaseId={}, activeVersion={}, historyId={}, reason=STALE_BINDING",
                    LogValue.safe(nodeForm.getFormId()),
                    LogValue.safe(nodeForm.getFormReleaseId()),
                    nodeForm.getFormReleaseVersion(),
                    LogValue.safe(active.getId()),
                    active.getVersion(),
                    LogValue.safe(processVersionHistoryId));
            throw new BusinessConflictException(
                    "PROCESS_FORM_RELEASE_STALE",
                    "流程节点表单已发布新版本，请重新发布流程后再新增数据");
        }
        log.info(
                "新增流程数据节点表单校验通过: formId={}, pinnedReleaseId={}, pinnedVersion={}, activeReleaseId={}, activeVersion={}, historyId={}",
                LogValue.safe(nodeForm.getFormId()),
                LogValue.safe(nodeForm.getFormReleaseId()),
                nodeForm.getFormReleaseVersion(),
                LogValue.safe(active.getId()),
                active.getVersion(),
                LogValue.safe(processVersionHistoryId));
    }

    /**
     * 获取实体的默认表单（按发布版本解析）。
     *
     * @param entityId 实体ID
     * @return 运行时默认表单，不存在时返回 null
     */
    public EntityForm getDefaultForm(String entityId) {
        EntityForm form = formMapper.selectDefaultByEntityId(entityId);
        return form == null ? null : releaseService.resolveRuntimeForm(form.getId());
    }
}
