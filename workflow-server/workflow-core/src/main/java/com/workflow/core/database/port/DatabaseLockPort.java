package com.workflow.core.database.port;

import java.util.Optional;

/** 跨实例互斥锁。业务事务和 DDL 的提交不能释放该锁；竞争返回空，连接错误抛异常。 */
public interface DatabaseLockPort {
    Optional<Handle> tryAcquire(String namespace, String key);

    /** 句柄拥有独立会话；必须 close，重复 close 无副作用。 */
    interface Handle extends AutoCloseable {
        @Override void close();
    }
}
