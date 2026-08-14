package com.workflow.entity.version.application;

import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class EntityVersionConfigurationValidatorTest {

    private final EntityVersionConfigurationValidator validator =
            new EntityVersionConfigurationValidator(
                    mock(EntityDefinitionMapper.class));

    @Test
    void rejectsUnsupportedTriggerConditionBeforePublish() {
        EntityVersionConfiguration configuration = configuration();
        configuration.getTriggers().get(0).setCondition(Map.of(
                "field", "status",
                "source", "AFTER",
                "operator", "UNKNOWN"));

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(configuration));
    }

    @Test
    void acceptsNestedConditionSupportedByRuntimeMatcher() {
        EntityVersionConfiguration configuration = configuration();
        configuration.getTriggers().get(0).setCondition(Map.of(
                "all", List.of(
                        Map.of(
                                "field", "status",
                                "source", "AFTER",
                                "operator", "EQ",
                                "value", "APPROVED"),
                        Map.of(
                                "not", Map.of(
                                        "field", "deleted",
                                        "operator", "EQ",
                                        "value", true)))));

        assertDoesNotThrow(() -> validator.validate(configuration));
    }

    @Test
    void rejectsEmptyConditionGroup() {
        EntityVersionConfiguration configuration = configuration();
        configuration.getTriggers().get(0).setCondition(
                Map.of("any", List.of()));

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(configuration));
    }

    @Test
    void rejectsExcessivelyDeepConditionTree() {
        EntityVersionConfiguration configuration = configuration();
        Map<String, Object> condition = Map.of(
                "field", "status", "operator", "EQ", "value", "APPROVED");
        for (int index = 0; index < 18; index++) {
            Map<String, Object> parent = new LinkedHashMap<>();
            parent.put("not", condition);
            condition = parent;
        }
        configuration.getTriggers().get(0).setCondition(condition);

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(configuration));
    }

    @Test
    void validatesLegacyScenarioConditionBeforeRelease() {
        EntityVersionConfiguration configuration =
                new EntityVersionConfiguration();
        configuration.setSchemaVersion(1);
        EntityVersionConfiguration.Scenario scenario =
                new EntityVersionConfiguration.Scenario();
        scenario.setScenarioCode("LEGACY_UPDATE");
        scenario.setScenarioName("旧版更新");
        scenario.setCondition(Map.of(
                "field", "status",
                "operator", "UNSUPPORTED"));
        configuration.setScenarios(List.of(scenario));

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(configuration));
    }

    private EntityVersionConfiguration configuration() {
        EntityVersionConfiguration configuration =
                new EntityVersionConfiguration();
        EntityVersionConfiguration.CaptureTrigger trigger =
                new EntityVersionConfiguration.CaptureTrigger();
        trigger.setTriggerCode("ON_APPROVED");
        trigger.setTriggerName("审批通过");
        trigger.setTriggerType("ROOT_MUTATION");
        configuration.setTriggers(List.of(trigger));
        return configuration;
    }
}
