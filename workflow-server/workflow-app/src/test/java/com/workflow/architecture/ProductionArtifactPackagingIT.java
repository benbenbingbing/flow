package com.workflow.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Test;

class ProductionArtifactPackagingIT {

    @Test
    void openIntegrationRuntimeDependenciesArePackaged()
            throws Exception {
        Path artifact = Path.of(
                "target/workflow-server-1.0.0.jar");

        try (ZipFile archive = new ZipFile(artifact.toFile())) {
            assertTrue(
                    archive.stream().anyMatch(entry ->
                            entry.getName().startsWith(
                                    "BOOT-INF/lib/json-schema-validator-")),
                    "JSON Schema validator is missing from production JAR");
        }
    }
}
