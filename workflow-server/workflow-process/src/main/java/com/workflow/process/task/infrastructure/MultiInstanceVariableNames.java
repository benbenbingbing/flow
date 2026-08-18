package com.workflow.process.task.infrastructure;

/**
 * 多实例流程变量命名约定集中定义，避免跨层重复维护前缀与清洗规则。
 *
 * <p>节点 ID 清洗规则必须与前端 {@code process-config/index.js} 保持一致，
 * 否则完成条件表达式读不到运行时写入的计数/驳回变量。</p>
 */
public final class MultiInstanceVariableNames {

    private MultiInstanceVariableNames() {
    }

    public static final String APPROVED_COUNT_VARIABLE_PREFIX = "_wf_mi_approved_count_";
    public static final String REJECTED_VARIABLE_PREFIX = "_wf_mi_rejected_";
    public static final String COLLECTION_VARIABLE_PREFIX = "_wfMultiInstanceUsers_";
    public static final String LEGACY_COLLECTION_VARIABLE = "_wfMultiInstanceUsers_";

    /**
     * 将节点定义 ID 规范为可写入 Flowable 表达式的标识符。
     *
     * @param taskDefinitionKey 会签节点定义 ID，空值按 {@code node} 处理
     * @return 仅含字母数字和下划线的节点后缀
     */
    public static String sanitizeNodeId(String taskDefinitionKey) {
        String normalizedNodeId = String.valueOf(
                taskDefinitionKey == null ? "node" : taskDefinitionKey)
                .trim();
        normalizedNodeId = normalizedNodeId
                .replaceAll("[^A-Za-z0-9_]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "")
                .replaceFirst("^$", "node");
        return normalizedNodeId;
    }

    /**
     * 根据节点 ID 生成会签通过人数变量名。
     *
     * @param taskDefinitionKey 会签节点定义 ID
     * @return 统一规范后的变量名，示例：_wf_mi_approved_count_task_1
     */
    public static String buildApprovedCountVariableName(String taskDefinitionKey) {
        return APPROVED_COUNT_VARIABLE_PREFIX + sanitizeNodeId(taskDefinitionKey);
    }

    /**
     * 根据节点 ID 生成会签一票否决标记变量名。
     *
     * @param taskDefinitionKey 会签节点定义 ID
     * @return 统一规范后的变量名，示例：_wf_mi_rejected_task_1
     */
    public static String buildRejectedVariableName(String taskDefinitionKey) {
        return REJECTED_VARIABLE_PREFIX + sanitizeNodeId(taskDefinitionKey);
    }

    /**
     * 根据节点 ID 生成多实例人员集合变量名（不含表达式括号）。
     *
     * @param taskDefinitionKey 会签节点定义 ID
     * @return 统一规范后的变量名，示例：_wfMultiInstanceUsers_task_1
     */
    public static String buildCollectionVariableName(String taskDefinitionKey) {
        return COLLECTION_VARIABLE_PREFIX + sanitizeNodeId(taskDefinitionKey);
    }
}
