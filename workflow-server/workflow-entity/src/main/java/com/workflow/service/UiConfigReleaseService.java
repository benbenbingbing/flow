package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.common.UserContext;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.EntityListConfigDTO;
import com.workflow.dto.UiConfigDiffDTO;
import com.workflow.dto.UiConfigDiffItemDTO;
import com.workflow.entity.EntityForm;
import com.workflow.entity.EntityFormField;
import com.workflow.entity.EntityFormNode;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListField;
import com.workflow.entity.UiComponentTemplate;
import com.workflow.entity.UiComponentTemplateVersion;
import com.workflow.entity.UiConfigRelease;
import com.workflow.mapper.EntityFormMapper;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.UiConfigReleaseMapper;
import com.workflow.mapper.UiComponentTemplateMapper;
import com.workflow.mapper.UiComponentTemplateVersionMapper;
import com.workflow.mapper.UiDataSourceDefinitionMapper;
import com.workflow.service.config.EntityListConfigurationValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * UI 配置发布服务，负责表单与列表草稿的快照构建、发布、激活、差异比对与运行时解析。
 *
 * <p>发布时构建草稿快照并校验节点树、模板引用、扩展引用和数据源引用，
 * 计算内容哈希保证完整性；支持版本激活回滚、草稿与发布版本差异比对，
 * 以及运行时表单/列表发布版本的解析与完整性校验。</p>
 */
@Service
@RequiredArgsConstructor
public class UiConfigReleaseService {

    /** 表单配置类型。 */
    public static final String FORM = "FORM";
    /** 列表配置类型。 */
    public static final String LIST = "LIST";
    private static final int MAX_FORM_DEPTH = 8;
    private static final Set<String> FORM_NODE_TYPES = Set.of(
            "SECTION", "GRID", "TAB_SET", "TAB", "COLLAPSE",
            "TEXT", "FIELD", "SUB_FORM", "REPEATER", "ACTION_SLOT");
    private static final Set<String> FORM_CONTAINER_TYPES = Set.of(
            "SECTION", "GRID", "TAB_SET", "TAB", "COLLAPSE",
            "SUB_FORM", "REPEATER");
    private static final Set<String> STANDARD_CONTAINER_CHILD_TYPES = Set.of(
            "SECTION", "GRID", "TAB_SET", "COLLAPSE",
            "TEXT", "FIELD", "SUB_FORM", "REPEATER", "ACTION_SLOT");
    private static final Map<String, Set<String>> ALLOWED_CHILD_TYPES = Map.of(
            "SECTION", STANDARD_CONTAINER_CHILD_TYPES,
            "GRID", STANDARD_CONTAINER_CHILD_TYPES,
            "TAB_SET", Set.of("TAB"),
            "TAB", STANDARD_CONTAINER_CHILD_TYPES,
            "COLLAPSE", STANDARD_CONTAINER_CHILD_TYPES,
            "SUB_FORM", STANDARD_CONTAINER_CHILD_TYPES,
            "REPEATER", STANDARD_CONTAINER_CHILD_TYPES);
    private static final Map<String, Set<String>> TEMPLATE_NODE_TYPES = Map.of(
            "FIELD_GROUP", Set.of("SECTION", "GRID", "TAB", "COLLAPSE"),
            "FORM_SECTION", Set.of(
                    "SECTION", "GRID", "TAB_SET", "TAB", "COLLAPSE"),
            "SUB_FORM", Set.of("SUB_FORM", "REPEATER"));
    private static final Set<String> VOLATILE_SNAPSHOT_KEYS = Set.of(
            "revision",
            "activeReleaseId",
            "draftHash",
            "publishedVersion",
            "publishedSnapshot",
            "toolbarCapabilities",
            "createTime",
            "updateTime",
            "createdAt",
            "updatedAt",
            "deleted");

    private final UiConfigReleaseMapper releaseMapper;
    private final UiDataSourceDefinitionMapper dataSourceMapper;
    private final UiComponentTemplateMapper templateMapper;
    private final UiComponentTemplateVersionMapper templateVersionMapper;
    private final EntityFormMapper formMapper;
    private final EntityListConfigMapper listConfigMapper;
    private final EntityFormService formService;
    private final EntityFormNodeService formNodeService;
    private final UiExtensionDefinitionService extensionDefinitionService;
    private final EntityListConfigService listConfigService;
    private final EntityListConfigurationValidator listConfigurationValidator;
    private final JsonDocumentCodec codec;
    private final ObjectMapper objectMapper;

    /**
     * 查询指定配置的所有发布历史记录。
     *
     * @param configType 配置类型（FORM 或 LIST）
     * @param configId   配置ID
     * @return 发布记录列表
     */
    public List<UiConfigRelease> releases(String configType, String configId) {
        requireType(configType);
        return releaseMapper.findReleases(configType, configId);
    }

    /**
     * 查询指定配置当前激活的发布记录。
     *
     * @param configType 配置类型
     * @param configId   配置ID
     * @return 激活的发布记录，不存在返回 null
     */
    public UiConfigRelease active(String configType, String configId) {
        requireType(configType);
        return releaseMapper.findActive(configType, configId);
    }

    /**
     * 读取当前激活发布版本的快照 Map。
     *
     * @param configType 配置类型
     * @param configId   配置ID
     * @return 快照 Map，不存在激活版本返回 null
     */
    public Map<String, Object> activeSnapshot(String configType, String configId) {
        UiConfigRelease release = active(configType, configId);
        return release == null
                ? null
                : codec.readObject(release.getSnapshotDocument(), "UI发布快照");
    }

    /**
     * 解析表单运行时发布版本，返回发布元信息与已校验快照文档。
     *
     * @param formId          表单ID
     * @param releaseId       发布记录ID，为空取当前激活版本
     * @param expectedVersion 期望版本号，为空跳过校验
     * @return 包含 id、configId、version、contentHash、snapshotDocument 的 Map
     * @throws IllegalArgumentException 发布版本不存在或版本号不一致时抛出
     */
    public Map<String, Object> runtimeFormRelease(
            String formId,
            String releaseId,
            Integer expectedVersion) {
        UiConfigRelease release = StringUtils.hasText(releaseId)
                ? releaseMapper.selectById(releaseId)
                : releaseMapper.findActive(FORM, formId);
        if (release == null
                || !FORM.equals(release.getConfigType())
                || !Objects.equals(formId, release.getConfigId())) {
            throw new IllegalArgumentException("表单运行时发布版本不存在");
        }
        if (expectedVersion != null
                && !Objects.equals(expectedVersion, release.getVersion())) {
            throw new IllegalArgumentException("表单运行时发布版本号不一致");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", release.getId());
        result.put("configId", release.getConfigId());
        result.put("version", release.getVersion());
        result.put("contentHash", release.getContentHash());
        result.put("snapshotDocument", verifiedSnapshot(release));
        return result;
    }

    /**
     * 构建配置的草稿快照（不落库），用于差异比对与发布预览。
     *
     * @param configType 配置类型
     * @param configId   配置ID
     * @return 草稿快照 Map
     * @throws IllegalArgumentException 配置不存在时抛出
     */
    public Map<String, Object> draftSnapshot(String configType, String configId) {
        return buildDraftSnapshot(configType, configId);
    }

    /**
     * 比较草稿快照与当前激活发布快照的差异。
     *
     * @param configType 配置类型
     * @param configId   配置ID
     * @return 差异 DTO，包含是否变化、变化区块与明细
     */
    public UiConfigDiffDTO diff(String configType, String configId) {
        Map<String, Object> draft = buildDraftSnapshot(configType, configId);
        String draftDocument = canonical(draft);
        String draftHash = hash(draftDocument);
        UiConfigRelease active = active(configType, configId);
        Map<String, Object> activeSnapshot = active == null
                ? Map.of()
                : stableMap(codec.readObject(
                        active.getSnapshotDocument(), "UI发布快照"));
        String activeHash = active == null
                ? null
                : hash(canonical(activeSnapshot));
        List<String> changedSections = new ArrayList<>();
        for (String key : draft.keySet()) {
            if (!equivalent(draft.get(key), activeSnapshot.get(key))) {
                changedSections.add(key);
            }
        }
        return UiConfigDiffDTO.builder()
                .configType(configType)
                .configId(configId)
                .draftHash(draftHash)
                .activeHash(activeHash)
                .changed(active == null || !draftHash.equals(activeHash))
                .changedSections(changedSections)
                .changedItems(detailedChanges(
                        configType,
                        draft,
                        activeSnapshot,
                        changedSections))
                .build();
    }

    private List<UiConfigDiffItemDTO> detailedChanges(
            String configType,
            Map<String, Object> draft,
            Map<String, Object> active,
            List<String> changedSections) {
        List<UiConfigDiffItemDTO> changes = new ArrayList<>();
        if (FORM.equals(configType)) {
            appendObjectChange(
                    changes,
                    "form",
                    "form",
                    "表单设置",
                    mapValue(draft.get("form")),
                    mapValue(active.get("form")));
            appendCollectionChanges(
                    changes,
                    "nodes",
                    "节点",
                    mapList(draft.get("nodes")),
                    mapList(active.get("nodes")),
                    List.of("id", "nodeKey"),
                    List.of("label", "fieldLabel", "fieldName", "nodeKey"),
                    true);
            return changes;
        }

        Map<String, Object> draftList = mapValue(draft.get("list"));
        Map<String, Object> activeList = mapValue(active.get("list"));
        appendObjectChange(
                changes,
                "list",
                "list",
                "列表设置",
                withoutKeys(draftList, Set.of(
                        "fields", "toolbarConfig", "rowActionConfig", "allowedScenes")),
                withoutKeys(activeList, Set.of(
                        "fields", "toolbarConfig", "rowActionConfig", "allowedScenes")));
        appendCollectionChanges(
                changes,
                "fields",
                "列表字段",
                mapList(draftList.get("fields")),
                mapList(activeList.get("fields")),
                List.of("id", "fieldCode"),
                List.of("fieldLabel", "fieldName", "fieldCode"),
                true);
        appendCollectionChanges(
                changes,
                "toolbarActions",
                "工具栏按钮",
                mapList(draftList.get("toolbarConfig")),
                mapList(activeList.get("toolbarConfig")),
                List.of("id", "key", "actionCode"),
                List.of("label", "name", "key", "actionCode"),
                true);
        appendCollectionChanges(
                changes,
                "rowActions",
                "行按钮",
                mapList(draftList.get("rowActionConfig")),
                mapList(activeList.get("rowActionConfig")),
                List.of("id", "key", "actionCode"),
                List.of("label", "name", "key", "actionCode"),
                true);
        appendValueCollectionChanges(
                changes,
                "allowedScenes",
                "列表场景",
                draftList.get("allowedScenes"),
                activeList.get("allowedScenes"));
        if (changes.isEmpty() && !changedSections.isEmpty()) {
            changes.add(UiConfigDiffItemDTO.builder()
                    .section("list")
                    .id("list")
                    .label("列表草稿")
                    .changeType("UPDATED")
                    .changedFields(changedSections)
                    .build());
        }
        return changes;
    }

    private void appendObjectChange(
            List<UiConfigDiffItemDTO> changes,
            String section,
            String id,
            String label,
            Map<String, Object> draft,
            Map<String, Object> active) {
        if (equivalent(draft, active)) {
            return;
        }
        changes.add(UiConfigDiffItemDTO.builder()
                .section(section)
                .id(id)
                .label(label)
                .changeType(active.isEmpty() ? "ADDED" : "UPDATED")
                .changedFields(changedKeys(draft, active))
                .build());
    }

    private void appendCollectionChanges(
            List<UiConfigDiffItemDTO> changes,
            String section,
            String defaultLabel,
            List<Map<String, Object>> draftItems,
            List<Map<String, Object>> activeItems,
            List<String> idKeys,
            List<String> labelKeys,
            boolean supportsMove) {
        Map<String, Map<String, Object>> draftById =
                indexByStableId(draftItems, idKeys);
        Map<String, Map<String, Object>> activeById =
                indexByStableId(activeItems, idKeys);
        for (Map.Entry<String, Map<String, Object>> entry : draftById.entrySet()) {
            String id = entry.getKey();
            Map<String, Object> draft = entry.getValue();
            Map<String, Object> active = activeById.remove(id);
            if (active == null) {
                changes.add(itemChange(
                        section, id, itemLabel(draft, labelKeys, defaultLabel),
                        "ADDED", List.of()));
                continue;
            }
            if (equivalent(draft, active)) {
                continue;
            }
            List<String> changedFields = changedKeys(draft, active);
            boolean moved = supportsMove
                    && !changedFields.isEmpty()
                    && changedFields.stream().allMatch(field ->
                            "parentId".equals(field) || "orderKey".equals(field));
            changes.add(itemChange(
                    section,
                    id,
                    itemLabel(draft, labelKeys, defaultLabel),
                    moved ? "MOVED" : "UPDATED",
                    changedFields));
        }
        activeById.forEach((id, active) -> changes.add(itemChange(
                section,
                id,
                itemLabel(active, labelKeys, defaultLabel),
                "REMOVED",
                List.of())));
    }

    private void appendValueCollectionChanges(
            List<UiConfigDiffItemDTO> changes,
            String section,
            String label,
            Object draft,
            Object active) {
        Set<String> draftValues = textSet(draft);
        Set<String> activeValues = textSet(active);
        for (String value : draftValues) {
            if (!activeValues.remove(value)) {
                changes.add(itemChange(
                        section, value, value, "ADDED", List.of()));
            }
        }
        activeValues.forEach(value -> changes.add(itemChange(
                section, value, value, "REMOVED", List.of())));
    }

    private UiConfigDiffItemDTO itemChange(
            String section,
            String id,
            String label,
            String changeType,
            List<String> changedFields) {
        return UiConfigDiffItemDTO.builder()
                .section(section)
                .id(id)
                .label(label)
                .changeType(changeType)
                .changedFields(changedFields)
                .build();
    }

    private Map<String, Map<String, Object>> indexByStableId(
            List<Map<String, Object>> items,
            List<String> idKeys) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Map<String, Object> item : items) {
            String id = firstText(idKeys.stream()
                    .map(item::get)
                    .map(this::text)
                    .toArray(String[]::new));
            if (StringUtils.hasText(id)) {
                result.put(id, item);
            }
        }
        return result;
    }

    private String itemLabel(
            Map<String, Object> item,
            List<String> labelKeys,
            String fallback) {
        List<String> labels = new ArrayList<>(labelKeys.stream()
                .map(item::get)
                .map(this::text)
                .toList());
        labels.add(fallback);
        return firstText(labels.toArray(String[]::new));
    }

    private List<String> changedKeys(
            Map<String, Object> draft,
            Map<String, Object> active) {
        Set<String> keys = new LinkedHashSet<>();
        keys.addAll(draft.keySet());
        keys.addAll(active.keySet());
        return keys.stream()
                .filter(key -> !equivalent(draft.get(key), active.get(key)))
                .sorted()
                .toList();
    }

    private Map<String, Object> withoutKeys(
            Map<String, Object> source,
            Set<String> ignoredKeys) {
        Map<String, Object> result = new LinkedHashMap<>(source);
        ignoredKeys.forEach(result::remove);
        return result;
    }

    private Map<String, Object> mapValue(Object source) {
        if (!(source instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private List<Map<String, Object>> mapList(Object source) {
        if (!(source instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            Map<String, Object> value = mapValue(item);
            if (!value.isEmpty()) {
                result.add(value);
            }
        }
        return result;
    }

    private Set<String> textSet(Object source) {
        if (!(source instanceof List<?> list)) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object value : list) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                result.add(text);
            }
        }
        return result;
    }

    /**
     * 发布配置草稿快照，校验完整性后生成新版本并激活；内容未变化时直接复用激活版本。
     *
     * @param configType  配置类型
     * @param configId    配置ID
     * @param description 发布描述
     * @return 发布记录
     * @throws IllegalArgumentException 配置不存在或快照校验失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public UiConfigRelease publish(
            String configType,
            String configId,
            String description) {
        lockOwner(configType, configId);
        Map<String, Object> snapshot = buildDraftSnapshot(configType, configId);
        validateForPublish(configType, configId, snapshot);
        String document = canonical(snapshot);
        String contentHash = hash(document);
        UiConfigRelease active = releaseMapper.findActive(
                configType,
                configId);
        if (active != null
                && Objects.equals(contentHash, active.getContentHash())) {
            activateOnOwner(
                    configType,
                    configId,
                    active,
                    active.getContentHash());
            return active;
        }
        List<UiConfigRelease> releases = releaseMapper.findReleases(configType, configId);
        int nextVersion = releases.isEmpty() ? 1 : releases.get(0).getVersion() + 1;
        deactivate(configType, configId);

        UiConfigRelease release = new UiConfigRelease();
        release.setConfigType(configType);
        release.setConfigId(configId);
        release.setVersion(nextVersion);
        release.setSnapshotDocument(document);
        release.setContentHash(contentHash);
        release.setStatus("ACTIVE");
        release.setDescription(blankToNull(description));
        release.setPublishedBy(UserContext.getUserId());
        release.setPublishedAt(LocalDateTime.now());
        releaseMapper.insert(release);
        activateOnOwner(configType, configId, release, contentHash);
        return release;
    }

    /**
     * 激活指定历史发布版本，校验快照完整性后切换激活状态。
     *
     * @param configType 配置类型
     * @param configId   配置ID
     * @param releaseId  要激活的发布记录ID
     * @return 激活的发布记录
     * @throws IllegalArgumentException 发布版本不存在或完整性校验失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public UiConfigRelease activate(
            String configType,
            String configId,
            String releaseId) {
        lockOwner(configType, configId);
        UiConfigRelease release = releaseMapper.selectById(releaseId);
        if (release == null
                || !configType.equals(release.getConfigType())
                || !configId.equals(release.getConfigId())) {
            throw new IllegalArgumentException("发布版本不存在");
        }
        Map<String, Object> snapshot = codec.readObject(
                release.getSnapshotDocument(), "待激活UI发布快照");
        String actualHash = hash(canonical(snapshot));
        if (!StringUtils.hasText(release.getContentHash())
                || !Objects.equals(release.getContentHash(), actualHash)) {
            throw new IllegalArgumentException("发布快照完整性校验失败，内容可能已被篡改");
        }
        validateSnapshotForActivation(configType, configId, snapshot);
        deactivate(configType, configId);
        UpdateWrapper<UiConfigRelease> releaseUpdate = new UpdateWrapper<>();
        releaseUpdate.eq("id", releaseId).set("status", "ACTIVE");
        releaseMapper.update(null, releaseUpdate);
        release.setStatus("ACTIVE");
        activateOnOwner(configType, configId, release, release.getContentHash());
        return release;
    }

    private void lockOwner(String configType, String configId) {
        requireType(configType);
        if (FORM.equals(configType)) {
            if (formMapper.selectByIdForUpdate(configId) == null) {
                throw new IllegalArgumentException("表单不存在");
            }
            return;
        }
        if (listConfigMapper.selectByIdForUpdate(configId) == null) {
            throw new IllegalArgumentException("列表配置不存在");
        }
    }

    /**
     * 解析表单运行时发布版本对应的表单对象（取当前激活版本）。
     *
     * @param formId 表单ID
     * @return 运行时表单对象
     */
    public EntityForm resolveRuntimeForm(String formId) {
        return resolveRuntimeFormRelease(formId).form();
    }

    /**
     * 解析表单运行时发布版本信息（取当前激活版本，非钉定）。
     *
     * @param formId 表单ID
     * @return 解析后的表单发布版本信息
     */
    public ResolvedEntityFormRelease resolveRuntimeFormRelease(
            String formId) {
        UiConfigRelease release = active(FORM, formId);
        if (release == null) {
            return new ResolvedEntityFormRelease(
                    formService.getById(formId),
                    null,
                    null);
        }
        return resolvedRuntimeForm(release, false);
    }

    /**
     * 解析指定发布版本的表单运行时对象，支持版本号一致性校验。
     *
     * @param formId          表单ID
     * @param releaseId       发布记录ID，为空取当前激活版本
     * @param expectedVersion 期望版本号，为空跳过校验
     * @return 运行时表单对象
     * @throws IllegalArgumentException 发布版本不存在或版本号不一致时抛出
     */
    public EntityForm resolveRuntimeForm(
            String formId,
            String releaseId,
            Integer expectedVersion) {
        return resolveRuntimeFormRelease(
                formId,
                releaseId,
                expectedVersion).form();
    }

    /**
     * 解析指定发布版本的表单运行时发布版本信息，支持版本号一致性校验。
     *
     * @param formId          表单ID
     * @param releaseId       发布记录ID，为空取当前激活版本
     * @param expectedVersion 期望版本号，为空跳过校验
     * @return 解析后的表单发布版本信息（钉定发布时 pinned 为 true）
     * @throws IllegalArgumentException 发布版本不存在或版本号不一致时抛出
     */
    public ResolvedEntityFormRelease resolveRuntimeFormRelease(
            String formId,
            String releaseId,
            Integer expectedVersion) {
        if (!StringUtils.hasText(releaseId)) {
            return resolveRuntimeFormRelease(formId);
        }
        UiConfigRelease release = releaseMapper.selectById(releaseId);
        if (release == null
                || !FORM.equals(release.getConfigType())
                || !Objects.equals(formId, release.getConfigId())) {
            throw new IllegalArgumentException("表单发布版本不存在或不属于当前表单");
        }
        if (expectedVersion != null
                && !Objects.equals(expectedVersion, release.getVersion())) {
            throw new IllegalArgumentException("表单发布版本号与流程快照不一致");
        }
        return resolvedRuntimeForm(release, true);
    }

    private ResolvedEntityFormRelease resolvedRuntimeForm(
            UiConfigRelease release,
            boolean pinned) {
        return new ResolvedEntityFormRelease(
                runtimeForm(verifiedSnapshot(release)),
                release.getId(),
                release.getVersion(),
                pinned);
    }

    private Map<String, Object> verifiedSnapshot(UiConfigRelease release) {
        Map<String, Object> snapshot = codec.readObject(
                release.getSnapshotDocument(), "UI发布快照");
        String actualHash = hash(canonical(snapshot));
        if (!StringUtils.hasText(release.getContentHash())
                || !Objects.equals(release.getContentHash(), actualHash)) {
            throw new IllegalArgumentException("发布快照完整性校验失败，内容可能已被篡改");
        }
        return snapshot;
    }

    /**
     * 校验并返回发布版本的快照 Map，确保内容哈希一致。
     *
     * @param release 发布记录，不能为空
     * @return 已校验的快照 Map
     * @throws IllegalArgumentException 发布记录为空或完整性校验失败时抛出
     */
    public Map<String, Object> verifiedReleaseSnapshot(
            UiConfigRelease release) {
        if (release == null) {
            throw new IllegalArgumentException("UI发布版本不能为空");
        }
        return verifiedSnapshot(release);
    }

    private EntityForm runtimeForm(Map<String, Object> snapshot) {
        EntityForm form = objectMapper.convertValue(
                snapshot.get("form"), EntityForm.class);
        form.setFields(objectMapper.convertValue(
                snapshot.getOrDefault("legacyFields", List.of()),
                new TypeReference<List<EntityFormField>>() {}));
        form.setNodes(objectMapper.convertValue(
                snapshot.getOrDefault("nodes", List.of()),
                new TypeReference<List<EntityFormNode>>() {}));
        return form;
    }

    /**
     * 解析列表运行时配置，优先取激活发布快照，回退到数据库草稿配置。
     *
     * @param listConfigId 列表配置ID
     * @return 运行时列表配置 DTO
     */
    public EntityListConfigDTO resolveRuntimeList(String listConfigId) {
        Map<String, Object> snapshot = activeSnapshot(LIST, listConfigId);
        return snapshot == null
                ? listConfigService.findById(listConfigId)
                : objectMapper.convertValue(
                        snapshot.get("list"), EntityListConfigDTO.class);
    }

    private Map<String, Object> buildDraftSnapshot(
            String configType,
            String configId) {
        requireType(configType);
        if (FORM.equals(configType)) {
            EntityForm form = formService.getById(configId);
            if (form == null) {
                throw new IllegalArgumentException("表单不存在");
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("schemaVersion", 1);
            snapshot.put("configType", FORM);
            snapshot.put("form", stableValue(formMetadata(form)));
            snapshot.put("nodes", stableValue(
                    form.getNodes() == null ? List.of() : form.getNodes()));
            snapshot.put("legacyFields", stableValue(deriveRuntimeFields(form)));
            return snapshot;
        }
        EntityListConfigDTO list = listConfigService.findById(configId);
        if (list == null) {
            throw new IllegalArgumentException("列表配置不存在");
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("schemaVersion", 1);
        snapshot.put("configType", LIST);
        snapshot.put("list", stableValue(list));
        return snapshot;
    }

    private Map<String, Object> formMetadata(EntityForm form) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("id", form.getId());
        metadata.put("entityId", form.getEntityId());
        metadata.put("formName", form.getFormName());
        metadata.put("formKey", form.getFormKey());
        metadata.put("description", form.getDescription());
        metadata.put("layoutType", form.getLayoutType());
        metadata.put("isDefault", form.getIsDefault());
        metadata.put("status", form.getStatus());
        metadata.put("customComponent", form.getCustomComponent());
        metadata.put(
                "customComponentVersion",
                form.getCustomComponentVersion());
        metadata.put(
                "customComponentSnapshotVersion",
                form.getCustomComponentSnapshotVersion());
        metadata.put("initConfig", form.getInitConfig());
        metadata.put(
                "dataSourceBindingsDocument",
                form.getDataSourceBindingsDocument());
        metadata.put("viewConfig", form.getViewConfig());
        return metadata;
    }

    private List<EntityFormField> deriveRuntimeFields(EntityForm form) {
        List<EntityFormField> existing =
                form.getFields() == null ? List.of() : form.getFields();
        Map<String, EntityFormField> byId = new HashMap<>();
        Map<String, EntityFormField> byCode = new HashMap<>();
        existing.forEach(field -> {
            byId.put(field.getId(), field);
            if (StringUtils.hasText(field.getFieldCode())) {
                byCode.put(field.getFieldCode(), field);
            }
        });
        List<EntityFormField> runtimeFields = new ArrayList<>();
        int sortOrder = 0;
        for (EntityFormNode node : form.getNodes() == null
                ? List.<EntityFormNode>of()
                : form.getNodes()) {
            if (!Set.of("FIELD", "SUB_FORM", "REPEATER")
                    .contains(node.getNodeType())) {
                continue;
            }
            Map<String, Object> props = StringUtils.hasText(node.getPropsDocument())
                    ? codec.readObject(node.getPropsDocument(), "发布表单节点属性")
                    : Map.of();
            String fieldCode = text(props.getOrDefault("fieldCode", node.getNodeKey()));
            EntityFormField field = new EntityFormField();
            EntityFormField previous = byId.get(node.getId());
            if (previous == null) {
                previous = byCode.get(fieldCode);
            }
            if (previous != null) {
                BeanUtils.copyProperties(previous, field);
            }
            field.setId(node.getId());
            field.setFormId(form.getId());
            if (props.containsKey("fieldId")) {
                field.setFieldId(text(props.get("fieldId")));
            }
            field.setFieldCode(fieldCode);
            if (props.containsKey("fieldName")) {
                field.setFieldName(text(props.get("fieldName")));
            }
            if (!StringUtils.hasText(field.getFieldName())) {
                field.setFieldName(text(props.get("label")));
            }
            field.setFieldLabel(text(props.getOrDefault("label", field.getFieldName())));
            if (props.containsKey("fieldType")) {
                field.setFieldType(text(props.get("fieldType")));
            }
            if (!StringUtils.hasText(field.getFieldType())) {
                field.setFieldType("REPEATER".equals(node.getNodeType())
                        ? "SUB_FORM_LIST" : node.getNodeType());
            }
            if (props.containsKey("componentType")) {
                field.setComponentType(text(props.get("componentType")));
            }
            if (!StringUtils.hasText(field.getComponentType())) {
                field.setComponentType(node.getNodeType().toLowerCase());
            }
            if (props.containsKey("placeholder")) {
                field.setPlaceholder(text(props.get("placeholder")));
            }
            if (props.containsKey("defaultValue")) {
                field.setDefaultValue(text(props.get("defaultValue")));
            }
            if (props.containsKey("gridSpan")) {
                field.setGridSpan(integer(props.get("gridSpan"), 24));
            } else if (field.getGridSpan() == null) {
                field.setGridSpan(24);
            }
            if (props.containsKey("required")) {
                field.setIsRequired(booleanFlag(props.get("required")));
            }
            if (props.containsKey("readonly")) {
                field.setIsReadonly(booleanFlag(props.get("readonly")));
            }
            if (props.containsKey("hidden")) {
                field.setIsHidden(booleanFlag(props.get("hidden")));
            }
            field.setSortOrder(sortOrder++);
            if (props.containsKey("componentProps")) {
                Object componentProps = props.get("componentProps");
                field.setComponentProps(componentProps == null
                        ? null : codec.write(componentProps, "发布字段组件属性"));
            }
            Map<String, Object> rules = StringUtils.hasText(node.getRulesDocument())
                    ? codec.readObject(node.getRulesDocument(), "发布表单节点规则")
                    : Map.of();
            if (rules.containsKey("validation")) {
                Object validation = rules.get("validation");
                field.setValidationRules(validation == null
                        ? null : codec.write(validation, "发布字段校验规则"));
            }
            if (rules.containsKey("extension")) {
                Object extension = rules.get("extension");
                field.setExtensionConfig(extension == null
                        ? null : codec.write(extension, "发布字段扩展配置"));
            }
            if (StringUtils.hasText(node.getDataSourceBindingsDocument())) {
                field.setDataSourceBindings(codec.readObject(
                        node.getDataSourceBindingsDocument(),
                        "发布字段数据源绑定"));
            }
            runtimeFields.add(field);
        }
        return runtimeFields.isEmpty() ? existing : runtimeFields;
    }

    private void validateForPublish(
            String configType,
            String configId,
            Map<String, Object> snapshot) {
        if (FORM.equals(configType)) {
            formNodeService.validateTree(configId);
            validateTemplateReferences(snapshot);
            validateExtensionReferences(snapshot);
            validateDataSourceReferences(snapshot);
            return;
        }
        EntityListConfigDTO list = objectMapper.convertValue(
                snapshot.get("list"), EntityListConfigDTO.class);
        listConfigurationValidator.validate(list);
        validateListTemplateReferences(list);
        validateDataSourceReferences(snapshot);
    }

    private void validateSnapshotForActivation(
            String configType,
            String configId,
            Map<String, Object> snapshot) {
        requireType(configType);
        if (FORM.equals(configType)) {
            validateFormSnapshotTree(configId, snapshot);
            validateTemplateReferences(snapshot);
            validateExtensionReferences(snapshot);
            validateDataSourceReferences(snapshot);
            return;
        }
        EntityListConfigDTO list = objectMapper.convertValue(
                snapshot.get("list"), EntityListConfigDTO.class);
        listConfigurationValidator.validate(list);
        validateListTemplateReferences(list);
        validateDataSourceReferences(snapshot);
    }

    private void validateExtensionReferences(Map<String, Object> snapshot) {
        EntityForm form = objectMapper.convertValue(
                snapshot.get("form"), EntityForm.class);
        List<EntityFormNode> nodes = objectMapper.convertValue(
                snapshot.getOrDefault("nodes", List.of()),
                new TypeReference<List<EntityFormNode>>() {});
        if (StringUtils.hasText(form.getCustomComponent())) {
            var definition = extensionDefinitionService.requireActive(
                    "FORM",
                    form.getCustomComponent(),
                    form.getCustomComponentVersion());
            extensionDefinitionService.validateCompatibility(
                    definition,
                    null,
                    null,
                    null,
                    form.getCustomComponentSnapshotVersion());
        }
        for (EntityFormNode node : nodes) {
            if (!StringUtils.hasText(node.getComponentName())) {
                continue;
            }
            var definition = extensionDefinitionService.requireActive(
                    "NODE",
                    node.getComponentName(),
                    node.getComponentVersion());
            extensionDefinitionService.validateCompatibility(
                    definition,
                    null,
                    node.getNodeType(),
                    node.getBindingType(),
                    node.getSnapshotVersion());
        }
    }

    private void validateTemplateReferences(Map<String, Object> snapshot) {
        List<EntityFormNode> nodes = snapshotNodes(snapshot);
        for (EntityFormNode node : nodes) {
            boolean hasTemplateId = StringUtils.hasText(node.getTemplateId());
            boolean hasTemplateVersion = node.getTemplateVersion() != null;
            if (!hasTemplateId && !hasTemplateVersion) {
                continue;
            }
            if (!hasTemplateId
                    || node.getTemplateVersion() == null
                    || node.getTemplateVersion() < 1) {
                throw new IllegalArgumentException(
                        "节点模板必须同时锁定 templateId 和 templateVersion: "
                                + nodeLabel(node));
            }
            UiComponentTemplate template =
                    templateMapper.selectById(node.getTemplateId());
            if (template == null
                    || Integer.valueOf(1).equals(template.getDeleted())
                    || !"ACTIVE".equalsIgnoreCase(template.getStatus())) {
                throw new IllegalArgumentException(
                        "节点引用的组件模板不存在或未启用: " + node.getTemplateId());
            }
            String templateType = normalize(template.getTemplateType());
            Set<String> compatibleTypes = TEMPLATE_NODE_TYPES.get(templateType);
            if (compatibleTypes == null
                    || !compatibleTypes.contains(normalize(node.getNodeType()))) {
                throw new IllegalArgumentException(
                        "组件模板类型 "
                                + templateType
                                + " 与节点类型 "
                                + node.getNodeType()
                                + " 不兼容: "
                                + nodeLabel(node));
            }
            UiComponentTemplateVersion version = templateVersionMapper.selectOne(
                    new LambdaQueryWrapper<UiComponentTemplateVersion>()
                            .eq(UiComponentTemplateVersion::getTemplateId,
                                    node.getTemplateId())
                            .eq(UiComponentTemplateVersion::getVersion,
                                    node.getTemplateVersion()));
            if (version == null) {
                throw new IllegalArgumentException(
                        "节点引用的组件模板版本不存在: "
                                + node.getTemplateId()
                                + "@"
                                + node.getTemplateVersion());
            }
            verifyTemplateVersionIntegrity(
                    version,
                    "节点 " + nodeLabel(node));
        }
    }

    private void validateListTemplateReferences(EntityListConfigDTO list) {
        if (list == null) {
            throw new IllegalArgumentException("列表发布快照不能为空");
        }
        for (EntityListField field : list.getFields() == null
                ? List.<EntityListField>of()
                : list.getFields()) {
            validateTemplateBinding(
                    field.getTemplateId(),
                    field.getTemplateVersion(),
                    "LIST_COLUMN_GROUP",
                    "列表字段 " + firstText(field.getFieldCode(), field.getId()));
        }
        validateListActionTemplateReferences(
                list.getToolbarConfig(),
                "工具栏按钮");
        validateListActionTemplateReferences(
                list.getRowActionConfig(),
                "行按钮");
    }

    private void validateListActionTemplateReferences(
            List<Map<String, Object>> actions,
            String positionLabel) {
        for (Map<String, Object> action : actions == null
                ? List.<Map<String, Object>>of()
                : actions) {
            validateTemplateBinding(
                    text(action.get("templateId")),
                    nullableInteger(action.get("templateVersion")),
                    "BUTTON_GROUP",
                    positionLabel
                            + " "
                            + firstText(
                                    text(action.get("key")),
                                    text(action.get("label"))));
        }
    }

    private void validateTemplateBinding(
            String templateId,
            Integer templateVersion,
            String requiredType,
            String referenceLabel) {
        boolean hasTemplateId = StringUtils.hasText(templateId);
        boolean hasTemplateVersion = templateVersion != null;
        if (!hasTemplateId && !hasTemplateVersion) {
            return;
        }
        if (!hasTemplateId || templateVersion == null || templateVersion < 1) {
            throw new IllegalArgumentException(
                    referenceLabel
                            + " 必须同时锁定 templateId 和 templateVersion");
        }
        UiComponentTemplate template = templateMapper.selectById(templateId);
        if (template == null
                || Integer.valueOf(1).equals(template.getDeleted())
                || !"ACTIVE".equalsIgnoreCase(template.getStatus())) {
            throw new IllegalArgumentException(
                    referenceLabel
                            + " 引用的组件模板不存在或未启用: "
                            + templateId);
        }
        if (!requiredType.equals(normalize(template.getTemplateType()))) {
            throw new IllegalArgumentException(
                    referenceLabel
                            + " 必须绑定 "
                            + requiredType
                            + " 模板，实际为 "
                            + template.getTemplateType());
        }
        UiComponentTemplateVersion version = templateVersionMapper.selectOne(
                new LambdaQueryWrapper<UiComponentTemplateVersion>()
                        .eq(UiComponentTemplateVersion::getTemplateId, templateId)
                        .eq(UiComponentTemplateVersion::getVersion, templateVersion));
        if (version == null) {
            throw new IllegalArgumentException(
                    referenceLabel
                            + " 引用的组件模板版本不存在: "
                            + templateId
                            + "@"
                            + templateVersion);
        }
        verifyTemplateVersionIntegrity(version, referenceLabel);
    }

    private void verifyTemplateVersionIntegrity(
            UiComponentTemplateVersion version,
            String referenceLabel) {
        if (!StringUtils.hasText(version.getSnapshotDocument())
                || !StringUtils.hasText(version.getContentHash())
                || !Objects.equals(
                        version.getContentHash(),
                        hash(version.getSnapshotDocument()))) {
            throw new IllegalArgumentException(
                    referenceLabel
                            + " 引用的组件模板版本完整性校验失败: "
                            + version.getTemplateId()
                            + "@"
                            + version.getVersion());
        }
        codec.readObject(
                version.getSnapshotDocument(),
                "组件模板版本快照");
    }

    private void validateFormSnapshotTree(
            String formId,
            Map<String, Object> snapshot) {
        List<EntityFormNode> nodes = snapshotNodes(snapshot);
        Map<String, EntityFormNode> byId = new HashMap<>();
        Set<String> nodeKeys = new HashSet<>();
        for (EntityFormNode node : nodes) {
            if (!StringUtils.hasText(node.getId())) {
                throw new IllegalArgumentException("发布快照中的表单节点缺少稳定 ID");
            }
            if (byId.put(node.getId(), node) != null) {
                throw new IllegalArgumentException(
                        "发布快照中的表单节点 ID 重复: " + node.getId());
            }
            if (!StringUtils.hasText(node.getNodeKey())
                    || !nodeKeys.add(node.getNodeKey())) {
                throw new IllegalArgumentException(
                        "发布快照中的表单节点编码为空或重复: " + node.getNodeKey());
            }
            String nodeType = normalize(node.getNodeType());
            if (!FORM_NODE_TYPES.contains(nodeType)) {
                throw new IllegalArgumentException(
                        "发布快照包含不支持的表单节点类型: " + node.getNodeType());
            }
            node.setNodeType(nodeType);
        }
        for (EntityFormNode node : nodes) {
            EntityFormNode parent = StringUtils.hasText(node.getParentId())
                    ? byId.get(node.getParentId())
                    : null;
            if (StringUtils.hasText(node.getParentId()) && parent == null) {
                throw new IllegalArgumentException(
                        "发布快照中的表单节点父级不存在: " + nodeLabel(node));
            }
            validateSnapshotParentChild(node, parent);
            int depth = 1;
            Set<String> visited = new HashSet<>();
            String parentId = node.getParentId();
            while (StringUtils.hasText(parentId)) {
                if (!visited.add(parentId) || Objects.equals(parentId, node.getId())) {
                    throw new IllegalArgumentException(
                            "发布快照中的表单节点存在循环引用: " + nodeLabel(node));
                }
                EntityFormNode ancestor = byId.get(parentId);
                if (ancestor == null) {
                    throw new IllegalArgumentException(
                            "发布快照中的表单节点父级不存在: " + nodeLabel(node));
                }
                if (!FORM_CONTAINER_TYPES.contains(ancestor.getNodeType())) {
                    throw new IllegalArgumentException(
                            "发布快照中的非容器节点不能包含子节点: "
                                    + nodeLabel(ancestor));
                }
                parentId = ancestor.getParentId();
                if (++depth > MAX_FORM_DEPTH) {
                    throw new IllegalArgumentException(
                            "发布快照表单嵌套层级不能超过 "
                                    + MAX_FORM_DEPTH
                                    + " 层");
                }
            }
        }
        Map<String, List<String>> referenceCache = new HashMap<>();
        referenceCache.put(formId, referencedFormIds(nodes));
        validatePublishedFormGraph(
                formId, 1, new LinkedHashSet<>(), referenceCache);
    }

    private void validateSnapshotParentChild(
            EntityFormNode child,
            EntityFormNode parent) {
        if (parent == null) {
            if ("TAB".equals(child.getNodeType())) {
                throw new IllegalArgumentException("TAB 节点只能位于 TAB_SET 下");
            }
            return;
        }
        Set<String> allowedChildren = ALLOWED_CHILD_TYPES.get(parent.getNodeType());
        if (allowedChildren == null || !allowedChildren.contains(child.getNodeType())) {
            if ("TAB".equals(child.getNodeType())) {
                throw new IllegalArgumentException("TAB 节点只能位于 TAB_SET 下");
            }
            if ("TAB_SET".equals(parent.getNodeType())) {
                throw new IllegalArgumentException("TAB_SET 的直接子节点只能是 TAB");
            }
            throw new IllegalArgumentException(
                    parent.getNodeType()
                            + " 节点不能直接包含 "
                            + child.getNodeType()
                            + " 节点");
        }
    }

    private void validatePublishedFormGraph(
            String formId,
            int depth,
            LinkedHashSet<String> path,
            Map<String, List<String>> referenceCache) {
        if (!path.add(formId)) {
            throw new IllegalArgumentException(
                    "子表单发布引用存在循环: "
                            + String.join(" -> ", path)
                            + " -> "
                            + formId);
        }
        for (String referencedFormId : referenceCache.computeIfAbsent(
                formId, this::activePublishedFormReferences)) {
            if (path.contains(referencedFormId)) {
                throw new IllegalArgumentException(
                        "子表单发布引用存在循环: "
                                + String.join(" -> ", path)
                                + " -> "
                                + referencedFormId);
            }
            if (depth >= MAX_FORM_DEPTH) {
                throw new IllegalArgumentException(
                        "跨表单嵌套层级不能超过 " + MAX_FORM_DEPTH + " 层");
            }
            validatePublishedFormGraph(
                    referencedFormId,
                    depth + 1,
                    path,
                    referenceCache);
        }
        path.remove(formId);
    }

    private List<String> activePublishedFormReferences(String formId) {
        UiConfigRelease release = releaseMapper.findActive(FORM, formId);
        if (release == null || !StringUtils.hasText(release.getSnapshotDocument())) {
            throw new IllegalArgumentException("子表单引用的表单尚未发布: " + formId);
        }
        Map<String, Object> snapshot = codec.readObject(
                release.getSnapshotDocument(), "子表单发布快照");
        return referencedFormIds(snapshotNodes(snapshot));
    }

    private List<String> referencedFormIds(List<EntityFormNode> nodes) {
        List<String> references = new ArrayList<>();
        for (EntityFormNode node : nodes) {
            if (!Set.of("SUB_FORM", "REPEATER").contains(node.getNodeType())
                    || !StringUtils.hasText(node.getPropsDocument())) {
                continue;
            }
            Map<String, Object> props = codec.readObject(
                    node.getPropsDocument(), "子表单发布节点属性");
            Object publishedFormId = props.get("publishedFormId");
            if (publishedFormId != null
                    && StringUtils.hasText(String.valueOf(publishedFormId))) {
                String referencedFormId = String.valueOf(publishedFormId).trim();
                if (!references.contains(referencedFormId)) {
                    references.add(referencedFormId);
                }
            }
        }
        return references;
    }

    private List<EntityFormNode> snapshotNodes(Map<String, Object> snapshot) {
        return objectMapper.convertValue(
                snapshot.getOrDefault("nodes", List.of()),
                new TypeReference<List<EntityFormNode>>() {});
    }

    private String nodeLabel(EntityFormNode node) {
        return StringUtils.hasText(node.getNodeKey())
                ? node.getNodeKey()
                : node.getId();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT)
                : null;
    }

    private void validateDataSourceReferences(Map<String, Object> snapshot) {
        String document = codec.write(snapshot, "待发布UI配置");
        if (document.contains("\"sourceType\":\"SQL\"")
                || document.contains("\"sourceType\":\"SCRIPT\"")
                || document.contains("\"sourceType\":\"URL\"")
                || document.contains("\"sql\":")
                || document.contains("\"script\":")
                || document.contains("\"url\":")) {
            throw new IllegalArgumentException(
                    "发布配置禁止包含任意 SQL、脚本或外网 URL 数据源");
        }
        validateDataSourceValue(snapshot, "$");
    }

    private void validateDataSourceValue(Object value, String path) {
        if (value instanceof Map<?, ?> map) {
            Object sourceId = map.get("sourceId");
            if (sourceId != null && StringUtils.hasText(String.valueOf(sourceId))) {
                String id = String.valueOf(sourceId);
                var definition = dataSourceMapper.selectById(id);
                if (definition == null
                        || !Boolean.TRUE.equals(definition.getEnabled())
                        || Integer.valueOf(1).equals(definition.getDeleted())) {
                    throw new IllegalArgumentException(
                            "发布配置引用的数据源不存在或未启用: "
                                    + path + ".sourceId=" + id);
                }
            }
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                validateDataSourceValue(
                        entry.getValue(),
                        path + "." + entry.getKey());
            }
        } else if (value instanceof List<?> list) {
            for (int index = 0; index < list.size(); index++) {
                validateDataSourceValue(
                        list.get(index),
                        path + "[" + index + "]");
            }
        }
    }

    private void deactivate(String configType, String configId) {
        UpdateWrapper<UiConfigRelease> update = new UpdateWrapper<>();
        update.eq("config_type", configType)
                .eq("config_id", configId)
                .eq("status", "ACTIVE")
                .set("status", "INACTIVE");
        releaseMapper.update(null, update);
    }

    private void activateOnOwner(
            String configType,
            String configId,
            UiConfigRelease release,
            String contentHash) {
        if (FORM.equals(configType)) {
            UpdateWrapper<EntityForm> update = new UpdateWrapper<>();
            update.eq("id", configId)
                    .set("active_release_id", release.getId())
                    .set("draft_hash", contentHash)
                    .set("update_time", LocalDateTime.now());
            formMapper.update(null, update);
            return;
        }
        UpdateWrapper<EntityListConfig> update = new UpdateWrapper<>();
        update.eq("id", configId)
                .set("active_release_id", release.getId())
                .set("published_version", release.getVersion())
                .set("draft_hash", contentHash)
                .set("update_time", LocalDateTime.now());
        listConfigMapper.update(null, update);
    }

    private String canonical(Map<String, Object> snapshot) {
        String document = codec.write(snapshot, "UI配置快照");
        return codec.canonicalize(document, "UI配置快照");
    }

    private boolean equivalent(Object left, Object right) {
        if (left == null || right == null) {
            return Objects.equals(left, right);
        }
        String leftDocument = codec.write(left, "UI配置差异左值");
        String rightDocument = codec.write(right, "UI配置差异右值");
        return Objects.equals(
                codec.canonicalize(leftDocument, "UI配置差异左值"),
                codec.canonicalize(rightDocument, "UI配置差异右值"));
    }

    private Map<String, Object> stableMap(Map<String, Object> source) {
        Object value = stableValue(source);
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, child) -> result.put(String.valueOf(key), child));
        return result;
    }

    private Object stableValue(Object source) {
        Object value = objectMapper.convertValue(source, Object.class);
        return stripVolatile(value);
    }

    private Object stripVolatile(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            map.forEach((key, child) -> {
                String name = String.valueOf(key);
                if (!VOLATILE_SNAPSHOT_KEYS.contains(name)) {
                    Object stableChild = stripVolatile(child);
                    if (stableChild != null) {
                        result.put(name, stableChild);
                    }
                }
            });
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(this::stripVolatile).toList();
        }
        return value;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("计算配置哈希失败", exception);
        }
    }

    private void requireType(String configType) {
        if (!FORM.equals(configType) && !LIST.equals(configType)) {
            throw new IllegalArgumentException("配置类型只能是 FORM 或 LIST");
        }
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "未命名项";
    }

    private Integer nullableInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("模板版本必须是整数", exception);
        }
    }

    private Integer integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private Integer booleanFlag(Object value) {
        return Boolean.TRUE.equals(value) ? 1 : 0;
    }
}
