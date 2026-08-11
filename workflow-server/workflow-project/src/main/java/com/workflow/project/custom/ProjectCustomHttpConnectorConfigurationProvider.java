package com.workflow.project.custom;

import com.workflow.core.logging.LogValue;
import com.workflow.http.HttpConnectorConfiguration;
import com.workflow.http.HttpConnectorConfigurationProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * HTTP Connector 配置读取器替换示例。
 *
 * <p>平台默认从数据库读取已启用的 HTTP Connector 配置，因此该类不注册为
 * Spring Bean。真实项目只有在配置来自配置中心或外部服务时才应显式替换默认
 * 实现，并负责完整返回基础地址、允许访问的主机、鉴权方式和操作映射。</p>
 *
 * <p>示例仅打印配置 ID 后拒绝返回伪配置，避免绕过平台的主机白名单和密钥
 * 管理机制。</p>
 */
@Slf4j
public class ProjectCustomHttpConnectorConfigurationProvider
        implements HttpConnectorConfigurationProvider {

    @Override
    public HttpConnectorConfiguration findActive(
            String configurationId) {
        log.info(
                "项目 HTTP Connector 配置读取器被调用: configurationId={}",
                LogValue.safe(configurationId));
        throw new UnsupportedOperationException(
                "项目 HTTP Connector 配置读取示例未接入真实配置中心");
    }
}
