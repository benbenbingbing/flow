package com.workflow.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.networknt.schema.InputFormat;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.parser.OpenAPIV3Parser;
import io.swagger.v3.parser.core.models.ParseOptions;
import io.swagger.v3.parser.core.models.SwaggerParseResult;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.openapitools.openapidiff.core.OpenApiCompare;
import org.openapitools.openapidiff.core.model.ChangedOpenApi;

class OpenIntegrationContractTest {

    private static final String OPEN_API_PATH =
            "docs/api/openapi-v1.yaml";
    private static final String EVENT_SCHEMA_PATH =
            "docs/api/events";
    private static final String GIT_EXECUTABLE = "/usr/bin/git";

    @Test
    void openApiContractIsValidAndContainsTheV1Boundary()
            throws IOException {
        Path contract = repositoryRoot().resolve(OPEN_API_PATH);
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

        OpenAPI api = result.getOpenAPI();
        assertEquals("3.1.0", api.getOpenapi());
        assertEquals(Set.of(
                        "/oauth2/token",
                        "/api/open/v1/process-definitions",
                        "/api/open/v1/process-instances",
                        "/api/open/v1/process-instances/{processInstanceId}",
                        "/api/open/v1/process-instances/{processInstanceId}/tasks",
                        "/api/open/v1/process-instances/{processInstanceId}"
                                + "/messages/{messageKey}"),
                api.getPaths().keySet());
        assertNotNull(api.getComponents()
                .getSecuritySchemes().get("clientCredentials"));
    }

    @Test
    void eventExamplesConformToTheirDraft202012Schemas()
            throws IOException {
        Path eventDirectory = repositoryRoot().resolve(EVENT_SCHEMA_PATH);
        List<Path> schemas;
        try (Stream<Path> values = Files.list(eventDirectory)) {
            schemas = values
                    .filter(path -> path.getFileName().toString()
                            .endsWith(".schema.json"))
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        }
        assertEquals(6, schemas.size());

        SchemaRegistry registry = SchemaRegistry.withDefaultDialect(
                SpecificationVersion.DRAFT_2020_12);
        for (Path schemaPath : schemas) {
            String fileName = schemaPath.getFileName().toString();
            Path examplePath = eventDirectory.resolve("examples")
                    .resolve(fileName.replace(".schema.json", ".json"));
            assertTrue(Files.isRegularFile(examplePath),
                    () -> "Missing event example: " + examplePath);

            Schema schema = registry.getSchema(
                    Files.readString(schemaPath),
                    InputFormat.JSON);
            List<com.networknt.schema.Error> errors = schema.validate(
                    Files.readString(examplePath),
                    InputFormat.JSON,
                    context -> context.executionConfig(
                            config -> config.formatAssertionsEnabled(true)));
            assertTrue(errors.isEmpty(),
                    () -> examplePath + " failed validation: " + errors);
        }
    }

    @Test
    void compatibilityEngineRejectsADeletedOperation() {
        String baseline = """
                openapi: 3.0.3
                info:
                  title: Compatibility fixture
                  version: 1.0.0
                paths:
                  /stable:
                    get:
                      responses:
                        '200':
                          description: ok
                """;
        String breaking = """
                openapi: 3.0.3
                info:
                  title: Compatibility fixture
                  version: 1.0.0
                paths: {}
                """;

        ChangedOpenApi difference =
                OpenApiCompare.fromContents(baseline, breaking);
        assertFalse(difference.isCompatible());
    }

    @Test
    void pullRequestContractIsBackwardCompatible()
            throws IOException, InterruptedException {
        String baseRef = System.getenv("OPENAPI_BASE_REF");
        if (baseRef == null || baseRef.isBlank()) {
            return;
        }

        Path root = repositoryRoot();
        String baseline = readContractFromGit(root, baseRef);
        if (baseline == null) {
            return;
        }
        String current = Files.readString(
                root.resolve(OPEN_API_PATH));

        ChangedOpenApi difference =
                OpenApiCompare.fromContents(baseline, current);
        assertTrue(difference.isCompatible(),
                () -> "OpenAPI v1 contains a breaking change: "
                        + difference);
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
}
