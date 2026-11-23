package com.workflow.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 保护共享契约的角色分包。
 *
 * <p>平台 Port 必须是领域根包的顶层接口。SPI 保留自己的命名空间和扩展点包。
 * 配套模型按功能分包，强绑定的值对象和异常可以嵌套在接口内。</p>
 */
@AnalyzeClasses(
        packages = "com.workflow",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ContractPackageArchitectureTest {

    @ArchTest
    static final ArchRule PLATFORM_PORTS_DECLARE_FLAT_MODULE_PACKAGES =
            classes()
                    .that().resideInAnyPackage("com.workflow.port..", "com.workflow.contracts..")
                    .and().areTopLevelClasses()
                    .and().haveSimpleNameEndingWith("Port")
                    .should().resideInAPackage("com.workflow.port.*");

    @ArchTest
    static final ArchRule DOMAIN_ROOT_PACKAGES_CONTAIN_PORT_INTERFACES_ONLY =
            classes()
                    .that().resideInAPackage("com.workflow.port.*")
                    .and().areTopLevelClasses()
                    .and().doNotHaveSimpleName("package-info")
                    .should().beInterfaces()
                    .andShould().haveSimpleNameEndingWith("Port");

    @ArchTest
    static final ArchRule PORT_NAMES_INDICATE_THE_BUSINESS_OBJECT =
            noClasses()
                    .that().resideInAPackage("com.workflow.port.*")
                    .and().areTopLevelClasses()
                    .should().haveNameMatching(".*(Read|Write|Query|Command)Port");

    @ArchTest
    static final ArchRule SPI_PACKAGES_CONTAIN_INTERFACES_ONLY =
            classes()
                    .that().resideInAPackage("com.workflow.contracts..spi")
                    .and().areTopLevelClasses()
                    .should().beInterfaces();

    @ArchTest
    static final ArchRule SPI_NAMES_DECLARE_THEIR_ROLE =
            classes()
                    .that().resideInAPackage("com.workflow.contracts..spi")
                    .and().areTopLevelClasses()
                    .and().doNotHaveSimpleName("package-info")
                    .should().haveSimpleNameEndingWith("Provider");

    @ArchTest
    static final ArchRule MODELS_DO_NOT_DEPEND_ON_PORTS_OR_SPI =
            noClasses()
                    .that().resideInAnyPackage("com.workflow.port..model..", "com.workflow.contracts..model..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.workflow.port.*",
                            "com.workflow.contracts..spi..");

    // 这些旧入口会把同一能力的模型与接口拆散，新增契约必须回到所属业务能力下。
    @ArchTest
    static final ArchRule CONTRACTS_DO_NOT_REINTRODUCE_RETIRED_PACKAGES =
            noClasses()
                    .should().resideInAnyPackage(
                            "com.workflow.contracts.action..",
                            "com.workflow.contracts.ui..",
                            "com.workflow.contracts.identity.resolver..",
                            "com.workflow.contracts..port..",
                            "com.workflow.port.*.port..");

    @ArchTest
    static final ArchRule CONTRACT_TYPES_DO_NOT_USE_DEPRECATED_BRIDGES =
            noClasses()
                    .that().resideInAnyPackage("com.workflow.port..", "com.workflow.contracts..")
                    .should().beAnnotatedWith(Deprecated.class);
}
