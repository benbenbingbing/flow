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

import java.util.List;

// 搜索模式在 MyBatis 中组装并以 VARCHAR 绑定，保留通配符语义，避免数据库 CONCAT 差异。
/** 当前关系查询；管理列表保留停用对象便于清理，审批查询仅返回启用对象。 */
@Mapper
public interface SysPositionAssignmentMapper
        extends BaseMapper<SysPositionAssignment> {

    /**
     * 集合替换必须使用锁定读：MySQL REPEATABLE_READ 下，先前的职务查询可能已建立旧快照。
     * 仅锁职务和组织而继续普通 SELECT，会在等待并发事务后仍读到过期人员。
     */
    @Select("SELECT * FROM sys_position_assignment WHERE position_id = #{positionId} "
            + "AND organization_unit_id = #{unitId} ORDER BY user_id FOR UPDATE")
    List<SysPositionAssignment> selectMembersForUpdate(@Param("positionId") String positionId,
                                                       @Param("unitId") String unitId);

    /** 删除前锁定读取最新关系，与集合替换共用职务、组织、关系的锁顺序。 */
    @Select("SELECT * FROM sys_position_assignment WHERE id = #{id} FOR UPDATE")
    SysPositionAssignment selectMemberForUpdate(@Param("id") String id);

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
            @Param("scopeUnitIds") List<String> scopeUnitIds);

    @Select("""
            <script>
            """ + VIEW_COLUMNS + """
            WHERE assignment_record.organization_unit_id = #{unitId}
              AND position_record.deleted = 0
            ORDER BY position_record.sort_order,
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
            ORDER BY assignment_record.id
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
            ORDER BY assignment_record.user_id, position_record.sort_order, assignment_record.id
            </script>
            """)
    List<PositionAssignmentViewRow> selectCurrentRowsByUsers(
            @Param("userIds") List<String> userIds,
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
            ORDER BY assignment_record.user_id
            """)
    List<PositionAssignmentViewRow> selectCurrentHolders(
            @Param("positionCode") String positionCode,
            @Param("unitId") String unitId);

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

    default String selectDirectoryRevision(String positionId, String unitId) {
        PositionDirectoryRevisionRow row = selectDirectoryRevisionState(positionId, unitId);
        return row == null ? null : row.toRevisionToken();
    }

    /** 当前关联仍阻止删除职务定义，避免产生悬空引用。 */
    default long countAssignments(String positionId) {
        return selectCount(Wrappers.<SysPositionAssignment>lambdaQuery()
                .eq(SysPositionAssignment::getPositionId, positionId));
    }

    /** null 为全部组织，空列表必须返回零，不能扩大管理员的数据范围。 */
    default long countCurrentAssignments(String positionId, List<String> unitIds) {
        // 空权限范围不能省略 IN 条件，否则会扩大可见任职计数。
        if (unitIds != null && unitIds.isEmpty()) {
            return 0;
        }
        return selectCount(Wrappers.<SysPositionAssignment>lambdaQuery()
                .eq(SysPositionAssignment::getPositionId, positionId)
                .in(unitIds != null, SysPositionAssignment::getOrganizationUnitId, unitIds));
    }
}
