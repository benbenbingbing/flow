package com.workflow.service;

import com.workflow.entity.SysOrganization;

import java.util.List;

/**
 * 组织部门服务接口
 * <p>
 * 提供组织/部门的树形查询、增删改、状态切换、路径名称及用户所属组织/部门的查询能力。
 * 组织与部门共用同一张表，通过 type 字段区分。
 * </p>
 */
public interface SysOrganizationService {
    
    /**
     * 查询组织部门树
     *
     * @param type 组织类型（org-组织，dept-部门），为空则查询所有启用项
     * @return 树形结构的组织部门列表
     */
    List<SysOrganization> getOrgTree(String type);
    
    /**
     * 查询所有启用中的组织部门（平铺列表）
     *
     * @return 启用中的组织部门平铺列表
     */
    List<SysOrganization> getEnabledList();
    
    /**
     * 根据ID查询
     *
     * @param id 组织部门ID
     * @return 组织部门对象，不存在返回 null
     */
    SysOrganization getById(String id);
    
    /**
     * 保存组织部门（新增或更新）
     *
     * @param org 组织部门对象
     * @return 保存后的组织部门对象
     */
    SysOrganization saveOrg(SysOrganization org);
    
    /**
     * 删除组织部门
     *
     * @param id 组织部门ID
     */
    void deleteOrg(String id);
    
    /**
     * 更新状态
     *
     * @param id     组织部门ID
     * @param status 状态值：0-启用 1-禁用
     */
    void updateStatus(String id, String status);
    
    /**
     * 获取父级路径名称（如：集团/公司/部门）
     *
     * @param id 组织部门ID
     * @return 从顶级到当前节点的完整路径名称，不存在返回空串
     */
    String getFullPathName(String id);
    
    /**
     * 根据用户ID查询其组织
     *
     * @param userId 用户ID
     * @return 用户所属组织对象，未关联则返回 null
     */
    SysOrganization getUserOrg(String userId);
    
    /**
     * 根据用户ID查询其部门
     *
     * @param userId 用户ID
     * @return 用户所属部门对象，未关联则返回 null
     */
    SysOrganization getUserDept(String userId);
}
