package com.workflow.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** 保护扩展目录与接口运行链之间的单向依赖，避免 Spring Bean 循环。 */
@AnalyzeClasses(
        packages = "com.workflow",
        importOptions = ImportOption.DoNotIncludeTests.class)
class UiExtensionDependencyBoundaryTest {

    /**
     * 发布服务依赖扩展目录做快照校验，而接口运行链又依赖发布服务。
     * 目录服务不得反向依赖接口运行服务，否则两条发布回路都会形成闭环。
     */
    @ArchTest
    static final ArchRule EXTENSION_CATALOG_DOES_NOT_DEPEND_ON_INTERFACE_RUNTIME =
            noClasses()
                    .that().haveSimpleName("UiExtensionDefinitionService")
                    .should().dependOnClassesThat()
                    .haveSimpleName("UiInterfaceExtensionService");
}
