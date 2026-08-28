package com.workflow.admin.identity.position.infrastructure.persistence.mapper;

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

    @Select("SELECT * FROM sys_position_assignment WHERE id = #{id} FOR UPDATE")
    SysPositionAssignment selectForUpdate(@Param("id") String id);

    @Select("""
            <script>
            SELECT * FROM sys_position_assignment
            WHERE position_id = #{positionId}
              AND organization_unit_id = #{unitId}
              AND revoked_at IS NULL
              AND effective_from &lt; COALESCE(#{effectiveTo}, '9999-12-31 23:59:59.999999')
              AND COALESCE(effective_to, '9999-12-31 23:59:59.999999') &gt; #{effectiveFrom}
            <if test="excludeId != null and excludeId != ''">
              AND id &lt;&gt; #{excludeId}
            </if>
            ORDER BY effective_from, id
            </script>
            """)
    List<SysPositionAssignment> selectOverlaps(
            @Param("positionId") String positionId,
            @Param("unitId") String unitId,
            @Param("effectiveFrom") LocalDateTime effectiveFrom,
            @Param("effectiveTo") LocalDateTime effectiveTo,
            @Param("excludeId") String excludeId);

    @Select("""
            <script>
            SELECT assignment_record.*
            FROM sys_position_assignment assignment_record
            WHERE assignment_record.position_id = #{positionId}
              AND assignment_record.revoked_at IS NULL
            ORDER BY assignment_record.organization_unit_id,
                     assignment_record.effective_from,
                     assignment_record.id
            </script>
            """)
    List<SysPositionAssignment> selectNonRevokedByPosition(
            @Param("positionId") String positionId);

    @Select("""
            <script>
            """ + VIEW_COLUMNS + """
            WHERE position_record.deleted = 0
            <if test="keyword != null and keyword != ''">
              AND (position_record.position_code LIKE CONCAT('%', #{keyword}, '%')
                OR position_record.position_name LIKE CONCAT('%', #{keyword}, '%')
                OR organization_unit.org_name LIKE CONCAT('%', #{keyword}, '%')
                OR user_record.username LIKE CONCAT('%', #{keyword}, '%')
                OR user_record.nickname LIKE CONCAT('%', #{keyword}, '%'))
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

    @Select("""
            SELECT CONCAT(
              position_record.revision, ':',
              DATE_FORMAT(organization_unit.update_time, '%Y%m%d%H%i%s.%f'), ':',
              COUNT(assignment_record.id), ':',
              COALESCE(MAX(assignment_record.revision), 0), ':',
              COALESCE(DATE_FORMAT(MAX(assignment_record.update_time),
                '%Y%m%d%H%i%s.%f'), '0'))
            FROM sys_position position_record
            JOIN sys_organization organization_unit
              ON organization_unit.id = #{unitId}
            LEFT JOIN sys_position_assignment assignment_record
              ON assignment_record.position_id = position_record.id
             AND assignment_record.organization_unit_id = organization_unit.id
            WHERE position_record.id = #{positionId}
            GROUP BY position_record.revision, organization_unit.update_time
            """)
    String selectDirectoryRevision(
            @Param("positionId") String positionId,
            @Param("unitId") String unitId);

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

    @Update("""
            UPDATE sys_position_assignment
            SET effective_to = #{effectiveTo}, updated_by = #{actor},
                revision = revision + 1, update_time = UTC_TIMESTAMP(6)
            WHERE id = #{id} AND revision = #{expectedRevision}
            """)
    int closeAt(
            @Param("id") String id,
            @Param("effectiveTo") LocalDateTime effectiveTo,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);

    @Update("""
            UPDATE sys_position_assignment
            SET revoked_at = #{revokedAt}, revoked_by = #{actor},
                revoke_reason = #{reason}, effective_to = #{effectiveTo},
                updated_by = #{actor}, revision = revision + 1,
                update_time = UTC_TIMESTAMP(6)
            WHERE id = #{id} AND revision = #{expectedRevision}
              AND revoked_at IS NULL
            """)
    int revoke(
            @Param("id") String id,
            @Param("revokedAt") LocalDateTime revokedAt,
            @Param("effectiveTo") LocalDateTime effectiveTo,
            @Param("reason") String reason,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);

    @Update("""
            UPDATE sys_position_assignment
            SET effective_from = #{effectiveFrom}, effective_to = #{effectiveTo},
                updated_by = #{actor}, revision = revision + 1,
                update_time = UTC_TIMESTAMP(6)
            WHERE id = #{id} AND revision = #{expectedRevision}
              AND revoked_at IS NULL
            """)
    int updatePeriod(
            @Param("id") String id,
            @Param("effectiveFrom") LocalDateTime effectiveFrom,
            @Param("effectiveTo") LocalDateTime effectiveTo,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);
}
