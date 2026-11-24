package com.workflow.entity.data.infrastructure.adapter;

import com.workflow.port.entity.EntityRelationPort;
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
public class EntityRelationQueryAdapter implements EntityRelationPort {
    private final EntityPublishedSnapshotService snapshots;
    private final PublishedRelationPathResolver paths;
    private final EntityRelationGraphReadService graphs;

    @Override
    @Transactional(readOnly = true)
    public Result findRelatedRecords(String sourceEntityCode, List<String> sourceRecordIds, List<Step> steps,
            int pageNumber, int pageSize) {
        var source = snapshots.getLatestByEntityCode(sourceEntityCode);
        var path = paths.compile(source.getHistoryId(), steps.stream().map(this::toStep).toList());
        var defaults = Limits.defaults();
        var graph = graphs.readForPort(path, sourceRecordIds, new Limits(defaults.maxRowsPerHop(), defaults.maxTotalNodes(),
                defaults.maxEdges(), defaults.maxPathStates(), pageNumber, pageSize));
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
