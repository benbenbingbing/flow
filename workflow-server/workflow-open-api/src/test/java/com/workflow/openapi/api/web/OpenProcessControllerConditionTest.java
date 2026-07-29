package com.workflow.openapi.api.web;

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenProcessControllerConditionTest {

    @Test
    void publicControllerIsAbsentWhenFeatureIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        OpenProcessController.class)
                .withPropertyValues(
                        "workflow.open-api.enabled=false")
                .run(context -> assertFalse(
                        context.containsBean(
                                "openProcessController")));
    }
}
