package com.workflow.process.status.application;

/**
 * 持久化到引擎删除原因的结束协议。类型用于状态同步、历史展示及重新发起校验，
 * 原因只用于展示；用户填写“撤回”等文字不能改变真正的操作类型。
 * 沿用引擎自身的持久化字段，使结束监听、事务提交后的对账和历史查询读取同一事实。
 */
public final class ProcessEndReason {
    private static final String PREFIX = "FLOW_END_V1:";

    private ProcessEndReason() { }

    /** 编码明确的人工/系统取消类型；拒绝把正常完成伪装成取消。 */
    public static String encode(String type, String comment) {
        if (!"TERMINATED".equals(type) && !"WITHDRAWN".equals(type)) {
            throw new IllegalArgumentException("不支持的取消类型: " + type);
        }
        return PREFIX + type + "\n" + (comment == null ? "" : comment);
    }

    /**
     * 从持久化原因恢复类型。旧撤回接口固定写入“发起人撤回”前缀，仅对旧格式兼容；
     * 不对自由文本做 contains 判断，避免终止意见中的“撤回”触发错误业务动作。
     */
    public static String category(String reason) {
        if (reason == null || reason.isBlank()) return "COMPLETED";
        if (reason.startsWith(PREFIX)) {
            int separator = reason.indexOf('\n');
            String type = reason.substring(PREFIX.length(), separator < 0 ? reason.length() : separator);
            if ("WITHDRAWN".equals(type) || "TERMINATED".equals(type)) return type;
            throw new IllegalArgumentException("无法识别的流程结束类型: " + type);
        }
        return reason.startsWith("发起人撤回") ? "WITHDRAWN" : "TERMINATED";
    }

    /** 活动/任务有真实删除原因才算取消；旧引擎可能把正常完成写成 completed。 */
    public static boolean isCancelled(String reason) {
        return reason != null && !reason.isBlank() && !"completed".equalsIgnoreCase(reason);
    }

    /** 展示时去除协议头，避免把机器类型编码当成用户意见。 */
    public static String comment(String reason) {
        if (reason == null || !reason.startsWith(PREFIX)) return reason;
        category(reason);
        int separator = reason.indexOf('\n');
        return separator < 0 ? "" : reason.substring(separator + 1);
    }
}
