package com.workflow.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 核对两个契约制品的源码归属、命名空间以及 Port 索引。
 * 依赖检查涵盖嵌套类型、模型和上下文，防止平台契约间接依赖扩展契约。
 */
class ContractModuleBoundaryTest {
    private static final String PORT_PACKAGE = "com.workflow.port";
    private static final String SPI_PACKAGE = "com.workflow.contracts";

    @Test
    void contractArtifactsHaveSingleOwnershipAndOneWayDependencies() throws Exception {
        Set<String> portTypes = types("workflow-port");
        Set<String> spiTypes = types("workflow-spi");
        Set<String> duplicates = new HashSet<>(portTypes);
        duplicates.retainAll(spiTypes);
        assertTrue(duplicates.isEmpty(), "duplicate contract types: " + duplicates);
        assertFalse(portTypes.isEmpty());
        assertFalse(spiTypes.isEmpty());

        noClasses().that().resideInAPackage(PORT_PACKAGE + "..")
                .should().dependOnClassesThat().resideInAPackage(SPI_PACKAGE + "..")
                .check(new ClassFileImporter().importPackages(PORT_PACKAGE, SPI_PACKAGE));

        String portPom = Files.readString(Path.of("../workflow-port/pom.xml"));
        String spiPom = Files.readString(Path.of("../workflow-spi/pom.xml"));
        assertFalse(portPom.contains("<artifactId>workflow-spi</artifactId>"));
        assertTrue(spiPom.contains("<artifactId>workflow-port</artifactId>"));
    }

    @Test
    void callableInterfacesLiveInTheirOwningArtifact() throws Exception {
        for (String type : types("workflow-port")) {
            assertTrue(type.startsWith(PORT_PACKAGE + "."), "Unexpected platform contract namespace: " + type);
            assertFalse(type.contains(".spi."), "SPI leaked into platform artifact: " + type);
        }
        for (String type : types("workflow-spi")) {
            assertTrue(type.startsWith(SPI_PACKAGE + "."), "Unexpected SPI namespace: " + type);
            assertFalse(type.contains(".port.") || type.endsWith("Port"), "Port leaked into extension artifact: " + type);
        }
        assertTrue(types("workflow-spi").containsAll(Set.of(
                "com.workflow.contracts.entity.list.spi.ListFieldDataProvider",
                "com.workflow.contracts.entity.permission.spi.EntityPermissionOptionProvider",
                "com.workflow.contracts.storage.spi.FileStorageProvider",
                "com.workflow.contracts.outbox.spi.OutboxEventHandlerProvider")));

        // The Java identities of these protocol constants are also referenced by released UI provider classes.
        assertTrue(types("workflow-spi").containsAll(Set.of(
                "com.workflow.contracts.entity.list.model.EntityListQueryFields",
                "com.workflow.contracts.entity.ui.model.UiDataSourceUsages")));
    }

    @Test
    void platformPortsAreFlatAndEveryPortIsIndexedOnce() throws Exception {
        String prefix = PORT_PACKAGE + ".";
        Pattern directDomainPort = Pattern.compile(Pattern.quote(prefix) + "\\w+\\.\\w+Port");
        Set<String> expected = new HashSet<>();
        Set<String> groups = new HashSet<>();
        for (String type : types("workflow-port")) {
            assertTrue(type.startsWith(prefix), "Unexpected contract namespace: " + type);
            groups.add(type.substring(prefix.length()).split("\\.", 2)[0]);
            if (type.endsWith("Port")) {
                assertTrue(directDomainPort.matcher(type).matches(),
                        "Port must be declared directly in its capability package: " + type);
                expected.add("src/main/java/" + type.replace('.', '/') + ".java");
            }
        }
        assertFalse(expected.isEmpty());
        assertEquals(Set.of("system", "entity", "process", "migration", "embed", "platform"), groups,
                "Keep every Port and its supporting types under the documented capability groups");

        // Use the linked source path as the identity so stale links, missing rows and duplicates fail together.
        String readme = Files.readString(Path.of("../workflow-port/README.md"));
        var rows = Pattern.compile(
                "(?m)^\\| \\[`(\\w+Port)`\\]\\((src/main/java/[^)]+)\\)"
                        + " \\| [^|]+ \\| [^|]+ \\| [^|]+ \\|$")
                .matcher(readme);
        Set<String> indexed = new HashSet<>();
        while (rows.find()) {
            String link = rows.group(2);
            assertEquals(rows.group(1) + ".java", Path.of(link).getFileName().toString());
            assertTrue(indexed.add(link), "duplicate Port index entry: " + link);
        }
        assertEquals(expected, indexed, "Port index must match the production sources");
    }

    private Set<String> types(String module) throws Exception {
        Path source = Path.of("..", module, "src/main/java");
        try (var paths = Files.walk(source)) {
            Set<String> result = new HashSet<>();
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .forEach(path -> result.add(source.relativize(path).toString()
                            .replace('/', '.').replace('\\', '.')
                            .replaceFirst("\\.java$", "")));
            return result;
        }
    }

}
