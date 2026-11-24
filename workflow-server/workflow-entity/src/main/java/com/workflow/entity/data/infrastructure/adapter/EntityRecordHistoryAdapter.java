package com.workflow.entity.data.infrastructure.adapter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.port.entity.EntityRecordVersionPort;
import com.workflow.entity.version.application.EntityRecordVersionComparisonService;
import com.workflow.entity.version.application.EntityRecordVersionService;
import com.workflow.entity.version.application.EntityVersionRestorePlanService;
import com.workflow.entity.version.api.request.ManualVersionCaptureRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntityRecordHistoryAdapter implements EntityRecordVersionPort {
    private final EntityRecordVersionService versions;
    private final EntityRecordVersionComparisonService comparison;
    private final EntityVersionRestorePlanService restore;
    private final ObjectMapper json;

    @Override
    public Page listVersions(String entityCode, String recordId, int pageNumber, int pageSize) {
        var page = versions.listPage(entityCode, recordId, pageNumber, pageSize);
        var summaries = page.getRecords().stream()
                .map(v -> new Version(v.id(), v.versionNo(), v.versionTitle(), v.operationType(), v.operatorId(),
                        v.operatorName(), v.processInstanceId(), v.createTime()))
                .toList();
        return new Page(summaries, page.getTotal(), page.getPageNum(), page.getPageSize());
    }

    @Override
    public Map<String, Object> getSnapshot(String entityCode, String recordId, int version) {
        return versions.detail(entityCode, recordId, version);
    }

    @Override
    public Map<String, Object> compareVersions(String entityCode, String recordId, int fromVersion, int toVersion) {
        return json.convertValue(comparison.compare(entityCode, recordId, fromVersion, toVersion),
                new TypeReference<>() { });
    }

    @Override
    public Map<String, Object> previewRestore(String entityCode, String recordId, int version) {
        return json.convertValue(restore.planForPort(entityCode, recordId, version), new TypeReference<>() { });
    }

    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public Version capture(String entityCode, String recordId, String idempotencyKey, String triggerCode, String reason) {
        // 手工留档服务在写锁后读取当前记录，由原有策略校验和幂等控制处理重试。
        var request = new ManualVersionCaptureRequest();
        request.setTriggerCode(triggerCode);
        request.setReason(reason);
        var captured = versions.captureManualForPort(entityCode, recordId, request, idempotencyKey);
        return new Version(captured.getId(), captured.getVersionNo(), captured.getVersionTitle(),
                captured.getOperationType(), captured.getOperatorId(), captured.getOperatorName(),
                captured.getProcessInstanceId(), captured.getCreateTime());
    }

    @Override
    public Map<String, Object> pageSnapshotRows(String entityCode, String recordId, int version,
            String nodeCode, long pageNumber, long pageSize) {
        return json.convertValue(comparison.snapshotRows(entityCode, recordId, version, nodeCode, pageNumber, pageSize),
                new TypeReference<>() { });
    }

    @Override
    public Map<String, Object> pageDifferenceRows(String entityCode, String recordId, int fromVersion, int toVersion,
            String nodeCode, long pageNumber, long pageSize, boolean changedOnly) {
        return json.convertValue(comparison.compareRows(entityCode, recordId, fromVersion, toVersion,
                nodeCode, pageNumber, pageSize, changedOnly), new TypeReference<>() { });
    }

}
