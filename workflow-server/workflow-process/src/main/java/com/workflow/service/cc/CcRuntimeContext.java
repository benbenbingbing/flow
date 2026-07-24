package com.workflow.service.cc;

import java.util.Map;

/**
 * 知会运行时上下文。
 *
 * <p>记录触发知会的流程节点、时机与上下文变量，供知会人员解析与记录创建使用。
 * 作为不可变值对象传递给各解析器与渠道。</p>
 *
 * @param processInstanceId   流程实例ID
 * @param processDefinitionId 流程定义ID
 * @param processKey         流程标识
 * @param processName        流程名称
 * @param businessKey        业务Key
 * @param nodeId             节点ID
 * @param nodeName           节点名称
 * @param timing             知会时机（如 NODE_START、TASK_COMPLETE 等）
 * @param operatorId         操作人（当前办理人）
 * @param variables          流程变量快照
 */
public record CcRuntimeContext(
        String processInstanceId,
        String processDefinitionId,
        String processKey,
        String processName,
        String businessKey,
        String nodeId,
        String nodeName,
        String timing,
        String operatorId,
        Map<String, Object> variables) {
}
