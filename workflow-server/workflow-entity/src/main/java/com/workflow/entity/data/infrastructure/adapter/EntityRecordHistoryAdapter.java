package com.workflow.entity.data.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.port.EntityRecordHistoryPort;
import com.workflow.contracts.identity.port.CurrentAuthorizationPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.version.application.EntityRecordVersionComparisonService;
import com.workflow.entity.version.application.EntityRecordVersionService;
import com.workflow.entity.version.application.EntityVersionRestorePlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntityRecordHistoryAdapter implements EntityRecordHistoryPort {
    private final CurrentAuthorizationPort authorization;
    private final EntityActionCapabilityService access;
    private final EntityDataDynamicService records;
    private final EntityRecordVersionService versions;
    private final EntityRecordVersionComparisonService comparison;
    private final EntityVersionRestorePlanService restore;
    private final ObjectMapper json;

    @Override
    public Page list(String entityCode, String recordId, int pageNumber, int pageSize) {
        requireHistoricalView(entityCode, recordId);
        var page = versions.listPage(entityCode, recordId, pageNumber, pageSize);
        var summaries = page.getRecords().stream()
                .map(v -> new Version(v.id(), v.versionNo(), v.versionTitle(), v.operationType(), v.operatorId(),
                        v.operatorName(), v.processInstanceId(), v.createTime()))
                .toList();
        return new Page(summaries, page.getTotal(), page.getPageNum(), page.getPageSize());
    }

    @Override
    public Map<String, Object> snapshot(String entityCode, String recordId, int version) {
        requireHistoricalView(entityCode, recordId);
        return versions.detail(entityCode, recordId, version);
    }

    @Override
    public Map<String, Object> compare(String entityCode, String recordId, int fromVersion, int toVersion) {
        requireHistoricalView(entityCode, recordId);
        return json.convertValue(comparison.compare(entityCode, recordId, fromVersion, toVersion),
                new TypeReference<>() { });
    }

    @Override
    public Map<String, Object> planRestore(String entityCode, String recordId, int version) {
        requireHistoricalView(entityCode, recordId);
        return json.convertValue(restore.plan(entityCode, recordId, version), new TypeReference<>() { });
    }

    /** 历史访问沿用页面的功能权限、实体 VIEW 和行范围校验，覆盖归档记录。 */
    private void requireHistoricalView(String entityCode, String recordId) {
        if (!authorization.hasPermission("entity:version:record:view")) {
            throw new ForbiddenException("没有业务记录版本读取权限");
        }
        access.requireStandardPermission(entityCode, EntityPermissionAction.VIEW);
        records.findAccessibleIncludingDeletedById(entityCode, recordId, null);
    }
}
