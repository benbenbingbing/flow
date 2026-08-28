package com.workflow.contracts.identity.resolver;

/**
 * 人员解析器的发布时静态配置校验扩展点。
 */
public interface PersonResolverConfigurationValidator {

    /** 与 {@link PersonResolverDescriptor#code()} 一致的稳定解析器编码。 */
    String resolverCode();

    /**
     * 在 BPMN 发布边界校验配置；校验失败应抛出可向设计者解释的
     * {@link IllegalArgumentException}。
     */
    void validate(PersonResolverConfigurationValidationRequest request);
}
