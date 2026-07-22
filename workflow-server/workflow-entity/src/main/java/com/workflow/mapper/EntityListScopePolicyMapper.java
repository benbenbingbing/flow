package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListScopePolicy;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityListScopePolicyMapper extends BaseMapper<EntityListScopePolicy> {

    @Select("SELECT * FROM entity_list_scope_policy "
            + "WHERE entity_code = #{entityCode} AND deleted = 0 ORDER BY create_time ASC")
    List<EntityListScopePolicy> findByEntityCode(@Param("entityCode") String entityCode);
}
