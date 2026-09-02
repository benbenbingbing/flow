package com.workflow.process.instance.application;

import com.workflow.process.assignment.relative.InitiatorOrganizationSnapshotService;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 流程发起入口共用的平台保留变量边界。
 */
public final class WorkflowReservedVariables {

    /**
     * 平台内部变量统一命名空间。调用方不允许创建、覆盖或读取任何
     * 此前缀键，避免新增安全上下文时遗漏逐项黑名单。
     */
    public static final String INTERNAL_PREFIX = "_wf";

    private WorkflowReservedVariables() {
    }

    public static final Set<String> TRUSTED_START_VARIABLES = Set.of(
            "startUserId",
            "initiator",
            "submitterId",
            "submitterName",
            "entityCode",
            "entityDataId",
            "dataNo",
            "skipNodeEnabled",
            InitiatorOrganizationSnapshotService.VARIABLE_NAME);

    private static final Set<String> ENGINE_CONTEXT_VARIABLES = Set.of(
            "businessKey",
            "traceId",
            "nrOfInstances",
            "nrOfActiveInstances",
            "nrOfCompletedInstances",
            "loopCounter");

    /**
     * 复制调用方变量并移除平台保留键，避免修改请求对象自身。
     */
    public static Map<String, Object> sanitize(
            Map<String, Object> source) {
        Map<String, Object> result = source == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(source);
        TRUSTED_START_VARIABLES.forEach(result::remove);
        result.keySet().removeIf(
                WorkflowReservedVariables::isInternalVariable);
        return result;
    }

    /** 删除不得通过任务、接收任务或表单提交改写的平台保留键。 */
    public static Map<String, Object> sanitizeRuntimeMutation(
            Map<String, Object> source) {
        return sanitize(source);
    }

    /** 在对外 DTO 投影前原地移除内部安全上下文。 */
    public static void removeInternalVariables(
            Map<String, Object> variables) {
        if (variables != null) {
            variables.keySet().removeIf(
                    WorkflowReservedVariables::isInternalVariable);
        }
    }

    /** 判断变量名是否属于平台内部命名空间。 */
    public static boolean isInternalVariable(String variableName) {
        return variableName != null
                && variableName.startsWith(INTERNAL_PREFIX);
    }

    /**
     * 判断流程模型是否不得把变量名复用为多实例 collection 等业务输出。
     */
    public static boolean isProtectedContextVariable(String variableName) {
        return variableName != null
                && (TRUSTED_START_VARIABLES.contains(variableName)
                || ENGINE_CONTEXT_VARIABLES.contains(variableName)
                || isInternalVariable(variableName));
    }
}
