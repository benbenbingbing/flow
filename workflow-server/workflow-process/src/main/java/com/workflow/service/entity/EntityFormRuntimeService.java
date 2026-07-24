package com.workflow.service.entity;

import com.workflow.entity.EntityForm;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.service.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

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
