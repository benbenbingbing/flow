package com.workflow.contracts.execution.port;

/** Provider 的协作式执行预算；所有 IO 与长循环应共享同一预算，不能在重试时重新计时。 */
public interface ExecutionControlPort {
    /** 返回剩余毫秒，已经取消或超时则抛出异常；可用于 HTTP/JDBC 的底层超时参数。 */
    long remainingMillis();

    /** 在开始 IO、循环迭代及返回结果前检查，禁止继续执行已经过期的工作。 */
    void check();

    /** 注册正在使用的资源的取消操作，例如 Statement.cancel；资源关闭前注销回调。 */
    Registration onCancellation(Runnable cancel);

    interface Registration extends AutoCloseable {
        @Override void close();
    }
}
