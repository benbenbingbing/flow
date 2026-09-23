package com.workflow.process.assignment.domain;

import java.util.List;

/**
 * 结构化办理人解析结果，空结果和解析错误不会再用 null 或空集合静默表达。
 *
 * @param status 状态标识，决定后续办理人解析结果采用的处理分支
 * @param usernames {@code usernames}，保存在对象中供后续校验、查询或展示
 * @param reasonCode 原因编码，后续用于处理办理人解析结果时定位或关联目标
 * @param reasonMessage 原因消息，保存在对象中供后续校验、查询或展示
 * @param resolverCode 解析器编码，后续用于处理办理人解析结果时定位或关联目标
 */
public record AssigneeResolutionResult(
        Status status,
        List<String> usernames,
        String reasonCode,
        String reasonMessage,
        String resolverCode) {

    /**
     * 处理已解析，并将结果传给后续步骤。
     *
     * @param usernames {@code usernames}，作为 {@code AssigneeResolutionResult} 的输入影响后续处理
     * @param resolverCode 解析器编码，后续用于处理已解析时定位或关联目标
     * @return 处理后的已解析结果，供调用方继续处理
     */
    public static AssigneeResolutionResult resolved(
            List<String> usernames,
            String resolverCode) {
        return new AssigneeResolutionResult(
                Status.RESOLVED, List.copyOf(usernames), null, null, resolverCode);
    }

    /**
     * 处理空，并将结果传给后续步骤。
     *
     * @param reasonCode 原因编码，后续用于处理空时定位或关联目标
     * @param reasonMessage 原因消息，供本方法处理空时使用
     * @param resolverCode 解析器编码，后续用于处理空时定位或关联目标
     * @return 处理后的空结果，供调用方继续处理
     */
    public static AssigneeResolutionResult empty(
            String reasonCode,
            String reasonMessage,
            String resolverCode) {
        return new AssigneeResolutionResult(
                Status.EMPTY, List.of(), reasonCode, reasonMessage, resolverCode);
    }

    /**
     * 处理错误，并将结果传给后续步骤。
     *
     * @param reasonCode 原因编码，后续用于处理错误时定位或关联目标
     * @param reasonMessage 原因消息，供本方法处理错误时使用
     * @param resolverCode 解析器编码，后续用于处理错误时定位或关联目标
     * @return 处理后的错误结果，供调用方继续处理
     */
    public static AssigneeResolutionResult error(
            String reasonCode,
            String reasonMessage,
            String resolverCode) {
        return new AssigneeResolutionResult(
                Status.ERROR, List.of(), reasonCode, reasonMessage, resolverCode);
    }

    /**
     * 判断已解析条件是否成立，供调用方选择后续分支。
     *
     * @return 已解析条件成立时为 true，否则为 false
     */
    public boolean resolved() {
        return status == Status.RESOLVED && !usernames.isEmpty();
    }

    /**
     * 定义状态的可选值；调用方据此选择对应的处理分支。
     */
    public enum Status {
        RESOLVED,
        EMPTY,
        ERROR
    }
}
