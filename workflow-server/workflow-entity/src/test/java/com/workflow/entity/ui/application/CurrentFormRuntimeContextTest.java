package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.contracts.entity.ui.context.UiRuntimeResolutionContext;
import com.workflow.entity.ui.infrastructure.persistence.mapper.UiConfigReleaseMapper;
import com.workflow.entity.ui.infrastructure.persistence.record.UiConfigRelease;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 全局发布与受理边界：新请求读取新配置，已受理请求和事务终检保持一致。 */
class CurrentFormRuntimeContextTest {
    private final UiConfigReleaseMapper mapper = mock(UiConfigReleaseMapper.class);
    private final ObjectMapper json = new ObjectMapper();
    private final CurrentFormRuntimeContext context = new CurrentFormRuntimeContext(mapper, json);
    @AfterEach void cleanup() { RequestContextHolder.resetRequestAttributes(); }
    private void request() { RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(new MockHttpServletRequest())); }

    @Test void acceptedRequestUsesSameSnapshotWhileNextRequestSeesNewPublication() {
        AtomicReference<UiConfigRelease> current = new AtomicReference<>(row("root", "before", "{}"));
        when(mapper.findActive("FORM", "root")).thenAnswer(call -> current.get());
        request();
        UiConfigRelease accepted = context.current("root");
        current.set(row("root", "after", "{}"));
        assertSame(accepted, context.accepted("root", "before"));
        request();
        assertEquals("after", context.current("root").getId());
        assertEquals("FORM_CONFIGURATION_CHANGED", assertThrows(BusinessConflictException.class,
                () -> context.accepted("root", "before")).getErrorCode());
    }

    @Test void childOnlyPublicationInvalidatesParentAuthorizationBeforeWrite() {
        when(mapper.findActive("FORM", "root")).thenReturn(row("root", "root-hash",
                "{\"nodes\":[{\"propsDocument\":\"{\\\"childFormId\\\":\\\"child\\\"}\"}]}"));
        AtomicReference<UiConfigRelease> child = new AtomicReference<>(row("child", "child-before", "{}"));
        when(mapper.findActive("FORM", "child")).thenAnswer(call -> child.get());
        UiReleaseResolutionTokenService tokens = new UiReleaseResolutionTokenService(json);
        ReflectionTestUtils.setField(tokens, "secret", "test-secret");
        ReflectionTestUtils.setField(tokens, "currentForms", context);
        request();
        String token = tokens.issue(UiRuntimeResolutionContext.standalone(), "root", "root-hash", 1, 0);
        assertNotNull(token);
        assertDoesNotThrow(() -> tokens.verify(token));
        child.set(row("child", "child-after", "{}"));
        request();
        assertEquals("FORM_CONFIGURATION_CHANGED", assertThrows(BusinessConflictException.class,
                () -> tokens.verify(token)).getErrorCode());
        String fresh = tokens.issue(UiRuntimeResolutionContext.standalone(), "root", "root-hash", 1, 0);
        assertDoesNotThrow(() -> tokens.verify(fresh));
    }

    @Test void unpublishedFormNeverFallsBackToHistoricalRecordOrDraft() {
        assertEquals("FORM_NOT_PUBLISHED", assertThrows(BusinessConflictException.class,
                () -> context.current("draft-only")).getErrorCode());
        verify(mapper, never()).selectById(anyString());
    }

    @Test void dependencyCycleFailsClosed() {
        when(mapper.findActive("FORM", "a")).thenReturn(row("a", "a-hash", "{\"childFormId\":\"b\"}"));
        when(mapper.findActive("FORM", "b")).thenReturn(row("b", "b-hash", "{\"childFormId\":\"a\"}"));
        assertThrows(IllegalStateException.class, () -> context.graphHash("a"));
    }
    @Test void inactiveLegacyReferencesDoNotEnterAuthorizationGraph() {
        when(mapper.findActive("FORM", "root")).thenReturn(row("root","current-hash",
                "{\"nodes\":[{\"legacyPropsDocument\":\"{\\\"childFormId\\\":\\\"unpublished-old-form\\\"}\"}]}"));
        assertNotNull(context.graphHash("root"));
        verify(mapper,never()).findActive("FORM","unpublished-old-form");
    }
    private UiConfigRelease row(String id, String hash, String document) {
        UiConfigRelease row = new UiConfigRelease(); row.setConfigId(id); row.setConfigType("FORM");
        row.setId(hash); row.setContentHash(hash); row.setVersion(1); row.setSnapshotDocument(document); return row;
    }
}
