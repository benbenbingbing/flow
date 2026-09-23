package com.workflow.entity.permission.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Constants;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.entity.permission.infrastructure.persistence.record.EntityListScopeDelegation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 实体列表数据范围授权（代办/委托） Mapper
 *
 * 提供按被授权用户与实体编码查询当前有效的数据范围委托关系的能力。
 */
@Mapper
public interface EntityListScopeDelegationMapper extends BaseMapper<EntityListScopeDelegation> {

    /**
     * 查询指定被授权用户当前有效的数据范围委托列表。
     * 条件：已启用、未删除、未过期，且实体编码为空（全局）或与传入实体编码一致。
     *
     * @param toUserId   被授权用户 ID
     * @param entityCode 实体编码
     * @return 有效的委托列表
     */
    default List<EntityListScopeDelegation> findActiveByToUserId(String toUserId, String entityCode) {
        // 自定义时间查询仍使用 Wrapper 构造条件及字段投影；投影沿用实体别名映射。
        // 自定义语句不会自动追加逻辑删除，因此这里显式约束 deleted，避免历史委托重新生效。
        return selectActiveRows(Wrappers.<EntityListScopeDelegation>lambdaQuery()
                .select(EntityListScopeDelegation.class, field -> true)
                .eq(EntityListScopeDelegation::getToUserId, toUserId)
                .eq(EntityListScopeDelegation::getEnabled, 1)
                .eq(EntityListScopeDelegation::getDeleted, 0)
                .and(scope -> scope.isNull(EntityListScopeDelegation::getEntityCode)
                        .or().eq(EntityListScopeDelegation::getEntityCode, "")
                        .or().eq(EntityListScopeDelegation::getEntityCode, entityCode)));
    }

    /**
     * 执行当前有效期比较；Wrapper 仅由上面的业务方法在服务端构造，不接受请求中的 SQL。
     * 时间片段按当前 SqlSessionFactory 的 databaseId 选择，PostgreSQL 使用语句时间，
     * 避免长事务跨过委托起止边界后仍按事务开始时间授权；不把方言依赖扩散到权限引擎。
     */
    @Select("""
            <script>
            SELECT ${ew.sqlSelect} FROM entity_list_scope_delegation
            ${ew.customSqlSegment}
            AND (start_time IS NULL OR start_time &lt;= ${@com.workflow.integration.database.api.DatabaseRuntimeSql@currentNow(_databaseId)})
            AND (end_time IS NULL OR end_time >= ${@com.workflow.integration.database.api.DatabaseRuntimeSql@currentNow(_databaseId)})
            </script>
            """)
    List<EntityListScopeDelegation> selectActiveRows(
            @Param(Constants.WRAPPER) Wrapper<EntityListScopeDelegation> conditions);
}
