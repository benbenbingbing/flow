package com.workflow.admin.identity.position.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.admin.identity.position.infrastructure.persistence.record.SysPosition;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 职务定义持久化及稳定行锁入口。
 */
@Mapper
public interface SysPositionMapper extends BaseMapper<SysPosition> {

    @Select("SELECT * FROM sys_position WHERE position_code = #{code} AND deleted = 0 LIMIT 1")
    SysPosition selectByCode(@Param("code") String code);

    @Select("SELECT * FROM sys_position WHERE position_code = #{code} LIMIT 1")
    SysPosition selectAnyByCode(@Param("code") String code);

    @Select("""
            SELECT * FROM sys_position
            WHERE position_code = #{code} AND status = 'ENABLED' AND deleted = 0
            LIMIT 1
            """)
    SysPosition selectEnabledByCode(@Param("code") String code);

    @Select("""
            SELECT * FROM sys_position
            WHERE status = 'ENABLED' AND deleted = 0
              AND (#{unitType} IS NULL OR #{unitType} = ''
                OR applicable_unit_type = 'ANY'
                OR applicable_unit_type = #{unitType})
            ORDER BY sort_order, position_code
            """)
    List<SysPosition> selectEnabled(@Param("unitType") String unitType);

    @Select("""
            <script>
            SELECT * FROM sys_position
            WHERE position_code IN
            <foreach collection="codes" item="code" open="(" separator="," close=")">
              #{code}
            </foreach>
              AND deleted = 0
            ORDER BY id
            </script>
            """)
    List<SysPosition> selectByCodes(@Param("codes") List<String> codes);

    /**
     * 批量写事务必须先按职务 ID、再按组织 ID 的固定顺序加锁。
     */
    @Select("""
            <script>
            SELECT * FROM sys_position
            WHERE position_code IN
            <foreach collection="codes" item="code" open="(" separator="," close=")">
              #{code}
            </foreach>
              AND deleted = 0
            ORDER BY id
            FOR UPDATE
            </script>
            """)
    List<SysPosition> selectForUpdateByCodes(@Param("codes") List<String> codes);

    @Select("SELECT * FROM sys_position WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SysPosition selectForUpdate(@Param("id") String id);

    @Select("SELECT COUNT(*) FROM sys_position_assignment WHERE position_id = #{positionId}")
    long countAssignments(@Param("positionId") String positionId);

    @Select("""
            <script>
            SELECT COUNT(*) FROM sys_position_assignment assignment_record
            WHERE assignment_record.position_id = #{positionId}
              AND assignment_record.revoked_at IS NULL
              AND assignment_record.effective_from &lt;= #{asOf}
              AND (assignment_record.effective_to IS NULL
                OR assignment_record.effective_to &gt; #{asOf})
            <if test="unitIds != null">
              <choose>
                <when test="unitIds.size() &gt; 0">
                  AND assignment_record.organization_unit_id IN
                  <foreach collection="unitIds" item="unitId" open="(" separator="," close=")">
                    #{unitId}
                  </foreach>
                </when>
                <otherwise>AND 1 = 0</otherwise>
              </choose>
            </if>
            </script>
            """)
    long countCurrentAssignments(
            @Param("positionId") String positionId,
            @Param("asOf") LocalDateTime asOf,
            @Param("unitIds") List<String> unitIds);

    @Select("""
            SELECT
              (SELECT COUNT(*) FROM process_node_config
                WHERE deleted = 0
                  AND config_json LIKE CONCAT('%', #{positionCode}, '%'))
              +
              (SELECT COUNT(*) FROM process_definition_config
                WHERE deleted = 0
                  AND bpmn_xml LIKE CONCAT('%', #{positionCode}, '%'))
              +
              (SELECT COUNT(*) FROM process_version_history
                WHERE deleted = 0
                  AND bpmn_xml LIKE CONCAT('%', #{positionCode}, '%'))
            """)
    long countProcessReferences(@Param("positionCode") String positionCode);

    @Select("""
            SELECT COUNT(*)
            FROM sys_position_assignment first_assignment
            JOIN sys_position_assignment second_assignment
              ON first_assignment.id &lt; second_assignment.id
             AND first_assignment.position_id = second_assignment.position_id
             AND first_assignment.organization_unit_id = second_assignment.organization_unit_id
             AND first_assignment.effective_from &lt;
                 COALESCE(second_assignment.effective_to, '9999-12-31 23:59:59.999999')
             AND COALESCE(first_assignment.effective_to, '9999-12-31 23:59:59.999999')
                 &gt; second_assignment.effective_from
            WHERE first_assignment.position_id = #{positionId}
              AND first_assignment.revoked_at IS NULL
              AND second_assignment.revoked_at IS NULL
            """)
    long countOverlapsPreventingSingleMode(@Param("positionId") String positionId);

    @Select("""
            SELECT COUNT(*)
            FROM sys_position_assignment assignment_record
            JOIN sys_organization organization_unit
              ON organization_unit.id = assignment_record.organization_unit_id
            WHERE assignment_record.position_id = #{positionId}
              AND organization_unit.deleted = 0
              AND organization_unit.type &lt;&gt; #{unitType}
            """)
    long countAssignmentsOutsideUnitType(
            @Param("positionId") String positionId,
            @Param("unitType") String unitType);

    @Update("""
            UPDATE sys_position
            SET position_name = #{positionName},
                applicable_unit_type = #{applicableUnitType},
                holder_mode = #{holderMode},
                sort_order = #{sortOrder},
                description = #{description},
                updated_by = #{actor},
                revision = revision + 1,
                update_time = UTC_TIMESTAMP(6)
            WHERE id = #{id} AND revision = #{expectedRevision} AND deleted = 0
            """)
    int updateDefinition(
            @Param("id") String id,
            @Param("positionName") String positionName,
            @Param("applicableUnitType") String applicableUnitType,
            @Param("holderMode") String holderMode,
            @Param("sortOrder") int sortOrder,
            @Param("description") String description,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);

    @Update("""
            UPDATE sys_position
            SET status = #{status}, updated_by = #{actor},
                revision = revision + 1, update_time = UTC_TIMESTAMP(6)
            WHERE id = #{id} AND revision = #{expectedRevision} AND deleted = 0
            """)
    int updateStatus(
            @Param("id") String id,
            @Param("status") String status,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);

    @Update("""
            UPDATE sys_position
            SET deleted = 1, status = 'DISABLED', updated_by = #{actor},
                revision = revision + 1, update_time = UTC_TIMESTAMP(6)
            WHERE id = #{id} AND revision = #{expectedRevision} AND deleted = 0
            """)
    int softDelete(
            @Param("id") String id,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);
}
