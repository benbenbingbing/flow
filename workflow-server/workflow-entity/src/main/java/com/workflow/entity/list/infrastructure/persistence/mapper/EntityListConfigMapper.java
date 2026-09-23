package com.workflow.entity.list.infrastructure.persistence.mapper;

import com.workflow.core.database.OffsetPage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体列表配置 Mapper
 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface EntityListConfigMapper extends BaseMapper<EntityListConfig> {

    /**
     * 根据实体ID查询列表配置
     *
     * @param entityId 实体ID，后续用于查询实体ID时定位或关联目标
     * @return 实体列表配置集合，供调用方遍历或展示
     */
    default List<EntityListConfig> findByEntityId(String entityId) {
        return selectList(Wrappers.<EntityListConfig>lambdaQuery()
                .eq(EntityListConfig::getEntityId, entityId)
                .orderByAsc(EntityListConfig::getCreatedAt));
    }

    /**
     * 根据实体ID和列表标识查询
     *
     * @param entityId 实体ID，后续用于查询实体ID与列表键时定位或关联目标
     * @param listKey 列表配置键，后续用于确定数据权限与展示字段范围
     * @return 符合条件的实体列表配置结果，供调用方继续处理
     */
    default EntityListConfig findByEntityIdAndListKey(String entityId, String listKey) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityListConfig>lambdaQuery()
                .eq(EntityListConfig::getEntityId, entityId)
                .eq(EntityListConfig::getListKey, listKey))
                .stream().findFirst().orElse(null);
    }

    /**
     * 根据实体编码和列表标识查询列表配置
     *
     * @param entityCode 实体编码
     * @param listKey    列表标识
     * @return 列表配置，无则返回 null
     */
    default EntityListConfig findByEntityCodeAndListKey(String entityCode, String listKey) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityListConfig>lambdaQuery()
                .eq(EntityListConfig::getEntityCode, entityCode)
                .eq(EntityListConfig::getListKey, listKey))
                .stream().findFirst().orElse(null);
    }

    /**
     * 根据实体编码查询全部列表配置，默认列表优先，按创建时间升序排列。
     *
     * @param entityCode 实体编码
     * @return 列表配置列表
     */
    default List<EntityListConfig> findByEntityCode(String entityCode) {
        return selectList(Wrappers.<EntityListConfig>lambdaQuery()
                .eq(EntityListConfig::getEntityCode, entityCode)
                .orderByDesc(EntityListConfig::getIsDefault)
                .orderByAsc(EntityListConfig::getCreatedAt));
    }

    /**
     * 根据主键 ID 加锁查询列表配置（FOR UPDATE），用于并发更新场景。
     *
     * @param id 主键 ID
     * @return 列表配置，无则返回 null
     */
    @Select("SELECT * FROM entity_list_config WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    EntityListConfig selectByIdForUpdate(@Param("id") String id);
}
