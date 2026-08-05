package com.workflow.contracts.ui.catalog;

import java.util.Map;
import java.util.Set;

/**
 * UI 扩展目录的跨模块只读视图。
 *
 * <p>该对象只暴露扩展管理需要的稳定元数据，不泄露实体模块的数据库记录。</p>
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
        Integer revision) {
}
