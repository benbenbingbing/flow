package com.workflow.service.entity;

import com.workflow.entity.EntityForm;
import com.workflow.entity.ProcessNodeForm;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.service.UiConfigReleaseService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EntityFormRuntimeService {

    private final UiConfigReleaseService releaseService;
    private final EntityFormMapper formMapper;

    public EntityForm getById(String formId) {
        return releaseService.resolveRuntimeForm(formId);
    }

    public EntityForm getByBinding(ProcessNodeForm nodeForm) {
        if (nodeForm == null || nodeForm.getFormId() == null) {
            return null;
        }
        return releaseService.resolveRuntimeForm(
                nodeForm.getFormId(),
                nodeForm.getFormReleaseId(),
                nodeForm.getFormReleaseVersion());
    }

    public EntityForm getDefaultForm(String entityId) {
        EntityForm form = formMapper.selectDefaultByEntityId(entityId);
        return form == null ? null : releaseService.resolveRuntimeForm(form.getId());
    }
}
