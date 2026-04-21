package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.ProcessCcRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 流程抄送记录 Mapper
 */
@Mapper
public interface ProcessCcRecordMapper extends BaseMapper<ProcessCcRecord> {
    
    /**
     * 根据流程实例ID查询抄送记录
     */
    @Select("SELECT * FROM process_cc_record WHERE process_instance_id = #{processInstanceId} AND deleted = 0 ORDER BY create_time DESC")
    List<ProcessCcRecord> findByProcessInstanceId(@Param("processInstanceId") String processInstanceId);
    
    /**
     * 根据抄送人查询抄送记录（分页）
     */
    @Select("SELECT * FROM process_cc_record WHERE cc_user_id = #{userId} AND deleted = 0 ORDER BY create_time DESC LIMIT #{offset}, #{limit}")
    List<ProcessCcRecord> findByCcUserId(@Param("userId") String userId, @Param("offset") int offset, @Param("limit") int limit);
    
    /**
     * 统计抄送人的抄送记录数
     */
    @Select("SELECT COUNT(*) FROM process_cc_record WHERE cc_user_id = #{userId} AND deleted = 0")
    long countByCcUserId(@Param("userId") String userId);
    
    /**
     * 统计未读抄送数
     */
    @Select("SELECT COUNT(*) FROM process_cc_record WHERE cc_user_id = #{userId} AND read_status = 'UNREAD' AND deleted = 0")
    long countUnreadByUserId(@Param("userId") String userId);
    
    /**
     * 标记为已读
     */
    @Update("UPDATE process_cc_record SET read_status = 'READ', read_time = NOW() WHERE id = #{id}")
    int markAsRead(@Param("id") String id);
    
    /**
     * 批量标记为已读
     */
    @Update("UPDATE process_cc_record SET read_status = 'READ', read_time = NOW() WHERE cc_user_id = #{userId} AND read_status = 'UNREAD'")
    int markAllAsRead(@Param("userId") String userId);
}
