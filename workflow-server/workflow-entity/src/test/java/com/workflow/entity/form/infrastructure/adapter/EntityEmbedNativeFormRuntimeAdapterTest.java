package com.workflow.entity.form.infrastructure.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.embed.EmbedDelegatedRequestContext;
import com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.ui.application.UiConfigReleaseService;
import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class EntityEmbedNativeFormRuntimeAdapterTest {

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void nativeRootTokenIsBoundToEmbedAbsoluteSessionExpiry() {
        EntityDefinitionMapper definitionMapper = mock(
                EntityDefinitionMapper.class);
        UiConfigReleaseService releaseService = mock(
                UiConfigReleaseService.class);
        UiReleaseResolutionTokenService tokenService =
                new UiReleaseResolutionTokenService(new ObjectMapper());
        ReflectionTestUtils.setField(
                tokenService,
                "secret",
                "embed-native-form-runtime-test-secret");
        UserContext.setCurrentUser("user-1", "alice");

        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-1");
        definition.setEntityCode("work_order");
        when(definitionMapper.findByEntityCode("work_order"))
                .thenReturn(Optional.of(definition));
        EntityForm form = new EntityForm();
        form.setId("form-1");
        form.setEntityId("entity-1");
        when(releaseService.resolveRuntimeFormRelease(
                "form-1",
                "form-release-3",
                3,
                UiRuntimeResolutionContext.standalone()))
                .thenReturn(new ResolvedEntityFormRelease(
                        form,
                        "form-release-3",
                        3,
                        true));
        EntityEmbedNativeFormRuntimeAdapter adapter =
                new EntityEmbedNativeFormRuntimeAdapter(
                        definitionMapper,
                        releaseService,
                        tokenService);
        Instant sessionAbsoluteExpiry = Instant.now()
                .plusSeconds(1_800)
                .truncatedTo(ChronoUnit.SECONDS);

        try (EmbedDelegatedRequestContext.Scope ignored =
                     EmbedDelegatedRequestContext.openSession(
                             "session-1", "view-release-1")) {
            String token = adapter.issueReleaseResolutionToken(
                    new com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.Target(
                            "work_order",
                            "form-1",
                            "form-release-3",
                            3,
                            sessionAbsoluteExpiry));

            assertNotNull(token);
            UiReleaseResolutionTokenService.Claims claims =
                    tokenService.verify(token);
            when(releaseService.runtimeFormRelease(
                    "form-1", "form-release-3", 3, token))
                    .thenReturn(Map.of(
                            "configId", "form-1",
                            "id", "form-release-3",
                            "version", 3));
            assertEquals(
                    sessionAbsoluteExpiry.getEpochSecond(),
                    claims.expiresAt());
            assertEquals("session-1", claims.embedSessionId());
            assertEquals(
                    "view-release-1", claims.embedViewReleaseId());
            assertTrue(claims.expiresAt() - claims.issuedAt() > 300L);
            assertEquals(
                    new com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.VerifiedTarget(
                            "work_order", "form-1",
                            "form-release-3", 3),
                    adapter.verifyReleaseResolutionToken(
                            token,
                            new com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.VerificationTarget(
                                    "work_order", "form-1",
                                    "form-release-3", 3,
                                    "session-1", "view-release-1",
                                    sessionAbsoluteExpiry)));
        }
    }

    @Test
    void signedParentTokenCanAuthorizeOnlyPlatformResolvedChildRelease() {
        EntityDefinitionMapper definitionMapper = mock(
                EntityDefinitionMapper.class);
        UiConfigReleaseService releaseService = mock(
                UiConfigReleaseService.class);
        UiReleaseResolutionTokenService tokenService =
                new UiReleaseResolutionTokenService(new ObjectMapper());
        ReflectionTestUtils.setField(
                tokenService, "secret",
                "embed-native-form-runtime-test-secret");
        UserContext.setCurrentUser("user-1", "alice");
        EntityDefinition definition = new EntityDefinition();
        definition.setId("entity-2");
        definition.setEntityCode("customer");
        when(definitionMapper.findByEntityCode("customer"))
                .thenReturn(Optional.of(definition));
        EntityForm child = new EntityForm();
        child.setId("child-form");
        child.setEntityId("entity-2");
        when(releaseService.resolveRuntimeFormRelease(
                "child-form", "child-release", 7,
                UiRuntimeResolutionContext.standalone()))
                .thenReturn(new ResolvedEntityFormRelease(
                        child, "child-release", 7, true));
        EntityEmbedNativeFormRuntimeAdapter adapter =
                new EntityEmbedNativeFormRuntimeAdapter(
                        definitionMapper, releaseService, tokenService);
        Instant expiry = Instant.now().plusSeconds(900)
                .truncatedTo(ChronoUnit.SECONDS);

        try (EmbedDelegatedRequestContext.Scope ignored =
                     EmbedDelegatedRequestContext.openSession(
                             "session-1", "view-release-1")) {
            String parentToken = tokenService.issue(
                    UiRuntimeResolutionContext.standalone(),
                    "parent-form", "parent-release", 3, 0);
            when(releaseService.runtimeFormRelease(
                    "child-form", "child-release", 7, parentToken))
                    .thenReturn(Map.of(
                            "configId", "child-form",
                            "id", "child-release",
                            "version", 7));

            assertEquals(
                    new com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.VerifiedTarget(
                            "customer", "child-form", "child-release", 7),
                    adapter.verifyRuntimeReleaseRequest(
                            parentToken,
                            new com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.VerificationTarget(
                                    "customer", "child-form",
                                    "child-release", 7,
                                    "session-1", "view-release-1", expiry)));
            assertThrows(
                    IllegalArgumentException.class,
                    () -> adapter.verifyReleaseResolutionToken(
                            parentToken,
                            new com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.VerificationTarget(
                                    "customer", "child-form",
                                    "child-release", 7,
                                    "session-1", "view-release-1", expiry)),
                    "按钮/事件不能直接使用父表单 token");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> adapter.verifyRuntimeReleaseRequest(
                            parentToken,
                            new com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.VerificationTarget(
                                    "customer", "child-form",
                                    "child-release", 7,
                                    "session-1", "view-release-1",
                                    Instant.now().plusSeconds(60))),
                    "token 到期时间不能越过 Session 上限");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> adapter.verifyRuntimeReleaseRequest(
                            parentToken,
                            new com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.VerificationTarget(
                                    "customer", "child-form",
                                    "child-release", 7,
                                    "session-2", "view-release-1", expiry)),
                    "父子表单解析也必须绑定当前 Embed Session");

            when(releaseService.runtimeFormRelease(
                    "unreferenced-form", "unreferenced-release", 1,
                    parentToken)).thenThrow(new BusinessForbiddenException(
                            "CHILD_FORM_RELEASE_NOT_REFERENCED",
                            "请求的子表单发布版本不属于父表单有效快照"));
            assertThrows(
                    BusinessForbiddenException.class,
                    () -> adapter.verifyRuntimeReleaseRequest(
                            parentToken,
                            new com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort.VerificationTarget(
                                    null, "unreferenced-form",
                                    "unreferenced-release", 1,
                                    "session-1", "view-release-1", expiry)));
        }
    }
}
