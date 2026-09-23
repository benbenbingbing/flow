package com.workflow.entity.form.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityFormNode;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Delete;

import java.util.List;

/**
 * 实体表单节点 Mapper
 *
 * 提供按表单 ID 查询节点列表、按表单 ID 与节点 key 查询活跃节点、按父节点查询同级节点的能力。
 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface EntityFormNodeMapper extends BaseMapper<EntityFormNode> {

    /**
     * 根据表单 ID 查询未删除的节点列表，按 parent_id、order_key、create_time 排序。
     *
     * @param formId 表单 ID
     * @return 节点列表
     */
    default List<EntityFormNode> findByFormId(String formId) {
        // 顶层节点的 NULL 父 ID 按空串归组；排序表达式固定在代码中，不能由请求提供。
        return selectList(Wrappers.<EntityFormNode>query()
                .eq("form_id", formId)
                .orderByAsc("COALESCE(parent_id, '')", "order_key", "create_time"));
    }

    /** 锁定表单下全部草稿节点，包含逻辑删除节点。 */
    @Select("SELECT * FROM entity_form_node "
            + "WHERE form_id = #{formId} ORDER BY id FOR UPDATE")
    List<EntityFormNode> findAllByFormIdForUpdate(
            @Param("formId") String formId);

    /** 物理清理表单草稿节点，供发布快照精确恢复稳定 ID。 */
    @Delete("DELETE FROM entity_form_node WHERE form_id = #{formId}")
    int deleteAllByFormIdForReleaseRestore(
            @Param("formId") String formId);

    /**
     * 根据表单 ID 与节点 key 查询最新的活跃节点（取更新时间最新的一条）。
     *
     * @param formId  表单 ID
     * @param nodeKey 节点 key
     * @return 匹配的节点，无匹配时返回 null
     */
    default EntityFormNode findActiveByFormIdAndNodeKey(String formId, String nodeKey) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityFormNode>lambdaQuery()
                .eq(EntityFormNode::getFormId, formId)
                .eq(EntityFormNode::getNodeKey, nodeKey)
                .orderByDesc(EntityFormNode::getUpdatedAt)
                .orderByDesc(EntityFormNode::getCreatedAt)
                .orderByDesc(EntityFormNode::getId))
                .stream().findFirst().orElse(null);
    }

    /**
     * 冲突后的当前读：活动节点的 (form_id, node_key) 唯一，不需要分页。
     * 绕过 MyBatis 缓存及旧事务快照；共享读避免 MySQL 重复插入后的锁升级死锁。
     */
    @Select("<script> SELECT * FROM entity_form_node "
            + "WHERE form_id = #{formId} AND node_key = #{nodeKey} AND deleted = 0 "
            + "${@com.workflow.integration.database.api.DatabaseQuerySql@readGuard(_databaseId)} </script>")
    @Options(useCache = false, flushCache = Options.FlushCachePolicy.TRUE)
    EntityFormNode findActiveByFormIdAndNodeKeyForConflict(
            @Param("formId") String formId,
            @Param("nodeKey") String nodeKey);

    /**
     * 查询指定父节点下的同级节点列表，parentId 为 null 时查询顶层节点。
     *
     * @param formId   表单 ID
     * @param parentId 父节点 ID，可为 null
     * @return 同级节点列表
     */
    default List<EntityFormNode> findSiblings(String formId, String parentId) {
        return selectList(Wrappers.<EntityFormNode>lambdaQuery()
                .eq(EntityFormNode::getFormId, formId)
                // 保留数据库对空串/NULL 的原有判定，Oracle 空串也按顶层节点处理。
                // 固定谓词通过 Wrapper 绑定值，不能在 Java 中仅按 null 分支改写。
                .apply("((parent_id IS NULL AND {0,jdbcType=VARCHAR} IS NULL) "
                        + "OR parent_id = {0,jdbcType=VARCHAR})", parentId)
                .orderByAsc(EntityFormNode::getOrderKey, EntityFormNode::getCreatedAt));
    }
}
