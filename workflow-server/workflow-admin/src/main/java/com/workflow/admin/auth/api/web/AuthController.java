package com.workflow.admin.auth.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.PublicApi;

import com.workflow.admin.auth.infrastructure.JwtUtil;
import com.workflow.admin.auth.infrastructure.ClientAddressResolver;
import com.workflow.admin.auth.application.LoginThrottleService;
import com.workflow.admin.authorization.application.PermissionUtil;
import com.workflow.core.result.Result;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.audit.AuditAction;
import com.workflow.contracts.audit.AuditModule;
import com.workflow.contracts.audit.AuditResult;
import com.workflow.contracts.audit.AuditRiskLevel;
import com.workflow.contracts.audit.SystemAuditEvent;
import com.workflow.contracts.audit.SystemAuditPort;
import com.workflow.admin.auth.api.request.ChangePasswordDTO;
import com.workflow.admin.auth.api.request.LoginDTO;
import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.admin.identity.user.application.SysUserService;
import com.workflow.admin.auth.api.response.LoginUserVO;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 认证控制器。
 *
 * <p>提供登录、当前用户信息、改密、退出和权限码查询；登录与退出事件显式写入系统审计。</p>
 */
@AuthenticatedApi(objectAuthorization = true)
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String DUMMY_PASSWORD_HASH =
            "$2y$10$KVN3n7mW3JkwqTki/svFdOxdOdcp8M3vicjVv."
                    + "yd6jGqw.zyTV9OK";

    private final SysUserService userService;
    private final SystemAuditPort auditPort;
    private final LoginThrottleService loginThrottleService;
    private final ClientAddressResolver clientAddressResolver;

    /**
     * 用户登录。
     */
    @PublicApi
    @PostMapping("/login")
    public Result<LoginUserVO> login(
            @Validated @RequestBody LoginDTO loginDTO,
            HttpServletRequest request) {
        String clientAddress =
                clientAddressResolver.resolve(request);
        loginThrottleService.assertAllowed(
                loginDTO.getUsername(),
                clientAddress);
        SysUser user = userService.getByUsername(loginDTO.getUsername());
        boolean passwordMatches =
                userService.passwordMatches(
                        loginDTO.getPassword(),
                        user == null
                                ? DUMMY_PASSWORD_HASH
                                : user.getPassword());
        if (user == null) {
            loginThrottleService.recordFailure(
                    loginDTO.getUsername(),
                    clientAddress);
            recordLogin(
                    loginDTO.getUsername(),
                    null,
                    AuditResult.FAILURE,
                    "用户名或密码错误");
            return Result.error("用户名或密码错误");
        }
        if ("1".equals(user.getStatus())) {
            loginThrottleService.recordFailure(
                    loginDTO.getUsername(),
                    clientAddress);
            recordLogin(
                    loginDTO.getUsername(),
                    user,
                    AuditResult.FAILURE,
                    "用户已被禁用");
            return Result.error("用户名或密码错误");
        }
        if (!passwordMatches) {
            loginThrottleService.recordFailure(
                    loginDTO.getUsername(),
                    clientAddress);
            recordLogin(
                    loginDTO.getUsername(),
                    user,
                    AuditResult.FAILURE,
                    "用户名或密码错误");
            return Result.error("用户名或密码错误");
        }
        if (!user.getPassword().startsWith("$2")) {
            userService.migrateLegacyPassword(
                    user.getId(),
                    loginDTO.getPassword());
            user = userService.getById(user.getId());
        }
        loginThrottleService.recordSuccess(
                loginDTO.getUsername());

        String token = JwtUtil.generateToken(
                user.getId(),
                user.getUsername(),
                user.getTokenVersion() == null ? 0L : user.getTokenVersion());
        LoginUserVO loginUser = toLoginUser(user);
        loginUser.setToken(token);

        recordLogin(
                loginDTO.getUsername(),
                user,
                AuditResult.SUCCESS,
                null);
        return Result.success(loginUser);
    }

    /**
     * 获取当前登录用户信息。
     */
    @GetMapping("/current")
    public Result<LoginUserVO> getCurrentUser() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error("未登录");
        }
        SysUser user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        return Result.success(toLoginUser(user));
    }

    /**
     * 修改当前登录用户密码。
     */
    @PostMapping("/change-password")
    public Result<Void> changePassword(
            @Validated @RequestBody ChangePasswordDTO request) {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error("未登录");
        }
        userService.changePassword(
                userId,
                request.getCurrentPassword(),
                request.getNewPassword());
        return Result.success();
    }

    /**
     * 退出登录。
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        boolean validToken = token != null && JwtUtil.validateToken(token);
        String userId = validToken
                ? JwtUtil.getUserIdFromToken(token)
                : null;
        String username = validToken
                ? JwtUtil.getUsernameFromToken(token)
                : null;
        if (validToken && userId != null) {
            userService.revokeSessions(userId);
        }
        auditPort.record(SystemAuditEvent.builder()
                .module(AuditModule.SECURITY)
                .action(AuditAction.LOGOUT)
                .operationName("用户退出登录")
                .riskLevel(AuditRiskLevel.LOW)
                .result(AuditResult.SUCCESS)
                .operatorId(userId)
                .operatorName(username)
                .targetType("AUTH_SESSION")
                .targetId(userId)
                .summary("用户退出登录")
                .createdAt(LocalDateTime.now())
                .build());
        return Result.success();
    }

    /**
     * 获取当前登录用户的权限码集合。
     */
    @GetMapping("/permissions")
    public Result<Set<String>> getPermissions() {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error("未登录");
        }
        return Result.success(
                PermissionUtil.getUserPermissions(userId));
    }

    private LoginUserVO toLoginUser(SysUser user) {
        LoginUserVO loginUser = new LoginUserVO();
        loginUser.setId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setNickname(user.getNickname());
        loginUser.setAvatar(user.getAvatar());
        loginUser.setEmail(user.getEmail());
        loginUser.setPhone(user.getPhone());
        loginUser.setPasswordResetRequired(
                Boolean.TRUE.equals(user.getPasswordResetRequired()));
        if (user.getRoles() != null) {
            loginUser.setRoles(user.getRoles().stream()
                    .map(role -> role.getRoleCode())
                    .collect(Collectors.toList()));
        }
        return loginUser;
    }

    private void recordLogin(
            String attemptedUsername,
            SysUser user,
            AuditResult result,
            String errorMessage) {
        String auditUsername = user == null
                ? maskUsername(attemptedUsername)
                : user.getUsername();
        auditPort.record(SystemAuditEvent.builder()
                .module(AuditModule.SECURITY)
                .action(AuditAction.LOGIN)
                .operationName("用户登录")
                .riskLevel(result == AuditResult.SUCCESS
                        ? AuditRiskLevel.LOW
                        : AuditRiskLevel.HIGH)
                .result(result)
                .operatorId(user == null ? null : user.getId())
                .operatorName(auditUsername)
                .targetType("AUTH_SESSION")
                .targetId(user == null ? null : user.getId())
                .targetName(auditUsername)
                .summary(result == AuditResult.SUCCESS
                        ? "用户登录成功"
                        : "用户登录失败")
                .errorCode(result == AuditResult.FAILURE
                        ? "AUTHENTICATION_FAILED"
                        : null)
                .errorMessage(errorMessage)
                .createdAt(LocalDateTime.now())
                .build());
    }

    private String maskUsername(String username) {
        if (username == null || username.length() <= 2) {
            return "***";
        }
        return username.charAt(0)
                + "***"
                + username.charAt(username.length() - 1);
    }
}
