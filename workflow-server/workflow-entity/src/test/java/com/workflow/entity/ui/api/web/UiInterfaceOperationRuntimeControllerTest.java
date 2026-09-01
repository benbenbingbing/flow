package com.workflow.entity.ui.api.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.workflow.contracts.embed.EmbedDelegatedRequestContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ui.api.request.UiInterfaceOperationExecuteRequest;
import com.workflow.entity.ui.application.UiDataSourceService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class UiInterfaceOperationRuntimeControllerTest {

    @Test
    void requestAcceptsPinnedEmbedOwnerCoordinates() throws Exception {
        UiInterfaceOperationExecuteRequest body = new ObjectMapper()
                .readValue("""
                        {
                          "ownerType":"FORM",
                          "ownerId":"child-form",
                          "releaseId":"child-release",
                          "releaseVersion":7,
                          "releaseResolutionToken":"signed-child",
                          "entityCode":"customer",
                          "listKey":"customers",
                          "recordId":"record-7",
                          "viewCompositionTraversalToken":"traversal-1"
                        }
                        """, UiInterfaceOperationExecuteRequest.class);

        assertEquals("child-release", body.getReleaseId());
        assertEquals(7, body.getReleaseVersion());
        assertEquals("signed-child", body.getReleaseResolutionToken());
        assertEquals("record-7", body.getRecordId());
    }

    @Test
    void delegatedNativeExecutionUsesServerPinnedFormRelease() {
        UiDataSourceService service = mock(UiDataSourceService.class);
        UiInterfaceOperationExecuteRequest body =
                new UiInterfaceOperationExecuteRequest();
        when(service.executeBoundOperationAtRelease(
                body, "form-release-4", 4)).thenReturn("ok");
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/ui-runtime/interface-operations/execute");
        request.setAttribute(
                EmbedDelegatedRequestContext.TARGET_ATTRIBUTE,
                new EmbedDelegatedRequestContext.AuthorizedTarget(
                        "order", "form-1", "form-release-4", 4,
                        "VIEW", "record-1"));
        UiInterfaceOperationRuntimeController controller =
                new UiInterfaceOperationRuntimeController(service);

        controller.execute(body, request);

        verify(service).executeBoundOperationAtRelease(
                body, "form-release-4", 4);
        verifyNoMoreInteractions(service);
    }
}
