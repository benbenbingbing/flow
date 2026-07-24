package com.workflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.workflow.entity.SysOrganization;
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
    @Select("SELECT * FROM sys_organization WHERE org_code = #{orgCode} AND deleted = 0 LIMIT 1")
    SysOrganization selectByCode(@Param("orgCode") String orgCode);
    
    /**
     * 检查编码是否存在
     *
     * @param orgCode   组织编码
     * @param excludeId 排除的ID（更新时传入自身ID，新增传空串）
     * @return 存在返回 true，否则 false
     */
    @Select("SELECT COUNT(*) > 0 FROM sys_organization WHERE org_code = #{orgCode} AND deleted = 0 AND (#{excludeId} = '' OR id != #{excludeId})")
    boolean existsCode(@Param("orgCode") String orgCode, @Param("excludeId") String excludeId);
    
    /**
     * 查询子节点
     *
     * @param parentId 父级ID
     * @return 直接子节点列表
     */
    @Select("SELECT * FROM sys_organization WHERE parent_id = #{parentId} AND deleted = 0 ORDER BY sort_order ASC, create_time ASC")
    List<SysOrganization> selectChildren(@Param("parentId") String parentId);
    
    /**
     * 查询所有子节点（使用path字段，避免递归）
     * 查询path以当前path开头的所有记录
     *
     * @param path 当前节点路径
     * @return 所有后代节点列表
     */
    @Select("SELECT * FROM sys_organization WHERE path LIKE CONCAT(#{path}, '%') AND deleted = 0")
    List<SysOrganization> selectAllChildrenByPath(@Param("path") String path);
    
    /**
     * 查询启用中的组织部门
     *
     * @return 启用中的组织部门列表
     */
    @Select("SELECT * FROM sys_organization WHERE status = '0' AND deleted = 0 ORDER BY level ASC, sort_order ASC")
    List<SysOrganization> selectEnabledList();
    
    /**
     * 根据类型查询
     *
     * @param type 组织类型（org-组织，dept-部门）
     * @return 指定类型且启用中的组织部门列表
     */
    @Select("SELECT * FROM sys_organization WHERE type = #{type} AND status = '0' AND deleted = 0 ORDER BY sort_order ASC")
    List<SysOrganization> selectByType(@Param("type") String type);
    
    /**
     * 更新path字段（当父级变化时）
     *
     * @param oldPath 原路径
     * @param newPath 新路径
     * @return 受影响的记录数
     */
    @Update("UPDATE sys_organization SET path = REPLACE(path, #{oldPath}, #{newPath}) WHERE path LIKE CONCAT(#{oldPath}, '%') AND deleted = 0")
    int updateChildrenPath(@Param("oldPath") String oldPath, @Param("newPath") String newPath);
    
    /**
     * 统计组织下的用户数
     *
     * @param orgId 组织部门ID
     * @return 关联用户数
     */
    @Select("SELECT COUNT(*) FROM sys_user WHERE org_id = #{orgId} AND deleted = 0")
    int countUsers(@Param("orgId") String orgId);
    
    /**
     * 根据path查询父级列表（用于快速获取所有父级）
     * path: /0/1/2/3/ -> 查询id in (0,1,2,3)
     *
     * @param path 当前节点路径
     * @return 所有父级节点列表
     */
    List<SysOrganization> selectParentsByPath(@Param("path") String path);
}
