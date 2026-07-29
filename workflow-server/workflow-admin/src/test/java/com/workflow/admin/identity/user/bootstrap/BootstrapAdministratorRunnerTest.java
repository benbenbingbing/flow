package com.workflow.admin.identity.user.bootstrap;

import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.contracts.bootstrap.BootstrapJobCoordinator;
import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BootstrapAdministratorRunnerTest {

    @Test
    void activatesLegacyAccountWithExternallySuppliedPassword() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.activateBootstrapAdministrator(anyString(), anyString()))
                .thenReturn(1);
        BootstrapAdministratorRunner runner = runner(mapper);
        ReflectionTestUtils.setField(
                runner,
                "bootstrapPassword",
                "LocalOnly-9xQ7!Secure");

        runner.run(null);

        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(mapper).activateBootstrapAdministrator(
                hash.capture(),
                org.mockito.ArgumentMatchers.eq(
                        BootstrapAdministratorRunner.LEGACY_BOOTSTRAP_HASH));
        assertTrue(new BCryptPasswordEncoder().matches(
                "LocalOnly-9xQ7!Secure",
                hash.getValue()));
    }

    @Test
    void blankSecretIsAllowedAfterBootstrapWasConsumed() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.isBootstrapAdministratorPending(anyString())).thenReturn(false);
        BootstrapAdministratorRunner runner = runner(mapper);
        ReflectionTestUtils.setField(runner, "bootstrapPassword", "");

        runner.run(null);

        verify(mapper, never()).activateBootstrapAdministrator(anyString(), anyString());
    }

    @Test
    void blankSecretFailsStartupWhileBootstrapIsPending() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        when(mapper.isBootstrapAdministratorPending(anyString())).thenReturn(true);
        BootstrapAdministratorRunner runner = runner(mapper);
        ReflectionTestUtils.setField(runner, "bootstrapPassword", "");

        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(mapper, never()).activateBootstrapAdministrator(anyString(), anyString());
    }

    @Test
    void weakBootstrapSecretFailsClosed() {
        SysUserMapper mapper = mock(SysUserMapper.class);
        BootstrapAdministratorRunner runner = runner(mapper);
        ReflectionTestUtils.setField(runner, "bootstrapPassword", "admin-password");

        assertThrows(IllegalStateException.class, () -> runner.run(null));
        verify(mapper, never()).activateBootstrapAdministrator(anyString(), anyString());
    }

    private BootstrapAdministratorRunner runner(SysUserMapper mapper) {
        return new BootstrapAdministratorRunner(
                mapper,
                new BootstrapJobCoordinator() {
                    @Override
                    public <T> Optional<T> executeOnce(
                            String jobName,
                            int requiredVersion,
                            Supplier<T> action) {
                        return Optional.ofNullable(action.get());
                    }
                });
    }
}
