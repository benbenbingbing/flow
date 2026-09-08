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

    /** Flowable 原生 skipExpression 的启用开关。 */
    public static final String FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE =
            "_FLOWABLE_SKIP_EXPRESSION_ENABLED";

    /** Flowable 兼容读取的旧 Activiti 开关；存在时优先于 Flowable 开关。 */
    public static final String ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE =
            "_ACTIVITI_SKIP_EXPRESSION_ENABLED";

    /** 兼容已部署的旧模型中 ${skipNodeEnabled} 表达式。 */
    public static final String LEGACY_SKIP_NODE_ENABLED_VARIABLE =
            "skipNodeEnabled";

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
            LEGACY_SKIP_NODE_ENABLED_VARIABLE,
            FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE,
            ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE,
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

    /**
     * 在所有不可信变量完成合并后写入原生跳过能力所需的可信上下文。
     *
     * <p>必须移除旧 Activiti 开关，因为 Flowable 会优先读取它；若调用方注入
     * {@code false}，将覆盖本平台写入的 Flowable 开关。旧的
     * {@code skipNodeEnabled} 仅用于兼容历史部署，不再作为新发布模型的权威路径。</p>
     *
     * @param variables 即将交给流程引擎的可变启动变量
     */
    public static void enableNativeSkipExpressions(
            Map<String, Object> variables) {
        if (variables == null) {
            throw new IllegalArgumentException("流程变量不能为空");
        }
        variables.remove(ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE);
        variables.put(FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE, true);
        variables.put(LEGACY_SKIP_NODE_ENABLED_VARIABLE, true);
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
                && (variableName.startsWith(INTERNAL_PREFIX)
                || LEGACY_SKIP_NODE_ENABLED_VARIABLE.equals(variableName)
                || FLOWABLE_SKIP_EXPRESSION_ENABLED_VARIABLE.equals(
                        variableName)
                || ACTIVITI_SKIP_EXPRESSION_ENABLED_VARIABLE.equals(
                        variableName));
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
