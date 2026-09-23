package com.workflow.entity.list.infrastructure.adapter;

import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.ListCoordinate;
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

    /**
     * 初始化实体嵌入式原生列表依赖运行时适配器，保存构造参数供后续方法使用。
     *
     * @param listConfigMapper 列表配置映射器依赖，保存到当前对象供后续业务方法调用
     * @param releaseService 发布版本服务依赖，保存到当前对象供后续业务方法调用
     */
    public EntityEmbedNativeListDependencyRuntimeAdapter(
            EntityListConfigMapper listConfigMapper,
            UiConfigReleaseService releaseService) {
        this.listConfigMapper = listConfigMapper;
        this.releaseService = releaseService;
    }

    /**
     * 校验精确列表版本并抽取 toolbar/row action 中的 open-list 边。
     *
     * @param requested 请求，作为 {@code requireOwner} 的输入影响后续处理
     * @return 解析后的精确结果，供调用方继续处理
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

    /**
     * 校验并获取归属方；不满足约束时阻止后续处理。
     *
     * @param requested 请求，作为 {@code listConfigMapper.findByEntityCodeAndListKey} 的输入影响后续处理
     * @return 校验并获取后的归属方结果，供调用方继续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 收集目标集合；结果供调用方的后续步骤使用。
     *
     * @param buttons 按钮集合，供本方法收集目标集合时使用
     * @param targets 目标集合，供本方法收集目标集合时使用
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
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

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param value 待处理文本的原始输入，结果供调用方继续使用
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 将输入解析为整数，供后续范围校验或计算使用。
     *
     * @param value 待处理整数的原始输入，结果供调用方继续使用
     * @return 处理后的整数结果，供调用方继续处理
     */
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
