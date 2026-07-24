package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListScopeDelegation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体列表数据范围授权（代办/委托） Mapper
 * 
 * 提供按被授权用户与实体编码查询当前有效的数据范围委托关系的能力。
 */
@Mapper
public interface EntityListScopeDelegationMapper extends BaseMapper<EntityListScopeDelegation> {

    /**
     * 查询指定被授权用户当前有效的数据范围委托列表。
     * 条件：已启用、未删除、未过期，且实体编码为空（全局）或与传入实体编码一致。
     *
     * @param toUserId   被授权用户 ID
     * @param entityCode 实体编码
     * @return 有效的委托列表
     */
    @Select("SELECT * FROM entity_list_scope_delegation "
            + "WHERE to_user_id = #{toUserId} AND enabled = 1 AND deleted = 0 "
            + "AND (entity_code IS NULL OR entity_code = '' OR entity_code = #{entityCode}) "
            + "AND (start_time IS NULL OR start_time <= NOW()) "
            + "AND (end_time IS NULL OR end_time >= NOW())")
    List<EntityListScopeDelegation> findActiveByToUserId(
            @Param("toUserId") String toUserId,
            @Param("entityCode") String entityCode);
}
