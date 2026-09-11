package com.workflow.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import com.workflow.contracts.embed.launch.port.EmbedLaunchIssuePort;
import com.workflow.contracts.embed.EmbedLaunchIssued;
import com.workflow.contracts.embed.EmbedLaunchView;
import com.workflow.contracts.embed.runtime.port.EmbedNativeFormRuntimePort;
import com.workflow.embed.api.web.EmbedApiExceptionHandler;
import com.workflow.embed.api.web.EmbedLaunchEntryController;
import com.workflow.embed.api.web.EmbedNativeFormTargetController;
import com.workflow.embed.api.web.EmbedRecordCreateController;
import com.workflow.embed.api.web.EmbedRecordCreateRequest;
import com.workflow.embed.api.web.EmbedRecordCreateViews;
import com.workflow.embed.api.web.EmbedRuntimeController;
import com.workflow.embed.api.web.EmbedRuntimeListQueryRequest;
import com.workflow.embed.api.web.EmbedRuntimeViews;
import com.workflow.embed.api.web.EmbedSessionController;
import com.workflow.embed.application.launch.EmbedLaunchEntryService;
import com.workflow.embed.application.record.EmbedRecordCreateFacade;
import com.workflow.embed.application.runtime.EmbedNativeFormTargetResolver;
import com.workflow.embed.application.runtime.EmbedRuntimeReadFacade;
import com.workflow.embed.application.session.EmbedSessionAuthenticationService;
import com.workflow.embed.application.session.EmbedSessionExchangeService;
import com.workflow.embed.config.EmbedProperties;
import com.workflow.embed.domain.AuthenticatedEmbedSession;
import com.workflow.embed.domain.EmbedErrorCode;
import com.workflow.embed.domain.EmbedException;
import com.workflow.embed.domain.EmbedNativeFormTarget;
import com.workflow.embed.domain.EmbedSessionIssued;
import com.workflow.embed.domain.EmbedSessionState;
import com.workflow.embed.security.EmbedContextHolder;
import com.workflow.openapi.api.request.OpenEmbedLaunchRequest;
import com.workflow.openapi.api.web.EmbedLaunchController;
import com.workflow.openapi.security.OpenApplicationActorResolver;
import com.workflow.openapi.security.OpenApplicationActorResolver.ResolvedApplicationActor;
import com.workflow.openapi.security.OpenIntegrationProperties;
import io.swagger.v3.core.util.Yaml31;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Locks the independently deployable Embed V1 boundary to its reviewed
 * external contract.
 */
class EmbedOpenApiContractTest {

    private static final String OPEN_API_PATH = "docs/api/embed-v1.yaml";
    private static final String GIT_EXECUTABLE = "/usr/bin/git";
    private static final Set<String> CONTRACT_CRITICAL_HEADERS = Set.of(
            "Location", "Retry-After", "Idempotent-Replay");
    private static final Set<String> V1_CAPABILITIES = Set.of(
            "LIST_QUERY",
            "SELECTION_RETURN",
            "RECORD_VIEW",
            "RECORD_CREATE",
            "ACTION_EXECUTE");
    private static final Set<OperationKey> RETIRED_FORM_PROJECTION_OPERATIONS =
            Set.of(
                    new OperationKey(
                            "/api/embed/v1/runtime/form", HttpMethod.GET),
                    new OperationKey(
                            "/api/embed/v1/runtime/form/evaluations",
                            HttpMethod.POST),
                    new OperationKey(
                            "/api/embed/v1/runtime/form/fields/"
                                    + "{fieldCode}/options/query",
                            HttpMethod.POST),
                    new OperationKey(
                            "/api/embed/v1/runtime/form/fields/"
                                    + "{fieldCode}/lookups/query",
                            HttpMethod.POST),
                    new OperationKey(
                            "/api/embed/v1/runtime/records/{recordId}",
                            HttpMethod.GET));

    private static final Set<String> CLOSED_REQUEST_SCHEMAS = Set.of(
            "ClientCredentialsTokenRequest",
            "EmbedLaunchRequest",
            "SignedJwtSubject",
            "TrustedExternalIdSubject",
            "ListEntry",
            "CreateEntry",
            "ViewEntry",
            "LaunchUi",
            "ExchangeRequest",
            "HeartbeatRequest",
            "ListQueryRequest",
            "ScalarFilterInput",
            "InFilterInput",
            "BetweenFilterInput",
            "CreateRecordRequest");

    private static final Set<String> CLOSED_MESSAGE_SCHEMAS = Set.of(
            "ReadyWindowMessage",
            "InitWindowMessage",
            "InitAckMessage",
            "AckEventPayload",
            "InitializedEventPayload",
            "ResizeEventPayload",
            "SelectionChangedEventPayload",
            "FormSavedEventPayload",
            "EmbedBridgeErrorPayload",
            "SessionExpiredEventPayload",
            "NavigationRequestEventPayload",
            "ActionStartedEventPayload",
            "ActionCompletedEventPayload",
            "CloseRequestedEventPayload",
            "HostRecordProjection");

    private static final List<Class<?>> EMBED_CONTROLLER_TYPES = List.of(
            EmbedLaunchController.class,
            EmbedLaunchEntryController.class,
            EmbedSessionController.class,
            EmbedRuntimeController.class,
            EmbedNativeFormTargetController.class,
            EmbedRecordCreateController.class);

    private static final Map<OperationKey, RequestDtoContract>
            REQUEST_DTO_CONTRACTS = Map.ofEntries(
                    requestDto(
                            "/api/open/v1/embed-launches",
                            "EmbedLaunchRequest",
                            OpenEmbedLaunchRequest.class,
                            """
                                    {
                                      "viewKey":"supplier-work-orders",
                                      "parentOrigin":"https://portal.partner.example",
                                      "channelId":"channel_0123456789",
                                      "subject":{
                                        "type":"SIGNED_JWT",
                                        "assertion":"signed.jwt.value"
                                      },
                                      "entry":{"mode":"LIST"},
                                      "context":{},
                                      "ui":{"locale":"zh-CN","theme":"light"}
                                    }
                                    """),
                    requestDto(
                            "/api/embed/v1/launches/{launchId}/exchange",
                            "ExchangeRequest",
                            EmbedSessionController.ExchangeRequest.class,
                            """
                                    {
                                      "launchCode":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                      "channelId":"channel_0123456789",
                                      "parentOrigin":"https://portal.partner.example",
                                      "parentNonce":"AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                      "childNonce":"BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                                      "sdkVersion":"1.0.0"
                                    }
                                    """),
                    requestDto(
                            "/api/embed/v1/session/heartbeat",
                            "HeartbeatRequest",
                            EmbedSessionController.HeartbeatRequest.class,
                            """
                                    {
                                      "visible":true,
                                      "clientTime":"2026-08-27T07:00:00Z"
                                    }
                                    """),
                    requestDto(
                            "/api/embed/v1/runtime/list/query",
                            "ListQueryRequest",
                            EmbedRuntimeListQueryRequest.class,
                            """
                                    {"pageNum":1,"pageSize":20,"filters":[]}
                                    """),
                    requestDto(
                            "/api/embed/v1/runtime/records",
                            "CreateRecordRequest",
                            EmbedRecordCreateRequest.class,
                            """
                                    {
                                      "data":{"title":"新工单"},
                                      "clientMutationId":"client-1"
                                    }
                                    """));

    private static final Map<OperationKey, OperationContract>
            EXPECTED_OPERATIONS = Map.ofEntries(
                    operation(
                            "/oauth2/token",
                            HttpMethod.POST,
                            "issueMachineAccessToken",
                            SecurityBoundary.CLIENT_BASIC,
                            Set.of("200")),
                    operation(
                            "/api/open/v1/embed-launches",
                            HttpMethod.POST,
                            "issueEmbedLaunch",
                            SecurityBoundary.MACHINE_OAUTH,
                            Set.of("201")),
                    operation(
                            "/embed/v1/launches/{launchId}",
                            HttpMethod.GET,
                            "getEmbedLaunchEntry",
                            SecurityBoundary.PUBLIC,
                            Set.of("200")),
                    operation(
                            "/api/embed/v1/launches/{launchId}/exchange",
                            HttpMethod.POST,
                            "exchangeEmbedLaunch",
                            SecurityBoundary.PUBLIC,
                            Set.of("200")),
                    operation(
                            "/api/embed/v1/session",
                            HttpMethod.GET,
                            "getEmbedSession",
                            SecurityBoundary.EMBED_BEARER,
                            Set.of("200")),
                    operation(
                            "/api/embed/v1/session",
                            HttpMethod.DELETE,
                            "logoutEmbedSession",
                            SecurityBoundary.EMBED_BEARER,
                            Set.of("204")),
                    operation(
                            "/api/embed/v1/session/heartbeat",
                            HttpMethod.POST,
                            "heartbeatEmbedSession",
                            SecurityBoundary.EMBED_BEARER,
                            Set.of("200")),
                    operation(
                            "/api/embed/v1/runtime/bootstrap",
                            HttpMethod.GET,
                            "getEmbedBootstrap",
                            SecurityBoundary.EMBED_BEARER,
                            Set.of("200")),
                    operation(
                            "/api/embed/v1/runtime/schema",
                            HttpMethod.GET,
                            "getEmbedListSchema",
                            SecurityBoundary.EMBED_BEARER,
                            Set.of("200")),
                    operation(
                            "/api/embed/v1/runtime/list/query",
                            HttpMethod.POST,
                            "queryEmbedList",
                            SecurityBoundary.EMBED_BEARER,
                            Set.of("200")),
                    operation(
                            "/api/embed/v1/runtime/native-form-target",
                            HttpMethod.GET,
                            "getEmbedNativeFormTarget",
                            SecurityBoundary.EMBED_BEARER,
                            Set.of("200")),
                    operation(
                            "/api/embed/v1/runtime/records",
                            HttpMethod.POST,
                            "createEmbedRecord",
                            SecurityBoundary.EMBED_BEARER,
                            Set.of("201")));

    @Test
    void embedOpenApiIsFullyValidAndLocksTheV1Boundary()
            throws IOException {
        OpenAPI api = parseCurrentContract();

        assertCurrentContract(api);
    }

    @Test
    void pullRequestContractIsBackwardCompatibleWhenBaselineExists()
            throws IOException, InterruptedException {
        Path root = repositoryRoot();
        Path contract = root.resolve(OPEN_API_PATH);

        // A first introduction has no historical file to compare. It still
        // runs every structural assertion instead of skipping this test.
        assertCurrentContract(parseContract(contract));

        String baseRef = System.getenv("OPENAPI_BASE_REF");
        if (baseRef == null || baseRef.isBlank()
                || baseRef.matches("0+")) {
            baseRef = "origin/main";
        }
        String baseline = readContractFromGit(root, baseRef);
        if (baseline == null) {
            baseline = readContractFromGit(root, "HEAD^1");
        }
        if (baseline == null) {
            assertTrue(Files.isRegularFile(contract),
                    "首次引入无基线时，当前 Embed 契约仍必须存在");
            return;
        }

        ChangedOpenApi difference = OpenApiCompare.fromContents(
                baseline,
                Files.readString(contract));
        assertNotNull(difference, "Embed OpenAPI 兼容性差异不能为空");
        if (!difference.isCompatible()) {
            // Published Form projection endpoints and the configurable OAuth
            // business scope were intentionally retired as contract changes.
            // Surviving operations remain locked by the bidirectional
            // controller/OpenAPI assertions below. Once the new contract is
            // merged, neither exception exists in the next baseline.
            assertTrue(containsRetiredProjectionOperations(baseline)
                            || containsRetiredMachineScope(baseline),
                    () -> "Embed OpenAPI V1 contains an unrelated breaking "
                            + "change: " + difference);
        }
    }

    @Test
    void controllerMappingsAndSuccessCodesMatchTheOpenApiBothWays()
            throws Exception {
        OpenAPI api = parseCurrentContract();
        Map<OperationKey, Operation> documented = openApiOperations(api);
        Map<OperationKey, HandlerBinding> implemented =
                controllerOperations();

        assertEquals(documented.keySet(), implemented.keySet(),
                "Controller 与 Embed OpenAPI 的 method/path 必须双向一致");

        Map<OperationKey, SuccessProof> actualSuccessResponses =
                invokeControllerSuccessPaths();
        assertEquals(documented.keySet(), actualSuccessResponses.keySet(),
                "每个 Embed operation 都必须有实现侧成功语义证明");
        documented.forEach((key, operation) -> assertEquals(
                successCodes(operation),
                actualSuccessResponses.get(key).statusCodes(),
                () -> "Controller 成功状态码与 OpenAPI 不一致: " + key));
    }

    @Test
    void controllerAndErrorWriterResponsesConformToOpenApiSchemasAndHeaders()
            throws Exception {
        OpenAPI api = parseCurrentContract();
        JsonNode contractSource = contractSource();
        ObjectMapper mapper = responseMapper();
        Map<OperationKey, SuccessProof> successes =
                invokeControllerSuccessPaths();

        Set<OperationKey> locallySerializedJsonSuccesses = successes
                .entrySet().stream()
                .filter(entry -> entry.getValue().response() != null)
                .filter(entry -> entry.getValue().response().getBody() != null)
                .filter(entry -> hasJsonResponse(
                        contractSource,
                        entry.getKey(),
                        entry.getValue().statusCodes().iterator().next()))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                        new OperationKey(
                                "/api/open/v1/embed-launches",
                                HttpMethod.POST),
                        new OperationKey(
                                "/api/embed/v1/launches/{launchId}/exchange",
                                HttpMethod.POST),
                        new OperationKey(
                                "/api/embed/v1/session", HttpMethod.GET),
                        new OperationKey(
                                "/api/embed/v1/session/heartbeat",
                                HttpMethod.POST),
                        new OperationKey(
                                "/api/embed/v1/runtime/bootstrap",
                                HttpMethod.GET),
                        new OperationKey(
                                "/api/embed/v1/runtime/schema",
                                HttpMethod.GET),
                        new OperationKey(
                                "/api/embed/v1/runtime/list/query",
                                HttpMethod.POST),
                        new OperationKey(
                                "/api/embed/v1/runtime/native-form-target",
                                HttpMethod.GET),
                        new OperationKey(
                                "/api/embed/v1/runtime/records",
                                HttpMethod.POST)),
                locallySerializedJsonSuccesses,
                "新增本地 JSON 成功响应时必须登记真实序列化契约；"
                        + "OAuth token 由 Spring Authorization Server 持有");

        successes.forEach((key, proof) -> {
            if (!locallySerializedJsonSuccesses.contains(key)) {
                return;
            }
            String status = proof.statusCodes().iterator().next();
            assertResponseBodyConforms(
                    contractSource, mapper, key, status,
                    proof.response().getBody());
            assertCriticalHeadersConform(
                    contractSource, mapper, key, status,
                    proof.response());
        });

        for (ErrorResponseFixture fixture : errorResponseFixtures()) {
            assertEquals(Integer.parseInt(fixture.status()),
                    fixture.response().getStatusCode().value(),
                    () -> "异常 Writer 状态码漂移: " + fixture.key());
            assertResponseBodyConforms(
                    contractSource, mapper, fixture.key(), fixture.status(),
                    fixture.response().getBody());
            assertCriticalHeadersConform(
                    contractSource, mapper, fixture.key(), fixture.status(),
                    fixture.response());
        }
    }

    @Test
    void jsonRequestDtosMatchSchemasAndRejectUnknownOrMissingFields()
            throws Exception {
        OpenAPI api = parseCurrentContract();
        Map<OperationKey, Operation> documented = openApiOperations(api);
        Map<OperationKey, HandlerBinding> implemented =
                controllerOperations();
        Map<OperationKey, Class<?>> actualRequestBodies = new HashMap<>();

        Set<OperationKey> documentedJsonBodies = documented.entrySet()
                .stream()
                .filter(entry -> entry.getValue().getRequestBody() != null)
                .filter(entry -> entry.getValue().getRequestBody()
                        .getContent() != null)
                .filter(entry -> entry.getValue().getRequestBody()
                        .getContent().containsKey("application/json"))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
        assertEquals(REQUEST_DTO_CONTRACTS.keySet(),
                documentedJsonBodies,
                "OpenAPI JSON request body 集合与 DTO 门禁登记不一致");

        implemented.forEach((key, binding) -> {
            if (binding.controllerMethod() == null) {
                return;
            }
            List<Parameter> bodies = Arrays.stream(
                            binding.controllerMethod().getParameters())
                    .filter(parameter -> parameter.isAnnotationPresent(
                            RequestBody.class))
                    .toList();
            assertTrue(bodies.size() <= 1,
                    () -> "Controller 只能有一个 @RequestBody: " + key);
            if (!bodies.isEmpty()) {
                actualRequestBodies.put(key, bodies.get(0).getType());
            }
        });
        assertEquals(REQUEST_DTO_CONTRACTS.keySet(),
                actualRequestBodies.keySet(),
                "JSON request body 的 Controller 集合与门禁登记不一致");

        ObjectMapper mapper = new ObjectMapper()
                .findAndRegisterModules()
                .configure(
                        DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                        false);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            REQUEST_DTO_CONTRACTS.forEach((key, contract) -> {
                Operation operation = documented.get(key);
                assertNotNull(operation, () -> "OpenAPI operation 缺失: " + key);
                assertEquals(contract.type(), actualRequestBodies.get(key),
                        () -> "Controller DTO 类型漂移: " + key);
                assertEquals(contract.schemaName(),
                        requestSchemaName(operation),
                        () -> "OpenAPI request schema 漂移: " + key);

                ObjectNode valid = readObject(mapper, contract.validJson());
                assertAccepted(mapper, validator, contract, valid, key);

                ObjectNode unknown = valid.deepCopy();
                unknown.putNull("flowUserId");
                assertRejected(mapper, validator, contract, unknown,
                        key + " unknown field");

                Schema<?> schema = api.getComponents().getSchemas()
                        .get(contract.schemaName());
                assertNotNull(schema,
                        () -> "请求 Schema 缺失: " + contract.schemaName());
                List<String> required = schema.getRequired() == null
                        ? List.of() : schema.getRequired();
                required.forEach(field -> {
                    ObjectNode missing = valid.deepCopy();
                    missing.remove(field);
                    assertRejected(mapper, validator, contract, missing,
                            key + " missing required field " + field);
                });
                schema.getProperties().keySet().stream()
                        .filter(field -> !required.contains(field))
                        .forEach(field -> {
                            ObjectNode optional = valid.deepCopy();
                            optional.remove(field);
                            assertAccepted(
                                    mapper, validator, contract, optional,
                                    key + " optional field " + field);
                        });
            });

            assertNestedUnknownFieldsRejected(mapper, validator);
        }
    }

    /**
     * Verifies paths, operations, authentication and success semantics as one
     * boundary so adding a route cannot silently widen the iframe API.
     */
    private void assertCurrentContract(OpenAPI api) throws IOException {
        assertEquals("3.1.0", api.getOpenapi());
        assertNotNull(api.getPaths(), "Embed paths 缺失");
        assertEquals(11, api.getPaths().size());
        assertEquals(
                EXPECTED_OPERATIONS.keySet().stream()
                        .map(OperationKey::path)
                        .collect(Collectors.toSet()),
                api.getPaths().keySet());
        assertTrue(api.getSecurity() == null || api.getSecurity().isEmpty(),
                "Embed 外部契约不得使用会掩盖逐操作边界的全局鉴权");

        Map<OperationKey, Operation> actual = openApiOperations(api);
        assertEquals(12, actual.size());
        assertEquals(EXPECTED_OPERATIONS.keySet(), actual.keySet());
        RETIRED_FORM_PROJECTION_OPERATIONS.forEach(key -> assertFalse(
                actual.containsKey(key),
                () -> "旧 Embed FORM 投影路由不得重新公开: " + key));
        assertFalse(actual.keySet().stream()
                        .anyMatch(key -> key.method() == HttpMethod.PATCH),
                "Embed V1 禁止注册 PATCH 操作");

        Set<String> operationIds = new HashSet<>();
        EXPECTED_OPERATIONS.forEach((key, expected) -> {
            Operation operation = actual.get(key);
            assertNotNull(operation, () -> "缺少操作: " + key);
            assertEquals(expected.operationId(), operation.getOperationId(),
                    () -> "operationId 漂移: " + key);
            assertTrue(operationIds.add(operation.getOperationId()),
                    () -> "operationId 重复: "
                            + operation.getOperationId());
            assertSecurity(key, operation, expected.security());

            assertEquals(expected.successCodes(), successCodes(operation),
                    () -> "成功状态码漂移: " + key);
        });
        assertEquals(12, operationIds.size());

        assertSecuritySchemes(api);
        assertClosedSchemas(api);
        assertInitializedCapabilities(api);
        assertStableRuntimeViewIdentity(api);
        assertFormPresentationContract(api);
        assertNativeFormRuntimeContract(api);
    }

    /**
     * Hosts retain only the stable Embed view identity. Internal runtime
     * snapshot revisions are session implementation details and must never
     * become coordinates that third-party callers persist or replay.
     */
    private void assertStableRuntimeViewIdentity(OpenAPI api) {
        Map<String, Schema> schemas = api.getComponents().getSchemas();
        assertEquals(Set.of("key", "surfaceType"),
                schemas.get("LaunchView").getProperties().keySet());
        assertEquals(Set.of("key", "name", "surfaceType", "entryMode"),
                schemas.get("BootstrapView").getProperties().keySet());
        assertEquals(Set.of("key", "surfaceType"),
                schemas.get("SchemaView").getProperties().keySet());
    }

    /** Launch accepts the optional selector and Bootstrap always returns the resolved value. */
    @SuppressWarnings("unchecked")
    private void assertFormPresentationContract(OpenAPI api) {
        Map<String, Schema> schemas = api.getComponents().getSchemas();
        Schema<?> launchUi = schemas.get("LaunchUi");
        Schema<?> requested = (Schema<?>) launchUi.getProperties().get("formPresentation");
        assertEquals(List.of("seamless", "dialog"), requested.getEnum());
        assertEquals("seamless", requested.getDefault());

        Schema<?> bootstrapUi = schemas.get("BootstrapUi");
        assertTrue(bootstrapUi.getRequired().contains("formPresentation"));
        Schema<?> resolved = (Schema<?>) bootstrapUi.getProperties().get("formPresentation");
        assertEquals(List.of("seamless", "dialog"), resolved.getEnum());
    }

    private Map<OperationKey, Operation> openApiOperations(OpenAPI api) {
        Map<OperationKey, Operation> operations = new HashMap<>();
        api.getPaths().forEach((path, item) ->
                item.readOperationsMap().forEach((method, operation) -> {
                    Operation previous = operations.put(
                            new OperationKey(path, method), operation);
                    assertTrue(previous == null,
                            () -> "OpenAPI method/path 重复: "
                                    + method + " " + path);
                }));
        return operations;
    }

    private Set<String> successCodes(Operation operation) {
        return operation.getResponses().keySet().stream()
                .filter(code -> code.matches("2\\d\\d"))
                .collect(Collectors.toSet());
    }

    /**
     * Uses Spring's own mapping merger so class-level and method-level paths
     * are interpreted exactly like MVC. The OAuth token endpoint is supplied
     * by Spring Authorization Server, so its production settings bean is
     * invoked reflectively and included as the twelfth implementation.
     */
    private Map<OperationKey, HandlerBinding> controllerOperations()
            throws Exception {
        Map<OperationKey, HandlerBinding> operations = new HashMap<>();
        MappingInspector inspector = new MappingInspector();
        for (Class<?> controllerType : EMBED_CONTROLLER_TYPES) {
            for (Method method : controllerType.getDeclaredMethods()) {
                RequestMappingInfo mapping = inspector.inspect(
                        method, controllerType);
                if (mapping == null) {
                    continue;
                }
                Set<String> paths = mapping.getPatternValues();
                Set<RequestMethod> methods = mapping
                        .getMethodsCondition().getMethods();
                assertEquals(1, paths.size(),
                        () -> "Controller mapping 必须只有一个 path: "
                                + controllerType.getName() + "#"
                                + method.getName());
                assertEquals(1, methods.size(),
                        () -> "Controller mapping 必须只有一个 method: "
                                + controllerType.getName() + "#"
                                + method.getName());
                OperationKey key = new OperationKey(
                        paths.iterator().next(),
                        HttpMethod.valueOf(
                                methods.iterator().next().name()));
                HandlerBinding previous = operations.put(
                        key,
                        new HandlerBinding(method,
                                controllerType.getName()));
                assertTrue(previous == null,
                        () -> "Controller method/path 重复: " + key);
            }
        }

        TokenEndpointBinding token = tokenEndpointBinding();
        OperationKey tokenKey = new OperationKey(
                token.settings().getTokenEndpoint(), HttpMethod.POST);
        HandlerBinding previous = operations.put(
                tokenKey,
                new HandlerBinding(null,
                        token.factoryMethod().toGenericString()));
        assertTrue(previous == null,
                () -> "OAuth token endpoint 与 Controller mapping 冲突: "
                        + tokenKey);
        return operations;
    }

    private TokenEndpointBinding tokenEndpointBinding() throws Exception {
        Class<?> configurationType = Class.forName(
                "com.workflow.openapi.security."
                        + "OpenIntegrationSecurityConfiguration"
                        + "$EnabledOpenIntegrationSecurity");
        Constructor<?> constructor = configurationType
                .getDeclaredConstructor();
        assertTrue(constructor.trySetAccessible(),
                "无法访问 Open Integration security 配置");
        Object configuration = constructor.newInstance();
        Method factory = configurationType.getDeclaredMethod(
                "authorizationServerSettings",
                OpenIntegrationProperties.class);
        assertTrue(factory.trySetAccessible(),
                "无法访问 AuthorizationServerSettings bean 工厂");
        Object value = factory.invoke(
                configuration,
                new OpenIntegrationProperties());
        assertTrue(value instanceof AuthorizationServerSettings,
                "AuthorizationServerSettings bean 类型漂移");
        return new TokenEndpointBinding(
                (AuthorizationServerSettings) value,
                factory);
    }

    /**
     * Executes every implemented happy path through its real controller
     * method, including the native Published Form target and ID-only create
     * receipt.
     */
    private Map<OperationKey, SuccessProof> invokeControllerSuccessPaths()
            throws Exception {
        Map<OperationKey, SuccessProof> statuses = new HashMap<>();
        TokenEndpointBinding token = tokenEndpointBinding();
        // Spring Authorization Server owns this handler; a successful OAuth2
        // token response is the protocol-defined HTTP 200 response. There is
        // no project-owned response object that could be honestly serialized
        // here, so its proof intentionally contains status semantics only.
        statuses.put(new OperationKey(
                        token.settings().getTokenEndpoint(), HttpMethod.POST),
                new SuccessProof(Set.of("200"), null));

        EmbedLaunchIssuePort issuePort = mock(EmbedLaunchIssuePort.class);
        OpenApplicationActorResolver actorResolver =
                mock(OpenApplicationActorResolver.class);
        Authentication authentication = mock(Authentication.class);
        when(actorResolver.resolve(authentication, "trace-contract"))
                .thenReturn(new ResolvedApplicationActor(
                        "app-contract", "client-contract",
                        "trace-contract"));
        when(issuePort.issue(any(), any())).thenReturn(
                new EmbedLaunchIssued(
                        "lch_0123456789abcdef",
                        "https://embed.flow.example.com/embed/v1/launches/"
                                + "lch_0123456789abcdef",
                        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        Instant.parse("2026-08-27T08:31:00Z"),
                        new EmbedLaunchView(
                                "supplier-work-orders", "LIST"),
                        "flow-embed/1"));
        MockHttpServletRequest launchServletRequest =
                new MockHttpServletRequest();
        launchServletRequest.addHeader("X-Trace-Id", "trace-contract");
        recordStatus(statuses,
                new OperationKey(
                        "/api/open/v1/embed-launches", HttpMethod.POST),
                new EmbedLaunchController(issuePort, actorResolver).issue(
                        launchRequest(), authentication,
                        launchServletRequest));

        EmbedLaunchEntryService entryService =
                mock(EmbedLaunchEntryService.class);
        when(entryService.resolve("lch_0123456789abcdef"))
                .thenReturn(Optional.of(
                        new EmbedLaunchEntryService.EntryMetadata(
                                "lch_0123456789abcdef",
                                "https://portal.partner.example",
                                "channel_0123456789",
                                "flow-embed/1")));
        EmbedProperties entryProperties = new EmbedProperties();
        entryProperties.setEntryAssetPath("/embed-assets/embed-main.js");
        entryProperties.setEntryStylePath("/embed-assets/embed-main.css");
        recordStatus(statuses,
                new OperationKey(
                        "/embed/v1/launches/{launchId}", HttpMethod.GET),
                new EmbedLaunchEntryController(
                        entryService,
                        entryProperties,
                        new ObjectMapper()).entry(
                                "lch_0123456789abcdef"));

        invokeSessionControllerSuccessPaths(statuses);
        invokeRuntimeControllerSuccessPaths(statuses);
        invokeNativeFormControllerSuccessPaths(statuses);
        return statuses;
    }

    private void invokeSessionControllerSuccessPaths(
            Map<OperationKey, SuccessProof> statuses) {
        EmbedSessionExchangeService exchangeService =
                mock(EmbedSessionExchangeService.class);
        EmbedSessionAuthenticationService authenticationService =
                mock(EmbedSessionAuthenticationService.class);
        EmbedSessionController controller = new EmbedSessionController(
                exchangeService, authenticationService);
        Instant idle = Instant.parse("2026-08-27T08:40:00Z");
        Instant absolute = Instant.parse("2026-08-27T09:30:00Z");
        when(exchangeService.exchange(any(), any())).thenReturn(
                new EmbedSessionIssued(
                        "ses-contract",
                        "opaque-token",
                        absolute,
                        idle,
                        60,
                        "/api/embed/v1/runtime/bootstrap",
                        "flow-embed/1"));
        MockHttpServletRequest exchangeRequest =
                tracedRequest();
        exchangeRequest.setRemoteAddr("127.0.0.1");
        recordStatus(statuses,
                new OperationKey(
                        "/api/embed/v1/launches/{launchId}/exchange",
                        HttpMethod.POST),
                controller.exchange(
                        "lch_0123456789abcdef",
                        "1",
                        exchangeRequest,
                        new EmbedSessionController.ExchangeRequest(
                                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                "channel_0123456789",
                                "https://portal.partner.example",
                                "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                                "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB",
                                "1.0.0")));

        AuthenticatedEmbedSession session = authenticatedSession(
                idle, absolute);
        MockHttpServletRequest authenticatedRequest =
                tracedRequest();
        authenticatedRequest.setAttribute(
                com.workflow.embed.security
                        .EmbedSessionAuthenticationFilter
                        .AUTHENTICATED_SESSION_ATTRIBUTE,
                session);
        EmbedSessionState state = new EmbedSessionState(
                "ses-contract", "ACTIVE", idle, absolute, 60);
        when(authenticationService.state(session)).thenReturn(state);
        when(authenticationService.heartbeat(session)).thenReturn(state);
        recordStatus(statuses,
                new OperationKey(
                        "/api/embed/v1/session", HttpMethod.GET),
                controller.session(authenticatedRequest));
        recordStatus(statuses,
                new OperationKey(
                        "/api/embed/v1/session/heartbeat",
                        HttpMethod.POST),
                controller.heartbeat(
                        authenticatedRequest,
                        new EmbedSessionController.HeartbeatRequest(
                                true,
                                Instant.parse("2026-08-27T08:00:00Z"))));
        recordStatus(statuses,
                new OperationKey(
                        "/api/embed/v1/session", HttpMethod.DELETE),
                controller.logout(
                        "Bearer "
                                + "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
                        tracedRequest()));
    }

    private void invokeRuntimeControllerSuccessPaths(
            Map<OperationKey, SuccessProof> statuses) {
        EmbedRuntimeReadFacade facade = mock(EmbedRuntimeReadFacade.class);
        EmbedRuntimeController controller = new EmbedRuntimeController(facade);
        Instant expiresAt = Instant.parse("2026-08-27T09:30:00Z");
        Instant idleExpiresAt = Instant.parse("2026-08-27T08:40:00Z");
        when(facade.bootstrap()).thenReturn(new EmbedRuntimeViews.Bootstrap(
                new EmbedRuntimeViews.Session(
                        "ses-contract", expiresAt, idleExpiresAt),
                new EmbedRuntimeViews.Actor("契约用户"),
                new EmbedRuntimeViews.View(
                        "supplier-work-orders", "供应商工单", "LIST",
                        "LIST"),
                List.copyOf(V1_CAPABILITIES),
                new EmbedRuntimeViews.Ui(
                        "zh-CN", "light", "seamless", true, true, true, 20, "AUTO"),
                new EmbedRuntimeViews.Limits(100, 262144, 100)));
        when(facade.schema()).thenReturn(new EmbedRuntimeViews.Schema(
                new EmbedRuntimeViews.SchemaView(
                        "supplier-work-orders", "LIST"),
                new EmbedRuntimeViews.Entity("work_order", "工单"),
                new EmbedRuntimeViews.ListSchema(
                        new EmbedRuntimeViews.Selection(
                                "SINGLE", "id", List.of("title")),
                        new EmbedRuntimeViews.Pagination(true, 100),
                        List.of(), List.of()),
                null,
                List.of()));
        when(facade.query(any())).thenReturn(
                new EmbedRuntimeViews.ListResult(
                        List.of(), false, 1, 20, 0L));
        recordStatus(statuses,
                new OperationKey(
                        "/api/embed/v1/runtime/bootstrap", HttpMethod.GET),
                controller.bootstrap("1", tracedRequest()));
        recordStatus(statuses,
                new OperationKey(
                        "/api/embed/v1/runtime/schema", HttpMethod.GET),
                controller.schema(tracedRequest()));
        recordStatus(statuses,
                new OperationKey(
                        "/api/embed/v1/runtime/list/query",
                        HttpMethod.POST),
                controller.query(
                        new EmbedRuntimeListQueryRequest(),
                        tracedRequest()));
    }

    private void invokeNativeFormControllerSuccessPaths(
            Map<OperationKey, SuccessProof> statuses) {
        EmbedNativeFormTargetResolver targetResolver =
                mock(EmbedNativeFormTargetResolver.class);
        EmbedNativeFormRuntimePort nativeRuntimePort =
                mock(EmbedNativeFormRuntimePort.class);
        EmbedNativeFormTarget target = new EmbedNativeFormTarget(
                "work_order", "form-1", "form-release-1", 4,
                "supplier-work-orders", "list-release-1", 3,
                "CREATE", null, null,
                Map.of("supplierId", "supplier-1"),
                Map.of("supplierId", "supplier-1"),
                Map.of("supplierId", "supplier-1"));
        when(targetResolver.authorize(any(), any(), any()))
                .thenReturn(target);
        when(nativeRuntimePort.issueReleaseResolutionToken(any()))
                .thenReturn("native-release-token-0123456789abcdef");
        EmbedNativeFormTargetController targetController =
                new EmbedNativeFormTargetController(
                        targetResolver, nativeRuntimePort);

        EmbedContextHolder.set(authenticatedSession(
                Instant.parse("2026-08-27T08:40:00Z"),
                Instant.parse("2026-08-27T09:30:00Z")));
        try {
            recordStatus(statuses,
                    new OperationKey(
                            "/api/embed/v1/runtime/native-form-target",
                            HttpMethod.GET),
                    targetController.target(
                            "CREATE", null, tracedRequest()));
        } finally {
            EmbedContextHolder.clear();
        }

        EmbedRecordCreateFacade createFacade =
                mock(EmbedRecordCreateFacade.class);
        EmbedRecordCreateViews.CreateResult result =
                new EmbedRecordCreateViews.CreateResult(
                        "eor_0123456789abcdef",
                        new EmbedRecordCreateViews.CreatedRecord(
                                "record-created", null),
                        List.of(), "client-1");
        when(createFacade.create(any(), any(), any())).thenReturn(
                new EmbedRecordCreateFacade.CreateOutcome(result, false));
        EmbedRecordCreateRequest create = new EmbedRecordCreateRequest();
        create.setData(Map.of("title", "新工单"));
        recordStatus(statuses,
                new OperationKey(
                        "/api/embed/v1/runtime/records", HttpMethod.POST),
                new EmbedRecordCreateController(createFacade).create(
                        create, "key-contract", tracedRequest()));
    }

    private OpenEmbedLaunchRequest launchRequest() {
        return new OpenEmbedLaunchRequest(
                "supplier-work-orders",
                "https://portal.partner.example",
                "channel_0123456789",
                new OpenEmbedLaunchRequest.Subject(
                        "SIGNED_JWT", "signed.jwt.value",
                        null, null),
                new OpenEmbedLaunchRequest.Entry("LIST", null),
                Map.of(),
                new OpenEmbedLaunchRequest.Ui("zh-CN", "light"));
    }

    private AuthenticatedEmbedSession authenticatedSession(
            Instant idle,
            Instant absolute) {
        return new AuthenticatedEmbedSession(
                "ses-contract",
                "app-contract",
                "grant-contract",
                "view-contract",
                "release-contract",
                "user-contract",
                "contract-user",
                "https://portal.partner.example",
                "channel_0123456789",
                "LIST",
                null,
                Map.of(),
                V1_CAPABILITIES,
                idle,
                absolute);
    }

    private void recordStatus(
            Map<OperationKey, SuccessProof> statuses,
            OperationKey key,
            ResponseEntity<?> response) {
        assertNotNull(response, () -> "Controller response 不能为空: " + key);
        SuccessProof previous = statuses.put(
                key,
                new SuccessProof(
                        Set.of(String.valueOf(
                                response.getStatusCode().value())),
                        response));
        assertTrue(previous == null,
                () -> "Controller success fixture 重复: " + key);
    }

    private MockHttpServletRequest tracedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Trace-Id", "trace-contract");
        return request;
    }

    /**
     * Uses the production exception writer rather than hand-building an error
     * JSON fixture. The cases cover the generic envelope, its specialized 422
     * data shape, and both operations that attach a semantic Retry-After.
     */
    private List<ErrorResponseFixture> errorResponseFixtures() {
        EmbedApiExceptionHandler handler = new EmbedApiExceptionHandler();
        List<ErrorResponseFixture> fixtures = new ArrayList<>();
        fixtures.add(new ErrorResponseFixture(
                new OperationKey(
                        "/api/embed/v1/runtime/schema", HttpMethod.GET),
                "403",
                handler.handle(new EmbedException(
                                403,
                                EmbedErrorCode.EMBED_OPERATION_NOT_ALLOWED,
                                "Embed operation is not allowed"),
                        tracedRequest())));
        fixtures.add(new ErrorResponseFixture(
                new OperationKey(
                        "/api/embed/v1/runtime/list/query",
                        HttpMethod.POST),
                "429",
                handler.handle(new EmbedException(
                                429,
                                EmbedErrorCode.RATE_LIMIT_EXCEEDED,
                                "Embed rate limit exceeded",
                                7L),
                        tracedRequest())));
        fixtures.add(new ErrorResponseFixture(
                new OperationKey(
                        "/api/embed/v1/runtime/records", HttpMethod.POST),
                "409",
                handler.handle(new EmbedException(
                                409,
                                EmbedErrorCode.EMBED_REQUEST_IN_PROGRESS,
                                "The same idempotent request is still processing",
                                2L),
                        tracedRequest())));
        fixtures.add(new ErrorResponseFixture(
                new OperationKey(
                        "/api/embed/v1/runtime/records", HttpMethod.POST),
                "422",
                handler.handle(new EmbedException(
                                422,
                                EmbedErrorCode.FORM_VALIDATION_FAILED,
                                "Form validation failed",
                                null,
                                null,
                                Map.of(
                                        "violations", List.of(Map.of(
                                                "path", "data.title",
                                                "code", "REQUIRED",
                                                "message", "Title is required")),
                                        "relaunchRequired", false)),
                        tracedRequest())));
        return List.copyOf(fixtures);
    }

    private boolean hasJsonResponse(
            JsonNode source,
            OperationKey key,
            String status) {
        return documentedResponse(source, key, status)
                .path("content")
                .has("application/json");
    }

    private void assertResponseBodyConforms(
            JsonNode source,
            ObjectMapper mapper,
            OperationKey key,
            String status,
            Object body) {
        JsonNode mediaType = documentedResponse(source, key, status)
                .path("content")
                .path("application/json");
        assertTrue(!mediaType.isMissingNode(),
                () -> "OpenAPI JSON response 缺失: " + key + " " + status);
        JsonNode schemaNode = mediaType.path("schema");
        assertTrue(schemaNode.isObject() || schemaNode.isBoolean(),
                () -> "OpenAPI response schema 缺失: " + key + " " + status);
        assertJsonConforms(
                source,
                mapper,
                schemaNode,
                mapper.valueToTree(body),
                key + " " + status + " response body");
    }

    /**
     * Critical transport headers are compared both ways. This catches an
     * implementation header that clients cannot discover as well as a
     * documented retry/replay/location promise that the controller omitted.
     */
    private void assertCriticalHeadersConform(
            JsonNode source,
            ObjectMapper mapper,
            OperationKey key,
            String status,
            ResponseEntity<?> response) {
        JsonNode headers = documentedResponse(source, key, status)
                .path("headers");
        Set<String> documented = new HashSet<>();
        if (headers.isObject()) {
            headers.fieldNames().forEachRemaining(name -> {
                if (CONTRACT_CRITICAL_HEADERS.contains(name)) {
                    documented.add(name);
                }
            });
        }
        Set<String> implemented = CONTRACT_CRITICAL_HEADERS.stream()
                .filter(name -> response.getHeaders().containsKey(name))
                .collect(Collectors.toSet());
        assertEquals(documented, implemented,
                () -> "关键响应头声明与实现不一致: " + key + " " + status);

        documented.forEach(name -> {
            String value = response.getHeaders().getFirst(name);
            assertNotNull(value,
                    () -> "关键响应头值缺失: " + key + " " + status
                            + " " + name);
            JsonNode header = resolveLocalReference(
                    source, headers.path(name));
            JsonNode headerSchema = header.path("schema");
            JsonNode instance = "integer".equals(
                    headerSchema.path("type").asText())
                    ? mapper.valueToTree(Long.parseLong(value))
                    : mapper.valueToTree(value);
            assertJsonConforms(
                    source,
                    mapper,
                    headerSchema,
                    instance,
                    key + " " + status + " header " + name);
        });
    }

    private void assertJsonConforms(
            JsonNode source,
            ObjectMapper mapper,
            JsonNode instanceSchema,
            JsonNode instance,
            Object label) {
        ObjectNode validationSchema = mapper.createObjectNode();
        validationSchema.put(
                "$schema", "https://json-schema.org/draft/2020-12/schema");
        validationSchema.set(
                "components", source.path("components").deepCopy());
        if (instanceSchema.isObject()) {
            validationSchema.setAll((ObjectNode) instanceSchema.deepCopy());
        } else {
            validationSchema.set("allOf", mapper.createArrayNode()
                    .add(instanceSchema.deepCopy()));
        }
        com.networknt.schema.Schema schema = SchemaRegistry
                .withDefaultDialect(SpecificationVersion.DRAFT_2020_12)
                .getSchema(validationSchema.toString(), InputFormat.JSON);
        List<com.networknt.schema.Error> errors = schema.validate(
                instance.toString(),
                InputFormat.JSON,
                context -> context.executionConfig(
                        config -> config.formatAssertionsEnabled(true)));
        assertTrue(errors.isEmpty(),
                () -> label + " does not conform to OpenAPI: " + errors
                        + System.lineSeparator() + instance.toPrettyString());
    }

    private JsonNode documentedResponse(
            JsonNode source,
            OperationKey key,
            String status) {
        JsonNode response = source.path("paths")
                .path(key.path())
                .path(key.method().name().toLowerCase())
                .path("responses")
                .path(status);
        assertTrue(!response.isMissingNode(),
                () -> "OpenAPI response 缺失: " + key + " " + status);
        return resolveLocalReference(source, response);
    }

    private JsonNode resolveLocalReference(
            JsonNode source,
            JsonNode node) {
        JsonNode current = node;
        Set<String> visited = new HashSet<>();
        while (current.isObject() && current.has("$ref")) {
            String reference = current.path("$ref").asText();
            assertTrue(reference.startsWith("#/"),
                    () -> "响应契约禁止远程引用: " + reference);
            assertTrue(visited.add(reference),
                    () -> "响应契约引用形成循环: " + reference);
            current = source.at(reference.substring(1));
            assertTrue(!current.isMissingNode(),
                    () -> "响应契约引用不存在: " + reference);
        }
        return current;
    }

    private JsonNode contractSource() throws IOException {
        return Yaml31.mapper().readTree(Files.readString(
                repositoryRoot().resolve(OPEN_API_PATH)));
    }

    private ObjectMapper responseMapper() {
        // Mirrors Spring Boot MVC's default temporal JSON representation.
        return Jackson2ObjectMapperBuilder.json()
                .featuresToDisable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
    }

    private String requestSchemaName(Operation operation) {
        assertNotNull(operation.getRequestBody(),
                "OpenAPI requestBody 缺失");
        var mediaType = operation.getRequestBody().getContent()
                .get("application/json");
        assertNotNull(mediaType,
                "OpenAPI application/json requestBody 缺失");
        assertNotNull(mediaType.getSchema(),
                "OpenAPI request schema 缺失");
        String reference = mediaType.getSchema().get$ref();
        assertNotNull(reference,
                "OpenAPI JSON request 必须引用命名 Schema");
        return reference.substring(reference.lastIndexOf('/') + 1);
    }

    private ObjectNode readObject(ObjectMapper mapper, String json) {
        try {
            JsonNode value = mapper.readTree(json);
            assertTrue(value instanceof ObjectNode,
                    "请求 fixture 必须是 JSON object");
            return (ObjectNode) value;
        } catch (IOException error) {
            throw new AssertionError("无法解析请求 fixture", error);
        }
    }

    private void assertAccepted(
            ObjectMapper mapper,
            Validator validator,
            RequestDtoContract contract,
            ObjectNode json,
            Object label) {
        try {
            Object value = mapper.treeToValue(json, contract.type());
            assertTrue(validator.validate(value).isEmpty(),
                    () -> "有效请求 DTO 被实现拒绝: " + label);
        } catch (Exception error) {
            throw new AssertionError(
                    "有效请求 DTO 无法反序列化: " + label, error);
        }
    }

    private void assertRejected(
            ObjectMapper mapper,
            Validator validator,
            RequestDtoContract contract,
            ObjectNode json,
            Object label) {
        boolean rejected;
        try {
            Object value = mapper.treeToValue(json, contract.type());
            rejected = !validator.validate(value).isEmpty();
        } catch (Exception expected) {
            rejected = true;
        }
        assertTrue(rejected,
                () -> "请求 DTO 未拒绝非法结构: " + label);
    }

    /**
     * Top-level checks protect every request root. These extra mutations lock
     * the nested launch discriminators and list filter shapes where hidden
     * coordinates or browser-supplied operators would be most dangerous.
     */
    private void assertNestedUnknownFieldsRejected(
            ObjectMapper mapper,
            Validator validator) {
        RequestDtoContract launch = REQUEST_DTO_CONTRACTS.get(
                new OperationKey(
                        "/api/open/v1/embed-launches", HttpMethod.POST));
        for (String child : List.of("subject", "entry", "ui")) {
            ObjectNode mutated = readObject(mapper, launch.validJson());
            ((ObjectNode) mutated.get(child)).putNull(
                    "forbiddenCoordinate");
            assertRejected(mapper, validator, launch, mutated,
                    "EmbedLaunchRequest." + child + " unknown field");
        }
        for (String pointer : List.of(
                "subject/type",
                "subject/assertion",
                "entry/mode")) {
            String[] segments = pointer.split("/");
            ObjectNode mutated = readObject(mapper, launch.validJson());
            ((ObjectNode) mutated.get(segments[0])).remove(segments[1]);
            assertRejected(mapper, validator, launch, mutated,
                    "EmbedLaunchRequest missing " + pointer);
        }

        RequestDtoContract list = REQUEST_DTO_CONTRACTS.get(
                new OperationKey(
                        "/api/embed/v1/runtime/list/query",
                        HttpMethod.POST));
        ObjectNode operator = readObject(mapper, """
                {
                  "filters":[{
                    "field":"status",
                    "value":"OPEN",
                    "operator":"EQ"
                  }]
                }
                """);
        assertRejected(mapper, validator, list, operator,
                "ListFilterInput browser operator");
        for (String field : List.of("field", "value")) {
            ObjectNode missing = operator.deepCopy();
            ((ObjectNode) missing.withArray("filters").get(0))
                    .remove(field);
            // Remove the injected operator so the required-field assertion is
            // independently responsible for the rejection.
            ((ObjectNode) missing.withArray("filters").get(0))
                    .remove("operator");
            assertRejected(mapper, validator, list, missing,
                    "ScalarFilterInput missing " + field);
        }

        ObjectNode range = readObject(mapper, """
                {
                  "filters":[{
                    "field":"createdAt",
                    "range":{
                      "start":"2026-08-01",
                      "end":"2026-08-31",
                      "timezone":"UTC"
                    }
                  }]
                }
                """);
        assertRejected(mapper, validator, list, range,
                "BetweenFilterInput unknown field");
        for (String field : List.of("start", "end")) {
            ObjectNode missing = range.deepCopy();
            ObjectNode rangeValue = (ObjectNode) missing
                    .withArray("filters").get(0).get("range");
            rangeValue.remove("timezone");
            rangeValue.remove(field);
            assertRejected(mapper, validator, list, missing,
                    "BetweenFilterInput missing " + field);
        }

    }

    /**
     * Locks the four intentionally different trust boundaries. In particular,
     * public handshake routes must declare an empty security array explicitly
     * and runtime routes may only accept the opaque Embed session token.
     */
    private void assertSecurity(
            OperationKey key,
            Operation operation,
            SecurityBoundary boundary) {
        List<SecurityRequirement> security = operation.getSecurity();
        assertNotNull(security,
                () -> "操作必须显式声明 security: " + key);
        if (boundary == SecurityBoundary.PUBLIC) {
            assertTrue(security.isEmpty(),
                    () -> "握手公开路由不得接受已有凭据: " + key);
            return;
        }

        assertEquals(1, security.size(),
                () -> "操作只能声明一个鉴权边界: " + key);
        SecurityRequirement requirement = security.get(0);
        switch (boundary) {
            case CLIENT_BASIC -> assertEquals(
                    Map.of("clientBasic", List.of()), requirement,
                    () -> "OAuth Token 必须只使用 Client Basic: " + key);
            case MACHINE_OAUTH -> assertEquals(
                    Map.of("machineOAuth", List.of()),
                    requirement,
                    () -> "Launch 只需有效的机器令牌，不应依赖 scope: " + key);
            case EMBED_BEARER -> assertEquals(
                    Map.of("embedBearer", List.of()), requirement,
                    () -> "运行态必须只使用 Embed Bearer: " + key);
            case PUBLIC -> throw new IllegalStateException(
                    "PUBLIC boundary was handled before this switch");
        }
    }

    private void assertSecuritySchemes(OpenAPI api) {
        assertNotNull(api.getComponents(), "components 缺失");
        Map<String, SecurityScheme> schemes =
                api.getComponents().getSecuritySchemes();
        assertNotNull(schemes, "securitySchemes 缺失");
        assertEquals(Set.of(
                "clientBasic", "machineOAuth", "embedBearer"),
                schemes.keySet());

        SecurityScheme clientBasic = schemes.get("clientBasic");
        assertEquals(SecurityScheme.Type.HTTP, clientBasic.getType());
        assertEquals("basic", clientBasic.getScheme());

        SecurityScheme machineOAuth = schemes.get("machineOAuth");
        assertEquals(SecurityScheme.Type.OAUTH2, machineOAuth.getType());
        assertNotNull(machineOAuth.getFlows());
        assertNotNull(machineOAuth.getFlows().getClientCredentials());
        assertEquals("/oauth2/token", machineOAuth.getFlows()
                .getClientCredentials().getTokenUrl());
        assertEquals(Set.of(), machineOAuth.getFlows()
                .getClientCredentials().getScopes().keySet());

        SecurityScheme embedBearer = schemes.get("embedBearer");
        assertEquals(SecurityScheme.Type.HTTP, embedBearer.getType());
        assertEquals("bearer", embedBearer.getScheme());
        assertEquals("opaque", embedBearer.getBearerFormat());
    }

    /**
     * Request and bridge messages are closed DTOs. This prevents a browser
     * from smuggling provider coordinates or future commands into V1 payloads.
     */
    private void assertClosedSchemas(OpenAPI api) throws IOException {
        Map<String, Schema> schemas = api.getComponents().getSchemas();
        assertNotNull(schemas, "schemas 缺失");
        JsonNode sourceSchemas = Yaml31.mapper()
                .readTree(Files.readString(
                        repositoryRoot().resolve(OPEN_API_PATH)))
                .path("components")
                .path("schemas");
        Set<String> closedSchemas = new HashSet<>(CLOSED_REQUEST_SCHEMAS);
        closedSchemas.addAll(CLOSED_MESSAGE_SCHEMAS);
        closedSchemas.forEach(name -> {
            Schema<?> schema = schemas.get(name);
            assertNotNull(schema, () -> "关键封闭 Schema 缺失: " + name);
            // Swagger Parser represents an OpenAPI 3.1 boolean schema as an
            // empty Schema object. Inspect the already parsed YAML tree to
            // distinguish the literal false value from an open object schema.
            JsonNode additionalProperties = sourceSchemas.path(name)
                    .path("additionalProperties");
            assertTrue(additionalProperties.isBoolean()
                            && !additionalProperties.booleanValue(),
                    () -> name
                            + " 必须声明 additionalProperties: false");
        });
    }

    @SuppressWarnings("unchecked")
    private void assertInitializedCapabilities(OpenAPI api) {
        Map<String, Schema> schemas = api.getComponents().getSchemas();
        Schema<?> initialized = schemas.get("InitializedEventPayload");
        assertNotNull(initialized, "InitializedEventPayload 缺失");
        assertNotNull(initialized.getRequired(),
                "InitializedEventPayload.required 缺失");
        assertTrue(initialized.getRequired().contains("capabilities"),
                "InitializedEventPayload.capabilities 必须必填");
        Schema<?> capabilities = (Schema<?>) initialized.getProperties()
                .get("capabilities");
        assertNotNull(capabilities,
                "InitializedEventPayload.capabilities 缺失");
        assertNotNull(capabilities.getItems(),
                "Initialized capabilities.items 缺失");
        assertEquals("#/components/schemas/V1Capability",
                capabilities.getItems().get$ref());

        Schema<?> capability = schemas.get("V1Capability");
        assertNotNull(capability, "V1Capability 缺失");
        assertNotNull(capability.getEnum(), "V1Capability.enum 缺失");
        assertEquals(5, capability.getEnum().size());
        assertEquals(V1_CAPABILITIES, new HashSet<>(capability.getEnum()));

        Object boundaryValue = api.getExtensions()
                .get("x-flow-v1-boundary");
        assertTrue(boundaryValue instanceof Map,
                "x-flow-v1-boundary 缺失或格式错误");
        Map<String, Object> boundary = (Map<String, Object>) boundaryValue;
        Object boundaryCapabilities = boundary.get("capabilities");
        assertTrue(boundaryCapabilities instanceof List,
                "x-flow-v1-boundary.capabilities 缺失或格式错误");
        List<String> values = (List<String>) boundaryCapabilities;
        assertEquals(5, values.size());
        assertEquals(V1_CAPABILITIES, new HashSet<>(values));
    }

    /**
     * Locks the architecture boundary: Embed publishes only a fixed native
     * target and an ID-only creation receipt, never a second form/component
     * projection contract.
     */
    @SuppressWarnings("unchecked")
    private void assertNativeFormRuntimeContract(OpenAPI api) {
        Map<String, Schema> schemas = api.getComponents().getSchemas();
        Schema<?> target = schemas.get("NativeFormTarget");
        assertNotNull(target, "NativeFormTarget 缺失");
        assertEquals(Set.of(
                        "entityCode", "formId", "formReleaseId",
                        "formReleaseVersion", "listKey", "listReleaseId",
                        "listReleaseVersion", "listReleaseResolutionToken",
                        "entryMode", "recordId",
                        "processInstanceId", "formReleaseResolutionToken",
                        "defaultFormResolved", "initialData", "parameters", "context"),
                target.getProperties().keySet(),
                "原生目标只能公开固定坐标和上下文，不能投影表单组件");
        for (String forbidden : List.of(
                "fields", "components", "layout", "options", "actions")) {
            assertFalse(target.getProperties().containsKey(forbidden),
                    () -> "NativeFormTarget 禁止包含 " + forbidden);
        }

        Schema<?> createdRecord = schemas.get("CreatedRecord");
        assertNotNull(createdRecord, "CreatedRecord 缺失");
        assertEquals(Set.of("id", "recordVersion"),
                createdRecord.getProperties().keySet(),
                "RECORD_CREATE 响应必须保持 ID-only");
        Schema<?> createResult = schemas.get("CreateResult");
        Schema<?> record = (Schema<?>) createResult.getProperties()
                .get("record");
        assertEquals("#/components/schemas/CreatedRecord", record.get$ref());

        for (String retiredSchema : Set.of(
                "FormResult", "FormView", "FormField",
                "FormControlType", "PublishedFormRuntimeProfile",
                "PublishedFormRuntimeNode", "PublishedFormComponentProps",
                "CreateFormEvaluationRequest", "OptionQueryRequest",
                "LookupQueryRequest", "RecordResult", "RecordView")) {
            assertFalse(schemas.containsKey(retiredSchema),
                    () -> "旧 Embed FORM 投影 Schema 不得重新公开: "
                            + retiredSchema);
        }
    }

    private boolean containsRetiredProjectionOperations(String contract) {
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(
                contract, null, options);
        return result != null
                && result.getOpenAPI() != null
                && openApiOperations(result.getOpenAPI()).keySet()
                .containsAll(RETIRED_FORM_PROJECTION_OPERATIONS);
    }

    private boolean containsRetiredMachineScope(String contract) {
        try {
            JsonNode document = Yaml31.mapper().readTree(contract);
            if (document.path("components")
                    .path("securitySchemes")
                    .path("machineOAuth")
                    .path("flows")
                    .path("clientCredentials")
                    .path("scopes")
                    .has("embed.launch")) {
                return true;
            }
        } catch (IOException ignored) {
            // 继续使用 OpenAPI Parser，让既有容错路径决定是否存在旧 Scope。
        }
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIV3Parser().readContents(
                contract, null, options);
        if (result == null || result.getOpenAPI() == null
                || result.getOpenAPI().getComponents() == null
                || result.getOpenAPI().getComponents()
                .getSecuritySchemes() == null) {
            return false;
        }
        SecurityScheme machineOAuth = result.getOpenAPI().getComponents()
                .getSecuritySchemes().get("machineOAuth");
        return machineOAuth != null
                && machineOAuth.getFlows() != null
                && machineOAuth.getFlows().getClientCredentials() != null
                && machineOAuth.getFlows().getClientCredentials()
                .getScopes() != null
                && machineOAuth.getFlows().getClientCredentials()
                .getScopes().containsKey("embed.launch");
    }

    private OpenAPI parseCurrentContract() throws IOException {
        return parseContract(repositoryRoot().resolve(OPEN_API_PATH));
    }

    /** Parses with reference resolution enabled and treats every parser
     * message as a contract failure, including non-fatal warnings. */
    private OpenAPI parseContract(Path contract) throws IOException {
        assertTrue(Files.isRegularFile(contract),
                () -> "Embed OpenAPI 文件不存在: " + contract);
        ParseOptions options = new ParseOptions();
        options.setResolve(true);
        SwaggerParseResult result = new OpenAPIV3Parser()
                .readLocation(contract.toString(), null, options);
        assertNotNull(result.getOpenAPI(), () -> String.join(
                System.lineSeparator(), result.getMessages()));
        assertTrue(result.getMessages() == null
                        || result.getMessages().isEmpty(),
                () -> String.join(
                        System.lineSeparator(), result.getMessages()));
        return result.getOpenAPI();
    }

    private String readContractFromGit(
            Path root,
            String baseRef) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                GIT_EXECUTABLE,
                "show",
                baseRef + ":" + OPEN_API_PATH)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            return null;
        }
        return new String(output, StandardCharsets.UTF_8);
    }

    private Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isRegularFile(candidate.resolve(OPEN_API_PATH))
                    && Files.isRegularFile(candidate.resolve(
                            "workflow-server/pom.xml"))) {
                return candidate;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException(
                "Unable to locate repository root from "
                        + Path.of("").toAbsolutePath());
    }

    private static Map.Entry<OperationKey, OperationContract> operation(
            String path,
            HttpMethod method,
            String operationId,
            SecurityBoundary security,
            Set<String> successCodes) {
        return Map.entry(
                new OperationKey(path, method),
                new OperationContract(
                        operationId, security, successCodes));
    }

    private static Map.Entry<OperationKey, RequestDtoContract> requestDto(
            String path,
            String schemaName,
            Class<?> type,
            String validJson) {
        return Map.entry(
                new OperationKey(path, HttpMethod.POST),
                new RequestDtoContract(schemaName, type, validJson));
    }

    private enum SecurityBoundary {
        CLIENT_BASIC,
        MACHINE_OAUTH,
        PUBLIC,
        EMBED_BEARER
    }

    private record OperationKey(String path, HttpMethod method) {
    }

    private record OperationContract(
            String operationId,
            SecurityBoundary security,
            Set<String> successCodes) {
    }

    private record RequestDtoContract(
            String schemaName,
            Class<?> type,
            String validJson) {
    }

    private record SuccessProof(
            Set<String> statusCodes,
            ResponseEntity<?> response) {
    }

    private record ErrorResponseFixture(
            OperationKey key,
            String status,
            ResponseEntity<?> response) {
    }

    private record HandlerBinding(
            Method controllerMethod,
            String source) {
    }

    private record TokenEndpointBinding(
            AuthorizationServerSettings settings,
            Method factoryMethod) {
    }

    private static final class MappingInspector
            extends RequestMappingHandlerMapping {

        private RequestMappingInfo inspect(
                Method method,
                Class<?> handlerType) {
            return super.getMappingForMethod(method, handlerType);
        }
    }
}
