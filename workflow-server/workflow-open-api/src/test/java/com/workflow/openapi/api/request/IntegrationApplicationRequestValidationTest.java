package com.workflow.openapi.api.request;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.workflow.openapi.application.IntegrationScope;
import jakarta.validation.Validation;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class IntegrationApplicationRequestValidationTest {

    /**
     * 创建和更新请求必须接受当前全部受支持的 Scope，避免新增 Scope 后请求层的固定数量上限
     * 与业务白名单再次发生漂移。
     */
    @Test
    void acceptsEverySupportedScopeForCreateAndAccessUpdate() {
        Set<String> allSupportedScopes = Arrays.stream(IntegrationScope.values())
                .map(IntegrationScope::value)
                .collect(Collectors.toUnmodifiableSet());
        var createRequest = new CreateIntegrationApplicationRequest(
                "Development integration",
                null,
                null,
                allSupportedScopes,
                Set.of(),
                null,
                null,
                null,
                null);
        var updateRequest = new UpdateIntegrationAccessRequest(
                allSupportedScopes,
                Set.of(),
                0L);

        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertTrue(validator.validate(createRequest).isEmpty());
            assertTrue(validator.validate(updateRequest).isEmpty());
        }
    }
}
