package com.workflow.architecture;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards Maven module ownership and source package layout.
 */
class ModulePackageLayoutTest {

    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("(?m)^package\\s+([a-zA-Z0-9_.]+);");

    @Test
    void parentPomUsesFunctionalModulesOnly() throws Exception {
        String pom = Files.readString(Path.of("../pom.xml"));

        for (String module : List.of(
                "workflow-core",
                "workflow-contracts",
                "workflow-admin",
                "workflow-storage",
                "workflow-entity",
                "workflow-process",
                "workflow-integration",
                "workflow-migration",
                "workflow-devtools",
                "workflow-app")) {
            assertTrue(pom.contains("<module>" + module + "</module>"),
                    "missing module: " + module);
        }

        for (String retired : List.of(
                "workflow-common",
                "workflow-system",
                "workflow-action",
                "workflow-extension",
                "workflow-administration",
                "workflow-kernel",
                "workflow-ui-config")) {
            assertFalse(pom.contains("<module>" + retired + "</module>"),
                    "retired module remains: " + retired);
        }
    }

    @Test
    void productionSourcesStayInsideModuleOwnedPackages() throws Exception {
        for (ModuleLayout layout : List.of(
                new ModuleLayout("workflow-core", "com.workflow.core"),
                new ModuleLayout(
                        "workflow-contracts",
                        "com.workflow.contracts"),
                new ModuleLayout("workflow-admin", "com.workflow.admin"),
                new ModuleLayout("workflow-storage", "com.workflow.storage"),
                new ModuleLayout("workflow-entity", "com.workflow.entity"),
                new ModuleLayout("workflow-process", "com.workflow.process"),
                new ModuleLayout(
                        "workflow-migration",
                        "com.workflow.migration"),
                new ModuleLayout(
                        "workflow-devtools",
                        "com.workflow.devtools"),
                new ModuleLayout(
                        "workflow-integration/workflow-outbox",
                        "com.workflow.outbox"),
                new ModuleLayout(
                        "workflow-integration/workflow-http",
                        "com.workflow.http"),
                new ModuleLayout(
                        "workflow-integration/workflow-notification",
                        "com.workflow.notification"))) {
            assertLayout(layout);
        }
    }

    private void assertLayout(ModuleLayout layout) throws Exception {
        Path sourceRoot = Path.of(
                "..", layout.module(), "src/main/java").normalize();
        assertTrue(Files.isDirectory(sourceRoot),
                "missing source root: " + sourceRoot);

        try (var files = Files.walk(sourceRoot)) {
            for (Path file : files
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                String source = Files.readString(file);
                var matcher = PACKAGE_DECLARATION.matcher(source);
                assertTrue(matcher.find(), "missing package: " + file);

                String packageName = matcher.group(1);
                assertTrue(
                        packageName.equals(layout.packageRoot())
                                || packageName.startsWith(
                                layout.packageRoot() + "."),
                        () -> file + " belongs to " + packageName
                                + ", expected " + layout.packageRoot());

                String expected = packageName.replace('.', '/')
                        + "/" + file.getFileName();
                String actual = sourceRoot.relativize(file)
                        .toString()
                        .replace('\\', '/');
                assertEquals(expected, actual,
                        "package/path mismatch: " + file);
            }
        }
    }

    private record ModuleLayout(
            String module,
            String packageRoot) {
    }
}
