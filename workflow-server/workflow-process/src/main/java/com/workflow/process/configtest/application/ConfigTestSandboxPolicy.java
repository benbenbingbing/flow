package com.workflow.process.configtest.application;

import com.workflow.process.configtest.application.ConfigTestModels.TestCase;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 配置测试沙箱边界。
 *
 * <p>所有测试默认使用 MOCK 适配器和冻结时间，显式请求真实外部调用、生产环境写入
 * 或 LIVE 适配器时直接阻断。该策略独立于数据库事务，避免把事务回滚误当成副作用隔离。</p>
 */
@Component
public class ConfigTestSandboxPolicy {

    private static final Set<String> SAFE_ADAPTERS = Set.of("MOCK", "STUB", "ISOLATED");
    private static final Instant DEFAULT_FROZEN_TIME = Instant.parse("2020-01-01T00:00:00Z");

    /** 校验单条用例的隔离环境并返回规范化上下文。 */
    public SandboxContext requireSafe(TestCase testCase) {
        Map<String, Object> input = testCase == null || testCase.input() == null
                ? Map.of() : testCase.input();
        Map<String, Object> environment = map(input.get("environment"));
        String adapterMode = upper(first(environment.get("adapterMode"), input.get("adapterMode"), "MOCK"));
        if (!SAFE_ADAPTERS.contains(adapterMode)) {
            throw new UnsafeTestExecutionException("配置测试禁止使用真实或未知适配器: " + adapterMode);
        }
        String environmentName = upper(first(environment.get("name"), input.get("environmentName"), "TEST"));
        if (Set.of("PROD", "PRODUCTION", "ONLINE").contains(environmentName)) {
            throw new UnsafeTestExecutionException("配置测试禁止绑定生产环境");
        }
        if (truthy(environment.get("allowExternalSideEffects"))
                || truthy(input.get("allowExternalSideEffects"))
                || truthy(environment.get("allowBusinessWrites"))
                || truthy(input.get("allowBusinessWrites"))) {
            throw new UnsafeTestExecutionException("配置测试禁止真实外部副作用和生产业务写入");
        }
        Instant frozenAt = instant(first(environment.get("frozenAt"), input.get("frozenAt"), null));
        String identity = text(first(environment.get("identity"), input.get("identity"), "anonymous-test-user"));
        List<String> organizations = strings(first(
                environment.get("organizations"), input.get("organizations"), List.of()));
        return new SandboxContext(
                adapterMode,
                environmentName,
                identity,
                organizations,
                frozenAt == null ? DEFAULT_FROZEN_TIME : frozenAt,
                true);
    }

    private Object first(Object first, Object second, Object fallback) {
        return first != null ? first : second != null ? second : fallback;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
    }

    private List<String> strings(Object value) {
        if (!(value instanceof Collection<?> collection)) {
            return StringUtils.hasText(text(value)) ? List.of(text(value)) : List.of();
        }
        return collection.stream().map(this::text).filter(StringUtils::hasText).distinct().toList();
    }

    private Instant instant(Object value) {
        if (!StringUtils.hasText(text(value))) return null;
        try {
            return Instant.parse(text(value));
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException("冻结时间必须使用 ISO-8601 Instant 格式", exception);
        }
    }

    private String upper(Object value) {
        return text(value).trim().toUpperCase(Locale.ROOT);
    }

    private String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private boolean truthy(Object value) {
        return Boolean.TRUE.equals(value) || "true".equalsIgnoreCase(String.valueOf(value));
    }

    public record SandboxContext(
            String adapterMode,
            String environmentName,
            String identity,
            List<String> organizations,
            Instant frozenAt,
            boolean sideEffectsBlocked) {
    }

    /** 可转换为稳定测试结果码的沙箱阻断异常。 */
    public static class UnsafeTestExecutionException extends IllegalArgumentException {
        public UnsafeTestExecutionException(String message) {
            super(message);
        }
    }
}
