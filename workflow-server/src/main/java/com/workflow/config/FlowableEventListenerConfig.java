package com.workflow.config;

import com.workflow.listener.EntityStatusUpdateListener;
import com.workflow.listener.MultiInstanceCollectionListener;
import com.workflow.listener.ProcessEndListener;
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
    private final ProcessEndListener processEndListener;
    private final MultiInstanceCollectionListener multiInstanceCollectionListener;
    
    @PostConstruct
    public void init() {
        // 注册任务完成事件监听器（不指定事件类型，监听所有事件，在监听器中过滤）
        runtimeService.addEventListener(entityStatusUpdateListener);
        
        // 注册流程结束事件监听器
        runtimeService.addEventListener(processEndListener);
        
        // 注册多实例集合变量自动准备监听器
        runtimeService.addEventListener(multiInstanceCollectionListener);
    }
}
