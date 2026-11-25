package com.workflow.entity.definition.infrastructure.persistence.mapper;

import com.workflow.core.database.mybatis.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityField;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 实体字段 Mapper
 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface EntityFieldMapper extends BaseMapper<EntityField> {

    /**
     * 根据实体ID查询字段列表
     *
     * @param entityId 实体ID，后续用于查询实体ID时定位或关联目标
     * @return 实体字段集合，供调用方遍历或展示
     */
    default List<EntityField> findByEntityId(String entityId) {
        return selectList(Wrappers.<EntityField>lambdaQuery()
                .eq(EntityField::getEntityId, entityId)
                .orderByAsc(EntityField::getSortOrder));
    }

    /**
     * 在实体行锁内读取当前字段版本，供全量更新校验发布状态。
     * 普通查询在可重复读事务中可能沿用等待实体行锁之前的快照。
     *
     * @param entityId 已锁定的实体 ID
     * @return 参与本次更新的字段定义
     */
    default List<EntityField> findByEntityIdForUpdate(String entityId) {
        return selectList(Wrappers.<EntityField>lambdaQuery()
                .eq(EntityField::getEntityId, entityId)
                .last("FOR UPDATE"));
    }

    /**
     * 在实体行锁内读取待删除字段的当前版本，避免使用旧的字段发布状态。
     *
     * @param fieldId 待删除的字段 ID
     * @return 当前字段定义，已删除时返回 null
     */
    default EntityField findByIdForUpdate(String fieldId) {
        return selectOne(Wrappers.<EntityField>lambdaQuery()
                .eq(EntityField::getId, fieldId)
                .last("FOR UPDATE"));
    }

    /**
     * 根据实体ID删除字段
     *
     * @param entityId 实体ID，后续用于删除实体ID时定位或关联目标
     */
    default void deleteByEntityId(String entityId) {
        delete(Wrappers.<EntityField>lambdaQuery().eq(EntityField::getEntityId, entityId));
    }

    /**
     * 根据实体ID和字段编码查询字段
     *
     * @param entityId 实体ID，后续用于查询实体ID与字段编码时定位或关联目标
     * @param fieldCode 字段编码，后续用于查询实体ID与字段编码时定位或关联目标
     * @return 符合条件的实体字段结果，供调用方继续处理
     */
    default EntityField findByEntityIdAndFieldCode(String entityId, String fieldCode) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityField>lambdaQuery()
                .eq(EntityField::getEntityId, entityId)
                .eq(EntityField::getFieldCode, fieldCode))
                .stream().findFirst().orElse(null);
    }

    /**
     * 根据字符串主键查询字段，使用实体声明的主键映射。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 符合条件的实体字段结果，供调用方继续处理
     */
    default EntityField findByIdString(String id) {
        return selectById(id);
    }
}
