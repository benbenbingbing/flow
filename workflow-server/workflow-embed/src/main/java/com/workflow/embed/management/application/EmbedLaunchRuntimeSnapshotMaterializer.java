package com.workflow.embed.management.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure.FormCoordinate;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure.ListCoordinate;
import com.workflow.contracts.embed.EmbedNativeListDependencyClosure.ListNode;
import com.workflow.contracts.embed.runtime.port.EmbedNativeListDependencyRuntimePort.ResolvedList;
import com.workflow.contracts.embed.runtime.port.EmbedNativeListDependencyRuntimePort;
import com.workflow.contracts.entity.form.port.EntityNewDataFormRuntimePort.ResolvedForm;
import com.workflow.contracts.entity.form.port.EntityNewDataFormRuntimePort;
import com.workflow.embed.application.port.EmbedRuntimeSnapshotMaterializationPort;
import com.workflow.embed.application.runtime.EmbedNativeListDependencyClosureCodec;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedReleaseSnapshot;
import com.workflow.embed.management.domain.EmbedManagementModel.ReleaseState;
import com.workflow.embed.management.domain.EmbedManagementModel.SurfaceType;
import com.workflow.embed.management.domain.EmbedManagementModel.ValidationResult;
import com.workflow.embed.management.port.EmbedManagementRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 把 View 当前配置解析为一次 Launch 专属的内部 Runtime Snapshot。
 *
 * <p>调用方已经锁定 View；这里始终解析 Flow 当前 ACTIVE 发布版本，忽略历史
 * releasePolicy/pinned 配置。内部沿用 {@code embed_view_release} 只为兼容现有表结构和
 * Session 外键，不更新 {@code published_release_id}，也不向产品暴露 revision。</p>
 */
@Component
public class EmbedLaunchRuntimeSnapshotMaterializer
        implements EmbedRuntimeSnapshotMaterializationPort {

    private static final int MAX_LIST_DEPENDENCY_DEPTH = 8;
    private static final int MAX_LIST_DEPENDENCY_NODES = 64;
    private static final int MAX_LIST_DEPENDENCY_EDGES = 256;
    private static final int MAX_RUNTIME_SNAPSHOT_BYTES = 262_144;
    private static final Comparator<ListCoordinate> COORDINATE_ORDER =
            Comparator.comparing(ListCoordinate::entityCode)
                    .thenComparing(ListCoordinate::listKey)
                    .thenComparing(ListCoordinate::listConfigId)
                    .thenComparing(ListCoordinate::listReleaseId)
                    .thenComparingInt(ListCoordinate::listReleaseVersion);

    private final EmbedViewConfigurationValidator validator;
    private final EmbedManagementRepository repository;
    private final ObjectMapper objectMapper;
    private final EmbedNativeListDependencyRuntimePort listDependencyPort;
    private final EntityNewDataFormRuntimePort newDataFormRuntimePort;

    public EmbedLaunchRuntimeSnapshotMaterializer(
            EmbedViewConfigurationValidator validator,
            EmbedManagementRepository repository,
            ObjectMapper objectMapper,
            EmbedNativeListDependencyRuntimePort listDependencyPort,
            EntityNewDataFormRuntimePort newDataFormRuntimePort) {
        this.validator = validator;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.listDependencyPort = listDependencyPort;
        this.newDataFormRuntimePort = newDataFormRuntimePort;
    }

    /**
     * 解析并持久化当前 ACTIVE 资源坐标；任一校验失败都会回滚整个 Launch 事务。
     */
    @Override
    @Transactional(propagation = Propagation.MANDATORY, rollbackFor = Exception.class)
    public EmbedReleaseSnapshot materialize(
            String viewId,
            String surfaceType,
            String currentConfigJson,
            String materializedBy,
            Instant now) {
        SurfaceType surface = surface(surfaceType);
        ValidationResult validation = validator.validateCurrentActive(
                surface, read(currentConfigJson));
        if (!validation.valid() || validation.resolved() == null) {
            throw unavailableConfiguration();
        }
        MaterializedConfig materialized = materializedConfig(
                surface, validation);
        ReleaseState existing = repository.findReleaseByConfigHash(
                viewId, materialized.configHash());
        if (existing != null) {
            // configHash 包含 resolved ACTIVE 坐标；同配置、同 ACTIVE 资源直接复用，
            // 只有配置或 Flow active release 变化才新增内部快照。
            return snapshot(existing);
        }
        JsonNode config = read(materialized.canonicalConfig());
        long internalRevision = repository.nextReleaseRevision(viewId);
        LocalDateTime materializedAt = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        ReleaseState snapshot = new ReleaseState(
                "evr_" + IdWorker.getIdStr(),
                viewId,
                internalRevision,
                surface,
                validation.resolved().entityCode(),
                validation.resolved().listKey(),
                validation.resolved().defaultFormId(),
                validation.resolved().listReleaseId(),
                validation.resolved().listReleaseVersion(),
                validation.resolved().formReleaseId(),
                validation.resolved().formReleaseVersion(),
                write(config.path("entryModes")),
                write(config.path("capabilities")),
                write(config.path("fieldPolicy")),
                write(config.path("actionPolicy")),
                write(config.path("contextSchema")),
                write(config.path("contextBindings")),
                write(config.path("ui")),
                materialized.canonicalConfig(),
                materialized.configHash(),
                null,
                normalizedActor(materializedBy),
                materializedAt);
        repository.insertRelease(snapshot);
        return snapshot(snapshot);
    }

    /**
     * 把 LIST 的完整导航依赖闭包加入 canonical 文档，再重新计算 configHash。
     *
     * <p>目标默认表单 ACTIVE 的变化会改变摘要并产生新快照；已经创建的 Session
     * 继续引用旧快照，因此不会在页面停留期间漂移。</p>
     */
    private MaterializedConfig materializedConfig(
            SurfaceType surface,
            ValidationResult validation) {
        ObjectNode config = (ObjectNode) read(
                validation.canonicalConfig()).deepCopy();
        // 闭包只能由服务端从 exact Release 生成，绝不能继承管理草稿或历史快照值。
        config.remove(EmbedNativeListDependencyClosureCodec.CONFIG_FIELD);
        config.remove(EmbedNativeListDependencyClosureCodec.HASH_FIELD);
        if (surface == SurfaceType.LIST) {
            try {
                EmbedNativeListDependencyClosure closure =
                        buildListDependencyClosure(validation.resolved());
                JsonNode closureNode = objectMapper.valueToTree(closure);
                config.set(
                        EmbedNativeListDependencyClosureCodec.CONFIG_FIELD,
                        closureNode);
                config.put(
                        EmbedNativeListDependencyClosureCodec.HASH_FIELD,
                        EmbedNativeListDependencyClosureCodec.canonicalHash(
                                objectMapper, closureNode));
            } catch (EmbedException error) {
                throw error;
            } catch (RuntimeException error) {
                throw new EmbedException(
                        403,
                        EmbedErrorCode.EMBED_VIEW_DISABLED,
                        "Embed list dependency closure cannot be materialized",
                        null,
                        error);
            }
        }
        String canonical = write(validator.canonicalize(config));
        if (canonical.getBytes(StandardCharsets.UTF_8).length
                > MAX_RUNTIME_SNAPSHOT_BYTES) {
            throw unavailableConfiguration();
        }
        return new MaterializedConfig(
                canonical,
                EmbedViewConfigurationValidator.sha256(canonical));
    }

    /**
     * 从根 exact List Release 做有界广度遍历；同坐标只解析一次，但保留循环边。
     */
    private EmbedNativeListDependencyClosure buildListDependencyClosure(
            com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource root) {
        if (listDependencyPort == null || newDataFormRuntimePort == null
                || root == null
                || !StringUtils.hasText(root.entityCode())
                || !StringUtils.hasText(root.listKey())
                || !StringUtils.hasText(root.listReleaseId())
                || root.listReleaseVersion() == null
                || root.listReleaseVersion() < 1L
                || root.listReleaseVersion() > Integer.MAX_VALUE) {
            throw unavailableConfiguration();
        }
        ListCoordinate rootRequest = new ListCoordinate(
                root.entityCode(), root.listKey(), null,
                root.listReleaseId(), root.listReleaseVersion().intValue());
        Queue<PendingList> pending = new ArrayDeque<>();
        pending.add(new PendingList(rootRequest, 0));
        Map<String, ListNode> nodes = new LinkedHashMap<>();
        Map<String, ResolvedDefaultForm> formsByEntity =
                new LinkedHashMap<>();
        formsByEntity.put(root.entityCode(), rootDefaultForm(root));
        int edgeCount = 0;

        while (!pending.isEmpty()) {
            PendingList item = pending.remove();
            String requestedKey = coordinateKey(item.coordinate());
            if (nodes.containsKey(requestedKey)) {
                continue;
            }
            if (item.depth() > MAX_LIST_DEPENDENCY_DEPTH
                    || nodes.size() >= MAX_LIST_DEPENDENCY_NODES) {
                throw unavailableConfiguration();
            }
            ResolvedList resolved =
                    listDependencyPort.resolveExact(item.coordinate());
            requireSameCoordinate(item.coordinate(), resolved.list());
            List<ListCoordinate> targets = resolved.openListTargets().stream()
                    .distinct()
                    .sorted(COORDINATE_ORDER)
                    .toList();
            edgeCount += targets.size();
            if (edgeCount > MAX_LIST_DEPENDENCY_EDGES) {
                throw unavailableConfiguration();
            }
            ResolvedDefaultForm defaultForm = formsByEntity.computeIfAbsent(
                    resolved.list().entityCode(),
                    this::resolveDefaultForm);
            nodes.put(
                    coordinateKey(resolved.list()),
                    new ListNode(
                            resolved.list(),
                            defaultForm.resolved(),
                            defaultForm.form(),
                            targets));
            // 队列去重由 exact coordinate key 完成；循环边仍保留在节点 targets 中，
            // 运行时可以按原生页面导航，但不会导致物化递归失控。
            for (ListCoordinate target : targets) {
                if (!nodes.containsKey(coordinateKey(target))) {
                    pending.add(new PendingList(target, item.depth() + 1));
                }
            }
        }
        List<ListNode> ordered = new ArrayList<>(nodes.values());
        ordered.sort(Comparator.comparing(ListNode::list, COORDINATE_ORDER));
        return new EmbedNativeListDependencyClosure(
                EmbedNativeListDependencyClosure.CURRENT_VERSION,
                ordered);
    }

    private ResolvedDefaultForm rootDefaultForm(
            com.workflow.embed.management.domain.EmbedManagementModel.ResolvedResource root) {
        boolean hasId = StringUtils.hasText(root.defaultFormId());
        boolean hasRelease = StringUtils.hasText(root.formReleaseId());
        boolean hasVersion = root.formReleaseVersion() != null;
        if (!(hasId == hasRelease && hasRelease == hasVersion)
                || hasVersion && (root.formReleaseVersion() < 1L
                || root.formReleaseVersion() > Integer.MAX_VALUE)) {
            throw unavailableConfiguration();
        }
        FormCoordinate form = hasId
                ? new FormCoordinate(
                root.defaultFormId(), root.formReleaseId(),
                root.formReleaseVersion().intValue())
                : null;
        return new ResolvedDefaultForm(true, form);
    }

    private ResolvedDefaultForm resolveDefaultForm(String entityCode) {
        ResolvedForm resolved =
                newDataFormRuntimePort.resolveForNewData(entityCode)
                        .orElse(null);
        if (resolved == null) {
            // resolved=true + null 是权威“当次没有新增表单”，运行时不得回退 ACTIVE。
            return new ResolvedDefaultForm(true, null);
        }
        if (!StringUtils.hasText(resolved.formId())
                || !StringUtils.hasText(resolved.releaseId())
                || resolved.releaseVersion() == null
                || resolved.releaseVersion() < 1) {
            throw unavailableConfiguration();
        }
        return new ResolvedDefaultForm(
                true,
                new FormCoordinate(
                        resolved.formId(), resolved.releaseId(),
                        resolved.releaseVersion()));
    }

    private static void requireSameCoordinate(
            ListCoordinate requested,
            ListCoordinate resolved) {
        if (resolved == null
                || !Objects.equals(requested.entityCode(), resolved.entityCode())
                || !Objects.equals(requested.listKey(), resolved.listKey())
                || StringUtils.hasText(requested.listConfigId())
                && !Objects.equals(
                requested.listConfigId(), resolved.listConfigId())
                || !Objects.equals(
                requested.listReleaseId(), resolved.listReleaseId())
                || requested.listReleaseVersion()
                != resolved.listReleaseVersion()
                || !StringUtils.hasText(resolved.listConfigId())) {
            throw unavailableConfiguration();
        }
    }

    private static String coordinateKey(ListCoordinate value) {
        if (value == null) {
            throw unavailableConfiguration();
        }
        return String.join("\u001f",
                String.valueOf(value.entityCode()),
                String.valueOf(value.listKey()),
                String.valueOf(value.listReleaseId()),
                String.valueOf(value.listReleaseVersion()));
    }

    private SurfaceType surface(String value) {
        try {
            return SurfaceType.valueOf(value);
        } catch (RuntimeException error) {
            throw unavailableConfiguration();
        }
    }

    private JsonNode read(String json) {
        try {
            JsonNode result = objectMapper.readTree(json);
            if (result == null || !result.isObject()) {
                throw unavailableConfiguration();
            }
            return result;
        } catch (JsonProcessingException error) {
            throw unavailableConfiguration();
        }
    }

    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new EmbedException(
                    503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                    "Embed runtime snapshot cannot be serialized", null, error);
        }
    }

    private static String normalizedActor(String value) {
        String actor = value == null ? "embed-runtime" : value.trim();
        return actor.isEmpty() || actor.length() > 64 ? "embed-runtime" : actor;
    }

    private static EmbedReleaseSnapshot snapshot(ReleaseState value) {
        return new EmbedReleaseSnapshot(
                value.id(), value.revision(), value.surfaceType().name(),
                value.entryModesJson(), value.capabilitiesJson(),
                value.contextSchemaJson(), value.uiConfigJson());
    }

    private static EmbedException unavailableConfiguration() {
        return new EmbedException(
                403,
                EmbedErrorCode.EMBED_VIEW_DISABLED,
                "Embed configuration has no active Flow resource");
    }

    private record PendingList(ListCoordinate coordinate, int depth) {
    }

    private record ResolvedDefaultForm(
            boolean resolved,
            FormCoordinate form) {
    }

    private record MaterializedConfig(
            String canonicalConfig,
            String configHash) {
    }
}
