package com.workflow.project.custom;

import com.workflow.contracts.identity.IdentityDirectoryPort;
import com.workflow.contracts.identity.IdentityUser;
import com.workflow.contracts.identity.external.ExternalIdentityResolutionRequest;
import com.workflow.contracts.identity.external.ExternalIdentityResolver;
import com.workflow.core.logging.LogValue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 外部身份映射示例。
 *
 * <p>仅处理命名空间 {@code project-demo}，并通过平台用户目录做精确匹配，
 * 不接受模糊匹配或客户端声明的任意内部用户。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProjectCustomExternalIdentityResolver
        implements ExternalIdentityResolver {

    public static final String NAMESPACE = "project-demo";

    private final IdentityDirectoryPort
            identityDirectoryPort;

    @Override
    public boolean supports(String namespace) {
        return NAMESPACE.equalsIgnoreCase(namespace);
    }

    @Override
    public Optional<String> resolve(
            ExternalIdentityResolutionRequest request) {
        Optional<String> resolved =
                identityDirectoryPort
                        .findUser(request.externalUserId())
                        .map(IdentityUser::username);
        log.info(
                "项目外部身份解析完成: namespace={}, externalSystem={}, processKey={}, resolved={}",
                LogValue.safe(request.namespace()),
                LogValue.safe(request.externalSystem()),
                LogValue.safe(request.processKey()),
                resolved.isPresent());
        return resolved;
    }
}
