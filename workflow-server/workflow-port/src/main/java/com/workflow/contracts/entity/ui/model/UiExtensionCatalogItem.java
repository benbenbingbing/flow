package com.workflow.contracts.entity.ui.model;

import java.util.Map;
import java.util.Set;

/**
 * UI 扩展目录的跨模块只读视图。
 *
 * <p>该对象只暴露扩展管理需要的稳定元数据，不泄露实体模块的数据库记录。</p>
 *
 * @param id 对象标识，供后续引用、更新或关联
 * @param extensionType 扩展类型标识，决定后续界面扩展目录条目采用的处理分支
 * @param extensionKey 扩展键，后续用于授权校验、关联或幂等去重
 * @param displayName 用户可见名称，供界面和日志展示
 * @param version 版本，保存在对象中供后续校验、查询或展示
 * @param snapshotVersion 快照版本，保存在对象中供后续校验、查询或展示
 * @param status 状态标识，决定后续界面扩展目录条目采用的处理分支
 * @param visibilityScope {@code visibility}作用域，保存在对象中供后续校验、查询或展示
 * @param entityCodes 实体编码集合，保存在对象中供后续校验、查询或展示
 * @param supportedModes {@code supported}模式集合，保存在对象中供后续校验、查询或展示
 * @param supportedNodeTypes {@code supported}节点类型集合，保存在对象中供后续校验、查询或展示
 * @param supportedBindings {@code supported}绑定集合，保存在对象中供后续校验、查询或展示
 * @param configSchema 配置结构，保存在对象中供后续校验、查询或展示
 * @param capabilities 能力集合，保存在对象中供后续校验、查询或展示
 * @param implementationType 实现类型标识，决定后续界面扩展目录条目采用的处理分支
 * @param providerCode 提供者编码，后续用于处理界面扩展目录条目时定位或关联目标
 * @param scopeType 作用域类型标识，决定后续界面扩展目录条目采用的处理分支
 * @param scopeId 作用域ID，后续用于处理界面扩展目录条目时定位或关联目标
 * @param interfaceKind 接口类型，保存在对象中供后续校验、查询或展示
 * @param interfaceContextType 接口上下文类型标识，决定后续界面扩展目录条目采用的处理分支
 * @param implementationConfig 实现配置内容，决定后续界面扩展目录条目的处理规则
 * @param executionPolicy 执行策略，保存在对象中供后续校验、查询或展示
 * @param inputSchema 输入结构，保存在对象中供后续校验、查询或展示
 * @param outputSchema 输出结构，保存在对象中供后续校验、查询或展示
 * @param revision 修订版本，保存在对象中供后续校验、查询或展示
 */
public record UiExtensionCatalogItem(
        String id,
        String extensionType,
        String extensionKey,
        String displayName,
        Integer version,
        Integer snapshotVersion,
        String status,
        String visibilityScope,
        Set<String> entityCodes,
        Set<String> supportedModes,
        Set<String> supportedNodeTypes,
        Set<String> supportedBindings,
        Object configSchema,
        Map<String, Object> capabilities,
        String implementationType,
        String providerCode,
        String scopeType,
        String scopeId,
        String interfaceKind,
        String interfaceContextType,
        Map<String, Object> implementationConfig,
        Map<String, Object> executionPolicy,
        Map<String, Object> inputSchema,
        Map<String, Object> outputSchema,
        Integer revision) {
}
