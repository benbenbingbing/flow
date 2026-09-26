package com.workflow.process.action.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.port.EntityCodeCatalogPort;
import com.workflow.contracts.process.action.context.FlowActionFailureContext;
import com.workflow.contracts.process.action.model.*;
import com.workflow.contracts.process.action.spi.*;
import com.workflow.process.action.infrastructure.persistence.record.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationContext;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 直接验证策略契约与约束；异常分类属于实现的可选逻辑，不改变动作失败的触发机制。 */
class FlowActionFailureStrategyTest {
    private final ObjectMapper json = new ObjectMapper();
    private final EntityCodeCatalogPort entities = mock(EntityCodeCatalogPort.class);
    private final ApplicationContext beans = mock(ApplicationContext.class);

    @Test void rejectsDuplicateIdentityAndInvalidParameters() {
        var provider = provider(FailureDecision.retry(60, "稍后重试"));
        assertThrows(IllegalArgumentException.class, () -> catalog(provider, provider));
        var catalog = catalog(provider);
        var action = action();
        assertEquals(60, catalog.configuration(action).get("delay"));
        assertThrows(UnsupportedOperationException.class, () -> catalog.configuration(action).put("delay", 2));
        for (String raw : List.of("[]", "null", "{\"delay\":\"60\"}", "{\"delay\":1.5}", "{\"delay\":0}", "{\"unknown\":1}")) {
            action.setFailureStrategyConfig(raw);
            assertThrows(IllegalArgumentException.class, () -> catalog.configuration(action), raw);
        }
    }

    @Test void validatesModeVisibilityAndRetryCapabilityBeforePublication() {
        var catalog = catalog(provider(FailureDecision.retry(60, "重试")));
        var action = action();
        var handler = mock(FlowActionProvider.class);
        when(beans.getBean("handler", FlowActionProvider.class)).thenReturn(handler);
        assertThrows(IllegalArgumentException.class, () -> catalog.validate(action));
        when(handler.retryable()).thenReturn(true);
        assertDoesNotThrow(() -> catalog.validate(action));
        action.setExecutionMode("IN_TRANSACTION");
        assertThrows(IllegalArgumentException.class, () -> catalog.validate(action));
        action.setFailureStrategyVersion("missing");
        assertThrows(IllegalArgumentException.class, () -> catalog.descriptor(action));
    }

    @Test void retryBudgetMeansAdditionalAttemptsAndCannotOverrideIdempotency() {
        var coordinator = new FlowActionFailureCoordinator(catalog(provider(FailureDecision.retry(60, "重试"))));
        for (int max : List.of(0, 1, 3)) {
            var execution = execution(max);
            for (int attempt = 1; attempt <= max + 1; attempt++) {
                execution.setAttemptNo(attempt);
                var decision = coordinator.decide(action(), execution, new RuntimeException("业务失败"), true).effective();
                assertEquals(attempt <= max ? FailureDisposition.RETRY : FailureDisposition.MANUAL, decision.disposition());
            }
        }
        assertEquals("HANDLER_NOT_RETRYABLE", coordinator.decide(action(), execution(3), new RuntimeException(), false).effective().reasonCode());
    }

    @Test void invalidDecisionAndProviderErrorPreserveConservativeFallback() {
        for (FailureDecision bad : Arrays.asList(null, FailureDecision.retry(0, "无效间隔"),
                FailureDecision.retry(21601, "超长间隔"), FailureDecision.of(FailureDisposition.ROLLBACK, "BAD", "提交后不能回滚"))) {
            var result = new FlowActionFailureCoordinator(catalog(provider(bad)))
                    .decide(action(), execution(3), new IllegalArgumentException("原始业务失败"), true);
            assertEquals("STRATEGY_ERROR", result.effective().reasonCode());
            assertNotNull(result.strategyError());
        }
        var throwing = new FlowActionFailureStrategyProvider() {
            public FailureStrategyDescriptor descriptor() { return provider(null).descriptor(); }
            public FailureDecision decide(FlowActionFailureContext context, Map<String, Object> config) { throw new IllegalStateException("策略损坏"); }
        };
        assertEquals("STRATEGY_ERROR", new FlowActionFailureCoordinator(catalog(throwing))
                .decide(action(), execution(3), new RuntimeException("原始失败"), true).effective().reasonCode());
    }

    private FlowActionFailureStrategyCatalog catalog(FlowActionFailureStrategyProvider... providers) {
        return new FlowActionFailureStrategyCatalog(List.of(providers), json, entities, beans);
    }

    private FlowActionFailureStrategyProvider provider(FailureDecision result) {
        return new FlowActionFailureStrategyProvider() {
            public FailureStrategyDescriptor descriptor() {
                return new FailureStrategyDescriptor("TEST", "1", "测试策略", "",
                        Set.of(FlowActionExecutionMode.AFTER_COMMIT), Set.of(FailureDisposition.RETRY, FailureDisposition.MANUAL),
                        Set.of(), List.of(new FailureStrategyParameter("delay", "间隔", "number", true, 60, 1L, 21600L, List.of(), "")));
            }
            public FailureDecision decide(FlowActionFailureContext context, Map<String, Object> config) { return result; }
        };
    }

    private FlowAction action() {
        var action = new FlowAction(); action.setFailurePolicy("CUSTOM"); action.setExecutionMode("AFTER_COMMIT");
        action.setFailureStrategyCode("TEST"); action.setFailureStrategyVersion("1"); action.setInterfaceName("handler");
        return action;
    }

    private FlowActionExecution execution(int max) {
        var execution = new FlowActionExecution(); execution.setAttemptNo(1); execution.setMaxRetries(max); return execution;
    }
}
