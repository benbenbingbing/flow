package com.workflow.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 以实际字节码依赖防止新增跨模块持久化访问。基线只登记尚未迁移的精确类型边，
 * 不能按整个包豁免；删除旧依赖时必须同步缩减基线，新增边必须先完成领域边界设计。
 */
@AnalyzeClasses(packages = "com.workflow", importOptions = ImportOption.DoNotIncludeTests.class)
class CrossModulePersistenceBoundaryTest {
    @ArchTest
    static void crossModulePersistenceAccessCannotGrow(JavaClasses classes) throws Exception {
        Set<String> actual = new TreeSet<>();
        for (var source : classes) {
            for (var dependency : source.getDirectDependenciesFromSelf()) {
                String target = dependency.getTargetClass().getName();
                if (target.startsWith("com.workflow.") && target.contains(".infrastructure.persistence.")
                        && !owner(source.getName()).equals(owner(target))) {
                    actual.add(source.getName() + " -> " + target);
                }
            }
        }
        Set<String> baseline = Files.readAllLines(Path.of(
                        "src/test/resources/architecture/cross-module-persistence-baseline.txt"))
                .stream().map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> added = new TreeSet<>(actual);
        added.removeAll(baseline);
        assertTrue(added.isEmpty(), "新增跨模块持久化依赖，改用所属领域的公开边界：\n" + String.join("\n", added));
        Set<String> removed = new TreeSet<>(baseline);
        removed.removeAll(actual);
        assertTrue(removed.isEmpty(), "已解除的依赖必须从基线删除：\n" + String.join("\n", removed));
    }

    /** 源码根包与 Maven 模块一一对应；app 的装配子包统一归为 app。 */
    private static String owner(String name) {
        String prefix = name.substring("com.workflow.".length()).split("\\.")[0];
        return Set.of("config", "bootstrap", "adapter", "web", "observability", "WorkflowApplication")
                .contains(prefix) ? "app" : prefix;
    }
}
