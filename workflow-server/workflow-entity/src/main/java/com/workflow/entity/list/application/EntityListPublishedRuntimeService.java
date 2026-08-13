package com.workflow.entity.list.application;

import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.logging.LogValue;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 实体列表发布运行时解析服务，只允许使用已发布快照。
 *
 * <p>工具栏、行按钮、字段、场景等配置均从发布版本读取，保证草稿修改不会进入运行时。</p>
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
     * @return 运行时列表配置
     */
    public EntityListConfig resolveConfig(EntityListConfig draft) {
        return resolveConfig(draft, null, null, null);
    }

    /**
     * 按当前 ACTIVE 或父表单签名上下文中的固定版本解析列表。
     */
    public EntityListConfig resolveConfig(
            EntityListConfig draft,
            String releaseId,
            Integer releaseVersion,
            String releaseResolutionToken) {
        if (draft == null) {
            return null;
        }
        UiConfigReleaseService.ResolvedEntityListRelease resolved =
                releaseService.resolveRuntimeListRelease(
                        draft.getId(),
                        releaseId,
                        releaseVersion,
                        releaseResolutionToken);
        EntityListConfigDTO snapshot = resolved.list();
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
        config.setActiveReleaseId(resolved.releaseId());
        config.setPublishedVersion(resolved.releaseVersion());
        config.setPublishedSnapshot(true);
        config.setRuntimeFields(snapshot.getFields() == null
                ? List.of() : List.copyOf(snapshot.getFields()));
        config.setPinnedRelease(resolved.pinned());
        config.setReleaseResolutionToken(
                releaseResolutionToken);
        log.info(
                "列表运行时解析完成: listId={}, listKey={}, releaseId={}, releaseVersion={}, source={}",
                LogValue.safe(config.getId()),
                LogValue.safe(config.getListKey()),
                LogValue.safe(resolved.releaseId()),
                resolved.releaseVersion(),
                resolved.pinned() ? "PINNED" : "ACTIVE");
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
        return config.getRuntimeFields() == null
                ? List.of() : config.getRuntimeFields();
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
        List<Map<String, Object>> buttons = readMapList(
                config.getToolbarConfig(),
                "发布工具栏配置");
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
        List<Map<String, Object>> buttons = readMapList(
                config.getRowActionConfig(),
                "发布行按钮配置");
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
        if (!StringUtils.hasText(config.getAllowedScenes())) {
            return List.of();
        }
        return codec.readArray(
                        config.getAllowedScenes(),
                        "发布允许场景")
                .stream()
                .map(String::valueOf)
                .toList();
    }

    private String write(Object value, String label) {
        return value == null ? null : codec.write(value, label);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readMapList(
            String document,
            String label) {
        if (!StringUtils.hasText(document)) {
            return List.of();
        }
        return codec.readArray(document, label).stream()
                .filter(Map.class::isInstance)
                .map(value -> (Map<String, Object>) value)
                .toList();
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
