package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessTaskAddSign;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 任务加签操作 Mapper
 * 提供加签操作的查询、加锁、计数、取消操作
 */
@Mapper
public interface ProcessTaskAddSignMapper extends BaseMapper<ProcessTaskAddSign> {
    /**
     * 根据源任务ID查询进行中的加签记录（ACTIVE/WAITING_SOURCE）。
     *
     * @param taskId 源任务ID
     * @return 最近一条进行中的加签记录，无则返回 null
     */
    @Select("SELECT * FROM process_task_add_sign WHERE source_task_id = #{taskId} " +
            "AND status IN ('ACTIVE','WAITING_SOURCE') ORDER BY create_time DESC LIMIT 1")
    ProcessTaskAddSign findOpenBySourceTaskId(@Param("taskId") String taskId);

    /**
     * 根据源任务ID加锁查询进行中的加签记录（FOR UPDATE）。
     *
     * @param taskId 源任务ID
     * @return 最近一条进行中的加签记录，无则返回 null
     */
    @Select("SELECT * FROM process_task_add_sign WHERE source_task_id = #{taskId} " +
            "AND status IN ('ACTIVE','WAITING_SOURCE') ORDER BY create_time DESC LIMIT 1 FOR UPDATE")
    ProcessTaskAddSign findOpenBySourceTaskIdForUpdate(@Param("taskId") String taskId);

    /**
     * 根据加签ID加锁查询加签记录（FOR UPDATE）。
     *
     * @param addSignId 加签记录ID
     * @return 加签记录，无则返回 null
     */
    @Select("SELECT * FROM process_task_add_sign WHERE id = #{addSignId} LIMIT 1 FOR UPDATE")
    ProcessTaskAddSign selectByIdForUpdate(@Param("addSignId") String addSignId);

    /**
     * 统计源任务下进行中（ACTIVE/WAITING_SOURCE）的加签数。
     * <p>
     * 用于判断源任务是否被加签阻塞，暂不能完成。
     *
     * @param taskId 源任务ID
     * @return 阻塞中的加签数
     */
    @Select("SELECT COUNT(*) FROM process_task_add_sign WHERE source_task_id = #{taskId} AND status IN ('ACTIVE','WAITING_SOURCE')")
    long countBlockingBySourceTaskId(@Param("taskId") String taskId);

    /**
     * 取消进行中的加签操作（置为 CANCELLED）。
     *
     * @param addSignId 加签记录ID
     * @return 受影响行数
     */
    @Update("UPDATE process_task_add_sign SET status = 'CANCELLED', complete_time = NOW() " +
            "WHERE id = #{addSignId} AND status IN ('ACTIVE','WAITING_SOURCE')")
    int cancel(@Param("addSignId") String addSignId);
}
