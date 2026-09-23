package com.workflow.admin.audit.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.model.AuditResult;
import com.workflow.contracts.audit.model.AuditSourcePointer;
import com.workflow.contracts.audit.context.OperationContext;
import com.workflow.contracts.audit.context.OperationContextHolder;
import com.workflow.contracts.audit.annotation.SystemAudit;
import com.workflow.contracts.audit.model.SystemAuditEvent;
import com.workflow.contracts.audit.port.SystemAuditPort;
import com.workflow.core.web.CorrelationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 处理标准应用服务写操作的统一系统审计。
 */
@Slf4j
@Aspect
@Component
@Order(100)
@RequiredArgsConstructor
public class SystemAuditAspect {

    private static final ThreadLocal<Integer> AUDIT_DEPTH = ThreadLocal.withInitial(() -> 0);

    private final SystemAuditPort auditPort;
    private final ObjectMapper objectMapper;

    /**
     * 记录系统审计{@code aspect}；供后续追溯或审计使用。
     *
     * @param joinPoint {@code join}{@code point}，供本方法记录系统审计{@code aspect}时使用
     * @param audit 审计，作为 {@code Around} 的输入影响后续处理
     * @return 记录后的系统审计{@code aspect}结果，供调用方继续处理
     * @throws Throwable 被调用操作抛出错误时继续向上层传播
     */
    @Around("@annotation(audit)")
    public Object record(ProceedingJoinPoint joinPoint, SystemAudit audit) throws Throwable {
        if (AUDIT_DEPTH.get() > 0) {
            return joinPoint.proceed();
        }
        AUDIT_DEPTH.set(1);
        long startedAt = System.nanoTime();
        String eventId = UUID.randomUUID().toString().replace("-", "");
        Object[] arguments = joinPoint.getArgs();
        OperationContext operationContext = operationContext(
                arguments, eventId);
        try (OperationContextHolder.Scope ignored =
                     OperationContextHolder.open(operationContext)) {
            Object beforeData = audit.captureArguments()
                    ? snapshot(argumentMap(joinPoint, arguments))
                    : null;
            try {
                Object result = joinPoint.proceed();
                ResultAssessment assessment = assessResult(result);
                auditPort.record(buildEvent(
                        joinPoint, audit, eventId, assessment.result(), arguments,
                        beforeData, result, assessment.error(), startedAt,
                        operationContext));
                return result;
            } catch (Throwable throwable) {
                try {
                    auditPort.record(buildEvent(
                            joinPoint, audit, eventId, AuditResult.FAILURE,
                            arguments, beforeData, null, throwable, startedAt,
                            operationContext));
                } catch (RuntimeException auditException) {
                    log.error("记录失败操作审计时发生异常: operation={}", audit.operation(), auditException);
                }
                throw throwable;
            }
        } finally {
            AUDIT_DEPTH.remove();
        }
    }

    /**
     * 构建事件；结果供后续流程传递或持久化。
     *
     * @param joinPoint {@code join}{@code point}，供本方法构建事件时使用
     * @param audit 审计，作为 {@code targetValue} 的输入影响后续处理
     * @param eventId 事件ID，后续用于构建事件时定位或关联目标
     * @param result 结果，作为 {@code result} 的输入影响后续处理
     * @param arguments {@code arguments}，作为 {@code targetValue} 的输入影响后续处理
     * @param beforeData 之前数据，供本方法构建事件时使用
     * @param returnValue 返回值，作为 {@code targetValue} 的输入影响后续处理
     * @param throwable {@code throwable}，作为 {@code errorCode} 的输入影响后续处理
     * @param startedAt 已启动时间，后续用于判断有效期或展示该事件的发生时间
     * @param operationContext 执行上下文，向后续事件步骤传递身份、配置或状态
     * @return 构建后的事件结果，供调用方继续处理
     */
    private SystemAuditEvent buildEvent(
            ProceedingJoinPoint joinPoint,
            SystemAudit audit,
            String eventId,
            AuditResult result,
            Object[] arguments,
            Object beforeData,
            Object returnValue,
            Throwable throwable,
            long startedAt,
            OperationContext operationContext) {
        Object target = targetValue(audit, arguments, returnValue);
        String targetId = extractProperty(target, "getId");
        if (!StringUtils.hasText(targetId)) {
            // 统一响应对象通常把真实发布记录包在 data 中；simpleValue 只提取
            // 其中的稳定 ID，不会把整个响应载荷写入来源指针。
            targetId = extractProperty(returnValue, "getData");
        }
        if (!StringUtils.hasText(targetId) && audit.targetIdArg() >= 0
                && audit.targetIdArg() < arguments.length) {
            targetId = simpleValue(arguments[audit.targetIdArg()]);
        }
        if (!StringUtils.hasText(targetId)) {
            targetId = extractProperty(returnValue, "getSourceRecordId");
        }
        String targetName = extractTargetName(target);
        String returnedOperationId = operationId(arguments, returnValue);
        AuditSourcePointer sourcePointer = sourcePointer(
                audit, targetId, operationContext);
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        return SystemAuditEvent.builder()
                .eventId(eventId)
                // operationId 串联业务操作，traceId 只跟踪请求链路；两者分别
                // 持久化，避免过去把业务幂等键塞入 traceId 后产生错误聚合。
                .operationId(StringUtils.hasText(returnedOperationId)
                        ? returnedOperationId
                        : operationContext.operationId())
                .traceId(operationContext.traceId())
                .parentOperationId(operationContext.parentOperationId())
                .sourcePointer(sourcePointer)
                .module(audit.module())
                .action(audit.action())
                .operationName(audit.operation())
                .riskLevel(audit.risk())
                .result(result)
                .required(audit.required())
                .targetType(audit.targetType())
                .targetId(targetId)
                .targetName(targetName)
                .summary(summary(audit.operation(), targetId, result))
                .beforeData(beforeData)
                .afterData(audit.captureResult() ? returnValue : null)
                .errorCode(throwable == null ? null : throwable.getClass().getSimpleName())
                .errorMessage(throwable == null ? null : throwable.getMessage())
                .durationMs(durationMs)
                .createdAt(LocalDateTime.now())
                .build();
    }

    /**
     * 生成操作ID文本，供后续匹配或展示。
     *
     * @param arguments {@code arguments}，供本方法处理操作ID时使用
     * @param returnValue 返回值，作为 {@code extractProperty} 的输入影响后续处理
     * @return 处理后的操作ID文本，供调用方比较或展示
     */
    private String operationId(
            Object[] arguments,
            Object returnValue) {
        String value = extractProperty(returnValue, "getOperationId");
        if (StringUtils.hasText(value)) {
            return value;
        }
        for (Object argument : arguments) {
            value = extractProperty(argument, "getOperationId");
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 为最外层审计用例建立同步传播上下文。调用参数已有 operationId 时直接
     * 复用；否则继承父操作，最外层请求则生成新的稳定操作 ID。
     *
     * @param arguments {@code arguments}，作为 {@code operationId} 的输入影响后续处理
     * @param eventId 事件ID，后续用于处理操作上下文时定位或关联目标
     * @return 处理后的操作上下文结果，供调用方继续处理
     */
    private OperationContext operationContext(
            Object[] arguments,
            String eventId) {
        OperationContext inherited =
                OperationContextHolder.current().orElse(null);
        String explicit = operationId(arguments, null);
        String operationId = StringUtils.hasText(explicit)
                ? explicit
                : inherited == null
                        ? "audit_" + eventId
                        : inherited.operationId();
        String traceId = inherited == null
                ? firstText(
                        MDC.get(CorrelationContext.BUSINESS_TRACE_MDC_KEY),
                        MDC.get(CorrelationContext.LEGACY_TRACE_MDC_KEY))
                : inherited.traceId();
        String parentOperationId = inherited != null
                && !operationId.equals(inherited.operationId())
                ? inherited.operationId()
                : inherited == null
                        ? null
                        : inherited.parentOperationId();
        return new OperationContext(
                operationId,
                traceId,
                parentOperationId,
                inherited == null
                        ? null
                        : inherited.sourcePointer());
    }

    /**
     * 处理来源指针，并将结果传给后续步骤。
     *
     * @param audit 审计，作为 {@code AuditSourcePointer} 的输入影响后续处理
     * @param targetId 目标ID，后续用于处理来源指针时定位或关联目标
     * @param operationContext 执行上下文，向后续来源指针步骤传递身份、配置或状态
     * @return 处理后的来源指针结果，供调用方继续处理
     */
    private AuditSourcePointer sourcePointer(
            SystemAudit audit,
            String targetId,
            OperationContext operationContext) {
        if (StringUtils.hasText(audit.targetType())
                || StringUtils.hasText(targetId)) {
            return new AuditSourcePointer(
                    audit.module().name(),
                    audit.targetType(),
                    targetId,
                    null);
        }
        return operationContext.sourcePointer();
    }

    /**
     * 按候选顺序取首个非空文本，供后续匹配或展示使用。
     *
     * @param values 待写入的列值映射，后续作为绑定参数生成插入语句
     * @return 处理后的首个文本文本，供调用方比较或展示
     */
    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 处理快照，并将结果传给后续步骤。
     *
     * @param value 待处理快照的原始输入，结果供调用方继续使用
     * @return 处理后的快照结果，供调用方继续处理
     */
    private Object snapshot(Object value) {
        try {
            return objectMapper.valueToTree(value);
        } catch (IllegalArgumentException exception) {
            return Map.of(
                    "snapshotError", exception.getClass().getSimpleName(),
                    "valueType", value == null ? "null" : value.getClass().getName());
        }
    }

    /**
     * 处理目标值，并将结果传给后续步骤。
     *
     * @param audit 审计，供本方法处理目标值时使用
     * @param arguments {@code arguments}，供本方法处理目标值时使用
     * @param returnValue 返回值，供本方法处理目标值时使用
     * @return 处理后的目标值结果，供调用方继续处理
     */
    private Object targetValue(SystemAudit audit, Object[] arguments, Object returnValue) {
        if (audit.targetIdArg() >= 0 && audit.targetIdArg() < arguments.length) {
            return arguments[audit.targetIdArg()];
        }
        if (returnValue != null) {
            return returnValue;
        }
        for (Object argument : arguments) {
            if (argument != null && extractProperty(argument, "getId") != null) {
                return argument;
            }
        }
        return null;
    }

    /**
     * 整理{@code argument}映射数据，供调用方遍历或继续处理。
     *
     * @param joinPoint {@code join}{@code point}，供本方法处理{@code argument}映射时使用
     * @param arguments {@code arguments}，供本方法处理{@code argument}映射时使用
     * @return {@code argument}映射键值结果，供调用方继续处理
     */
    private Map<String, Object> argumentMap(ProceedingJoinPoint joinPoint, Object[] arguments) {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        String[] names = ((MethodSignature) joinPoint.getSignature()).getParameterNames();
        Map<String, Object> values = new LinkedHashMap<>();
        for (int index = 0; index < arguments.length; index++) {
            Object argument = arguments[index];
            if (isInfrastructureArgument(argument)) {
                continue;
            }
            String name = names != null && index < names.length
                    ? names[index]
                    : method.getName() + "Arg" + index;
            values.put(name, argument);
        }
        return values;
    }

    /**
     * 判断是否{@code infrastructure}{@code argument}；判断结果决定调用方的后续分支。
     *
     * @param argument {@code argument}，供本方法判断是否{@code infrastructure}{@code argument}时使用
     * @return {@code infrastructure}{@code argument}条件成立时为 true，否则为 false
     */
    private boolean isInfrastructureArgument(Object argument) {
        if (argument == null) {
            return false;
        }
        String className = argument.getClass().getName();
        return className.startsWith("jakarta.servlet.")
                || className.startsWith("org.springframework.web.multipart.")
                || className.contains("HttpServlet");
    }

    /**
     * 提取目标名称；输出作为后续校验或处理的输入。
     *
     * @param target 目标，作为 {@code extractProperty} 的输入影响后续处理
     * @return 提取后的目标名称文本，供调用方比较或展示
     */
    private String extractTargetName(Object target) {
        if (target == null) {
            return null;
        }
        for (String getter : new String[]{
                "getName", "getUsername", "getRoleName", "getMenuName",
                "getGroupName", "getOrgName", "getDictName", "getItemLabel",
                "getEntityName", "getProcessName", "getTitle", "getCode"}) {
            String value = extractProperty(target, getter);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 提取属性；输出作为后续校验或处理的输入。
     *
     * @param target 目标，作为 {@code simpleValue} 的输入影响后续处理
     * @param methodName {@code method}名称，后续用于提取属性时匹配或展示
     * @return 提取后的属性文本，供调用方比较或展示
     */
    private String extractProperty(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        if (target instanceof Map<?, ?> map && "getId".equals(methodName)) {
            return simpleValue(map.get("id"));
        }
        try {
            Method method = target.getClass().getMethod(methodName);
            return simpleValue(method.invoke(target));
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    /**
     * 生成简要值文本，供后续匹配或展示。
     *
     * @param value 待处理简要值的原始输入，结果供调用方继续使用
     * @return 处理后的简要值文本，供调用方比较或展示
     */
    private String simpleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Enum<?>) {
            return String.valueOf(value);
        }
        return extractProperty(value, "getId");
    }

    /**
     * 生成摘要文本，供后续匹配或展示。
     *
     * @param operation 操作标识，决定后续摘要采用的处理分支
     * @param targetId 目标ID，后续用于处理摘要时定位或关联目标
     * @param result 结果，供本方法处理摘要时使用
     * @return 处理后的摘要文本，供调用方比较或展示
     */
    private String summary(String operation, String targetId, AuditResult result) {
        StringBuilder value = new StringBuilder(operation)
                .append("：")
                .append(result == AuditResult.SUCCESS ? "成功" : "失败");
        if (StringUtils.hasText(targetId)) {
            value.append("，目标ID=").append(targetId);
        }
        return value.toString();
    }

    /**
     * 处理{@code assess}结果，并将结果传给后续步骤。
     *
     * @param value 待处理{@code assess}结果的原始输入，结果供调用方继续使用
     * @return 处理后的{@code assess}结果，供调用方继续处理
     */
    private ResultAssessment assessResult(Object value) {
        if (value == null) {
            return new ResultAssessment(AuditResult.SUCCESS, null);
        }
        try {
            Method codeMethod = value.getClass().getMethod("getCode");
            Object codeValue = codeMethod.invoke(value);
            if (codeValue instanceof Number number && number.intValue() >= 400) {
                String message = extractProperty(value, "getMessage");
                return new ResultAssessment(
                        AuditResult.FAILURE,
                        new ReturnedFailureException(message));
            }
        } catch (ReflectiveOperationException ignored) {
            // 非统一响应对象按正常返回处理。
        }
        return new ResultAssessment(AuditResult.SUCCESS, null);
    }

    /**
     * 封装结果{@code assessment}的不可变数据；各分量供后续校验、传递或结果展示使用。
     *
     * @param result 结果，保存在对象中供后续校验、查询或展示
     * @param error 错误，保存在对象中供后续校验、查询或展示
     */
    private record ResultAssessment(AuditResult result, Throwable error) {
    }

    /**
     * 表示{@code returned}失败处理失败；调用方可据此区分错误并终止后续操作。
     */
    private static final class ReturnedFailureException extends RuntimeException {
        /**
         * 初始化{@code returned}失败异常，保存构造参数供后续方法使用。
         *
         * @param message 消息，保存在对象中供后续校验、查询或展示
         */
        private ReturnedFailureException(String message) {
            super(message);
        }
    }
}
