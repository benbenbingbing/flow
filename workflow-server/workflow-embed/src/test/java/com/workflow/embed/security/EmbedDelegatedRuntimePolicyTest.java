package com.workflow.embed.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.embed.EmbedDelegatedRuntimeApi;
import com.workflow.contracts.embed.EmbedNativeFormRuntimePort;
import com.workflow.contracts.embed.EmbedNativeListRuntimePort;
import com.workflow.contracts.embed.EmbedNativeProcessRuntimePort;
import com.workflow.contracts.embed.EmbedNativeTraversalRuntimePort;
import com.workflow.embed.application.runtime.EmbedNativeFormTargetResolver;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedNativeFormTarget;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.HandlerMapping;

class EmbedDelegatedRuntimePolicyTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EmbedNativeFormTargetResolver targetResolver = mock(
            EmbedNativeFormTargetResolver.class);
    private final EmbedNativeFormRuntimePort formRuntimePort = mock(
            EmbedNativeFormRuntimePort.class);
    private final EmbedNativeListRuntimePort listRuntimePort = mock(
            EmbedNativeListRuntimePort.class);
    private final EmbedNativeFormTarget root = new EmbedNativeFormTarget(
            "order", "form-1", "form-release-1", 3,
            "default", "list-release-1", 2,
            "VIEW", "record-1", "process-1",
            Map.of(), Map.of(), Map.of());

    @Test
    void rootMetadataUsesUriTemplateBindingInsteadOfEndpointPathList() {
        AuthenticatedEmbedSession session = session(Set.of("RECORD_VIEW"));
        when(targetResolver.resolveRoot(session)).thenReturn(root);
        EmbedDelegatedRuntimePolicy policy = policy(null, null);

        MockHttpServletRequest metadata = get("/api/future-component/meta/order");
        pathVariables(metadata, Map.of("entityCode", "order"));
        assertEquals(root, policy.authorize(
                metadata, session, null, declaration("rootEntity")));

        MockHttpServletRequest escaped = get("/another/path/customer");
        pathVariables(escaped, Map.of("entityCode", "customer"));
        assertThrows(EmbedException.class, () -> policy.authorize(
                escaped, session, null, declaration("rootEntity")));
    }

    @Test
    void newAnnotatedReferenceEndpointNeedsNoPolicyPathChange() {
        AuthenticatedEmbedSession session = session(Set.of("RECORD_VIEW"));
        when(targetResolver.resolveRoot(session)).thenReturn(root);
        EmbedDelegatedRuntimePolicy policy = policy(null, null);

        assertEquals(root, policy.authorize(
                get("/api/components-added-next-year/options"),
                session, null, declaration("referenceRead")));
    }

    @Test
    void scopeBindingAndHttpMethodMustFormAValidDeclarativeContract() {
        AuthenticatedEmbedSession session = session(Set.of("RECORD_VIEW"));
        EmbedDelegatedRuntimePolicy policy = policy(null, null);

        assertThrows(EmbedException.class, () -> policy.authorize(
                new MockHttpServletRequest(
                        "POST", "/api/future/reference-mutation"),
                session, null, declaration("referenceRead")));
        assertThrows(EmbedException.class, () -> policy.authorize(
                get("/api/future/misdeclared-detail"),
                session, null, declaration("referenceWithDetailBinding")));
        verifyNoInteractions(targetResolver);
    }

    @Test
    void missingDeclarationIsAlwaysDeniedBeforeTargetResolution() {
        AuthenticatedEmbedSession session = session(Set.of("RECORD_VIEW"));
        EmbedDelegatedRuntimePolicy policy = policy(null, null);

        assertThrows(EmbedException.class, () -> policy.authorize(
                get("/api/private/unannotated"), session, null, null));
        verifyNoInteractions(targetResolver);
    }

    @Test
    void detailRequiresEveryPinnedCoordinateButNotEmbedCapabilityCeiling() {
        AuthenticatedEmbedSession withoutView = session(Set.of("ACTION_EXECUTE"));
        EmbedDelegatedRuntimePolicy policy = policy(null, null);
        when(targetResolver.resolveRoot(withoutView)).thenReturn(root);
        when(targetResolver.authorizePinnedForm(
                eq(withoutView), any(EmbedNativeFormTarget.class),
                eq("VIEW"), eq("record-1")))
                .thenReturn(root);
        MockHttpServletRequest exact = detail("record-1");
        assertEquals(root, policy.authorize(
                exact, withoutView, null, declaration("recordDetail")));

        MockHttpServletRequest tampered = detail("record-1");
        tampered.setParameter("formReleaseId", "new-active-release");
        assertThrows(EmbedException.class, () -> policy.authorize(
                tampered, withoutView, null, declaration("recordDetail")));
    }

    @Test
    void fileReadAndWriteUseDeclarativeBindingsWithoutEmbedCapabilityCeiling() {
        AuthenticatedEmbedSession viewSession = session(Set.of("RECORD_VIEW"));
        when(targetResolver.resolveRoot(viewSession)).thenReturn(root);
        EmbedDelegatedRuntimePolicy policy = policy(null, null);
        MockHttpServletRequest preview = get("/api/file/preview");
        preview.setParameter("url", "embed/session-1/sample.txt");
        assertEquals(root, policy.authorize(
                preview, viewSession, null, declaration("fileRead")));

        MockHttpServletRequest upload = new MockHttpServletRequest(
                "POST", "/api/file/new-uploader");
        upload.setContentType("multipart/form-data; boundary=native");
        upload.addHeader("Idempotency-Key", "upload-1");
        assertEquals(root, policy.authorize(
                upload, viewSession, null, declaration("fileWrite")));
    }

    @Test
    void processReadMustMapBackToAuthorizedFixedRecord() {
        AuthenticatedEmbedSession session = session(Set.of("RECORD_VIEW"));
        when(targetResolver.resolveRoot(session)).thenReturn(root);
        when(targetResolver.authorize(session, "VIEW", "record-1"))
                .thenReturn(root);
        EmbedNativeProcessRuntimePort process = mock(
                EmbedNativeProcessRuntimePort.class);
        when(process.findRecordTarget("process-1")).thenReturn(Optional.of(
                new EmbedNativeProcessRuntimePort.RecordTarget(
                        "order", "record-1")));
        EmbedDelegatedRuntimePolicy policy = policy(process, null);
        MockHttpServletRequest request = get(
                "/api/process-instance/process-1/new-read-model");
        pathVariables(request, Map.of("processInstanceId", "process-1"));

        assertEquals(root, policy.authorize(
                request, session, null, declaration("processRead")));
        verify(targetResolver).authorize(session, "VIEW", "record-1");

        when(process.findRecordTarget("process-2")).thenReturn(Optional.of(
                new EmbedNativeProcessRuntimePort.RecordTarget(
                        "customer", "record-9")));
        MockHttpServletRequest escaped = get("/api/future/process/process-2");
        pathVariables(escaped, Map.of("processInstanceId", "process-2"));
        assertThrows(EmbedException.class, () -> policy.authorize(
                escaped, session, null, declaration("processRead")));
    }

    @Test
    void signedTraversalAllowsOnlyServerResolvedChildEntityAndRecord()
            throws Exception {
        AuthenticatedEmbedSession session = session(Set.of("RECORD_VIEW"));
        when(targetResolver.resolveRoot(session)).thenReturn(root);
        when(targetResolver.authorize(session, "VIEW", "record-1"))
                .thenReturn(root);
        EmbedNativeTraversalRuntimePort traversal = mock(
                EmbedNativeTraversalRuntimePort.class);
        when(traversal.resolve("signed-child")).thenReturn(
                new EmbedNativeTraversalRuntimePort.TraversalTarget(
                        "FORM", "form-1", "form-release-1", 3,
                        "record-1", "FORM", "child-form",
                        "child-release", 7, "child-record",
                        "customer"));
        EmbedDelegatedRuntimePolicy policy = policy(null, traversal);
        MockHttpServletRequest metadata = get("/any/new/metadata/route");
        pathVariables(metadata, Map.of("entityCode", "customer"));
        metadata.setParameter(
                "viewCompositionTraversalToken", "signed-child");

        EmbedNativeFormTarget child = policy.authorize(
                metadata, session, null, declaration("rootEntity"));
        assertEquals("customer", child.entityCode());
        assertEquals("child-form", child.formId());
        assertEquals("child-record", child.recordId());

        MockHttpServletRequest detail = new MockHttpServletRequest(
                "POST", "/future/detail/child-record");
        pathVariables(detail, Map.of(
                "entityCode", "customer", "recordId", "child-record"));
        detail.setParameter("formId", "child-form");
        detail.setParameter("formReleaseId", "child-release");
        detail.setParameter("formReleaseVersion", "7");
        detail.setParameter(
                "formReleaseResolutionToken", "signed-release");
        detail.setParameter(
                "viewCompositionTraversalToken", "signed-child");
        assertEquals("child-record", policy.authorize(
                detail, session, objectMapper.readTree("{}"),
                declaration("recordDetail")).recordId());
    }

    @Test
    void everyUiEventUsesPinnedCoordinatesWithoutEmbedCapabilityCeiling()
            throws Exception {
        JsonNode body = objectMapper.readTree("""
                {
                  "configType":"FORM",
                  "configId":"form-1",
                  "releaseId":"form-release-1",
                  "releaseVersion":3,
                  "releaseResolutionToken":"signed-release",
                  "entityCode":"order",
                  "recordId":"record-1"
                }
                """);
        MockHttpServletRequest event = new MockHttpServletRequest(
                "POST", "/api/ui-runtime/events/FUTURE_WIDGET_EVENT/execute");
        pathVariables(event, Map.of("eventCode", "FUTURE_WIDGET_EVENT"));
        EmbedDelegatedRuntimePolicy policy = policy(null, null);

        AuthenticatedEmbedSession withoutAction = session(
                Set.of("RECORD_VIEW"));
        when(targetResolver.resolveRoot(withoutAction)).thenReturn(root);
        when(targetResolver.authorizePinnedForm(
                eq(withoutAction), any(EmbedNativeFormTarget.class),
                eq("VIEW"), eq("record-1")))
                .thenReturn(root);
        assertEquals(root, policy.authorize(
                event, withoutAction, body, declaration("formEvent")));
    }

    @Test
    void nonRootRuntimeReleaseRequiresSessionBoundExactFormToken() {
        AuthenticatedEmbedSession session = listSession();
        EmbedNativeFormTarget listRoot = listRoot();
        when(targetResolver.resolveRoot(session)).thenReturn(listRoot);
        EmbedDelegatedRuntimePolicy policy = policy(null, null);
        MockHttpServletRequest request = get(
                "/api/entity-forms/child-form/runtime-release");
        pathVariables(request, Map.of("formId", "child-form"));
        request.setParameter("releaseId", "child-release");
        request.setParameter("version", "7");
        request.setParameter(
                "releaseResolutionToken", "signed-child-form");

        EmbedNativeFormTarget target = policy.authorize(
                request, session, null, declaration("formRelease"));

        assertEquals("customer", target.entityCode());
        assertEquals("child-form", target.formId());
        assertEquals("child-release", target.formReleaseId());
        verify(formRuntimePort).verifyRuntimeReleaseRequest(
                eq("signed-child-form"),
                any(EmbedNativeFormRuntimePort.VerificationTarget.class));
    }

    @Test
    void nonRootActionNormalizesEditToViewWithoutEmbedCapabilityCeiling()
            throws Exception {
        AuthenticatedEmbedSession session = listSession();
        EmbedNativeFormTarget listRoot = listRoot();
        EmbedNativeFormTarget child = childTarget();
        when(targetResolver.resolveRoot(session)).thenReturn(listRoot);
        when(targetResolver.authorizePinnedForm(
                eq(session), any(EmbedNativeFormTarget.class),
                eq("edit"), eq("child-record")))
                .thenReturn(child);
        EmbedDelegatedRuntimePolicy policy = policy(null, null);
        JsonNode body = objectMapper.readTree("""
                {
                  "formId":"child-form",
                  "releaseId":"child-release",
                  "releaseVersion":7,
                  "releaseResolutionToken":"signed-child-form",
                  "entityCode":"customer",
                  "mode":"edit",
                  "recordId":"child-record"
                }
                """);

        assertEquals(child, policy.authorize(
                post("/api/ui-runtime/form-actions/resolve"),
                session, body, declaration("formAction")));
    }

    @Test
    void nonRootApprovalActionKeepsApproveModeAndUsesSignedPinnedTarget()
            throws Exception {
        AuthenticatedEmbedSession session = listSession();
        EmbedNativeFormTarget listRoot = listRoot();
        EmbedNativeFormTarget approvalTarget = new EmbedNativeFormTarget(
                "customer", "child-form", "child-release", 7,
                null, null, null,
                "APPROVE", "child-record", "process-1",
                Map.of(), Map.of(), Map.of());
        when(targetResolver.resolveRoot(session)).thenReturn(listRoot);
        when(targetResolver.authorizePinnedForm(
                eq(session), any(EmbedNativeFormTarget.class),
                eq("approve"), eq("child-record")))
                .thenReturn(approvalTarget);
        EmbedDelegatedRuntimePolicy policy = policy(null, null);
        JsonNode body = objectMapper.readTree("""
                {
                  "formId":"child-form",
                  "releaseId":"child-release",
                  "releaseVersion":7,
                  "releaseResolutionToken":"signed-child-form",
                  "entityCode":"customer",
                  "mode":"approve",
                  "recordId":"child-record",
                  "taskId":"task-1"
                }
                """);

        assertEquals(approvalTarget, policy.authorize(
                post("/api/ui-runtime/form-actions/resolve"),
                session, body, declaration("formAction")));
    }

    @Test
    void nonRootEventUsesSameSignedPinnedTargetChain()
            throws Exception {
        AuthenticatedEmbedSession session = listSession();
        EmbedNativeFormTarget child = childTarget();
        when(targetResolver.resolveRoot(session)).thenReturn(listRoot());
        when(targetResolver.authorizePinnedForm(
                eq(session), any(EmbedNativeFormTarget.class),
                eq("VIEW"), eq("child-record")))
                .thenReturn(child);
        EmbedDelegatedRuntimePolicy policy = policy(null, null);
        MockHttpServletRequest request = post(
                "/api/ui-runtime/events/FUTURE_EVENT/execute");
        pathVariables(request, Map.of("eventCode", "FUTURE_EVENT"));
        JsonNode body = objectMapper.readTree("""
                {
                  "configType":"FORM",
                  "configId":"child-form",
                  "releaseId":"child-release",
                  "releaseVersion":7,
                  "releaseResolutionToken":"signed-child-form",
                  "entityCode":"customer",
                  "recordId":"child-record"
                }
                """);

        assertEquals(child, policy.authorize(
                request, session, body, declaration("formEvent")));
    }

    @Test
    void nonRootDetailRunsPinnedViewAuthorizationAndDataScope()
            throws Exception {
        AuthenticatedEmbedSession session = listSession();
        EmbedNativeFormTarget child = childTarget();
        when(targetResolver.resolveRoot(session)).thenReturn(listRoot());
        when(targetResolver.authorizePinnedForm(
                eq(session), any(EmbedNativeFormTarget.class),
                eq("VIEW"), eq("child-record")))
                .thenReturn(child);
        EmbedDelegatedRuntimePolicy policy = policy(null, null);
        MockHttpServletRequest request = post(
                "/api/entity-data/entity/customer/detail/child-record/load");
        pathVariables(request, Map.of(
                "entityCode", "customer", "recordId", "child-record"));
        request.setParameter("formId", "child-form");
        request.setParameter("formReleaseId", "child-release");
        request.setParameter("formReleaseVersion", "7");
        request.setParameter(
                "formReleaseResolutionToken", "signed-child-form");

        assertEquals(child, policy.authorize(
                request, session, objectMapper.readTree("{}"),
                declaration("recordDetail")));
        verify(targetResolver).authorizePinnedForm(
                eq(session), any(EmbedNativeFormTarget.class),
                eq("VIEW"), eq("child-record"));
    }

    @Test
    void nonRootFormOwnerUsesSignedTargetInsteadOfRootFallback()
            throws Exception {
        AuthenticatedEmbedSession session = listSession();
        EmbedNativeFormTarget child = childTarget();
        when(targetResolver.resolveRoot(session)).thenReturn(listRoot());
        when(targetResolver.authorizePinnedForm(
                eq(session), any(EmbedNativeFormTarget.class),
                eq("VIEW"), eq("child-record")))
                .thenReturn(child);
        EmbedDelegatedRuntimePolicy policy = policy(null, null);
        JsonNode body = objectMapper.readTree("""
                {
                  "ownerType":"FORM",
                  "ownerId":"child-form",
                  "releaseId":"child-release",
                  "releaseVersion":7,
                  "releaseResolutionToken":"signed-child-form",
                  "entityCode":"customer",
                  "recordId":"child-record"
                }
                """);

        assertEquals(child, policy.authorize(
                post("/api/ui-runtime/view-compositions/resolve"),
                session, body, declaration("formOwner")));
    }

    @Test
    void nonRootUniquePrecheckUsesSignedTarget()
            throws Exception {
        AuthenticatedEmbedSession session = listSession();
        EmbedNativeFormTarget child = childTarget();
        when(targetResolver.resolveRoot(session)).thenReturn(listRoot());
        when(targetResolver.authorizePinnedForm(
                eq(session), any(EmbedNativeFormTarget.class),
                eq("VIEW"), eq("child-record")))
                .thenReturn(child);
        EmbedDelegatedRuntimePolicy policy = policy(null, null);
        MockHttpServletRequest request = post(
                "/api/entity-forms/child-form/unique-precheck");
        pathVariables(request, Map.of("formId", "child-form"));
        JsonNode body = objectMapper.readTree("""
                {
                  "releaseId":"child-release",
                  "releaseVersion":7,
                  "releaseResolutionToken":"signed-child-form",
                  "recordId":"child-record"
                }
                """);

        assertEquals(child, policy.authorize(
                request, session, body, declaration("formUnique")));
    }

    @SuppressWarnings("unchecked")
    private EmbedDelegatedRuntimePolicy policy(
            EmbedNativeProcessRuntimePort process,
            EmbedNativeTraversalRuntimePort traversal) {
        ObjectProvider<EmbedNativeProcessRuntimePort> processProvider =
                mock(ObjectProvider.class);
        ObjectProvider<EmbedNativeTraversalRuntimePort> traversalProvider =
                mock(ObjectProvider.class);
        ObjectProvider<EmbedNativeListRuntimePort> listProvider =
                mock(ObjectProvider.class);
        ObjectProvider<EmbedNativeFormRuntimePort> formProvider =
                mock(ObjectProvider.class);
        when(processProvider.getIfAvailable()).thenReturn(process);
        when(traversalProvider.getIfAvailable()).thenReturn(traversal);
        when(listProvider.getIfAvailable()).thenReturn(listRuntimePort);
        when(formProvider.getIfAvailable()).thenReturn(formRuntimePort);
        when(formRuntimePort.verifyReleaseResolutionToken(
                anyString(), any(EmbedNativeFormRuntimePort
                        .VerificationTarget.class)))
                .thenAnswer(invocation -> {
                    EmbedNativeFormRuntimePort.VerificationTarget target =
                            invocation.getArgument(1);
                    String entityCode = target.entityCode() == null
                            ? ("child-form".equals(target.formId())
                                    ? "customer" : root.entityCode())
                            : target.entityCode();
                    return new EmbedNativeFormRuntimePort.VerifiedTarget(
                            entityCode, target.formId(),
                            target.formReleaseId(),
                            target.formReleaseVersion());
                });
        when(formRuntimePort.verifyRuntimeReleaseRequest(
                anyString(), any(EmbedNativeFormRuntimePort
                        .VerificationTarget.class)))
                .thenAnswer(invocation -> {
                    EmbedNativeFormRuntimePort.VerificationTarget target =
                            invocation.getArgument(1);
                    String entityCode = target.entityCode() == null
                            ? ("child-form".equals(target.formId())
                                    ? "customer" : root.entityCode())
                            : target.entityCode();
                    return new EmbedNativeFormRuntimePort.VerifiedTarget(
                            entityCode, target.formId(),
                            target.formReleaseId(),
                            target.formReleaseVersion());
                });
        return new EmbedDelegatedRuntimePolicy(
                targetResolver, processProvider, traversalProvider,
                listProvider, formProvider);
    }

    private MockHttpServletRequest detail(String recordId) {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST", "/api/entity-data/entity/order/detail/"
                + recordId + "/load");
        pathVariables(request, Map.of(
                "entityCode", "order", "recordId", recordId));
        request.setParameter("formId", "form-1");
        request.setParameter("formReleaseId", "form-release-1");
        request.setParameter("formReleaseVersion", "3");
        request.setParameter(
                "formReleaseResolutionToken", "signed-release");
        request.setParameter("listKey", "default");
        request.setParameter("releaseId", "list-release-1");
        request.setParameter("releaseVersion", "2");
        request.setParameter(
                "releaseResolutionToken", "signed-list-release");
        return request;
    }

    private static MockHttpServletRequest get(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    private static MockHttpServletRequest post(String path) {
        return new MockHttpServletRequest("POST", path);
    }

    private static void pathVariables(
            MockHttpServletRequest request,
            Map<String, String> variables) {
        request.setAttribute(
                HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE,
                variables);
    }

    private static EmbedDelegatedRuntimeApi declaration(String method) {
        try {
            return Declarations.class.getDeclaredMethod(method)
                    .getAnnotation(EmbedDelegatedRuntimeApi.class);
        } catch (NoSuchMethodException error) {
            throw new AssertionError(error);
        }
    }

    private static AuthenticatedEmbedSession session(Set<String> capabilities) {
        return new AuthenticatedEmbedSession(
                "session-1", "app-1", "grant-1", "view-1", "release-1",
                "user-1", "alice", "https://portal.example",
                "channel-1234567890", "VIEW", "record-1",
                Map.of(), capabilities,
                Instant.parse("2026-08-31T00:00:00Z"),
                Instant.parse("2026-08-31T01:00:00Z"));
    }

    private static AuthenticatedEmbedSession listSession() {
        return new AuthenticatedEmbedSession(
                "session-1", "app-1", "grant-1", "view-1", "release-1",
                "user-1", "alice", "https://portal.example",
                "channel-1234567890", "LIST", null,
                Map.of(), Set.of("LIST_QUERY"),
                Instant.parse("2026-08-31T00:00:00Z"),
                Instant.parse("2026-08-31T01:00:00Z"));
    }

    private static EmbedNativeFormTarget listRoot() {
        return new EmbedNativeFormTarget(
                "order", "form-1", "form-release-1", 3,
                "default", "list-release-1", 2,
                "LIST", null, null,
                Map.of(), Map.of(), Map.of());
    }

    private static EmbedNativeFormTarget childTarget() {
        return new EmbedNativeFormTarget(
                "customer", "child-form", "child-release", 7,
                null, null, null,
                "VIEW", "child-record", null,
                Map.of(), Map.of(), Map.of());
    }

    private static final class Declarations {

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.ROOT_ENTITY_METADATA,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                        .ROOT_ENTITY_PATH)
        void rootEntity() {
        }

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.FORM_RELEASE,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                        .FORM_RELEASE_PATH_QUERY)
        void formRelease() {
        }

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.FORM_CONTEXT,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                        .FORM_ACTION_BODY)
        void formAction() {
        }

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.REFERENCE_READ,
                requiredCapability = EmbedDelegatedRuntimeApi.Capability
                        .RECORD_VIEW,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.NONE)
        void referenceRead() {
        }

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.REFERENCE_READ,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                        .RECORD_DETAIL_PATH_QUERY)
        void referenceWithDetailBinding() {
        }

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.RECORD_DETAIL,
                requiredCapability = EmbedDelegatedRuntimeApi.Capability
                        .RECORD_VIEW,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                        .RECORD_DETAIL_PATH_QUERY)
        void recordDetail() {
        }

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.FILE_RUNTIME,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.FILE_READ)
        void fileRead() {
        }

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.FILE_RUNTIME,
                requiredCapability = EmbedDelegatedRuntimeApi.Capability
                        .RECORD_CREATE,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding.FILE_WRITE)
        void fileWrite() {
        }

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.PROCESS_RECORD_RUNTIME,
                requiredCapability = EmbedDelegatedRuntimeApi.Capability
                        .RECORD_VIEW,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                        .PROCESS_INSTANCE_PATH)
        void processRead() {
        }

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.FORM_CONTEXT,
                requiredCapability = EmbedDelegatedRuntimeApi.Capability
                        .ACTION_EXECUTE,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                        .FORM_EVENT_BODY)
        void formEvent() {
        }

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.FORM_CONTEXT,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                        .FORM_UNIQUE_PRECHECK)
        void formUnique() {
        }

        @EmbedDelegatedRuntimeApi(
                value = EmbedDelegatedRuntimeApi.Scope.FORM_OWNER_RUNTIME,
                targetBinding = EmbedDelegatedRuntimeApi.TargetBinding
                        .FORM_OWNER_BODY)
        void formOwner() {
        }
    }
}
