package com.workflow.process.task.infrastructure.persistence.mapper;

import com.workflow.contracts.entity.model.EntityTaskSummary;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** 摘要和身份独立更新，不能用整行 updateById 覆盖并发业务结果或写不进去的 NULL。 */
@Mapper
public interface TaskInboxProjectionMapper {
    String SUMMARY_ASSIGNMENTS = """
            business_name=#{s.name,jdbcType=LONGVARCHAR},business_code=#{s.code,jdbcType=LONGVARCHAR},
            business_data_name=#{s.dataName,jdbcType=LONGVARCHAR},
            business_current_task_name=#{s.currentTaskName,jdbcType=LONGVARCHAR},business_status=#{s.status,jdbcType=LONGVARCHAR}
            """;
    /** 回填与引擎变更先竞争同一任务行，再刷新平台投影；运行任务消失时继续修复历史。 */
    @Select("SELECT ID_ FROM ACT_RU_TASK WHERE ID_=#{taskId} FOR UPDATE")
    String lockEngineTask(@Param("taskId") String taskId);

    /** 撤回可能先逻辑删除平台任务，再删除引擎任务；仍需找到主键清理候选，不能重新创建。 */
    @Select("SELECT id FROM process_task WHERE task_id=#{taskId} AND deleted=1 FOR UPDATE")
    Long findDeletedTaskId(@Param("taskId") String taskId);

    @Update("""
            UPDATE process_task SET assignee_id=#{assignee,jdbcType=VARCHAR},
              assignee_name=#{name,jdbcType=VARCHAR},assignee_type=#{type,jdbcType=VARCHAR},inbox_identity_ready=1
            WHERE id=#{id} AND deleted=0
            """)
    int updateIdentity(@Param("id") Long id, @Param("assignee") String assignee,
                       @Param("name") String name, @Param("type") String type);

    @Update("UPDATE process_task SET " + SUMMARY_ASSIGNMENTS + " WHERE id=#{id} AND deleted=0")
    int updateBusinessSummary(@Param("id") Long id, @Param("s") EntityTaskSummary summary);

    /** 按业务索引一次更新全部历史/活跃任务，避免长流程每次编辑发出数千条 UPDATE。 */
    @Update("UPDATE process_task SET " + SUMMARY_ASSIGNMENTS
            + " WHERE entity_code=#{entityCode} AND entity_data_id=#{recordId} AND deleted=0")
    int updateBusinessSummaries(@Param("entityCode") String entityCode, @Param("recordId") String recordId,
                               @Param("s") EntityTaskSummary summary);

    @Update("UPDATE process_task SET start_user_id=#{starter,jdbcType=VARCHAR},inbox_summary_ready=1 WHERE id=#{id} AND deleted=0")
    int markSummaryReady(@Param("id") Long id, @Param("starter") String starter);

    @Select("<script> SELECT id FROM process_task WHERE entity_code=#{entityCode} AND entity_data_id=#{recordId} AND deleted=0 ORDER BY id "
            + "${@com.workflow.integration.database.api.query.DatabaseQuerySql@page(_databaseId, '0', '1')} </script>")
    Long findFirstBusinessTask(@Param("entityCode") String entityCode, @Param("recordId") String recordId);

    @Select("<script> SELECT id FROM process_task WHERE deleted=0 AND id>#{afterId} "
            + "AND (COALESCE(inbox_summary_ready,0)=0 OR COALESCE(inbox_identity_ready,0)=0) ORDER BY id "
            + "${@com.workflow.integration.database.api.query.DatabaseQuerySql@page(_databaseId, '0', 'limit')} </script>")
    List<Long> findUnready(@Param("afterId") long afterId, @Param("limit") int limit);

    @Select("SELECT id FROM process_task WHERE id=#{id} AND deleted=0 FOR UPDATE")
    Long lockTask(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM process_task WHERE deleted=0 AND (COALESCE(inbox_summary_ready,0)=0 OR COALESCE(inbox_identity_ready,0)=0)")
    long countUnready();
}
