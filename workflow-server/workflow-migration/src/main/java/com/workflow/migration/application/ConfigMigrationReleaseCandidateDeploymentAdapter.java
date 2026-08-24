package com.workflow.migration.application;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/** 复用现有 wfpack 发布和回滚实现的候选部署适配器。 */
@Component
@RequiredArgsConstructor
public class ConfigMigrationReleaseCandidateDeploymentAdapter
        implements ReleaseCandidateDeploymentPort {

    private final ConfigMigrationImportApplyService importApplyService;

    @Override
    public Map<String, Object> publishImport(String importId) {
        return importApplyService.publish(importId);
    }

    @Override
    public Map<String, Object> rollbackImport(String importId) {
        return importApplyService.rollback(importId);
    }
}
