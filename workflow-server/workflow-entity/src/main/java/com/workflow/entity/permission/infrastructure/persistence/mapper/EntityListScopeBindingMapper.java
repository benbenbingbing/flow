package com.workflow.entity.permission.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopeBinding;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体列表数据范围绑定 Mapper
 * 
 * 提供按实体编码查询数据范围绑定列表的能力。
 */
@Mapper
public interface EntityListScopeBindingMapper extends BaseMapper<EntityListScopeBinding> {

    /**
     * 根据实体编码查询未删除的数据范围绑定列表，按创建时间升序排列。
     *
     * @param entityCode 实体编码
     * @return 数据范围绑定列表
     */
    @Select("SELECT * FROM entity_list_scope_binding "
            + "WHERE entity_code = #{entityCode} AND deleted = 0 ORDER BY create_time ASC")
    List<EntityListScopeBinding> findByEntityCode(@Param("entityCode") String entityCode);

    /**
     * 清理已经失效的数据范围绑定草稿，避免重复迁移持续累积无效记录。
     *
     * @param entityCode 实体编码
     * @return 删除行数
     */
    @Delete("DELETE FROM entity_list_scope_binding "
            + "WHERE entity_code = #{entityCode} AND deleted = 1")
    int purgeDeletedByEntityCode(@Param("entityCode") String entityCode);
}
