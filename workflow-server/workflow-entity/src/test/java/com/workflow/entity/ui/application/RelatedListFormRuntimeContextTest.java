package com.workflow.entity.ui.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.core.error.BusinessForbiddenException;
import com.workflow.entity.form.application.model.ResolvedEntityFormRelease;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.api.response.EntityListConfigDTO;
import com.workflow.entity.ui.api.request.UiViewCompositionResolveRequest;
import com.workflow.entity.ui.api.response.UiViewCompositionResolveResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 关联列表查看详情既要允许列表到表单导航，也必须保留发布入口和行范围约束。 */
class RelatedListFormRuntimeContextTest {
    private final UiConfigReleaseService service = mock(UiConfigReleaseService.class, CALLS_REAL_METHODS);
    private final UiViewCompositionRuntimeService compositions = mock(UiViewCompositionRuntimeService.class);
    private final EntityForm form = new EntityForm();
    private final EntityListConfigDTO list = new EntityListConfigDTO();
    private final UiViewCompositionResolveResponse target = UiViewCompositionResolveResponse.builder()
            .targetContentType("LIST").targetContentId("child-list").targetContentKey("items")
            .targetEntityId("child-entity").targetReleaseId("pinned-list").targetReleaseVersion(2).build();
    private final Map<String, Object> source = Map.of("ownerType", "FORM", "ownerId", "parent-form",
            "compositionKey", "children", "recordId", "parent-record");

    RelatedListFormRuntimeContextTest() {
        ObjectProvider<UiViewCompositionRuntimeService> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(compositions);
        ReflectionTestUtils.setField(service, "compositionRuntime", provider);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
        when(compositions.resolve(any())).thenReturn(target);
        form.setId("child-form");
        form.setEntityId("child-entity");
        doReturn(new ResolvedEntityFormRelease(form, "form-hash", 1))
                .when(service).resolveRuntimeFormRelease("child-form");
        doReturn(form).when(service).currentDefaultForm("child-entity");
        doReturn(new UiConfigReleaseService.ResolvedEntityListRelease(list, "pinned-list", 2, true, Map.of()))
                .when(service).resolveServerPinnedRuntimeListRelease("child-list", "pinned-list", 2);
    }

    private Map<String, Object> context() {
        return new LinkedHashMap<>(Map.of("compositionContext", source, "recordId", "child-record", "listKey", "items"));
    }

    @Test void defaultFormLoadsAndRechecksChildRecordMembership() {
        assertDoesNotThrow(() -> service.authorizeRuntimeContext("child-form", context()));
        verify(compositions).requireTargetMembership(target, "child-record");
        verify(compositions).resolve(argThat(request -> "parent-record".equals(request.getRecordId())));
    }

    @Test void explicitRowFormUsesPinnedButtonBinding() {
        list.setRowActionConfig(List.of(Map.of("key", "view-detail", "targetFormId", "child-form")));
        Map<String, Object> context = context();
        context.put("buttonId", "view-detail");
        assertDoesNotThrow(() -> service.authorizeRuntimeContext("child-form", context));
        verify(service).resolveServerPinnedRuntimeListRelease("child-list", "pinned-list", 2);
        verify(service, never()).currentDefaultForm(any());
        verify(compositions).requireTargetMembership(target, "child-record");
    }

    @Test void toolbarFormCanLoadBeforeANewRecordExists() {
        list.setToolbarConfig(List.of(Map.of("id", "create-detail", "targetFormId", "child-form")));
        Map<String, Object> context = context();
        context.put("buttonId", "create-detail");
        context.remove("recordId");
        assertDoesNotThrow(() -> service.authorizeRuntimeContext("child-form", context));
        verify(compositions, never()).requireTargetMembership(any(), any());
    }

    @Test void undeclaredButtonCannotSelectAnArbitraryForm() {
        list.setRowActionConfig(List.of(Map.of("key", "view-detail", "targetFormId", "other-form")));
        Map<String, Object> context = context();
        context.put("buttonId", "view-detail");
        assertThrows(BusinessForbiddenException.class, () -> service.authorizeRuntimeContext("child-form", context));
        verify(compositions, never()).requireTargetMembership(any(), any());
    }

    @Test void nonDefaultFormRequiresAnExplicitPublishedButton() {
        EntityForm other = new EntityForm(); other.setId("other-form");
        doReturn(other).when(service).currentDefaultForm("child-entity");
        assertThrows(BusinessForbiddenException.class, () -> service.authorizeRuntimeContext("child-form", context()));
    }

    @Test void wrongEntityOrListCannotReuseCompositionSource() {
        form.setEntityId("other-entity");
        assertThrows(BusinessForbiddenException.class, () -> service.authorizeRuntimeContext("child-form", context()));
        form.setEntityId("child-entity");
        Map<String, Object> context = context(); context.put("listKey", "other-items");
        assertThrows(BusinessForbiddenException.class, () -> service.authorizeRuntimeContext("child-form", context));
    }

    @Test void outOfScopeChildRecordRemainsForbidden() {
        doThrow(new BusinessForbiddenException("VIEW_COMPOSITION_ROW_OUT_OF_SCOPE", "来源记录不属于关联范围"))
                .when(compositions).requireTargetMembership(target, "child-record");
        assertEquals("VIEW_COMPOSITION_ROW_OUT_OF_SCOPE", assertThrows(BusinessForbiddenException.class,
                () -> service.authorizeRuntimeContext("child-form", context())).getErrorCode());
    }

    @Test void directFormCompositionStillRequiresTheExactFormAndRecord() {
        when(compositions.resolve(any(UiViewCompositionResolveRequest.class))).thenReturn(target.toBuilder()
                .targetContentType("FORM").targetContentId("child-form").targetRecordId("child-record").build());
        assertDoesNotThrow(() -> service.authorizeRuntimeContext("child-form", context()));
        Map<String, Object> context = context(); context.put("recordId", "other-record");
        assertThrows(BusinessForbiddenException.class, () -> service.authorizeRuntimeContext("child-form", context));
        assertThrows(BusinessForbiddenException.class, () -> service.authorizeRuntimeContext("other-form", context()));
    }
}
