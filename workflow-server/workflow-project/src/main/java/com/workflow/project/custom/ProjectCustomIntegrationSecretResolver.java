package com.workflow.project.custom;

import com.workflow.contracts.integration.IntegrationSecretResolver;
import com.workflow.core.logging.LogValue;
import lombok.extern.slf4j.Slf4j;

/**
 * 集成密钥解析器替换示例。
 *
 * <p>当前应用已有数据库密钥解析器，因此该类不注册为 Spring Bean。
 * 示例只记录密钥别名并拒绝返回伪密钥；真实实现应对接 KMS 或密钥中心，
 * 且绝不能输出密钥值。</p>
 */
@Slf4j
public class ProjectCustomIntegrationSecretResolver
        implements IntegrationSecretResolver {

    @Override
    public String resolve(String secretAlias) {
        log.info(
                "项目集成密钥解析器被调用: secretAlias={}",
                LogValue.safe(secretAlias));
        throw new UnsupportedOperationException(
                "项目密钥解析示例未接入真实密钥服务");
    }
}
