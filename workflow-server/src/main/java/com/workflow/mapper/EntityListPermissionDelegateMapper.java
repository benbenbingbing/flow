package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListPermissionDelegate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 数据权限委托 Mapper
 */
@Mapper
public interface EntityListPermissionDelegateMapper extends BaseMapper<EntityListPermissionDelegate> {

    /**
     * 查询受托方当前有效的委托记录
     */
    @Select("SELECT * FROM entity_list_permission_delegate " +
            "WHERE to_user_id = #{toUserId} AND enabled = 1 " +
            "AND (start_time IS NULL OR start_time <= NOW()) " +
            "AND (end_time IS NULL OR end_time >= NOW()) " +
            "AND (entity_code = #{entityCode} OR entity_code IS NULL OR entity_code = '')")
    List<EntityListPermissionDelegate> findActiveByToUserId(
            @Param("toUserId") String toUserId,
            @Param("entityCode") String entityCode);
}
