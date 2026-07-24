package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.EntityFormNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体表单节点 Mapper
 * 
 * 提供按表单 ID 查询节点列表、按表单 ID 与节点 key 查询活跃节点、按父节点查询同级节点的能力。
 */
@Mapper
public interface EntityFormNodeMapper extends BaseMapper<EntityFormNode> {

    /**
     * 根据表单 ID 查询未删除的节点列表，按 parent_id、order_key、create_time 排序。
     *
     * @param formId 表单 ID
     * @return 节点列表
     */
    @Select("SELECT * FROM entity_form_node "
            + "WHERE form_id = #{formId} AND deleted = 0 "
            + "ORDER BY COALESCE(parent_id, ''), order_key, create_time")
    List<EntityFormNode> findByFormId(@Param("formId") String formId);

    /**
     * 根据表单 ID 与节点 key 查询最新的活跃节点（取更新时间最新的一条）。
     *
     * @param formId  表单 ID
     * @param nodeKey 节点 key
     * @return 匹配的节点，无匹配时返回 null
     */
    @Select("SELECT * FROM entity_form_node "
            + "WHERE form_id = #{formId} AND node_key = #{nodeKey} "
            + "AND deleted = 0 "
            + "ORDER BY update_time DESC, create_time DESC, id DESC "
            + "LIMIT 1")
    EntityFormNode findActiveByFormIdAndNodeKey(
            @Param("formId") String formId,
            @Param("nodeKey") String nodeKey);

    /**
     * 查询指定父节点下的同级节点列表，parentId 为 null 时查询顶层节点。
     *
     * @param formId   表单 ID
     * @param parentId 父节点 ID，可为 null
     * @return 同级节点列表
     */
    @Select("SELECT * FROM entity_form_node "
            + "WHERE form_id = #{formId} AND "
            + "((parent_id IS NULL AND #{parentId} IS NULL) OR parent_id = #{parentId}) "
            + "AND deleted = 0 ORDER BY order_key, create_time")
    List<EntityFormNode> findSiblings(
            @Param("formId") String formId,
            @Param("parentId") String parentId);
}
