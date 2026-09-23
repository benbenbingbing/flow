package com.workflow.entity.definition.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
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
     */
    default List<EntityField> findByEntityId(String entityId) {
        return selectList(Wrappers.<EntityField>lambdaQuery()
                .eq(EntityField::getEntityId, entityId)
                .orderByAsc(EntityField::getSortOrder));
    }

    /**
     * 根据实体ID删除字段
     */
    default void deleteByEntityId(String entityId) {
        delete(Wrappers.<EntityField>lambdaQuery().eq(EntityField::getEntityId, entityId));
    }

    /**
     * 根据实体ID和字段编码查询字段
     */
    default EntityField findByEntityIdAndFieldCode(String entityId, String fieldCode) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityField>lambdaQuery()
                .eq(EntityField::getEntityId, entityId)
                .eq(EntityField::getFieldCode, fieldCode))
                .stream().findFirst().orElse(null);
    }

    /**
     * 根据字符串主键查询字段，使用实体声明的主键映射。
     */
    default EntityField findByIdString(String id) {
        return selectById(id);
    }
}
