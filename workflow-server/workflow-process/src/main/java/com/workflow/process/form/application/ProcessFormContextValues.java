package com.workflow.process.form.application;

import com.workflow.process.definition.infrastructure.persistence.record.ProcessVersionHistory;
import com.workflow.process.form.infrastructure.persistence.record.ProcessNodeForm;
import org.flowable.task.api.Task;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/** 将流程入口已经校验的快照和任务转换为表单变量，和业务定位坐标分开转交。 */
public final class ProcessFormContextValues {
    private ProcessFormContextValues() { }

    public static Map<String, String> start(ProcessVersionHistory history, ProcessNodeForm binding) {
        Map<String, String> values = new LinkedHashMap<>();
        if (history != null) {
            put(values, "processDefinition.key", history.getProcessKey());
            put(values, "processDefinition.name", history.getProcessName());
        }
        if (binding != null) {
            put(values, "currentProcessNode.key", binding.getNodeId());
            put(values, "currentProcessNode.name", binding.getNodeName());
        }
        return values;
    }

    /** 本地加签使用独立的待办 ID 和办理人，节点则沿用其源任务的发布绑定。 */
    public static Map<String, String> task(ProcessVersionHistory history, ProcessNodeForm binding, Task task,
            String taskId, String assignee, String starter) {
        Map<String, String> values = start(history, binding);
        put(values, "currentProcessInstance.id", task.getProcessInstanceId());
        put(values, "currentProcessInstance.starterUserId", starter);
        put(values, "currentTask.id", taskId);
        put(values, "currentTask.nodeKey", task.getTaskDefinitionKey());
        put(values, "currentTask.nodeName", task.getName());
        put(values, "currentTask.assigneeUserId", assignee);
        return values;
    }

    private static void put(Map<String, String> values, String key, String value) {
        if (StringUtils.hasText(value)) values.put(key, value);
    }
}
