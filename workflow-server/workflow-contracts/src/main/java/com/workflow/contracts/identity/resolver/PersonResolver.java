package com.workflow.contracts.identity.resolver;

/**
 * 统一人员解析器 SPI。
 */
public interface PersonResolver {

    PersonResolverDescriptor descriptor();

    PersonResolveResult resolve(PersonResolveRequest request);
}
