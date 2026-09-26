package com.workflow.embed.management.application;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.FormCoordinate;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.ListCoordinate;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.ListNode;
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
import com.workflow.embed.management.application.port.EmbedManagementRepository;
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

    /**
     * 初始化嵌入式启动记录运行时快照{@code materializer}，保存构造参数供后续方法使用。
     *
     * @param validator 校验器依赖，保存到当前对象供后续业务方法调用
     * @param repository 仓储依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param listDependencyPort 列表依赖端口依赖，保存到当前对象供后续业务方法调用
     * @param newDataFormRuntimePort 新数据表单运行时端口依赖，保存到当前对象供后续业务方法调用
     */
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
     *
     * @param viewId 视图ID，后续用于处理{@code materialize}时定位或关联目标
     * @param surfaceType 界面类型标识，决定后续{@code materialize}采用的处理分支
     * @param currentConfigJson 当前配置JSON，作为 {@code validator.validateCurrentActive} 的输入影响后续处理
     * @param materializedBy {@code materialized}，供本方法处理{@code materialize}时使用
     * @param now 当前时间，作为 {@code LocalDateTime.ofInstant} 的输入影响后续处理
     * @return 处理后的{@code materialize}结果，供调用方继续处理
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
     *
     * @param surface 界面，供本方法处理{@code materialized}配置时使用
     * @param validation 校验，作为 {@code read} 的输入影响后续处理
     * @return 处理后的{@code materialized}配置结果，供调用方继续处理
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
     *
     * @param root 根，作为 {@code ListCoordinate} 的输入影响后续处理
     * @return 构建后的列表依赖闭包结果，供调用方继续处理
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

    /**
     * 处理根默认表单，并将结果传给后续步骤。
     *
     * @param root 根，作为 {@code FormCoordinate} 的输入影响后续处理
     * @return 处理后的根默认表单结果，供调用方继续处理
     */
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

    /**
     * 解析默认表单；输出作为后续校验或处理的输入。
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 解析后的默认表单结果，供调用方继续处理
     */
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

    /**
     * 校验并获取相同坐标；不满足约束时阻止后续处理。
     *
     * @param requested 请求，供本方法校验并获取相同坐标时使用
     * @param resolved 已解析，供本方法校验并获取相同坐标时使用
     */
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

    /**
     * 生成坐标键文本，供后续匹配或展示。
     *
     * @param value 待处理坐标键的原始输入，结果供调用方继续使用
     * @return 处理后的坐标键文本，供调用方比较或展示
     */
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

    /**
     * 处理界面，并将结果传给后续步骤。
     *
     * @param value 待处理界面的原始输入，结果供调用方继续使用
     * @return 处理后的界面结果，供调用方继续处理
     */
    private SurfaceType surface(String value) {
        try {
            return SurfaceType.valueOf(value);
        } catch (RuntimeException error) {
            throw unavailableConfiguration();
        }
    }

    /**
     * 读取嵌入式启动记录运行时快照{@code materializer}；查询结果供调用方展示或继续处理。
     *
     * @param json JSON，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @return 读取后的嵌入式启动记录运行时快照{@code materializer}结果，供调用方继续处理
     */
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

    /**
     * 写入嵌入式启动记录运行时快照{@code materializer}；后续读取或执行将使用更新后的状态。
     *
     * @param value 待写入嵌入式启动记录运行时快照{@code materializer}的原始输入，结果供调用方继续使用
     * @return 写入后的嵌入式启动记录运行时快照{@code materializer}文本，供调用方比较或展示
     */
    private String write(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException error) {
            throw new EmbedException(
                    503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                    "Embed runtime snapshot cannot be serialized", null, error);
        }
    }

    /**
     * 生成规范化操作人文本，供后续匹配或展示。
     *
     * @param value 待处理规范化操作人的原始输入，结果供调用方继续使用
     * @return 处理后的规范化操作人文本，供调用方比较或展示
     */
    private static String normalizedActor(String value) {
        String actor = value == null ? "embed-runtime" : value.trim();
        return actor.isEmpty() || actor.length() > 64 ? "embed-runtime" : actor;
    }

    /**
     * 处理快照，并将结果传给后续步骤。
     *
     * @param value 待处理快照的原始输入，结果供调用方继续使用
     * @return 处理后的快照结果，供调用方继续处理
     */
    private static EmbedReleaseSnapshot snapshot(ReleaseState value) {
        return new EmbedReleaseSnapshot(
                value.id(), value.revision(), value.surfaceType().name(),
                value.entryModesJson(), value.capabilitiesJson(),
                value.contextSchemaJson(), value.uiConfigJson());
    }

    /**
     * 构造不可用配置异常，供调用方区分失败原因。
     *
     * @return 处理后的不可用配置结果，供调用方继续处理
     */
    private static EmbedException unavailableConfiguration() {
        return new EmbedException(
                403,
                EmbedErrorCode.EMBED_VIEW_DISABLED,
                "Embed configuration has no active Flow resource");
    }

    /**
     * 封装待处理列表的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param coordinate 坐标，保存在对象中供后续校验、查询或展示
     * @param depth 深度，保存在对象中供后续校验、查询或展示
     */
    private record PendingList(ListCoordinate coordinate, int depth) {
    }

    /**
     * 封装已解析默认表单的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param resolved 已解析，保存在对象中供后续校验、查询或展示
     * @param form 表单，保存在对象中供后续校验、查询或展示
     */
    private record ResolvedDefaultForm(
            boolean resolved,
            FormCoordinate form) {
    }

    /**
     * 封装{@code materialized}配置的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param canonicalConfig 规范配置内容，决定后续{@code materialized}配置的处理规则
     * @param configHash 配置哈希，保存在对象中供后续校验、查询或展示
     */
    private record MaterializedConfig(
            String canonicalConfig,
            String configHash) {
    }
}
