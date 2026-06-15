package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 流程待办Mapper
 */
@Mapper
public interface ProcessTaskMapper extends BaseMapper<ProcessTask> {
    
    /**
     * 查询待办列表（根据用户ID查询用户的待办）
     */
    @Select("SELECT pt.* FROM process_task pt " +
            "WHERE pt.status = 'todo' AND pt.deleted = 0 " +
            "AND (" +
            "  pt.assignee_id = #{userId} " +
            "  OR pt.assignee_id COLLATE utf8mb4_unicode_ci = (SELECT id FROM sys_user WHERE username = #{userId} AND deleted = 0 LIMIT 1) " +
            "  OR pt.assignee_id COLLATE utf8mb4_unicode_ci = (SELECT username FROM sys_user WHERE id = #{userId} AND deleted = 0 LIMIT 1) " +
            "  OR (" +
            "    pt.assignee_type = 'group' " +
            "    AND EXISTS (" +
            "      SELECT 1 FROM sys_group g " +
            "      INNER JOIN sys_user_group ug ON ug.group_id = g.id " +
            "      INNER JOIN sys_user u ON u.id = ug.user_id " +
            "      WHERE (u.username = #{userId} OR u.id = #{userId}) " +
            "        AND g.deleted = 0 " +
            "        AND FIND_IN_SET(g.group_code COLLATE utf8mb4_0900_ai_ci, pt.assignee_id) > 0" +
            "    )" +
            "  )" +
            ") ORDER BY pt.create_time DESC")
    List<ProcessTask> selectTodoByUser(@Param("userId") String userId);
    
    /**
     * 查询已办列表（根据用户ID查询用户已完成的）
     */
    @Select("SELECT * FROM process_task pt WHERE (" +
            "pt.assignee_id = #{userId} " +
            "OR pt.assignee_id COLLATE utf8mb4_unicode_ci = (SELECT id FROM sys_user WHERE username = #{userId} AND deleted = 0 LIMIT 1) " +
            "OR pt.assignee_id COLLATE utf8mb4_unicode_ci = (SELECT username FROM sys_user WHERE id = #{userId} AND deleted = 0 LIMIT 1)" +
            ") AND pt.status = 'done' AND pt.deleted = 0 ORDER BY pt.end_time DESC")
    List<ProcessTask> selectDoneByUser(@Param("userId") String userId);
    
    /**
     * 根据流程实例ID查询待办
     */
    @Select("SELECT * FROM process_task WHERE process_instance_id = #{processInstanceId} AND deleted = 0 ORDER BY create_time")
    List<ProcessTask> selectByProcessInstance(@Param("processInstanceId") String processInstanceId);
    
    /**
     * 根据流程实例ID查询当前待办任务（status=0）
     */
    @Select("SELECT * FROM process_task WHERE process_instance_id = #{processInstanceId} AND status = 'todo' AND deleted = 0 LIMIT 1")
    ProcessTask selectTodoTaskByProcessInstance(@Param("processInstanceId") String processInstanceId);
    
    /**
     * 根据Flowable任务ID查询
     */
    @Select("SELECT * FROM process_task WHERE task_id = #{taskId} AND deleted = 0 LIMIT 1")
    ProcessTask selectByTaskId(@Param("taskId") String taskId);
    
    /**
     * 完成任务
     */
    @Update("UPDATE process_task SET status = #{status}, action = #{action}, comment = #{comment}, " +
            "end_time = NOW(), duration = #{duration} WHERE id = #{id}")
    int completeTask(@Param("id") Long id, @Param("status") String status, 
                     @Param("action") String action, @Param("comment") String comment,
                     @Param("duration") Long duration);
    
    /**
     * 统计用户待办数
     */
    @Select("SELECT COUNT(*) FROM process_task pt " +
            "WHERE pt.status = 'todo' AND pt.deleted = 0 " +
            "AND (" +
            "  pt.assignee_id = #{userId} " +
            "  OR pt.assignee_id COLLATE utf8mb4_unicode_ci = (SELECT id FROM sys_user WHERE username = #{userId} AND deleted = 0 LIMIT 1) " +
            "  OR pt.assignee_id COLLATE utf8mb4_unicode_ci = (SELECT username FROM sys_user WHERE id = #{userId} AND deleted = 0 LIMIT 1) " +
            "  OR (" +
            "    pt.assignee_type = 'group' " +
            "    AND EXISTS (" +
            "      SELECT 1 FROM sys_group g " +
            "      INNER JOIN sys_user_group ug ON ug.group_id = g.id " +
            "      INNER JOIN sys_user u ON u.id = ug.user_id " +
            "      WHERE (u.username = #{userId} OR u.id = #{userId}) " +
            "        AND g.deleted = 0 " +
            "        AND FIND_IN_SET(g.group_code COLLATE utf8mb4_0900_ai_ci, pt.assignee_id) > 0" +
            "    )" +
            "  )" +
            ")")
    Long countTodoByUser(@Param("userId") String userId);
    
    /**
     * 统计用户已办数
     */
    @Select("SELECT COUNT(*) FROM process_task pt WHERE (" +
            "pt.assignee_id = #{userId} " +
            "OR pt.assignee_id COLLATE utf8mb4_unicode_ci = (SELECT id FROM sys_user WHERE username = #{userId} AND deleted = 0 LIMIT 1) " +
            "OR pt.assignee_id COLLATE utf8mb4_unicode_ci = (SELECT username FROM sys_user WHERE id = #{userId} AND deleted = 0 LIMIT 1)" +
            ") AND pt.status = 'done' AND pt.deleted = 0")
    Long countDoneByUser(@Param("userId") String userId);
}
