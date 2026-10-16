package com.workflow.process.variable.application;

import com.workflow.contracts.process.error.ProcessOperationException;
import com.workflow.contracts.process.model.ProcessValues;
import com.workflow.process.instance.application.WorkflowReservedVariables;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/** 公共变量读写边界；引擎及任务编排内部写入继续由其权威业务操作维护。 */
public final class ProcessVariablePolicy {
    private ProcessVariablePolicy() {}

    /** 在任何写入开始前验证整个补丁，防止先写入部分值后才发现保留变量。 */
    public static void requireWritable(Collection<String> names, String targetId) {
        for (String name : names) {
            ProcessValues.required(name, "变量名");
            if (!WorkflowReservedVariables.isPublicBusinessVariable(name)) {
                throw new ProcessOperationException("VARIABLE_NOT_WRITABLE", "平台保留变量不可直接修改: " + name, targetId);
            }
        }
    }

    /** 移除内部编排字段并深拷贝；原始引擎对象及嵌套可变引用不会泄露给 biz。 */
    public static Map<String, Object> visibleSnapshot(Map<String, Object> values) {
        Map<String, Object> visible = new LinkedHashMap<>();
        if (values != null) values.forEach((name, value) -> {
            if (WorkflowReservedVariables.isPublicBusinessVariable(name)) {
                visible.put(name, value);
            }
        });
        return ProcessValues.snapshot(visible);
    }
}
