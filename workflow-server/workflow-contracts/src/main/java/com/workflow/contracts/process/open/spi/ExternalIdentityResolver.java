package com.workflow.contracts.process.open.spi;

import com.workflow.contracts.identity.external.ExternalIdentityResolutionRequest;
import java.util.Optional;

/**
 * 将外部主体解析为规范流程用户名的 SPI。
 *
 * <p>实现必须使用精确且按命名空间隔离的映射。</p>
 */
public interface ExternalIdentityResolver {

    /**
     * 判断解析器是否支持给定的外部身份命名空间。
     *
     * @param namespace 外部身份命名空间
     * @return 支持时返回 true
     */
    boolean supports(String namespace);

    /**
     * 将外部主体解析为规范流程用户名。
     *
     * @param request 外部身份解析请求
     * @return 解析成功时的用户名
     */
    Optional<String> resolve(ExternalIdentityResolutionRequest request);
}
