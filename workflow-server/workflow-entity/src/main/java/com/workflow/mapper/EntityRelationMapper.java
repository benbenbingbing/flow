package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityRelation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体关系 Mapper
 * 
 * 提供按父实体（ID/编码/字段编码/绑定引用）查询实体关系，以及按父实体 ID 删除关系的能力。
 */
@Mapper
public interface EntityRelationMapper extends BaseMapper<EntityRelation> {

    /**
     * 根据父实体 ID 查询已启用且未删除的关系列表，按 sort_order、create_time 排序。
     *
     * @param parentEntityId 父实体 ID
     * @return 关系列表
     */
    @Select("SELECT * FROM entity_relation WHERE parent_entity_id = #{parentEntityId} AND enabled = 1 AND deleted = 0 ORDER BY sort_order ASC, create_time ASC")
    List<EntityRelation> selectByParentEntityId(@Param("parentEntityId") String parentEntityId);

    /**
     * 根据父实体编码查询已启用且未删除的关系列表。
     *
     * @param parentEntityCode 父实体编码
     * @return 关系列表
     */
    @Select("SELECT * FROM entity_relation WHERE parent_entity_code = #{parentEntityCode} AND enabled = 1 AND deleted = 0 ORDER BY sort_order ASC, create_time ASC")
    List<EntityRelation> selectByParentEntityCode(@Param("parentEntityCode") String parentEntityCode);

    /**
     * 根据父实体 ID 与父字段编码查询关系（含已禁用），取一条。
     *
     * @param parentEntityId   父实体 ID
     * @param parentFieldCode  父字段编码
     * @return 关系记录，无则返回 null
     */
    @Select("SELECT * FROM entity_relation WHERE parent_entity_id = #{parentEntityId} AND parent_field_code = #{parentFieldCode} AND deleted = 0 LIMIT 1")
    EntityRelation selectByParentField(@Param("parentEntityId") String parentEntityId, @Param("parentFieldCode") String parentFieldCode);

    /**
     * 根据父实体 ID 与绑定引用（bindingRef）查询已启用的关系。
     * bindingRef 可匹配 relation_code 或 parent_field_code，relation_code 优先。
     *
     * @param parentEntityId 父实体 ID
     * @param bindingRef     绑定引用（关系编码或字段编码）
     * @return 匹配的关系记录，无则返回 null
     */
    @Select("""
            SELECT *
            FROM entity_relation
            WHERE parent_entity_id = #{parentEntityId}
              AND enabled = 1
              AND deleted = 0
              AND (relation_code = #{bindingRef}
                   OR parent_field_code = #{bindingRef})
            ORDER BY CASE WHEN relation_code = #{bindingRef} THEN 0 ELSE 1 END
            LIMIT 1
            """)
    EntityRelation selectActiveByBindingRef(
            @Param("parentEntityId") String parentEntityId,
            @Param("bindingRef") String bindingRef);

    /**
     * 根据父实体 ID 物理删除所有关系（用于批量保存前清理旧数据）。
     *
     * @param parentEntityId 父实体 ID
     */
    @Delete("DELETE FROM entity_relation WHERE parent_entity_id = #{parentEntityId}")
    void deleteByParentEntityId(@Param("parentEntityId") String parentEntityId);
}
