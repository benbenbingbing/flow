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

    /**
     * 初始化运行时发布版本适配器，后续通过映射器读取会话固定的发布快照。
     *
     * @param mapper 映射器依赖，保存到当前对象供后续业务方法调用
     */
    public MyBatisEmbedRuntimeReleaseAdapter(EmbedRuntimeReleaseMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 按会话、视图和发布版本读取固定快照，供运行时使用一致的已发布配置。
     *
     * @param sessionId 会话 ID，限定快照所属的运行时会话
     * @param viewId 视图 ID，限定快照对应的嵌入式视图
     * @param releaseId 发布版本 ID，避免运行中切换到其他配置版本
     * @return 固定版本的发布快照；没有匹配记录时返回 {@code null}
     */
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
                row.configJson(), row.actorDisplayName(), row.uiLocale(), row.uiTheme(),
                row.uiFormPresentation());
    }
}
