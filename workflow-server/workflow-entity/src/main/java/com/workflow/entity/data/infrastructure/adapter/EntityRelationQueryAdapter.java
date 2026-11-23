package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.contracts.entity.port.EntityRelationQueryPort;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationPlan.InternalPurpose;
import com.workflow.entity.data.application.EntityRelationGraphAuthorizationService;
import com.workflow.entity.data.application.EntityRelationGraphInternalCapabilityService;
import com.workflow.entity.data.application.EntityRelationGraphReadService;
import com.workflow.entity.data.application.model.EntityRelationGraph.Limits;
import com.workflow.entity.definition.application.EntityPublishedSnapshotService;
import com.workflow.entity.definition.application.PublishedRelationPathResolver;
import com.workflow.entity.definition.application.model.PublishedRelationPath.StepSpec;
import com.workflow.entity.definition.application.model.PublishedRelationPath.StepType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class EntityRelationQueryAdapter implements EntityRelationQueryPort {
    private final EntityRelationGraphInternalCapabilityService capability;
    private final EntityPublishedSnapshotService snapshots;
    private final PublishedRelationPathResolver paths;
    private final EntityRelationGraphAuthorizationService authorization;
    private final EntityRelationGraphReadService graphs;

    @Override
    @Transactional(readOnly = true)
    public Result find(String sourceEntityCode, List<String> sourceRecordIds, List<Step> steps,
            int pageNumber, int pageSize) {
        capability.require(InternalPurpose.BUSINESS_QUERY);
        var source = snapshots.getLatestByEntityCode(sourceEntityCode);
        var path = paths.compile(source.getHistoryId(), steps.stream().map(this::toStep).toList());
        // 发布路径只能在宿主签发逐跳数据范围后读取，调用方无法传入范围条件。
        var plan = authorization.authorizeInternal(path, InternalPurpose.BUSINESS_QUERY);
        var defaults = Limits.defaults();
        var graph = graphs.read(path, sourceRecordIds, new Limits(defaults.maxRowsPerHop(), defaults.maxTotalNodes(),
                defaults.maxEdges(), defaults.maxPathStates(), pageNumber, pageSize), plan);
        return new Result(graph.terminalRecords().stream()
                .map(record -> new RecordRef(record.entityCode(), record.recordId())).toList(), graph.terminalTotal(),
                graph.terminalPageNum(), graph.terminalPageSize(), graph.truncated());
    }

    private StepSpec toStep(Step step) {
        String targetId = step.direction() == Direction.REVERSE_REFERENCE
                ? snapshots.getLatestByEntityCode(step.targetEntityCode()).getEntityId() : null;
        return new StepSpec(StepType.valueOf(step.direction().name()), step.code(), targetId);
    }
}
