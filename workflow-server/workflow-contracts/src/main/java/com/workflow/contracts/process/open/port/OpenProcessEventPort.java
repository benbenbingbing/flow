package com.workflow.contracts.process.open.port;

import com.workflow.contracts.process.open.OpenProcessEvent;

/**
 * 发布外部绑定流程实例生命周期事实的端口。
 */
public interface OpenProcessEventPort {

    /**
     * 发布一条已发生的流程生命周期事件。
     *
     * @param event 生命周期事实
     */
    void publish(OpenProcessEvent event);
}
