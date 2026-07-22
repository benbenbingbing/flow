package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListScopeRelease;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface EntityListScopeReleaseMapper extends BaseMapper<EntityListScopeRelease> {

    @Select("SELECT * FROM entity_list_scope_release "
            + "WHERE entity_code = #{entityCode} AND status = 'ACTIVE' "
            + "ORDER BY version DESC LIMIT 1")
    EntityListScopeRelease findActive(@Param("entityCode") String entityCode);

    @Select("SELECT COALESCE(MAX(version), 0) FROM entity_list_scope_release "
            + "WHERE entity_code = #{entityCode}")
    int findMaxVersion(@Param("entityCode") String entityCode);

    @Update("UPDATE entity_list_scope_release SET status = 'INACTIVE' "
            + "WHERE entity_code = #{entityCode} AND status = 'ACTIVE'")
    int deactivate(@Param("entityCode") String entityCode);
}
