package com.workflow.contracts.process.assignment.spi;

import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveResult;
import com.workflow.contracts.identity.resolver.PersonResolverDescriptor;

/**
 * 流程人员解析器 SPI。
 */
public interface PersonResolver {

    /**
     * 返回解析器的稳定描述信息。
     *
     * @return 解析器描述
     */
    PersonResolverDescriptor descriptor();

    /**
     * 按流程人员解析请求计算人员结果。
     *
     * @param request 人员解析请求
     * @return 人员解析结果
     */
    PersonResolveResult resolve(PersonResolveRequest request);
}
