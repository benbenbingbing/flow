package com.workflow.service;

import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.entity.UiConfigRelease;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class EntityListPublishedRuntimeService {

    private final UiConfigReleaseService releaseService;
    private final JsonDocumentCodec codec;

    public EntityListConfig resolveConfig(EntityListConfig draft) {
        if (draft == null) {
            return null;
        }
        UiConfigRelease active =
                releaseService.active(UiConfigReleaseService.LIST, draft.getId());
        if (active == null) {
            return draft;
        }
        EntityListConfigDTO snapshot =
                releaseService.resolveRuntimeList(draft.getId());
        if (snapshot == null) {
            return draft;
        }
        EntityListConfig config = new EntityListConfig();
        BeanUtils.copyProperties(snapshot, config);
        config.setToolbarConfig(write(snapshot.getToolbarConfig(), "发布工具栏配置"));
        config.setRowActionConfig(write(snapshot.getRowActionConfig(), "发布操作列配置"));
        config.setViewConfig(write(snapshot.getViewConfig(), "发布列表视图配置"));
        config.setAllowedScenes(write(snapshot.getAllowedScenes(), "发布允许场景"));
        config.setSelectionConfig(write(snapshot.getSelectionConfig(), "发布选择配置"));
        config.setFixedFilterConfig(write(snapshot.getFixedFilterConfig(), "发布固定条件"));
        config.setContextBindingConfig(
                write(snapshot.getContextBindingConfig(), "发布上下文绑定"));
        config.setActiveReleaseId(active.getId());
        config.setPublishedVersion(active.getVersion());
        config.setPublishedSnapshot(true);
        return config;
    }

    public List<EntityListField> resolveFields(
            EntityListConfig config,
            List<EntityListField> fallback) {
        if (config == null || !Boolean.TRUE.equals(config.getPublishedSnapshot())) {
            return fallback;
        }
        EntityListConfigDTO snapshot =
                releaseService.resolveRuntimeList(config.getId());
        return snapshot == null || snapshot.getFields() == null
                ? fallback : snapshot.getFields();
    }

    public List<Map<String, Object>> resolveToolbar(
            EntityListConfig config,
            List<Map<String, Object>> fallback) {
        if (config == null || !Boolean.TRUE.equals(config.getPublishedSnapshot())) {
            return fallback;
        }
        EntityListConfigDTO snapshot =
                releaseService.resolveRuntimeList(config.getId());
        return snapshot == null || snapshot.getToolbarConfig() == null
                ? fallback : snapshot.getToolbarConfig();
    }

    public List<Map<String, Object>> resolveRowActions(
            EntityListConfig config,
            List<Map<String, Object>> fallback) {
        if (config == null || !Boolean.TRUE.equals(config.getPublishedSnapshot())) {
            return fallback;
        }
        EntityListConfigDTO snapshot =
                releaseService.resolveRuntimeList(config.getId());
        return snapshot == null || snapshot.getRowActionConfig() == null
                ? fallback : snapshot.getRowActionConfig();
    }

    public List<String> resolveScenes(
            EntityListConfig config,
            List<String> fallback) {
        if (config == null || !Boolean.TRUE.equals(config.getPublishedSnapshot())) {
            return fallback;
        }
        EntityListConfigDTO snapshot =
                releaseService.resolveRuntimeList(config.getId());
        return snapshot == null || snapshot.getAllowedScenes() == null
                ? fallback : snapshot.getAllowedScenes();
    }

    private String write(Object value, String label) {
        return value == null ? null : codec.write(value, label);
    }
}
