package com.workflow.service;

import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveResult;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolver;
import com.workflow.contracts.identity.resolver.PersonResolverDescriptor;
import com.workflow.entity.SysUser;
import com.workflow.mapper.SysGroupMapper;
import com.workflow.mapper.SysOrganizationMapper;
import com.workflow.mapper.SysRoleMapper;
import com.workflow.mapper.SysUserGroupMapper;
import com.workflow.mapper.SysUserMapper;
import com.workflow.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 统一人员解析器运行时契约测试。
 */
class PersonResolverRuntimeServiceTest {

    @Test
    void forwardsFixedContextAndExtraParams() {
        AtomicReference<PersonResolveRequest> captured =
                new AtomicReference<>();
        PersonResolver resolver = resolver(
                Set.of(PersonResolveUsage.CC),
                request -> {
                    captured.set(request);
                    return PersonResolveResult.users(
                            List.of("observer"));
                });
        SysUserMapper userMapper = mock(SysUserMapper.class);
        SysUser observer = enabledUser("observer");
        when(userMapper.selectByUsername("observer"))
                .thenReturn(observer);

        PersonResolverRuntimeService service = service(
                resolver, userMapper);
        PersonResolveRequest request = request(
                PersonResolveUsage.CC,
                Map.of("departmentLevel", 2));

        assertEquals(
                List.of("observer"),
                service.resolveUsernames("testResolver", request));
        assertEquals(
                "process-1",
                captured.get().processInstanceId());
        assertEquals(
                2,
                captured.get().extraParams()
                        .get("departmentLevel"));
    }

    @Test
    void rejectsUnsupportedUsage() {
        PersonResolver resolver = resolver(
                Set.of(PersonResolveUsage.CC),
                request -> PersonResolveResult.users(
                        List.of("observer")));
        PersonResolverRuntimeService service = service(
                resolver, mock(SysUserMapper.class));

        assertFalse(service.supports(
                "testResolver",
                PersonResolveUsage.ASSIGNEE));
        assertThrows(
                IllegalArgumentException.class,
                () -> service.resolveUsernames(
                        "testResolver",
                        request(
                                PersonResolveUsage.ASSIGNEE,
                                Map.of())));
    }

    private PersonResolver resolver(
            Set<PersonResolveUsage> usages,
            java.util.function.Function<
                    PersonResolveRequest,
                    PersonResolveResult> function) {
        return new PersonResolver() {
            @Override
            public PersonResolverDescriptor descriptor() {
                return new PersonResolverDescriptor(
                        "testResolver",
                        "测试人员接口",
                        "测试固定契约",
                        1,
                        1,
                        usages,
                        Map.of(
                                "departmentLevel",
                                Map.of("type", "integer")),
                        true);
            }

            @Override
            public PersonResolveResult resolve(
                    PersonResolveRequest request) {
                return function.apply(request);
            }
        };
    }

    private PersonResolverRuntimeService service(
            PersonResolver resolver,
            SysUserMapper userMapper) {
        return new PersonResolverRuntimeService(
                List.of(resolver),
                userMapper,
                mock(SysRoleMapper.class),
                mock(SysUserRoleMapper.class),
                mock(SysGroupMapper.class),
                mock(SysUserGroupMapper.class),
                mock(SysOrganizationMapper.class));
    }

    private PersonResolveRequest request(
            PersonResolveUsage usage,
            Map<String, Object> extraParams) {
        return new PersonResolveRequest(
                1,
                "trace-1",
                "idempotency-1",
                usage,
                "config-1",
                "definition-1",
                "process-1",
                "business-1",
                "approve",
                "经理审批",
                "task-1",
                "expense",
                "entity-1",
                "starter",
                "admin",
                Map.of("amount", 100),
                Map.of("amount", 100),
                extraParams);
    }

    private SysUser enabledUser(String username) {
        SysUser user = new SysUser();
        user.setUsername(username);
        user.setStatus(SysUser.Status.ENABLED.getValue());
        user.setDeleted(0);
        return user;
    }
}
