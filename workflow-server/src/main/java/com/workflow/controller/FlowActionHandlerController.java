package com.workflow.controller;

import com.workflow.common.Result;
import com.workflow.process.action.FlowActionHandler;
import com.workflow.process.action.TypedFlowActionHandler;
import lombok.Data;
import org.springframework.context.ApplicationContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 流程动作处理器查询接口。
 */
@RestController
@RequestMapping("/api/flow-action-handlers")
public class FlowActionHandlerController {

    private final ApplicationContext applicationContext;

    public FlowActionHandlerController(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 获取所有已注册的 FlowActionHandler Bean。
     */
    @GetMapping
    public Result<List<FlowActionHandlerInfo>> listHandlers() {
        Map<String, FlowActionHandler> beans = applicationContext.getBeansOfType(FlowActionHandler.class);
        List<FlowActionHandlerInfo> result = new ArrayList<>();
        for (Map.Entry<String, FlowActionHandler> entry : beans.entrySet()) {
            FlowActionHandlerInfo info = new FlowActionHandlerInfo();
            info.setBeanName(entry.getKey());
            info.setClassName(entry.getValue().getClass().getName());
            info.setTyped(entry.getValue() instanceof TypedFlowActionHandler);
            if (entry.getValue() instanceof TypedFlowActionHandler<?> typed) {
                info.setParamType(typed.getParamType().getName());
            }
            info.setSupportedTriggerTimings(entry.getValue().supportedTriggerTimings());
            info.setSupportedExecutionModes(entry.getValue().supportedExecutionModes());
            info.setRecommendedExecutionMode(entry.getValue().recommendedExecutionMode());
            result.add(info);
        }
        return Result.success(result);
    }

    @Data
    public static class FlowActionHandlerInfo {
        private String beanName;
        private String className;
        private Boolean typed;
        private String paramType;
        private java.util.Set<String> supportedTriggerTimings;
        private java.util.Set<String> supportedExecutionModes;
        private String recommendedExecutionMode;
    }
}
