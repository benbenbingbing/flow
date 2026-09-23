package com.workflow.entity.list.application;

import com.workflow.entity.form.application.EntityFormNodeService;
import com.workflow.entity.list.application.validation.ListCellActionMappingPolicy;
import com.workflow.entity.list.application.validation.ListButtonSelectionPolicy;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.workflow.core.error.RevisionConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.list.api.request.EntityListActionSaveRequest;
import com.workflow.entity.list.api.request.EntityListItemReorderRequest;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListAction;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListActionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.permission.application.EntityListActionRulePolicy;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 实体列表关系型配置服务，管理按钮的关系型存储与差异同步。
 *
 * <p>将列表工具栏和行内按钮以关系型表存储，支持按 key 增量同步、
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
            "localOverridesDocument",
            "availabilityRuleDocument");

    private final EntityListActionMapper actionMapper;
    private final EntityListConfigMapper configMapper;
    private final EntityFormMapper formMapper;
    private final UiConfigReleaseMapper releaseMapper;
    private final JsonDocumentCodec codec;
    private final EntityListActionRulePolicy actionRulePolicy;

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
        replaceActionsInternal(
                listConfigId,
                position,
                buttons,
                false);
    }

    /**
     * 按发布快照全量替换按钮，并保留快照中的稳定按钮 ID。
     *
     * @param listConfigId 列表配置ID，后续用于处理替换动作集合发布版本时定位或关联目标
     * @param position 位置，作为 {@code replaceActionsInternal} 的输入影响后续处理
     * @param buttons 按钮集合，供本方法处理替换动作集合发布版本时使用
     */
    @Transactional(rollbackFor = Exception.class)
    public void replaceActionsForRelease(
            String listConfigId,
            String position,
            List<Map<String, Object>> buttons) {
        replaceActionsInternal(
                listConfigId,
                position,
                normalizeReleaseActionPersistenceDefaults(
                        listConfigId,
                        position,
                        buttons,
                        List.of()),
                true);
    }

    /**
     * 为历史发布按钮补齐关系表必然物化的稳定值。
     *
     * <p>旧快照可能没有按钮 ID 和关系表默认字段。缺失 ID 时优先沿用当前草稿中
     * 同位置、同 key 的 ID，以保持事件绑定目标稳定；没有可沿用 ID 时再按列表、
     * 位置和 key 派生确定性 ID。排序和展示默认值只按发布快照顺序补齐，禁止从
     * 当前草稿回填，避免把待撤销的重排或 link 修改带回发布基线。</p>
     *
     * @param listConfigId     列表配置 ID
     * @param position         TOOLBAR 或 ROW
     * @param publishedButtons 发布快照按钮
     * @param currentButtons   当前草稿按钮，仅用于回填缺失 ID
     * @return 不修改入参的规范化按钮副本
     */
    public static List<Map<String, Object>>
            normalizeReleaseActionPersistenceDefaults(
                    String listConfigId,
                    String position,
                    List<Map<String, Object>> publishedButtons,
                    List<Map<String, Object>> currentButtons) {
        if (!StringUtils.hasText(listConfigId)) {
            throw new IllegalArgumentException("列表配置ID不能为空");
        }
        String normalizedPosition = StringUtils.hasText(position)
                ? position.trim().toUpperCase(Locale.ROOT)
                : TOOLBAR;
        if (!Set.of(TOOLBAR, ROW).contains(normalizedPosition)) {
            throw new IllegalArgumentException(
                    "按钮位置只能是 TOOLBAR 或 ROW");
        }

        Map<String, String> currentIdsByKey = new LinkedHashMap<>();
        List<Map<String, Object>> safeCurrent = currentButtons == null
                ? List.of() : currentButtons;
        for (int index = 0; index < safeCurrent.size(); index++) {
            Map<String, Object> current = safeCurrent.get(index);
            if (current == null) {
                continue;
            }
            String key = releaseText(
                    current.get("key"),
                    fallbackButtonKey(normalizedPosition, index));
            String id = releaseText(current.get("id"), null);
            if (StringUtils.hasText(id)) {
                currentIdsByKey.putIfAbsent(
                        key.toLowerCase(Locale.ROOT),
                        id);
            }
        }

        List<Map<String, Object>> safePublished = publishedButtons == null
                ? List.of() : publishedButtons;
        List<Map<String, Object>> normalized = new ArrayList<>();
        Set<String> publishedKeys = new java.util.HashSet<>();
        for (int index = 0; index < safePublished.size(); index++) {
            Map<String, Object> original = safePublished.get(index);
            Map<String, Object> button = original == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(original);
            String key = releaseText(
                    button.get("key"),
                    fallbackButtonKey(normalizedPosition, index));
            button.put("key", key);
            String logicalKey = key.toLowerCase(Locale.ROOT);
            if (!publishedKeys.add(logicalKey)) {
                // 数据库唯一键使用大小写不敏感排序规则，必须在写入前给出明确错误。
                throw new IllegalArgumentException(
                        "发布列表按钮编码重复: " + key);
            }

            String id = releaseText(button.get("id"), null);
            if (!StringUtils.hasText(id)) {
                id = currentIdsByKey.get(logicalKey);
            }
            button.put(
                    "id",
                    StringUtils.hasText(id)
                            ? id
                            : deterministicReleaseActionId(
                                    listConfigId,
                                    normalizedPosition,
                                    logicalKey));
            button.put(
                    "orderKey",
                    releaseLong(
                            button.get("orderKey"),
                            (index + 1L)
                                    * EntityFormNodeService.ORDER_STEP));
            button.put("sort", releaseInteger(
                    button.get("sort"), index));
            button.put("type", releaseText(
                    button.get("type"), "built-in"));
            button.put("label", releaseText(
                    button.get("label"), key));
            button.put("link", Boolean.TRUE.equals(
                    button.get("link")));
            button.put("enabled", !Boolean.FALSE.equals(
                    button.get("enabled")));
            normalized.add(button);
        }
        return List.copyOf(normalized);
    }

    /**
     * 处理替换动作集合内部，并将结果传给后续步骤。
     *
     * @param listConfigId 列表配置ID，后续用于处理替换动作集合内部时定位或关联目标
     * @param position 位置，作为 {@code ListCellActionMappingPolicy.validateButtons} 的输入影响后续处理
     * @param buttons 按钮集合，作为 {@code ListCellActionMappingPolicy.validateButtons} 的输入影响后续处理
     * @param preservePublishedIds {@code preserve}已发布ID 集合，供本方法处理替换动作集合内部时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void replaceActionsInternal(
            String listConfigId,
            String position,
            List<Map<String, Object>> buttons,
            boolean preservePublishedIds) {
        if (!StringUtils.hasText(listConfigId)) {
            throw new IllegalArgumentException("列表配置ID不能为空");
        }
        ListCellActionMappingPolicy.validateButtons(position, buttons);
        EntityListConfig listConfig = requireList(listConfigId);
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
            if (preservePublishedIds
                    && StringUtils.hasText(actionId)) {
                desired.setId(actionId.trim());
            }
            validateAndSanitizeTargetForm(listConfig, desired);
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
     * 锁定列表按钮草稿，供配置级撤销建立串行化边界。
     *
     * @param listConfigId 列表配置ID，后续用于锁定草稿子节点发布版本时定位或关联目标
     */
    public void lockDraftChildrenForRelease(String listConfigId) {
        actionMapper.findAllByListConfigIdForUpdate(listConfigId);
    }

    /**
     * 删除指定列表的所有按钮关系型配置。
     *
     * @param listConfigId 列表配置ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteByListConfigId(String listConfigId) {
        actionMapper.deleteByListConfigId(listConfigId);
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
        EntityListConfig listConfig = requireList(listConfigId);
        EntityListAction action = new EntityListAction();
        applyAction(action, request);
        action.setListConfigId(listConfigId);
        action.setPosition(normalizedPosition(request.getPosition()));
        validateAndSanitizeTargetForm(listConfig, action);
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
        validateAndSanitizeTargetForm(requireList(listConfigId), updated);
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

    /**
     * 转换为按钮；输出作为后续校验或处理的输入。
     *
     * @param action 动作标识，决定后续按钮采用的处理分支
     * @return 按钮键值结果，供调用方继续处理
     */
    private Map<String, Object> toButton(EntityListAction action) {
        Map<String, Object> button = StringUtils.hasText(action.getActionParamsDocument())
                ? new LinkedHashMap<>(codec.readObject(
                        action.getActionParamsDocument(), "列表按钮扩展参数"))
                : new LinkedHashMap<>();
        // availabilityRule 以独立列为唯一事实来源，避免清空后被扩展参数旧副本带回。
        button.remove("availabilityRule");
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

    /**
     * 处理动作起始按钮，并将结果传给后续步骤。
     *
     * @param listConfigId 列表配置ID，后续用于处理动作起始按钮时定位或关联目标
     * @param position 位置，作为 {@code action.setPosition} 的输入影响后续处理
     * @param button 按钮，作为 {@code action.setButtonKey} 的输入影响后续处理
     * @param fallbackSort 兜底排序，主值不可用时供后续处理兜底
     * @return 处理后的动作起始按钮结果，供调用方继续处理
     */
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
        if (availabilityRule != null) {
            action.setAvailabilityRuleDocument(codec.write(
                    actionRulePolicy.normalizeDocument(availabilityRule),
                    "按钮适用条件"));
        }
        action.setActionParamsDocument(codec.write(
                actionParamsWithoutAvailabilityRule(button),
                "列表按钮配置"));
        action.setCreatedAt(LocalDateTime.now());
        action.setUpdatedAt(LocalDateTime.now());
        action.setDeleted(0);
        return action;
    }

    /**
     * 判断相同动作条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，作为 {@code sameActionParams} 的输入影响后续处理
     * @param right 右侧，供本方法处理相同动作时使用
     * @return 相同动作条件成立时为 true，否则为 false
     */
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

    /**
     * 判断相同动作参数条件是否成立，供调用方选择后续分支。
     *
     * @param left 左侧，供本方法处理相同动作参数时使用
     * @param right 右侧，供本方法处理相同动作参数时使用
     * @return 相同动作参数条件成立时为 true，否则为 false
     */
    private boolean sameActionParams(String left, String right) {
        Map<String, Object> leftMap = StringUtils.hasText(left)
                ? new LinkedHashMap<>(codec.readObject(left, "列表按钮配置"))
                : new LinkedHashMap<>();
        Map<String, Object> rightMap = StringUtils.hasText(right)
                ? new LinkedHashMap<>(codec.readObject(right, "列表按钮配置"))
                : new LinkedHashMap<>();
        for (String transientKey : List.of(
                "id", "revision", "orderKey", "availabilityRule")) {
            leftMap.remove(transientKey);
            rightMap.remove(transientKey);
        }
        return Objects.equals(leftMap, rightMap);
    }

    /**
     * 应用动作，并将结果传给后续步骤。
     *
     * @param action 动作标识，决定后续动作采用的处理分支
     * @param request 本次请求，后续经校验后用于应用动作
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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
        if (request.getSortOrder() != null) action.setSortOrder(request.getSortOrder());
        if (request.getActionParams() != null) {
            action.setActionParamsDocument(
                    codec.write(
                            actionParamsWithoutAvailabilityRule(
                                    request.getActionParams()),
                            "列表按钮参数"));
        }
        if (clearFields.contains("availabilityRuleDocument")) {
            action.setAvailabilityRuleDocument(null);
            action.setActionParamsDocument(codec.write(
                    actionParamsWithoutAvailabilityRule(
                            StringUtils.hasText(
                                    action.getActionParamsDocument())
                                    ? codec.readObject(
                                            action.getActionParamsDocument(),
                                            "列表按钮参数")
                                    : Map.of()),
                    "列表按钮参数"));
        } else if (request.getAvailabilityRule() != null) {
            action.setAvailabilityRuleDocument(
                    codec.write(
                            actionRulePolicy.normalizeDocument(
                                    request.getAvailabilityRule()),
                            "列表按钮适用条件"));
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

    /**
     * 独立规则列是唯一事实来源，扩展参数不得重复保存规则。
     *
     * @param source 待处理动作参数{@code without}{@code availability}规则的原始输入，结果供调用方继续使用
     * @return 动作参数{@code without}{@code availability}规则键值结果，供调用方继续处理
     */
    private Map<String, Object> actionParamsWithoutAvailabilityRule(
            Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>(
                source == null ? Map.of() : source);
        result.remove("availabilityRule");
        return result;
    }

    /**
     * 校验与清洗目标表单；不满足约束时阻止后续处理。
     *
     * @param listConfig 列表配置内容，决定后续与清洗目标表单的处理规则
     * @param action 动作标识，决定后续与清洗目标表单采用的处理分支
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private void validateAndSanitizeTargetForm(
            EntityListConfig listConfig,
            EntityListAction action) {
        Map<String, Object> params =
                StringUtils.hasText(action.getActionParamsDocument())
                        ? new LinkedHashMap<>(codec.readObject(
                                action.getActionParamsDocument(),
                                "列表按钮参数"))
                        : new LinkedHashMap<>();
        ListCellActionMappingPolicy.validate(action.getPosition(), action.getButtonType(),
                action.getButtonKey(), action.getCustomMode(), params);
        ListButtonSelectionPolicy.validate(action.getPosition(), action.getButtonType(),
                action.getButtonKey(), action.getCustomMode(), params);
        if (params.containsKey("parameterMappings")) {
            params.put("parameterMappings", com.workflow.entity.ui.application.PageParameterPolicy.mappings(params.get("parameterMappings")));
        }
        params.remove("targetFormReleaseId");
        params.remove("targetFormReleaseVersion");

        String targetFormId = text(params.get("targetFormId"), null);
        boolean customOpenForm =
                "custom".equalsIgnoreCase(action.getButtonType())
                        && "open-form".equalsIgnoreCase(action.getCustomMode());
        boolean builtInTargetForm =
                "built-in".equalsIgnoreCase(action.getButtonType())
                        && (TOOLBAR.equals(action.getPosition())
                                ? "create".equals(action.getButtonKey())
                                : Set.of("view", "edit", "approve")
                                        .contains(action.getButtonKey()));

        if (!StringUtils.hasText(targetFormId)) {
            if (customOpenForm) {
                throw new IllegalArgumentException(
                        "自定义打开表单按钮必须选择目标表单");
            }
            params.remove("targetFormId");
            params.remove("targetFormMode");
            action.setActionParamsDocument(params.isEmpty()
                    ? null
                    : codec.write(params, "列表按钮参数"));
            return;
        }
        if (!customOpenForm && !builtInTargetForm) {
            throw new IllegalArgumentException(
                    "当前按钮不支持配置打开表单");
        }

        EntityForm form = formMapper.selectById(targetFormId);
        if (form == null) {
            throw new IllegalArgumentException("目标表单不存在");
        }
        if (!Objects.equals(listConfig.getEntityId(), form.getEntityId())) {
            throw new IllegalArgumentException(
                    "目标表单必须属于当前列表实体");
        }
        if (!Objects.equals(form.getStatus(), 1)) {
            throw new IllegalArgumentException("目标表单未启用");
        }
        UiConfigRelease activeRelease =
                releaseMapper.findActive("FORM", targetFormId);
        if (activeRelease == null
                || !Objects.equals(
                        form.getActiveReleaseId(),
                        activeRelease.getId())) {
            throw new IllegalArgumentException(
                    "目标表单没有可用的激活发布版本");
        }

        if (customOpenForm) {
            String mode = text(params.get("targetFormMode"), null);
            mode = StringUtils.hasText(mode)
                    ? mode.toUpperCase(java.util.Locale.ROOT)
                    : (TOOLBAR.equals(action.getPosition())
                            ? "CREATE"
                            : "VIEW");
            if (TOOLBAR.equals(action.getPosition())
                    && !"CREATE".equals(mode)) {
                throw new IllegalArgumentException(
                        "工具栏打开表单按钮仅支持新增模式");
            }
            if (ROW.equals(action.getPosition())
                    && !Set.of("VIEW", "EDIT").contains(mode)) {
                throw new IllegalArgumentException(
                        "行打开表单按钮仅支持查看或编辑模式");
            }
            params.put("targetFormMode", mode);
        } else {
            params.remove("targetFormMode");
        }
        // 参数名称由目标已发布页面声明；草稿中新增但未发布的参数不能作为按钮契约。
        if (!com.workflow.entity.ui.application.PageParameterPolicy.mappings(params.get("parameterMappings")).isEmpty()) {
            Map<String, Object> snapshot = codec.readObject(activeRelease.getSnapshotDocument(), "目标表单发布快照");
            Map<String, Object> targetView = com.workflow.entity.ui.application.PageParameterPolicy.map(
                    com.workflow.entity.ui.application.PageParameterPolicy.map(snapshot.get("form")).get("viewConfig"));
            Map<String, Object> properties = com.workflow.entity.ui.application.PageParameterPolicy.map(
                    com.workflow.entity.ui.application.PageParameterPolicy.map(targetView.get("inputParameterSchema")).get("properties"));
            for (Map<String, Object> mapping : com.workflow.entity.ui.application.PageParameterPolicy.mappings(params.get("parameterMappings"))) {
                if (!properties.containsKey(String.valueOf(mapping.get("parameter")))) throw new IllegalArgumentException("目标发布表单未声明输入参数: " + mapping.get("parameter"));
            }
        }
        params.put("targetFormId", targetFormId);
        action.setActionParamsDocument(codec.write(
                params,
                "列表按钮参数"));
    }

    /**
     * 校验并获取实体列表{@code relational}配置列表；不满足约束时阻止后续处理。
     *
     * @param listConfigId 列表配置ID，后续用于校验并获取实体列表{@code relational}配置列表时定位或关联目标
     * @return 校验并获取后的实体列表{@code relational}配置列表结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityListConfig requireList(String listConfigId) {
        EntityListConfig config = configMapper.selectById(listConfigId);
        if (config == null) throw new IllegalArgumentException("列表配置不存在");
        return config;
    }

    /**
     * 校验并获取动作；不满足约束时阻止后续处理。
     *
     * @param listConfigId 列表配置ID，后续用于校验并获取动作时定位或关联目标
     * @param actionId 动作ID，后续用于校验并获取动作时定位或关联目标
     * @return 校验并获取后的动作结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private EntityListAction requireAction(String listConfigId, String actionId) {
        EntityListAction action = actionMapper.selectById(actionId);
        if (action == null || !listConfigId.equals(action.getListConfigId())
                || Integer.valueOf(1).equals(action.getDeleted())) {
            throw new IllegalArgumentException("列表按钮不存在");
        }
        return action;
    }

    /**
     * 校验并获取修订版本；不满足约束时阻止后续处理。
     *
     * @param expected 预期，供本方法校验并获取修订版本时使用
     * @param current 当前，作为 {@code RevisionConflictException} 的输入影响后续处理
     */
    private void requireRevision(Integer expected, EntityListAction current) {
        if (expected == null || !expected.equals(current.getRevision())) {
            throw new RevisionConflictException("列表按钮已被其他人修改", current);
        }
    }

    /**
     * 处理下一步动作顺序，并将结果传给后续步骤。
     *
     * @param listConfigId 列表配置ID，后续用于处理下一步动作顺序时定位或关联目标
     * @param position 位置，作为 {@code actionMapper.findByListAndPosition} 的输入影响后续处理
     * @return 处理后的下一步动作顺序结果，供调用方继续处理
     */
    private long nextActionOrder(String listConfigId, String position) {
        List<EntityListAction> actions =
                actionMapper.findByListAndPosition(listConfigId, position);
        return actions.isEmpty()
                ? EntityFormNodeService.ORDER_STEP
                : actions.get(actions.size() - 1).getOrderKey()
                        + EntityFormNodeService.ORDER_STEP;
    }

    /**
     * 处理动作{@code boundary}，并将结果传给后续步骤。
     *
     * @param listConfigId 列表配置ID，后续用于处理动作{@code boundary}时定位或关联目标
     * @param position 位置，供本方法处理动作{@code boundary}时使用
     * @param actionId 动作ID，后续用于处理动作{@code boundary}时定位或关联目标
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的动作{@code boundary}结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 生成规范化位置文本，供后续匹配或展示。
     *
     * @param position 位置，供本方法处理规范化位置时使用
     * @return 处理后的规范化位置文本，供调用方比较或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private String normalizedPosition(String position) {
        String value = StringUtils.hasText(position)
                ? position.trim().toUpperCase()
                : TOOLBAR;
        if (!Set.of(TOOLBAR, ROW).contains(value)) {
            throw new IllegalArgumentException("按钮位置只能是 TOOLBAR 或 ROW");
        }
        return value;
    }

    /**
     * 处理更新访问时间列表，并将结果传给后续步骤。
     *
     * @param listConfigId 列表配置ID，后续用于处理更新访问时间列表时定位或关联目标
     */
    private void touchList(String listConfigId) {
        UpdateWrapper<EntityListConfig> wrapper = new UpdateWrapper<>();
        wrapper.eq("id", listConfigId)
                .setSql("revision = revision + 1")
                .set("draft_hash", null)
                .set("update_time", LocalDateTime.now());
        configMapper.update(null, wrapper);
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value, null);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private String text(Object value, String fallback) {
        return value == null || !StringUtils.hasText(String.valueOf(value))
                ? fallback
                : String.valueOf(value).trim();
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的整数结果，供调用方继续处理
     */
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

    /**
     * 处理可空整数，并将结果传给后续步骤。
     *
     * @param value 待处理可空整数的原始输入，结果供调用方继续使用
     * @return 处理后的可空整数结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 处理{@code long}值，并将结果传给后续步骤。
     *
     * @param value 待处理{@code long}值的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的{@code long}值结果，供调用方继续处理
     */
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

    /**
     * 历史发布按钮缺少 ID 时，按逻辑身份生成可重复的物理主键。
     *
     * @param listConfigId 列表配置ID，后续用于处理{@code deterministic}发布版本动作ID时定位或关联目标
     * @param position 位置，供本方法处理{@code deterministic}发布版本动作ID时使用
     * @param buttonKey 按钮键，后续用于授权校验、关联或幂等去重
     * @return 处理后的{@code deterministic}发布版本动作ID文本，供调用方比较或展示
     */
    private static String deterministicReleaseActionId(
            String listConfigId,
            String position,
            String buttonKey) {
        String identity = "ENTITY_LIST_RELEASE_ACTION:"
                + listConfigId.trim()
                + ":" + position
                + ":" + buttonKey;
        return UUID.nameUUIDFromBytes(
                        identity.getBytes(StandardCharsets.UTF_8))
                .toString()
                .replace("-", "");
    }

    /**
     * 生成兜底按钮键文本，供后续匹配或展示。
     *
     * @param position 位置，供本方法处理兜底按钮键时使用
     * @param index 索引，供本方法处理兜底按钮键时使用
     * @return 处理后的兜底按钮键文本，供调用方比较或展示
     */
    private static String fallbackButtonKey(
            String position,
            int index) {
        return position.toLowerCase(Locale.ROOT) + "_" + index;
    }

    /**
     * 生成发布版本文本文本，供后续匹配或展示。
     *
     * @param value 待处理发布版本文本的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的发布版本文本文本，供调用方比较或展示
     */
    private static String releaseText(Object value, String fallback) {
        return value == null || !StringUtils.hasText(String.valueOf(value))
                ? fallback
                : String.valueOf(value).trim();
    }

    /**
     * 处理发布版本整数，并将结果传给后续步骤。
     *
     * @param value 待处理发布版本整数的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的发布版本整数结果，供调用方继续处理
     */
    private static int releaseInteger(Object value, int fallback) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null
                    ? fallback
                    : Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /**
     * 处理发布版本{@code long}，并将结果传给后续步骤。
     *
     * @param value 待处理发布版本{@code long}的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的发布版本{@code long}结果，供调用方继续处理
     */
    private static long releaseLong(Object value, long fallback) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return value == null
                    ? fallback
                    : Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    /**
     * 生成JSON文档文本，供后续匹配或展示。
     *
     * @param value 待处理JSON文档的原始输入，结果供调用方继续使用
     * @param label 标签，后续用于处理JSON文档时匹配或展示
     * @return 处理后的JSON文档文本，供调用方比较或展示
     */
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

    /**
     * 把空白文本转为 null，避免后续把空字符串当作有效配置。
     *
     * @param value 待处理空白截止空值的原始输入，结果供调用方继续使用
     * @return 处理后的空白截止空值文本，供调用方比较或展示
     */
    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
