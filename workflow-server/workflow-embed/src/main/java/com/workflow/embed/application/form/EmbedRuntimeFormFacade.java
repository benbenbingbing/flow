package com.workflow.embed.application.form;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedRuntimeFormPort;
import com.workflow.contracts.embed.EmbedRuntimeFormPort.Field;
import com.workflow.contracts.embed.EmbedRuntimeFormPort.PolicySource;
import com.workflow.contracts.embed.EmbedRuntimeFormPort.RuntimeMode;
import com.workflow.embed.api.web.EmbedRuntimeFormViews;
import com.workflow.embed.api.web.EmbedRuntimeFormViews.ActionCapability;
import com.workflow.embed.api.web.EmbedRuntimeFormViews.ActionDescriptor;
import com.workflow.embed.api.web.EmbedRuntimeFormViews.DependencyPolicy;
import com.workflow.embed.api.web.EmbedRuntimeFormViews.FieldState;
import com.workflow.embed.api.web.EmbedRuntimeFormViews.FieldView;
import com.workflow.embed.api.web.EmbedRuntimeFormViews.FilterPolicy;
import com.workflow.embed.api.web.EmbedRuntimeFormViews.LookupSource;
import com.workflow.embed.api.web.EmbedRuntimeFormViews.OptionSource;
import com.workflow.embed.api.web.EmbedRuntimeLookupQueryRequest;
import com.workflow.embed.api.web.EmbedRuntimeOptionQueryRequest;
import com.workflow.embed.application.port.EmbedRuntimeReleasePort;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedRuntimeReleaseSnapshot;
import com.workflow.embed.security.EmbedContextHolder;
import java.math.BigDecimal;
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
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Embed 表单和记录只读用例。
 *
 * <p>浏览器只提交模式、记录 ID 和已公开的查询值；实体、表单、Release、
 * 可见/可写字段与上下文绑定均从已认证 Session 和不可变 Embed Release 恢复。
 * Entity 适配器负责 Flow 对象权限和数据范围，本层再做最后的 External Projection
 * 白名单求交。</p>
 */
@Service
public class EmbedRuntimeFormFacade {

    private static final Pattern SAFE_KEY = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final Pattern SAFE_RECORD_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern PHONE = Pattern.compile("^1\\d{10}$");
    private static final Pattern URL = Pattern.compile(
            "^https?://\\S+$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> PUBLIC_CAPABILITIES = Set.of(
            "LIST_QUERY", "SELECTION_RETURN", "RECORD_VIEW", "RECORD_CREATE");
    private static final Map<RuntimeMode, String> MODE_CAPABILITY = Map.of(
            RuntimeMode.CREATE, "RECORD_CREATE",
            RuntimeMode.VIEW, "RECORD_VIEW");
    private static final Set<String> VALIDATION_KEYS = Set.of(
            "minLength", "maxLength", "minimum", "maximum", "pattern", "format");
    private static final Set<String> FILTER_OPERATORS = Set.of(
            "EQ", "CONTAINS", "IN", "GT", "GTE", "LT", "LTE", "BETWEEN");
    private static final int MAX_COLLECTION_VALUES = 100;

    private final EmbedRuntimeReleasePort releasePort;
    private final EmbedRuntimeFormPort formPort;
    private final ObjectMapper objectMapper;

    public EmbedRuntimeFormFacade(
            EmbedRuntimeReleasePort releasePort,
            EmbedRuntimeFormPort formPort,
            ObjectMapper objectMapper) {
        this.releasePort = releasePort;
        this.formPort = formPort;
        this.objectMapper = objectMapper;
    }

    /**
     * 按当前 Session 允许的 V1 模式解析表单；VIEW 同时完成记录鉴权。
     */
    public EmbedRuntimeFormViews.FormResult form(
            String requestedMode,
            String requestedRecordId) {
        ResolvedForm resolved = resolve(requestedMode, requestedRecordId);
        return formResult(resolved);
    }

    /**
     * 使用浏览器当前的部分 CREATE 草稿只读重算联动状态。
     *
     * <p>浏览器键只能来自固定 Embed Release 的 writable 字段白名单，值必须符合
     * 基础控件的封闭类型；FORCED_FORM_VALUE 随后覆盖同名草稿。该操作不校验 required
     * 或最终字段约束，也不触发幂等 Claim、记录写入或流程动作。</p>
     */
    public EmbedRuntimeFormViews.FormResult evaluateCreate(
            Map<String, Object> requestedData) {
        ResolvedForm initial = resolve(RuntimeMode.CREATE.name(), null);
        Map<String, Object> source = requestedData == null ? Map.of() : requestedData;
        if (source.size() > 100) {
            throw invalidRequest();
        }

        Map<String, ProjectedField> byCode = new LinkedHashMap<>();
        initial.fields().forEach(field -> byCode.put(field.source().code(), field));
        Map<String, Object> effective = new LinkedHashMap<>();
        source.forEach((code, value) -> {
            if (!SAFE_KEY.matcher(code == null ? "" : code).matches()) {
                throw invalidRequest();
            }
            ProjectedField field = byCode.get(code);
            if (field == null
                    || !initial.policy().writable().contains(code)
                    || !safeFieldValue(field.view().type(), value)) {
                throw invalidRequest();
            }
            effective.put(code, immutableFieldValue(value));
        });
        // Session Context 永远后写；浏览器即使提交同名键，也不能影响联动求值。
        effective.putAll(forcedValues(
                initial.target().release(), initial.target().session()));
        return formResult(resolve(
                RuntimeMode.CREATE.name(), null,
                Collections.unmodifiableMap(effective)));
    }

    private EmbedRuntimeFormViews.FormResult formResult(ResolvedForm resolved) {
        return new EmbedRuntimeFormViews.FormResult(
                resolved.mode().name(),
                recordView(resolved.record(), resolved.visibleValueFields()),
                new EmbedRuntimeFormViews.FormView(
                        resolved.source().title(),
                        new EmbedRuntimeFormViews.FormLayout(
                                externalLayout(resolved.source().layoutType())),
                        resolved.fields().stream().map(ProjectedField::view).toList(),
                        resolved.fields().stream()
                                .filter(field -> field.view().fieldState().visible())
                                .map(field -> field.source().code())
                                .filter(resolved.policy().returnable()::contains)
                                .toList(),
                        formActions(resolved)));
    }

    /**
     * 为 RECORD_CREATE 构造一次服务端可信授权快照。
     *
     * <p>浏览器字段先与当前模式实际可写字段求交；FORCED_FORM_VALUE 的同名浏览器值
     * 只参与幂等 Body Hash，不进入实体命令，最终始终由 Session Context 覆盖。</p>
     */
    public CreateAuthorization authorizeCreate(Map<String, Object> requestedData) {
        ResolvedForm resolved = resolve(RuntimeMode.CREATE.name(), null);
        ActionDescriptor save = formActions(resolved).stream()
                .filter(action -> "save".equals(action.key()))
                .findFirst()
                .orElseThrow(EmbedRuntimeFormFacade::operationNotAllowed);
        if (!save.enabled()
                || !"RECORD_CREATE".equals(save.transport())
                || save.requiresRecordVersion()
                || !save.idempotencyRequired()) {
            throw operationNotAllowed();
        }
        Map<String, Object> source = requestedData == null
                ? Map.of() : requestedData;
        if (source.size() > 100) {
            throw invalidRequest();
        }
        Map<String, ProjectedField> byCode = new LinkedHashMap<>();
        resolved.fields().forEach(field -> byCode.put(
                field.source().code(), field));
        Map<String, Object> forced = forcedValues(
                resolved.target().release(), resolved.target().session());
        Map<String, Object> clientValues = new LinkedHashMap<>();
        Map<String, Object> effective = new LinkedHashMap<>();
        source.forEach((code, value) -> {
            if (!SAFE_KEY.matcher(code == null ? "" : code).matches()) {
                throw invalidRequest();
            }
            ProjectedField field = byCode.get(code);
            if (forced.containsKey(code)) {
                // 浏览器不能改变固定值，但保留其安全规范值用于请求摘要，确保同键异主体冲突。
                if (!safeClientValue(value)) {
                    throw invalidRequest();
                }
                clientValues.put(code, immutableFieldValue(value));
                return;
            }
            if (field == null || !field.view().fieldState().writable()
                    || !safeFieldValue(field.view().type(), value)) {
                throw invalidRequest();
            }
            Object immutable = immutableFieldValue(value);
            clientValues.put(code, immutable);
            effective.put(code, immutable);
        });
        // Context 值最后写入，任何同名浏览器值都无法覆盖它。
        effective.putAll(forced);
        // 初始表单只负责限制浏览器可提交的候选字段；真正提交前必须用最终有效值
        // 重新求联动状态，防止字段因其他输入变为隐藏/只读后仍被写入。
        ResolvedForm finalResolved = resolve(
                RuntimeMode.CREATE.name(), null, effective);
        validateCreateValues(finalResolved, clientValues, effective, forced);
        Map<String, String> visibleTypes = new LinkedHashMap<>();
        finalResolved.fields().stream()
                .filter(field -> field.view().fieldState().visible())
                .forEach(field -> visibleTypes.put(
                        field.source().code(), field.view().type()));
        return new CreateAuthorization(
                finalResolved.target().session(), finalResolved.target().release().viewKey(),
                finalResolved.target().portTarget(),
                Collections.unmodifiableMap(clientValues),
                Collections.unmodifiableMap(effective),
                Collections.unmodifiableMap(visibleTypes),
                contextFilters(resolved.target().release(),
                        resolved.target().session().context()));
    }

    /**
     * 按本次请求刚完成的当前授权重新读取并投影记录；重放与首次成功共用这一路径。
     */
    public EmbedRuntimeFormViews.RecordView projectCreatedRecord(
            CreateAuthorization authorization,
            String recordId) {
        if (authorization == null || normalizedRecordId(recordId) == null) {
            throw invalidRequest();
        }
        AuthenticatedEmbedSession current = EmbedContextHolder.require();
        if (!Objects.equals(current.sessionId(), authorization.session().sessionId())
                || !Objects.equals(current.applicationId(),
                        authorization.session().applicationId())
                || !Objects.equals(current.viewId(), authorization.session().viewId())
                || !Objects.equals(current.viewReleaseId(),
                        authorization.session().viewReleaseId())) {
            throw operationNotAllowed();
        }
        try {
            EmbedRuntimeFormPort.RecordSnapshot record = formPort.findRecord(
                            new EmbedRuntimeFormPort.RecordQuery(
                                    authorization.target(), recordId,
                                    authorization.contextFilters()))
                    .orElseThrow(EmbedRuntimeFormFacade::resourceNotFound);
            if (!Objects.equals(recordId, record.id())) {
                throw corruptedRelease("recordSnapshot", null);
            }
            return recordView(record, authorization.visibleTypes().keySet());
        } catch (EmbedException error) {
            throw error;
        } catch (RuntimeException error) {
            throw runtimeUnavailable(error);
        }
    }

    /** 返回当前 View 可见值、字段状态与可执行动作。 */
    public EmbedRuntimeFormViews.RecordResult record(String recordId) {
        ResolvedForm resolved = resolve(
                RuntimeMode.VIEW.name(), recordId);
        Map<String, EmbedRuntimeFormViews.RecordFieldState> states = new LinkedHashMap<>();
        resolved.fields().forEach(field -> states.put(
                field.source().code(),
                new EmbedRuntimeFormViews.RecordFieldState(
                        field.view().fieldState().visible(),
                        !field.view().fieldState().writable(),
                        field.view().required())));
        Map<String, ActionCapability> actions = new LinkedHashMap<>();
        for (EmbedRuntimeFormPort.Action action : resolved.source().actions()) {
            if (resolved.policy().allowedActions().contains(action.key())
                    && knownReadAction(action.key())) {
                actions.put(action.key(), new ActionCapability(
                        action.visible(), action.enabled(), null));
            }
        }
        return new EmbedRuntimeFormViews.RecordResult(
                recordView(resolved.record(), resolved.visibleValueFields()),
                Collections.unmodifiableMap(states),
                Collections.unmodifiableMap(actions));
    }

    /** 查询当前 Session 固定 Form Release 在本地导航模式下的已声明动态选项。 */
    public EmbedRuntimeFormViews.OptionPage queryOptions(
            String fieldCode,
            EmbedRuntimeOptionQueryRequest request) {
        requireSafeFieldCode(fieldCode);
        EmbedRuntimeOptionQueryRequest safe = request == null
                ? new EmbedRuntimeOptionQueryRequest() : request;
        ResolvedForm resolved = resolve(safe.getMode(), safe.getRecordId());
        ProjectedField field = requireProjectedField(resolved, fieldCode);
        if (!field.source().runtimeOptions()
                || !field.view().fieldState().visible()) {
            throw operationNotAllowed();
        }

        Set<String> clientDependencies = new LinkedHashSet<>();
        for (EmbedRuntimeFormPort.DependencyRule rule
                : field.source().dependencyPolicy()) {
            if (rule.source() == PolicySource.CLIENT_WRITABLE) {
                clientDependencies.add(rule.code());
            }
        }
        Map<String, Object> dependencies = validateClientValues(
                safe.getDependencies(), clientDependencies);
        // 只有当前模式下仍可写的表单字段才能成为客户端依赖；
        // CONTEXT/CURRENT_RECORD_READONLY/CONSTANT 来源永远由适配器从可信上下文恢复。
        dependencies.forEach((code, value) -> {
            ProjectedField dependency = requireProjectedField(resolved, code);
            if (!dependency.view().fieldState().writable()
                    || !safeFieldValue(dependency.view().type(), value)) {
                throw invalidRequest();
            }
        });

        try {
            EmbedRuntimeFormPort.OptionPage page = formPort.queryOptions(
                    new EmbedRuntimeFormPort.OptionQuery(
                            resolved.target().portTarget(), resolved.mode(),
                            resolved.record() == null ? null : resolved.record().id(),
                            fieldCode, normalizeKeyword(safe.getKeyword()),
                            dependencies, resolved.target().session().context(),
                            safe.getPageNum(), safe.getPageSize()));
            requirePage(
                    page == null ? null : page.items(),
                    page == null ? -1 : page.pageNum(),
                    page == null ? -1 : page.pageSize(),
                    safe.getPageNum(), safe.getPageSize());
            return new EmbedRuntimeFormViews.OptionPage(
                    page.items().stream()
                            .map(option -> externalOption(
                                    option, field.view().type()))
                            .toList(),
                    page.hasMore(), page.pageNum(), page.pageSize());
        } catch (EmbedRuntimeFormPort.OperationNotAllowedException error) {
            throw operationNotAllowed();
        } catch (RuntimeException error) {
            throw runtimeUnavailable(error);
        }
    }

    /**
     * V1 保留稳定 Lookup 路由用于返回明确的未开放错误，但不会解析 Release 或调用 Port。
     * 这样即使未来替换适配器，也不能意外开放尚未发布的候选记录能力。
     */
    public EmbedRuntimeFormViews.LookupPage queryLookups(
            String fieldCode,
            EmbedRuntimeLookupQueryRequest request) {
        throw operationNotAllowed();
    }

    private ResolvedForm resolve(String requestedMode, String requestedRecordId) {
        return resolve(requestedMode, requestedRecordId, Map.of());
    }

    /**
     * 解析固定表单；CREATE 可携带已经过第一层白名单校验的最终值用于联动求值。
     */
    private ResolvedForm resolve(
            String requestedMode,
            String requestedRecordId,
            Map<String, Object> trustedCreateValues) {
        RuntimeMode mode = parseMode(requestedMode);
        if (mode != RuntimeMode.CREATE
                && trustedCreateValues != null
                && !trustedCreateValues.isEmpty()) {
            throw new IllegalStateException("非 CREATE 模式不能注入提交值");
        }
        RuntimeTarget target = target();
        requireMode(target, mode);
        String recordId = validateRecordCoordinate(
                target.session(), mode, requestedRecordId);
        EmbedRuntimeFormPort.RecordSnapshot record = mode == RuntimeMode.CREATE
                ? null : findRecord(target, recordId);
        ProjectionPolicy policy = projectionPolicy(target.release());
        Map<String, Object> forced = forcedValues(
                target.release(), target.session());
        Map<String, Object> trustedFormValues = new LinkedHashMap<>(
                record == null || record.values() == null
                        ? Map.of() : record.values());
        if (mode == RuntimeMode.CREATE && trustedCreateValues != null) {
            trustedFormValues.putAll(trustedCreateValues);
        }
        // FORCED_FORM_VALUE 是发布配置的一部分，联动求值也必须看到最终服务端值；
        // 否则浏览器初始字段状态会与真实提交约束不一致。
        trustedFormValues.putAll(forced);

        EmbedRuntimeFormPort.FormSnapshot source;
        try {
            source = formPort.resolveForm(new EmbedRuntimeFormPort.ResolveQuery(
                    target.portTarget(), mode, recordId,
                    Collections.unmodifiableMap(trustedFormValues)));
        } catch (EmbedRuntimeFormPort.OperationNotAllowedException error) {
            throw operationNotAllowed();
        } catch (RuntimeException error) {
            throw runtimeUnavailable(error);
        }
        if (source == null || source.fields() == null || source.actions() == null
                || !StringUtils.hasText(source.title())
                || source.title().length() > 200) {
            throw corruptedRelease("formSnapshot", null);
        }
        validateSourceActions(source.actions());

        List<ProjectedField> fields = projectFields(source.fields(), policy, forced, mode);
        validateRuntimeSources(fields);
        Set<String> actual = new LinkedHashSet<>();
        fields.forEach(field -> actual.add(field.source().code()));
        if (!actual.containsAll(policy.visible())) {
            // Embed Release 的字段白名单必须是它所固定 Form Release 的精确子集；
            // 快照损坏或跨资源错配时不得静默降级为部分表单。
            throw corruptedRelease("fieldPolicy", null);
        }
        Set<String> visibleValues = fields.stream()
                .filter(field -> field.view().fieldState().visible())
                .map(field -> field.source().code())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        return new ResolvedForm(
                target, mode, source, record, policy,
                List.copyOf(fields), visibleValues);
    }

    /**
     * 对联动后的最终 CREATE 投影执行声明式约束校验。
     *
     * <p>校验发生在幂等 Claim 和业务事务之前；错误最多公开 20 个当前 External
     * Projection 字段路径，不携带内部表单配置、SQL 或异常文本。</p>
     */
    private void validateCreateValues(
            ResolvedForm resolved,
            Map<String, Object> clientValues,
            Map<String, Object> effectiveValues,
            Map<String, Object> forcedValues) {
        Map<String, ProjectedField> fields = new LinkedHashMap<>();
        resolved.fields().forEach(field -> fields.put(field.source().code(), field));
        List<Map<String, Object>> violations = new ArrayList<>();

        clientValues.forEach((code, value) -> {
            if (forcedValues.containsKey(code)) {
                return;
            }
            ProjectedField field = fields.get(code);
            if (field == null || !field.view().fieldState().writable()) {
                addViolation(violations, code, "NOT_WRITABLE",
                        "Field is not writable in the current form state");
            }
        });

        for (ProjectedField field : resolved.fields()) {
            if (violations.size() >= 20 || !field.view().fieldState().visible()) {
                continue;
            }
            String code = field.source().code();
            boolean supplied = effectiveValues.containsKey(code);
            Object value = effectiveValues.get(code);
            if (field.view().required() && (!supplied || blank(value))) {
                addViolation(violations, code, "REQUIRED", "Field is required");
                continue;
            }
            if (!supplied || blank(value)) {
                continue;
            }
            validateFieldConstraints(field, value, violations);
        }
        if (!violations.isEmpty()) {
            throw formValidation(violations);
        }
    }

    private void validateFieldConstraints(
            ProjectedField field,
            Object value,
            List<Map<String, Object>> violations) {
        Map<String, Object> rules = field.view().validation();
        if (value instanceof String text) {
            Integer minLength = integerRule(rules, "minLength");
            Integer maxLength = integerRule(rules, "maxLength");
            if (minLength != null && text.length() < minLength) {
                addViolation(violations, field.source().code(),
                        "MIN_LENGTH", "Field is shorter than allowed");
            }
            if (maxLength != null && text.length() > maxLength) {
                addViolation(violations, field.source().code(),
                        "MAX_LENGTH", "Field is longer than allowed");
            }
            validateFormat(
                    field.source().code(), text,
                    rules.get("format"), violations);
        }
        if (value instanceof Number number) {
            BigDecimal actual;
            try {
                actual = new BigDecimal(number.toString());
            } catch (NumberFormatException error) {
                addViolation(violations, field.source().code(),
                        "NUMBER", "Field must be a finite number");
                return;
            }
            BigDecimal minimum = decimalRule(rules, "minimum");
            BigDecimal maximum = decimalRule(rules, "maximum");
            if (minimum != null && actual.compareTo(minimum) < 0) {
                addViolation(violations, field.source().code(),
                        "MINIMUM", "Field is below the allowed minimum");
            }
            if (maximum != null && actual.compareTo(maximum) > 0) {
                addViolation(violations, field.source().code(),
                        "MAXIMUM", "Field exceeds the allowed maximum");
            }
        }
        validateStaticOptions(field, value, violations);
    }

    private void validateStaticOptions(
            ProjectedField field,
            Object value,
            List<Map<String, Object>> violations) {
        if (!Set.of("SELECT", "MULTI_SELECT").contains(field.view().type())
                || field.source().options().isEmpty()) {
            return;
        }
        Set<Object> allowed = new LinkedHashSet<>();
        field.source().options().stream()
                .filter(option -> !option.disabled())
                .map(EmbedRuntimeFormPort.Option::value)
                .forEach(allowed::add);
        boolean valid = value instanceof Collection<?> values
                ? values.stream().allMatch(allowed::contains)
                : allowed.contains(value);
        if (!valid) {
            addViolation(violations, field.source().code(),
                    "ENUM", "Field contains an unpublished option");
        }
    }

    private static void validateFormat(
            String code,
            String value,
            Object rawFormat,
            List<Map<String, Object>> violations) {
        if (rawFormat == null) {
            return;
        }
        String format = String.valueOf(rawFormat).trim().toUpperCase(Locale.ROOT);
        Pattern rule = switch (format) {
            case "EMAIL" -> EMAIL;
            case "PHONE" -> PHONE;
            case "URL", "URI" -> URL;
            default -> throw corruptedRelease("validationFormat", null);
        };
        if (!rule.matcher(value).matches()) {
            addViolation(violations, code, "FORMAT", "Field format is invalid");
        }
    }

    private static Integer integerRule(Map<String, Object> rules, String key) {
        Object value = rules.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)
                || number.intValue() < 0
                || number.doubleValue() != number.intValue()) {
            throw corruptedRelease("validation", null);
        }
        return number.intValue();
    }

    private static BigDecimal decimalRule(Map<String, Object> rules, String key) {
        Object value = rules.get(key);
        if (value == null) {
            return null;
        }
        if (!(value instanceof Number number)) {
            throw corruptedRelease("validation", null);
        }
        try {
            return new BigDecimal(number.toString());
        } catch (NumberFormatException error) {
            throw corruptedRelease("validation", error);
        }
    }

    private static boolean blank(Object value) {
        return value == null
                || value instanceof String text && !StringUtils.hasText(text)
                || value instanceof Collection<?> values && values.isEmpty();
    }

    private static void addViolation(
            List<Map<String, Object>> violations,
            String fieldCode,
            String code,
            String message) {
        if (violations.size() >= 20) {
            return;
        }
        violations.add(Map.of(
                "path", "data." + fieldCode,
                "code", code,
                "message", message));
    }

    private RuntimeTarget target() {
        AuthenticatedEmbedSession session = EmbedContextHolder.require();
        EmbedRuntimeReleaseSnapshot release = releasePort.find(
                session.sessionId(), session.viewId(), session.viewReleaseId());
        if (release == null) {
            throw runtimeUnavailable(null);
        }
        if (!Objects.equals(session.viewId(), release.viewId())
                || !Objects.equals(session.viewReleaseId(), release.releaseId())) {
            throw corruptedRelease("releaseIdentity", null);
        }
        if (!Set.of("LIST", "FORM").contains(release.surfaceType())) {
            throw operationNotAllowed();
        }
        if (!StringUtils.hasText(release.entityCode())
                || !StringUtils.hasText(release.formReleaseId())
                || release.formReleaseVersion() == null
                || release.formReleaseVersion() < 1) {
            throw corruptedRelease("formRelease", null);
        }
        boolean hasListReleaseId = StringUtils.hasText(release.listReleaseId());
        boolean hasListReleaseVersion = release.listReleaseVersion() != null;
        if (hasListReleaseId != hasListReleaseVersion
                || (hasListReleaseVersion && release.listReleaseVersion() < 1)
                || (hasListReleaseId && !StringUtils.hasText(release.listKey()))) {
            throw corruptedRelease("listRelease", null);
        }
        JsonNode config = readObject(release.configJson(), "config");
        String formId = text(config.path("resolved"), "defaultFormId");
        if (!StringUtils.hasText(formId)) {
            throw corruptedRelease("formId", null);
        }
        Set<String> releaseCapabilities = stringSet(
                readArray(release.capabilitiesJson(), "capabilities"));
        Set<String> capabilities = releaseCapabilities.stream()
                .filter(PUBLIC_CAPABILITIES::contains)
                .filter(session.capabilities()::contains)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<String> entryModes = stringSet(config.path("entryModes"));
        if (entryModes.isEmpty()
                || !Set.of("LIST", "CREATE", "VIEW")
                        .containsAll(entryModes)) {
            throw corruptedRelease("entryModes", null);
        }
        EmbedRuntimeFormPort.Target portTarget = new EmbedRuntimeFormPort.Target(
                release.entityCode(), formId, release.formReleaseId(),
                release.formReleaseVersion(), release.listKey(),
                release.listReleaseId(), release.listReleaseVersion());
        return new RuntimeTarget(
                session, release, capabilities, entryModes, portTarget);
    }

    private void requireMode(RuntimeTarget target, RuntimeMode mode) {
        String capability = MODE_CAPABILITY.get(mode);
        if (capability == null
                || !target.capabilities().contains(capability)
                || !target.entryModes().contains(mode.name())) {
            throw operationNotAllowed();
        }
        String entryMode = target.session().entryMode();
        if (!Set.of("LIST", "CREATE", "VIEW").contains(entryMode)
                || (!"LIST".equals(entryMode) && !entryMode.equals(mode.name()))) {
            throw operationNotAllowed();
        }
    }

    private String validateRecordCoordinate(
            AuthenticatedEmbedSession session,
            RuntimeMode mode,
            String requestedRecordId) {
        if (mode == RuntimeMode.CREATE) {
            if (StringUtils.hasText(requestedRecordId)) {
                throw invalidRequest();
            }
            return null;
        }
        String boundRecordId = normalizedRecordId(session.recordId());
        if (StringUtils.hasText(session.recordId()) && boundRecordId == null) {
            throw corruptedRelease("sessionRecordId", null);
        }
        String recordId = StringUtils.hasText(requestedRecordId)
                ? normalizedRecordId(requestedRecordId) : boundRecordId;
        if (recordId == null) {
            throw invalidRequest();
        }
        if (boundRecordId != null && !Objects.equals(boundRecordId, recordId)) {
            throw resourceNotFound();
        }
        return recordId;
    }

    private EmbedRuntimeFormPort.RecordSnapshot findRecord(
            RuntimeTarget target,
            String recordId) {
        try {
            EmbedRuntimeFormPort.RecordSnapshot result = formPort.findRecord(
                            new EmbedRuntimeFormPort.RecordQuery(
                            target.portTarget(), recordId,
                            contextFilters(target.release(),
                                    target.session().context())))
                    .orElseThrow(EmbedRuntimeFormFacade::resourceNotFound);
            if (!Objects.equals(recordId, result.id())
                    || normalizedRecordId(result.id()) == null
                    || result.values() == null
                    || result.recordVersion() != null
                    && result.recordVersion() < 0) {
                throw corruptedRelease("recordSnapshot", null);
            }
            return result;
        } catch (EmbedException error) {
            throw error;
        } catch (RuntimeException error) {
            throw runtimeUnavailable(error);
        }
    }

    private List<ProjectedField> projectFields(
            List<Field> sourceFields,
            ProjectionPolicy policy,
            Map<String, Object> forced,
            RuntimeMode mode) {
        Map<String, Field> byCode = new LinkedHashMap<>();
        for (Field field : sourceFields) {
            requireSourceField(field);
            if (byCode.putIfAbsent(field.code(), field) != null) {
                throw corruptedRelease("duplicateField", null);
            }
        }
        List<ProjectedField> result = new ArrayList<>();
        for (String code : policy.visible()) {
            Field field = byCode.get(code);
            if (field == null) {
                continue;
            }
            String type = externalType(field.type());
            boolean visible = !field.hidden() && field.modeVisible();
            boolean writable = visible
                    && mode != RuntimeMode.VIEW
                    && policy.writable().contains(code)
                    && !field.readOnly()
                    && field.modeEditable()
                    && !forced.containsKey(code);
            Object defaultValue = forced.containsKey(code)
                    ? forced.get(code) : field.defaultValue();
            if (!safeFieldValue(type, defaultValue)) {
                throw corruptedRelease("defaultValue", null);
            }
            List<EmbedRuntimeFormViews.OptionView> options = field.runtimeOptions()
                    ? null : field.options().stream()
                            .map(option -> externalOption(option, type))
                            .toList();
            OptionSource optionSource = field.runtimeOptions()
                    ? new OptionSource(
                            "RUNTIME",
                            "/api/embed/v1/runtime/form/fields/" + code + "/options/query",
                            field.dependencyPolicy().stream()
                                    .map(rule -> new DependencyPolicy(
                                            rule.code(), rule.source().name()))
                                    .toList())
                    : null;
            LookupSource lookupSource = field.lookup()
                    ? new LookupSource(
                            "RUNTIME",
                            "/api/embed/v1/runtime/form/fields/" + code + "/lookups/query",
                            field.filterPolicy().stream()
                                    .map(rule -> new FilterPolicy(
                                            rule.code(), rule.source().name(),
                                            validateOperators(rule.operators())))
                                    .toList())
                    : null;
            FieldView view = new FieldView(
                    code, field.label(), type, field.required(), !writable,
                    !visible, defaultValue, externalValidation(field.validation()),
                    options, optionSource, lookupSource,
                    new EmbedRuntimeFormViews.FieldLayout(normalizeSpan(field.span())),
                    new FieldState(visible, writable));
            result.add(new ProjectedField(field, view));
        }
        return result;
    }

    /**
     * 动态依赖必须在当前投影中仍是可见可写字段；不能发布一个浏览器永远无法安全
     * 满足、随后由内部服务猜测或回退的候选项契约。
     */
    private void validateRuntimeSources(List<ProjectedField> fields) {
        Map<String, ProjectedField> byCode = new LinkedHashMap<>();
        fields.forEach(field -> byCode.put(field.source().code(), field));
        for (ProjectedField field : fields) {
            String type = field.view().type();
            if (Set.of("LOOKUP", "MULTI_LOOKUP").contains(type)
                    && !field.source().lookup()
                    && field.view().fieldState().writable()) {
                // 历史或绕过发布器的 Release 也不能把没有固定候选来源的引用字段
                // 暴露为可写控件；否则浏览器只能猜测内部记录 ID。
                throw corruptedRelease("lookupSource", null);
            }
            if (field.source().runtimeOptions()
                    && !Set.of("SELECT", "MULTI_SELECT").contains(type)) {
                throw corruptedRelease("optionSource", null);
            }
            if (field.source().lookup()
                    && !Set.of("LOOKUP", "MULTI_LOOKUP").contains(type)) {
                throw corruptedRelease("lookupSource", null);
            }
            if ((!field.source().runtimeOptions()
                    && !field.source().dependencyPolicy().isEmpty())
                    || (!field.source().lookup()
                    && (!field.source().filterPolicy().isEmpty()
                    || !field.source().lookupProjection().isEmpty()))) {
                throw corruptedRelease("fieldSource", null);
            }
            Set<String> dependencies = new LinkedHashSet<>();
            for (EmbedRuntimeFormPort.DependencyRule rule
                    : field.source().dependencyPolicy()) {
                if (!dependencies.add(rule.code())) {
                    throw corruptedRelease("dependencyPolicy", null);
                }
                if (rule.source() != PolicySource.CLIENT_WRITABLE) {
                    continue;
                }
                ProjectedField dependency = byCode.get(rule.code());
                if (dependency == null
                        || !dependency.view().fieldState().visible()
                        || !dependency.view().fieldState().writable()) {
                    throw corruptedRelease("dependencyPolicy", null);
                }
            }
        }
    }

    private List<ActionDescriptor> formActions(ResolvedForm resolved) {
        if (resolved.mode() != RuntimeMode.CREATE) {
            return List.of();
        }
        String capability = MODE_CAPABILITY.get(resolved.mode());
        if (!resolved.target().capabilities().contains(capability)
                || !resolved.policy().allowedActions().contains("save")) {
            return List.of();
        }
        EmbedRuntimeFormPort.Action save = resolved.source().actions().stream()
                .filter(action -> "save".equals(action.key()))
                .findFirst()
                .orElse(null);
        if (save == null || !save.visible()) {
            return List.of();
        }
        return List.of(new ActionDescriptor(
                save.key(), save.label(), "FORM", "MUTATION",
                "RECORD_CREATE", "NONE", "NONE",
                writableSchema(resolved.fields()), false, null, true,
                save.enabled(), null));
    }

    private Object writableSchema(List<ProjectedField> fields) {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> required = new ArrayList<>();
        for (ProjectedField field : fields) {
            if (!field.view().fieldState().writable()) {
                continue;
            }
            Map<String, Object> definition = new LinkedHashMap<>();
            definition.put("type", jsonType(field.view().type()));
            definition.putAll(field.view().validation());
            properties.put(field.view().code(), Collections.unmodifiableMap(definition));
            if (field.view().required()) {
                required.add(field.view().code());
            }
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.unmodifiableMap(properties));
        if (!required.isEmpty()) {
            schema.put("required", List.copyOf(required));
        }
        schema.put("additionalProperties", false);
        return Collections.unmodifiableMap(schema);
    }

    private ProjectionPolicy projectionPolicy(EmbedRuntimeReleaseSnapshot release) {
        JsonNode fields = readObject(release.fieldPolicyJson(), "fieldPolicy");
        JsonNode actions = readObject(release.actionPolicyJson(), "actionPolicy");
        return new ProjectionPolicy(
                stringSet(fields.path("visible")),
                stringSet(fields.path("writable")),
                fields.has("returnable")
                        ? stringSet(fields.path("returnable")) : Set.of(),
                stringSet(actions.path("allowed")));
    }

    private Map<String, Object> forcedValues(
            EmbedRuntimeReleaseSnapshot release,
            AuthenticatedEmbedSession session) {
        JsonNode bindings = readArray(release.contextBindingsJson(), "contextBindings");
        Map<String, Object> result = new LinkedHashMap<>();
        for (JsonNode binding : bindings) {
            if (!"FORCED_FORM_VALUE".equals(text(binding, "usage"))) {
                continue;
            }
            String source = text(binding, "source");
            String target = text(binding, "target");
            Object value = session.context() == null ? null : session.context().get(source);
            if (!StringUtils.hasText(source) || !SAFE_KEY.matcher(target == null ? "" : target).matches()
                    || session.context() == null || !session.context().containsKey(source)
                    || !scalar(value) || result.putIfAbsent(target, value) != null) {
                throw corruptedRelease("contextBindings", null);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * 将 Session Context 按已发布绑定转换为服务端固定过滤条件。
     * 浏览器不能提交或覆盖这些条件；记录详情也必须与列表查询使用同一约束。
     */
    private Map<String, Object> contextFilters(
            EmbedRuntimeReleaseSnapshot release,
            Map<String, Object> context) {
        JsonNode bindings = readArray(
                release.contextBindingsJson(), "contextBindings");
        Map<String, Object> result = new LinkedHashMap<>();
        for (JsonNode binding : bindings) {
            if (!"FIXED_FILTER".equals(text(binding, "usage"))) {
                continue;
            }
            String source = text(binding, "source");
            String target = text(binding, "target");
            Object value = context == null ? null : context.get(source);
            if (!StringUtils.hasText(source)
                    || !SAFE_KEY.matcher(target == null ? "" : target).matches()
                    || context == null || !context.containsKey(source)
                    || value == null || !scalar(value)
                    || result.containsKey(target + "_op")
                    || result.putIfAbsent(target, value) != null
                    || result.putIfAbsent(target + "_op", "EQ") != null) {
                throw corruptedRelease("contextBindings", null);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private EmbedRuntimeFormViews.RecordView recordView(
            EmbedRuntimeFormPort.RecordSnapshot record,
            Set<String> visible) {
        if (record == null) {
            return null;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        if (record.values() != null) {
            for (String code : visible) {
                if (!record.values().containsKey(code)) {
                    continue;
                }
                Object value = record.values().get(code);
                if (!safeExternalRecordValue(value)) {
                    throw corruptedRelease("recordValue", null);
                }
                values.put(code, value);
            }
        }
        return new EmbedRuntimeFormViews.RecordView(
                // V1 没有 UPDATE 能力，永远不向浏览器暴露适配器的并发版本语义。
                record.id(), null,
                Collections.unmodifiableMap(values),
                new EmbedRuntimeFormViews.RecordMeta(
                        record.createdAt(), record.updatedAt()));
    }

    private Map<String, Object> validateClientValues(
            Map<String, Object> requested,
            Set<String> allowed) {
        if (requested == null || requested.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        requested.forEach((key, value) -> {
            if (!SAFE_KEY.matcher(key == null ? "" : key).matches()
                    || !allowed.contains(key) || !safeClientValue(value)) {
                throw invalidRequest();
            }
            result.put(key, value);
        });
        return Collections.unmodifiableMap(result);
    }

    private ProjectedField requireProjectedField(
            ResolvedForm resolved,
            String fieldCode) {
        return resolved.fields().stream()
                .filter(field -> field.source().code().equals(fieldCode))
                .findFirst()
                .orElseThrow(EmbedRuntimeFormFacade::operationNotAllowed);
    }

    private void requireSourceField(Field field) {
        if (field == null || !SAFE_KEY.matcher(field.code() == null ? "" : field.code()).matches()
                || !StringUtils.hasText(field.label()) || field.label().length() > 200
                || field.options() == null || field.dependencyPolicy() == null
                || field.filterPolicy() == null || field.lookupProjection() == null
                || field.validation() == null) {
            throw corruptedRelease("formField", null);
        }
        field.dependencyPolicy().forEach(rule -> {
            if (rule == null || !SAFE_KEY.matcher(rule.code() == null ? "" : rule.code()).matches()
                    || rule.source() == null) {
                throw corruptedRelease("dependencyPolicy", null);
            }
        });
        field.options().forEach(option -> {
            if (option == null || !StringUtils.hasText(option.label())
                    || option.label().length() > 200 || !scalar(option.value())) {
                throw corruptedRelease("options", null);
            }
        });
        field.lookupProjection().forEach(code -> {
            if (!SAFE_KEY.matcher(code == null ? "" : code).matches()) {
                throw corruptedRelease("lookupProjection", null);
            }
        });
        field.filterPolicy().forEach(rule -> {
            if (rule == null || !SAFE_KEY.matcher(rule.code() == null ? "" : rule.code()).matches()
                    || rule.source() == null || rule.operators() == null) {
                throw corruptedRelease("filterPolicy", null);
            }
        });
    }

    private void validateSourceActions(
            Collection<EmbedRuntimeFormPort.Action> actions) {
        Set<String> keys = new LinkedHashSet<>();
        for (EmbedRuntimeFormPort.Action action : actions) {
            if (action == null
                    || !SAFE_KEY.matcher(action.key() == null ? "" : action.key()).matches()
                    || !StringUtils.hasText(action.label())
                    || action.label().length() > 100
                    || !keys.add(action.key())) {
                throw corruptedRelease("formActions", null);
            }
        }
    }

    private Map<String, Object> externalValidation(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (!VALIDATION_KEYS.contains(key)) {
                return;
            }
            if ("pattern".equals(key)) {
                // Java 回溯正则无法提供可靠单次超时；发布端也会阻断该字段，运行端
                // 对历史/篡改快照再次 fail closed，避免恶意输入耗尽请求线程。
                throw corruptedRelease("validationPattern", null);
            }
            if (!scalar(value)) {
                throw corruptedRelease("validation", null);
            }
            result.put(key, value);
        });
        return Collections.unmodifiableMap(result);
    }

    private List<String> validateOperators(Collection<String> source) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : source) {
            String operator = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
            if (!FILTER_OPERATORS.contains(operator)) {
                throw corruptedRelease("filterPolicy", null);
            }
            result.add(operator);
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

    private JsonNode readArray(String json, String label) {
        try {
            JsonNode node = objectMapper.readTree(json);
            if (node == null || !node.isArray()) {
                throw corruptedRelease(label, null);
            }
            return node;
        } catch (JsonProcessingException error) {
            throw corruptedRelease(label, error);
        }
    }

    private static Set<String> stringSet(JsonNode array) {
        if (array == null || !array.isArray()) {
            throw corruptedRelease("stringSet", null);
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (JsonNode value : array) {
            if (!value.isTextual() || !StringUtils.hasText(value.textValue())) {
                throw corruptedRelease("stringSet", null);
            }
            if (!result.add(value.textValue())) {
                throw corruptedRelease("stringSet", null);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    private static RuntimeMode parseMode(String value) {
        if (!StringUtils.hasText(value)) {
            throw invalidRequest();
        }
        // EDIT 曾出现在早期草案中；V1 明确按“能力未开放”拒绝，不能因枚举收缩
        // 漂移成普通参数错误。其他未知字符串仍属于无效请求。
        if ("EDIT".equals(value.trim().toUpperCase(Locale.ROOT))) {
            throw operationNotAllowed();
        }
        try {
            return RuntimeMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            throw invalidRequest();
        }
    }

    private static String externalType(String value) {
        String type = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "STRING", "TEXT", "INPUT", "TEXTAREA" -> "TEXT";
            case "INTEGER", "LONG", "DECIMAL", "NUMBER", "MONEY" -> "NUMBER";
            case "DATE", "DATETIME", "TIME", "BOOLEAN" -> type;
            case "SELECT", "RADIO" -> "SELECT";
            case "MULTI_SELECT", "CHECKBOX" -> "MULTI_SELECT";
            case "REFERENCE", "USER", "DEPT", "ROLE", "GROUP" -> "LOOKUP";
            case "MULTI_REFERENCE" -> "MULTI_LOOKUP";
            default -> throw corruptedRelease("fieldType", null);
        };
    }

    private static String externalLayout(String value) {
        String layout = StringUtils.hasText(value)
                ? value.trim().toUpperCase(Locale.ROOT) : "GRID";
        if (!Set.of("GRID", "VERTICAL", "HORIZONTAL").contains(layout)) {
            throw corruptedRelease("layout", null);
        }
        return layout;
    }

    private static String jsonType(String type) {
        return switch (type) {
            case "NUMBER" -> "number";
            case "BOOLEAN" -> "boolean";
            case "MULTI_SELECT", "MULTI_LOOKUP" -> "array";
            default -> "string";
        };
    }

    private static boolean safeFieldValue(String type, Object value) {
        if (value == null) {
            return true;
        }
        return switch (type) {
            case "NUMBER" -> value instanceof Number;
            case "BOOLEAN" -> value instanceof Boolean;
            case "MULTI_SELECT", "MULTI_LOOKUP" -> value instanceof Collection<?> values
                    && values.size() <= MAX_COLLECTION_VALUES
                    && values.stream().allMatch(EmbedRuntimeFormFacade::scalar);
            default -> scalar(value)
                    && (!(value instanceof String text) || text.length() <= 2048);
        };
    }

    private static boolean safeClientValue(Object value) {
        if (scalar(value)) {
            return !(value instanceof String text) || text.length() <= 2048;
        }
        return value instanceof Collection<?> values
                && values.size() <= MAX_COLLECTION_VALUES
                && values.stream().allMatch(EmbedRuntimeFormFacade::scalar);
    }

    private static Object immutableFieldValue(Object value) {
        if (value instanceof Collection<?> values) {
            return Collections.unmodifiableList(new ArrayList<>(values));
        }
        return value;
    }

    private static EmbedRuntimeFormViews.OptionView externalOption(
            EmbedRuntimeFormPort.Option option,
            String fieldType) {
        if (option == null || !StringUtils.hasText(option.label())
                || option.label().length() > 200
                || !safeFieldValue(fieldType, option.value())) {
            throw corruptedRelease("options", null);
        }
        return new EmbedRuntimeFormViews.OptionView(
                option.label(), option.value(), option.disabled());
    }

    private static EmbedRuntimeFormViews.LookupItem externalLookup(
            EmbedRuntimeFormPort.LookupItem item,
            Collection<String> projection) {
        if (item == null || normalizedRecordId(item.id()) == null
                || !StringUtils.hasText(item.label()) || item.label().length() > 200
                || item.values() == null) {
            throw corruptedRelease("lookupResult", null);
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (String code : projection) {
            if (!item.values().containsKey(code)) {
                continue;
            }
            Object value = item.values().get(code);
            if (!safeExternalRecordValue(value)) {
                throw corruptedRelease("lookupResult", null);
            }
            values.put(code, value);
        }
        return new EmbedRuntimeFormViews.LookupItem(
                item.id(), item.label(), Collections.unmodifiableMap(values));
    }

    private static void requirePage(
            Collection<?> items,
            int actualPageNum,
            int actualPageSize,
            int requestedPageNum,
            int requestedPageSize) {
        if (items == null || items.size() > requestedPageSize
                || actualPageNum != requestedPageNum
                || actualPageSize != requestedPageSize) {
            throw corruptedRelease("queryResult", null);
        }
    }

    private static boolean safeExternalRecordValue(Object value) {
        if (scalar(value)) {
            return !(value instanceof String text) || text.length() <= 100_000;
        }
        return value instanceof Collection<?> values
                && values.size() <= MAX_COLLECTION_VALUES
                && values.stream().allMatch(EmbedRuntimeFormFacade::scalar);
    }

    private static boolean scalar(Object value) {
        return value == null || value instanceof String
                || value instanceof Number || value instanceof Boolean;
    }

    private static int normalizeSpan(int value) {
        if (value < 1 || value > 24) {
            throw corruptedRelease("fieldLayout", null);
        }
        return value;
    }

    private static String normalizeKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String result = value.trim();
        if (result.length() > 200) {
            throw invalidRequest();
        }
        return result;
    }

    private static String normalizedRecordId(String value) {
        if (!StringUtils.hasText(value) || !value.equals(value.trim())
                || !SAFE_RECORD_ID.matcher(value).matches()) {
            return null;
        }
        return value;
    }

    private static void requireSafeFieldCode(String value) {
        if (value == null || !SAFE_KEY.matcher(value).matches()) {
            throw invalidRequest();
        }
    }

    private static boolean knownReadAction(String key) {
        return Set.of("view", "close").contains(key);
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        return value != null && value.isTextual() && StringUtils.hasText(value.textValue())
                ? value.textValue() : null;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> value) {
        return value == null ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private static EmbedException invalidRequest() {
        return new EmbedException(400, EmbedErrorCode.INVALID_REQUEST, "Embed request is invalid");
    }

    private static EmbedException operationNotAllowed() {
        return new EmbedException(
                403, EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED,
                "Embed operation is not allowed");
    }

    private static EmbedException formValidation(
            List<Map<String, Object>> violations) {
        Map<String, Object> data = Map.of(
                "violations", List.copyOf(violations),
                "relaunchRequired", false);
        return new EmbedException(
                422, EmbedErrorCode.FORM_VALIDATION_FAILED,
                "Form validation failed", null, null, data);
    }

    private static EmbedException resourceNotFound() {
        return new EmbedException(
                404, EmbedErrorCode.EMBED_RESOURCE_NOT_FOUND,
                "Embed resource was not found");
    }

    private static EmbedException runtimeUnavailable(Throwable cause) {
        if (cause instanceof EmbedException embed) {
            return embed;
        }
        return new EmbedException(
                503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed runtime is temporarily unavailable", null, cause);
    }

    private static EmbedException corruptedRelease(String field, Throwable cause) {
        return runtimeUnavailable(cause == null
                ? new IllegalStateException("Invalid release " + field) : cause);
    }

    private record RuntimeTarget(
            AuthenticatedEmbedSession session,
            EmbedRuntimeReleaseSnapshot release,
            Set<String> capabilities,
            Set<String> entryModes,
            EmbedRuntimeFormPort.Target portTarget) {
    }

    private record ProjectionPolicy(
            Set<String> visible,
            Set<String> writable,
            Set<String> returnable,
            Set<String> allowedActions) {
    }

    private record ProjectedField(
            Field source,
            FieldView view) {
    }

    private record ResolvedForm(
            RuntimeTarget target,
            RuntimeMode mode,
            EmbedRuntimeFormPort.FormSnapshot source,
            EmbedRuntimeFormPort.RecordSnapshot record,
            ProjectionPolicy policy,
            List<ProjectedField> fields,
            Set<String> visibleValueFields) {
    }

    /** RECORD_CREATE 用例内部使用的服务端可信授权材料。 */
    public record CreateAuthorization(
            AuthenticatedEmbedSession session,
            String viewKey,
            EmbedRuntimeFormPort.Target target,
            Map<String, Object> clientData,
            Map<String, Object> effectiveData,
            Map<String, String> visibleTypes,
            Map<String, Object> contextFilters) {
    }
}
