package com.workflow.admin.identity.user.application;

import com.workflow.core.logging.LogValue;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.workflow.core.result.PageResult;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAudit;
import com.workflow.admin.organization.infrastructure.persistence.record.SysOrganization;
import com.workflow.admin.authorization.role.infrastructure.persistence.record.SysRole;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUserRole;
import com.workflow.admin.organization.infrastructure.persistence.mapper.SysOrganizationMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.admin.auth.infrastructure.AuthRefreshSessionMapper;
import com.workflow.admin.identity.position.application.PositionAssignmentQueryService;
import com.workflow.admin.identity.position.application.PositionOrganizationScopeService;
import com.workflow.admin.identity.position.infrastructure.persistence.record.PositionAssignmentViewRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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

    /** 用户 Mapper */
    private final SysUserMapper userMapper;
    /** 角色 Mapper，用于查询用户角色 */
    private final SysRoleMapper roleMapper;
    /** 用户角色关联 Mapper */
    private final SysUserRoleMapper userRoleMapper;
    /** 浏览器刷新会话 Mapper，用于全设备撤销。 */
    private final AuthRefreshSessionMapper refreshSessionMapper;
    /** 组织部门 Mapper，用于回填用户的组织/部门名称 */
    private final SysOrganizationMapper orgMapper;
    /** 当前任职摘要查询，内部重复执行组织数据范围过滤。 */
    private final PositionAssignmentQueryService positionAssignmentQueryService;
    /** 用户职务筛选必须把可见组织范围下推到分页 SQL。 */
    private final PositionOrganizationScopeService positionOrganizationScopeService;
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
            String roleId,
            String positionCode) {
        int safePageNum = Math.max(pageNum, 1);
        int safePageSize = Math.min(Math.max(pageSize, 1), 100);
        LocalDateTime asOf = LocalDateTime.now(ZoneOffset.UTC);
        List<String> visibleAssignmentUnits = StringUtils.hasText(positionCode)
                ? positionOrganizationScopeService.visibleUnitIds()
                : null;
        Page<SysUser> page = userMapper.selectUserPage(
                new Page<>(safePageNum, safePageSize),
                trimToNull(keyword),
                trimToNull(status),
                trimToNull(orgId),
                trimToNull(deptId),
                trimToNull(roleId),
                normalizePositionCode(positionCode),
                asOf,
                visibleAssignmentUnits);
        page.getRecords().forEach(user -> {
            fillUserRoles(user);
            fillUserOrgInfo(user);
        });
        fillCurrentPositionAssignments(page.getRecords(), asOf);
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
        SysUser existing = null;
        if (StringUtils.hasText(user.getId())) {
            existing = userMapper.selectById(user.getId());
            if (existing == null) {
                throw new IllegalArgumentException("用户不存在");
            }
        }
        validateOrganizationMembership(user, existing);

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
            validateNewPassword(user.getPassword());
            user.setPassword(passwordEncoder.encode(user.getPassword()));
            user.setPasswordResetRequired(true);
            userMapper.insert(user);
            log.info("新增用户：{}", LogValue.safe(user.getUsername()));
        } else {
            // 更新 - 不更新密码
            user.setPassword(null);
            user.setPasswordResetRequired(null);
            userMapper.updateById(user);
            if (SysUser.Status.DISABLED.getValue()
                    .equals(user.getStatus())
                    && !SysUser.Status.DISABLED.getValue()
                    .equals(existing.getStatus())) {
                revokeSessions(user.getId());
            }
            log.info("更新用户：{}", LogValue.safe(user.getUsername()));
        }
        
        // 保存角色关联
        if (user.getRoleIds() != null) {
            saveUserRoles(user.getId(), user.getRoleIds());
        }
        
        return user;
    }

    /**
     * 校验用户单一 org/dept 归属；父链只读取 parent_id，最多遍历 32 层，
     * 不信任可能滞后的 path/level 冗余字段。
     */
    private void validateOrganizationMembership(
            SysUser requested,
            SysUser existing) {
        // 现有更新接口同时服务整 DTO 表单和内部的局部对象：null 表示未提供、
        // 沿用旧值；非 null 的空字符串表示前端 clearable 控件显式清空。
        // 这里只计算提交后的最终归属状态，不把局部更新扩展成新的 PATCH 契约。
        String orgId = requested.getOrgId() == null
                ? existing == null ? null : trimToNull(existing.getOrgId())
                : trimToNull(requested.getOrgId());
        String deptId = requested.getDeptId() == null
                ? existing == null ? null : trimToNull(existing.getDeptId())
                : trimToNull(requested.getDeptId());
        if (!StringUtils.hasText(orgId)) {
            if (StringUtils.hasText(deptId)) {
                throw new IllegalArgumentException("设置部门前必须先设置所属组织");
            }
            return;
        }
        SysOrganization organization = orgMapper.selectById(orgId);
        if (organization == null
                || !SysOrganization.Type.ORG.getValue().equals(organization.getType())
                || !SysOrganization.Status.ENABLED.getValue().equals(
                    organization.getStatus())) {
            throw new IllegalArgumentException("orgId 必须指向启用的组织节点");
        }
        if (!StringUtils.hasText(deptId)) {
            return;
        }
        SysOrganization department = orgMapper.selectById(deptId);
        if (department == null
                || !SysOrganization.Type.DEPT.getValue().equals(department.getType())
                || !SysOrganization.Status.ENABLED.getValue().equals(
                    department.getStatus())) {
            throw new IllegalArgumentException("deptId 必须指向启用的部门节点");
        }
        Set<String> visited = new HashSet<>();
        String currentId = department.getId();
        for (int depth = 0; depth < 32; depth++) {
            if (!visited.add(currentId)) {
                throw new IllegalArgumentException("部门所属组织链存在环");
            }
            if (orgId.equals(currentId)) {
                return;
            }
            SysOrganization current = orgMapper.selectById(currentId);
            if (current == null) {
                throw new IllegalArgumentException("部门所属组织链存在悬空节点");
            }
            String parentId = current.getParentId();
            if (!StringUtils.hasText(parentId) || "0".equals(parentId)) {
                break;
            }
            currentId = parentId;
        }
        throw new IllegalArgumentException("deptId 不在 orgId 的组织范围内");
    }

    /** 为用户分页批量回填范围安全的当前任职摘要。 */
    private void fillCurrentPositionAssignments(
            List<SysUser> users,
            LocalDateTime asOf) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<PositionAssignmentViewRow> rows =
                positionAssignmentQueryService.currentRowsByUsers(
                        users.stream().map(SysUser::getId).toList(), asOf);
        Map<String, List<SysUser.CurrentPositionAssignment>> byUser =
                new HashMap<>();
        for (PositionAssignmentViewRow row : rows) {
            byUser.computeIfAbsent(row.getUserId(), ignored -> new ArrayList<>())
                    .add(new SysUser.CurrentPositionAssignment(
                            row.getId(), row.getPositionCode(),
                            row.getPositionName(), row.getOrganizationUnitId(),
                            row.getOrganizationUnitName(),
                            Boolean.TRUE.equals(row.getIsPrimary()),
                            row.getEffectiveFrom().toInstant(ZoneOffset.UTC),
                            row.getEffectiveTo() == null ? null
                                    : row.getEffectiveTo().toInstant(ZoneOffset.UTC)));
        }
        users.forEach(user -> user.setCurrentPositionAssignments(
                List.copyOf(byUser.getOrDefault(user.getId(), List.of()))));
    }

    private String normalizePositionCode(String positionCode) {
        return StringUtils.hasText(positionCode)
                ? positionCode.trim().toUpperCase(java.util.Locale.ROOT) : null;
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
        
        // 删除用户前先递增全局令牌版本并撤销所有浏览器会话。
        revokeSessions(id);

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
        if (SysUser.Status.DISABLED.getValue().equals(status)) {
            revokeSessions(id);
        }
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
     * 重置为管理员通过安全输入提交的新密码。
     *
     * @param id 用户ID
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
    public void resetPassword(String id, String newPassword) {
        SysUser existing = userMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        validateNewPassword(newPassword);
        if (passwordMatches(newPassword, existing.getPassword())) {
            throw new IllegalArgumentException("新密码不能与当前密码相同");
        }
        SysUser update = new SysUser();
        update.setId(id);
        update.setPassword(passwordEncoder.encode(newPassword));
        update.setPasswordResetRequired(true);
        update.setUpdateTime(LocalDateTime.now());
        userMapper.updateById(update);
        revokeSessions(id);
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
        revokeSessions(id);
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
        revokeSessions(id);
    }

    public void revokeSessions(String id) {
        if (userMapper.incrementTokenVersion(id) != 1) {
            throw new IllegalArgumentException("用户不存在");
        }
        refreshSessionMapper.revokeByUserId(
                id,
                LocalDateTime.now(),
                "TOKEN_VERSION_CHANGED");
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
