package com.workflow.embed.application.audit;

import com.workflow.contracts.audit.model.AuditAction;
import com.workflow.contracts.audit.model.AuditModule;
import com.workflow.contracts.audit.model.AuditResult;
import com.workflow.contracts.audit.model.AuditRiskLevel;
import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Embed V1 运行时审计边界。
 *
 * <p>首次记录创建使用 required 事件并加入业务事务；读请求、幂等重放和请求失败
 * 使用 best-effort 事件。所有事件都由封闭的操作枚举与坐标白名单构造，调用方不能
 * 传入任意 Map，因此 token、launch code、JWT、外部 Subject、Context、Origin
 * 和 User-Agent 不会进入审计载荷。</p>
 */
@Component
public class EmbedRuntimeAudit {

    private static final Logger LOGGER = LoggerFactory.getLogger(EmbedRuntimeAudit.class);
    private static final String RECORDS_PATH = "/api/embed/v1/runtime/records";
    private static final Pattern NATIVE_RECORD_DETAIL_PATH = Pattern.compile(
            "^/api/entity-data/entity/[A-Za-z][A-Za-z0-9_]{0,99}"
                    + "/detail/([A-Za-z0-9][A-Za-z0-9_.:-]{0,127})/load$");

    private final SystemAuditPort auditPort;
    private final Clock clock;

    /**
     * 初始化嵌入式运行时审计，保存构造参数供后续方法使用。
     *
     * @param auditPort 审计端口，保存在对象中供后续校验、查询或展示
     */
    @Autowired
    public EmbedRuntimeAudit(SystemAuditPort auditPort) {
        this(auditPort, Clock.systemUTC());
    }

    /**
     * 初始化嵌入式运行时审计，保存构造参数供后续方法使用。
     *
     * @param auditPort 审计端口依赖，保存到当前对象供后续业务方法调用
     * @param clock 时钟依赖，保存到当前对象供后续业务方法调用
     */
    EmbedRuntimeAudit(SystemAuditPort auditPort, Clock clock) {
        this.auditPort = auditPort;
        this.clock = clock;
    }

    /**
     * 首次创建成功的 required 审计。调用方必须处于实体写、Embed Receipt 和幂等
     * 完成所在的同一事务；审计异常故意向上抛出，使业务事务整体回滚。
     *
     * @param session 会话，作为 {@code requireCoordinates} 的输入影响后续处理
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     * @param traceId 追踪ID，后续用于记录已创建必填时定位或关联目标
     * @param durationMs 时长{@code ms}，供本方法记录已创建必填时使用
     */
    public void recordCreatedRequired(
            AuthenticatedEmbedSession session,
            String recordId,
            String traceId,
            long durationMs) {
        requireCoordinates(session, traceId);
        if (!StringUtils.hasText(recordId)) {
            throw new IllegalArgumentException("Embed create audit recordId is required");
        }
        auditPort.record(event(
                session,
                Operation.RECORD_CREATE,
                Operation.RECORD_CREATE.operationName,
                AuditResult.SUCCESS,
                true,
                "CREATED",
                201,
                traceId,
                durationMs,
                "RECORD",
                recordId,
                null));
    }

    /**
     * 在 HTTP 响应完成后记录读、重放或失败结果。首次创建成功由事务内 required
     * 事件负责，避免产生两个含义相同的成功事件。
     *
     * @param session 会话，供本方法记录{@code completed}{@code best}{@code effort}时使用
     * @param requestOperation 请求操作，供本方法记录{@code completed}{@code best}{@code effort}时使用
     * @param traceId 追踪ID，后续用于记录{@code completed}{@code best}{@code effort}时定位或关联目标
     * @param status 状态标识，决定后续{@code completed}{@code best}{@code effort}采用的处理分支
     * @param idempotentReplay 幂等重放，供本方法记录{@code completed}{@code best}{@code effort}时使用
     * @param location {@code location}，供本方法记录{@code completed}{@code best}{@code effort}时使用
     * @param durationMs 时长{@code ms}，供本方法记录{@code completed}{@code best}{@code effort}时使用
     */
    public void recordCompletedBestEffort(
            AuthenticatedEmbedSession session,
            RequestOperation requestOperation,
            String traceId,
            int status,
            boolean idempotentReplay,
            String location,
            long durationMs) {
        if (session == null || requestOperation == null) {
            return;
        }
        boolean success = status >= 200 && status < 300;
        if (requestOperation.operation() == Operation.RECORD_CREATE
                && success && !idempotentReplay) {
            return;
        }
        Operation operation = requestOperation.operation();
        String operationName = operation == Operation.RECORD_CREATE
                && idempotentReplay
                ? "EMBED_RECORD_CREATE_REPLAY"
                : operation.operationName;
        String outcome = idempotentReplay && success
                ? "IDEMPOTENT_REPLAY"
                : success ? "SUCCESS" : "FAILURE";
        String targetType = operation == Operation.LIST_QUERY
                ? "EMBED_VIEW" : "RECORD";
        String targetId = operation == Operation.RECORD_DETAIL
                ? requestOperation.recordId()
                : session.viewId();
        recordBestEffort(operationName, () -> event(
                session,
                operation,
                operationName,
                success ? AuditResult.SUCCESS : AuditResult.FAILURE,
                false,
                outcome,
                status,
                traceId,
                durationMs,
                targetType,
                targetId,
                success ? null : "HTTP_" + status));
    }

    /**
     * 记录未被 MVC 异常映射器处理的运行时失败，不写入异常 message 或类型。
     *
     * @param session 会话，供本方法记录{@code unhandled}失败{@code best}{@code effort}时使用
     * @param requestOperation 请求操作，供本方法记录{@code unhandled}失败{@code best}{@code effort}时使用
     * @param traceId 追踪ID，后续用于记录{@code unhandled}失败{@code best}{@code effort}时定位或关联目标
     * @param durationMs 时长{@code ms}，供本方法记录{@code unhandled}失败{@code best}{@code effort}时使用
     */
    public void recordUnhandledFailureBestEffort(
            AuthenticatedEmbedSession session,
            RequestOperation requestOperation,
            String traceId,
            long durationMs) {
        if (session == null || requestOperation == null) {
            return;
        }
        Operation operation = requestOperation.operation();
        String targetType = operation == Operation.LIST_QUERY
                ? "EMBED_VIEW" : "RECORD";
        String targetId = operation == Operation.RECORD_DETAIL
                ? requestOperation.recordId() : session.viewId();
        recordBestEffort(operation.operationName, () -> event(
                session,
                operation,
                operation.operationName,
                AuditResult.FAILURE,
                false,
                "FAILURE",
                503,
                traceId,
                durationMs,
                targetType,
                targetId,
                "UNHANDLED_FAILURE"));
    }

    /**
     * 只识别本期需要审计的三类稳定路由；详情审计跟随 Flow 原生加载端点，
     * 不再保留旧 Embed record 投影路由。动态记录 ID 必须满足安全字符约束，
     * 避免把任意 URL 片段写入审计。
     *
     * @param method {@code method}，供本方法处理{@code classify}时使用
     * @param requestUri 请求{@code uri}，作为 {@code NATIVE_RECORD_DETAIL_PATH.matcher} 的输入影响后续处理
     * @return 匹配的{@code classify}；未找到时为空
     */
    public static Optional<RequestOperation> classify(
            String method,
            String requestUri) {
        if ("POST".equalsIgnoreCase(method)
                && "/api/embed/v1/runtime/list/query".equals(requestUri)) {
            return Optional.of(new RequestOperation(Operation.LIST_QUERY, null));
        }
        if ("POST".equalsIgnoreCase(method) && RECORDS_PATH.equals(requestUri)) {
            return Optional.of(new RequestOperation(Operation.RECORD_CREATE, null));
        }
        if ("POST".equalsIgnoreCase(method) && requestUri != null) {
            Matcher detail = NATIVE_RECORD_DETAIL_PATH.matcher(requestUri);
            if (detail.matches()) {
                return Optional.of(new RequestOperation(
                        Operation.RECORD_DETAIL, detail.group(1)));
            }
        }
        return Optional.empty();
    }

    /**
     * 记录{@code best}{@code effort}；供后续追溯或审计使用。
     *
     * @param operationName 操作名称，后续用于记录{@code best}{@code effort}时匹配或展示
     * @param eventSupplier 事件{@code supplier}，作为 {@code auditPort.record} 的输入影响后续处理
     */
    private void recordBestEffort(
            String operationName,
            Supplier<SystemAuditEvent> eventSupplier) {
        try {
            auditPort.record(eventSupplier.get());
        } catch (RuntimeException failure) {
            // best-effort 审计不得覆盖已经完成的业务响应；日志只含封闭操作码
            // 和异常类型。
            LOGGER.warn(
                    "Embed runtime best-effort audit failed: operation={}, exceptionType={}",
                    operationName, failure.getClass().getName());
        }
    }

    /**
     * 处理事件，并将结果传给后续步骤。
     *
     * @param session 会话，作为 {@code requireCoordinates} 的输入影响后续处理
     * @param operation 操作标识，决定后续事件采用的处理分支
     * @param operationName 操作名称，后续用于处理事件时匹配或展示
     * @param result 结果，供本方法处理事件时使用
     * @param required 必填，供本方法处理事件时使用
     * @param outcome 结果，作为 {@code coordinates.put} 的输入影响后续处理
     * @param status 状态标识，决定后续事件采用的处理分支
     * @param traceId 追踪ID，后续用于处理事件时定位或关联目标
     * @param durationMs 时长{@code ms}，供本方法处理事件时使用
     * @param targetType 目标类型标识，决定后续事件采用的处理分支
     * @param targetId 目标ID，后续用于处理事件时定位或关联目标
     * @param errorCode 错误编码，后续用于处理事件时定位或关联目标
     * @return 处理后的事件结果，供调用方继续处理
     */
    private SystemAuditEvent event(
            AuthenticatedEmbedSession session,
            Operation operation,
            String operationName,
            AuditResult result,
            boolean required,
            String outcome,
            int status,
            String traceId,
            long durationMs,
            String targetType,
            String targetId,
            String errorCode) {
        requireCoordinates(session, traceId);
        Map<String, Object> coordinates = new LinkedHashMap<>();
        coordinates.put("applicationId", session.applicationId());
        coordinates.put("viewId", session.viewId());
        coordinates.put("viewReleaseId", session.viewReleaseId());
        coordinates.put("sessionId", session.sessionId());
        coordinates.put("flowUserId", session.flowUserId());
        coordinates.put("operation", operationName);
        coordinates.put("outcome", outcome);
        coordinates.put("httpStatus", status);
        return SystemAuditEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .traceId(traceId)
                .module(AuditModule.ENTITY)
                .action(operation.action)
                .operationName(operationName)
                .riskLevel(operation.riskLevel)
                .result(result)
                .required(required)
                .operatorId(session.flowUserId())
                .operatorName(session.flowUsername())
                .requestMethod(operation.method)
                .requestPath(operation.pathTemplate)
                .targetType(targetType)
                .targetId(targetId)
                .summary(operationName)
                .afterData(Collections.unmodifiableMap(coordinates))
                .errorCode(errorCode)
                .durationMs(Math.max(0L, durationMs))
                .createdAt(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .build();
    }

    /**
     * 校验并获取{@code coordinates}；不满足约束时阻止后续处理。
     *
     * @param session 会话，作为 {@code hasText} 的输入影响后续处理
     * @param traceId 追踪ID，后续用于校验并获取{@code coordinates}时定位或关联目标
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    private static void requireCoordinates(
            AuthenticatedEmbedSession session,
            String traceId) {
        if (session == null
                || !StringUtils.hasText(session.applicationId())
                || !StringUtils.hasText(session.viewId())
                || !StringUtils.hasText(session.viewReleaseId())
                || !StringUtils.hasText(session.sessionId())
                || !StringUtils.hasText(session.flowUserId())
                || !StringUtils.hasText(traceId)) {
            throw new IllegalArgumentException(
                    "Embed runtime audit coordinates are incomplete");
        }
    }

    /**
     * 封闭的运行时操作集合，同时作为审计和后续细分流控的稳定分类。
     */
    public enum Operation {
        LIST_QUERY(
                "EMBED_RUNTIME_LIST_QUERY",
                AuditAction.OTHER,
                AuditRiskLevel.LOW,
                "POST",
                "/api/embed/v1/runtime/list/query"),
        RECORD_DETAIL(
                "EMBED_RUNTIME_RECORD_DETAIL",
                AuditAction.OTHER,
                AuditRiskLevel.LOW,
                "POST",
                "/api/entity-data/entity/{entityCode}/detail/{recordId}/load"),
        RECORD_CREATE(
                "EMBED_RECORD_CREATE",
                AuditAction.CREATE,
                AuditRiskLevel.HIGH,
                "POST",
                RECORDS_PATH);

        private final String operationName;
        private final AuditAction action;
        private final AuditRiskLevel riskLevel;
        private final String method;
        private final String pathTemplate;

        /**
         * 初始化操作，保存构造参数供后续方法使用。
         *
         * @param operationName 操作名称依赖，保存到当前对象供后续业务方法调用
         * @param action 动作依赖，保存到当前对象供后续业务方法调用
         * @param riskLevel 风险层级依赖，保存到当前对象供后续业务方法调用
         * @param method {@code method}依赖，保存到当前对象供后续业务方法调用
         * @param pathTemplate 路径模板依赖，保存到当前对象供后续业务方法调用
         */
        Operation(
                String operationName,
                AuditAction action,
                AuditRiskLevel riskLevel,
                String method,
                String pathTemplate) {
            this.operationName = operationName;
            this.action = action;
            this.riskLevel = riskLevel;
            this.method = method;
            this.pathTemplate = pathTemplate;
        }

        /**
         * 生成操作名称文本，供后续匹配或展示。
         *
         * @return 处理后的操作名称文本，供调用方比较或展示
         */
        public String operationName() {
            return operationName;
        }
    }

    /**
     * 封装请求操作的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param operation 操作标识，决定后续请求操作采用的处理分支
     * @param recordId 业务记录 ID，用于定位目标数据并关联后续变更或审计
     */
    public record RequestOperation(Operation operation, String recordId) {
    }
}
