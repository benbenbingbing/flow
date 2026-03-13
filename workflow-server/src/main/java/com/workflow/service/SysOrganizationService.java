package com.workflow.service;

import com.workflow.entity.SysOrganization;

import java.util.List;

/**
 * 组织部门服务接口
 */
public interface SysOrganizationService {
    
    /**
     * 查询组织部门树
     */
    List<SysOrganization> getOrgTree(String type);
    
    /**
     * 查询所有启用中的组织部门（平铺列表）
     */
    List<SysOrganization> getEnabledList();
    
    /**
     * 根据ID查询
     */
    SysOrganization getById(String id);
    
    /**
     * 保存组织部门
     */
    SysOrganization saveOrg(SysOrganization org);
    
    /**
     * 删除组织部门
     */
    void deleteOrg(String id);
    
    /**
     * 更新状态
     */
    void updateStatus(String id, String status);
    
    /**
     * 获取父级路径名称（如：集团/公司/部门）
     */
    String getFullPathName(String id);
    
    /**
     * 根据用户ID查询其组织
     */
    SysOrganization getUserOrg(String userId);
    
    /**
     * 根据用户ID查询其部门
     */
    SysOrganization getUserDept(String userId);
}
