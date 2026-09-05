package com.workflow.entity.list.infrastructure.adapter;

import com.workflow.contracts.embed.EmbedNativeListDependencyClosure.ListCoordinate;
import com.workflow.contracts.embed.runtime.port.EmbedNativeListDependencyRuntimePort;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.list.infrastructure.persistence.mapper.EntityListConfigMapper;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 从 Entity 的不可变 List Release 恢复 Embed open-list 直接依赖。
 *
 * <p>适配器只解释导航坐标，不投影列、按钮或组件。每条边都重新校验目标列表 ID、
 * 实体、listKey 与 Release 归属，物化器因此不会把发布快照中的伪造坐标写入闭包。</p>
 */
@Component
public class EntityEmbedNativeListDependencyRuntimeAdapter
        implements EmbedNativeListDependencyRuntimePort {

    private static final Comparator<ListCoordinate> COORDINATE_ORDER =
            Comparator.comparing(ListCoordinate::entityCode)
                    .thenComparing(ListCoordinate::listKey)
                    .thenComparing(ListCoordinate::listConfigId)
                    .thenComparing(ListCoordinate::listReleaseId)
                    .thenComparingInt(ListCoordinate::listReleaseVersion);

    private final EntityListConfigMapper listConfigMapper;
    private final UiConfigReleaseService releaseService;

    public EntityEmbedNativeListDependencyRuntimeAdapter(
            EntityListConfigMapper listConfigMapper,
            UiConfigReleaseService releaseService) {
        this.listConfigMapper = listConfigMapper;
        this.releaseService = releaseService;
    }

    /**
     * 校验精确列表版本并抽取 toolbar/row action 中的 open-list 边。
     */
    @Override
    public ResolvedList resolveExact(ListCoordinate requested) {
        EntityListConfig owner = requireOwner(requested);
        UiConfigReleaseService.ResolvedEntityListRelease resolved =
                releaseService.resolveServerPinnedRuntimeListRelease(
                        owner.getId(), requested.listReleaseId(),
                        requested.listReleaseVersion());
        EntityListConfigDTO list = resolved.list();
        if (list == null
                || !Objects.equals(requested.entityCode(), list.getEntityCode())
                || !Objects.equals(requested.listKey(), list.getListKey())
                || !Objects.equals(requested.listReleaseId(), resolved.releaseId())
                || !Objects.equals(requested.listReleaseVersion(),
                resolved.releaseVersion())) {
            throw new IllegalArgumentException("Embed 列表依赖发布坐标归属不一致");
        }
        ListCoordinate exact = new ListCoordinate(
                requested.entityCode(), requested.listKey(), owner.getId(),
                resolved.releaseId(), resolved.releaseVersion());
        LinkedHashSet<ListCoordinate> targets = new LinkedHashSet<>();
        collectTargets(list.getToolbarConfig(), targets);
        collectTargets(list.getRowActionConfig(), targets);
        return new ResolvedList(
                exact,
                targets.stream().sorted(COORDINATE_ORDER).toList());
    }

    private EntityListConfig requireOwner(ListCoordinate requested) {
        if (requested == null
                || !StringUtils.hasText(requested.entityCode())
                || !StringUtils.hasText(requested.listKey())
                || !StringUtils.hasText(requested.listReleaseId())
                || requested.listReleaseVersion() < 1) {
            throw new IllegalArgumentException("Embed 列表依赖坐标不完整");
        }
        EntityListConfig owner = listConfigMapper.findByEntityCodeAndListKey(
                requested.entityCode(), requested.listKey());
        if (owner == null
                || !StringUtils.hasText(owner.getId())
                || StringUtils.hasText(requested.listConfigId())
                && !Objects.equals(requested.listConfigId(), owner.getId())) {
            throw new IllegalArgumentException("Embed 列表依赖目标不存在或已被替换");
        }
        return owner;
    }

    private void collectTargets(
            List<Map<String, Object>> buttons,
            LinkedHashSet<ListCoordinate> targets) {
        for (Map<String, Object> button : buttons == null
                ? List.<Map<String, Object>>of() : buttons) {
            if (!"open-list".equalsIgnoreCase(text(button.get("customMode")))) {
                continue;
            }
            String entityCode = text(button.get("targetEntityCode"));
            String listKey = text(button.get("targetListKey"));
            String listConfigId = text(button.get("targetListId"));
            String releaseId = text(button.get("targetListReleaseId"));
            Integer releaseVersion = integer(
                    button.get("targetListReleaseVersion"));
            if (!StringUtils.hasText(entityCode)
                    || !StringUtils.hasText(listKey)
                    || !StringUtils.hasText(listConfigId)
                    || !StringUtils.hasText(releaseId)
                    || releaseVersion == null || releaseVersion < 1) {
                throw new IllegalArgumentException(
                        "Embed open-list 目标缺少精确发布坐标");
            }
            // resolveExact 在递归阶段还会验证目标 Release；这里先固定完整坐标，
            // 并在当前发布快照边界阻止同 entity/listKey 的 listId 替换。
            EntityListConfig owner = listConfigMapper
                    .findByEntityCodeAndListKey(entityCode, listKey);
            if (owner == null || !Objects.equals(listConfigId, owner.getId())) {
                throw new IllegalArgumentException(
                        "Embed open-list 目标列表归属不一致");
            }
            targets.add(new ListCoordinate(
                    entityCode, listKey, listConfigId,
                    releaseId, releaseVersion));
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Integer integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            String text = text(value);
            return text == null ? null : Integer.valueOf(text);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
