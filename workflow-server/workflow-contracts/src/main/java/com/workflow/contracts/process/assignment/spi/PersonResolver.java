package com.workflow.contracts.process.assignment.spi;

import com.workflow.contracts.process.assignment.model.PersonResolveRequest;
import com.workflow.contracts.process.assignment.model.PersonResolveResult;
import com.workflow.contracts.process.assignment.model.PersonResolverDescriptor;
import com.workflow.contracts.extension.ExtensionImplementationOrigin;

/**
 * 流程人员解析器 SPI。
 */
public interface PersonResolver {

    /**
     * 返回扩展实现归属。
     *
     * <p>SPI 默认视为项目自定义；平台内置解析器必须显式覆盖，避免按包名猜测。</p>
     *
     * @return 处理后的实现来源结果，供调用方继续处理
     */
    default ExtensionImplementationOrigin implementationOrigin() {
        return ExtensionImplementationOrigin.CUSTOM;
    }

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
