package com.workflow.entity.definition.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 实体状态 Mapper
 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface EntityStatusMapper extends BaseMapper<EntityStatus> {

    /**
     * 根据实体编码查询状态列表
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @return 实体状态集合，供调用方遍历或展示
     */
    default List<EntityStatus> findByEntityCode(String entityCode) {
        return selectList(Wrappers.<EntityStatus>lambdaQuery()
                .eq(EntityStatus::getEntityCode, entityCode)
                .eq(EntityStatus::getDeleted, 0)
                .orderByAsc(EntityStatus::getSortOrder));
    }

    /**
     * 根据实体编码和状态编码查询
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param statusCode 状态编码，后续用于查询实体与编码时定位或关联目标
     * @return 符合条件的实体状态结果，供调用方继续处理
     */
    default EntityStatus findByEntityAndCode(String entityCode, String statusCode) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityStatus>lambdaQuery()
                .eq(EntityStatus::getEntityCode, entityCode)
                .eq(EntityStatus::getStatusCode, statusCode)
                .eq(EntityStatus::getDeleted, 0))
                .stream().findFirst().orElse(null);
    }

    /**
     * 根据分类查询
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     * @param category 类别，决定后续状态或结果的归类
     * @return 实体状态集合，供调用方遍历或展示
     */
    default List<EntityStatus> findByCategory(String entityCode, String category) {
        return selectList(Wrappers.<EntityStatus>lambdaQuery()
                .eq(EntityStatus::getEntityCode, entityCode)
                .eq(EntityStatus::getStatusCategory, category)
                .eq(EntityStatus::getDeleted, 0)
                .orderByAsc(EntityStatus::getSortOrder));
    }

    /**
     * 物理删除指定实体的所有状态（用于批量保存前清理旧数据）
     *
     * @param entityCode 实体编码，用于限定后续数据读取、校验或写入的实体范围
     */
    @org.apache.ibatis.annotations.Delete("DELETE FROM entity_status WHERE entity_code = #{entityCode}")
    void physicalDeleteByEntityCode(@Param("entityCode") String entityCode);
}
