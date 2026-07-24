package com.workflow.service.entity;

import com.workflow.common.BusinessConflictException;
import com.workflow.entity.EntityForm;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.entity.UiConfigRelease;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.service.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
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
        if (nodeForm == null || nodeForm.getFormId() == null) {
            return null;
        }
        return releaseService.resolveRuntimeForm(
                nodeForm.getFormId(),
                nodeForm.getFormReleaseId(),
                nodeForm.getFormReleaseVersion());
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
        if (nodeForm == null || !StringUtils.hasText(nodeForm.getFormId())) {
            return;
        }
        UiConfigRelease active = releaseService.active(
                UiConfigReleaseService.FORM,
                nodeForm.getFormId());
        if (active == null) {
            throw new BusinessConflictException(
                    "PROCESS_NODE_FORM_NOT_PUBLISHED",
                    "流程节点表单当前没有激活发布版本，请先发布表单并重新发布流程");
        }
        boolean pinned = StringUtils.hasText(nodeForm.getFormReleaseId())
                || nodeForm.getFormReleaseVersion() != null;
        if (pinned
                && (!Objects.equals(active.getId(), nodeForm.getFormReleaseId())
                || !Objects.equals(active.getVersion(), nodeForm.getFormReleaseVersion()))) {
            throw new BusinessConflictException(
                    "PROCESS_FORM_RELEASE_STALE",
                    "流程节点表单已发布新版本，请重新发布流程后再新增数据");
        }
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
