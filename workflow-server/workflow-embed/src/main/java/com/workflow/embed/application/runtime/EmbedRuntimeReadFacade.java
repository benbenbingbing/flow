package com.workflow.embed.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.runtime.port.EmbedNativeActorRuntimePort.ActorSnapshot;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure;
import com.workflow.contracts.embed.runtime.model.EmbedNativeListDependencyClosure.ListNode;
import com.workflow.contracts.embed.runtime.port.EmbedRuntimeEntityPort.Action;
import com.workflow.contracts.embed.runtime.port.EmbedRuntimeEntityPort.Field;
import com.workflow.contracts.embed.runtime.port.EmbedRuntimeEntityPort.ListPage;
import com.workflow.contracts.embed.runtime.port.EmbedRuntimeEntityPort.ListSchema;
import com.workflow.contracts.embed.runtime.port.EmbedRuntimeEntityPort.Row;
import com.workflow.contracts.embed.runtime.port.EmbedNativeActorRuntimePort;
import com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort;
import com.workflow.contracts.embed.runtime.port.EmbedNativeListRuntimePort;
import com.workflow.contracts.embed.runtime.port.EmbedRuntimeEntityPort;
import com.workflow.core.error.ForbiddenException;
import com.workflow.embed.api.web.EmbedRuntimeListFilterRequest;
import com.workflow.embed.api.web.EmbedRuntimeListQueryRequest;
import com.workflow.embed.api.web.EmbedRuntimeViews;
import com.workflow.embed.api.web.EmbedRuntimeViews.ExternalActionDescriptor;
import com.workflow.embed.application.port.EmbedRuntimeReleasePort;
import com.workflow.embed.config.EmbedProperties;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedRuntimeReleaseSnapshot;
import com.workflow.embed.domain.EmbedNativeFormTarget;
import com.workflow.embed.security.EmbedContextHolder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Read-only Embed Runtime use case.
 *
 * <p>It resolves all target coordinates and trusted filters from the authenticated Session plus immutable
 * Embed Release. The Entity adapter remains responsible for platform permissions and data-scope enforcement;
 * this facade applies the final External Projection whitelist before anything reaches the browser.</p>
 */
@Service
public class EmbedRuntimeReadFacade {

    private static final Set<String> PUBLIC_CAPABILITIES = Set.of(
            "LIST_QUERY", "SELECTION_RETURN", "RECORD_VIEW", "RECORD_CREATE", "ACTION_EXECUTE");
    private static final int MAX_FILTERS = 32;
    private static final int MAX_FILTER_VALUES = 100;

    private final EmbedRuntimeReleasePort releasePort;
    private final EmbedRuntimeEntityPort entityPort;
    private final EmbedProperties properties;
    private final ObjectMapper objectMapper;
    private final EmbedNativeFormTargetResolver nativeTargetResolver;
    private final EmbedNativeFormRuntimePort nativeFormRuntimePort;
    private final EmbedNativeListRuntimePort nativeListRuntimePort;
    private final EmbedNativeActorRuntimePort nativeActorRuntimePort;
    private final boolean legacyProjectionTestMode;

    /**
     * 初始化嵌入式运行时读取{@code facade}，保存构造参数供后续方法使用。
     *
     * @param releasePort 发布版本端口依赖，保存到当前对象供后续业务方法调用
     * @param entityPort 实体端口依赖，保存到当前对象供后续业务方法调用
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     * @param targetResolverProvider 目标解析器提供者，保存在对象中供后续校验、查询或展示
     * @param nativeFormRuntimePortProvider 原生表单运行时端口提供者，保存在对象中供后续校验、查询或展示
     * @param nativeListRuntimePortProvider 原生列表运行时端口提供者，保存在对象中供后续校验、查询或展示
     * @param nativeActorRuntimePortProvider 原生操作人运行时端口提供者，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public EmbedRuntimeReadFacade(
            EmbedRuntimeReleasePort releasePort,
            EmbedRuntimeEntityPort entityPort,
            EmbedProperties properties,
            ObjectMapper objectMapper,
            ObjectProvider<EmbedNativeFormTargetResolver> targetResolverProvider,
            ObjectProvider<EmbedNativeFormRuntimePort> nativeFormRuntimePortProvider,
            ObjectProvider<EmbedNativeListRuntimePort> nativeListRuntimePortProvider,
            ObjectProvider<EmbedNativeActorRuntimePort> nativeActorRuntimePortProvider) {
        this.releasePort = releasePort;
        this.entityPort = entityPort;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.nativeTargetResolver = targetResolverProvider.getIfAvailable();
        this.nativeFormRuntimePort = nativeFormRuntimePortProvider.getIfAvailable();
        this.nativeListRuntimePort = nativeListRuntimePortProvider.getIfAvailable();
        this.nativeActorRuntimePort = nativeActorRuntimePortProvider.getIfAvailable();
        this.legacyProjectionTestMode = false;
    }

    /**
     * 兼容纯 LIST 单元测试；生产 Spring Bean 使用上方完整构造器。
     *
     * @param releasePort 发布版本端口依赖，保存到当前对象供后续业务方法调用
     * @param entityPort 实体端口依赖，保存到当前对象供后续业务方法调用
     * @param properties 属性集合依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
    public EmbedRuntimeReadFacade(
            EmbedRuntimeReleasePort releasePort,
            EmbedRuntimeEntityPort entityPort,
            EmbedProperties properties,
            ObjectMapper objectMapper) {
        this.releasePort = releasePort;
        this.entityPort = entityPort;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.nativeTargetResolver = null;
        this.nativeFormRuntimePort = null;
        this.nativeListRuntimePort = null;
        this.nativeActorRuntimePort = null;
        this.legacyProjectionTestMode = true;
    }

    /**
     * 处理初始化，并将结果传给后续步骤。
     *
     * @return 处理后的初始化结果，供调用方继续处理
     */
    public EmbedRuntimeViews.Bootstrap bootstrap() {
        RuntimeTarget target = target();
        UiPolicy ui = uiPolicy(target.release());
        return new EmbedRuntimeViews.Bootstrap(
                new EmbedRuntimeViews.Session(
                        target.session().sessionId(),
                        target.session().absoluteExpiresAt(),
                        target.session().idleExpiresAt()),
                actor(target),
                new EmbedRuntimeViews.View(
                        target.release().viewKey(),
                        target.release().viewName(),
                        target.release().surfaceType(),
                        target.session().entryMode()),
                target.capabilities(),
                new EmbedRuntimeViews.Ui(
                        normalized(target.release().uiLocale(), "zh-CN"),
                        normalized(target.release().uiTheme(), "light"),
                        normalizedFormPresentation(target.release().uiFormPresentation()),
                        ui.showSearch(), ui.showPagination(), ui.showToolbar(),
                        ui.pageSize(), ui.heightMode()),
                new EmbedRuntimeViews.Limits(
                        ui.maxPageSize(),
                        properties.getMaxPayloadBytes(),
                        properties.getMaxSelectionSize()),
                nativeTarget(target));
    }

    /**
     * 原生页仅获取实时非敏感 UI 权限，请求授权仍以服务端为准。
     *
     * @param runtime 运行时，后续用于判断有效期或展示该事件的发生时间
     * @return 处理后的操作人结果，供调用方继续处理
     */
    private EmbedRuntimeViews.Actor actor(RuntimeTarget runtime) {
        if (nativeActorRuntimePort == null) {
            return new EmbedRuntimeViews.Actor(
                    runtime.session().flowUsername(),
                    normalized(
                            runtime.release().actorDisplayName(),
                            runtime.session().flowUsername()),
                    normalized(
                            runtime.release().actorDisplayName(),
                            runtime.session().flowUsername()),
                    List.of(), false, List.of());
        }
        ActorSnapshot actor =
                nativeActorRuntimePort.resolve(
                        runtime.session().flowUserId(),
                        runtime.session().flowUsername(),
                        runtime.release().actorDisplayName());
        return new EmbedRuntimeViews.Actor(
                actor.username(), actor.nickname(), actor.displayName(),
                actor.roles(), actor.isSuperAdmin(), actor.permissions());
    }

    /**
     * LIST/FORM 启动只下发原生运行时坐标，不下发字段投影。
     *
     * @param runtime 运行时，后续用于判断有效期或展示该事件的发生时间
     * @return 处理后的原生目标结果，供调用方继续处理
     */
    private EmbedRuntimeViews.Target nativeTarget(RuntimeTarget runtime) {
        boolean formSurface = "FORM".equals(runtime.release().surfaceType());
        boolean listSurface = "LIST".equals(runtime.release().surfaceType());
        if (!formSurface && !listSurface) {
            return null;
        }
        if (legacyProjectionTestMode) {
            return null;
        }
        if (nativeTargetResolver == null
                || formSurface && nativeFormRuntimePort == null
                || listSurface && nativeListRuntimePort == null) {
            throw runtimeUnavailable(null);
        }
        EmbedNativeFormTarget target = formSurface
                ? nativeTargetResolver.resolve(runtime.session())
                : nativeTargetResolver.resolveRoot(runtime.session());
        EmbedNativeListDependencyClosureCodec.Decoded listDependency =
                listSurface
                        ? EmbedNativeListDependencyClosureCodec.decode(
                        objectMapper, runtime.release().configJson())
                        : null;
        ListNode rootListNode = listSurface
                ? requireRootListNode(listDependency.closure(), target)
                : null;
        String formToken = null;
        if (StringUtils.hasText(target.formId())
                && StringUtils.hasText(target.formReleaseId())
                && target.formReleaseVersion() != null) {
            if (nativeFormRuntimePort == null) {
                throw runtimeUnavailable(null);
            }
            formToken = nativeFormRuntimePort.issueReleaseResolutionToken(
                    new com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.Target(
                            target.entityCode(), target.formId(),
                            target.formReleaseId(), target.formReleaseVersion(),
                            runtime.session().absoluteExpiresAt()));
        }
        String listToken = null;
        if (StringUtils.hasText(target.listKey())
                && StringUtils.hasText(target.listReleaseId())
                && target.listReleaseVersion() != null) {
            if (nativeListRuntimePort == null) {
                throw runtimeUnavailable(null);
            }
            listToken = nativeListRuntimePort.issueReleaseResolutionToken(
                    new com.workflow.contracts.embed.runtime.port.EmbedNativeListRuntimePort.Target(
                            target.entityCode(), target.listKey(),
                            target.listReleaseId(), target.listReleaseVersion(),
                            runtime.session().sessionId(),
                            runtime.session().viewId(),
                            runtime.session().viewReleaseId(),
                            runtime.session().absoluteExpiresAt(),
                            listDependency.closure().version(),
                            listDependency.hash(),
                            listDependency.closure()));
        }
        return new EmbedRuntimeViews.Target(
                target.entityCode(), target.formId(),
                target.formReleaseId(), target.formReleaseVersion(),
                target.listKey(), target.listReleaseId(),
                target.listReleaseVersion(), target.entryMode(),
                target.recordId(), target.processInstanceId(), formToken,
                formSurface || rootListNode.defaultFormResolved(), listToken,
                target.initialData(),
                target.parameters(), target.context());
    }

    /**
     * 根 LIST 顶层坐标和默认表单必须与同一 canonical closure 节点完全一致。
     *
     * @param closure 闭包，供本方法校验并获取根列表节点时使用
     * @param target 目标，供本方法校验并获取根列表节点时使用
     * @return 校验并获取后的根列表节点结果，供调用方继续处理
     */
    private static ListNode requireRootListNode(
            EmbedNativeListDependencyClosure closure,
            EmbedNativeFormTarget target) {
        if (closure == null
                || closure.version()
                != EmbedNativeListDependencyClosure.CURRENT_VERSION) {
            throw runtimeUnavailable(null);
        }
        ListNode root = closure.nodes().stream()
                .filter(node -> node != null && node.list() != null)
                .filter(node -> Objects.equals(
                        target.entityCode(), node.list().entityCode()))
                .filter(node -> Objects.equals(
                        target.listKey(), node.list().listKey()))
                .filter(node -> Objects.equals(
                        target.listReleaseId(),
                        node.list().listReleaseId()))
                .filter(node -> target.listReleaseVersion() != null
                        && target.listReleaseVersion()
                        == node.list().listReleaseVersion())
                .findFirst()
                .orElseThrow(() -> runtimeUnavailable(null));
        EmbedNativeListDependencyClosure.FormCoordinate form =
                root.defaultForm();
        boolean targetHasForm = StringUtils.hasText(target.formId())
                && StringUtils.hasText(target.formReleaseId())
                && target.formReleaseVersion() != null;
        boolean closureHasForm = form != null;
        if (!root.defaultFormResolved()
                || targetHasForm != closureHasForm
                || closureHasForm
                && (!Objects.equals(target.formId(), form.formId())
                || !Objects.equals(
                target.formReleaseId(), form.formReleaseId())
                || target.formReleaseVersion()
                != form.formReleaseVersion())) {
            throw runtimeUnavailable(null);
        }
        return root;
    }

    /**
     * 处理结构，并将结果传给后续步骤。
     *
     * @return 处理后的结构结果，供调用方继续处理
     */
    public EmbedRuntimeViews.Schema schema() {
        RuntimeTarget target = requireListTarget();
        ListSchema source = loadSchema(target.release());
        ProjectionPolicy policy = projectionPolicy(target.release());
        UiPolicy ui = uiPolicy(target.release());
        List<Field> visibleFields = source.fields().stream()
                .filter(Field::shown)
                .filter(field -> policy.visible().contains(field.code()))
                .toList();

        List<EmbedRuntimeViews.Column> columns = visibleFields.stream()
                .map(field -> new EmbedRuntimeViews.Column(
                        field.code(), field.label(), externalType(field.type()), field.width(), false,
                        options(field)))
                .toList();
        List<EmbedRuntimeViews.Filter> filters = source.fields().stream()
                .filter(Field::queryable)
                .filter(field -> policy.queryable().contains(field.code()))
                .filter(field -> safeExternalFilterCode(field.code()))
                .map(field -> new EmbedRuntimeViews.Filter(
                        field.code(), field.label(), externalType(field.type()),
                        externalOperator(field.queryOperator()), options(field)))
                .toList();

        List<String> returnableFields = visibleFields.stream()
                .map(Field::code)
                .filter(policy.returnable()::contains)
                .toList();
        EmbedRuntimeViews.Selection selection = selection(
                source.selection(), target.capabilities(), returnableFields);
        return new EmbedRuntimeViews.Schema(
                new EmbedRuntimeViews.SchemaView(
                        target.release().viewKey(), target.release().surfaceType()),
                new EmbedRuntimeViews.Entity(source.entityCode(), source.entityName()),
                new EmbedRuntimeViews.ListSchema(
                        selection,
                        new EmbedRuntimeViews.Pagination(ui.allowTotal(), ui.maxPageSize()),
                        columns,
                        filters),
                null,
                externalActions(source, policy.allowedActions(), target.capabilities()));
    }

    /**
     * 查询嵌入式运行时读取{@code facade}；查询结果供调用方展示或继续处理。
     *
     * @param request 本次请求，后续经校验后用于查询嵌入式运行时读取{@code facade}
     * @return 查询后的嵌入式运行时读取{@code facade}结果，供调用方继续处理
     */
    public EmbedRuntimeViews.ListResult query(EmbedRuntimeListQueryRequest request) {
        RuntimeTarget target = requireListTarget();
        ListSchema source = loadSchema(target.release());
        ProjectionPolicy policy = projectionPolicy(target.release());
        UiPolicy ui = uiPolicy(target.release());
        int pageNum = request == null || request.getPageNum() == null
                ? 1 : request.getPageNum();
        int requestedSize = request == null || request.getPageSize() == null
                ? ui.pageSize() : request.getPageSize();
        if (requestedSize > ui.maxPageSize()) {
            throw invalidRequest();
        }
        int pageSize = requestedSize;

        Map<String, Field> exposedFilters = new LinkedHashMap<>();
        source.fields().stream()
                .filter(Field::queryable)
                .filter(field -> policy.queryable().contains(field.code()))
                .filter(field -> safeExternalFilterCode(field.code()))
                .forEach(field -> exposedFilters.putIfAbsent(field.code(), field));
        Map<String, Object> filters = encodeClientFilters(
                request == null ? List.of() : request.getFilters(), exposedFilters);
        Map<String, Object> contextFilters = contextFilters(
                target.release(), target.session().context());

        ListPage page;
        try {
            page = entityPort.queryList(
                    target.release().entityCode(),
                    target.release().listKey(),
                    target.release().listReleaseId(),
                    target.release().listReleaseVersion(),
                    pageNum,
                    pageSize,
                    filters,
                    contextFilters);
        } catch (RuntimeException error) {
            throw runtimeUnavailable(error);
        }

        Set<String> allowedRowActions = externalActions(
                source, policy.allowedActions(), target.capabilities()).stream()
                .map(ExternalActionDescriptor::key)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<EmbedRuntimeViews.ListItem> items = page.rows().stream()
                .map(row -> listItem(row, policy.visible(), allowedRowActions))
                .toList();
        long offset = (long) Math.max(0, page.pageNum() - 1) * page.pageSize();
        boolean hasMore = offset + items.size() < page.total();
        return new EmbedRuntimeViews.ListResult(
                items,
                hasMore,
                page.pageNum(),
                page.pageSize(),
                ui.allowTotal() ? page.total() : null);
    }

    /**
     * 校验并获取列表目标；不满足约束时阻止后续处理。
     *
     * @return 校验并获取后的列表目标结果，供调用方继续处理
     */
    private RuntimeTarget requireListTarget() {
        RuntimeTarget target = target();
        if (!"LIST".equals(target.release().surfaceType())
                || !target.capabilities().contains("LIST_QUERY")
                || !StringUtils.hasText(target.release().listKey())
                || !StringUtils.hasText(target.release().listReleaseId())
                || target.release().listReleaseVersion() == null
                || target.release().listReleaseVersion() < 1) {
            throw operationNotAllowed();
        }
        return target;
    }

    /**
     * 处理目标，并将结果传给后续步骤。
     *
     * @return 处理后的目标结果，供调用方继续处理
     */
    private RuntimeTarget target() {
        AuthenticatedEmbedSession session = EmbedContextHolder.require();
        EmbedRuntimeReleaseSnapshot release = releasePort.find(
                session.sessionId(), session.viewId(), session.viewReleaseId());
        if (release == null) {
            throw new EmbedException(
                    503,
                    EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                    "Embed runtime is temporarily unavailable");
        }
        List<String> releaseCapabilities = stringArray(
                release.capabilitiesJson(), "capabilities");
        List<String> capabilities = releaseCapabilities.stream()
                .filter(PUBLIC_CAPABILITIES::contains)
                .filter(session.capabilities()::contains)
                .toList();
        return new RuntimeTarget(session, release, capabilities);
    }

    /**
     * 加载结构；查询结果供调用方展示或继续处理。
     *
     * @param release 发布版本，作为 {@code entityPort.loadListSchema} 的输入影响后续处理
     * @return 符合条件的列表结构结果，供调用方继续处理
     */
    private ListSchema loadSchema(EmbedRuntimeReleaseSnapshot release) {
        try {
            return entityPort.loadListSchema(
                    release.entityCode(), release.listKey(), release.listReleaseId(),
                    release.listReleaseVersion());
        } catch (RuntimeException error) {
            throw runtimeUnavailable(error);
        }
    }

    /**
     * 处理投影策略，并将结果传给后续步骤。
     *
     * @param release 发布版本，作为 {@code readObject} 的输入影响后续处理
     * @return 处理后的投影策略结果，供调用方继续处理
     */
    private ProjectionPolicy projectionPolicy(EmbedRuntimeReleaseSnapshot release) {
        JsonNode fields = readObject(release.fieldPolicyJson(), "fieldPolicy");
        JsonNode actions = readObject(release.actionPolicyJson(), "actionPolicy");
        return new ProjectionPolicy(
                stringSet(fields.path("visible")),
                stringSet(fields.path("queryable")),
                stringSet(fields.path("returnable")),
                stringSet(actions.path("allowed")));
    }

    /**
     * 处理界面策略，并将结果传给后续步骤。
     *
     * @param release 发布版本，作为 {@code readObject} 的输入影响后续处理
     * @return 处理后的界面策略结果，供调用方继续处理
     */
    private UiPolicy uiPolicy(EmbedRuntimeReleaseSnapshot release) {
        JsonNode ui = readObject(release.uiConfigJson(), "ui");
        JsonNode config = readObject(release.configJson(), "config");
        JsonNode queryPolicy = config.path("queryPolicy");
        int configuredMax = boundedInt(
                queryPolicy.path("maxPageSize"), properties.getMaxPageSize(),
                properties.getMaxPageSize());
        int pageSize = boundedInt(ui.path("pageSize"), 20, configuredMax);
        String heightMode = ui.path("heightMode").asText("AUTO").toUpperCase(Locale.ROOT);
        if (!Set.of("AUTO", "FIXED").contains(heightMode)) {
            heightMode = "AUTO";
        }
        return new UiPolicy(
                ui.path("showSearch").asBoolean(true),
                ui.path("showPagination").asBoolean(true),
                ui.path("showToolbar").asBoolean(true),
                pageSize,
                heightMode,
                queryPolicy.path("allowTotal").asBoolean(false),
                configuredMax);
    }

    /**
     * 整理上下文过滤条件数据，供调用方遍历或继续处理。
     *
     * @param release 发布版本，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @param context 执行上下文，向后续上下文过滤条件步骤传递身份、配置或状态
     * @return 上下文过滤条件键值结果，供调用方继续处理
     */
    private Map<String, Object> contextFilters(
            EmbedRuntimeReleaseSnapshot release,
            Map<String, Object> context) {
        JsonNode bindings;
        try {
            bindings = objectMapper.readTree(release.contextBindingsJson());
        } catch (JsonProcessingException error) {
            throw corruptedRelease("contextBindings", error);
        }
        if (bindings == null || !bindings.isArray()) {
            throw corruptedRelease("contextBindings", null);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (JsonNode binding : bindings) {
            if (!"FIXED_FILTER".equals(binding.path("usage").asText())) {
                continue;
            }
            String source = binding.path("source").asText();
            String target = binding.path("target").asText();
            if (!StringUtils.hasText(source) || !StringUtils.hasText(target)
                    || context == null || !context.containsKey(source)) {
                throw corruptedRelease("contextBindings", null);
            }
            Object value = context.get(source);
            if (value == null || !scalar(value)
                    || result.containsKey(target)
                    || result.containsKey(target + "_op")) {
                throw corruptedRelease("contextBindings", null);
            }
            result.put(target, value);
            result.put(target + "_op", "EQ");
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 将无操作符的外部过滤 DTO 编码为 Entity 列表内部条件键。
     *
     * <p>发布字段 descriptor 是操作符的唯一可信来源。浏览器不能提交
     * {@code _op/_start/_end}，也不能通过值形状改变已发布操作符。</p>
     *
     * @param requested 请求，供本方法编码客户端过滤条件时使用
     * @param allowed 允许，供本方法编码客户端过滤条件时使用
     * @return 客户端过滤条件键值结果，供调用方继续处理
     */
    private Map<String, Object> encodeClientFilters(
            List<EmbedRuntimeListFilterRequest> requested,
            Map<String, Field> allowed) {
        if (requested == null || requested.isEmpty()) {
            return Map.of();
        }
        if (requested.size() > MAX_FILTERS) {
            throw invalidRequest();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        Set<String> seen = new LinkedHashSet<>();
        for (EmbedRuntimeListFilterRequest requestedFilter : requested) {
            if (requestedFilter == null
                    || !requestedFilter.isShapeAllowed()
                    || !safeExternalFilterCode(requestedFilter.getField())
                    || !seen.add(requestedFilter.getField())) {
                throw invalidRequest();
            }
            Field publishedField = allowed.get(requestedFilter.getField());
            if (publishedField == null) {
                throw invalidRequest();
            }
            encodeClientFilter(
                    result,
                    publishedField.code(),
                    externalOperator(publishedField.queryOperator()),
                    requestedFilter);
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 编码客户端过滤；输出作为后续校验或处理的输入。
     *
     * @param target 目标，作为 {@code putOperatorFilter} 的输入影响后续处理
     * @param fieldCode 字段编码，后续用于编码客户端过滤时定位或关联目标
     * @param operator 操作人，作为 {@code putOperatorFilter} 的输入影响后续处理
     * @param requested 请求，作为 {@code requiredScalar} 的输入影响后续处理
     */
    private void encodeClientFilter(
            Map<String, Object> target,
            String fieldCode,
            String operator,
            EmbedRuntimeListFilterRequest requested) {
        switch (operator) {
            case "EQ", "CONTAINS", "GT", "GTE", "LT", "LTE" -> {
                Object value = requiredScalar(requested);
                switch (operator) {
                    case "EQ" -> putOperatorFilter(target, fieldCode, value, "EQ");
                    case "CONTAINS" -> putOperatorFilter(target, fieldCode, value, "LIKE");
                    case "GT", "LT" -> putOperatorFilter(target, fieldCode, value, operator);
                    // Entity 的既有条件契约用单边范围表达 >= 和 <=，不能透传浏览器操作符。
                    case "GTE" -> target.put(fieldCode + "_start", value);
                    case "LTE" -> target.put(fieldCode + "_end", value);
                    default -> throw invalidRequest();
                }
            }
            case "IN" -> {
                if (!requested.hasValues()
                        || requested.getValues() == null
                        || requested.getValues().isEmpty()
                        || requested.getValues().size() > MAX_FILTER_VALUES
                        || requested.getValues().stream().anyMatch(value -> !safeFilterScalar(value))) {
                    throw invalidRequest();
                }
                putOperatorFilter(
                        target,
                        fieldCode,
                        List.copyOf(requested.getValues()),
                        "IN");
            }
            case "BETWEEN" -> {
                EmbedRuntimeListFilterRequest.Range range = requested.getRange();
                if (!requested.hasRange()
                        || range == null
                        || !range.isShapeAllowed()
                        || !safeFilterScalar(range.getStart())
                        || !safeFilterScalar(range.getEnd())) {
                    throw invalidRequest();
                }
                target.put(fieldCode + "_start", range.getStart());
                target.put(fieldCode + "_end", range.getEnd());
            }
            default -> throw invalidRequest();
        }
    }

    /**
     * 处理必填标量，并将结果传给后续步骤。
     *
     * @param requested 请求，供本方法处理必填标量时使用
     * @return 处理后的必填标量结果，供调用方继续处理
     */
    private static Object requiredScalar(EmbedRuntimeListFilterRequest requested) {
        if (!requested.hasValue() || !safeFilterScalar(requested.getValue())) {
            throw invalidRequest();
        }
        return requested.getValue();
    }

    /**
     * 写入操作人过滤；后续读取或执行将使用更新后的状态。
     *
     * @param target 目标，供本方法写入操作人过滤时使用
     * @param fieldCode 字段编码，后续用于写入操作人过滤时定位或关联目标
     * @param value 待写入操作人过滤的原始输入，结果供调用方继续使用
     * @param internalOperator 内部操作人，作为 {@code target.put} 的输入影响后续处理
     */
    private static void putOperatorFilter(
            Map<String, Object> target,
            String fieldCode,
            Object value,
            String internalOperator) {
        target.put(fieldCode, value);
        target.put(fieldCode + "_op", internalOperator);
    }

    /**
     * 列出条目；查询结果供调用方展示或继续处理。
     *
     * @param row 行，作为 {@code EmbedRuntimeViews.ListItem} 的输入影响后续处理
     * @param visible 可见，作为 {@code actions.put} 的输入影响后续处理
     * @param allowedActions 允许动作集合，供本方法列出条目时使用
     * @return 符合条件的嵌入式运行时{@code views.list}条目结果，供调用方继续处理
     */
    private EmbedRuntimeViews.ListItem listItem(
            Row row,
            Set<String> visible,
            Set<String> allowedActions) {
        Map<String, Object> values = new LinkedHashMap<>();
        row.values().forEach((key, value) -> {
            if (visible.contains(key)) {
                values.put(key, value);
            }
        });
        Map<String, EmbedRuntimeViews.ItemActionCapability> actions = new LinkedHashMap<>();
        row.actionCapabilities().forEach((key, value) -> {
            if (allowedActions.contains(key)) {
                actions.put(key, new EmbedRuntimeViews.ItemActionCapability(
                        value.visible(),
                        value.enabled(),
                        emptyToNull(value.reason())));
            }
        });
        return new EmbedRuntimeViews.ListItem(
                row.id(),
                null,
                Collections.unmodifiableMap(values),
                new EmbedRuntimeViews.ItemMeta(row.updatedAt()),
                Collections.unmodifiableMap(actions));
    }

    /**
     * 整理外部动作集合数据，供调用方遍历或继续处理。
     *
     * @param schema 结构，供本方法处理外部动作集合时使用
     * @param allowed 允许，供本方法处理外部动作集合时使用
     * @param capabilities 能力集合，供本方法处理外部动作集合时使用
     * @return 外部动作描述集合，供调用方遍历或展示
     */
    private List<ExternalActionDescriptor> externalActions(
            ListSchema schema,
            Set<String> allowed,
            Collection<String> capabilities) {
        Map<String, Action> source = new LinkedHashMap<>();
        schema.toolbarActions().forEach(action -> source.putIfAbsent(action.key(), action));
        schema.rowActions().forEach(action -> source.putIfAbsent(action.key(), action));
        List<ExternalActionDescriptor> result = new ArrayList<>();
        for (String key : allowed) {
            Action action = source.get(key);
            if (action == null) {
                continue;
            }
            if ("view".equals(key) && capabilities.contains("RECORD_VIEW")) {
                result.add(action(action, "NAVIGATION", "LOCAL_FORM", "CURRENT", "NONE"));
            } else if ("create".equals(key) && capabilities.contains("RECORD_CREATE")) {
                result.add(action(action, "NAVIGATION", "LOCAL_FORM", "NONE", "NONE"));
            }
        }
        return List.copyOf(result);
    }

    /**
     * 处理动作，并将结果传给后续步骤。
     *
     * @param action 动作标识，决定后续动作采用的处理分支
     * @param kind 类型，供本方法处理动作时使用
     * @param transport 传输，供本方法处理动作时使用
     * @param recordMode 记录模式标识，决定后续动作采用的处理分支
     * @param selectionMode 选择模式标识，决定后续动作采用的处理分支
     * @return 处理后的动作结果，供调用方继续处理
     */
    private static ExternalActionDescriptor action(
            Action action,
            String kind,
            String transport,
            String recordMode,
            String selectionMode) {
        return new ExternalActionDescriptor(
                action.key(), action.label(), action.placement(), kind, transport,
                recordMode, selectionMode, null, false, null, false);
    }

    /**
     * 处理选择，并将结果传给后续步骤。
     *
     * @param source 待处理选择的原始输入，结果供调用方继续使用
     * @param capabilities 能力集合，供本方法处理选择时使用
     * @param returnableFields {@code returnable}字段，作为 {@code EmbedRuntimeViews.Selection} 的输入影响后续处理
     * @return 处理后的选择结果，供调用方继续处理
     */
    private static EmbedRuntimeViews.Selection selection(
            Map<String, Object> source,
            Collection<String> capabilities,
            List<String> returnableFields) {
        if (!capabilities.contains("SELECTION_RETURN")) {
            return new EmbedRuntimeViews.Selection("NONE", "id", List.of());
        }
        String mode = normalized(Objects.toString(source.get("mode"), null), "SINGLE")
                .toUpperCase(Locale.ROOT);
        if (!Set.of("SINGLE", "MULTIPLE").contains(mode)) {
            mode = "SINGLE";
        }
        String valueField = normalized(
                Objects.toString(source.get("valueField"), null), "id");
        if (!"id".equals(valueField) && !returnableFields.contains(valueField)) {
            valueField = "id";
        }
        return new EmbedRuntimeViews.Selection(mode, valueField, returnableFields);
    }

    /**
     * 整理选项数据，供调用方遍历或继续处理。
     *
     * @param field 字段，供本方法处理选项时使用
     * @return 嵌入式运行时视图集合，供调用方遍历或展示
     */
    private List<EmbedRuntimeViews.Option> options(Field field) {
        if (!"SELECT".equals(externalType(field.type()))) {
            return null;
        }
        return field.options().stream()
                .map(value -> new EmbedRuntimeViews.Option(value.label(), value.value()))
                .toList();
    }

    /**
     * 生成外部类型文本，供后续匹配或展示。
     *
     * @param value 待处理外部类型的原始输入，结果供调用方继续使用
     * @return 处理后的外部类型文本，供调用方比较或展示
     */
    private static String externalType(String value) {
        String normalized = normalized(value, "TEXT").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SELECT", "SINGLE_SELECT", "RADIO", "ENUM", "MULTI_SELECT", "CHECKBOX" -> "SELECT";
            case "NUMBER", "INTEGER", "LONG", "DECIMAL", "MONEY" -> "NUMBER";
            case "DATE", "DATETIME", "TIME", "BOOLEAN" -> normalized;
            default -> "TEXT";
        };
    }

    /**
     * 生成外部操作人文本，供后续匹配或展示。
     *
     * @param value 待处理外部操作人的原始输入，结果供调用方继续使用
     * @return 处理后的外部操作人文本，供调用方比较或展示
     */
    private static String externalOperator(String value) {
        String normalized = normalized(value, "EQ").toUpperCase(Locale.ROOT);
        return Set.of("EQ", "CONTAINS", "GT", "GTE", "LT", "LTE", "IN", "BETWEEN")
                .contains(normalized) ? normalized : "EQ";
    }

    /**
     * 整理字符串数组数据，供调用方遍历或继续处理。
     *
     * @param json JSON，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @param label 标签，后续用于处理字符串数组时匹配或展示
     * @return 嵌入式运行时读取{@code facade}集合，供调用方遍历或展示
     */
    private List<String> stringArray(String json, String label) {
        JsonNode node;
        try {
            node = objectMapper.readTree(json);
        } catch (JsonProcessingException error) {
            throw corruptedRelease(label, error);
        }
        if (node == null || !node.isArray()) {
            throw corruptedRelease(label, null);
        }
        List<String> result = new ArrayList<>();
        for (JsonNode item : node) {
            if (!item.isTextual() || !StringUtils.hasText(item.asText())) {
                throw corruptedRelease(label, null);
            }
            result.add(item.asText());
        }
        return List.copyOf(result);
    }

    /**
     * 读取对象；查询结果供调用方展示或继续处理。
     *
     * @param json JSON，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @param label 标签，后续用于读取对象时匹配或展示
     * @return 读取后的对象结果，供调用方继续处理
     */
    private JsonNode readObject(String json, String label) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isObject()) {
                throw corruptedRelease(label, null);
            }
            return node;
        } catch (JsonProcessingException error) {
            throw corruptedRelease(label, error);
        }
    }

    /**
     * 整理字符串设置数据，供调用方遍历或继续处理。
     *
     * @param array 数组，供本方法处理字符串设置时使用
     * @return 嵌入式运行时读取{@code facade}集合，供调用方遍历或展示
     */
    private static Set<String> stringSet(JsonNode array) {
        if (!array.isArray()) {
            throw corruptedRelease("projectionPolicy", null);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonNode value : array) {
            if (!value.isTextual() || !StringUtils.hasText(value.asText())) {
                throw corruptedRelease("projectionPolicy", null);
            }
            result.add(value.asText());
        }
        return Collections.unmodifiableSet(result);
    }

    /**
     * 处理{@code bounded}整数，并将结果传给后续步骤。
     *
     * @param node 节点，供本方法处理{@code bounded}整数时使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @param max 最大，作为 {@code Math.max} 的输入影响后续处理
     * @return 处理后的{@code bounded}整数结果，供调用方继续处理
     */
    private static int boundedInt(JsonNode node, int fallback, int max) {
        int value = node.canConvertToInt() ? node.asInt() : fallback;
        return Math.max(1, Math.min(max, value));
    }

    /**
     * 判断安全外部过滤编码条件是否成立，供调用方选择后续分支。
     *
     * @param fieldCode 字段编码，后续用于处理安全外部过滤编码时定位或关联目标
     * @return 安全外部过滤编码条件成立时为 true，否则为 false
     */
    private static boolean safeExternalFilterCode(String fieldCode) {
        if (!StringUtils.hasText(fieldCode) || fieldCode.length() > 100) {
            return false;
        }
        String normalized = fieldCode.toLowerCase(Locale.ROOT);
        return !normalized.endsWith("_op")
                && !normalized.endsWith("_start")
                && !normalized.endsWith("_end");
    }

    /**
     * 判断安全过滤标量条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理安全过滤标量的原始输入，结果供调用方继续使用
     * @return 安全过滤标量条件成立时为 true，否则为 false
     */
    private static boolean safeFilterScalar(Object value) {
        return value != null
                && scalar(value)
                && (!(value instanceof String text)
                || (!text.isBlank() && text.length() <= 2048));
    }

    /**
     * 判断标量条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理标量的原始输入，结果供调用方继续使用
     * @return 标量条件成立时为 true，否则为 false
     */
    private static boolean scalar(Object value) {
        return value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean;
    }

    /**
     * 生成空截止空值文本，供后续匹配或展示。
     *
     * @param value 待处理空截止空值的原始输入，结果供调用方继续使用
     * @return 处理后的空截止空值文本，供调用方比较或展示
     */
    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    /**
     * 生成规范化文本，供后续匹配或展示。
     *
     * @param value 待处理规范化的原始输入，结果供调用方继续使用
     * @param fallback 兜底，主值不可用时供后续处理兜底
     * @return 处理后的规范化文本，供调用方比较或展示
     */
    private static String normalized(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    /**
     * Legacy/null snapshots use the V1 seamless default; constrained values pass through.
     *
     * @param value 待处理规范化表单展示的原始输入，结果供调用方继续使用
     * @return 处理后的规范化表单展示文本，供调用方比较或展示
     */
    private static String normalizedFormPresentation(String value) {
        return "dialog".equals(value) ? "dialog" : "seamless";
    }

    /**
     * 构造无效请求异常，供调用方区分失败原因。
     *
     * @return 处理后的无效请求结果，供调用方继续处理
     */
    private static EmbedException invalidRequest() {
        return new EmbedException(400, EmbedErrorCode.INVALID_REQUEST, "Embed request is invalid");
    }

    /**
     * 构造操作非允许异常，供调用方区分失败原因。
     *
     * @return 处理后的操作非允许结果，供调用方继续处理
     */
    private static EmbedException operationNotAllowed() {
        return new EmbedException(
                403, EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED,
                "Embed operation is not allowed");
    }

    /**
     * 构造运行时不可用异常，供调用方区分失败原因。
     *
     * @param cause 原因，供本方法处理运行时不可用时使用
     * @return 处理后的运行时不可用结果，供调用方继续处理
     */
    private static EmbedException runtimeUnavailable(Throwable cause) {
        if (cause instanceof EmbedException embed) {
            return embed;
        }
        if (cause instanceof ForbiddenException) {
            return operationNotAllowed();
        }
        return new EmbedException(
                503,
                EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed runtime is temporarily unavailable",
                null,
                cause);
    }

    /**
     * 构造{@code corrupted}发布版本异常，供调用方区分失败原因。
     *
     * @param field 字段，作为 {@code IllegalStateException} 的输入影响后续处理
     * @param cause 原因，供本方法处理{@code corrupted}发布版本时使用
     * @return 处理后的{@code corrupted}发布版本结果，供调用方继续处理
     */
    private static EmbedException corruptedRelease(String field, Throwable cause) {
        return new EmbedException(
                503,
                EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed runtime is temporarily unavailable",
                null,
                cause == null ? new IllegalStateException("Invalid release " + field) : cause);
    }

    /**
     * 封装运行时目标的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param session 会话，保存在对象中供后续校验、查询或展示
     * @param release 发布版本，保存在对象中供后续校验、查询或展示
     * @param capabilities 能力集合，保存在对象中供后续校验、查询或展示
     */
    private record RuntimeTarget(
            AuthenticatedEmbedSession session,
            EmbedRuntimeReleaseSnapshot release,
            List<String> capabilities) {
    }

    /**
     * 封装投影策略的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param visible 可见，保存在对象中供后续校验、查询或展示
     * @param queryable {@code queryable}，保存在对象中供后续校验、查询或展示
     * @param returnable {@code returnable}，保存在对象中供后续校验、查询或展示
     * @param allowedActions 允许动作集合，保存在对象中供后续校验、查询或展示
     */
    private record ProjectionPolicy(
            Set<String> visible,
            Set<String> queryable,
            Set<String> returnable,
            Set<String> allowedActions) {
    }

    /**
     * 封装界面策略的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param showSearch {@code show}{@code search}，保存在对象中供后续校验、查询或展示
     * @param showPagination {@code show}{@code pagination}，保存在对象中供后续校验、查询或展示
     * @param showToolbar {@code show}{@code toolbar}，保存在对象中供后续校验、查询或展示
     * @param pageSize 分页大小参数，用于限制后续查询范围和返回数量
     * @param heightMode {@code height}模式标识，决定后续界面策略采用的处理分支
     * @param allowTotal 允许总数，保存在对象中供后续校验、查询或展示
     * @param maxPageSize 最大分页大小，保存在对象中供后续校验、查询或展示
     */
    private record UiPolicy(
            boolean showSearch,
            boolean showPagination,
            boolean showToolbar,
            int pageSize,
            String heightMode,
            boolean allowTotal,
            int maxPageSize) {
    }
}
