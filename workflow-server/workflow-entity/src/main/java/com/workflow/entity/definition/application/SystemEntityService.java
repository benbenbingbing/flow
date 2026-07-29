package com.workflow.entity.definition.application;

import com.workflow.core.result.PageRequest;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.admin.identity.group.infrastructure.persistence.record.SysGroup;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
        PageRequest page = PageRequest.normalize(pageNum, pageSize, 10, 100);
        Selection selection = switch (entityType.toUpperCase(Locale.ROOT)) {
            case "USER" -> selectUserList(keyword, page);
            case "DEPT" -> selectDeptList(keyword, page);
            case "ROLE" -> selectRoleList(keyword, page);
            case "GROUP" -> selectGroupList(keyword, page);
            default -> throw new IllegalArgumentException("未知的系统实体类型: " + entityType);
        };

        Map<String, Object> result = new HashMap<>();
        result.put("records", selection.records());
        result.put("total", selection.total());
        result.put("pageNum", page.pageNumber());
        result.put("pageSize", page.pageSize());
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

    private Selection selectUserList(String keyword, PageRequest page) {
        LambdaQueryWrapper<SysUser> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(SysUser::getUsername, keyword)
                    .or()
                    .like(SysUser::getNickname, keyword));
        }
        Page<SysUser> result = userMapper.selectPage(new Page<>(page.pageNumber(), page.pageSize()), query);
        return new Selection(result.getRecords().stream().map(this::convertUser).toList(), result.getTotal());
    }

    private Selection selectDeptList(String keyword, PageRequest page) {
        LambdaQueryWrapper<SysOrganization> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(SysOrganization::getOrgName, keyword)
                    .or()
                    .like(SysOrganization::getOrgCode, keyword));
        }
        Page<SysOrganization> result = organizationMapper.selectPage(new Page<>(page.pageNumber(), page.pageSize()), query);
        return new Selection(result.getRecords().stream().map(this::convertDept).toList(), result.getTotal());
    }

    private Selection selectRoleList(String keyword, PageRequest page) {
        LambdaQueryWrapper<SysRole> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(SysRole::getRoleName, keyword)
                    .or()
                    .like(SysRole::getRoleCode, keyword));
        }
        Page<SysRole> result = roleMapper.selectPage(new Page<>(page.pageNumber(), page.pageSize()), query);
        return new Selection(result.getRecords().stream().map(this::convertRole).toList(), result.getTotal());
    }

    private Selection selectGroupList(String keyword, PageRequest page) {
        LambdaQueryWrapper<SysGroup> query = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            query.and(wrapper -> wrapper.like(SysGroup::getGroupName, keyword)
                    .or()
                    .like(SysGroup::getGroupCode, keyword));
        }
        Page<SysGroup> result = groupMapper.selectPage(new Page<>(page.pageNumber(), page.pageSize()), query);
        return new Selection(result.getRecords().stream().map(this::convertGroup).toList(), result.getTotal());
    }

    private record Selection(List<Map<String, Object>> records, long total) {
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
