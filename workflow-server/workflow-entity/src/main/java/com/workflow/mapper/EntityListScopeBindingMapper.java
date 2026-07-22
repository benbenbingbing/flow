package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListScopeBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityListScopeBindingMapper extends BaseMapper<EntityListScopeBinding> {

    @Select("SELECT * FROM entity_list_scope_binding "
            + "WHERE entity_code = #{entityCode} AND deleted = 0 ORDER BY create_time ASC")
    List<EntityListScopeBinding> findByEntityCode(@Param("entityCode") String entityCode);
}
