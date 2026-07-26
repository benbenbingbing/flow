package com.workflow.system.audit.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
        Object beforeData = audit.captureArguments()
                ? snapshot(argumentMap(joinPoint, arguments))
                : null;
        try {
            Object result = joinPoint.proceed();
            ResultAssessment assessment = assessResult(result);
            auditPort.record(buildEvent(
                    joinPoint, audit, eventId, assessment.result(), arguments,
                    beforeData, result,
                    assessment.error(), startedAt));
            return result;
        } catch (Throwable throwable) {
            try {
                auditPort.record(buildEvent(
                        joinPoint, audit, eventId, AuditResult.FAILURE,
                        arguments, beforeData, null, throwable, startedAt));
            } catch (RuntimeException auditException) {
                log.error("记录失败操作审计时发生异常: operation={}", audit.operation(), auditException);
            }
            throw throwable;
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
            long startedAt) {
        Object target = targetValue(audit, arguments, returnValue);
        String targetId = extractProperty(target, "getId");
        if (!StringUtils.hasText(targetId) && audit.targetIdArg() >= 0
                && audit.targetIdArg() < arguments.length) {
            targetId = simpleValue(arguments[audit.targetIdArg()]);
        }
        String targetName = extractTargetName(target);
        long durationMs = (System.nanoTime() - startedAt) / 1_000_000;
        return SystemAuditEvent.builder()
                .eventId(eventId)
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
