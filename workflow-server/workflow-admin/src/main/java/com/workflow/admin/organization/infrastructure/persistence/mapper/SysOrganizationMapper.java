package com.workflow.admin.organization.infrastructure.persistence.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 组织部门Mapper
 */
@Mapper
public interface SysOrganizationMapper extends BaseMapper<SysOrganization> {
    
    /**
     * 根据编码查询
     *
     * @param orgCode 组织编码
     * @return 组织部门对象，不存在返回 null
     */
    default SysOrganization selectByCode(String orgCode) {
        // 保留原查询仅取一行的语义，由分页插件生成目标数据库的限制语法。
        return selectPage(new Page<SysOrganization>(1, 1, false), Wrappers.<SysOrganization>lambdaQuery()
                .eq(SysOrganization::getOrgCode, orgCode)).getRecords().stream().findFirst().orElse(null);
    }

    @Select("SELECT * FROM sys_organization WHERE id = #{id} AND deleted = 0 FOR UPDATE")
    SysOrganization selectForUpdate(@Param("id") String id);

    /**
     * 任职批量事务按组织 ID 排序锁定稳定存在的组织行。
     */
    @Select("""
            <script>
            SELECT * FROM sys_organization
            WHERE id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">
              #{id}
            </foreach>
              AND deleted = 0
            ORDER BY id
            FOR UPDATE
            </script>
            """)
    List<SysOrganization> selectForUpdateByIds(@Param("ids") List<String> ids);
    
    /**
     * 检查编码是否存在
     *
     * @param orgCode   组织编码
     * @param excludeId 排除的ID（更新时传入自身ID，新增传空串或 null）
     * @return 存在返回 true，否则 false
     */
    default boolean existsCode(String orgCode, String excludeId) {
        return selectCount(Wrappers.<SysOrganization>lambdaQuery()
                .eq(SysOrganization::getOrgCode, orgCode)
                .ne(excludeId != null && !excludeId.isEmpty(), SysOrganization::getId, excludeId)) > 0;
    }
    
    /**
     * 查询子节点
     *
     * @param parentId 父级ID
     * @return 直接子节点列表
     */
    default List<SysOrganization> selectChildren(String parentId) {
        return selectList(Wrappers.<SysOrganization>lambdaQuery()
                .eq(SysOrganization::getParentId, parentId)
                .orderByAsc(SysOrganization::getSortOrder)
                .orderByAsc(SysOrganization::getCreateTime));
    }
    
    /**
     * 查询所有子节点（使用path字段，避免递归）
     * 查询path以当前path开头的所有记录
     *
     * @param path 当前节点路径；null 不匹配任何记录，避免厂商 NULL 拼接语义扩大查询范围
     * @return 所有后代节点列表
     */
    default List<SysOrganization> selectAllChildrenByPath(String path) {
        // Wrapper 对 null 做字符串拼接会形成 "null%"，提前返回保持原来不匹配任何路径的语义。
        return path == null ? List.of() : selectList(Wrappers.<SysOrganization>lambdaQuery()
                .likeRight(SysOrganization::getPath, path));
    }
    
    /**
     * 查询启用中的组织部门
     *
     * @return 启用中的组织部门列表
     */
    default List<SysOrganization> selectEnabledList() {
        return selectList(Wrappers.<SysOrganization>lambdaQuery()
                .eq(SysOrganization::getStatus, "0")
                .orderByAsc(SysOrganization::getLevel)
                .orderByAsc(SysOrganization::getSortOrder));
    }
    
    /**
     * 根据类型查询
     *
     * @param type 组织类型（org-组织，dept-部门）
     * @return 指定类型且启用中的组织部门列表
     */
    default List<SysOrganization> selectByType(String type) {
        return selectList(Wrappers.<SysOrganization>lambdaQuery()
                .eq(SysOrganization::getType, type)
                .eq(SysOrganization::getStatus, "0")
                .orderByAsc(SysOrganization::getSortOrder));
    }
    
    /**
     * 更新path字段（当父级变化时）。NULL 新路径显式写 NULL，
     * 避免被部分数据库的 REPLACE 解释为删除原路径子串。
     *
     * @param oldPath 原路径；null 不更新任何记录
     * @param newPath 新路径
     * @return 受影响的记录数
     */
    @Update("""
            <script>
            <bind name="_pathPrefix" value="oldPath == null ? null : oldPath + &quot;%&quot;"/>
            UPDATE sys_organization SET path =
            <choose>
              <when test="newPath == null">NULL</when>
              <otherwise>REPLACE(path, #{oldPath,jdbcType=VARCHAR}, #{newPath,jdbcType=VARCHAR})</otherwise>
            </choose>
            WHERE path LIKE #{_pathPrefix,jdbcType=VARCHAR} AND deleted = 0
            </script>
            """)
    int updateChildrenPath(@Param("oldPath") String oldPath, @Param("newPath") String newPath);
    


    /**
     * 仅由 UNIT_LEADER 任职服务单向维护旧负责人兼容投影。
     */
    @Update("""
            <script>
            UPDATE sys_organization
            SET leader_id = #{leaderId}, leader_name = #{leaderName},
                update_time = ${@com.workflow.integration.database.api.DatabaseRuntimeSql@utcNow(_databaseId)}
            WHERE id = #{unitId} AND deleted = 0
            </script>
            """)
    int updateLeaderProjection(
            @Param("unitId") String unitId,
            @Param("leaderId") String leaderId,
            @Param("leaderName") String leaderName);
    
    /**
     * 根据path查询父级列表（用于快速获取所有父级）
     * path: /0/1/2/3/ -> 查询id in (0,1,2,3)
     *
     * @param path 当前节点路径
     * @return 所有父级节点列表
     */
    List<SysOrganization> selectParentsByPath(@Param("path") String path);
}
