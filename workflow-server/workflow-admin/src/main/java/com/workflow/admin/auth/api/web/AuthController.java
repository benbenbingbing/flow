package com.workflow.admin.auth.api.web;

import com.workflow.core.security.AuthenticatedApi;
import com.workflow.core.security.PublicApi;

import com.workflow.admin.auth.infrastructure.ClientAddressResolver;
import com.workflow.admin.auth.application.AuthSessionException;
import com.workflow.admin.auth.application.AuthSessionProperties;
import com.workflow.admin.auth.application.AuthSessionService;
import com.workflow.admin.auth.application.AuthTokenBundle;
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
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;

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

    /** 用户不存在时参与恒定耗时密码校验的虚拟 BCrypt 摘要。 */
    private static final String DUMMY_PASSWORD_HASH =
            "$2y$10$KVN3n7mW3JkwqTki/svFdOxdOdcp8M3vicjVv."
                    + "yd6jGqw.zyTV9OK";

    /** 用户查询、密码更新和会话全量撤销服务。 */
    private final SysUserService userService;
    /** 认证操作审计端口。 */
    private final SystemAuditPort auditPort;
    /** 登录失败频率限制服务。 */
    private final LoginThrottleService loginThrottleService;
    /** 登录来源地址解析器。 */
    private final ClientAddressResolver clientAddressResolver;
    /** 浏览器刷新会话和 Access Token 服务。 */
    private final AuthSessionService authSessionService;
    /** Refresh Token Cookie 配置。 */
    private final AuthSessionProperties sessionProperties;

    /**
     * 用户登录。
     */
    @PublicApi
    @PostMapping("/login")
    public Result<LoginUserVO> login(
            @Validated @RequestBody LoginDTO loginDTO,
            HttpServletRequest request,
            HttpServletResponse response) {
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

        AuthTokenBundle tokens =
                authSessionService.createSession(user);
        LoginUserVO loginUser = toLoginUser(tokens);
        writeRefreshCookie(response, tokens);

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
    public Result<LoginUserVO> changePassword(
            @Validated @RequestBody ChangePasswordDTO request,
            HttpServletResponse response) {
        String userId = UserContext.getUserId();
        if (userId == null) {
            return Result.error("未登录");
        }
        AuthTokenBundle tokens =
                authSessionService.changePasswordAndCreateSession(
                        userId,
                        request.getCurrentPassword(),
                        request.getNewPassword());
        writeRefreshCookie(response, tokens);
        return Result.success(toLoginUser(tokens));
    }

    /**
     * 退出登录。
     */
    @PublicApi
    @PostMapping("/logout")
    public Result<Void> logout(
            HttpServletRequest request,
            HttpServletResponse response) {
        String refreshToken = readRefreshToken(request);
        authSessionService.revokeCurrent(
                refreshToken,
                "USER_LOGOUT");
        clearRefreshCookie(response);
        auditPort.record(SystemAuditEvent.builder()
                .module(AuditModule.SECURITY)
                .action(AuditAction.LOGOUT)
                .operationName("用户退出登录")
                .riskLevel(AuditRiskLevel.LOW)
                .result(AuditResult.SUCCESS)
                .operatorId(UserContext.getUserId())
                .operatorName(UserContext.getUsername())
                .targetType("AUTH_SESSION")
                .targetId(UserContext.getSessionId())
                .summary("用户退出登录")
                .createdAt(LocalDateTime.now())
                .build());
        return Result.success();
    }

    /**
     * 使用 HttpOnly Refresh Token 恢复或延续当前浏览器会话。
     */
    @PublicApi
    @PostMapping("/refresh")
    public Result<LoginUserVO> refresh(
            HttpServletRequest request,
            HttpServletResponse response) {
        try {
            AuthTokenBundle tokens =
                    authSessionService.refresh(
                            readRefreshToken(request));
            writeRefreshCookie(response, tokens);
            return Result.success(toLoginUser(tokens));
        } catch (AuthSessionException exception) {
            clearRefreshCookie(response);
            response.setStatus(
                    HttpServletResponse.SC_UNAUTHORIZED);
            return Result.error(
                    401,
                    exception.getErrorCode(),
                    exception.getMessage());
        }
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

    private LoginUserVO toLoginUser(
            AuthTokenBundle tokens) {
        LoginUserVO loginUser =
                toLoginUser(tokens.user());
        loginUser.setToken(tokens.accessToken());
        loginUser.setTokenExpiresAt(
                tokens.accessTokenExpiresAt().toString());
        return loginUser;
    }

    private void writeRefreshCookie(
            HttpServletResponse response,
            AuthTokenBundle tokens) {
        long maxAgeSeconds = Math.max(
                1L,
                Duration.between(
                        java.time.Instant.now(),
                        tokens.sessionAbsoluteExpiresAt())
                        .toSeconds());
        ResponseCookie cookie = ResponseCookie
                .from(
                        sessionProperties.getCookieName(),
                        tokens.refreshToken())
                .httpOnly(true)
                .secure(sessionProperties.isCookieSecure())
                .sameSite(
                        sessionProperties.getCookieSameSite())
                .path(sessionProperties.getCookiePath())
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString());
    }

    private void clearRefreshCookie(
            HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie
                .from(
                        sessionProperties.getCookieName(),
                        "")
                .httpOnly(true)
                .secure(sessionProperties.isCookieSecure())
                .sameSite(
                        sessionProperties.getCookieSameSite())
                .path(sessionProperties.getCookiePath())
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(
                HttpHeaders.SET_COOKIE,
                cookie.toString());
    }

    private String readRefreshToken(
            HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        return java.util.Arrays.stream(
                        request.getCookies())
                .filter(cookie ->
                        sessionProperties.getCookieName()
                                .equals(cookie.getName()))
                .map(jakarta.servlet.http.Cookie::getValue)
                .findFirst()
                .orElse(null);
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
