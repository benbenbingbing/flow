package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.BusinessForbiddenException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiViewCompositionTokenServiceTest {

    private UiViewCompositionTokenService service;

    @BeforeEach
    void setUp() {
        UserContext.setCurrentUser("user-1", "reader");
        service = new UiViewCompositionTokenService(new ObjectMapper());
        ReflectionTestUtils.setField(
                service,
                "secret",
                "unit-test-view-composition-secret");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void targetListTokenKeepsPinnedReleaseAndTrustedFilters() {
        String token = service.issueTargetList(
                "FORM",
                "host-form",
                "host-release",
                7,
                "project-requirements",
                "project",
                "project-1",
                "requirement",
                "requirement-list",
                "list-release",
                3,
                Map.of("projectId", "project-1"),
                false);

        UiViewCompositionTokenService.Claims claims =
                service.verifyTargetList(token);

        assertEquals("list-release", claims.targetReleaseId());
        assertEquals(3, claims.targetReleaseVersion());
        assertEquals(
                Map.of("projectId", "project-1"),
                claims.fixedFilters());
        assertEquals("user-1", claims.userId());
    }

    @Test
    void modifiedTokenAndWrongPurposeFailClosed() {
        String rowToken = service.issueSourceRow(
                "LIST",
                "host-list",
                "host-release",
                2,
                "row-project",
                "requirement",
                "requirement-1");

        assertThrows(
                BusinessForbiddenException.class,
                () -> service.verifyTargetList(rowToken));
        assertThrows(
                BusinessForbiddenException.class,
                () -> service.verifySourceRow(rowToken + "x"));
    }

    @Test
    void candidateListPurposeIsAcceptedForQueryButNotOrdinaryMembership() {
        String token = service.issueCandidateList(
                "FORM",
                "host-form",
                "host-release",
                7,
                "project-requirements",
                "project",
                "project-1",
                "requirement",
                "requirement-list",
                "list-release",
                3,
                Map.of(
                        "projectId", true,
                        "projectId_op", "IS_NULL"),
                false);

        assertEquals("CANDIDATE_LIST",
                service.verifyCandidateList(token).purpose());
        assertEquals("CANDIDATE_LIST",
                service.verifyListContext(token).purpose());
        assertThrows(
                BusinessForbiddenException.class,
                () -> service.verifyTargetList(token));
    }

    @Test
    void tokenCannotBeReplayedByAnotherUser() {
        String token = service.issueSourceRow(
                "FORM",
                "host-form",
                "host-release",
                1,
                "project-detail",
                "requirement",
                "requirement-1");
        UserContext.setCurrentUser("user-2", "other");

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> service.verifySourceRow(token));

        assertTrue(exception.getMessage().contains("当前用户"));
    }

    @Test
    void traversalTokenRejectsExactRuntimeNodeReentry() {
        String first = service.advanceTraversal(
                null,
                "FORM",
                "form-a",
                "release-a",
                1,
                "open-project",
                "record-1",
                "LIST",
                "list-b",
                "release-b",
                2,
                null);
        String second = service.advanceTraversal(
                first,
                "LIST",
                "list-b",
                "release-b",
                2,
                "open-requirement",
                "record-2",
                "FORM",
                "form-a",
                "release-a",
                1,
                "record-1");

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.advanceTraversal(
                        second,
                        "FORM",
                        "form-a",
                        "release-a",
                        1,
                        "open-project",
                        "record-1",
                        "LIST",
                        "list-c",
                        "release-c",
                        1,
                        null));

        assertEquals(
                "VIEW_COMPOSITION_RUNTIME_REENTRY_BLOCKED",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("避免循环"));
    }

    @Test
    void traversalTokenAllowsEightLevelsAndRejectsNinth() {
        String token = null;
        for (int level = 1; level <= 8; level++) {
            token = service.advanceTraversal(
                    token,
                    level % 2 == 0 ? "LIST" : "FORM",
                    "asset-" + level,
                    "release-" + level,
                    level,
                    "composition-" + level,
                    "record-" + level,
                    (level + 1) % 2 == 0 ? "LIST" : "FORM",
                    "asset-" + (level + 1),
                    "release-" + (level + 1),
                    level + 1,
                    null);
        }
        String eightLevelToken = token;

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> service.advanceTraversal(
                        eightLevelToken,
                        "FORM",
                        "asset-9",
                        "release-9",
                        9,
                        "composition-9",
                        "record-9",
                        "LIST",
                        "asset-10",
                        "release-10",
                        10,
                        null));

        assertEquals(
                "VIEW_COMPOSITION_RUNTIME_DEPTH_EXCEEDED",
                exception.getErrorCode());
        assertTrue(exception.getMessage().contains("8 层"));
    }

    @Test
    void traversalTokenCannotEnterAnotherPinnedRecord() {
        String token = service.advanceTraversal(
                null,
                "FORM",
                "form-a",
                "release-a",
                1,
                "open-project",
                "source-1",
                "FORM",
                "form-b",
                "release-b",
                2,
                "target-1");

        BusinessForbiddenException exception = assertThrows(
                BusinessForbiddenException.class,
                () -> service.verifyTraversalEntry(
                        token,
                        "FORM",
                        "form-b",
                        "release-b",
                        2,
                        "child-content",
                        "target-2"));

        assertTrue(exception.getMessage().contains("版本或记录不一致"));
    }
}
