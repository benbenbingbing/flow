package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.entity.ProcessTaskInstance;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 流程任务实例 Mapper
 */
@Mapper
public interface ProcessTaskInstanceMapper extends BaseMapper<ProcessTaskInstance> {
    
    /**
     * 查询待办任务列表
     */
    Page<ProcessTaskInstance> selectTodoList(Page<ProcessTaskInstance> page, 
            @Param("assigneeId") String assigneeId,
            @Param("processKey") String processKey,
            @Param("keyword") String keyword,
            @Param("priority") Integer priority);
    
    /**
     * 查询已办任务列表
     */
    Page<ProcessTaskInstance> selectDoneList(Page<ProcessTaskInstance> page,
            @Param("assigneeId") String assigneeId,
            @Param("processKey") String processKey,
            @Param("actionType") String actionType);
    
    /**
     * 查询抄送列表
     */
    Page<ProcessTaskInstance> selectCcList(Page<ProcessTaskInstance> page,
            @Param("userId") String userId,
            @Param("isRead") Boolean isRead);
    
    /**
     * 统计待办数量
     */
    @Select("SELECT COUNT(*) FROM process_task_instance WHERE assignee_id = #{userId} AND task_type = 'TODO'")
    Long countTodo(@Param("userId") String userId);
    
    /**
     * 统计已办数量
     */
    @Select("SELECT COUNT(*) FROM process_task_instance WHERE assignee_id = #{userId} AND task_type = 'DONE' AND DATE(start_time) = CURDATE()")
    Long countDoneToday(@Param("userId") String userId);
    
    /**
     * 统计未读抄送
     */
    @Select("SELECT COUNT(*) FROM process_task_instance WHERE assignee_id = #{userId} AND task_type = 'CC' AND is_read = 0")
    Long countUnreadCc(@Param("userId") String userId);
}
