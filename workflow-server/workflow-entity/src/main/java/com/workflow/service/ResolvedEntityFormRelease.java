package com.workflow.service;

import com.workflow.entity.EntityForm;

public record ResolvedEntityFormRelease(
        EntityForm form,
        String releaseId,
        Integer releaseVersion,
        boolean pinned) {

    public ResolvedEntityFormRelease(
            EntityForm form,
            String releaseId,
            Integer releaseVersion) {
        this(form, releaseId, releaseVersion, false);
    }
}
