package com.workflow.config;

import com.workflow.listener.EntityStatusUpdateListener;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.RuntimeService;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * Flowable 事件监听器配置
 */
@Configuration
@RequiredArgsConstructor
public class FlowableEventListenerConfig {
    
    private final RuntimeService runtimeService;
    private final EntityStatusUpdateListener entityStatusUpdateListener;
    
    @PostConstruct
    public void init() {
        // 注册任务完成事件监听器（不指定事件类型，监听所有事件，在监听器中过滤）
        runtimeService.addEventListener(entityStatusUpdateListener);
        
        // 也可以注册流程完成事件
        // runtimeService.addEventListener(entityStatusUpdateListener);
    }
}
