-- OceanBase MySQL 使用独立物理连接的未提交唯一键插入仲裁互斥，规避 GET_LOCK 并发首次获取的多赢家问题。
-- 此表只保存持锁事务自己的临时行；释放必须 ROLLBACK，禁止提交标记或复用为业务锁/租约表。
CREATE TABLE sys_database_lock (
    lock_key VARCHAR(64) NOT NULL COMMENT '命名空间及业务键哈希，唯一键冲突用于非阻塞互斥；仅持锁事务可见',
    PRIMARY KEY (lock_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='跨实例独立事务互斥锁标记，不持久化业务数据';
