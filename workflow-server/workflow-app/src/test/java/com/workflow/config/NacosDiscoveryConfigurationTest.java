package com.workflow.config;

import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceAutoConfiguration;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.cloud.nacos.discovery.NacosDiscoveryAutoConfiguration;
import com.alibaba.cloud.nacos.discovery.NacosDiscoveryClientConfiguration;
import com.alibaba.cloud.nacos.registry.NacosServiceRegistry;
import com.alibaba.cloud.nacos.registry.NacosServiceRegistryAutoConfiguration;
import com.alibaba.cloud.nacos.util.UtilIPv6AutoConfiguration;
import com.alibaba.nacos.api.naming.NamingService;
import com.alibaba.nacos.api.naming.pojo.Instance;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.web.server.WebServer;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.boot.web.servlet.context.ServletWebServerInitializedEvent;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.client.serviceregistry.AutoServiceRegistrationAutoConfiguration;
import org.springframework.cloud.commons.util.UtilAutoConfiguration;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 使用真实主配置与 Starter 自动配置验证可选注册，不连接真实注册中心或数据库。 */
class NacosDiscoveryConfigurationTest {

    /** 隔离开发机环境变量，并加载正式的 Config Data 导入链以防止配置遗漏。 */
    private ApplicationContextRunner context(Map<String, Object> variables) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        PropertyPlaceholderAutoConfiguration.class,
                        ConfigurationPropertiesAutoConfiguration.class,
                        UtilAutoConfiguration.class,
                        UtilIPv6AutoConfiguration.class,
                        NacosDiscoveryAutoConfiguration.class,
                        NacosDiscoveryClientConfiguration.class,
                        AutoServiceRegistrationAutoConfiguration.class,
                        NacosServiceRegistryAutoConfiguration.class))
                // Mock 注册中心没有在途流量，无需等待生产默认的注销传播窗口。
                .withPropertyValues("spring.config.location=file:src/main/resources/application.yml",
                        "spring.cloud.nacos.discovery.graceful-shutdown-wait-time=0")
                .withInitializer(context -> {
                    var sources = context.getEnvironment().getPropertySources();
                    sources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
                    sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                            new SystemEnvironmentPropertySource(
                                    StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
                    new ConfigDataApplicationContextInitializer().initialize(context);
                });
    }

    @Test
    void defaultStartupDoesNotCreateNacosClientsOrRegistry() {
        context(Map.of())
                .withConfiguration(AutoConfigurations.of(NacosServiceAutoConfiguration.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(NacosServiceManager.class);
                    assertThat(context).doesNotHaveBean(NacosServiceRegistry.class);
                    assertThat(context).doesNotHaveBean(DiscoveryClient.class);
                });
    }

    /** Web 端口就绪后才注册，且关闭上下文必须注销相同的业务实例，不能误用管理端口。 */
    @Test
    void optInRegistersActualWebPortAndDeregistersOnShutdown() throws Exception {
        var manager = mock(NacosServiceManager.class);
        var naming = mock(NamingService.class);
        when(manager.getNamingService()).thenReturn(naming);

        context(Map.of(
                "NACOS_DISCOVERY_ENABLED", "true",
                "NACOS_SERVER_ADDR", "nacos.example:8848",
                "NACOS_NAMESPACE", "test-namespace-id",
                "NACOS_GROUP", "FLOW_TEST",
                "NACOS_CLUSTER_NAME", "TEST_CLUSTER",
                "NACOS_USERNAME", "test-user",
                "NACOS_PASSWORD", "test-only-password",
                "NACOS_DISCOVERY_IP", "192.0.2.11"))
                .withBean(NacosServiceManager.class, () -> manager)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DiscoveryClient.class);
                    var properties = context.getBean(NacosDiscoveryProperties.class);
                    assertThat(properties.getServerAddr()).isEqualTo("nacos.example:8848");
                    assertThat(properties.getNamespace()).isEqualTo("test-namespace-id");
                    assertThat(properties.getUsername()).isEqualTo("test-user");
                    assertThat(properties.getPassword()).isEqualTo("test-only-password");
                    assertThat(properties.isFailFast()).isTrue();
                    assertThat(properties.getPort()).isEqualTo(-1);

                    context.publishEvent(webServerStarted(18081));
                    var instance = ArgumentCaptor.forClass(Instance.class);
                    verify(naming).registerInstance(eq("workflow-server"), eq("FLOW_TEST"), instance.capture());
                    assertThat(instance.getValue().getIp()).isEqualTo("192.0.2.11");
                    assertThat(instance.getValue().getPort()).isEqualTo(18081);
                    assertThat(instance.getValue().getClusterName()).isEqualTo("TEST_CLUSTER");
                });

        verify(naming).deregisterInstance("workflow-server", "FLOW_TEST", "192.0.2.11", 18081, "TEST_CLUSTER");
    }

    @Test
    void explicitAdvertisedServiceAndPortOverrideApplicationDefaults() {
        context(Map.of(
                "NACOS_DISCOVERY_ENABLED", "true",
                "NACOS_SERVICE_NAME", "flow-api",
                "NACOS_DISCOVERY_IP", "192.0.2.12",
                "NACOS_DISCOVERY_PORT", "28080"))
                .withBean(NacosServiceManager.class, () -> mock(NacosServiceManager.class))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(NacosDiscoveryProperties.class);
                    assertThat(properties.getService()).isEqualTo("flow-api");
                    assertThat(properties.getPort()).isEqualTo(28080);
                    assertThat(properties.getGroup()).isEqualTo("DEFAULT_GROUP");
                    assertThat(properties.getNamespace()).isEmpty();
                });
    }

    /** 订阅方可以发现其他服务，但收到 Web 启动事件后也不能注册自己的实例。 */
    @Test
    void discoveryOnlyModeDoesNotRegisterTheServer() {
        var manager = mock(NacosServiceManager.class);
        context(Map.of(
                "NACOS_DISCOVERY_ENABLED", "true",
                "NACOS_REGISTER_ENABLED", "false",
                "NACOS_DISCOVERY_IP", "192.0.2.13"))
                .withBean(NacosServiceManager.class, () -> manager)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(DiscoveryClient.class);
                    context.publishEvent(webServerStarted(18082));
                    verify(manager, never()).getNamingService();
                });
    }

    /** 模拟内嵌 Web 容器发布实际监听端口，触发 Starter 的注册生命周期。 */
    private ServletWebServerInitializedEvent webServerStarted(int port) {
        var server = mock(WebServer.class);
        when(server.getPort()).thenReturn(port);
        return new ServletWebServerInitializedEvent(server, mock(ServletWebServerApplicationContext.class));
    }
}
