package com.workflow.biz.project.embed.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class EmbedDemoBoundaryTest {
    @Test
    void registersNoDemoBeansWithoutExplicitProfile() {
        // 默认加载不能读取示例密钥、建立机器连接或替换产品认证配置。
        new ApplicationContextRunner().withUserConfiguration(EmbedDemoConfiguration.class,
                EmbedDemoSecurity.class, FlowHttpClient.class, FlowMachineTokenService.class,
                PartnerAssertionService.class, FlowEmbedService.class,
                FlowEmbedController.class, EmbedDemoExceptionHandler.class).run(context -> {
                    assertThat(context).hasNotFailed().doesNotHaveBean(EmbedDemoProperties.class)
                            .doesNotHaveBean(FlowEmbedController.class)
                            .doesNotHaveBean(EmbedDemoSecurity.class)
                            .doesNotHaveBean(FlowHttpClient.class);
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://embed.flow.example.com", "https://embed.flow.example.com/",
            "https://embed.flow.example.com/path", "https://user@embed.flow.example.com",
            "https://embed.flow.example.com?x=1", "https://embed.flow.example.com#x"})
    void requiresExactHttpsEmbedOrigin(String embedOrigin) {
        assertThatThrownBy(() -> config("http://127.0.0.1:8080", embedOrigin))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonLoopbackHttpApiAndSharedHostOrigin() {
        assertThatThrownBy(() -> config("http://api.flow.example.com", "https://embed.flow.example.com"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config("https://api.flow.example.com", "https://portal.partner.example"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private EmbedDemoProperties.Flow config(String apiBase, String embedOrigin) {
        return new EmbedDemoProperties.Flow(URI.create(apiBase), "client", "secret", URI.create(embedOrigin),
                URI.create("https://portal.partner.example"), "demo-view", "LIST", Set.of("LIST"),
                "https://id.partner.example", "test-key", Path.of("unused.pem"));
    }
}
