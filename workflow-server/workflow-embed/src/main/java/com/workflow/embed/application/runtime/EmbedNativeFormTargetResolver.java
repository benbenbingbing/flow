package com.workflow.embed.application.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.runtime.port.EmbedNativeFormAccessPort.OperationNotAllowedException;
import com.workflow.contracts.embed.runtime.port.EmbedNativeFormAccessPort.Target;
import com.workflow.contracts.embed.runtime.port.EmbedNativeFormAccessPort;
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

    /**
     * 初始化嵌入式原生表单目标解析器，保存构造参数供后续方法使用。
     *
     * @param releasePort 发布版本端口依赖，保存到当前对象供后续业务方法调用
     * @param accessPort 访问端口依赖，保存到当前对象供后续业务方法调用
     * @param objectMapper 对象映射器依赖，保存到当前对象供后续业务方法调用
     */
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
     *
     * @param session 会话，作为 {@code authorize} 的输入影响后续处理
     * @return 解析后的嵌入式原生表单目标解析器结果，供调用方继续处理
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
     *
     * @param session 会话，作为 {@code resolveRoot} 的输入影响后续处理
     * @param requestedMode 请求模式标识，决定后续授权采用的处理分支
     * @param requestedRecordId 请求记录ID，后续用于处理授权时定位或关联目标
     * @return 处理后的授权结果，供调用方继续处理
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
     *
     * @param session 会话，作为 {@code release} 的输入影响后续处理
     * @param pinned 固定，供本方法处理授权固定表单时使用
     * @param requestedMode 请求模式标识，决定后续授权固定表单采用的处理分支
     * @param requestedRecordId 请求记录ID，后续用于处理授权固定表单时定位或关联目标
     * @return 处理后的授权固定表单结果，供调用方继续处理
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

    /**
     * 处理授权固定表单，并将结果传给后续步骤。
     *
     * @param session 会话，作为 {@code validateRecordCoordinate} 的输入影响后续处理
     * @param pinned 固定，作为 {@code requireCompleteFormTarget} 的输入影响后续处理
     * @param release 发布版本，供本方法处理授权固定表单时使用
     * @param mode 模式标识，决定后续授权固定表单采用的处理分支
     * @param requestedRecordId 请求记录ID，后续用于处理授权固定表单时定位或关联目标
     * @param rootNavigation 根{@code navigation}，作为 {@code validateRecordCoordinate} 的输入影响后续处理
     * @return 处理后的授权固定表单结果，供调用方继续处理
     */
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
     *
     * @param session 会话，作为 {@code release} 的输入影响后续处理
     * @return 解析后的根结果，供调用方继续处理
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

    /**
     * RECORD_CREATE 幂等摘要使用的稳定 View 标识。
     *
     * @param session 会话，作为 {@code release} 的输入影响后续处理
     * @return 处理后的视图键文本，供调用方比较或展示
     */
    public String viewKey(AuthenticatedEmbedSession session) {
        String viewKey = release(session).viewKey();
        if (!StringUtils.hasText(viewKey)) {
            throw unavailable(null);
        }
        return viewKey;
    }

    /**
     * RECORD_CREATE 用例的服务端固定过滤条件；浏览器不能提交或覆盖。
     *
     * @param session 会话，供本方法处理固定上下文过滤条件时使用
     * @return 固定上下文过滤条件键值结果，供调用方继续处理
     */
    public Map<String, Object> fixedContextFilters(
            AuthenticatedEmbedSession session) {
        return fixedContextFilters(release(session), session.context());
    }

    /**
     * 按固定 Published Form 校验当前映射用户的原生 CREATE 按钮。
     *
     * @param target 目标，供本方法校验并获取创建动作时使用
     * @param actionKey 动作键，后续用于授权校验、关联或幂等去重
     */
    public void requireCreateAction(
            EmbedNativeFormTarget target,
            String actionKey) {
        try {
            accessPort.requireCreateAction(accessTarget(target), actionKey);
        } catch (OperationNotAllowedException error) {
            throw denied();
        } catch (RuntimeException error) {
            throw unavailable(error);
        }
    }

    /**
     * 处理发布版本，并将结果传给后续步骤。
     *
     * @param session 会话，作为 {@code releasePort.find} 的输入影响后续处理
     * @return 处理后的发布版本结果，供调用方继续处理
     */
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

    /**
     * 校验并获取模式；不满足约束时阻止后续处理。
     *
     * @param session 会话，供本方法校验并获取模式时使用
     * @param release 发布版本，作为 {@code stringSet} 的输入影响后续处理
     * @param mode 模式标识，决定后续模式采用的处理分支
     */
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

    /**
     * 校验记录坐标；不满足约束时阻止后续处理。
     *
     * @param session 会话，作为 {@code normalizeOptionalId} 的输入影响后续处理
     * @param mode 模式标识，决定后续记录坐标采用的处理分支
     * @param requestedRecordId 请求记录ID，后续用于校验记录坐标时定位或关联目标
     * @param bindToSessionRecord 绑定截止会话记录，供本方法校验记录坐标时使用
     * @return 校验后的记录坐标文本，供调用方比较或展示
     */
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

    /**
     * 只有 FORM Session 的根表单 VIEW 必须与 Launch recordId 一致。
     *
     * @param release 发布版本，作为 {@code object} 的输入影响后续处理
     * @param target 目标，供本方法判断是否根表单目标时使用
     * @return 根表单目标条件成立时为 true，否则为 false
     */
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

    /**
     * 处理绑定上下文，并将结果传给后续步骤。
     *
     * @param release 发布版本，作为 {@code json} 的输入影响后续处理
     * @param context 执行上下文，向后续绑定上下文步骤传递身份、配置或状态
     * @return 处理后的绑定上下文结果，供调用方继续处理
     */
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

    /**
     * 整理固定上下文过滤条件数据，供调用方遍历或继续处理。
     *
     * @param release 发布版本，作为 {@code json} 的输入影响后续处理
     * @param context 执行上下文，向后续固定上下文过滤条件步骤传递身份、配置或状态
     * @return 固定上下文过滤条件键值结果，供调用方继续处理
     */
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

    /**
     * 处理访问目标，并将结果传给后续步骤。
     *
     * @param target 目标，作为 {@code Target} 的输入影响后续处理
     * @return 处理后的访问目标结果，供调用方继续处理
     */
    private static Target accessTarget(
            EmbedNativeFormTarget target) {
        return new Target(
                target.entityCode(), target.formId(),
                target.formReleaseId(), target.formReleaseVersion(),
                target.listKey(), target.listReleaseId(),
                target.listReleaseVersion());
    }

    /**
     * 校验并获取完成表单目标；不满足约束时阻止后续处理。
     *
     * @param target 目标，作为 {@code hasText} 的输入影响后续处理
     */
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

    /**
     * 处理JSON，并将结果传给后续步骤。
     *
     * @param document 文档，作为 {@code objectMapper.readTree} 的输入影响后续处理
     * @return 处理后的JSON结果，供调用方继续处理
     */
    private JsonNode json(String document) {
        try {
            return objectMapper.readTree(document);
        } catch (Exception error) {
            throw unavailable(error);
        }
    }

    /**
     * 处理对象，并将结果传给后续步骤。
     *
     * @param document 文档，作为 {@code json} 的输入影响后续处理
     * @return 处理后的对象结果，供调用方继续处理
     */
    private JsonNode object(String document) {
        JsonNode value = json(document);
        if (value == null || !value.isObject()) {
            throw unavailable(null);
        }
        return value;
    }

    /**
     * 整理字符串设置数据，供调用方遍历或继续处理。
     *
     * @param array 数组，供本方法处理字符串设置时使用
     * @return 嵌入式原生表单目标解析器集合，供调用方遍历或展示
     */
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

    /**
     * 将输入转换为文本，供后续校验、映射或展示使用。
     *
     * @param object 对象，供本方法处理文本时使用
     * @param field 字段，作为 {@code object.get} 的输入影响后续处理
     * @return 处理后的文本文本，供调用方比较或展示
     */
    private static String text(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        return value != null && value.isTextual()
                ? value.asText() : null;
    }

    /**
     * 规范化模式；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化模式的原始输入，结果供调用方继续使用
     * @return 规范化后的模式文本，供调用方比较或展示
     */
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

    /**
     * 规范化可选ID；输出作为后续校验或处理的输入。
     *
     * @param value 待规范化可选ID的原始输入，结果供调用方继续使用
     * @return 规范化后的可选ID文本，供调用方比较或展示
     */
    private static String normalizeOptionalId(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return SAFE_RECORD_ID.matcher(normalized).matches()
                ? normalized : null;
    }

    /**
     * 判断标量条件是否成立，供调用方选择后续分支。
     *
     * @param value 待处理标量的原始输入，结果供调用方继续使用
     * @return 标量条件成立时为 true，否则为 false
     */
    private static boolean scalar(Object value) {
        return value == null
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean;
    }

    /**
     * 构造无效输入异常，阻止后续业务处理。
     *
     * @return 处理后的无效结果，供调用方继续处理
     */
    private static EmbedException invalid() {
        return new EmbedException(
                400, EmbedErrorCode.INVALID_REQUEST,
                "Embed request is invalid");
    }

    /**
     * 构造已拒绝异常，供调用方区分失败原因并终止后续处理。
     *
     * @return 处理后的已拒绝结果，供调用方继续处理
     */
    private static EmbedException denied() {
        return new EmbedException(
                403, EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED,
                "Embed operation is not allowed");
    }

    /**
     * 构造目标不存在异常，供调用方终止后续处理。
     *
     * @return 处理后的非已找到结果，供调用方继续处理
     */
    private static EmbedException notFound() {
        return new EmbedException(
                404, EmbedErrorCode.EMBED_RESOURCE_NOT_FOUND,
                "Embed resource was not found");
    }

    /**
     * 构造服务不可用异常，供调用方区分失败原因。
     *
     * @param cause 原因，作为 {@code EmbedException} 的输入影响后续处理
     * @return 处理后的不可用结果，供调用方继续处理
     */
    private static EmbedException unavailable(Throwable cause) {
        return new EmbedException(
                503, EmbedErrorCode.EMBED_RUNTIME_UNAVAILABLE,
                "Embed runtime is unavailable", null, cause);
    }

    /**
     * 封装绑定上下文的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param initialData 初始数据，保存在对象中供后续校验、查询或展示
     * @param exposed {@code exposed}，保存在对象中供后续校验、查询或展示
     */
    private record BoundContext(
            Map<String, Object> initialData,
            Map<String, Object> exposed) {
    }
}
