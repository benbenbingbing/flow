package com.workflow.embed.application.record;

import com.workflow.contracts.embed.EmbedRecordCreatePort;
import com.workflow.embed.application.runtime.EmbedNativeFormTargetResolver;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedNativeFormTarget;
import com.workflow.embed.security.EmbedContextHolder;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 为受控 RECORD_CREATE 构造与原生表单一致的最小可信授权材料。
 *
 * <p>本服务只限制通用 JSON 资源复杂度、固定坐标、Context 强制值和原生按钮；
 * 字段默认值、联动、必填、唯一性及组件值语义全部由实体域的标准 Published Form
 * 提交链处理，不在 Embed 中维护副本。</p>
 */
@Service
public class EmbedNativeRecordCreateAuthorizationService {

    private static final Pattern SAFE_KEY = Pattern.compile(
            "[A-Za-z][A-Za-z0-9_]{0,99}");
    private static final Pattern SENSITIVE_KEY = Pattern.compile(
            "(?i)(^password$|password_hash|token_version|(^|_)(secret|token|private_key|credential|salt|otp|mfa_secret)(_|$))");

    private final EmbedNativeFormTargetResolver targetResolver;

    public EmbedNativeRecordCreateAuthorizationService(
            EmbedNativeFormTargetResolver targetResolver) {
        this.targetResolver = targetResolver;
    }

    /** 固定 CREATE 目标和动作，并把服务端 Context 放在浏览器值之后强制覆盖。 */
    public Authorization authorize(
            Map<String, Object> requestedData,
            String requestedAction) {
        String actionKey = StringUtils.hasText(requestedAction)
                ? requestedAction.trim() : "save";
        if (!Set.of("save", "saveAndStart").contains(actionKey)) {
            throw denied();
        }
        AuthenticatedEmbedSession session = EmbedContextHolder.require();
        EmbedNativeFormTarget target = targetResolver.authorize(
                session, "CREATE", null);
        targetResolver.requireCreateAction(target, actionKey);
        Map<String, Object> clientData = nativePayload(requestedData);
        Map<String, Object> effective = new LinkedHashMap<>(clientData);
        effective.putAll(target.initialData() == null
                ? Map.of() : target.initialData());
        EmbedRecordCreatePort.Target createTarget =
                new EmbedRecordCreatePort.Target(
                        target.entityCode(), target.formId(),
                        target.formReleaseId(), target.formReleaseVersion());
        return new Authorization(
                session,
                targetResolver.viewKey(session),
                createTarget,
                Collections.unmodifiableMap(effective),
                targetResolver.fixedContextFilters(session),
                actionKey,
                "saveAndStart".equals(actionKey));
    }

    private static Map<String, Object> nativePayload(
            Map<String, Object> source) {
        if (source == null || source.size() > 500) {
            throw invalid();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (key == null || !SAFE_KEY.matcher(key).matches()
                    || forbiddenKey(key)) {
                throw invalid();
            }
            result.put(key, nativeValue(value, 0, new int[] {0}));
        });
        return Collections.unmodifiableMap(result);
    }

    /**
     * 限制浏览器 JSON 深度和节点数，防止资源消耗及原型污染；这不是字段类型或
     * 组件兼容校验，合法业务值仍由 Flow 标准提交服务判定。
     */
    private static Object nativeValue(
            Object value,
            int depth,
            int[] entries) {
        if (depth > 16 || ++entries[0] > 5_000) {
            throw invalid();
        }
        if (value == null || value instanceof String
                || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)
                        || key.length() > 200 || forbiddenKey(key)) {
                    throw invalid();
                }
                copy.put(key, nativeValue(
                        entry.getValue(), depth + 1, entries));
            }
            return Collections.unmodifiableMap(copy);
        }
        if (value instanceof Collection<?> values) {
            if (values.size() > 1_000) {
                throw invalid();
            }
            return values.stream()
                    .map(item -> nativeValue(item, depth + 1, entries))
                    .toList();
        }
        throw invalid();
    }

    private static boolean forbiddenKey(String key) {
        return Set.of("__proto__", "prototype", "constructor").contains(key)
                || SENSITIVE_KEY.matcher(key).find();
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

    /** RECORD_CREATE 幂等与事务服务内部使用的服务端可信材料。 */
    public record Authorization(
            AuthenticatedEmbedSession session,
            String viewKey,
            EmbedRecordCreatePort.Target target,
            Map<String, Object> effectiveData,
            Map<String, Object> contextFilters,
            String actionKey,
            boolean startProcess) {
    }
}
