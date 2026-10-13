package com.workflow.entity.list.application;

import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.FormCoordinate;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.ListCoordinate;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.ListNode;
import com.workflow.contracts.embed.runtime.port.EmbedNativeListDependencySnapshotPort.Reference;
import com.workflow.contracts.embed.runtime.port.EmbedNativeListDependencySnapshotPort;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.core.logging.LogValue;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final JsonDocumentCodec codec;
    private final UiConfigReleaseMapper releaseMapper;
    private final ObjectMapper objectMapper;
    private final EmbedNativeListDependencySnapshotPort
            listDependencySnapshotPort;

    /**
     * 解析列表运行时配置，存在发布版本时用发布快照覆盖草稿。
     *
     * @param draft 草稿列表配置，为空返回 null
     * @return 运行时列表配置
     */
    public EntityListConfig resolveConfig(EntityListConfig draft) {
        return resolveConfig(draft, null, Map.of());
    }

    /**
     * 按当前已发布配置解析列表。
     *
     * @param draft 草稿，作为 {@code releaseService.resolveRuntimeListRelease} 的输入影响后续处理
     * @param releaseId 发布版本ID，后续用于解析配置时定位或关联目标
     * @param releaseVersion 发布版本，供本方法解析配置时使用
     * @return 解析后的配置结果，供调用方继续处理
     */
    public EntityListConfig resolveConfig(
            EntityListConfig draft,
            String configHash,
            Map<String, Object> runtimeContext) {
        if (draft == null) {
            return null;
        }
        UiConfigReleaseService.ResolvedEntityListRelease resolved =
                releaseService.resolveRuntimeListRelease(
                        draft.getId(), configHash, runtimeContext);
        EntityListConfig config = runtimeConfig(
                resolved.list(),
                resolved.releaseId(),
                resolved.releaseVersion(),
                resolved.pinned());
        config.setRuntimeContext(runtimeContext == null ? Map.of() : runtimeContext);
        config.setViewCompositions(viewCompositions(resolved.snapshot()));
        return config;
    }

    /**
     * 使用已经验证的关联内容列表上下文读取精确钉定版本。
     *
     * <p>公开列表 API 不能凭 releaseId 读取历史版本；调用方必须先校验
     * 服务端关联内容来源和访问权限，再进入本服务。</p>
     *
     * @param draft 草稿，供本方法解析视图组合配置时使用
     * @param releaseId 发布版本ID，后续用于解析视图组合配置时定位或关联目标
     * @param releaseVersion 发布版本，供本方法解析视图组合配置时使用
     * @return 解析后的视图组合配置结果，供调用方继续处理
     */
    public EntityListConfig resolveViewCompositionConfig(
            EntityListConfig draft,
            String releaseId,
            Integer releaseVersion) {
        if (draft == null
                || !StringUtils.hasText(releaseId)
                || releaseVersion == null) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_LIST_RELEASE_REQUIRED",
                    "关联内容目标列表缺少固定发布版本");
        }
        UiConfigRelease release = releaseMapper.selectById(releaseId);
        if (release == null
                || !"LIST".equals(release.getConfigType())
                || !draft.getId().equals(release.getConfigId())
                || !releaseVersion.equals(release.getVersion())) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_LIST_RELEASE_CONFLICT",
                    "关联内容目标列表固定版本不存在或不一致");
        }
        Map<String, Object> snapshot =
                releaseService.verifiedReleaseSnapshot(release);
        Object listDocument = snapshot.get("list");
        if (listDocument == null) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_LIST_RELEASE_CONFLICT",
                    "关联内容目标列表发布快照缺少列表文档");
        }
        EntityListConfigDTO published = objectMapper.convertValue(
                listDocument,
                EntityListConfigDTO.class);
        if (!draft.getId().equals(published.getId())) {
            throw new BusinessConflictException(
                    "VIEW_COMPOSITION_LIST_RELEASE_CONFLICT",
                    "关联内容目标列表发布快照归属不一致");
        }
        EntityListConfig config = runtimeConfig(
                published,
                release.getId(),
                release.getVersion(),
                true);
        config.setViewCompositions(viewCompositions(snapshot));
        return config;
    }

    /**
     * 处理运行时配置，并将结果传给后续步骤。
     *
     * @param snapshot 快照，作为 {@code BeanUtils.copyProperties} 的输入影响后续处理
     * @param releaseId 发布版本ID，后续用于处理运行时配置时定位或关联目标
     * @param releaseVersion 发布版本，作为 {@code config.setPublishedVersion} 的输入影响后续处理
     * @param pinned 固定，作为 {@code config.setPinnedRelease} 的输入影响后续处理
     * @return 处理后的运行时配置结果，供调用方继续处理
     */
    private EntityListConfig runtimeConfig(
            EntityListConfigDTO snapshot,
            String releaseId,
            Integer releaseVersion,
            boolean pinned) {
        EntityListConfig config = new EntityListConfig();
        BeanUtils.copyProperties(snapshot, config);
        config.setToolbarConfig(write(snapshot.getToolbarConfig(), "发布工具栏配置"));
        config.setRowActionConfig(write(snapshot.getRowActionConfig(), "发布操作列配置"));
        config.setViewConfig(write(snapshot.getViewConfig(), "发布列表视图配置"));
        config.setSelectionConfig(write(snapshot.getSelectionConfig(), "发布选择配置"));
        config.setFixedFilterConfig(write(snapshot.getFixedFilterConfig(), "发布固定条件"));
        config.setActiveReleaseId(releaseId);
        config.setPublishedVersion(releaseVersion);
        config.setPublishedSnapshot(true);
        config.setRuntimeFields(snapshot.getFields() == null
                ? List.of() : List.copyOf(snapshot.getFields()));
        config.setPinnedRelease(pinned);
        UiConfigRelease accepted = releaseMapper.selectById(releaseId);
        config.setConfigHash(accepted == null ? null : accepted.getContentHash());
        log.info(
                "列表运行时解析完成: listId={}, listKey={}, releaseId={}, releaseVersion={}, source={}",
                LogValue.safe(config.getId()),
                LogValue.safe(config.getListKey()),
                LogValue.safe(releaseId),
                releaseVersion,
                pinned ? "PINNED" : "ACTIVE");
        return config;
    }

    /**
     * 关联内容只取自已完成哈希校验的发布快照，绝不从列表草稿补齐。
     *
     * @param snapshot 快照，作为 {@code objectMapper.convertValue} 的输入影响后续处理
     * @return 实体列表已发布集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> viewCompositions(
            Map<String, Object> snapshot) {
        if (snapshot == null
                || !(snapshot.get("viewCompositions")
                instanceof List<?>)) {
            return List.of();
        }
        List<Map<String, Object>> values = objectMapper.convertValue(
                snapshot.get("viewCompositions"),
                new TypeReference<List<Map<String, Object>>>() {});
        return values == null ? List.of() : List.copyOf(values);
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
                config, "TOOLBAR", buttons);
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
                config, "ROW_ACTION", buttons);
    }

    /**
     * 写入实体列表已发布运行时；后续读取或执行将使用更新后的状态。
     *
     * @param value 待写入实体列表已发布运行时的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于写入实体列表已发布运行时时匹配或展示
     * @return 写入后的实体列表已发布运行时文本，供调用方比较或展示
     */
    private String write(Object value, String label) {
        return value == null ? null : codec.write(value, label);
    }

    /**
     * 读取映射列表；查询结果供调用方展示或继续处理。
     *
     * @param document 文档，作为 {@code codec.readArray} 的输入影响后续处理
     * @param label 标签，后续用于读取映射列表时匹配或展示
     * @return 实体列表已发布集合，供调用方遍历或展示
     */
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

    /**
     * 整理授权固定目标表单集合数据，供调用方遍历或继续处理。
     *
     * @param listId 列表ID，后续用于处理授权固定目标表单集合时定位或关联目标
     * @param buttonArea 按钮{@code area}，供本方法处理授权固定目标表单集合时使用
     * @param buttons 按钮集合，供本方法处理授权固定目标表单集合时使用
     * @return 实体列表已发布集合，供调用方遍历或展示
     */
    private List<Map<String, Object>> authorizePinnedTargetForms(
            EntityListConfig config, String buttonArea, List<Map<String, Object>> buttons) {
        if (buttons == null) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> source : buttons) {
            Map<String, Object> button = new java.util.LinkedHashMap<>(source);
            // 前端只消费稳定目标身份和变更摘要；执行目标仍从服务端按钮配置重新解析。
            Map<String, Object> runtimeContext = new java.util.LinkedHashMap<>(
                    config.getRuntimeContext() == null ? Map.of() : config.getRuntimeContext());
            runtimeContext.put("listId", config.getId());
            runtimeContext.put("listKey", config.getListKey());
            runtimeContext.put("entityCode", config.getEntityCode());
            runtimeContext.put("buttonId", button.containsKey("id") ? button.get("id") : button.get("key"));
            runtimeContext.put("buttonArea", buttonArea);
            button.put("runtimeContext", runtimeContext);
            String formId = text(button.get("targetFormId"));
            if (StringUtils.hasText(formId)) {
                button.put("targetFormConfigHash", releaseService.formConfigHash(formId));
            }
            button.remove("targetFormReleaseId");
            button.remove("targetFormReleaseVersion");
            result.add(button);
        }
        return List.copyOf(result);
    }

    private String text(Object value) { return value == null ? "" : String.valueOf(value); }
}
