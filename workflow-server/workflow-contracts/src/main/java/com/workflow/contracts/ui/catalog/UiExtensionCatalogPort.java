package com.workflow.contracts.ui.catalog;

import java.util.List;

/**
 * 向管理模块提供 UI 扩展目录的只读端口。
 */
public interface UiExtensionCatalogPort {

    List<UiExtensionCatalogItem> listCatalogItems();
}
