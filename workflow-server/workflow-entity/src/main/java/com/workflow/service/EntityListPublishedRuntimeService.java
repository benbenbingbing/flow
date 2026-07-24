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

/**
 * 实体列表发布运行时解析服务，优先使用 ACTIVE 发布快照覆盖草稿配置。
 *
 * <p>当列表存在已发布快照时，工具栏、行按钮、字段、场景等配置均从发布版本读取，
 * 保证运行时与发布版本一致；无发布版本时回退到传入的草稿配置。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityListPublishedRuntimeService {

    private final UiConfigReleaseService releaseService;
    private final JsonDocumentCodec codec;

    /**
     * 解析列表运行时配置，存在发布版本时用发布快照覆盖草稿。
     *
     * @param draft 草稿列表配置，为空返回 null
     * @return 运行时列表配置，无发布版本时返回草稿
     */
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

    /**
     * 解析列表字段，发布快照存在时返回快照字段，否则回退到传入字段。
     *
     * @param config   列表配置
     * @param fallback 草稿字段回退列表
     * @return 运行时字段列表
     */
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

    /**
     * 解析工具栏按钮，发布快照存在时返回快照工具栏，否则回退。
     *
     * @param config   列表配置
     * @param fallback 草稿工具栏回退列表
     * @return 运行时工具栏按钮列表
     */
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

    /**
     * 解析行内按钮，发布快照存在时返回快照行按钮，否则回退。
     *
     * @param config   列表配置
     * @param fallback 草稿行按钮回退列表
     * @return 运行时行按钮列表
     */
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

    /**
     * 解析允许场景，发布快照存在时返回快照场景，否则回退。
     *
     * @param config   列表配置
     * @param fallback 草稿场景回退列表
     * @return 运行时允许场景列表
     */
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
