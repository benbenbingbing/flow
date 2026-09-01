package com.workflow.embed.application.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedNativeFormAccessPort;
import com.workflow.embed.application.port.EmbedRuntimeReleasePort;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedNativeFormTarget;
import com.workflow.embed.domain.EmbedRuntimeReleaseSnapshot;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 原生表单 Embed 的唯一目标与访问授权入口。
 *
 * <p>这里只恢复 Session 固定坐标、上下文绑定、模式能力和 VIEW DataScope；不会
 * 读取或转换字段、组件、布局、选项及弹层配置。浏览器拿到固定目标后直接运行
 * Flow 自己的 Published Form 页面。</p>
 */
@Service
public class EmbedNativeFormTargetResolver {

    private static final Pattern SAFE_RECORD_ID = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}");
    private static final Pattern SAFE_FIELD_CODE = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final Set<String> MODES = Set.of(
            "CREATE", "VIEW", "APPROVE");
    private static final Set<String> ROOT_MODES = Set.of(
            "LIST", "CREATE", "VIEW");
    private static final Map<String, String> MODE_CAPABILITY = Map.of(
            "CREATE", "RECORD_CREATE",
            "VIEW", "RECORD_VIEW");

    private final EmbedRuntimeReleasePort releasePort;
    private final EmbedNativeFormAccessPort accessPort;
    private final ObjectMapper objectMapper;

    public EmbedNativeFormTargetResolver(
            EmbedRuntimeReleasePort releasePort,
            EmbedNativeFormAccessPort accessPort,
            ObjectMapper objectMapper) {
        this.releasePort = releasePort;
        this.accessPort = accessPort;
        this.objectMapper = objectMapper;
    }

    /**
     * 恢复并授权当前 FORM Session 的固定目标；VIEW 会执行普通 Flow 的权限与
     * DataScope 校验。
     */
    public EmbedNativeFormTarget resolve(AuthenticatedEmbedSession session) {
        return authorize(session, session == null ? null : session.entryMode(),
                session == null ? null : session.recordId());
    }

    /**
     * 授权 FORM 入口或 LIST→FORM 本地导航；浏览器只能选择已发布模式和记录 ID。
     *
     * <p>FORM Session 的初始入口仍受 View/Grant capability 和
     * entryModes 限制；LIST 页面内的原生新建/查看按钮已经由
     * Flow 按 mapped user 权限求值，不能再被 Embed 入口能力二次
     * 裁剪。</p>
     */
    public EmbedNativeFormTarget authorize(
            AuthenticatedEmbedSession session,
            String requestedMode,
            String requestedRecordId) {
        EmbedNativeFormTarget root = resolveRoot(session);
        String mode = normalizeMode(requestedMode);
        EmbedRuntimeReleaseSnapshot release = release(session);
        if (!"LIST".equals(session.entryMode())) {
            requireMode(session, release, mode);
        }
        return authorizePinnedForm(
                session, root, release, mode, requestedRecordId, true);
    }

    /**
     * 授权已由服务端签名 token 精确固定的表单目标。
     *
     * <p>该入口专供原生页面内部导航：token 已经证明目标
     * Form Release 属于当前 Embed Session/View Release，因此这里只
     * 执行 mapped user 的平台权限和 DataScope，不重复套用 Embed
     * entryModes/capability ceiling。EDIT 是原生表单的 VIEW 记录模式别名；
     * APPROVE 只复用 VIEW 的记录读取与 DataScope，真正的任务审批权限仍由
     * Flow 原生流程服务在读取任务和提交审批时校验。</p>
     */
    public EmbedNativeFormTarget authorizePinnedForm(
            AuthenticatedEmbedSession session,
            EmbedNativeFormTarget pinned,
            String requestedMode,
            String requestedRecordId) {
        EmbedRuntimeReleaseSnapshot release = release(session);
        return authorizePinnedForm(
                session, pinned, release,
                normalizeMode(requestedMode), requestedRecordId,
                isRootFormTarget(release, pinned));
    }

    private EmbedNativeFormTarget authorizePinnedForm(
            AuthenticatedEmbedSession session,
            EmbedNativeFormTarget pinned,
            EmbedRuntimeReleaseSnapshot release,
            String mode,
            String requestedRecordId,
            boolean rootNavigation) {
        requireCompleteFormTarget(pinned);
        String recordId = validateRecordCoordinate(
                session, mode, requestedRecordId,
                rootNavigation && !"LIST".equals(session.entryMode()));
        String processInstanceId = null;
        if ("VIEW".equals(mode) || "APPROVE".equals(mode)) {
            Map<String, Object> trustedFilters = Objects.equals(
                    release.entityCode(), pinned.entityCode())
                    ? fixedContextFilters(release, session.context())
                    : Map.of();
            processInstanceId = accessPort.authorizeView(
                            accessTarget(pinned), recordId,
                            trustedFilters)
                    .orElseThrow(EmbedNativeFormTargetResolver::notFound)
                    .processInstanceId();
        }
        return new EmbedNativeFormTarget(
                pinned.entityCode(), pinned.formId(),
                pinned.formReleaseId(), pinned.formReleaseVersion(),
                pinned.listKey(), pinned.listReleaseId(),
                pinned.listReleaseVersion(), mode, recordId,
                processInstanceId, pinned.initialData(),
                pinned.parameters(), pinned.context());
    }

    /**
     * 恢复 LIST/FORM Release 共同固定的根坐标。该方法只做不可变快照完整性校验，
     * 具体 CREATE/VIEW 授权必须调用 {@link #authorize}。
     */
    public EmbedNativeFormTarget resolveRoot(AuthenticatedEmbedSession session) {
        if (session == null) {
            throw unavailable(null);
        }
        EmbedRuntimeReleaseSnapshot release = release(session);
        if (!Set.of("FORM", "LIST").contains(release.surfaceType())
                || !StringUtils.hasText(release.entityCode())) {
            throw unavailable(null);
        }
        boolean listId = StringUtils.hasText(release.listReleaseId());
        boolean listVersion = release.listReleaseVersion() != null;
        boolean formId = StringUtils.hasText(release.formReleaseId());
        boolean formVersion = release.formReleaseVersion() != null;
        if (listId != listVersion
                || listVersion && release.listReleaseVersion() < 1
                || listId && !StringUtils.hasText(release.listKey())
                || formId != formVersion
                || formVersion && release.formReleaseVersion() < 1
                || "FORM".equals(release.surfaceType()) && !formId
                || "LIST".equals(release.surfaceType()) && !listId) {
            throw unavailable(null);
        }
        try {
            JsonNode config = object(release.configJson());
            String resolvedFormId = config.path("resolved")
                    .path("defaultFormId").asText(null);
            Set<String> entryModes = stringSet(config.path("entryModes"));
            if ((formId != StringUtils.hasText(resolvedFormId))
                    || entryModes.isEmpty()
                    || !ROOT_MODES.containsAll(entryModes)
                    || !("LIST".equals(session.entryMode())
                    || entryModes.contains(session.entryMode()))) {
                throw unavailable(null);
            }
            BoundContext bound = boundContext(release, session.context());
            return new EmbedNativeFormTarget(
                    release.entityCode(), resolvedFormId,
                    release.formReleaseId(), release.formReleaseVersion(),
                    release.listKey(), release.listReleaseId(),
                    release.listReleaseVersion(), session.entryMode(),
                    normalizeOptionalId(session.recordId()), null,
                    bound.initialData(), bound.exposed(), bound.exposed());
        } catch (EmbedException error) {
            throw error;
        } catch (RuntimeException error) {
            throw unavailable(error);
        }
    }

    /** RECORD_CREATE 幂等摘要使用的稳定 View 标识。 */
    public String viewKey(AuthenticatedEmbedSession session) {
        String viewKey = release(session).viewKey();
        if (!StringUtils.hasText(viewKey)) {
            throw unavailable(null);
        }
        return viewKey;
    }

    /** RECORD_CREATE 用例的服务端固定过滤条件；浏览器不能提交或覆盖。 */
    public Map<String, Object> fixedContextFilters(
            AuthenticatedEmbedSession session) {
        return fixedContextFilters(release(session), session.context());
    }

    /** 按固定 Published Form 校验当前映射用户的原生 CREATE 按钮。 */
    public void requireCreateAction(
            EmbedNativeFormTarget target,
            String actionKey) {
        try {
            accessPort.requireCreateAction(accessTarget(target), actionKey);
        } catch (EmbedNativeFormAccessPort.OperationNotAllowedException error) {
            throw denied();
        } catch (RuntimeException error) {
            throw unavailable(error);
        }
    }

    private EmbedRuntimeReleaseSnapshot release(
            AuthenticatedEmbedSession session) {
        EmbedRuntimeReleaseSnapshot release = session == null ? null
                : releasePort.find(session.sessionId(), session.viewId(),
                session.viewReleaseId());
        if (release == null
                || !Objects.equals(session.viewId(), release.viewId())
                || !Objects.equals(session.viewReleaseId(), release.releaseId())) {
            throw unavailable(null);
        }
        return release;
    }

    private void requireMode(
            AuthenticatedEmbedSession session,
            EmbedRuntimeReleaseSnapshot release,
            String mode) {
        Set<String> publishedCapabilities = stringSet(
                json(release.capabilitiesJson()));
        Set<String> entryModes = stringSet(
                object(release.configJson()).path("entryModes"));
        String capability = MODE_CAPABILITY.get(mode);
        if (capability == null
                || session.capabilities() == null
                || !session.capabilities().contains(capability)
                || !publishedCapabilities.contains(capability)
                || !entryModes.contains(mode)
                || !("LIST".equals(session.entryMode())
                || mode.equals(session.entryMode()))) {
            throw denied();
        }
    }

    private static String validateRecordCoordinate(
            AuthenticatedEmbedSession session,
            String mode,
            String requestedRecordId,
            boolean bindToSessionRecord) {
        if ("CREATE".equals(mode)) {
            if (StringUtils.hasText(requestedRecordId)) {
                throw invalid();
            }
            return null;
        }
        String bound = normalizeOptionalId(session.recordId());
        if (StringUtils.hasText(session.recordId()) && bound == null) {
            throw unavailable(null);
        }
        String requested = StringUtils.hasText(requestedRecordId)
                ? normalizeOptionalId(requestedRecordId) : bound;
        if (requested == null) {
            throw invalid();
        }
        if (bindToSessionRecord
                && bound != null
                && !Objects.equals(bound, requested)) {
            throw notFound();
        }
        return requested;
    }

    /** 只有 FORM Session 的根表单 VIEW 必须与 Launch recordId 一致。 */
    private boolean isRootFormTarget(
            EmbedRuntimeReleaseSnapshot release,
            EmbedNativeFormTarget target) {
        String rootFormId = object(release.configJson())
                .path("resolved").path("defaultFormId").asText(null);
        return Objects.equals(release.entityCode(), target.entityCode())
                && Objects.equals(rootFormId, target.formId())
                && Objects.equals(
                        release.formReleaseId(), target.formReleaseId())
                && Objects.equals(
                        release.formReleaseVersion(),
                        target.formReleaseVersion());
    }

    private BoundContext boundContext(
            EmbedRuntimeReleaseSnapshot release,
            Map<String, Object> context) {
        JsonNode bindings = json(release.contextBindingsJson());
        if (!bindings.isArray()) {
            throw unavailable(null);
        }
        Map<String, Object> initialData = new LinkedHashMap<>();
        Map<String, Object> exposed = new LinkedHashMap<>();
        for (JsonNode binding : bindings) {
            String source = text(binding, "source");
            String target = text(binding, "target");
            String usage = text(binding, "usage");
            Object value = context == null ? null : context.get(source);
            if (!StringUtils.hasText(source)
                    || !StringUtils.hasText(target)
                    || context == null || !context.containsKey(source)
                    || !scalar(value)) {
                throw unavailable(null);
            }
            exposed.putIfAbsent(source, value);
            if ("FORCED_FORM_VALUE".equals(usage)) {
                if (!SAFE_FIELD_CODE.matcher(target).matches()
                        || initialData.putIfAbsent(target, value) != null) {
                    throw unavailable(null);
                }
            }
        }
        return new BoundContext(
                Collections.unmodifiableMap(initialData),
                Collections.unmodifiableMap(exposed));
    }

    private Map<String, Object> fixedContextFilters(
            EmbedRuntimeReleaseSnapshot release,
            Map<String, Object> context) {
        JsonNode bindings = json(release.contextBindingsJson());
        if (!bindings.isArray()) {
            throw unavailable(null);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (JsonNode binding : bindings) {
            if (!"FIXED_FILTER".equals(text(binding, "usage"))) {
                continue;
            }
            String source = text(binding, "source");
            String target = text(binding, "target");
            Object value = context == null ? null : context.get(source);
            if (!StringUtils.hasText(source)
                    || target == null
                    || !SAFE_FIELD_CODE.matcher(target).matches()
                    || context == null || !context.containsKey(source)
                    || value == null || !scalar(value)
                    || result.containsKey(target + "_op")
                    || result.putIfAbsent(target, value) != null
                    || result.putIfAbsent(target + "_op", "EQ") != null) {
                throw unavailable(null);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    private static EmbedNativeFormAccessPort.Target accessTarget(
            EmbedNativeFormTarget target) {
        return new EmbedNativeFormAccessPort.Target(
                target.entityCode(), target.formId(),
                target.formReleaseId(), target.formReleaseVersion(),
                target.listKey(), target.listReleaseId(),
                target.listReleaseVersion());
    }

    private static void requireCompleteFormTarget(
            EmbedNativeFormTarget target) {
        if (target == null
                || !StringUtils.hasText(target.entityCode())
                || !StringUtils.hasText(target.formId())
                || !StringUtils.hasText(target.formReleaseId())
                || target.formReleaseVersion() == null
                || target.formReleaseVersion() < 1) {
            throw denied();
        }
    }

    private JsonNode json(String document) {
        try {
            return objectMapper.readTree(document);
        } catch (Exception error) {
            throw unavailable(error);
        }
    }

    private JsonNode object(String document) {
        JsonNode value = json(document);
        if (value == null || !value.isObject()) {
            throw unavailable(null);
        }
        return value;
    }

    private static Set<String> stringSet(JsonNode array) {
        if (array == null || !array.isArray()) {
            return Set.of();
        }
        Set<String> result = new LinkedHashSet<>();
        array.forEach(value -> {
            if (value.isTextual() && StringUtils.hasText(value.asText())) {
                result.add(value.asText());
            }
        });
        return Collections.unmodifiableSet(result);
    }

    private static String text(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        return value != null && value.isTextual()
                ? value.asText() : null;
    }

    private static String normalizeMode(String value) {
        String mode = value == null ? null : value.trim().toUpperCase();
        if ("EDIT".equals(mode)) {
            mode = "VIEW";
        }
        if (!MODES.contains(mode)) {
            throw invalid();
        }
        return mode;
    }

    private static String normalizeOptionalId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return SAFE_RECORD_ID.matcher(normalized).matches()
                ? normalized : null;
    }

    private static boolean scalar(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    private static EmbedException invalid() {
        return new EmbedException(
                400, EmbedErrorCode.INVALID_REQUEST,
                "Embed request is invalid");
    }

    private static EmbedException denied() {
        return new EmbedException(
                403, EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED,
                "Embed operation is not allowed");
    }

    private static EmbedException notFound() {
        return new EmbedException(
                404, EmbedErrorCode.EMBED_RESOURCE_NOT_FOUND,
                "Embed resource was not found");
    }

    private static EmbedException unavailable(Throwable cause) {
        return new EmbedException(
                503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed runtime is unavailable", null, cause);
    }

    private record BoundContext(
            Map<String, Object> initialData,
            Map<String, Object> exposed) {
    }
}
