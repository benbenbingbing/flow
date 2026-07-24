package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityListScopeRelease;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 实体列表数据范围发布版本 Mapper
 * 
 * 提供按实体编码查询活跃数据范围版本、查询最大版本号、停用当前活跃版本的能力。
 */
@Mapper
public interface EntityListScopeReleaseMapper extends BaseMapper<EntityListScopeRelease> {

    /**
     * 根据实体编码查询当前活跃（ACTIVE）的数据范围发布版本，取版本号最大的一条。
     *
     * @param entityCode 实体编码
     * @return 活跃发布版本，无则返回 null
     */
    @Select("SELECT * FROM entity_list_scope_release "
            + "WHERE entity_code = #{entityCode} AND status = 'ACTIVE' "
            + "ORDER BY version DESC LIMIT 1")
    EntityListScopeRelease findActive(@Param("entityCode") String entityCode);

    /**
     * 查询指定实体的最大发布版本号，无记录时返回 0。
     *
     * @param entityCode 实体编码
     * @return 最大版本号
     */
    @Select("SELECT COALESCE(MAX(version), 0) FROM entity_list_scope_release "
            + "WHERE entity_code = #{entityCode}")
    int findMaxVersion(@Param("entityCode") String entityCode);

    /**
     * 将指定实体的活跃发布版本置为 INACTIVE（用于发布新版本前停用旧版本）。
     *
     * @param entityCode 实体编码
     * @return 影响行数
     */
    @Update("UPDATE entity_list_scope_release SET status = 'INACTIVE' "
            + "WHERE entity_code = #{entityCode} AND status = 'ACTIVE'")
    int deactivate(@Param("entityCode") String entityCode);
}
