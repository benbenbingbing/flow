package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.workflow.entity.SysOrganization;
import com.workflow.entity.SysRole;
import com.workflow.entity.SysUser;
import com.workflow.entity.SysUserRole;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysRoleMapper;
import com.workflow.mapper.SysUserMapper;
import com.workflow.mapper.SysUserRoleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {
    
    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysUserRoleMapper userRoleMapper;
    private final SysOrganizationMapper orgMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    /**
     * 查询用户列表
     */
    public List<SysUser> getUserList() {
        List<SysUser> users = userMapper.selectList(
            new LambdaQueryWrapper<SysUser>()
                .orderByDesc(SysUser::getCreateTime)
        );
        // 填充角色信息和组织部门信息
        users.forEach(user -> {
            fillUserRoles(user);
            fillUserOrgInfo(user);
        });
        return users;
    }
    
    /**
     * 根据ID查询用户
     */
    public SysUser getById(String id) {
        SysUser user = userMapper.selectById(id);
        if (user != null) {
            fillUserRoles(user);
            fillUserOrgInfo(user);
        }
        return user;
    }
    
    /**
     * 根据用户名查询用户
     */
    public SysUser getByUsername(String username) {
        return userMapper.selectByUsername(username);
    }
    
    /**
     * 保存用户
     */
    @Transactional(rollbackFor = Exception.class)
    public SysUser saveUser(SysUser user) {
        // 校验用户名唯一性
        if (StringUtils.hasText(user.getUsername())) {
            String excludeId = user.getId() != null ? user.getId() : "";
            if (userMapper.existsUsername(user.getUsername(), excludeId)) {
                throw new RuntimeException("用户名已存在：" + user.getUsername());
            }
        }
        
        // 设置默认值
        if (!StringUtils.hasText(user.getStatus())) {
            user.setStatus(SysUser.Status.ENABLED.getValue());
        }
        if (!StringUtils.hasText(user.getNickname())) {
            user.setNickname(user.getUsername());
        }
        
        user.setUpdateTime(LocalDateTime.now());
        
        if (!StringUtils.hasText(user.getId())) {
            // 新增
            user.setCreateTime(LocalDateTime.now());
            // 默认密码 123456
            user.setPassword("$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EO");
            userMapper.insert(user);
            log.info("新增用户：{}", user.getUsername());
        } else {
            // 更新 - 不更新密码
            user.setPassword(null);
            userMapper.updateById(user);
            log.info("更新用户：{}", user.getUsername());
        }
        
        // 保存角色关联
        if (user.getRoleIds() != null) {
            saveUserRoles(user.getId(), user.getRoleIds());
        }
        
        return user;
    }
    
    /**
     * 删除用户
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteUser(String id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        
        // 不能删除超级管理员
        if ("admin".equals(user.getUsername())) {
            throw new RuntimeException("不能删除超级管理员");
        }
        
        // 删除角色关联
        userRoleMapper.deleteByUserId(id);
        
        // 逻辑删除用户
        userMapper.deleteById(id);
        log.info("删除用户：{}", user.getUsername());
    }
    
    /**
     * 更新用户状态
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(String id, String status) {
        // 不能禁用超级管理员
        if ("1".equals(status)) {
            SysUser user = userMapper.selectById(id);
            if (user != null && "admin".equals(user.getUsername())) {
                throw new RuntimeException("不能禁用超级管理员");
            }
        }
        
        SysUser user = new SysUser();
        user.setId(id);
        user.setStatus(status);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }
    
    /**
     * 重置密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(String id) {
        SysUser user = new SysUser();
        user.setId(id);
        // 默认密码 123456
        user.setPassword(passwordEncoder.encode("123456"));
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }
    
    /**
     * 更新用户密码
     */
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(String id, String encodedPassword) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setPassword(encodedPassword);
        user.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(user);
    }
    
    /**
     * 保存用户角色关联
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserRoles(String userId, List<String> roleIds) {
        // 删除原有角色
        userRoleMapper.deleteByUserId(userId);
        
        // 添加新角色
        if (roleIds != null && !roleIds.isEmpty()) {
            for (String roleId : roleIds) {
                SysUserRole userRole = new SysUserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRole.setCreateTime(LocalDateTime.now());
                userRoleMapper.insert(userRole);
            }
        }
    }
    
    /**
     * 填充用户角色信息
     */
    private void fillUserRoles(SysUser user) {
        List<SysRole> roles = roleMapper.selectRolesByUserId(user.getId());
        user.setRoles(roles);
        user.setRoleIds(roles.stream().map(SysRole::getId).collect(Collectors.toList()));
    }
    
    /**
     * 填充用户组织部门信息
     */
    private void fillUserOrgInfo(SysUser user) {
        if (StringUtils.hasText(user.getOrgId())) {
            SysOrganization org = orgMapper.selectById(user.getOrgId());
            if (org != null) {
                user.setOrgName(org.getOrgName());
            }
        }
        if (StringUtils.hasText(user.getDeptId())) {
            SysOrganization dept = orgMapper.selectById(user.getDeptId());
            if (dept != null) {
                user.setDeptName(dept.getOrgName());
            }
        }
    }
}
