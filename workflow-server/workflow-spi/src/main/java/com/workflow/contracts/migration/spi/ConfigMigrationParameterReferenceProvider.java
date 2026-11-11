package com.workflow.contracts.migration.spi;

import java.util.Map;

/**
 * 扩展参数的跨环境引用契约。实现注册为 Spring Bean，只声明类型，不查询目录、不修改业务配置。
 * 平台负责将指定位置的本地 ID 转为编码、生成依赖，并在目标端恢复为目标 ID。
 */
public interface ConfigMigrationParameterReferenceProvider {
    /**
     * 声明某个参数文档内的引用位置；无法识别该扩展时返回空 Map。
     *
     * @param configuration 当前绑定的只读配置，可按 actionCode、resolverCode、extensionKey 等识别扩展
     * @param parameterName paramsJson、extraParams 或 implementationConfigDocument
     * @return 参数文档内的 JSON Pointer 到 USER/ROLE/GROUP/DEPT 的映射；VALUE 表示明确的普通业务值。
     *         空指针表示整个参数值；数组使用具体下标，可根据 configuration 中的参数生成声明。
     * @throws IllegalArgumentException 参数结构不符合扩展契约时抛出，消息应包含修改建议
     */
    Map<String, String> referenceTypes(Map<String, Object> configuration, String parameterName);
}
