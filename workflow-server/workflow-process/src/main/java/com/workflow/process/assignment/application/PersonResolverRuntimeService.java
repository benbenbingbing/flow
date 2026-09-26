package com.workflow.process.assignment.application;

import com.workflow.contracts.process.assignment.model.PersonPrincipal;
import com.workflow.contracts.process.assignment.model.PersonResolveRequest;
import com.workflow.contracts.process.assignment.model.PersonResolveUsage;
import com.workflow.contracts.process.assignment.spi.PersonResolverProvider;
import com.workflow.contracts.identity.port.IdentityMembershipPort;
import com.workflow.contracts.process.assignment.port.PersonResolverRegistrationPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 统一人员解析器运行时入口。
 */
@Service
@RequiredArgsConstructor
public class PersonResolverRuntimeService {

    private final List<PersonResolverProvider> resolvers;
    private final IdentityMembershipPort memberships;
    private final PersonResolverRegistrationPort registrations;

    /**
     * 判断是否支持人员解析器运行时；判断结果决定调用方的后续分支。
     *
     * @param resolverCode 解析器编码，后续用于判断是否支持人员解析器运行时时定位或关联目标
     * @param usage 使用场景，供本方法判断是否支持人员解析器运行时时使用
     * @return 人员解析器运行时条件成立时为 true，否则为 false
     */
    public boolean supports(String resolverCode, PersonResolveUsage usage) {
        PersonResolverProvider resolver = find(resolverCode);
        return resolver != null
                && (resolver.descriptor().supportedUsages().isEmpty()
                || resolver.descriptor().supportedUsages().contains(usage));
    }

    /**
     * 校验解析器实现、用途以及受控目录启用状态。
     *
     * @param resolverCode 解析器编码，后续用于判断是否支持已配置时定位或关联目标
     * @param usage 使用场景，供本方法判断是否支持已配置时使用
     * @return 已配置条件成立时为 true，否则为 false
     */
    public boolean supportsConfigured(
            String resolverCode,
            PersonResolveUsage usage) {
        if (!supports(resolverCode, usage)) {
            return false;
        }
        return registrations.isEnabled(resolverCode);
    }

    /**
     * 校验并获取已配置；不满足约束时阻止后续处理。
     *
     * @param resolverCode 解析器编码，后续用于校验并获取已配置时定位或关联目标
     * @param usage 使用场景，作为 {@code IllegalArgumentException} 的输入影响后续处理
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public void requireConfigured(
            String resolverCode,
            PersonResolveUsage usage) {
        if (!supportsConfigured(resolverCode, usage)) {
            throw new IllegalArgumentException(
                    "人员接口未配置、未启用、不可用或不支持用途 "
                            + usage.name()
                            + ": "
                            + resolverCode);
        }
    }

    /**
     * 解析{@code usernames}；输出作为后续校验或处理的输入。
     *
     * @param resolverCode 解析器编码，后续用于解析{@code usernames}时定位或关联目标
     * @param request 本次请求，后续经校验后用于解析{@code usernames}
     * @return 人员解析器集合，供调用方遍历或展示
     * @throws IllegalArgumentException 输入参数或目标数据不满足方法前置条件时抛出
     */
    public List<String> resolveUsernames(
            String resolverCode,
            PersonResolveRequest request) {
        PersonResolverProvider resolver = find(resolverCode);
        if (resolver == null) {
            throw new IllegalArgumentException(
                    "未注册人员解析器: " + resolverCode);
        }
        if (!resolver.descriptor().supportedUsages().isEmpty()
                && !resolver.descriptor().supportedUsages()
                .contains(request.usage())) {
            throw new IllegalArgumentException(
                    "人员解析器不支持用途 "
                            + request.usage().name()
                            + ": "
                            + resolverCode);
        }

        return resolvePrincipalUsernames(
                resolver.resolve(request).principals());
    }

    /**
     * 将用户、角色、用户组或组织主体统一展开为启用且未删除的本地用户名。
     * Flowable 任务监听器也使用该入口校验实际 identity link，避免静态配置
     * 绕过受控解析器已有的本地用户安全边界。
     *
     * @param principals {@code principals}，供本方法解析{@code principal}{@code usernames}时使用
     * @return 人员解析器集合，供调用方遍历或展示
     */
    public List<String> resolvePrincipalUsernames(
            Collection<PersonPrincipal> principals) {
        LinkedHashSet<String> users = new LinkedHashSet<>();
        if (principals == null) {
            return List.of();
        }
        for (PersonPrincipal principal : principals) {
            if (principal == null) {
                continue;
            }
            List<String> resolved = switch (principal.type()) {
                case USER -> memberships.users(List.of(principal.key()));
                case ROLE -> memberships.roles(List.of(principal.key()));
                case GROUP -> memberships.groups(List.of(principal.key()));
                case ORGANIZATION ->
                        memberships.organizations(List.of(principal.key()));
            };
            users.addAll(resolved);
        }
        return new ArrayList<>(users);
    }

    /**
     * 查询人员解析器；结果供调用方展示或继续处理。
     *
     * @param resolverCode 解析器编码，后续用于查询人员解析器运行时时定位或关联目标
     * @return 符合条件的人员解析器结果，供调用方继续处理
     */
    private PersonResolverProvider find(String resolverCode) {
        if (!StringUtils.hasText(resolverCode)) {
            return null;
        }
        return resolvers.stream()
                .filter(item -> item.descriptor().code()
                        .equalsIgnoreCase(resolverCode))
                .findFirst()
                .orElse(null);
    }

}
