package com.workflow.service;

import com.workflow.entity.SysGroup;
import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysRole;
import com.workflow.entity.SysUser;
import com.workflow.mapper.SysGroupMapper;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysRoleMapper;
import com.workflow.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 系统实体服务
 * 用于查询系统内置实体（用户、部门、角色、用户组）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemEntityService {

    private final SysUserMapper userMapper;
    private final SysOrganizationMapper organizationMapper;
    private final SysRoleMapper roleMapper;
    private final SysGroupMapper groupMapper;

    /**
     * 查询系统实体列表
     *
     * @param entityType 实体类型（USER/DEPT/ROLE/GROUP）
     * @param keyword    搜索关键词
     * @param pageNum    页码
     * @param pageSize   每页数量
     * @return 分页结果
     */
    public Map<String, Object> selectList(String entityType, String keyword, int pageNum, int pageSize) {
        List<Map<String, Object>> list = new ArrayList<>();
        long total = 0;

        switch (entityType.toUpperCase()) {
            case "USER":
                list = selectUserList(keyword, pageNum, pageSize);
                total = countUsers(keyword);
                break;
            case "DEPT":
                list = selectDeptList(keyword, pageNum, pageSize);
                total = countDepts(keyword);
                break;
            case "ROLE":
                list = selectRoleList(keyword, pageNum, pageSize);
                total = countRoles(keyword);
                break;
            case "GROUP":
                list = selectGroupList(keyword, pageNum, pageSize);
                total = countGroups(keyword);
                break;
            default:
                throw new RuntimeException("未知的系统实体类型: " + entityType);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", list);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /**
     * 根据ID查询系统实体
     */
    public Map<String, Object> selectById(String entityType, String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }

        switch (entityType.toUpperCase()) {
            case "USER":
                SysUser user = userMapper.selectById(id);
                return user != null ? convertUser(user) : null;
            case "DEPT":
                SysOrganization dept = organizationMapper.selectById(id);
                return dept != null ? convertDept(dept) : null;
            case "ROLE":
                SysRole role = roleMapper.selectById(id);
                return role != null ? convertRole(role) : null;
            case "GROUP":
                SysGroup group = groupMapper.selectById(id);
                return group != null ? convertGroup(group) : null;
            default:
                return null;
        }
    }

    /**
     * 批量查询系统实体
     */
    public List<Map<String, Object>> selectBatch(String entityType, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String id : ids) {
            Map<String, Object> item = selectById(entityType, id);
            if (item != null) {
                result.add(item);
            }
        }
        return result;
    }

    // ========== 私有方法 ==========

    private List<Map<String, Object>> selectUserList(String keyword, int pageNum, int pageSize) {
        // 查询所有用户，然后手动分页和过滤
        List<SysUser> users = userMapper.selectList(null);
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            users = users.stream()
                    .filter(u -> (u.getUsername() != null && u.getUsername().toLowerCase().contains(lowerKeyword))
                            || (u.getNickname() != null && u.getNickname().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }
        
        // 手动分页
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, users.size());
        List<SysUser> pageUsers = start < users.size() ? users.subList(start, end) : new ArrayList<>();
        
        return pageUsers.stream().map(this::convertUser).collect(Collectors.toList());
    }

    private long countUsers(String keyword) {
        List<SysUser> users = userMapper.selectList(null);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            users = users.stream()
                    .filter(u -> (u.getUsername() != null && u.getUsername().toLowerCase().contains(lowerKeyword))
                            || (u.getNickname() != null && u.getNickname().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }
        return users.size();
    }

    private List<Map<String, Object>> selectDeptList(String keyword, int pageNum, int pageSize) {
        List<SysOrganization> depts = organizationMapper.selectList(null);
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            depts = depts.stream()
                    .filter(d -> (d.getOrgName() != null && d.getOrgName().toLowerCase().contains(lowerKeyword))
                            || (d.getOrgCode() != null && d.getOrgCode().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }
        
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, depts.size());
        List<SysOrganization> pageDepts = start < depts.size() ? depts.subList(start, end) : new ArrayList<>();
        
        return pageDepts.stream().map(this::convertDept).collect(Collectors.toList());
    }

    private long countDepts(String keyword) {
        List<SysOrganization> depts = organizationMapper.selectList(null);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            depts = depts.stream()
                    .filter(d -> (d.getOrgName() != null && d.getOrgName().toLowerCase().contains(lowerKeyword))
                            || (d.getOrgCode() != null && d.getOrgCode().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }
        return depts.size();
    }

    private List<Map<String, Object>> selectRoleList(String keyword, int pageNum, int pageSize) {
        List<SysRole> roles = roleMapper.selectList(null);
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            roles = roles.stream()
                    .filter(r -> (r.getRoleName() != null && r.getRoleName().toLowerCase().contains(lowerKeyword))
                            || (r.getRoleCode() != null && r.getRoleCode().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }
        
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, roles.size());
        List<SysRole> pageRoles = start < roles.size() ? roles.subList(start, end) : new ArrayList<>();
        
        return pageRoles.stream().map(this::convertRole).collect(Collectors.toList());
    }

    private long countRoles(String keyword) {
        List<SysRole> roles = roleMapper.selectList(null);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            roles = roles.stream()
                    .filter(r -> (r.getRoleName() != null && r.getRoleName().toLowerCase().contains(lowerKeyword))
                            || (r.getRoleCode() != null && r.getRoleCode().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }
        return roles.size();
    }

    private List<Map<String, Object>> selectGroupList(String keyword, int pageNum, int pageSize) {
        List<SysGroup> groups = groupMapper.selectList(null);
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            groups = groups.stream()
                    .filter(g -> (g.getGroupName() != null && g.getGroupName().toLowerCase().contains(lowerKeyword))
                            || (g.getGroupCode() != null && g.getGroupCode().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }
        
        int start = (pageNum - 1) * pageSize;
        int end = Math.min(start + pageSize, groups.size());
        List<SysGroup> pageGroups = start < groups.size() ? groups.subList(start, end) : new ArrayList<>();
        
        return pageGroups.stream().map(this::convertGroup).collect(Collectors.toList());
    }

    private long countGroups(String keyword) {
        List<SysGroup> groups = groupMapper.selectList(null);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String lowerKeyword = keyword.toLowerCase();
            groups = groups.stream()
                    .filter(g -> (g.getGroupName() != null && g.getGroupName().toLowerCase().contains(lowerKeyword))
                            || (g.getGroupCode() != null && g.getGroupCode().toLowerCase().contains(lowerKeyword)))
                    .collect(Collectors.toList());
        }
        return groups.size();
    }

    // ========== 转换方法 ==========

    private Map<String, Object> convertUser(SysUser user) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("name", user.getNickname() != null ? user.getNickname() : user.getUsername());
        map.put("code", user.getUsername());
        map.put("status", user.getStatus());
        map.put("entityType", "USER");
        return map;
    }

    private Map<String, Object> convertDept(SysOrganization dept) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", dept.getId());
        map.put("name", dept.getOrgName());
        map.put("code", dept.getOrgCode());
        map.put("status", dept.getStatus());
        map.put("entityType", "DEPT");
        return map;
    }

    private Map<String, Object> convertRole(SysRole role) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", role.getId());
        map.put("name", role.getRoleName());
        map.put("code", role.getRoleCode());
        map.put("status", role.getStatus());
        map.put("entityType", "ROLE");
        return map;
    }

    private Map<String, Object> convertGroup(SysGroup group) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", group.getId());
        map.put("name", group.getGroupName());
        map.put("code", group.getGroupCode());
        map.put("entityType", "GROUP");
        return map;
    }
}
