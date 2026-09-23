package com.workflow.process.status.infrastructure.persistence.mapper;

import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 定义流程状态同步的调用契约；实现层按此提供能力，调用方无需依赖具体实现。
 */
@Mapper
public interface ProcessStatusSyncMapper {

    /**
     * 确认本事务取得的 APPLYING 记录；now 为数据库 UTC 时间，返回 1 才能提交业务结果。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param now 当前时间，供本方法标记{@code applied}时使用
     * @return 标记后的{@code applied}结果，供调用方继续处理
     */
    @Update("""
            UPDATE process_status_sync_event
            SET state = 'APPLIED',
                applied_at = #{now},
                update_time = #{now}
            WHERE id = #{id}
              AND state = 'APPLYING'
            """)
    int markApplied(@Param("id") String id, @Param("now") LocalDateTime now);
}
