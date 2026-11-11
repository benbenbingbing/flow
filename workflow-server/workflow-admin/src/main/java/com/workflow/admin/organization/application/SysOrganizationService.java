package com.workflow.admin.organization.application;

import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.contracts.identity.position.model.OrganizationBusinessLevelView;
import com.workflow.core.result.PageResult;

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
     * 分页浏览直属下级；keyword 非空时搜索当前节点及其后代，包含启用和禁用节点。
     *
     * @param pageNum 页码，从 1 开始
     * @param pageSize 每页数量，范围 1 到 100
     * @param parentId 浏览或搜索范围，0 表示全部组织
     * @param keyword 名称或编码关键词，空白表示浏览直属下级
     * @param type org/dept 或空，筛选结果类型
     * @param excludeId 选择上级时排除本节点及整个子树，可空
     * @return 带完整名称路径及按需展开标记的分页结果
     * @throws IllegalArgumentException 参数非法或范围节点不存在、父链未完整
     */
    PageResult<SysOrganization> getManagementPage(
            int pageNum, int pageSize, String parentId, String keyword, String type, String excludeId);

    /** 返回从真实根到目标节点的有序路径，用于面包屑和懒加载树定位；无有效父链时拒绝定位。 */
    List<SysOrganization> getAncestors(String id);
    
    /**
     * 查询所有启用中的组织部门（平铺列表）
     *
     * @return 启用中的组织部门平铺列表
     */
    List<SysOrganization> getEnabledList();

    /**
     * 返回组织编辑表单可用的业务层级，不要求额外的字典管理权限。
     *
     * @return 组织业务层级视图集合，供调用方遍历或展示
     */
    List<OrganizationBusinessLevelView> getBusinessLevelOptions();
    
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

    /** 来源预配入口，允许服务端标记的父链待关联状态；调用方须持有目录锁、验证来源权限并在同事务补齐下级。 */
    SysOrganization saveProvisionedOrganization(SysOrganization org);
    
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
