package com.workflow.embed.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedRuntimeEntityPort;
import com.workflow.contracts.embed.EmbedRuntimeEntityPort.Action;
import com.workflow.contracts.embed.EmbedRuntimeEntityPort.Field;
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
            "LIST_QUERY", "SELECTION_RETURN", "RECORD_VIEW", "RECORD_CREATE");
    private static final int MAX_FILTERS = 32;
    private static final int MAX_FILTER_VALUES = 100;

    private final EmbedRuntimeReleasePort releasePort;
    private final EmbedRuntimeEntityPort entityPort;
    private final EmbedProperties properties;
    private final ObjectMapper objectMapper;

    public EmbedRuntimeReadFacade(
            EmbedRuntimeReleasePort releasePort,
            EmbedRuntimeEntityPort entityPort,
            EmbedProperties properties,
            ObjectMapper objectMapper) {
        this.releasePort = releasePort;
        this.entityPort = entityPort;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public EmbedRuntimeViews.Bootstrap bootstrap() {
        RuntimeTarget target = target();
        UiPolicy ui = uiPolicy(target.release());
        return new EmbedRuntimeViews.Bootstrap(
                new EmbedRuntimeViews.Session(
                        target.session().sessionId(),
                        target.session().absoluteExpiresAt(),
                        target.session().idleExpiresAt()),
                new EmbedRuntimeViews.Actor(target.release().actorDisplayName()),
                new EmbedRuntimeViews.View(
                        target.release().viewKey(),
                        target.release().viewName(),
                        target.release().surfaceType(),
                        target.release().revision(),
                        target.session().entryMode()),
                target.capabilities(),
                new EmbedRuntimeViews.Ui(
                        normalized(target.release().uiLocale(), "zh-CN"),
                        normalized(target.release().uiTheme(), "light"),
                        ui.showSearch(), ui.showPagination(), ui.showToolbar(),
                        ui.pageSize(), ui.heightMode()),
                new EmbedRuntimeViews.Limits(
                        ui.maxPageSize(),
                        properties.getMaxPayloadBytes(),
                        properties.getMaxSelectionSize()));
    }

    public EmbedRuntimeViews.Schema schema() {
        RuntimeTarget target = requireListTarget();
        EmbedRuntimeEntityPort.ListSchema source = loadSchema(target.release());
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
                        target.release().viewKey(), target.release().surfaceType(),
                        target.release().revision()),
                new EmbedRuntimeViews.Entity(source.entityCode(), source.entityName()),
                new EmbedRuntimeViews.ListSchema(
                        selection,
                        new EmbedRuntimeViews.Pagination(ui.allowTotal(), ui.maxPageSize()),
                        columns,
                        filters),
                null,
                externalActions(source, policy.allowedActions(), target.capabilities()));
    }

    public EmbedRuntimeViews.ListResult query(EmbedRuntimeListQueryRequest request) {
        RuntimeTarget target = requireListTarget();
        EmbedRuntimeEntityPort.ListSchema source = loadSchema(target.release());
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

        EmbedRuntimeEntityPort.ListPage page;
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

    private EmbedRuntimeEntityPort.ListSchema loadSchema(EmbedRuntimeReleaseSnapshot release) {
        try {
            return entityPort.loadListSchema(
                    release.entityCode(), release.listKey(), release.listReleaseId(),
                    release.listReleaseVersion());
        } catch (RuntimeException error) {
            throw runtimeUnavailable(error);
        }
    }

    private ProjectionPolicy projectionPolicy(EmbedRuntimeReleaseSnapshot release) {
        JsonNode fields = readObject(release.fieldPolicyJson(), "fieldPolicy");
        JsonNode actions = readObject(release.actionPolicyJson(), "actionPolicy");
        return new ProjectionPolicy(
                stringSet(fields.path("visible")),
                stringSet(fields.path("queryable")),
                stringSet(fields.path("returnable")),
                stringSet(actions.path("allowed")));
    }

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

    private static Object requiredScalar(EmbedRuntimeListFilterRequest requested) {
        if (!requested.hasValue() || !safeFilterScalar(requested.getValue())) {
            throw invalidRequest();
        }
        return requested.getValue();
    }

    private static void putOperatorFilter(
            Map<String, Object> target,
            String fieldCode,
            Object value,
            String internalOperator) {
        target.put(fieldCode, value);
        target.put(fieldCode + "_op", internalOperator);
    }

    private EmbedRuntimeViews.ListItem listItem(
            EmbedRuntimeEntityPort.Row row,
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
                        value.visible(), value.enabled(), emptyToNull(value.reason())));
            }
        });
        return new EmbedRuntimeViews.ListItem(
                row.id(),
                null,
                Collections.unmodifiableMap(values),
                new EmbedRuntimeViews.ItemMeta(row.updatedAt()),
                Collections.unmodifiableMap(actions));
    }

    private List<ExternalActionDescriptor> externalActions(
            EmbedRuntimeEntityPort.ListSchema schema,
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

    private List<EmbedRuntimeViews.Option> options(Field field) {
        if (!"SELECT".equals(externalType(field.type()))) {
            return null;
        }
        return field.options().stream()
                .map(value -> new EmbedRuntimeViews.Option(value.label(), value.value()))
                .toList();
    }

    private static String externalType(String value) {
        String normalized = normalized(value, "TEXT").toUpperCase(Locale.ROOT);
        return switch (normalized) {
            case "SELECT", "SINGLE_SELECT", "RADIO", "ENUM", "MULTI_SELECT", "CHECKBOX" -> "SELECT";
            case "NUMBER", "INTEGER", "LONG", "DECIMAL", "MONEY" -> "NUMBER";
            case "DATE", "DATETIME", "TIME", "BOOLEAN" -> normalized;
            default -> "TEXT";
        };
    }

    private static String externalOperator(String value) {
        String normalized = normalized(value, "EQ").toUpperCase(Locale.ROOT);
        return Set.of("EQ", "CONTAINS", "GT", "GTE", "LT", "LTE", "IN", "BETWEEN")
                .contains(normalized) ? normalized : "EQ";
    }

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

    private static int boundedInt(JsonNode node, int fallback, int max) {
        int value = node.canConvertToInt() ? node.asInt() : fallback;
        return Math.max(1, Math.min(max, value));
    }

    private static boolean safeExternalFilterCode(String fieldCode) {
        if (!StringUtils.hasText(fieldCode) || fieldCode.length() > 100) {
            return false;
        }
        String normalized = fieldCode.toLowerCase(Locale.ROOT);
        return !normalized.endsWith("_op")
                && !normalized.endsWith("_start")
                && !normalized.endsWith("_end");
    }

    private static boolean safeFilterScalar(Object value) {
        return value != null
                && scalar(value)
                && (!(value instanceof String text)
                || (!text.isBlank() && text.length() <= 2048));
    }

    private static boolean scalar(Object value) {
        return value == null || value instanceof String || value instanceof Number
                || value instanceof Boolean;
    }

    private static String emptyToNull(String value) {
        return StringUtils.hasText(value) ? value : null;
    }

    private static String normalized(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static EmbedException invalidRequest() {
        return new EmbedException(400, EmbedErrorCode.INVALID_REQUEST, "Embed request is invalid");
    }

    private static EmbedException operationNotAllowed() {
        return new EmbedException(
                403, EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED,
                "Embed operation is not allowed");
    }

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

    private static EmbedException corruptedRelease(String field, Throwable cause) {
        return new EmbedException(
                503,
                EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed runtime is temporarily unavailable",
                null,
                cause == null ? new IllegalStateException("Invalid release " + field) : cause);
    }

    private record RuntimeTarget(
            AuthenticatedEmbedSession session,
            EmbedRuntimeReleaseSnapshot release,
            List<String> capabilities) {
    }

    private record ProjectionPolicy(
            Set<String> visible,
            Set<String> queryable,
            Set<String> returnable,
            Set<String> allowedActions) {
    }

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
