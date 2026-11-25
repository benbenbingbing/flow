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

    /**
     * 查询编码；查询结果供调用方展示或继续处理。
     *
     * @param code 编码，后续用于查询编码时定位或关联目标
     * @return 查询后的编码结果，供调用方继续处理
     */
    default SysPosition selectByCode(String code) {
        // 保留原查询仅取一行的语义，由分页插件生成目标数据库的限制语法。
        return selectPage(new Page<SysPosition>(1, 1, false), Wrappers.<SysPosition>lambdaQuery()
                .eq(SysPosition::getPositionCode, code)).getRecords().stream().findFirst().orElse(null);
    }

    /**
     * 编码唯一性覆盖历史删除行；不能使用自动追加逻辑删除条件的 BaseMapper 查询。
     *
     * @param code 编码，后续用于查询{@code any}编码时定位或关联目标
     * @return 查询后的{@code any}编码结果，供调用方继续处理
     */
    default SysPosition selectAnyByCode(String code) {
        return selectAnyByCodePage(new Page<>(1, 1, false), code).stream().findFirst().orElse(null);
    }

    /**
     * 保留包含已删除行的查询范围，首行限制由分页插件生成。
     *
     * @param page 分页参数，用于限制后续查询范围和返回数量
     * @param code 编码，后续用于查询{@code any}编码分页时定位或关联目标
     * @return 系统位置集合，供调用方遍历或展示
     */
    @Select("SELECT * FROM sys_position WHERE position_code = #{code}")
    List<SysPosition> selectAnyByCodePage(@Param("page") Page<SysPosition> page, @Param("code") String code);

    /**
     * 查询启用编码；查询结果供调用方展示或继续处理。
     *
     * @param code 编码，后续用于查询启用编码时定位或关联目标
     * @return 查询后的启用编码结果，供调用方继续处理
     */
    default SysPosition selectEnabledByCode(String code) {
        // 保留原查询仅取一行的语义，由分页插件生成目标数据库的限制语法。
        return selectPage(new Page<SysPosition>(1, 1, false), Wrappers.<SysPosition>lambdaQuery()
                .eq(SysPosition::getPositionCode, code)
                .eq(SysPosition::getStatus, "ENABLED")).getRecords().stream().findFirst().orElse(null);
    }

    /**
     * 按管理界面顺序读取全部启用职务。
     *
     * @return 系统位置集合，供调用方遍历或展示
     */
    default List<SysPosition> selectEnabled() {
        return selectList(Wrappers.<SysPosition>lambdaQuery()
                .eq(SysPosition::getStatus, "ENABLED")
                .orderByAsc(SysPosition::getSortOrder, SysPosition::getPositionCode));
    }

    /**
     * 按职务编码批量读取；空集合返回空列表，避免形成无条件查询。
     *
     * @param codes 编码集合，供本方法查询编码集合时使用
     * @return 系统位置集合，供调用方遍历或展示
     */
    default List<SysPosition> selectByCodes(List<String> codes) {
        return codes == null || codes.isEmpty() ? List.of() : selectList(Wrappers.<SysPosition>lambdaQuery()
                .in(SysPosition::getPositionCode, codes).orderByAsc(SysPosition::getId));
    }

    /**
     * 批量写事务必须先按职务 ID、再按组织 ID 的固定顺序加锁。
     *
     * @param codes 编码集合，供本方法查询更新编码集合时使用
     * @return 系统位置集合，供调用方遍历或展示
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

    /**
     * 查询更新；查询结果供调用方展示或继续处理。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @return 查询后的更新结果，供调用方继续处理
     */
    @Select("SELECT * FROM sys_position WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SysPosition selectForUpdate(@Param("id") String id);





    /**
     * 同一次查询汇总三类流程引用；使用派生表聚合，兼容要求 SELECT 必须带 FROM 的产品。
     *
     * @param positionCode 位置编码，后续用于统计流程引用时定位或关联目标
     * @return 符合条件的流程引用数量
     */
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

    /**
     * 更新定义；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param positionName 位置名称，后续用于更新定义时匹配或展示
     * @param sortOrder 排序顺序，供本方法更新定义时使用
     * @param description 描述，供本方法更新定义时使用
     * @param actor 操作人，供本方法更新定义时使用
     * @param expectedRevision 预期修订版本，供本方法更新定义时使用
     * @return 更新后的定义结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE sys_position
            SET position_name = #{positionName},
                sort_order = #{sortOrder},
                description = #{description},
                updated_by = #{actor},
                revision = revision + 1,
                update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id} AND revision = #{expectedRevision} AND deleted = 0
            </script>
            """)
    int updateDefinition(
            @Param("id") String id,
            @Param("positionName") String positionName,
            @Param("sortOrder") int sortOrder,
            @Param("description") String description,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);

    /**
     * 更新状态；后续读取或执行将使用更新后的状态。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param status 目标状态，写入记录后供流程分支或列表查询使用
     * @param actor 操作人，供本方法更新状态时使用
     * @param expectedRevision 预期修订版本，供本方法更新状态时使用
     * @return 更新后的状态结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE sys_position
            SET status = #{status}, updated_by = #{actor},
                revision = revision + 1, update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id} AND revision = #{expectedRevision} AND deleted = 0
            </script>
            """)
    int updateStatus(
            @Param("id") String id,
            @Param("status") String status,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);

    /**
     * 处理{@code soft}删除，并将结果传给后续步骤。
     *
     * @param id 目标记录 ID，后续用于定位具体数据或配置
     * @param actor 操作人，供本方法处理{@code soft}删除时使用
     * @param expectedRevision 预期修订版本，供本方法处理{@code soft}删除时使用
     * @return 处理后的{@code soft}删除结果，供调用方继续处理
     */
    @Update("""
            <script>
            UPDATE sys_position
            SET deleted = 1, status = 'DISABLED', updated_by = #{actor},
                revision = revision + 1, update_time = ${@com.workflow.integration.database.api.runtime.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{id} AND revision = #{expectedRevision} AND deleted = 0
            </script>
            """)
    int softDelete(
            @Param("id") String id,
            @Param("actor") String actor,
            @Param("expectedRevision") int expectedRevision);
}
