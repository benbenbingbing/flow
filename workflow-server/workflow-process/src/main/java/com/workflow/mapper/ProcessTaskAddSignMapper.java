package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessTaskAddSign;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProcessTaskAddSignMapper extends BaseMapper<ProcessTaskAddSign> {
    @Select("SELECT * FROM process_task_add_sign WHERE source_task_id = #{taskId} " +
            "AND status IN ('ACTIVE','WAITING_SOURCE') ORDER BY create_time DESC LIMIT 1")
    ProcessTaskAddSign findOpenBySourceTaskId(@Param("taskId") String taskId);

    @Select("SELECT * FROM process_task_add_sign WHERE source_task_id = #{taskId} " +
            "AND status IN ('ACTIVE','WAITING_SOURCE') ORDER BY create_time DESC LIMIT 1 FOR UPDATE")
    ProcessTaskAddSign findOpenBySourceTaskIdForUpdate(@Param("taskId") String taskId);

    @Select("SELECT * FROM process_task_add_sign WHERE id = #{addSignId} LIMIT 1 FOR UPDATE")
    ProcessTaskAddSign selectByIdForUpdate(@Param("addSignId") String addSignId);

    @Select("SELECT COUNT(*) FROM process_task_add_sign WHERE source_task_id = #{taskId} AND status IN ('ACTIVE','WAITING_SOURCE')")
    long countBlockingBySourceTaskId(@Param("taskId") String taskId);

    @Update("UPDATE process_task_add_sign SET status = 'CANCELLED', complete_time = NOW() " +
            "WHERE id = #{addSignId} AND status IN ('ACTIVE','WAITING_SOURCE')")
    int cancel(@Param("addSignId") String addSignId);
}
