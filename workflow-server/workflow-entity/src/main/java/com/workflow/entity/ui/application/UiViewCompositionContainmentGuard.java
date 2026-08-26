package com.workflow.entity.ui.application;

import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.serialization.JsonDocumentCodec;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 校验关联内容自动嵌入形成的跨表单/列表包含图。
 *
 * <p>INLINE、TAB 和 ROW_EXPAND 会把目标内容继续渲染在当前页面结构中，
 * 因此必须在发布、预览和历史激活共用的快照校验阶段阻断资产循环，并将
 * 当前宿主计为第一层，最多允许八层。弹窗、抽屉和新页面由用户显式触发，
 * 不属于自动包含图；它们可以形成业务导航环，由运行时签名链另行限制重入。</p>
 */
@Component
@RequiredArgsConstructor
public class UiViewCompositionContainmentGuard {

    static final int MAX_CONTAINMENT_DEPTH = 8;

    private static final Set<String> CONTAINMENT_POSITIONS = Set.of(
            "INLINE", "TAB", "ROW_EXPAND");

    private final UiConfigReleaseMapper releaseMapper;
    private final JsonDocumentCodec codec;

    /**
     * 校验一份待生效宿主快照的完整自动包含子图。
     *
     * @param ownerType FORM 或 LIST
     * @param ownerId 当前宿主资产 ID
     * @param ownerSnapshot 待发布、预览或激活的宿主快照
     * @throws BusinessConflictException 包含图成环、超过八层，或传递依赖快照失效
     */
    public void validate(
            String ownerType,
            String ownerId,
            Map<String, Object> ownerSnapshot) {
        AssetKey rootKey = new AssetKey(
                normalize(ownerType), requireText(ownerId, "宿主配置 ID"));
        Node root = new Node(
                rootKey,
                null,
                null,
                null,
                ownerSnapshot,
                assetLabel(rootKey, ownerSnapshot));
        List<Node> path = new ArrayList<>();
        Map<AssetKey, Integer> pathIndex = new LinkedHashMap<>();
        Map<String, Node> releaseCache = new HashMap<>();
        visit(root, 1, path, pathIndex, releaseCache);
    }

    /**
     * 深度优先遍历包含边。重复资产以 FORM/LIST + 配置 ID 判定，这样即便
     * 两条边固定到了该资产的不同历史发布版本，也不会构造出用户无法理解的
     * “同一页面包含自己”结构；发布版本仍作为节点缓存和完整性校验的一部分。
     */
    private void visit(
            Node node,
            int depth,
            List<Node> path,
            Map<AssetKey, Integer> pathIndex,
            Map<String, Node> releaseCache) {
        if (depth > MAX_CONTAINMENT_DEPTH) {
            throw depthExceeded(path, node);
        }

        pathIndex.put(node.asset(), path.size());
        path.add(node);
        try {
            for (ContainmentEdge edge : containmentEdges(node.snapshot())) {
                Node target = loadTarget(edge, releaseCache);
                Integer repeatedAt = pathIndex.get(target.asset());
                if (repeatedAt != null) {
                    throw cycleDetected(path, repeatedAt, target, edge);
                }
                visit(
                        target,
                        depth + 1,
                        path,
                        pathIndex,
                        releaseCache);
            }
        } finally {
            path.remove(path.size() - 1);
            pathIndex.remove(node.asset());
        }
    }

    private List<ContainmentEdge> containmentEdges(
            Map<String, Object> snapshot) {
        if (snapshot == null
                || !(snapshot.get("viewCompositions") instanceof List<?> items)) {
            return List.of();
        }
        List<ContainmentEdge> result = new ArrayList<>();
        for (Object rawItem : items) {
            if (!(rawItem instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> item = stringMap(rawMap);
            Map<String, Object> config = map(item.get("config"));
            if (Boolean.FALSE.equals(config.get("enabled"))) {
                continue;
            }
            String position = normalize(text(
                    map(config.get("presentation")).get("position")));
            if (!CONTAINMENT_POSITIONS.contains(position)) {
                continue;
            }
            Map<String, Object> target = map(config.get("target"));
            result.add(new ContainmentEdge(
                    requireText(item.get("compositionKey"), "关联内容编码"),
                    new AssetKey(
                            normalize(requireText(
                                    target.get("contentType"),
                                    "目标内容类型")),
                            requireText(
                                    target.get("contentId"),
                                    "目标表单或列表")),
                    requireText(
                            target.get("releaseId"),
                            "目标发布版本"),
                    positiveInteger(
                            target.get("releaseVersion"),
                            "目标发布版本号"),
                    normalizeHash(requireText(
                            target.get("contentHash"),
                            "目标发布内容哈希"))));
        }
        return List.copyOf(result);
    }

    /**
     * 传递遍历只接受边上精确固定且哈希完整的发布记录，避免包含图检查因读取
     * 当前 ACTIVE 版本而漏掉旧宿主真正会渲染的子图。
     */
    private Node loadTarget(
            ContainmentEdge edge,
            Map<String, Node> releaseCache) {
        Node cached = releaseCache.get(edge.releaseId());
        if (cached != null) {
            if (!cached.asset().equals(edge.target())
                    || !Objects.equals(
                    cached.releaseVersion(), edge.releaseVersion())
                    || !Objects.equals(
                    cached.contentHash(), edge.contentHash())) {
                throw invalidDependency(edge, "发布版本所属目标不一致");
            }
            return cached;
        }
        UiConfigRelease release = releaseMapper.selectById(edge.releaseId());
        if (release == null
                || !edge.target().type().equals(normalize(
                release.getConfigType()))
                || !edge.target().id().equals(release.getConfigId())
                || !Objects.equals(edge.releaseVersion(), release.getVersion())
                || !edge.contentHash().equals(normalizeHash(
                release.getContentHash()))
                || !StringUtils.hasText(release.getSnapshotDocument())) {
            throw invalidDependency(edge, "固定的发布版本不存在或版本信息不一致");
        }
        String canonical = codec.canonicalize(
                release.getSnapshotDocument(),
                "关联内容目标发布快照");
        if (!edge.contentHash().equals(sha256(canonical))) {
            throw invalidDependency(edge, "固定的发布快照完整性校验失败");
        }
        Map<String, Object> snapshot = codec.readObject(
                canonical, "关联内容目标发布快照");
        Map<String, Object> owner = map(snapshot.get(
                edge.target().type().toLowerCase(Locale.ROOT)));
        if (!edge.target().type().equals(normalize(text(
                snapshot.get("configType"))))
                || !edge.target().id().equals(text(owner.get("id")))) {
            throw invalidDependency(edge, "发布快照内的目标身份不一致");
        }
        Node target = new Node(
                edge.target(),
                edge.releaseId(),
                edge.releaseVersion(),
                edge.contentHash(),
                snapshot,
                assetLabel(edge.target(), snapshot));
        releaseCache.put(edge.releaseId(), target);
        return target;
    }

    private BusinessConflictException cycleDetected(
            List<Node> path,
            int repeatedAt,
            Node target,
            ContainmentEdge edge) {
        List<String> labels = new ArrayList<>();
        for (int index = repeatedAt; index < path.size(); index++) {
            labels.add(path.get(index).label());
        }
        labels.add(target.label());
        return new BusinessConflictException(
                "UI_VIEW_COMPOSITION_CONTAINMENT_CYCLE",
                "关联内容“" + edge.compositionKey()
                        + "”形成自动嵌入循环："
                        + String.join(" → ", labels)
                        + "。请将循环中的一项改为弹窗、抽屉或新页面显示。");
    }

    private BusinessConflictException depthExceeded(
            List<Node> path,
            Node target) {
        List<String> labels = path.stream()
                .map(Node::label)
                .collect(java.util.stream.Collectors.toCollection(
                        ArrayList::new));
        labels.add(target.label());
        return new BusinessConflictException(
                "UI_VIEW_COMPOSITION_CONTAINMENT_DEPTH_EXCEEDED",
                "关联内容自动嵌入层级超过 "
                        + MAX_CONTAINMENT_DEPTH + " 层："
                        + String.join(" → ", labels)
                        + "。请缩短嵌入层级，或将更深层内容改为弹窗、抽屉或新页面显示。");
    }

    private BusinessConflictException invalidDependency(
            ContainmentEdge edge,
            String reason) {
        return new BusinessConflictException(
                "UI_VIEW_COMPOSITION_CONTAINMENT_DEPENDENCY_INVALID",
                "关联内容“" + edge.compositionKey() + "”" + reason
                        + "，无法安全检查自动嵌入层级");
    }

    private String assetLabel(
            AssetKey asset,
            Map<String, Object> snapshot) {
        Map<String, Object> owner = map(snapshot == null ? null : snapshot.get(
                asset.type().toLowerCase(Locale.ROOT)));
        String name = firstText(
                owner.get("formName"),
                owner.get("listName"),
                owner.get("name"),
                owner.get("formKey"),
                owner.get("listKey"));
        String kind = "FORM".equals(asset.type()) ? "表单" : "列表";
        return kind + "“" + (StringUtils.hasText(name)
                ? name : asset.id()) + "”";
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = text(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return null;
    }

    private int positiveInteger(Object value, String label) {
        if (!(value instanceof Number number)
                || number.intValue() < 1
                || number.doubleValue() != number.intValue()) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_CONTAINMENT_DEPENDENCY_INVALID",
                    label + "必须为正整数，无法安全检查自动嵌入层级");
        }
        return number.intValue();
    }

    private String requireText(Object value, String label) {
        String result = text(value);
        if (!StringUtils.hasText(result)) {
            throw new BusinessConflictException(
                    "UI_VIEW_COMPOSITION_CONTAINMENT_DEPENDENCY_INVALID",
                    label + "不能为空，无法安全检查自动嵌入层级");
        }
        return result;
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(
                            value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "运行环境不支持 SHA-256", exception);
        }
    }

    private String normalizeHash(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalize(String value) {
        return StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value).trim();
    }

    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> raw ? stringMap(raw) : Map.of();
    }

    private Map<String, Object> stringMap(Map<?, ?> raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        raw.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private record AssetKey(String type, String id) {
    }

    private record Node(
            AssetKey asset,
            String releaseId,
            Integer releaseVersion,
            String contentHash,
            Map<String, Object> snapshot,
            String label) {
    }

    private record ContainmentEdge(
            String compositionKey,
            AssetKey target,
            String releaseId,
            Integer releaseVersion,
            String contentHash) {
    }
}
