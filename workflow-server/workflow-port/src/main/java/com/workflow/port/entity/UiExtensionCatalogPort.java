package com.workflow.port.entity;

import com.workflow.port.entity.ui.model.UiExtensionCatalogItem;

import java.util.List;

/** 向管理模块提供实体 UI 扩展目录的只读端口。 */
public interface UiExtensionCatalogPort {

    /**
     * @return 当前可用的 UI 扩展目录项
     *
     * @return 界面扩展目录条目集合，供调用方遍历或展示
     */
    List<UiExtensionCatalogItem> listCatalogItems();
}
