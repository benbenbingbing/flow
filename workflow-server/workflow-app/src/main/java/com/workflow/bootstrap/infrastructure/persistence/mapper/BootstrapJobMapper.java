package com.workflow.bootstrap.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.*;

/** 初始化任务状态；所有读写必须加入持有任务行锁的同一事务。 */
@Mapper
public interface BootstrapJobMapper {
    /** 当前读可见已完成版本，跨节点不会重复执行初始化。 */
    @Select("SELECT completed_version FROM workflow_bootstrap_job WHERE job_name = #{name} FOR UPDATE")
    Integer completedVersion(@Param("name") String name);

    /** 业务动作成功后登记完成时间；失败随外层事务回滚。 */
    @Update("UPDATE workflow_bootstrap_job SET completed_version = #{version}, owner_id = #{owner}, completed_at = #{at}, update_time = #{at} WHERE job_name = #{name}")
    int complete(@Param("name") String name, @Param("version") int version,
                 @Param("owner") String owner, @Param("at") LocalDateTime at);
}
