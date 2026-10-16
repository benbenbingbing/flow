package com.workflow.process.command.application;

import java.util.Objects;
import com.workflow.contracts.process.model.CommandMeta;
import com.workflow.contracts.process.error.ProcessOperationException;

/**
 * 由平台流程动作执行器建立的内部范围，不是公共契约或可反序列化的授权参数。
 * 只授予本次流程读取和业务变量修改，不能替代人工任务办理身份。
 */
public final class ProcessInvocationScope implements AutoCloseable {
    private static final ThreadLocal<ProcessInvocationScope> CURRENT = new ThreadLocal<>();
    private final ProcessInvocationScope previous;
    private final String instanceId;
    private final String actionId;
    private final String executionKey;
    private int commandSequence;

    private ProcessInvocationScope(String instanceId, String actionId, String executionKey) {
        this.previous = CURRENT.get();
        this.instanceId = Objects.requireNonNull(instanceId);
        this.actionId = Objects.requireNonNull(actionId);
        this.executionKey = Objects.requireNonNull(executionKey);
        CURRENT.set(this);
    }

    /** 只由宿主在调用已发布动作前创建；try-with-resources 保证嵌套及异常时恢复原范围。 */
    public static ProcessInvocationScope openAction(String instanceId, String actionId, String executionKey) {
        return new ProcessInvocationScope(instanceId, actionId, executionKey);
    }

    /** 当前范围是否允许访问该实例；不能因此查询其他实例或代替用户完成任务。 */
    static boolean allows(String instanceId) {
        ProcessInvocationScope scope = CURRENT.get();
        return scope != null && scope.instanceId.equals(instanceId);
    }

    /** 重试使用同一动作执行身份，避免后台线程是否带请求身份影响回执归属。 */
    static String key() {
        ProcessInvocationScope scope = CURRENT.get();
        if (scope == null) return null;
        // 长度编码避免字段中的冒号产生歧义，摘要令超长持久化执行键也能稳定落入回执列。
        String identity = part(scope.instanceId) + part(scope.actionId) + part(scope.executionKey);
        try {
            return "action:" + java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    /**
     * 供动作快捷写变量使用：同次执行中的每一步有独立键，重试按相同顺序重放。
     * 无动作范围或跨实例调用必须失败；复杂分支应直接使用公共端口和业务定义的稳定 requestId。
     */
    public static CommandMeta nextVariableCommand(String instanceId) {
        ProcessInvocationScope scope = CURRENT.get();
        if (scope == null || !scope.instanceId.equals(instanceId)) throw new ProcessOperationException(
                "PROCESS_ACCESS_DENIED", "动作快捷写入只能用于当前签发的流程实例", instanceId);
        return new CommandMeta("action-variable-" + ++scope.commandSequence);
    }

    private static String part(String value) { return value.length() + ":" + value; }

    @Override
    public void close() {
        if (previous == null) CURRENT.remove(); else CURRENT.set(previous);
    }
}
