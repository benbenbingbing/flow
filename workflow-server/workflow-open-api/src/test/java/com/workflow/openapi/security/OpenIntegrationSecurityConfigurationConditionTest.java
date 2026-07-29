package com.workflow.openapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenIntegrationSecurityConfigurationConditionTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(
                            OpenIntegrationSecurityConfiguration.class)
                    .withPropertyValues(
                            "workflow.open-api.enabled=true");

    @Test
    void nonWebBootstrapDoesNotCreateServletSecurityChains() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context)
                    .hasSingleBean(OpenIntegrationProperties.class);
            assertThat(context)
                    .doesNotHaveBean("existingApplicationSecurity");
            assertThat(context)
                    .doesNotHaveBean("authorizationServerSecurity");
        });
    }
}
