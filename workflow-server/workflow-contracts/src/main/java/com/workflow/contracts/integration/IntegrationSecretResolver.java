package com.workflow.contracts.integration;

/**
 * 集成密钥解析器。
 * 将密钥别名解析为实际密钥值，避免在配置中直接暴露密文。
 */
public interface IntegrationSecretResolver {

    /**
     * 根据密钥别名解析实际密钥值。
     *
     * @param secretAlias 密钥别名
     * @return 解析得到的密钥值
     */
    String resolve(String secretAlias);
}
