package com.workflow.biz.project.custom;

import com.workflow.contracts.entity.ui.model.UiExtensionCatalogItem;
import com.workflow.contracts.entity.ui.port.UiExtensionCatalogPort;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * UI 扩展目录读取端口的替换适配器示例。
 *
 * <p>平台已有数据库目录实现，因此该类不注册为 Spring Bean。当前返回空目录并
 * 记录日志，适合验证自定义目录适配器的调用位置。</p>
 */
@Slf4j
public class ProjectCustomUiExtensionCatalogAdapter
        implements UiExtensionCatalogPort {

    /**
     * 列出目录条目；查询结果供调用方展示或继续处理。
     *
     * @return 界面扩展目录条目集合，供调用方遍历或展示
     */
    @Override
    public List<UiExtensionCatalogItem>
            listCatalogItems() {
        log.info("项目 UI 扩展目录端口执行: resultCount=0");
        return List.of();
    }
}
