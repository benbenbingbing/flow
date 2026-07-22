package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityRelation;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityRelationMapper extends BaseMapper<EntityRelation> {

    @Select("SELECT * FROM entity_relation WHERE parent_entity_id = #{parentEntityId} AND enabled = 1 AND deleted = 0 ORDER BY sort_order ASC, create_time ASC")
    List<EntityRelation> selectByParentEntityId(@Param("parentEntityId") String parentEntityId);

    @Select("SELECT * FROM entity_relation WHERE parent_entity_code = #{parentEntityCode} AND enabled = 1 AND deleted = 0 ORDER BY sort_order ASC, create_time ASC")
    List<EntityRelation> selectByParentEntityCode(@Param("parentEntityCode") String parentEntityCode);

    @Select("SELECT * FROM entity_relation WHERE parent_entity_id = #{parentEntityId} AND parent_field_code = #{parentFieldCode} AND deleted = 0 LIMIT 1")
    EntityRelation selectByParentField(@Param("parentEntityId") String parentEntityId, @Param("parentFieldCode") String parentFieldCode);

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

    @Delete("DELETE FROM entity_relation WHERE parent_entity_id = #{parentEntityId}")
    void deleteByParentEntityId(@Param("parentEntityId") String parentEntityId);
}
