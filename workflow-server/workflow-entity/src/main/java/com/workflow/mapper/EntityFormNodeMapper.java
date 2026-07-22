package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityFormNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EntityFormNodeMapper extends BaseMapper<EntityFormNode> {

    @Select("SELECT * FROM entity_form_node "
            + "WHERE form_id = #{formId} AND deleted = 0 "
            + "ORDER BY COALESCE(parent_id, ''), order_key, create_time")
    List<EntityFormNode> findByFormId(@Param("formId") String formId);

    @Select("SELECT * FROM entity_form_node "
            + "WHERE form_id = #{formId} AND node_key = #{nodeKey} "
            + "AND deleted = 0 "
            + "ORDER BY update_time DESC, create_time DESC, id DESC "
            + "LIMIT 1")
    EntityFormNode findActiveByFormIdAndNodeKey(
            @Param("formId") String formId,
            @Param("nodeKey") String nodeKey);

    @Select("SELECT * FROM entity_form_node "
            + "WHERE form_id = #{formId} AND "
            + "((parent_id IS NULL AND #{parentId} IS NULL) OR parent_id = #{parentId}) "
            + "AND deleted = 0 ORDER BY order_key, create_time")
    List<EntityFormNode> findSiblings(
            @Param("formId") String formId,
            @Param("parentId") String parentId);
}
