package com.workflow.contracts.process.assignment.spi;

import com.workflow.contracts.identity.resolver.PersonResolverConfigurationValidationRequest;

/**
 * 流程人员解析器的发布时静态配置校验 SPI。
 */
public interface PersonResolverConfigurationValidator {

    /**
     * 返回与解析器描述一致的稳定解析器编码。
     *
     * @return 解析器编码
     */
    String resolverCode();

    /**
     * 在 BPMN 发布边界校验配置。
     *
     * @param request 配置校验请求
     * @throws IllegalArgumentException 配置无法向设计者解释或不满足约束时抛出
     */
    void validate(PersonResolverConfigurationValidationRequest request);
}
