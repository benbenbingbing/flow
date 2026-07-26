package com.workflow.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.common.PageResult;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
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

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户管理服务
 * <p>
 * 提供用户的增删改查、状态切换、密码重置/更新、角色关联维护及显示名称解析等能力。
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] LOWERCASE = "abcdefghjkmnpqrstuvwxyz".toCharArray();
    private static final char[] UPPERCASE = "ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();
    private static final char[] DIGITS = "23456789".toCharArray();
    private static final char[] SYMBOLS = "!@#$%&*+-_".toCharArray();
    private static final char[] TEMPORARY_PASSWORD_CHARS =
            "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789!@#$%&*+-_".toCharArray();
    
    /** 用户 Mapper */
    private final SysUserMapper userMapper;
    /** 角色 Mapper，用于查询用户角色 */
    private final SysRoleMapper roleMapper;
    /** 用户角色关联 Mapper */
    private final SysUserRoleMapper userRoleMapper;
    /** 组织部门 Mapper，用于回填用户的组织/部门名称 */
    private final SysOrganizationMapper orgMapper;
    /** BCrypt 密码编码器，用于密码加密与校验 */
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    
    /**
     * 查询用户列表（已填充角色和组织部门信息）
     *
     * @return 用户列表，按创建时间倒序
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

    public PageResult<SysUser> getUserPage(
            int pageNum,
            int pageSize,
            String keyword,
            String status,
            String orgId,
            String deptId,
            String roleId) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        Page<SysUser> page = userMapper.selectUserPage(
                new Page<>(safePageNum, safePageSize),
                trimToNull(keyword),
                trimToNull(status),
                trimToNull(orgId),
                trimToNull(deptId),
                trimToNull(roleId));
        page.getRecords().forEach(user -> {
            fillUserRoles(user);
            fillUserOrgInfo(user);
        });
        return new PageResult<>(
                page.getRecords(),
                page.getTotal(),
                page.getCurrent(),
                page.getSize());
    }

    /**
     * 分页查询已分配指定角色的用户
     *
     * @param roleId   角色ID
     * @param pageNum  页码
     * @param pageSize 每页条数
     * @param keyword  用户名、昵称、邮箱或手机号关键字
     * @return 用户分页结果
     */
    public PageResult<SysUser> getUsersByRolePage(
            String roleId,
            int pageNum,
            int pageSize,
            String keyword) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        String normalizedKeyword = StringUtils.hasText(keyword) ? keyword.trim() : null;

        Page<SysUser> page = new Page<>(safePageNum, safePageSize);
        Page<SysUser> resultPage = userMapper.selectPageByRoleId(page, roleId, normalizedKeyword);
        resultPage.getRecords().forEach(user -> {
            fillUserRoles(user);
            fillUserOrgInfo(user);
        });

        return new PageResult<>(
                resultPage.getRecords(),
                resultPage.getTotal(),
                resultPage.getCurrent(),
                resultPage.getSize());
    }
    
    /**
     * 根据ID查询用户
     *
     * @param id 用户ID
     * @return 用户对象（已填充角色和组织部门信息），不存在返回 null
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
     *
     * @param username 用户名
     * @return 用户对象（已填充角色和组织部门信息），不存在返回 null
     */
    public SysUser getByUsername(String username) {
        SysUser user = userMapper.selectByUsername(username);
        if (user != null) {
            fillUserRoles(user);
            fillUserOrgInfo(user);
        }
        return user;
    }
    
    /**
     * 根据用户名查询用户昵称
     * <p>
     * 先按用户名查询，未命中时再尝试按用户ID查询（多实例任务中 assignee 可能是用户ID）。
     * 昵称为空时回退为用户名，仍无则返回入参原值。
     * </p>
     *
     * @param username 用户名或用户ID
     * @return 用户昵称；查无用户时返回入参原值
     */
    public String getNicknameByUsername(String username) {
        if (username == null || username.isEmpty()) {
            return null;
        }
        SysUser user = userMapper.selectByUsername(username);
        if (user == null) {
            // 尝试按用户ID查询（多实例任务中 assignee 可能是用户ID）
            user = userMapper.selectById(username);
        }
        if (user != null && user.getNickname() != null && !user.getNickname().isEmpty()) {
            return user.getNickname();
        }
        return user != null ? user.getUsername() : username;
    }
    
    /**
     * 根据用户ID/用户名获取统一显示名称：nickname(username)
     */
    public String getDisplayName(String idOrUsername) {
        if (!StringUtils.hasText(idOrUsername)) {
            return idOrUsername;
        }
        SysUser user = userMapper.selectByUsername(idOrUsername);
        if (user == null) {
            user = userMapper.selectById(idOrUsername);
        }
        if (user == null) {
            return idOrUsername;
        }
        String nickname = StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
        if (nickname.equals(user.getUsername())) {
            return nickname;
        }
        return nickname + "(" + user.getUsername() + ")";
    }
    
    /**
     * 根据用户ID/用户名列表获取统一显示名称，逗号分隔
     */
    public String getDisplayNames(List<String> idsOrUsernames) {
        if (idsOrUsernames == null || idsOrUsernames.isEmpty()) {
            return "";
        }
        return idsOrUsernames.stream()
                .map(this::getDisplayName)
                .distinct()
                .collect(Collectors.joining(","));
    }
    
    /**
     * 保存用户（新增或更新），并同步用户角色关联
     *
     * @param user 用户对象，roleIds 为关联的角色ID列表
     * @return 保存后的用户对象
     * @throws RuntimeException 用户名已存在时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPSERT,
            operation = "保存用户",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "SYS_USER",
            captureArguments = true,
            captureResult = true)
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
            String temporaryPassword = generateTemporaryPassword();
            user.setPassword(passwordEncoder.encode(temporaryPassword));
            user.setPasswordResetRequired(true);
            userMapper.insert(user);
            user.setTemporaryPassword(temporaryPassword);
            log.info("新增用户：{}", user.getUsername());
        } else {
            // 更新 - 不更新密码
            user.setPassword(null);
            user.setPasswordResetRequired(null);
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
     * 删除用户（先删除角色关联，再逻辑删除用户）
     *
     * @param id 用户ID
     * @throws RuntimeException 用户不存在或为超级管理员时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.DELETE,
            operation = "删除用户",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_USER",
            targetIdArg = 0)
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
     *
     * @param id     用户ID
     * @param status 状态值：0-启用 1-禁用
     * @throws RuntimeException 禁用超级管理员时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPDATE,
            operation = "更新用户状态",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "SYS_USER",
            targetIdArg = 0,
            captureArguments = true)
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

    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.UPDATE,
            operation = "批量更新用户状态",
            risk = AuditRiskLevel.HIGH,
            required = true,
            targetType = "SYS_USER_BATCH",
            captureArguments = true)
    public void batchUpdateStatus(List<String> userIds, String status) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个用户");
        }
        if (!SysUser.Status.ENABLED.getValue().equals(status)
                && !SysUser.Status.DISABLED.getValue().equals(status)) {
            throw new IllegalArgumentException("用户状态只能为启用或禁用");
        }
        for (String userId : userIds.stream().distinct().toList()) {
            updateStatus(userId, status);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.ASSIGN_PERMISSION,
            operation = "批量分配用户角色",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_USER_BATCH",
            captureArguments = true)
    public void batchAssignRoles(List<String> userIds, List<String> roleIds) {
        if (userIds == null || userIds.isEmpty()) {
            throw new IllegalArgumentException("至少选择一个用户");
        }
        List<String> normalizedRoles = roleIds == null ? List.of() : roleIds.stream().distinct().toList();
        for (String userId : userIds.stream().distinct().toList()) {
            if (userMapper.selectById(userId) == null) {
                throw new IllegalArgumentException("用户不存在: " + userId);
            }
            saveUserRoles(userId, normalizedRoles);
        }
    }
    
    /**
     * 重置为一次性随机临时密码。
     *
     * @param id 用户ID
     * @return 只应在本次响应中展示的临时密码
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SECURITY,
            action = AuditAction.RESET_PASSWORD,
            operation = "重置用户密码",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_USER",
            targetIdArg = 0)
    public String resetPassword(String id) {
        SysUser existing = userMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        String temporaryPassword = generateTemporaryPassword();
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(temporaryPassword));
        update.setPasswordResetRequired(true);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        return temporaryPassword;
    }
    
    /**
     * 校验当前密码并完成改密，同时解除首次登录限制。
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SECURITY,
            action = AuditAction.RESET_PASSWORD,
            operation = "修改当前用户密码",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_USER",
            targetIdArg = 0)
    public void changePassword(String id, String currentPassword, String newPassword) {
        SysUser existing = userMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!passwordMatches(currentPassword, existing.getPassword())) {
            throw new IllegalArgumentException("当前密码不正确");
        }
        validateNewPassword(newPassword);
        if (passwordMatches(newPassword, existing.getPassword())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(newPassword));
        update.setPasswordResetRequired(false);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
    }

    /**
     * 登录成功后迁移历史明文密码，避免继续保留明文。
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SECURITY,
            action = AuditAction.RESET_PASSWORD,
            operation = "迁移历史用户密码",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_USER",
            targetIdArg = 0)
    public void migrateLegacyPassword(String id, String rawPassword) {
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(rawPassword));
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
    }

    public boolean passwordMatches(String rawPassword, String storedPassword) {
        if (!StringUtils.hasText(rawPassword) || !StringUtils.hasText(storedPassword)) {
            return false;
        }
        if (isBcryptPassword(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return java.security.MessageDigest.isEqual(
                rawPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                storedPassword.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private boolean isBcryptPassword(String password) {
        return password != null
                && (password.startsWith("$2a$")
                || password.startsWith("$2b$")
                || password.startsWith("$2y$"));
    }

    public boolean requiresPasswordReset(String id) {
        SysUser user = userMapper.selectById(id);
        return user != null && Boolean.TRUE.equals(user.getPasswordResetRequired());
    }
    
    /**
     * 保存用户角色关联（先删除原有角色，再批量插入新角色）
     *
     * @param userId  用户ID
     * @param roleIds 角色ID列表
     */
    @Transactional(rollbackFor = Exception.class)
    @SystemAudit(
            module = AuditModule.SYSTEM,
            action = AuditAction.ASSIGN_PERMISSION,
            operation = "分配用户角色",
            risk = AuditRiskLevel.CRITICAL,
            required = true,
            targetType = "SYS_USER",
            targetIdArg = 0,
            captureArguments = true)
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
     * 填充用户角色信息（回填 roles、roleIds 字段）
     *
     * @param user 待填充的用户对象
     */
    private void fillUserRoles(SysUser user) {
        List<SysRole> roles = roleMapper.selectRolesByUserId(user.getId());
        user.setRoles(roles);
        user.setRoleIds(roles.stream().map(SysRole::getId).collect(Collectors.toList()));
    }
    
    /**
     * 填充用户组织部门信息（回填 orgName、deptName 字段）
     *
     * @param user 待填充的用户对象
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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String generateTemporaryPassword() {
        List<Character> characters = new ArrayList<>();
        characters.add(randomCharacter(LOWERCASE));
        characters.add(randomCharacter(UPPERCASE));
        characters.add(randomCharacter(DIGITS));
        characters.add(randomCharacter(SYMBOLS));
        while (characters.size() < 16) {
            characters.add(randomCharacter(TEMPORARY_PASSWORD_CHARS));
        }
        Collections.shuffle(characters, SECURE_RANDOM);
        StringBuilder password = new StringBuilder(characters.size());
        characters.forEach(password::append);
        return password.toString();
    }

    private char randomCharacter(char[] characters) {
        return characters[SECURE_RANDOM.nextInt(characters.length)];
    }

    private void validateNewPassword(String password) {
        if (password == null || password.length() < 10 || password.length() > 72) {
            throw new IllegalArgumentException("新密码长度必须为10到72位");
        }
        if (!password.chars().anyMatch(Character::isLowerCase)
                || !password.chars().anyMatch(Character::isUpperCase)
                || !password.chars().anyMatch(Character::isDigit)) {
            throw new IllegalArgumentException("新密码必须同时包含大写字母、小写字母和数字");
        }
    }
}
