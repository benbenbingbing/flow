package com.workflow.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 两个制品保留原 Java 包名，因此按源码归属校验依赖，不能仅用包名区分 Maven 模块。
 * 连同嵌套类型检查，防止 Port 通过模型、上下文或回调间接依赖 SPI。
 */
class ContractModuleBoundaryTest {
    @Test
    void contractArtifactsHaveSingleOwnershipAndOneWayDependencies() throws Exception {
        Set<String> portTypes = types("workflow-port");
        Set<String> spiTypes = types("workflow-spi");
        Set<String> duplicates = new HashSet<>(portTypes);
        duplicates.retainAll(spiTypes);
        assertTrue(duplicates.isEmpty(), "duplicate contract types: " + duplicates);
        assertFalse(portTypes.isEmpty());
        assertFalse(spiTypes.isEmpty());

        noClasses().that(ownedBy("workflow-port", portTypes))
                .should().dependOnClassesThat(ownedBy("workflow-spi", spiTypes))
                .check(new ClassFileImporter().importPackages("com.workflow.contracts"));

        // 包括新迁入的 outbox/storage，防止契约通过模型重新引用任何宿主实现。
        noClasses().that().resideInAPackage("com.workflow.contracts..")
                .should().dependOnClassesThat(new DescribedPredicate<JavaClass>("workflow implementation") {
                    @Override
                    public boolean test(JavaClass type) {
                        return type.getName().startsWith("com.workflow.")
                                && !type.getName().startsWith("com.workflow.contracts.");
                    }
                }).check(new ClassFileImporter().importPackages("com.workflow.contracts"));

        String portPom = Files.readString(Path.of("../workflow-port/pom.xml"));
        String spiPom = Files.readString(Path.of("../workflow-spi/pom.xml"));
        assertFalse(portPom.contains("<artifactId>workflow-spi</artifactId>"));
        assertTrue(spiPom.contains("<artifactId>workflow-port</artifactId>"));
    }

    @Test
    void callableInterfacesLiveInTheirOwningArtifact() throws Exception {
        for (String type : types("workflow-port")) {
            assertFalse(type.contains(".spi."), "SPI leaked into platform artifact: " + type);
        }
        for (String type : types("workflow-spi")) {
            assertFalse(type.contains(".port."), "Port leaked into extension artifact: " + type);
        }
        assertTrue(types("workflow-spi").containsAll(Set.of(
                "com.workflow.contracts.entity.list.spi.ListFieldDataProvider",
                "com.workflow.contracts.entity.permission.spi.EntityPermissionOptionProvider",
                "com.workflow.contracts.storage.spi.FileStorageProvider",
                "com.workflow.contracts.outbox.spi.OutboxEventHandlerProvider")));
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

    private DescribedPredicate<JavaClass> ownedBy(String module, Set<String> types) {
        return new DescribedPredicate<>("owned by " + module) {
            @Override
            public boolean test(JavaClass type) {
                return types.contains(type.getName().split("\\$", 2)[0]);
            }
        };
    }
}
