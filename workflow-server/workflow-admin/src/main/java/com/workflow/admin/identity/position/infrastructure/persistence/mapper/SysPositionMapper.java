package com.workflow.admin.identity.position.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
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
// 搜索模式在 MyBatis 中组装并以 VARCHAR 绑定，保留通配符语义，避免数据库 CONCAT 差异。
@Mapper
public interface SysPositionMapper extends BaseMapper<SysPosition> {

    default SysPosition selectByCode(String code) {
        // 保留原查询仅取一行的语义，由分页插件生成目标数据库的限制语法。
        return selectPage(new Page<SysPosition>(1, 1, false), Wrappers.<SysPosition>lambdaQuery()
                .eq(SysPosition::getPositionCode, code)).getRecords().stream().findFirst().orElse(null);
    }

    /** 编码唯一性覆盖历史删除行；不能使用自动追加逻辑删除条件的 BaseMapper 查询。 */
    default SysPosition selectAnyByCode(String code) {
        return selectAnyByCodePage(new Page<>(1, 1, false), code).stream().findFirst().orElse(null);
    }

    /** 保留包含已删除行的查询范围，首行限制由分页插件生成。 */
    @Select("SELECT * FROM sys_position WHERE position_code = #{code}")
    List<SysPosition> selectAnyByCodePage(@Param("page") Page<SysPosition> page, @Param("code") String code);

    default SysPosition selectEnabledByCode(String code) {
        // 保留原查询仅取一行的语义，由分页插件生成目标数据库的限制语法。
        return selectPage(new Page<SysPosition>(1, 1, false), Wrappers.<SysPosition>lambdaQuery()
                .eq(SysPosition::getPositionCode, code)
                .eq(SysPosition::getStatus, "ENABLED")).getRecords().stream().findFirst().orElse(null);
    }

    /** 读取适用组织类型的启用职务；未指定类型时返回全部，ANY 对所有类型可用。 */
    default List<SysPosition> selectEnabled(String unitType) {
        return selectList(Wrappers.<SysPosition>lambdaQuery()
                .eq(SysPosition::getStatus, "ENABLED")
                // NULLIF 由数据库按既有排序规则比较空串，保留 MySQL 对尾部空格的处理。
                .and(condition -> condition.apply("NULLIF({0,jdbcType=VARCHAR}, '') IS NULL", unitType)
                        .or().eq(SysPosition::getApplicableUnitType, "ANY")
                        .or().eq(SysPosition::getApplicableUnitType, unitType))
                .orderByAsc(SysPosition::getSortOrder, SysPosition::getPositionCode));
    }

    /** 按职务编码批量读取；空集合返回空列表，避免形成无条件查询。 */
    default List<SysPosition> selectByCodes(List<String> codes) {
        return codes == null || codes.isEmpty() ? List.of() : selectList(Wrappers.<SysPosition>lambdaQuery()
                .in(SysPosition::getPositionCode, codes).orderByAsc(SysPosition::getId));
    }

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





    /** 同一次查询汇总三类流程引用；使用派生表聚合，兼容要求 SELECT 必须带 FROM 的产品。 */
    @Select("""
            <script>
            <bind name="_contains_positionCode" value="positionCode == null ? null : &quot;%&quot; + positionCode + &quot;%&quot;"/>
            SELECT SUM(reference_count) FROM (
              SELECT COUNT(*) AS reference_count FROM process_node_config
                WHERE deleted = 0
                  AND config_json LIKE #{_contains_positionCode,jdbcType=VARCHAR}
              UNION ALL
              SELECT COUNT(*) AS reference_count FROM process_definition_config
                WHERE deleted = 0
                  AND bpmn_xml LIKE #{_contains_positionCode,jdbcType=VARCHAR}
              UNION ALL
              SELECT COUNT(*) AS reference_count FROM process_version_history
                WHERE deleted = 0
                  AND bpmn_xml LIKE #{_contains_positionCode,jdbcType=VARCHAR}
            ) process_references
            </script>
            """)
    long countProcessReferences(@Param("positionCode") String positionCode);

    /** 切换单任职模式前检查同一组织内的重叠；日期列直接比较，开放区间使用有类型的最大时间。 */
    @Select("""
            <script>
            <bind name="overlapMaximum" value="@java.time.LocalDateTime@of(9999, 12, 31, 23, 59, 59, 999999000)"/>
            SELECT COUNT(*)
            FROM sys_position_assignment first_assignment
            JOIN sys_position_assignment second_assignment
              ON first_assignment.id &lt; second_assignment.id
             AND first_assignment.position_id = second_assignment.position_id
             AND first_assignment.organization_unit_id = second_assignment.organization_unit_id
             AND (first_assignment.effective_from &lt; second_assignment.effective_to
               OR (second_assignment.effective_to IS NULL
                 AND first_assignment.effective_from &lt; #{overlapMaximum,jdbcType=TIMESTAMP}))
             AND (first_assignment.effective_to &gt; second_assignment.effective_from
               OR (first_assignment.effective_to IS NULL
                 AND second_assignment.effective_from &lt; #{overlapMaximum,jdbcType=TIMESTAMP}))
            WHERE first_assignment.position_id = #{positionId}
              AND first_assignment.revoked_at IS NULL
              AND second_assignment.revoked_at IS NULL
            </script>
            """)
    long countOverlapsPreventingSingleMode(@Param("positionId") String positionId);

    @Select("""
            SELECT COUNT(*)
            FROM sys_position_assignment assignment_record
            JOIN sys_organization organization_unit
              ON organization_unit.id = assignment_record.organization_unit_id
            WHERE assignment_record.position_id = #{positionId}
              AND organization_unit.deleted = 0
              AND organization_unit.type <> #{unitType}
            """)
    long countAssignmentsOutsideUnitType(
            @Param("positionId") String positionId,
            @Param("unitType") String unitType);

    @Update("""
            <script>
            UPDATE sys_position
            SET position_name = #{positionName},
                applicable_unit_type = #{applicableUnitType},
                holder_mode = #{holderMode},
                sort_order = #{sortOrder},
                description = #{description},
                updated_by = #{actor},
                revision = revision + 1,
                update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id} AND revision = #{expectedRevision} AND deleted = 0
            </script>
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
            <script>
            UPDATE sys_position
            SET status = #{status}, updated_by = #{actor},
                revision = revision + 1, update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id} AND revision = #{expectedRevision} AND deleted = 0
            </script>
            """)
    int updateStatus(
            @Param("id") String id,
            @Param("status") String status,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);

    @Update("""
            <script>
            UPDATE sys_position
            SET deleted = 1, status = 'DISABLED', updated_by = #{actor},
                revision = revision + 1, update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id} AND revision = #{expectedRevision} AND deleted = 0
            </script>
            """)
    int softDelete(
            @Param("id") String id,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);
}
