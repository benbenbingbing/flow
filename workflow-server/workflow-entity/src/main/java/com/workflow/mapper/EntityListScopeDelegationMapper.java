package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListScopeDelegation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityListScopeDelegationMapper extends BaseMapper<EntityListScopeDelegation> {

    @Select("SELECT * FROM entity_list_scope_delegation "
            + "WHERE to_user_id = #{toUserId} AND enabled = 1 AND deleted = 0 "
            + "AND (entity_code IS NULL OR entity_code = '' OR entity_code = #{entityCode}) "
            + "AND (start_time IS NULL OR start_time <= NOW()) "
            + "AND (end_time IS NULL OR end_time >= NOW())")
    List<EntityListScopeDelegation> findActiveByToUserId(
            @Param("toUserId") String toUserId,
            @Param("entityCode") String entityCode);
}
