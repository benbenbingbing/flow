package com.workflow.entity.list.application;

import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;

import com.workflow.core.logging.LogValue;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 实体列表发布运行时解析服务，优先使用 ACTIVE 发布快照覆盖草稿配置。
 *
 * <p>当列表存在已发布快照时，工具栏、行按钮、字段、场景等配置均从发布版本读取，
 * 保证运行时与发布版本一致；无发布版本时回退到传入的草稿配置。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class EntityListPublishedRuntimeService {

    private final UiConfigReleaseService releaseService;
    private final UiReleaseResolutionTokenService resolutionTokenService;
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
            log.info(
                    "列表运行时使用草稿配置: listId={}, listKey={}, reason=NO_ACTIVE_RELEASE",
                    LogValue.safe(draft.getId()),
                    LogValue.safe(draft.getListKey()));
            return draft;
        }
        EntityListConfigDTO snapshot =
                releaseService.resolveRuntimeList(draft.getId());
        if (snapshot == null) {
            log.info(
                    "列表运行时回退草稿配置: listId={}, listKey={}, activeReleaseId={}, activeVersion={}, reason=EMPTY_RELEASE_SNAPSHOT",
                    LogValue.safe(draft.getId()),
                    LogValue.safe(draft.getListKey()),
                    LogValue.safe(active.getId()),
                    active.getVersion());
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
        log.info(
                "列表运行时解析完成: listId={}, listKey={}, releaseId={}, releaseVersion={}, source=PUBLISHED",
                LogValue.safe(config.getId()),
                LogValue.safe(config.getListKey()),
                LogValue.safe(active.getId()),
                active.getVersion());
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
        List<Map<String, Object>> buttons =
                snapshot == null || snapshot.getToolbarConfig() == null
                        ? fallback : snapshot.getToolbarConfig();
        return authorizePinnedTargetForms(
                config.getId(),
                "TOOLBAR",
                buttons);
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
        List<Map<String, Object>> buttons =
                snapshot == null || snapshot.getRowActionConfig() == null
                        ? fallback : snapshot.getRowActionConfig();
        return authorizePinnedTargetForms(
                config.getId(),
                "ROW_ACTION",
                buttons);
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

    private List<Map<String, Object>> authorizePinnedTargetForms(
            String listId,
            String buttonArea,
            List<Map<String, Object>> buttons) {
        if (buttons == null || buttons.isEmpty()) {
            return buttons == null ? List.of() : buttons;
        }
        List<Map<String, Object>> result =
                new ArrayList<>(buttons.size());
        int explicitFormCount = 0;
        int authorizedCount = 0;
        int incompleteCount = 0;
        for (Map<String, Object> source : buttons) {
            Map<String, Object> button =
                    new java.util.LinkedHashMap<>(
                            source == null ? Map.of() : source);
            String formId = text(button.get("targetFormId"));
            String releaseId =
                    text(button.get("targetFormReleaseId"));
            Integer releaseVersion =
                    integer(button.get(
                            "targetFormReleaseVersion"));
            if (StringUtils.hasText(formId)) {
                explicitFormCount++;
            }
            if (!StringUtils.hasText(formId)
                    || !StringUtils.hasText(releaseId)
                    || releaseVersion == null) {
                if (StringUtils.hasText(formId)) {
                    incompleteCount++;
                }
                result.add(button);
                continue;
            }
            String token = resolutionTokenService.issue(
                    UiRuntimeResolutionContext.standalone(),
                    formId,
                    releaseId,
                    releaseVersion,
                    0);
            if (StringUtils.hasText(token)) {
                button.put(
                        "targetFormReleaseResolutionToken",
                        token);
                authorizedCount++;
            }
            result.add(button);
        }
        if (explicitFormCount > 0) {
            log.info(
                    "列表显式表单按钮授权完成: listId={}, area={}, buttonCount={}, explicitFormCount={}, authorizedCount={}, incompleteCount={}",
                    LogValue.safe(listId),
                    LogValue.safe(buttonArea),
                    buttons.size(),
                    explicitFormCount,
                    authorizedCount,
                    incompleteCount);
        }
        return List.copyOf(result);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (!StringUtils.hasText(text(value))) {
            return null;
        }
        try {
            return Integer.parseInt(text(value));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
