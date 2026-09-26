package com.workflow.entity.permission.infrastructure.persistence.mapper;

import java.util.List;
import com.workflow.core.database.mybatis.OffsetPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopeRelease;
import org.apache.ibatis.annotations.Mapper;

/**
 * 实体列表数据范围发布版本 Mapper
 *
 * 提供按实体编码查询活跃数据范围版本、查询最大版本号、停用当前活跃版本的能力。
 */
// 普通查询使用 Wrapper；分页由 MyBatis-Plus 生成对应数据库语法。
@Mapper
public interface EntityListScopeReleaseMapper extends BaseMapper<EntityListScopeRelease> {

    /**
     * 根据实体编码查询当前活跃（ACTIVE）的数据范围发布版本，取版本号最大的一条。
     *
     * @param entityCode 实体编码
     * @return 活跃发布版本，无则返回 null
     */
    default EntityListScopeRelease findActive(String entityCode) {
        return selectList(new OffsetPage<>(0, 1), Wrappers.<EntityListScopeRelease>lambdaQuery()
                .eq(EntityListScopeRelease::getEntityCode, entityCode)
                .eq(EntityListScopeRelease::getStatus, "ACTIVE")
                .orderByDesc(EntityListScopeRelease::getVersion))
                .stream().findFirst().orElse(null);
    }

    /**
     * 查询指定实体的最大发布版本号，无记录时返回 0。
     *
     * @param entityCode 实体编码
     * @return 最大版本号
     */
    default int findMaxVersion(String entityCode) {
        List<Object> values = selectObjs(Wrappers.<EntityListScopeRelease>query()
                .select("MAX(version)")
                .eq("entity_code", entityCode));
        return values.isEmpty() || values.get(0) == null ? 0 : ((Number) values.get(0)).intValue();
    }

    /**
     * 将指定实体的活跃发布版本置为 INACTIVE（用于发布新版本前停用旧版本）。
     *
     * @param entityCode 实体编码
     * @return 影响行数
     */
    default int deactivate(String entityCode) {
        return update(null, Wrappers.<EntityListScopeRelease>lambdaUpdate()
                .eq(EntityListScopeRelease::getEntityCode, entityCode)
                .eq(EntityListScopeRelease::getStatus, "ACTIVE")
                .set(EntityListScopeRelease::getStatus, "INACTIVE"));
    }
}
