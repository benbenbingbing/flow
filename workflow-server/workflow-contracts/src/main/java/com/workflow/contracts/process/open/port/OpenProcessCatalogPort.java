package com.workflow.contracts.process.open.port;

import com.workflow.contracts.process.open.OpenApplicationActor;
import com.workflow.contracts.process.open.OpenProcessDefinition;
import java.util.Collection;
import java.util.List;

/**
 * 按外部应用身份查询已发布开放流程的端口。
 */
public interface OpenProcessCatalogPort {

    /**
     * 查询指定流程键中允许当前应用访问的已发布流程定义。
     *
     * @param processKeys 流程定义键集合
     * @param actor 发起请求的外部应用身份
     * @return 可访问的已发布流程定义
     */
    List<OpenProcessDefinition> listPublished(
            Collection<String> processKeys,
            OpenApplicationActor actor);
}
