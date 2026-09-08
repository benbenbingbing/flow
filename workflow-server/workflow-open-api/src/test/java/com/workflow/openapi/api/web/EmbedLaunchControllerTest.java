package com.workflow.openapi.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.workflow.contracts.embed.EmbedApplicationActor;
import com.workflow.contracts.embed.EmbedLaunchCommand;
import com.workflow.contracts.embed.launch.port.EmbedLaunchIssuePort;
import com.workflow.contracts.embed.EmbedLaunchIssued;
import com.workflow.contracts.embed.EmbedLaunchView;
import com.workflow.contracts.process.open.OpenApplicationActor;
import com.workflow.openapi.api.request.OpenEmbedLaunchRequest;
import com.workflow.openapi.api.response.OpenEmbedLaunchResponse;
import com.workflow.openapi.security.OpenApplicationActorResolver;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;

class EmbedLaunchControllerTest {

    @Test
    void mapsAuthenticatedApplicationAndReturnsOneTimeLaunch()
            throws Exception {
        EmbedLaunchIssuePort launchIssuePort = mock(EmbedLaunchIssuePort.class);
        OpenApplicationActorResolver actorResolver =
                mock(OpenApplicationActorResolver.class);
        EmbedLaunchController controller = new EmbedLaunchController(
                launchIssuePort,
                actorResolver);
        Authentication authentication = mock(Authentication.class);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "trace-embed-launch");
        request.addHeader("X-Request-Id", "request-embed-launch");
        when(actorResolver.resolve(authentication, "trace-embed-launch"))
                .thenReturn(new OpenApplicationActor(
                        "app-01",
                        "client-01",
                        "trace-embed-launch"));
        when(launchIssuePort.issue(any(), any()))
                .thenReturn(new EmbedLaunchIssued(
                        "lch_01K",
                        "https://embed.flow.example.com/embed/v1/launches/lch_01K",
                        "one-time-code",
                        Instant.parse("2026-08-27T08:31:00Z"),
                        new EmbedLaunchView(
                                "supplier-work-orders",
                                "LIST"),
                        "flow-embed/1"));

        var response = controller.issue(
                new OpenEmbedLaunchRequest(
                        "supplier-work-orders",
                        "https://portal.partner.example",
                        "66f82f09-89ec-4a5a-b81b-f54f02d22262",
                        new OpenEmbedLaunchRequest.Subject(
                                "SIGNED_JWT",
                                "signed-user-assertion",
                                null,
                                null),
                        new OpenEmbedLaunchRequest.Entry("LIST", null),
                        Map.of("supplierId", "S-10086"),
                        new OpenEmbedLaunchRequest.Ui("zh-CN", "light", "dialog")),
                authentication,
                request);

        ArgumentCaptor<EmbedApplicationActor> actorCaptor =
                ArgumentCaptor.forClass(EmbedApplicationActor.class);
        ArgumentCaptor<EmbedLaunchCommand> commandCaptor =
                ArgumentCaptor.forClass(EmbedLaunchCommand.class);
        verify(launchIssuePort).issue(
                actorCaptor.capture(),
                commandCaptor.capture());

        assertThat(actorCaptor.getValue()).isEqualTo(
                new EmbedApplicationActor(
                        "app-01",
                        "client-01",
                        "trace-embed-launch",
                        "request-embed-launch"));
        assertThat(commandCaptor.getValue().viewKey())
                .isEqualTo("supplier-work-orders");
        assertThat(commandCaptor.getValue().subject().assertion())
                .isEqualTo("signed-user-assertion");
        assertThat(commandCaptor.getValue().context())
                .containsEntry("supplierId", "S-10086");
        assertThat(commandCaptor.getValue().ui().formPresentation())
                .isEqualTo("dialog");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getHeaders().getCacheControl())
                .isEqualTo("no-store");
        assertThat(response.getBody().code()).isEqualTo(201);
        assertThat(response.getBody().message()).isEqualTo("created");
        OpenEmbedLaunchResponse data = response.getBody().data();
        assertThat(data.launchId()).isEqualTo("lch_01K");
        assertThat(data.launchCode()).isEqualTo("one-time-code");
        assertThat(data.view().key()).isEqualTo("supplier-work-orders");
        assertThat(data.view().surfaceType()).isEqualTo("LIST");
        assertThat(response.getBody().traceId())
                .isEqualTo("trace-embed-launch");
    }
}
