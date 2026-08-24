package com.workflow.process.assignment.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.process.assignment.domain.EmptyAssigneePolicy;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** 空办理人策略合并与边界校验测试。 */
class EmptyAssigneePolicyResolverTest {

    private final EmptyAssigneePolicyResolver resolver =
            new EmptyAssigneePolicyResolver(new ObjectMapper());

    @Test
    void supportsAllFiveStrategies() {
        for (EmptyAssigneePolicy.Strategy strategy
                : EmptyAssigneePolicy.Strategy.values()) {
            Map<String, Object> nodePolicy = Map.of(
                    "policy", strategy.name(),
                    "fallbackUser", "backup-user",
                    "fallbackGroup", "backup-group",
                    "maxRetries", 5,
                    "initialDelaySeconds", 30,
                    "backoffMultiplier", 2,
                    "responsibilityOwner", "workflow-ops");

            EmptyAssigneePolicy resolved = resolver.resolve((String) null,
                    Map.of(EmptyAssigneePolicyResolver.NODE_CONFIG_KEY,
                            nodePolicy));

            assertEquals(strategy, resolved.strategy());
        }
    }

    @Test
    void inheritUsesCompleteProcessSnapshot() {
        String processDefault = """
                {"policy":"WAIT_AND_RETRY","maxRetries":8,
                 "initialDelaySeconds":120,"backoffMultiplier":3.5,
                 "responsibilityOwner":"central-ops"}
                """;
        Map<String, Object> editorDefaults = Map.of(
                "policy", "INHERIT",
                "fallbackUser", "",
                "fallbackGroup", "",
                "maxRetries", 3,
                "initialDelaySeconds", 60,
                "backoffMultiplier", 2,
                "responsibilityOwner", "");

        EmptyAssigneePolicy resolved = resolver.resolve(
                processDefault,
                Map.of(EmptyAssigneePolicyResolver.NODE_CONFIG_KEY,
                        editorDefaults));

        assertEquals(EmptyAssigneePolicy.Strategy.WAIT_AND_RETRY,
                resolved.strategy());
        assertEquals(8, resolved.maxRetries());
        assertEquals(120, resolved.initialDelaySeconds());
        assertEquals(3.5, resolved.backoffMultiplier());
        assertEquals("central-ops", resolved.responsibilityOwner());
    }

    @Test
    void explicitNodePolicyOverridesProcessDefault() {
        EmptyAssigneePolicy resolved = resolver.resolve(
                "{\"policy\":\"BLOCK_PUBLISH\","
                        + "\"responsibilityOwner\":\"central-ops\"}",
                Map.of(EmptyAssigneePolicyResolver.NODE_CONFIG_KEY,
                        Map.of("policy", "FALLBACK_USER",
                                "fallbackUser", "node-owner",
                                "responsibilityOwner", "node-ops")));

        assertEquals(EmptyAssigneePolicy.Strategy.FALLBACK_USER,
                resolved.strategy());
        assertEquals("node-owner", resolved.fallbackUser());
        assertEquals("node-ops", resolved.responsibilityOwner());
    }

    @Test
    void rejectsInvalidFallbackAndRetryConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve((String) null,
                Map.of(EmptyAssigneePolicyResolver.NODE_CONFIG_KEY,
                        Map.of("policy", "FALLBACK_USER",
                                "responsibilityOwner", "ops"))));
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve((String) null,
                Map.of(EmptyAssigneePolicyResolver.NODE_CONFIG_KEY,
                        Map.of("policy", "WAIT_AND_RETRY",
                                "maxRetries", 21,
                                "responsibilityOwner", "ops"))));
    }
}
