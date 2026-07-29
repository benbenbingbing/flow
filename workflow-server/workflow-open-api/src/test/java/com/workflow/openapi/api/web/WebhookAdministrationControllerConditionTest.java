package com.workflow.openapi.api.web;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.workflow.core.security.RequiresPermission;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class WebhookAdministrationControllerConditionTest {

    @Test
    void administrationControllerIsAbsentWhenWebhookIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(
                        WebhookAdministrationController.class)
                .withPropertyValues(
                        "workflow.open-api.webhook.enabled=false")
                .run(context -> assertFalse(
                        context.containsBean(
                                "webhookAdministrationController")));
    }

    @Test
    void replayRequiresDedicatedPermission() throws Exception {
        RequiresPermission permission =
                WebhookAdministrationController.class
                        .getMethod(
                                "replay",
                                String.class,
                                String.class,
                                com.workflow.openapi.api.request
                                        .ReplayWebhookDeliveryRequest.class)
                        .getAnnotation(RequiresPermission.class);

        assertArrayEquals(
                new String[]{"system:integration:delivery-replay"},
                permission.value());
    }

    @Test
    void validationRequiresIntegrationManagementPermission()
            throws Exception {
        RequiresPermission permission =
                WebhookAdministrationController.class
                        .getMethod(
                                "validate",
                                String.class,
                                String.class)
                        .getAnnotation(RequiresPermission.class);

        assertArrayEquals(
                new String[]{"system:integration:manage"},
                permission.value());
    }
}
