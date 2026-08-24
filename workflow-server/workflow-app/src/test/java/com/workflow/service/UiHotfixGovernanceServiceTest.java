package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.ui.api.request.UiConfigPublishRequest;
import com.workflow.entity.ui.api.request.UiHotfixApplyRequest;
import com.workflow.entity.ui.api.request.UiHotfixCancelRequest;
import com.workflow.entity.ui.api.request.UiHotfixReviewRequest;
import com.workflow.entity.ui.api.response.UiConfigPublishPreviewDTO;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import com.workflow.entity.ui.application.UiHotfixGovernanceService;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigHotfixRequestMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigHotfixRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UI HOTFIX 双人复核、快照绑定和强制发布授权测试。 */
class UiHotfixGovernanceServiceTest {

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void reviewRiskApplicationWaitsForIndependentReviewer() {
        TestContext context = context();
        UserContext.setCurrentUser("applicant-1", "申请人");
        when(context.mapper().insert(
                any(UiConfigHotfixRequest.class)))
                .thenAnswer(invocation -> {
            UiConfigHotfixRequest record = invocation.getArgument(0);
            record.setId("request-1");
            return 1;
        });

        context.service().apply(preview("REVIEW", "draft-1"),
                applyRequest());

        ArgumentCaptor<UiConfigHotfixRequest> captor =
                ArgumentCaptor.forClass(UiConfigHotfixRequest.class);
        verify(context.mapper()).insert(captor.capture());
        assertEquals("PENDING_REVIEW", captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getReviewRequired());
        assertEquals("applicant-1", captor.getValue().getApplicantId());
    }

    @Test
    void applicantCannotReviewOwnHighRiskApplication() {
        TestContext context = context();
        UserContext.setCurrentUser("applicant-1", "申请人");
        UiConfigHotfixRequest record = approvedRecord("PENDING_REVIEW");
        when(context.mapper().selectById("request-1"))
                .thenReturn(record);
        UiHotfixReviewRequest review = new UiHotfixReviewRequest();
        review.setApproved(true);
        review.setComment("同意发布");

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().review("request-1", review));

        assertEquals("UI_HOTFIX_SELF_REVIEW_FORBIDDEN",
                exception.getErrorCode());
    }

    @Test
    void pendingReviewCannotBeBypassedByPublishRequest() {
        TestContext context = context();
        UserContext.setCurrentUser("publisher-1", "发布人");
        UiConfigHotfixRequest record = approvedRecord("PENDING_REVIEW");
        when(context.mapper().selectById("request-1"))
                .thenReturn(record);
        UiConfigPublishRequest request = publishRequest();

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().beginPublish(
                        request,
                        preview("REVIEW", "draft-1")));

        assertEquals("UI_HOTFIX_APPROVAL_REQUIRED",
                exception.getErrorCode());
    }

    @Test
    void approvedSnapshotDriftInvalidatesAuthorization() {
        TestContext context = context();
        UserContext.setCurrentUser("publisher-1", "发布人");
        UiConfigHotfixRequest record = approvedRecord("APPROVED");
        when(context.mapper().selectById("request-1"))
                .thenReturn(record);

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().beginPublish(
                        publishRequest(),
                        preview("REVIEW", "draft-2")));

        assertEquals("UI_HOTFIX_APPROVAL_STALE",
                exception.getErrorCode());
    }

    @Test
    void rollbackRequiresExplicitReason() {
        TestContext context = context();
        UserContext.setCurrentUser("operator-1", "运维人员");

        assertThrows(
                IllegalArgumentException.class,
                () -> context.service().authorizeRollback(
                        "release-hotfix-1", " "));
        verify(context.accessService()).requireHotfixRollbackAccess();
    }

    @Test
    void onlyApplicantCanCancelOpenApplication() {
        TestContext context = context();
        UserContext.setCurrentUser("other-user", "其他管理员");
        UiConfigHotfixRequest record = approvedRecord("APPROVED");
        when(context.mapper().selectById("request-1"))
                .thenReturn(record);
        UiHotfixCancelRequest request = new UiHotfixCancelRequest();
        request.setReason("草稿已变化");

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().cancel("request-1", request));

        assertEquals("UI_HOTFIX_CANCEL_APPLICANT_REQUIRED",
                exception.getErrorCode());
    }

    private TestContext context() {
        UiConfigHotfixRequestMapper mapper =
                mock(UiConfigHotfixRequestMapper.class);
        UiConfigurationAccessService accessService =
                mock(UiConfigurationAccessService.class);
        return new TestContext(
                new UiHotfixGovernanceService(
                        mapper,
                        mock(JdbcTemplate.class),
                        new ObjectMapper().findAndRegisterModules(),
                        accessService),
                mapper,
                accessService);
    }

    private UiHotfixApplyRequest applyRequest() {
        UiHotfixApplyRequest request = new UiHotfixApplyRequest();
        request.setConfigType("FORM");
        request.setConfigId("form-1");
        request.setExpectedDraftHash("draft-1");
        request.setExpectedActiveReleaseId("release-base-1");
        request.setImpactToken("impact-token-1");
        request.setReason("修复审批表单展示错误");
        request.setTicketRef("INC-1001");
        request.setWindowStart(LocalDateTime.now().minusMinutes(1));
        request.setWindowEnd(LocalDateTime.now().plusMinutes(30));
        return request;
    }

    private UiConfigPublishRequest publishRequest() {
        UiConfigPublishRequest request = new UiConfigPublishRequest();
        request.setReleaseMode("HOTFIX");
        request.setHotfixRequestId("request-1");
        return request;
    }

    private UiConfigPublishPreviewDTO preview(
            String riskLevel,
            String draftHash) {
        return UiConfigPublishPreviewDTO.builder()
                .configType("FORM")
                .configId("form-1")
                .releaseMode("HOTFIX")
                .rolloutScope("ACTIVE_AND_FUTURE")
                .draftHash(draftHash)
                .activeReleaseId("release-base-1")
                .activeVersion(1)
                .targetHash("target-hash-1")
                .impactToken("impact-token-1")
                .riskLevel(riskLevel)
                .changed(true)
                .canPublish(true)
                .changedItems(List.of())
                .riskItems(List.of())
                .targets(List.of())
                .blockers(List.of())
                .build();
    }

    private UiConfigHotfixRequest approvedRecord(String status) {
        UiConfigHotfixRequest record = new UiConfigHotfixRequest();
        record.setId("request-1");
        record.setConfigType("FORM");
        record.setConfigId("form-1");
        record.setDraftHash("draft-1");
        record.setActiveReleaseId("release-base-1");
        record.setTargetHash("target-hash-1");
        record.setImpactTokenHash(sha256("impact-token-1"));
        record.setRiskLevel("REVIEW");
        record.setApplicantId("applicant-1");
        record.setReviewRequired(1);
        record.setReviewerId("reviewer-1");
        record.setStatus(status);
        record.setWindowStart(LocalDateTime.now().minusMinutes(1));
        record.setWindowEnd(LocalDateTime.now().plusMinutes(30));
        record.setImpactDocument("{}");
        return record;
    }

    private String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record TestContext(
            UiHotfixGovernanceService service,
            UiConfigHotfixRequestMapper mapper,
            UiConfigurationAccessService accessService) {
    }
}
