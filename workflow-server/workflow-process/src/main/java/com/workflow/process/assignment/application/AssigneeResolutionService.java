package com.workflow.process.assignment.application;

import com.workflow.contracts.identity.resolver.PersonPrincipal;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.process.assignment.domain.AssigneeResolutionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;

/** 统一返回结构化的办理人解析结果，供运行时、发布预检和测试中心共用。 */
@Service
@RequiredArgsConstructor
public class AssigneeResolutionService {

    private final PersonResolverRuntimeService resolverRuntimeService;

    /** 调用受控人员解析器，并显式区分成功、空结果和错误。 */
    public AssigneeResolutionResult resolveConfigured(
            String resolverCode,
            PersonResolveRequest request) {
        if (!StringUtils.hasText(resolverCode)) {
            return AssigneeResolutionResult.empty(
                    "RESOLVER_CODE_MISSING", "未配置人员解析器", resolverCode);
        }
        try {
            if (!resolverRuntimeService.supportsConfigured(
                    resolverCode, request.usage())) {
                return AssigneeResolutionResult.error(
                        "RESOLVER_UNAVAILABLE",
                        "人员接口未注册、未启用或不支持当前用途: "
                                + request.usage(),
                        resolverCode);
            }
            List<String> users = distinct(
                    resolverRuntimeService.resolveUsernames(resolverCode, request));
            return users.isEmpty()
                    ? AssigneeResolutionResult.empty(
                    "RESOLVER_EMPTY", "人员接口未返回可用办理人", resolverCode)
                    : AssigneeResolutionResult.resolved(users, resolverCode);
        } catch (RuntimeException error) {
            return AssigneeResolutionResult.error(
                    "RESOLVER_ERROR", safeMessage(error), resolverCode);
        }
    }

    /** 解析固定用户、组或角色，并验证最终至少存在一个有效用户。 */
    public AssigneeResolutionResult resolvePrincipals(
            List<PersonPrincipal> principals,
            String reasonCode) {
        try {
            List<String> users = distinct(
                    resolverRuntimeService.resolvePrincipalUsernames(principals));
            return users.isEmpty()
                    ? AssigneeResolutionResult.empty(
                    reasonCode, "固定用户、组或角色未解析到启用用户", null)
                    : AssigneeResolutionResult.resolved(users, null);
        } catch (RuntimeException error) {
            return AssigneeResolutionResult.error(
                    reasonCode + "_ERROR", safeMessage(error), null);
        }
    }

    private List<String> distinct(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (values != null) {
            values.stream().filter(StringUtils::hasText)
                    .map(String::trim).forEach(result::add);
        }
        return List.copyOf(result);
    }

    private String safeMessage(Throwable error) {
        return StringUtils.hasText(error.getMessage())
                ? error.getMessage() : error.getClass().getSimpleName();
    }
}
