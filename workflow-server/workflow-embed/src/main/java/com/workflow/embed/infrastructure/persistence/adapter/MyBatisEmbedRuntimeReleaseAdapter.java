package com.workflow.embed.infrastructure.persistence.adapter;

import com.workflow.embed.application.port.EmbedRuntimeReleasePort;
import com.workflow.embed.domain.EmbedRuntimeReleaseSnapshot;
import com.workflow.embed.infrastructure.persistence.mapper.EmbedRuntimeReleaseMapper;
import com.workflow.embed.infrastructure.persistence.record.EmbedRuntimeReleaseRow;
import org.springframework.stereotype.Component;

/** MyBatis adapter for the immutable release pinned to a runtime session. */
@Component
public class MyBatisEmbedRuntimeReleaseAdapter implements EmbedRuntimeReleasePort {

    private final EmbedRuntimeReleaseMapper mapper;

    public MyBatisEmbedRuntimeReleaseAdapter(EmbedRuntimeReleaseMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public EmbedRuntimeReleaseSnapshot find(
            String sessionId,
            String viewId,
            String releaseId) {
        EmbedRuntimeReleaseRow row = mapper.find(sessionId, viewId, releaseId);
        return row == null ? null : new EmbedRuntimeReleaseSnapshot(
                row.releaseId(), row.viewId(), row.viewKey(), row.viewName(), row.revision(),
                row.surfaceType(), row.entityCode(), row.listKey(), row.listReleaseId(),
                row.listReleaseVersion(), row.formReleaseId(), row.formReleaseVersion(),
                row.capabilitiesJson(), row.fieldPolicyJson(),
                row.actionPolicyJson(), row.contextBindingsJson(), row.uiConfigJson(),
                row.configJson(), row.actorDisplayName(), row.uiLocale(), row.uiTheme());
    }
}
