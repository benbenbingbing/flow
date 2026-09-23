package com.workflow.admin.identity.user.bootstrap;

import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.contracts.bootstrap.port.BootstrapJobPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Activates the disabled bootstrap administrator from an external Secret.
 *
 * <p>The conditional update is safe when multiple application Pods start at
 * the same time. The password itself is never logged or returned.</p>
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name = "workflow.bootstrap.enabled",
        havingValue = "true",
        matchIfMissing = true)
@Order(20)
@RequiredArgsConstructor
public class BootstrapAdministratorRunner implements ApplicationRunner {

    static final String LEGACY_BOOTSTRAP_HASH =
            "$2y$10$VPL8vj30niywnU1gYVZGNOiPqQVACc8gG2n81hbOKQlH/.gxI8ZF6";

    private final SysUserMapper userMapper;
    private final BootstrapJobPort bootstrapJobCoordinator;

    @Value("${workflow.bootstrap.admin.password:}")
    private String bootstrapPassword;

    /**
     * 执行初始化管理员{@code runner}，并将结果传给后续步骤。
     *
     * @param args {@code args}，供本方法执行初始化管理员{@code runner}时使用
     */
    @Override
    public void run(ApplicationArguments args) {
        bootstrapJobCoordinator.executeOnce(
                "bootstrap-administrator",
                1,
                () -> {
                    initializeAdministrator();
                    return true;
                });
    }

    /**
     * 处理{@code initialize}管理员，并将结果传给后续步骤。
     *
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private void initializeAdministrator() {
        if (!StringUtils.hasText(bootstrapPassword)) {
            if (userMapper.isBootstrapAdministratorPending(LEGACY_BOOTSTRAP_HASH)) {
                throw new IllegalStateException(
                        "WORKFLOW_BOOTSTRAP_ADMIN_PASSWORD is required "
                                + "while the bootstrap administrator is pending");
            }
            log.info("Bootstrap administrator is already initialized; no bootstrap Secret was supplied");
            return;
        }
        validatePassword(bootstrapPassword);
        String passwordHash = new BCryptPasswordEncoder().encode(bootstrapPassword);
        int activated = userMapper.activateBootstrapAdministrator(
                passwordHash,
                LEGACY_BOOTSTRAP_HASH);
        if (activated == 1) {
            log.info("Bootstrap administrator activated from external Secret");
        } else {
            log.info("Bootstrap administrator was already initialized or customized");
        }
    }

    /**
     * 校验密码；不满足约束时阻止后续处理。
     *
     * @param password 密码，作为 {@code IllegalStateException} 的输入影响后续处理
     * @throws IllegalStateException 当前业务状态不允许继续处理时抛出
     */
    private void validatePassword(String password) {
        if (password.length() < 14 || password.length() > 72
                || !password.chars().anyMatch(Character::isLowerCase)
                || !password.chars().anyMatch(Character::isUpperCase)
                || !password.chars().anyMatch(Character::isDigit)) {
            throw new IllegalStateException(
                    "Bootstrap administrator password must be 14-72 characters "
                            + "and contain uppercase, lowercase, and numeric characters");
        }
        String normalized = password.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("admin")
                || normalized.contains("password")
                || normalized.contains("replace-with")) {
            throw new IllegalStateException(
                    "Bootstrap administrator password uses a forbidden public pattern");
        }
    }
}
