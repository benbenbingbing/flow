package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.common.RevisionConflictException;
import com.workflow.common.json.JsonDocumentCodec;
import com.workflow.dto.EntityListActionSaveRequest;
import com.workflow.dto.EntityListItemReorderRequest;
import com.workflow.dto.EntityListSceneSaveRequest;
import com.workflow.entity.EntityListConfig;
import com.workflow.entity.EntityListAction;
import com.workflow.entity.EntityListScene;
import com.workflow.mapper.EntityListConfigMapper;
import com.workflow.mapper.EntityListActionMapper;
import com.workflow.mapper.EntityListSceneMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 实体列表关系型配置服务，管理按钮和场景的关系型存储与差异同步。
 *
 * <p>将列表工具栏、行内按钮和允许场景以关系型表存储，支持按 key 增量同步、
 * 乐观锁补丁更新和基于 orderKey 的稀疏排序，便于发布快照与草稿差异比对。</p>
 */
@Service
@RequiredArgsConstructor
public class EntityListRelationalConfigService {

    /** 工具栏按钮位置。 */
    public static final String TOOLBAR = "TOOLBAR";
    /** 行内按钮位置。 */
    public static final String ROW = "ROW";

    private static final TypeReference<List<Map<String, Object>>> BUTTON_LIST_TYPE =
            new TypeReference<>() {};
    private static final Set<String> ACTION_CLEAR_FIELDS = Set.of(
            "templateId",
            "templateVersion",
            "localOverridesDocument");

    private final EntityListActionMapper actionMapper;
    private final EntityListSceneMapper sceneMapper;
    private final EntityListConfigMapper configMapper;
    private final JsonDocumentCodec codec;

    /**
     * 查询指定位置的按钮配置 Map 列表。
     *
     * @param listConfigId 列表配置ID
     * @param position     按钮位置（TOOLBAR 或 ROW）
     * @return 按钮 Map 列表，listConfigId 为空返回空列表
     */
    public List<Map<String, Object>> findActions(String listConfigId, String position) {
        if (!StringUtils.hasText(listConfigId)) {
            return List.of();
        }
        return actionMapper.findByListAndPosition(listConfigId, position).stream()
                .map(this::toButton)
                .toList();
    }

    /**
     * 查询列表允许的场景编码列表。
     *
     * @param listConfigId 列表配置ID
     * @return 场景编码列表
     */
    public List<String> findScenes(String listConfigId) {
        if (!StringUtils.hasText(listConfigId)) {
            return List.of();
        }
        return sceneMapper.findByListConfigId(listConfigId).stream()
                .map(EntityListScene::getSceneCode)
                .toList();
    }

    /**
     * 查询列表的场景配置项列表。
     *
     * @param listConfigId 列表配置ID
     * @return 场景配置项列表
     */
    public List<EntityListScene> findSceneItems(String listConfigId) {
        if (!StringUtils.hasText(listConfigId)) {
            return List.of();
        }
        requireList(listConfigId);
        return sceneMapper.findByListConfigId(listConfigId);
    }

    /**
     * 全量替换指定位置的按钮配置，按 key 增量同步并删除多余项。
     *
     * @param listConfigId 列表配置ID
     * @param position     按钮位置
     * @param buttons      按钮 Map 列表
     * @throws IllegalArgumentException listConfigId 为空时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceActions(
            String listConfigId,
            String position,
            List<Map<String, Object>> buttons) {
        if (!StringUtils.hasText(listConfigId)) {
            throw new IllegalArgumentException("列表配置ID不能为空");
        }
        List<EntityListAction> existing =
                actionMapper.findByListAndPosition(listConfigId, position);
        Map<String, EntityListAction> existingById = new LinkedHashMap<>();
        Map<String, EntityListAction> existingByKey = new LinkedHashMap<>();
        existing.forEach(action -> {
            existingById.put(action.getId(), action);
            existingByKey.put(action.getButtonKey(), action);
        });
        Set<String> retained = new java.util.HashSet<>();
        int fallbackSort = 0;
        for (Map<String, Object> button : buttons == null ? List.<Map<String, Object>>of() : buttons) {
            String key = text(
                    button.get("key"),
                    position.toLowerCase() + "_" + fallbackSort);
            String actionId = text(button.get("id"), null);
            EntityListAction current = StringUtils.hasText(actionId)
                    ? existingById.get(actionId)
                    : existingByKey.get(key);
            EntityListAction desired =
                    actionFromButton(listConfigId, position, button, fallbackSort);
            if (current == null) {
                actionMapper.insert(desired);
                retained.add(desired.getId());
            } else {
                retained.add(current.getId());
                desired.setId(current.getId());
                desired.setCreatedAt(current.getCreatedAt());
                desired.setRevision(current.getRevision());
                if (!sameAction(current, desired)) {
                    desired.setRevision(
                            current.getRevision() == null ? 2 : current.getRevision() + 1);
                    actionMapper.updateById(desired);
                }
            }
            fallbackSort++;
        }
        existing.stream()
                .filter(action -> !retained.contains(action.getId()))
                .forEach(actionMapper::deleteById);
    }

    /**
     * 全量替换列表允许的场景编码，按编码增量同步并删除多余项。
     *
     * @param listConfigId 列表配置ID
     * @param scenes       场景编码列表
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceScenes(String listConfigId, List<String> scenes) {
        List<EntityListScene> existing = sceneMapper.findByListConfigId(listConfigId);
        Map<String, EntityListScene> existingByCode = new LinkedHashMap<>();
        existing.forEach(scene -> existingByCode.put(scene.getSceneCode(), scene));
        Set<String> retained = new java.util.HashSet<>();
        int sort = 0;
        for (String scene : scenes == null ? List.<String>of() : scenes) {
            if (!StringUtils.hasText(scene)) {
                continue;
            }
            String sceneCode = scene.trim().toUpperCase();
            EntityListScene current = existingByCode.get(sceneCode);
            if (current == null) {
                EntityListScene value = new EntityListScene();
                value.setListConfigId(listConfigId);
                value.setSceneCode(sceneCode);
                value.setSortOrder(sort);
                value.setRevision(1);
                value.setCreatedAt(LocalDateTime.now());
                sceneMapper.insert(value);
                retained.add(value.getId());
            } else {
                retained.add(current.getId());
                if (!Objects.equals(current.getSortOrder(), sort)) {
                    current.setSortOrder(sort);
                    current.setRevision(
                            current.getRevision() == null ? 2 : current.getRevision() + 1);
                    sceneMapper.updateById(current);
                }
            }
            sort++;
        }
        existing.stream()
                .filter(scene -> !retained.contains(scene.getId()))
                .forEach(sceneMapper::deleteById);
    }

    /**
     * 删除指定列表的所有按钮和场景关系型配置。
     *
     * @param listConfigId 列表配置ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByListConfigId(String listConfigId) {
        actionMapper.deleteByListConfigId(listConfigId);
        sceneMapper.deleteByListConfigId(listConfigId);
    }

    /**
     * 创建单个列表按钮。
     *
     * @param listConfigId 列表配置ID
     * @param request      按钮保存请求
     * @return 创建的按钮
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListAction createAction(
            String listConfigId,
            EntityListActionSaveRequest request) {
        requireList(listConfigId);
        EntityListAction action = new EntityListAction();
        applyAction(action, request);
        action.setListConfigId(listConfigId);
        action.setPosition(normalizedPosition(request.getPosition()));
        action.setOrderKey(request.getOrderKey() == null
                ? nextActionOrder(listConfigId, action.getPosition())
                : request.getOrderKey());
        action.setSortOrder(request.getSortOrder() == null
                ? actionMapper.findByListAndPosition(
                        listConfigId, action.getPosition()).size()
                : request.getSortOrder());
        action.setRevision(1);
        action.setCreatedAt(LocalDateTime.now());
        action.setUpdatedAt(LocalDateTime.now());
        action.setDeleted(0);
        actionMapper.insert(action);
        touchList(listConfigId);
        return action;
    }

    /**
     * 按补丁请求更新单个按钮，基于乐观锁更新。
     *
     * @param listConfigId 列表配置ID
     * @param actionId     按钮ID
     * @param request      按钮保存请求
     * @return 更新后的按钮
     * @throws RevisionConflictException 版本冲突时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListAction patchAction(
            String listConfigId,
            String actionId,
            EntityListActionSaveRequest request) {
        EntityListAction current = requireAction(listConfigId, actionId);
        requireRevision(request == null ? null : request.getExpectedRevision(), current);
        EntityListAction updated = new EntityListAction();
        org.springframework.beans.BeanUtils.copyProperties(current, updated);
        applyAction(updated, request);
        if (StringUtils.hasText(request.getPosition())) {
            updated.setPosition(normalizedPosition(request.getPosition()));
        }
        updated.setRevision(current.getRevision() + 1);
        updated.setUpdatedAt(LocalDateTime.now());
        UpdateWrapper<EntityListAction> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", actionId)
                .eq("list_config_id", listConfigId)
                .eq("revision", current.getRevision())
                .eq("deleted", 0)
                .set("position", updated.getPosition())
                .set("button_key", updated.getButtonKey())
                .set("button_type", updated.getButtonType())
                .set("button_label", updated.getButtonLabel())
                .set("icon", updated.getIcon())
                .set("style_type", updated.getStyleType())
                .set("link_mode", updated.getLinkMode())
                .set("custom_mode", updated.getCustomMode())
                .set("handler_code", updated.getHandlerCode())
                .set("permission_code", updated.getPermissionCode())
                .set("enabled", updated.getEnabled())
                .set("unavailable_behavior", updated.getUnavailableBehavior())
                .set("action_params_document", updated.getActionParamsDocument())
                .set("availability_rule_document", updated.getAvailabilityRuleDocument())
                .set("template_id", updated.getTemplateId())
                .set("template_version", updated.getTemplateVersion())
                .set("local_overrides_document", updated.getLocalOverridesDocument())
                .set("sort_order", updated.getSortOrder())
                .set("order_key", updated.getOrderKey())
                .set("revision", updated.getRevision())
                .set("update_time", updated.getUpdatedAt());
        if (actionMapper.update(null, wrapper) != 1) {
            throw new RevisionConflictException(
                    "列表按钮已被其他人修改，请刷新后重试",
                    actionMapper.selectById(actionId));
        }
        touchList(listConfigId);
        return requireAction(listConfigId, actionId);
    }

    /**
     * 调整按钮在同位置中的排序，基于前后边界计算中值 orderKey。
     *
     * @param listConfigId 列表配置ID
     * @param actionId     按钮ID
     * @param request      排序请求
     * @return 更新后的按钮
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListAction reorderAction(
            String listConfigId,
            String actionId,
            EntityListItemReorderRequest request) {
        EntityListAction current = requireAction(listConfigId, actionId);
        requireRevision(request == null ? null : request.getExpectedRevision(), current);
        long previous = actionBoundary(
                listConfigId, current.getPosition(), request.getPreviousId(), 0L);
        long next = actionBoundary(
                listConfigId,
                current.getPosition(),
                request.getNextId(),
                previous + (EntityFormNodeService.ORDER_STEP * 2));
        EntityListActionSaveRequest patch = new EntityListActionSaveRequest();
        patch.setExpectedRevision(current.getRevision());
        patch.setOrderKey(previous + ((next - previous) / 2));
        return patchAction(listConfigId, actionId, patch);
    }

    /**
     * 删除单个按钮（软删除），基于乐观锁更新。
     *
     * @param listConfigId     列表配置ID
     * @param actionId         按钮ID
     * @param expectedRevision 期望版本号
     * @throws RevisionConflictException 版本冲突时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteAction(
            String listConfigId,
            String actionId,
            Integer expectedRevision) {
        EntityListAction current = requireAction(listConfigId, actionId);
        requireRevision(expectedRevision, current);
        UpdateWrapper<EntityListAction> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", actionId)
                .eq("list_config_id", listConfigId)
                .eq("revision", current.getRevision())
                .eq("deleted", 0)
                .set("deleted", 1)
                .setSql("revision = revision + 1")
                .set("update_time", LocalDateTime.now());
        if (actionMapper.update(null, wrapper) != 1) {
            throw new RevisionConflictException(
                    "列表按钮已被其他人修改，请刷新后重试",
                    actionMapper.selectById(actionId));
        }
        touchList(listConfigId);
    }

    /**
     * 创建单个列表场景配置。
     *
     * @param listConfigId 列表配置ID
     * @param request      场景保存请求
     * @return 创建的场景
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListScene createScene(
            String listConfigId,
            EntityListSceneSaveRequest request) {
        requireList(listConfigId);
        EntityListScene scene = new EntityListScene();
        scene.setListConfigId(listConfigId);
        scene.setSceneCode(normalizedScene(request.getSceneCode()));
        scene.setSortOrder(request.getSortOrder() == null
                ? sceneMapper.findByListConfigId(listConfigId).size()
                : request.getSortOrder());
        scene.setRevision(1);
        scene.setCreatedAt(LocalDateTime.now());
        sceneMapper.insert(scene);
        touchList(listConfigId);
        return scene;
    }

    /**
     * 按补丁请求更新单个场景，基于乐观锁更新。
     *
     * @param listConfigId 列表配置ID
     * @param sceneId      场景ID
     * @param request      场景保存请求
     * @return 更新后的场景
     * @throws RevisionConflictException 版本冲突时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public EntityListScene patchScene(
            String listConfigId,
            String sceneId,
            EntityListSceneSaveRequest request) {
        EntityListScene current = requireScene(listConfigId, sceneId);
        if (request == null || request.getExpectedRevision() == null
                || !request.getExpectedRevision().equals(current.getRevision())) {
            throw new RevisionConflictException("列表场景已被其他人修改", current);
        }
        UpdateWrapper<EntityListScene> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", sceneId)
                .eq("list_config_id", listConfigId)
                .eq("revision", current.getRevision())
                .set("scene_code", StringUtils.hasText(request.getSceneCode())
                        ? normalizedScene(request.getSceneCode())
                        : current.getSceneCode())
                .set("sort_order", request.getSortOrder() == null
                        ? current.getSortOrder()
                        : request.getSortOrder())
                .set("revision", current.getRevision() + 1);
        if (sceneMapper.update(null, wrapper) != 1) {
            throw new RevisionConflictException(
                    "列表场景已被其他人修改，请刷新后重试",
                    sceneMapper.selectById(sceneId));
        }
        touchList(listConfigId);
        return requireScene(listConfigId, sceneId);
    }

    /**
     * 删除单个场景，基于乐观锁校验。
     *
     * @param listConfigId     列表配置ID
     * @param sceneId          场景ID
     * @param expectedRevision 期望版本号
     * @throws RevisionConflictException 版本冲突时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteScene(
            String listConfigId,
            String sceneId,
            Integer expectedRevision) {
        EntityListScene current = requireScene(listConfigId, sceneId);
        if (expectedRevision == null || !expectedRevision.equals(current.getRevision())) {
            throw new RevisionConflictException("列表场景已被其他人修改", current);
        }
        sceneMapper.deleteById(sceneId);
        touchList(listConfigId);
    }

    /**
     * 解析按钮 JSON 文档为 Map 列表。
     *
     * @param document 按钮 JSON 文档
     * @param label    文档用途说明，用于错误提示
     * @return 按钮 Map 列表，文档为空返回空列表
     */
    public List<Map<String, Object>> parseButtons(String document, String label) {
        if (!StringUtils.hasText(document)) {
            return new ArrayList<>();
        }
        return codec.read(document, BUTTON_LIST_TYPE, label);
    }

    private Map<String, Object> toButton(EntityListAction action) {
        Map<String, Object> button = StringUtils.hasText(action.getActionParamsDocument())
                ? new LinkedHashMap<>(codec.readObject(
                        action.getActionParamsDocument(), "列表按钮扩展参数"))
                : new LinkedHashMap<>();
        button.put("key", action.getButtonKey());
        button.put("type", action.getButtonType());
        button.put("label", action.getButtonLabel());
        button.put("icon", action.getIcon());
        button.put("buttonType", action.getStyleType());
        button.put("link", Boolean.TRUE.equals(action.getLinkMode()));
        button.put("customMode", action.getCustomMode());
        button.put("customHandler", action.getHandlerCode());
        button.put("perm", action.getPermissionCode());
        button.put("sort", action.getSortOrder());
        button.put("enabled", action.getEnabled());
        button.put("id", action.getId());
        button.put("revision", action.getRevision());
        button.put("orderKey", action.getOrderKey());
        button.put("templateId", action.getTemplateId());
        button.put("templateVersion", action.getTemplateVersion());
        if (StringUtils.hasText(action.getLocalOverridesDocument())) {
            button.put("localOverridesDocument", codec.readObject(
                    action.getLocalOverridesDocument(), "列表按钮模板本地覆盖"));
        }
        if (StringUtils.hasText(action.getAvailabilityRuleDocument())) {
            button.put("availabilityRule", codec.readObject(
                    action.getAvailabilityRuleDocument(), "按钮适用条件"));
        }
        return button;
    }

    private EntityListAction actionFromButton(
            String listConfigId,
            String position,
            Map<String, Object> button,
            int fallbackSort) {
        EntityListAction action = new EntityListAction();
        action.setListConfigId(listConfigId);
        action.setPosition(position);
        action.setButtonKey(text(
                button.get("key"),
                position.toLowerCase() + "_" + fallbackSort));
        action.setButtonType(text(button.get("type"), "built-in"));
        action.setButtonLabel(text(button.get("label"), action.getButtonKey()));
        action.setIcon(text(button.get("icon"), null));
        action.setStyleType(text(button.get("buttonType"), null));
        action.setLinkMode(Boolean.TRUE.equals(button.get("link")));
        action.setCustomMode(text(button.get("customMode"), null));
        action.setHandlerCode(firstText(
                button.get("customHandler"),
                button.get("customComponent")));
        action.setPermissionCode(text(button.get("perm"), null));
        action.setSortOrder(integer(button.get("sort"), fallbackSort));
        action.setOrderKey(longValue(
                button.get("orderKey"),
                (fallbackSort + 1L) * EntityFormNodeService.ORDER_STEP));
        action.setRevision(1);
        action.setEnabled(!Boolean.FALSE.equals(button.get("enabled")));
        action.setTemplateId(text(button.get("templateId"), null));
        action.setTemplateVersion(nullableInteger(button.get("templateVersion")));
        Object localOverrides = button.containsKey("localOverridesDocument")
                ? button.get("localOverridesDocument")
                : button.get("localOverrides");
        action.setLocalOverridesDocument(jsonDocument(
                localOverrides,
                "列表按钮模板本地覆盖"));
        Object availabilityRule = button.get("availabilityRule");
        if (availabilityRule instanceof Map<?, ?> rule) {
            action.setUnavailableBehavior(text(rule.get("unavailableBehavior"), null));
            action.setAvailabilityRuleDocument(codec.write(rule, "按钮适用条件"));
        }
        action.setActionParamsDocument(codec.write(button, "列表按钮配置"));
        action.setCreatedAt(LocalDateTime.now());
        action.setUpdatedAt(LocalDateTime.now());
        action.setDeleted(0);
        return action;
    }

    private boolean sameAction(
            EntityListAction left,
            EntityListAction right) {
        return Objects.equals(left.getPosition(), right.getPosition())
                && Objects.equals(left.getButtonKey(), right.getButtonKey())
                && Objects.equals(left.getButtonType(), right.getButtonType())
                && Objects.equals(left.getButtonLabel(), right.getButtonLabel())
                && Objects.equals(left.getIcon(), right.getIcon())
                && Objects.equals(left.getStyleType(), right.getStyleType())
                && Objects.equals(left.getLinkMode(), right.getLinkMode())
                && Objects.equals(left.getCustomMode(), right.getCustomMode())
                && Objects.equals(left.getHandlerCode(), right.getHandlerCode())
                && Objects.equals(left.getPermissionCode(), right.getPermissionCode())
                && Objects.equals(left.getSortOrder(), right.getSortOrder())
                && Objects.equals(left.getOrderKey(), right.getOrderKey())
                && Objects.equals(left.getEnabled(), right.getEnabled())
                && Objects.equals(
                        left.getUnavailableBehavior(),
                        right.getUnavailableBehavior())
                && Objects.equals(
                        left.getAvailabilityRuleDocument(),
                        right.getAvailabilityRuleDocument())
                && Objects.equals(left.getTemplateId(), right.getTemplateId())
                && Objects.equals(
                        left.getTemplateVersion(),
                        right.getTemplateVersion())
                && Objects.equals(
                        left.getLocalOverridesDocument(),
                        right.getLocalOverridesDocument())
                && sameActionParams(
                        left.getActionParamsDocument(),
                        right.getActionParamsDocument());
    }

    private boolean sameActionParams(String left, String right) {
        Map<String, Object> leftMap = StringUtils.hasText(left)
                ? new LinkedHashMap<>(codec.readObject(left, "列表按钮配置"))
                : new LinkedHashMap<>();
        Map<String, Object> rightMap = StringUtils.hasText(right)
                ? new LinkedHashMap<>(codec.readObject(right, "列表按钮配置"))
                : new LinkedHashMap<>();
        for (String transientKey : List.of("id", "revision", "orderKey")) {
            leftMap.remove(transientKey);
            rightMap.remove(transientKey);
        }
        return Objects.equals(leftMap, rightMap);
    }

    private void applyAction(
            EntityListAction action,
            EntityListActionSaveRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("列表按钮不能为空");
        }
        Set<String> clearFields = request.getClearFields() == null
                ? Set.of()
                : request.getClearFields();
        if (!ACTION_CLEAR_FIELDS.containsAll(clearFields)) {
            Set<String> unknown = new java.util.LinkedHashSet<>(clearFields);
            unknown.removeAll(ACTION_CLEAR_FIELDS);
            throw new IllegalArgumentException(
                    "列表按钮包含不支持的清空字段: " + unknown);
        }
        if (request.getButtonKey() != null) action.setButtonKey(request.getButtonKey());
        if (request.getButtonType() != null) action.setButtonType(request.getButtonType());
        if (request.getButtonLabel() != null) action.setButtonLabel(request.getButtonLabel());
        if (request.getIcon() != null) action.setIcon(request.getIcon());
        if (request.getStyleType() != null) action.setStyleType(request.getStyleType());
        if (request.getLinkMode() != null) action.setLinkMode(request.getLinkMode());
        if (request.getCustomMode() != null) action.setCustomMode(request.getCustomMode());
        if (request.getHandlerCode() != null) action.setHandlerCode(request.getHandlerCode());
        if (request.getPermissionCode() != null) {
            action.setPermissionCode(request.getPermissionCode());
        }
        if (request.getEnabled() != null) action.setEnabled(request.getEnabled());
        if (request.getUnavailableBehavior() != null) {
            action.setUnavailableBehavior(request.getUnavailableBehavior());
        }
        if (request.getSortOrder() != null) action.setSortOrder(request.getSortOrder());
        if (request.getActionParams() != null) {
            action.setActionParamsDocument(
                    codec.write(request.getActionParams(), "列表按钮参数"));
        }
        if (request.getAvailabilityRule() != null) {
            action.setAvailabilityRuleDocument(
                    codec.write(request.getAvailabilityRule(), "列表按钮适用条件"));
        }
        if (request.getOrderKey() != null) action.setOrderKey(request.getOrderKey());
        if (clearFields.contains("templateId")) {
            action.setTemplateId(null);
        } else if (request.getTemplateId() != null) {
            action.setTemplateId(blankToNull(request.getTemplateId()));
        }
        if (clearFields.contains("templateVersion")) {
            action.setTemplateVersion(null);
        } else if (request.getTemplateVersion() != null) {
            action.setTemplateVersion(request.getTemplateVersion());
        }
        if (clearFields.contains("localOverridesDocument")) {
            action.setLocalOverridesDocument(null);
        } else if (request.getLocalOverridesDocument() != null) {
            action.setLocalOverridesDocument(jsonDocument(
                    request.getLocalOverridesDocument(),
                    "列表按钮模板本地覆盖"));
        }
        if (!StringUtils.hasText(action.getButtonKey())
                || !StringUtils.hasText(action.getButtonLabel())) {
            throw new IllegalArgumentException("按钮编码和名称不能为空");
        }
        if (!StringUtils.hasText(action.getButtonType())) action.setButtonType("built-in");
        if (action.getLinkMode() == null) action.setLinkMode(false);
        if (action.getEnabled() == null) action.setEnabled(true);
    }

    private EntityListConfig requireList(String listConfigId) {
        EntityListConfig config = configMapper.selectById(listConfigId);
        if (config == null) throw new IllegalArgumentException("列表配置不存在");
        return config;
    }

    private EntityListAction requireAction(String listConfigId, String actionId) {
        EntityListAction action = actionMapper.selectById(actionId);
        if (action == null || !listConfigId.equals(action.getListConfigId())
                || Integer.valueOf(1).equals(action.getDeleted())) {
            throw new IllegalArgumentException("列表按钮不存在");
        }
        return action;
    }

    private EntityListScene requireScene(String listConfigId, String sceneId) {
        EntityListScene scene = sceneMapper.selectById(sceneId);
        if (scene == null || !listConfigId.equals(scene.getListConfigId())) {
            throw new IllegalArgumentException("列表场景不存在");
        }
        return scene;
    }

    private void requireRevision(Integer expected, EntityListAction current) {
        if (expected == null || !expected.equals(current.getRevision())) {
            throw new RevisionConflictException("列表按钮已被其他人修改", current);
        }
    }

    private long nextActionOrder(String listConfigId, String position) {
        List<EntityListAction> actions =
                actionMapper.findByListAndPosition(listConfigId, position);
        return actions.isEmpty()
                ? EntityFormNodeService.ORDER_STEP
                : actions.get(actions.size() - 1).getOrderKey()
                        + EntityFormNodeService.ORDER_STEP;
    }

    private long actionBoundary(
            String listConfigId,
            String position,
            String actionId,
            long fallback) {
        if (!StringUtils.hasText(actionId)) return fallback;
        EntityListAction action = requireAction(listConfigId, actionId);
        if (!Objects.equals(position, action.getPosition())) {
            throw new IllegalArgumentException("排序边界按钮不在同一位置");
        }
        return action.getOrderKey();
    }

    private String normalizedPosition(String position) {
        String value = StringUtils.hasText(position)
                ? position.trim().toUpperCase()
                : TOOLBAR;
        if (!Set.of(TOOLBAR, ROW).contains(value)) {
            throw new IllegalArgumentException("按钮位置只能是 TOOLBAR 或 ROW");
        }
        return value;
    }

    private String normalizedScene(String scene) {
        if (!StringUtils.hasText(scene)) {
            throw new IllegalArgumentException("场景编码不能为空");
        }
        String value = scene.trim().toUpperCase();
        if (!Set.of(
                "MENU", "PAGE", "DIALOG", "DRAWER",
                "EMBEDDED", "FORM_PICKER", "SUB_TABLE").contains(value)) {
            throw new IllegalArgumentException("不支持的列表场景: " + value);
        }
        return value;
    }

    private void touchList(String listConfigId) {
        UpdateWrapper<EntityListConfig> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", listConfigId)
                .setSql("revision = revision + 1")
                .set("draft_hash", null)
                .set("update_time", LocalDateTime.now());
        configMapper.update(null, wrapper);
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value, null);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private String text(Object value, String fallback) {
        return value == null || !StringUtils.hasText(String.valueOf(value))
                ? fallback
                : String.valueOf(value).trim();
    }

    private int integer(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? fallback : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
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

    private long longValue(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null ? fallback : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private String jsonDocument(Object value, String label) {
        if (value == null) {
            return null;
        }
        if (value instanceof String document) {
            return StringUtils.hasText(document)
                    ? codec.canonicalize(document, label)
                    : null;
        }
        return codec.canonicalize(codec.write(value, label), label);
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
