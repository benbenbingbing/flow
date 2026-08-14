package com.workflow.entity.version.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import com.workflow.entity.version.application.model.EntityVersionScopePreview;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/** 固化范围的只读样例预览。 */
@Service
@RequiredArgsConstructor
public class EntityVersionScopePreviewService {

    private final EntityVersionConfigurationService configurationService;
    private final EntityRecordSnapshotService snapshotService;
    private final EntityDataDynamicService dataService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public EntityVersionScopePreview preview(
            String entityCode,
            EntityVersionConfiguration request,
            String recordId) {
        EntityVersionConfiguration resolved = configurationService
                .resolveDraft(entityCode, request);
        if (!StringUtils.hasText(recordId)) {
            return new EntityVersionScopePreview(
                    true, 0, 0L, false,
                    resolved.getSnapshotScope().getRelations().stream()
                            .filter(item -> !Boolean.FALSE.equals(item.getEnabled()))
                            .map(item -> new EntityVersionScopePreview.DatasetPreview(
                                    item.getNodeCode(), item.getRelationCode(),
                                    item.getRelationName(), item.getChildEntityCode(),
                                    item.getChildEntityName(), null,
                                    effectiveRelationLimit(resolved, item), false))
                            .toList(),
                    List.of("未提供样例记录ID，仅完成范围结构校验"));
        }
        EntityDataDTO record = dataService.findAccessibleById(
                entityCode, recordId, null);
        Map<String, Object> aggregate = objectMapper.convertValue(
                record, new TypeReference<>() { });
        return snapshotService.previewV2(resolved, aggregate);
    }

    private int effectiveRelationLimit(
            EntityVersionConfiguration configuration,
            EntityVersionConfiguration.RelationScope relation) {
        Integer global = configuration.getSnapshotScope().getLimits() == null
                ? null : configuration.getSnapshotScope().getLimits()
                        .getMaxRowsPerRelation();
        return Math.min(
                EntityRecordSnapshotService.HARD_MAX_ROWS_PER_RELATION,
                Math.min(global == null
                                ? EntityRecordSnapshotService.HARD_MAX_ROWS_PER_RELATION
                                : global,
                        relation.getMaxRows() == null
                                ? EntityRecordSnapshotService.HARD_MAX_ROWS_PER_RELATION
                                : relation.getMaxRows()));
    }
}
