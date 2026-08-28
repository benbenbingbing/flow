package com.workflow.openapi.api.web;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.workflow.contracts.embed.EmbedLaunchIssuePort;
import com.workflow.openapi.security.OpenApplicationActorResolver;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmbedLaunchControllerConditionTest {

    @Test
    void launchControllerIsAbsentWhenOpenApiIsDisabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(EmbedLaunchController.class)
                .withBean(EmbedLaunchIssuePort.class,
                        () -> mock(EmbedLaunchIssuePort.class))
                .withBean(OpenApplicationActorResolver.class,
                        () -> mock(OpenApplicationActorResolver.class))
                .withPropertyValues(
                        "workflow.open-api.enabled=false",
                        "workflow.embed.enabled=true")
                .run(context -> assertFalse(
                        context.containsBean("embedLaunchController")));
    }

    @Test
    void launchControllerIsAbsentWhenEmbedIsDisabled() {
        // Open API 可以独立启用；Embed 未启用时不能因为缺少 Launch Port 阻止应用启动。
        new ApplicationContextRunner()
                .withUserConfiguration(EmbedLaunchController.class)
                .withPropertyValues(
                        "workflow.open-api.enabled=true",
                        "workflow.embed.enabled=false")
                .run(context -> {
                    assertTrue(context.isRunning());
                    assertFalse(context.containsBean("embedLaunchController"));
                });
    }

    @Test
    void launchControllerIsPresentOnlyWhenBothFeaturesAreEnabled() {
        new ApplicationContextRunner()
                .withUserConfiguration(EmbedLaunchController.class)
                .withBean(EmbedLaunchIssuePort.class,
                        () -> mock(EmbedLaunchIssuePort.class))
                .withBean(OpenApplicationActorResolver.class,
                        () -> mock(OpenApplicationActorResolver.class))
                .withPropertyValues(
                        "workflow.open-api.enabled=true",
                        "workflow.embed.enabled=true")
                .run(context -> assertTrue(
                        context.containsBean("embedLaunchController")));
    }
}
