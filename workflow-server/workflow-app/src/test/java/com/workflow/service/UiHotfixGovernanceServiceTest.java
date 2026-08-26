package com.workflow.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.security.context.UserContext;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.ui.api.request.UiConfigPublishRequest;
import com.workflow.entity.ui.api.response.UiConfigPublishPreviewDTO;
import com.workflow.entity.ui.application.UiConfigurationAccessService;
import com.workflow.entity.ui.application.UiHotfixGovernanceService;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigHotfixRequestMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigHotfixRequest;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** UI HOTFIX 直接发布、观察窗口和回滚治理测试。 */
class UiHotfixGovernanceServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""),
                UiConfigHotfixRequest.class);
    }

    @AfterEach
    void clearUser() {
        UserContext.clear();
    }

    @Test
    void directPublishCreatesPublishingAuditRecordForReviewRisk() {
        TestContext context = context();
        UserContext.setCurrentUser("publisher-1", "发布人");
        assignInsertedId(context, "request-direct-1");

        String requestId = context.service().beginDirectPublish(
                publishRequest("修复审批表单展示错误"),
                preview("REVIEW", true));

        ArgumentCaptor<UiConfigHotfixRequest> captor =
                ArgumentCaptor.forClass(UiConfigHotfixRequest.class);
        verify(context.mapper()).insert(captor.capture());
        UiConfigHotfixRequest record = captor.getValue();
        assertEquals("request-direct-1", requestId);
        assertEquals("PUBLISHING", record.getStatus());
        assertEquals(0, record.getReviewRequired());
        assertEquals("REVIEW", record.getRiskLevel());
        assertEquals("修复审批表单展示错误", record.getReason());
        assertEquals("", record.getTicketRef());
        assertEquals("publisher-1", record.getApplicantId());
        assertEquals("发布人", record.getApplicantName());
        assertNull(record.getReviewerId());
        assertNull(record.getReviewerName());
        assertNull(record.getReviewComment());
        assertNull(record.getReviewedAt());
        assertNotNull(record.getImpactTokenHash());
        assertEquals(record.getWindowStart().plusMinutes(1),
                record.getWindowEnd());
        verify(context.accessService()).requireHotfixAccess(false);
        verify(context.accessService()).requireFormAccess("form-1");
    }

    @Test
    void directPublishUsesDefaultAndColumnSafeReason() {
        TestContext defaultContext = context();
        UserContext.setCurrentUser("publisher-1", "发布人");
        assignInsertedId(defaultContext, "request-default");

        defaultContext.service().beginDirectPublish(
                publishRequest("  "), preview("SAFE", true));

        ArgumentCaptor<UiConfigHotfixRequest> defaultCaptor =
                ArgumentCaptor.forClass(UiConfigHotfixRequest.class);
        verify(defaultContext.mapper()).insert(defaultCaptor.capture());
        assertEquals("直接发布热修复", defaultCaptor.getValue().getReason());

        TestContext longReasonContext = context();
        assignInsertedId(longReasonContext, "request-long-reason");
        longReasonContext.service().beginDirectPublish(
                publishRequest("变".repeat(1001)),
                preview("SAFE", true));

        ArgumentCaptor<UiConfigHotfixRequest> longReasonCaptor =
                ArgumentCaptor.forClass(UiConfigHotfixRequest.class);
        verify(longReasonContext.mapper()).insert(longReasonCaptor.capture());
        assertEquals(1000, longReasonCaptor.getValue().getReason().length());
    }

    @Test
    void directPublishRetiresLegacyOpenSlotsBeforeInsert() {
        TestContext context = context();
        UserContext.setCurrentUser("publisher-1", "发布人");
        assignInsertedId(context, "request-direct-1");

        context.service().beginDirectPublish(
                publishRequest("直接发布"), preview("SAFE", true));

        InOrder order = inOrder(context.mapper());
        ArgumentCaptor<LambdaUpdateWrapper<UiConfigHotfixRequest>> captor =
                updateWrapperCaptor();
        order.verify(context.mapper()).update(isNull(), captor.capture());
        order.verify(context.mapper()).insert(
                any(UiConfigHotfixRequest.class));
        Map<String, Object> parameters = renderParameters(captor.getValue());
        assertTrue(parameters.containsValue("PENDING_REVIEW"));
        assertTrue(parameters.containsValue("APPROVED"));
        assertTrue(parameters.containsValue("CANCELLED"));
        assertFalse(parameters.containsValue("PUBLISHING"));
        assertTrue(captor.getValue().getSqlSegment()
                .contains("release_id IS NULL"));
    }

    @Test
    void directPublishDoesNotStealExistingPublishingSlot() {
        TestContext context = context();
        UserContext.setCurrentUser("publisher-1", "发布人");
        when(context.mapper().insert(any(UiConfigHotfixRequest.class)))
                .thenThrow(new DuplicateKeyException("open slot"));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().beginDirectPublish(
                        publishRequest("直接发布"),
                        preview("REVIEW", true)));

        assertEquals("UI_HOTFIX_PUBLISH_STATE_CONFLICT",
                exception.getErrorCode());
    }

    @Test
    void blockedPreviewCannotCreatePublishingRecord() {
        TestContext context = context();
        UserContext.setCurrentUser("publisher-1", "发布人");

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> context.service().beginDirectPublish(
                        publishRequest("直接发布"),
                        preview("BLOCKED", false)));

        assertEquals("UI_HOTFIX_PREVIEW_BLOCKED",
                exception.getErrorCode());
        verify(context.mapper(), never()).insert(
                any(UiConfigHotfixRequest.class));
    }

    @Test
    void markPublishedMovesPublishingRecordIntoObservation() {
        TestContext context = context();
        when(context.mapper().update(isNull(), any()))
                .thenReturn(1);

        context.service().markPublished("request-direct-1", "release-2");

        ArgumentCaptor<LambdaUpdateWrapper<UiConfigHotfixRequest>> captor =
                updateWrapperCaptor();
        verify(context.mapper()).update(isNull(), captor.capture());
        Map<String, Object> parameters = renderParameters(captor.getValue());
        assertTrue(parameters.containsValue("request-direct-1"));
        assertTrue(parameters.containsValue("PUBLISHING"));
        assertTrue(parameters.containsValue("OBSERVING"));
        assertTrue(parameters.containsValue("release-2"));
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

    private void assignInsertedId(TestContext context, String id) {
        when(context.mapper().insert(any(UiConfigHotfixRequest.class)))
                .thenAnswer(invocation -> {
                    UiConfigHotfixRequest record = invocation.getArgument(0);
                    record.setId(id);
                    return 1;
                });
    }

    private UiConfigPublishRequest publishRequest(String description) {
        UiConfigPublishRequest request = new UiConfigPublishRequest();
        request.setReleaseMode("HOTFIX");
        request.setDescription(description);
        return request;
    }

    private UiConfigPublishPreviewDTO preview(
            String riskLevel,
            boolean canPublish) {
        return UiConfigPublishPreviewDTO.builder()
                .configType("FORM")
                .configId("form-1")
                .releaseMode("HOTFIX")
                .rolloutScope("ACTIVE_AND_FUTURE")
                .draftHash("draft-1")
                .activeReleaseId("release-base-1")
                .activeVersion(1)
                .targetHash("target-hash-1")
                .impactToken("impact-token-1")
                .riskLevel(riskLevel)
                .changed(true)
                .canPublish(canPublish)
                .changedItems(List.of())
                .riskItems(List.of())
                .targets(List.of())
                .blockers(canPublish ? List.of() : List.of("预检未通过"))
                .build();
    }

    private Map<String, Object> renderParameters(
            LambdaUpdateWrapper<UiConfigHotfixRequest> wrapper) {
        wrapper.getSqlSet();
        wrapper.getSqlSegment();
        return wrapper.getParamNameValuePairs();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<LambdaUpdateWrapper<UiConfigHotfixRequest>>
            updateWrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(
                LambdaUpdateWrapper.class);
    }

    private record TestContext(
            UiHotfixGovernanceService service,
            UiConfigHotfixRequestMapper mapper,
            UiConfigurationAccessService accessService) {
    }
}
