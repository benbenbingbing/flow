package com.workflow.entity.ui.application;

import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 发现当前表单变更影响的关联宿主；表单按稳定身份、列表按固定发布记录追踪。 */
final class CurrentFormCompositionDependencyIndex {
    private record Asset(String type, String id) { }

    /**
     * 返回直接或传递引用变更表单的已发布宿主，包括历史列表和手工导航环。
     * 本索引只读取引用坐标；调用方仍须校验受影响快照的完整性、参数和包含图。
     */
    static List<UiConfigRelease> affectedOwners(String formId, List<UiConfigRelease> owners, JsonDocumentCodec codec) {
        Map<Asset, Set<Asset>> referrers = new HashMap<>();
        for (UiConfigRelease owner : owners) {
            Asset host = asset(owner);
            Map<String, Object> snapshot = codec.readObject(owner.getSnapshotDocument(), "关联依赖索引 " + owner.getId());
            if (!(snapshot.get("viewCompositions") instanceof List<?> items)) continue;
            for (Object item : items) {
                if (!(item instanceof Map<?, ?> row) || !(row.get("config") instanceof Map<?, ?> config)
                        || !(config.get("target") instanceof Map<?, ?> target)) continue;
                String type = String.valueOf(target.get("contentType"));
                Object id = target.get("FORM".equals(type) ? "contentId" : "releaseId");
                if (!Set.of("FORM", "LIST").contains(type) || id == null || String.valueOf(id).isBlank()) continue;
                referrers.computeIfAbsent(new Asset(type, String.valueOf(id)), ignored -> new LinkedHashSet<>()).add(host);
            }
        }
        // 列表不同历史版本不能合并为同一个节点；否则无关版本会被当成当前表单的依赖。
        Set<Asset> affected = new LinkedHashSet<>();
        ArrayDeque<Asset> pending = new ArrayDeque<>();
        Asset changed = new Asset("FORM", formId);
        affected.add(changed); pending.add(changed);
        while (!pending.isEmpty()) {
            for (Asset parent : referrers.getOrDefault(pending.removeFirst(), Set.of()))
                if (affected.add(parent)) pending.addLast(parent);
        }
        List<UiConfigRelease> result = new ArrayList<>();
        for (UiConfigRelease owner : owners) if (affected.contains(asset(owner))) result.add(owner);
        return result;
    }

    private static Asset asset(UiConfigRelease row) {
        return new Asset(row.getConfigType(), "FORM".equals(row.getConfigType()) ? row.getConfigId() : row.getId());
    }
}
