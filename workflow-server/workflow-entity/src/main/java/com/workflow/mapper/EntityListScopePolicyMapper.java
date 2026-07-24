package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListScopePolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体列表数据范围策略 Mapper
 * 
 * 提供按实体编码查询数据范围策略列表的能力。
 */
@Mapper
public interface EntityListScopePolicyMapper extends BaseMapper<EntityListScopePolicy> {

    /**
     * 根据实体编码查询未删除的数据范围策略列表，按创建时间升序排列。
     *
     * @param entityCode 实体编码
     * @return 数据范围策略列表
     */
    @Select("SELECT * FROM entity_list_scope_policy "
            + "WHERE entity_code = #{entityCode} AND deleted = 0 ORDER BY create_time ASC")
    List<EntityListScopePolicy> findByEntityCode(@Param("entityCode") String entityCode);
}
