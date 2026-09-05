package com.workflow.contracts.entity.ui.port;

import com.workflow.contracts.ui.catalog.UiExtensionCatalogItem;

import java.util.List;

/** 向管理模块提供实体 UI 扩展目录的只读端口。 */
public interface UiExtensionCatalogPort {

    /** @return 当前可用的 UI 扩展目录项 */
    List<UiExtensionCatalogItem> listCatalogItems();
}
