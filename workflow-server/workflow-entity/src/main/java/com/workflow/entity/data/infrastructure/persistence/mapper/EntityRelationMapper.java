package com.workflow.entity.data.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.data.infrastructure.persistence.record.EntityRelation;
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
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface EntityRelationMapper extends BaseMapper<EntityRelation> {

    /**
     * 根据父实体 ID 查询已启用且未删除的关系列表，按 sort_order、create_time 排序。
     *
     * @param parentEntityId 父实体 ID
     * @return 关系列表
     */
    default List<EntityRelation> selectByParentEntityId(String parentEntityId) {
        return selectList(Wrappers.<EntityRelation>lambdaQuery()
                .eq(EntityRelation::getParentEntityId, parentEntityId)
                .eq(EntityRelation::getEnabled, 1)
                .orderByAsc(EntityRelation::getSortOrder)
                .orderByAsc(EntityRelation::getCreatedAt));
    }

    /**
     * 根据父实体编码查询已启用且未删除的关系列表。
     *
     * @param parentEntityCode 父实体编码
     * @return 关系列表
     */
    default List<EntityRelation> selectByParentEntityCode(String parentEntityCode) {
        return selectList(Wrappers.<EntityRelation>lambdaQuery()
                .eq(EntityRelation::getParentEntityCode, parentEntityCode)
                .eq(EntityRelation::getEnabled, 1)
                .orderByAsc(EntityRelation::getSortOrder)
                .orderByAsc(EntityRelation::getCreatedAt));
    }

    /**
     * 根据父实体 ID 查询全部未删除关系（包含禁用草稿）。
     *
     * @param parentEntityId 父级实体ID，后续用于查询全部父级实体ID时定位或关联目标
     * @return 实体关系集合，供调用方遍历或展示
     */
    default List<EntityRelation> selectAllByParentEntityId(String parentEntityId) {
        return selectList(Wrappers.<EntityRelation>lambdaQuery()
                .eq(EntityRelation::getParentEntityId, parentEntityId)
                .orderByAsc(EntityRelation::getSortOrder)
                .orderByAsc(EntityRelation::getCreatedAt));
    }

    /**
     * 页面可反向引用既有关系，关系本身仍只在父实体定义一次。
     *
     * @param entityId 实体ID，后续用于查询全部子级实体ID时定位或关联目标
     * @return 实体关系集合，供调用方遍历或展示
     */
    default List<EntityRelation> selectAllByChildEntityId(String entityId) {
        return selectList(Wrappers.<EntityRelation>lambdaQuery()
                .eq(EntityRelation::getChildEntityId, entityId)
                .orderByAsc(EntityRelation::getSortOrder)
                .orderByAsc(EntityRelation::getCreatedAt));
    }

    /**
     * 根据父实体 ID 和稳定关系编码查询关系（包含逻辑删除记录，防止稳定编码复用）。
     *
     * @param parentEntityId 父级实体ID，后续用于查询关系编码时定位或关联目标
     * @param relationCode 关系编码，后续用于查询关系编码时定位或关联目标
     * @return 查询后的关系编码结果，供调用方继续处理
     */
    default EntityRelation selectByRelationCode(String parentEntityId, String relationCode) {
        return selectByRelationCodeRows(new OffsetPage<>(0, 1), parentEntityId, relationCode);
    }

    /**
     * 复杂查询保留业务 SQL，行范围由 MyBatis-Plus 分页插件生成。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param parentEntityId 父级实体ID，后续用于查询关系编码行时定位或关联目标
     * @param relationCode 关系编码，后续用于查询关系编码行时定位或关联目标
     * @return 查询后的关系编码行结果，供调用方继续处理
     */
    @Select("<script> SELECT * FROM entity_relation WHERE parent_entity_id = #{parentEntityId} AND relation_code = #{relationCode}  </script>")
    EntityRelation selectByRelationCodeRows(
            @Param("page") com.baomidou.mybatisplus.core.metadata.IPage<?> page,
            @Param("parentEntityId") String parentEntityId,
            @Param("relationCode") String relationCode);

    /**
     * 根据父实体 ID 和聚合数据键查询关系（包含逻辑删除记录，防止稳定数据键复用）。
     *
     * @param parentEntityId 父级实体ID，后续用于查询数据键时定位或关联目标
     * @param dataKey 数据键，后续用于授权校验、关联或幂等去重
     * @return 查询后的数据键结果，供调用方继续处理
     */
    default EntityRelation selectByDataKey(String parentEntityId, String dataKey) {
        return selectByDataKeyRows(new OffsetPage<>(0, 1), parentEntityId, dataKey);
    }

    /**
     * 复杂查询保留业务 SQL，行范围由 MyBatis-Plus 分页插件生成。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param parentEntityId 父级实体ID，后续用于查询数据键行时定位或关联目标
     * @param dataKey 数据键，后续用于授权校验、关联或幂等去重
     * @return 查询后的数据键行结果，供调用方继续处理
     */
    @Select("<script> SELECT * FROM entity_relation WHERE parent_entity_id = #{parentEntityId} AND data_key = #{dataKey}  </script>")
    EntityRelation selectByDataKeyRows(
            @Param("page") com.baomidou.mybatisplus.core.metadata.IPage<?> page,
            @Param("parentEntityId") String parentEntityId,
            @Param("dataKey") String dataKey);

    /**
     * 根据父实体 ID 与父字段编码查询关系（含已禁用），取一条。
     *
     * @param parentEntityId   父实体 ID
     * @param parentFieldCode  父字段编码
     * @return 关系记录，无则返回 null
     */
    default EntityRelation selectByParentField(String parentEntityId, String parentFieldCode) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityRelation>lambdaQuery()
                .eq(EntityRelation::getParentEntityId, parentEntityId)
                .eq(EntityRelation::getParentFieldCode, parentFieldCode))
                .stream().findFirst().orElse(null);
    }

    /**
     * 根据父实体 ID 与绑定引用（bindingRef）查询已启用的关系。
     * bindingRef 可匹配 relation_code 或 parent_field_code，relation_code 优先。
     *
     * @param parentEntityId 父实体 ID
     * @param bindingRef     绑定引用（关系编码或字段编码）
     * @return 匹配的关系记录，无则返回 null
     */
    default EntityRelation selectActiveByBindingRef(String parentEntityId, String bindingRef) {
        return selectActiveByBindingRefRows(new OffsetPage<>(0, 1), parentEntityId, bindingRef);
    }

    /**
     * 复杂查询保留业务 SQL，行范围由 MyBatis-Plus 分页插件生成。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param parentEntityId 父级实体ID，后续用于查询活动绑定引用行时定位或关联目标
     * @param bindingRef 绑定引用，供本方法查询活动绑定引用行时使用
     * @return 查询后的活动绑定引用行结果，供调用方继续处理
     */
    @Select("""
            <script>
            SELECT *
            FROM entity_relation
            WHERE parent_entity_id = #{parentEntityId}
              AND enabled = 1
              AND deleted = 0
              AND (relation_code = #{bindingRef}
                   OR parent_field_code = #{bindingRef})
            ORDER BY CASE WHEN relation_code = #{bindingRef} THEN 0 ELSE 1 END

            </script>
            """)
    EntityRelation selectActiveByBindingRefRows(
            @Param("page") com.baomidou.mybatisplus.core.metadata.IPage<?> page,
            @Param("parentEntityId") String parentEntityId,
            @Param("bindingRef") String bindingRef);

    /**
     * 根据父实体 ID 物理删除所有关系（仅用于删除整个实体）。
     *
     * @param parentEntityId 父实体 ID
     */
    @Delete("DELETE FROM entity_relation WHERE parent_entity_id = #{parentEntityId}")
    void deleteByParentEntityId(@Param("parentEntityId") String parentEntityId);

    /**
     * 根据父实体 ID 与父字段编码物理删除关系。
     *
     * <p>仅保留给旧数据维护工具使用。实体字段保存不得再调用此方法。</p>
     *
     * @param parentEntityId  父实体 ID
     * @param parentFieldCode 父字段编码
     */
    @Delete("""
            DELETE FROM entity_relation
            WHERE parent_entity_id = #{parentEntityId}
              AND parent_field_code = #{parentFieldCode}
            """)
    void deleteByParentField(
            @Param("parentEntityId") String parentEntityId,
            @Param("parentFieldCode") String parentFieldCode);
}
