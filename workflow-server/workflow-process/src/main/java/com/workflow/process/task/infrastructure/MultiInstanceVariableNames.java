package com.workflow.process.task.infrastructure;

import com.workflow.process.engine.infrastructure.flowable.ConfiguredTaskPropertyReader;
import org.flowable.bpmn.model.Activity;
import org.flowable.bpmn.model.MultiInstanceLoopCharacteristics;
import org.springframework.util.StringUtils;

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

    /** 发布器保存动态 collection 原业务变量名的扩展属性。 */
    public static final String ENTRY_DYNAMIC_COLLECTION_PROPERTY =
            "entryDynamicCollectionVariable";

    /**
     * 动态 collection handler 前置表达式使用的安全字面量。
     *
     * <p>Flowable 7.2 会先计算 collection，再调用 handler。字面量无需依赖
     * 流程变量；handler 会忽略它并从权威业务来源返回真实集合。</p>
     */
    public static final String ENTRY_DYNAMIC_COLLECTION_LITERAL =
            "__wfEntryDynamicCollectionSeed";

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

    /**
     * 读取多实例节点的业务 collection 变量名。动态 handler 节点优先读取
     * 发布器保存的原变量名，普通节点继续从 loop 表达式解析。
     */
    public static String resolveCollectionVariable(Activity activity) {
        if (activity == null) {
            return null;
        }
        String preserved = ConfiguredTaskPropertyReader.read(
                activity, ENTRY_DYNAMIC_COLLECTION_PROPERTY);
        if (StringUtils.hasText(preserved)) {
            return simpleVariable(preserved);
        }
        return resolveCollectionVariable(activity.getLoopCharacteristics());
    }

    /** 从原始 loop 配置解析简单 collection 变量。 */
    public static String resolveCollectionVariable(
            MultiInstanceLoopCharacteristics loop) {
        if (loop == null) {
            return null;
        }
        String raw = StringUtils.hasText(loop.getInputDataItem())
                ? loop.getInputDataItem() : loop.getCollectionString();
        return simpleVariable(raw);
    }

    private static String simpleVariable(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String value = raw.trim();
        if ((value.startsWith("${") || value.startsWith("#{"))
                && value.endsWith("}")) {
            value = value.substring(2, value.length() - 1).trim();
        }
        return value.matches("[A-Za-z_][A-Za-z0-9_]*")
                ? value : null;
    }
}
