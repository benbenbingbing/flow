package com.workflow.admin.identity.position.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.admin.identity.position.infrastructure.persistence.record.PositionDirectoryRevisionRow;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.identity.position.infrastructure.persistence.record.PositionAssignmentViewRow;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPositionAssignment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任职事实查询、有效区间校验和并发更新入口。
 */
// 搜索模式在 MyBatis 中组装并以 VARCHAR 绑定，保留通配符语义，避免数据库 CONCAT 差异。
@Mapper
public interface SysPositionAssignmentMapper
        extends BaseMapper<SysPositionAssignment> {

    String VIEW_COLUMNS = """
            SELECT assignment_record.id,
                   assignment_record.position_id,
                   position_record.position_code,
                   position_record.position_name,
                   assignment_record.organization_unit_id,
                   organization_unit.org_name AS organization_unit_name,
                   organization_unit.type AS organization_unit_type,
                   organization_unit.business_level_code,
                   assignment_record.user_id,
                   user_record.username,
                   user_record.nickname,
                   assignment_record.is_primary,
                   assignment_record.sort_order,
                   assignment_record.effective_from,
                   assignment_record.effective_to,
                   assignment_record.revoked_at,
                   assignment_record.revoked_by,
                   assignment_record.revoke_reason,
                   assignment_record.revision,
                   assignment_record.create_time,
                   assignment_record.update_time
            FROM sys_position_assignment assignment_record
            JOIN sys_position position_record
              ON position_record.id = assignment_record.position_id
            LEFT JOIN sys_organization organization_unit
              ON organization_unit.id = assignment_record.organization_unit_id
            LEFT JOIN sys_user user_record
              ON user_record.id = assignment_record.user_id
            """;

    /**
     * 查询更新；查询结果供调用方展示或继续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 查询后的更新结果，供调用方继续处理
     */
    @Select("SELECT * FROM sys_position_assignment WHERE id = #{id} FOR UPDATE")
    SysPositionAssignment selectForUpdate(@Param("id") String id);

    /**
     * 查询半开任职区间的重叠记录，开放结束时间沿用最大时间边界。
     * 日期列直接比较已绑定的时间，避免 COALESCE 混合日期和参数后按文本比较，误判首尾相接。
     * 对开放存量区间只在开始时间小于最大边界时放行，保持原有边界及 NULL 开始时间语义。
     *
     * @param positionId 位置ID，后续用于查询{@code overlaps}时定位或关联目标
     * @param unitId 单元ID，后续用于查询{@code overlaps}时定位或关联目标
     * @param effectiveFrom 有效起始，作为 {@code or} 的输入影响后续处理
     * @param effectiveTo 有效截止，供本方法查询{@code overlaps}时使用
     * @param excludeId 排除ID，后续用于查询{@code overlaps}时定位或关联目标
     * @return 系统位置分配集合，供调用方遍历或展示
     */
    default List<SysPositionAssignment> selectOverlaps(
            String positionId, String unitId, LocalDateTime effectiveFrom,
            LocalDateTime effectiveTo, String excludeId) {
        LocalDateTime maximum = LocalDateTime.of(9999, 12, 31, 23, 59, 59, 999999000);
        return selectList(Wrappers.<SysPositionAssignment>lambdaQuery()
                .eq(SysPositionAssignment::getPositionId, positionId)
                .eq(SysPositionAssignment::getOrganizationUnitId, unitId)
                .isNull(SysPositionAssignment::getRevokedAt)
                // 显式 TIMESTAMP 绑定保留微秒和 null 语义；不能用字符串 COALESCE 比较日期。
                .apply("effective_from < {0,jdbcType=TIMESTAMP}", effectiveTo == null ? maximum : effectiveTo)
                .and(end -> end.apply("effective_to > {0,jdbcType=TIMESTAMP}", effectiveFrom)
                        .or(effectiveFrom != null && effectiveFrom.isBefore(maximum),
                                open -> open.isNull(SysPositionAssignment::getEffectiveTo)))
                .ne(excludeId != null && !excludeId.isEmpty(), SysPositionAssignment::getId, excludeId)
                .orderByAsc(SysPositionAssignment::getEffectiveFrom, SysPositionAssignment::getId));
    }

    /**
     * 读取职务全部未撤销任职，后续区间调整以组织和时间的稳定顺序处理。
     *
     * @param positionId 位置ID，后续用于查询非已撤销位置时定位或关联目标
     * @return 系统位置分配集合，供调用方遍历或展示
     */
    default List<SysPositionAssignment> selectNonRevokedByPosition(String positionId) {
        return selectList(Wrappers.<SysPositionAssignment>lambdaQuery()
                .eq(SysPositionAssignment::getPositionId, positionId)
                .isNull(SysPositionAssignment::getRevokedAt)
                .orderByAsc(SysPositionAssignment::getOrganizationUnitId,
                        SysPositionAssignment::getEffectiveFrom, SysPositionAssignment::getId));
    }

    /**
     * 查询分配分页；查询结果供调用方展示或继续处理。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param keyword 关键字，供本方法查询分配分页时使用
     * @param positionCode 位置编码，后续用于查询分配分页时定位或关联目标
     * @param unitId 单元ID，后续用于查询分配分页时定位或关联目标
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param activeOnly 活动仅，供本方法查询分配分页时使用
     * @param asOf {@code as}，供本方法查询分配分页时使用
     * @param scopeUnitIds 作用域单元ID 集合，供本方法查询分配分页时使用
     * @return 查询后的分配分页结果，供调用方继续处理
     */
    @Select("""
            <script>
            <bind name="_contains_keyword" value="keyword == null ? null : &quot;%&quot; + keyword + &quot;%&quot;"/>
            """ + VIEW_COLUMNS + """
            WHERE position_record.deleted = 0
            <if test="keyword != null and keyword != ''">
              AND (position_record.position_code LIKE #{_contains_keyword,jdbcType=VARCHAR}
                OR position_record.position_name LIKE #{_contains_keyword,jdbcType=VARCHAR}
                OR organization_unit.org_name LIKE #{_contains_keyword,jdbcType=VARCHAR}
                OR user_record.username LIKE #{_contains_keyword,jdbcType=VARCHAR}
                OR user_record.nickname LIKE #{_contains_keyword,jdbcType=VARCHAR})
            </if>
            <if test="positionCode != null and positionCode != ''">
              AND position_record.position_code = #{positionCode}
            </if>
            <if test="unitId != null and unitId != ''">
              AND assignment_record.organization_unit_id = #{unitId}
            </if>
            <if test="userId != null and userId != ''">
              AND assignment_record.user_id = #{userId}
            </if>
            <if test="activeOnly != null and activeOnly">
              AND assignment_record.revoked_at IS NULL
              AND assignment_record.effective_from &lt;= #{asOf}
              AND (assignment_record.effective_to IS NULL
                OR assignment_record.effective_to &gt; #{asOf})
            </if>
            <if test="scopeUnitIds != null">
              <choose>
                <when test="scopeUnitIds.size() &gt; 0">
                  AND assignment_record.organization_unit_id IN
                  <foreach collection="scopeUnitIds" item="scopeUnitId" open="(" separator="," close=")">
                    #{scopeUnitId}
                  </foreach>
                </when>
                <otherwise>AND 1 = 0</otherwise>
              </choose>
            </if>
            ORDER BY assignment_record.create_time DESC, assignment_record.id DESC
            </script>
            """)
    Page<PositionAssignmentViewRow> selectAssignmentPage(
            Page<PositionAssignmentViewRow> page,
            @Param("keyword") String keyword,
            @Param("positionCode") String positionCode,
            @Param("unitId") String unitId,
            @Param("userId") String userId,
            @Param("activeOnly") Boolean activeOnly,
            @Param("asOf") LocalDateTime asOf,
            @Param("scopeUnitIds") List<String> scopeUnitIds);

    /**
     * 查询行单元；查询结果供调用方展示或继续处理。
     *
     * @param unitId 单元ID，后续用于查询行单元时定位或关联目标
     * @return 位置分配视图行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            """ + VIEW_COLUMNS + """
            WHERE assignment_record.organization_unit_id = #{unitId}
              AND position_record.deleted = 0
            ORDER BY position_record.sort_order,
                     assignment_record.effective_from DESC,
                     assignment_record.id
            </script>
            """)
    List<PositionAssignmentViewRow> selectRowsByUnit(
            @Param("unitId") String unitId);

    /**
     * 查询行用户；查询结果供调用方展示或继续处理。
     *
     * @param userId 用户身份 ID，后续用于权限判断、目标分配或操作记录
     * @param scopeUnitIds 作用域单元ID 集合，供本方法查询行用户时使用
     * @return 位置分配视图行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            """ + VIEW_COLUMNS + """
            WHERE assignment_record.user_id = #{userId}
              AND position_record.deleted = 0
            <if test="scopeUnitIds != null">
              <choose>
                <when test="scopeUnitIds.size() &gt; 0">
                  AND assignment_record.organization_unit_id IN
                  <foreach collection="scopeUnitIds" item="scopeUnitId" open="(" separator="," close=")">
                    #{scopeUnitId}
                  </foreach>
                </when>
                <otherwise>AND 1 = 0</otherwise>
              </choose>
            </if>
            ORDER BY assignment_record.effective_from DESC, assignment_record.id
            </script>
            """)
    List<PositionAssignmentViewRow> selectRowsByUser(
            @Param("userId") String userId,
            @Param("scopeUnitIds") List<String> scopeUnitIds);

    /**
     * 查询当前行用户集合；查询结果供调用方展示或继续处理。
     *
     * @param userIds 用户ID 集合，供本方法查询当前行用户集合时使用
     * @param asOf {@code as}，供本方法查询当前行用户集合时使用
     * @param scopeUnitIds 作用域单元ID 集合，供本方法查询当前行用户集合时使用
     * @return 位置分配视图行集合，供调用方遍历或展示
     */
    @Select("""
            <script>
            """ + VIEW_COLUMNS + """
            WHERE assignment_record.user_id IN
              <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
              </foreach>
              AND position_record.deleted = 0
              AND position_record.status = 'ENABLED'
              AND assignment_record.revoked_at IS NULL
              AND assignment_record.effective_from &lt;= #{asOf}
              AND (assignment_record.effective_to IS NULL
                OR assignment_record.effective_to &gt; #{asOf})
            <if test="scopeUnitIds != null">
              <choose>
                <when test="scopeUnitIds.size() &gt; 0">
                  AND assignment_record.organization_unit_id IN
                  <foreach collection="scopeUnitIds" item="scopeUnitId" open="(" separator="," close=")">
                    #{scopeUnitId}
                  </foreach>
                </when>
                <otherwise>AND 1 = 0</otherwise>
              </choose>
            </if>
            ORDER BY assignment_record.user_id, assignment_record.is_primary DESC,
                     assignment_record.sort_order, position_record.sort_order
            </script>
            """)
    List<PositionAssignmentViewRow> selectCurrentRowsByUsers(
            @Param("userIds") List<String> userIds,
            @Param("asOf") LocalDateTime asOf,
            @Param("scopeUnitIds") List<String> scopeUnitIds);

    /**
     * 查询有效行；查询结果供调用方展示或继续处理。
     *
     * @param positionCode 位置编码，后续用于查询有效行时定位或关联目标
     * @param unitId 单元ID，后续用于查询有效行时定位或关联目标
     * @param asOf {@code as}，供本方法查询有效行时使用
     * @return 位置分配视图行集合，供调用方遍历或展示
     */
    @Select("""
            SELECT assignment_record.id,
                   assignment_record.position_id,
                   position_record.position_code,
                   position_record.position_name,
                   assignment_record.organization_unit_id,
                   organization_unit.org_name AS organization_unit_name,
                   organization_unit.type AS organization_unit_type,
                   organization_unit.business_level_code,
                   assignment_record.user_id,
                   user_record.username,
                   user_record.nickname,
                   assignment_record.is_primary,
                   assignment_record.sort_order,
                   assignment_record.effective_from,
                   assignment_record.effective_to,
                   assignment_record.revoked_at,
                   assignment_record.revoked_by,
                   assignment_record.revoke_reason,
                   assignment_record.revision,
                   assignment_record.create_time,
                   assignment_record.update_time
            FROM sys_position_assignment assignment_record
            JOIN sys_position position_record
              ON position_record.id = assignment_record.position_id
            JOIN sys_organization organization_unit
              ON organization_unit.id = assignment_record.organization_unit_id
            JOIN sys_user user_record
              ON user_record.id = assignment_record.user_id
            WHERE position_record.position_code = #{positionCode}
              AND position_record.status = 'ENABLED'
              AND position_record.deleted = 0
              AND organization_unit.id = #{unitId}
              AND organization_unit.status = '0'
              AND organization_unit.deleted = 0
              AND user_record.status = '0'
              AND user_record.deleted = 0
              AND assignment_record.revoked_at IS NULL
              AND assignment_record.effective_from <= #{asOf}
              AND (assignment_record.effective_to IS NULL
                OR assignment_record.effective_to > #{asOf})
            ORDER BY assignment_record.is_primary DESC,
                     assignment_record.sort_order,
                     assignment_record.effective_from,
                     assignment_record.user_id
            """)
    List<PositionAssignmentViewRow> selectEffectiveRows(
            @Param("positionCode") String positionCode,
            @Param("unitId") String unitId,
            @Param("asOf") LocalDateTime asOf);

    /**
     * 同一次聚合读取全部版本事实，避免分次查询引入不一致的版本标识。
     *
     * @param positionId 位置ID，后续用于查询目录修订版本状态时定位或关联目标
     * @param unitId 单元ID，后续用于查询目录修订版本状态时定位或关联目标
     * @return 查询后的目录修订版本状态结果，供调用方继续处理
     */
    @Select("""
            SELECT position_record.revision AS position_revision,
              organization_unit.update_time AS organization_updated_at,
              COUNT(assignment_record.id) AS assignment_count,
              COALESCE(MAX(assignment_record.revision), 0) AS assignment_revision,
              MAX(assignment_record.update_time) AS assignment_updated_at
            FROM sys_position position_record
            JOIN sys_organization organization_unit
              ON organization_unit.id = #{unitId}
            LEFT JOIN sys_position_assignment assignment_record
              ON assignment_record.position_id = position_record.id
             AND assignment_record.organization_unit_id = organization_unit.id
            WHERE position_record.id = #{positionId}
            GROUP BY position_record.revision, organization_unit.update_time
            """)
    PositionDirectoryRevisionRow selectDirectoryRevisionState(
            @Param("positionId") String positionId,
            @Param("unitId") String unitId);

    /**
     * 对外保留目录版本字符串，数据库只读取事实，格式由目录协议统一定义。
     *
     * @param positionId 位置ID，后续用于查询目录修订版本时定位或关联目标
     * @param unitId 单元ID，后续用于查询目录修订版本时定位或关联目标
     * @return 查询后的目录修订版本文本，供调用方比较或展示
     */
    default String selectDirectoryRevision(String positionId, String unitId) {
        PositionDirectoryRevisionRow row = selectDirectoryRevisionState(positionId, unitId);
        return row == null ? null : row.toRevisionToken();
    }

    /**
     * 查询{@code leader}投影单元ID 集合；查询结果供调用方展示或继续处理。
     *
     * @return 系统位置分配集合，供调用方遍历或展示
     */
    @Select("""
            SELECT DISTINCT organization_unit.id
            FROM sys_organization organization_unit
            LEFT JOIN sys_position_assignment assignment_record
              ON assignment_record.organization_unit_id = organization_unit.id
            LEFT JOIN sys_position position_record
              ON position_record.id = assignment_record.position_id
             AND position_record.position_code = 'UNIT_LEADER'
            WHERE organization_unit.deleted = 0
              AND (organization_unit.leader_id IS NOT NULL
                OR position_record.id IS NOT NULL)
            ORDER BY organization_unit.id
            """)
    List<String> selectLeaderProjectionUnitIds();

    /**
     * 处理关闭时间，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param effectiveTo 有效截止，供本方法处理关闭时间时使用
     * @param actor 操作人，供本方法处理关闭时间时使用
     * @param expectedRevision 预期修订版本，供本方法处理关闭时间时使用
     * @return 处理后的关闭时间结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE sys_position_assignment
            SET effective_to = #{effectiveTo}, updated_by = #{actor},
                revision = revision + 1, update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id} AND revision = #{expectedRevision}
            </script>
            """)
    int closeAt(
            @Param("id") String id,
            @Param("effectiveTo") LocalDateTime effectiveTo,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);

    /**
     * 撤销系统位置分配；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param revokedAt 已撤销时间，后续用于判断有效期或展示该事件的发生时间
     * @param effectiveTo 有效截止，供本方法撤销系统位置分配时使用
     * @param reason 原因，供本方法撤销系统位置分配时使用
     * @param actor 操作人，供本方法撤销系统位置分配时使用
     * @param expectedRevision 预期修订版本，供本方法撤销系统位置分配时使用
     * @return 撤销后的系统位置分配结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE sys_position_assignment
            SET revoked_at = #{revokedAt}, revoked_by = #{actor},
                revoke_reason = #{reason}, effective_to = #{effectiveTo},
                updated_by = #{actor}, revision = revision + 1,
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id} AND revision = #{expectedRevision}
              AND revoked_at IS NULL
            </script>
            """)
    int revoke(
            @Param("id") String id,
            @Param("revokedAt") LocalDateTime revokedAt,
            @Param("effectiveTo") LocalDateTime effectiveTo,
            @Param("reason") String reason,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);

    /**
     * 更新时段；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param effectiveFrom 有效起始，供本方法更新时段时使用
     * @param effectiveTo 有效截止，供本方法更新时段时使用
     * @param actor 操作人，供本方法更新时段时使用
     * @param expectedRevision 预期修订版本，供本方法更新时段时使用
     * @return 更新后的时段结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE sys_position_assignment
            SET effective_from = #{effectiveFrom}, effective_to = #{effectiveTo},
                updated_by = #{actor}, revision = revision + 1,
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id} AND revision = #{expectedRevision}
              AND revoked_at IS NULL
            </script>
            """)
    int updatePeriod(
            @Param("id") String id,
            @Param("effectiveFrom") LocalDateTime effectiveFrom,
            @Param("effectiveTo") LocalDateTime effectiveTo,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);

    /**
     * 历史任职也阻止删除职务定义，因此此统计不排除已撤销或已结束的任职。
     *
     * @param positionId 位置ID，后续用于统计分配集合时定位或关联目标
     * @return 符合条件的分配集合数量
     */
    default long countAssignments(String positionId) {
        return selectCount(Wrappers.<SysPositionAssignment>lambdaQuery()
                .eq(SysPositionAssignment::getPositionId, positionId));
    }

    /**
     * 统计调用方给定时点与组织范围内的有效任职，供职务列表展示。
     * 使用半开时间区间；unitIds 为 null 表示全部组织，空集合表示没有可见组织。
     *
     * @param positionId 位置ID，后续用于统计当前分配集合时定位或关联目标
     * @param asOf {@code as}，作为 {@code le} 的输入影响后续处理
     * @param unitIds 单元ID 集合，作为 {@code in} 的输入影响后续处理
     * @return 符合条件的当前分配集合数量
     */
    default long countCurrentAssignments(String positionId, LocalDateTime asOf, List<String> unitIds) {
        // 空权限范围不能省略 IN 条件，否则会扩大可见任职计数。
        if (unitIds != null && unitIds.isEmpty()) {
            return 0;
        }
        return selectCount(Wrappers.<SysPositionAssignment>lambdaQuery()
                .eq(SysPositionAssignment::getPositionId, positionId)
                .isNull(SysPositionAssignment::getRevokedAt)
                .le(SysPositionAssignment::getEffectiveFrom, asOf)
                .and(end -> end.isNull(SysPositionAssignment::getEffectiveTo)
                        .or().gt(SysPositionAssignment::getEffectiveTo, asOf))
                .in(unitIds != null, SysPositionAssignment::getOrganizationUnitId, unitIds));
    }
}
