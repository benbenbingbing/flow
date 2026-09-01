package com.workflow.service;

import com.workflow.entity.ui.application.UiReleaseResolutionTokenService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.admin.security.context.UserContext;
import com.workflow.contracts.embed.EmbedDelegatedRequestContext;
import com.workflow.contracts.ui.runtime.UiRuntimePurpose;
import com.workflow.contracts.ui.runtime.UiRuntimeResolutionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiReleaseResolutionTokenServiceTest {

    private static final String SECRET = "ui-release-resolution-test-secret";

    private ObjectMapper objectMapper;
    private UiReleaseResolutionTokenService tokenService;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        tokenService = new UiReleaseResolutionTokenService(objectMapper);
        ReflectionTestUtils.setField(tokenService, "secret", SECRET);
        UserContext.setCurrentUser("user-1", "alice");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void issueAndVerifyPreservesSignedRuntimeContext() {
        UiRuntimeResolutionContext context =
                new UiRuntimeResolutionContext(
                        UiRuntimePurpose.ACTIVE_TASK,
                        "history-1",
                        "node-approve");

        String token = tokenService.issue(
                context,
                "form-parent",
                "release-parent",
                7,
                2);

        assertNotNull(token);
        UiReleaseResolutionTokenService.Claims claims =
                tokenService.verify(token);
        assertAll(
                () -> assertEquals(context, claims.context()),
                () -> assertEquals(
                        "form-parent",
                        claims.parentFormId()),
                () -> assertEquals(
                        "release-parent",
                        claims.parentReleaseId()),
                () -> assertEquals(7, claims.parentReleaseVersion()),
                () -> assertEquals(2, claims.depth()),
                () -> assertEquals("user-1", claims.userId()),
                () -> assertEquals(
                        300L,
                        claims.expiresAt() - claims.issuedAt()),
                () -> assertTrue(
                        claims.expiresAt()
                                > Instant.now().getEpochSecond()));
    }

    @Test
    void sessionBoundTokenOutlivesDefaultTtlWithoutExceedingSession() {
        Instant sessionAbsoluteExpiry = Instant.now()
                .plusSeconds(1_800)
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        String token = tokenService.issue(
                UiRuntimeResolutionContext.standalone(),
                "form-parent",
                "release-parent",
                7,
                0,
                sessionAbsoluteExpiry);

        assertNotNull(token);
        UiReleaseResolutionTokenService.Claims claims =
                tokenService.verify(token);
        assertEquals(
                sessionAbsoluteExpiry.getEpochSecond(),
                claims.expiresAt());
        assertTrue(
                claims.expiresAt() - claims.issuedAt() > 300L,
                "长驻运行容器不应在默认五分钟后失去发布上下文");
    }

    @Test
    void sessionBoundTokenRejectsInvalidAbsoluteExpiry() {
        assertNull(tokenService.issue(
                UiRuntimeResolutionContext.standalone(),
                "form-parent",
                "release-parent",
                7,
                0,
                Instant.now().minusSeconds(1)));
        assertNull(tokenService.issue(
                UiRuntimeResolutionContext.standalone(),
                "form-parent",
                "release-parent",
                7,
                0,
                Instant.now().plusSeconds(90_000)));
    }

    @Test
    void embedBoundFormTokenRequiresExactSessionAndViewRelease() {
        String token;
        try (EmbedDelegatedRequestContext.Scope ignored =
                     EmbedDelegatedRequestContext.openSession(
                             "session-1", "view-release-1")) {
            token = tokenService.issue(
                    UiRuntimeResolutionContext.standalone(),
                    "form-parent", "release-parent", 7, 0,
                    Instant.now().plusSeconds(900));
            UiReleaseResolutionTokenService.Claims claims =
                    tokenService.verify(token);
            assertEquals("session-1", claims.embedSessionId());
            assertEquals(
                    "view-release-1", claims.embedViewReleaseId());
        }

        assertThrows(
                BusinessForbiddenException.class,
                () -> tokenService.verify(token),
                "Embed-bound token 不能离开原 Session 使用");
        try (EmbedDelegatedRequestContext.Scope ignored =
                     EmbedDelegatedRequestContext.openSession(
                             "session-2", "view-release-1")) {
            assertThrows(
                    BusinessForbiddenException.class,
                    () -> tokenService.verify(token));
        }
        try (EmbedDelegatedRequestContext.Scope ignored =
                     EmbedDelegatedRequestContext.openSession(
                             "session-1", "view-release-2")) {
            assertThrows(
                    BusinessForbiddenException.class,
                    () -> tokenService.verify(token));
        }
    }

    @Test
    void embedSessionRejectsOrdinaryUnboundFlowFormToken() {
        String ordinaryToken = tokenService.issue(
                UiRuntimeResolutionContext.standalone(),
                "form-parent", "release-parent", 7, 0);

        try (EmbedDelegatedRequestContext.Scope ignored =
                     EmbedDelegatedRequestContext.openSession(
                             "session-1", "view-release-1")) {
            assertThrows(
                    BusinessForbiddenException.class,
                    () -> tokenService.verify(ordinaryToken),
                    "普通 Flow token 不能被带入 Embed 会话");
        }
    }

    @Test
    void embedListTokenPinsReleaseUserAndSessionExpiry() {
        Instant sessionAbsoluteExpiry = Instant.now()
                .plusSeconds(1_800)
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);

        String token = tokenService.issueEmbedList(
                "work_order", "list-1", "list-release-7", 7,
                "session-1", "view-release-1",
                sessionAbsoluteExpiry);

        assertNotNull(token);
        assertTrue(tokenService.isEmbedListToken(token));
        UiReleaseResolutionTokenService.EmbedListClaims claims =
                tokenService.verifyEmbedList(token);
        assertAll(
                () -> assertEquals("work_order", claims.entityCode()),
                () -> assertEquals("list-1", claims.listConfigId()),
                () -> assertEquals("list-release-7", claims.releaseId()),
                () -> assertEquals(7, claims.releaseVersion()),
                () -> assertEquals("session-1", claims.sessionId()),
                () -> assertEquals("view-release-1", claims.viewReleaseId()),
                () -> assertEquals("user-1", claims.userId()),
                () -> assertEquals(
                        sessionAbsoluteExpiry.getEpochSecond(),
                        claims.expiresAt()));
    }

    @Test
    void embedListTokenRejectsTamperingAndAnotherMappedUser() {
        String token = tokenService.issueEmbedList(
                "work_order", "list-1", "list-release-7", 7,
                "session-1", "view-release-1",
                Instant.now().plusSeconds(900));
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("A") ? "B" : "A");

        assertThrows(
                BusinessForbiddenException.class,
                () -> tokenService.verifyEmbedList(tampered));
        UserContext.setCurrentUser("user-2", "bob");
        BusinessForbiddenException otherUser = assertThrows(
                BusinessForbiddenException.class,
                () -> tokenService.verifyEmbedList(token));
        assertTrue(otherUser.getMessage().contains("当前用户"));
    }

    @Test
    void verifyRejectsTokenIssuedForAnotherUser() {
        String token = tokenService.issue(
                UiRuntimeResolutionContext.historical(
                        "history-2",
                        "node-review"),
                "form-parent",
                "release-parent",
                3,
                1);
        UserContext.setCurrentUser("user-2", "bob");

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> tokenService.verify(token));

        assertEquals(
                "INVALID_RELEASE_RESOLUTION_TOKEN",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("当前用户"));
    }

    @Test
    void verifyRejectsTamperedSignature() {
        String token = tokenService.issue(
                UiRuntimeResolutionContext.standalone(),
                "form-parent",
                "release-parent",
                1,
                0);
        String[] parts = token.split("\\.", -1);
        char replacement = parts[1].endsWith("A") ? 'B' : 'A';
        String tampered = parts[0]
                + "."
                + parts[1].substring(0, parts[1].length() - 1)
                + replacement;

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> tokenService.verify(tampered));

        assertEquals(
                "INVALID_RELEASE_RESOLUTION_TOKEN",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("签名无效"));
    }

    @Test
    void verifyRejectsCorrectlySignedExpiredToken() throws Exception {
        long now = Instant.now().getEpochSecond();
        UiReleaseResolutionTokenService.Claims expiredClaims =
                new UiReleaseResolutionTokenService.Claims(
                        UiRuntimePurpose.ACTIVE_TASK,
                        "history-3",
                        "node-approve",
                        "form-parent",
                        "release-parent",
                        2,
                        1,
                        "user-1",
                        null,
                        null,
                        now - 600,
                        now - 1);

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> tokenService.verify(sign(expiredClaims)));

        assertEquals(
                "INVALID_RELEASE_RESOLUTION_TOKEN",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("已过期"));
    }

    @Test
    void depthLimitAppliesDuringIssueAndVerification() throws Exception {
        UiRuntimeResolutionContext context =
                new UiRuntimeResolutionContext(
                        UiRuntimePurpose.NEW_INSTANCE,
                        "history-4",
                        "node-start");

        assertNull(tokenService.issue(
                context,
                "form-parent",
                "release-parent",
                1,
                8));

        long now = Instant.now().getEpochSecond();
        UiReleaseResolutionTokenService.Claims excessiveDepthClaims =
                new UiReleaseResolutionTokenService.Claims(
                        UiRuntimePurpose.NEW_INSTANCE,
                        "history-4",
                        "node-start",
                        "form-parent",
                        "release-parent",
                        1,
                        8,
                        "user-1",
                        null,
                        null,
                        now,
                        now + 300);

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> tokenService.verify(sign(excessiveDepthClaims)));

        assertEquals(
                "INVALID_RELEASE_RESOLUTION_TOKEN",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("深度超过限制"));
    }

    private String sign(
            UiReleaseResolutionTokenService.Claims claims)
            throws Exception {
        String payload = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(objectMapper.writeValueAsBytes(claims));
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(
                SECRET.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        String signature = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(mac.doFinal(
                        payload.getBytes(StandardCharsets.UTF_8)));
        return payload + "." + signature;
    }
}
