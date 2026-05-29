package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体列表数据权限规则 Mapper
 */
@Mapper
public interface EntityListPermissionMapper extends BaseMapper<EntityListPermission> {

    /**
     * 查询某实体下所有启用的规则，按优先级降序
     */
    @Select("SELECT * FROM entity_list_permission " +
            "WHERE entity_code = #{entityCode} AND enabled = 1 AND deleted = 0 " +
            "ORDER BY priority DESC")
    List<EntityListPermission> findEnabledByEntityCode(@Param("entityCode") String entityCode);
}
