package com.workflow.core.database.port;

import java.util.Optional;

/** 跨实例互斥锁。业务事务和 DDL 的提交不能释放该锁；竞争返回空，连接错误抛异常。 */
public interface DatabaseLockPort {
    /**
     * 处理尝试获取，并将结果传给后续步骤。
     *
     * @param namespace 命名空间，供本方法处理尝试获取时使用
     * @param key 键，后续用于授权校验、关联或幂等去重
     * @return 匹配的尝试获取；未找到时为空
     */
    Optional<Handle> tryAcquire(String namespace, String key);

    /** 句柄拥有独立会话；必须 close，重复 close 无副作用。 */
    interface Handle extends AutoCloseable {
        /**
         * 处理关闭，并将结果传给后续步骤。
         */
        @Override void close();
    }
}
