package com.workflow.admin.audit.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditSourcePointer;
import com.workflow.contracts.audit.OperationContext;
import com.workflow.contracts.audit.OperationContextHolder;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
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

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private Object snapshot(Object value) {
        try {
            return objectMapper.valueToTree(value);
        } catch (IllegalArgumentException exception) {
            return Map.of(
                    "snapshotError", exception.getClass().getSimpleName(),
                    "valueType", value == null ? "null" : value.getClass().getName());
        }
    }

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

    private boolean isInfrastructureArgument(Object argument) {
        if (argument == null) {
            return false;
        }
        String className = argument.getClass().getName();
        return className.startsWith("jakarta.servlet.")
                || className.startsWith("org.springframework.web.multipart.")
                || className.contains("HttpServlet");
    }

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

    private String simpleValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof CharSequence || value instanceof Number || value instanceof Enum<?>) {
            return String.valueOf(value);
        }
        return extractProperty(value, "getId");
    }

    private String summary(String operation, String targetId, AuditResult result) {
        StringBuilder value = new StringBuilder(operation)
                .append("：")
                .append(result == AuditResult.SUCCESS ? "成功" : "失败");
        if (StringUtils.hasText(targetId)) {
            value.append("，目标ID=").append(targetId);
        }
        return value.toString();
    }

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

    private record ResultAssessment(AuditResult result, Throwable error) {
    }

    private static final class ReturnedFailureException extends RuntimeException {
        private ReturnedFailureException(String message) {
            super(message);
        }
    }
}
