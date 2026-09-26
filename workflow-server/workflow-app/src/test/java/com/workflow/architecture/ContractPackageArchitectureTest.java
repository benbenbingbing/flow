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
 * <p>本测试约束 canonical {@code port}/{@code spi} 包：顶层类型必须是接口，但允许
 * 接口内声明与该契约强绑定的值对象和异常。配套模型按业务能力归位后，不得再依赖
 * 调用端口或扩展接口，避免只移动目录却保留反向依赖。</p>
 */
@AnalyzeClasses(
        packages = "com.workflow",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ContractPackageArchitectureTest {

    @ArchTest
    static final ArchRule PORT_PACKAGES_CONTAIN_INTERFACES_ONLY =
            classes()
                    .that().resideInAPackage("com.workflow.contracts..port")
                    .and().areTopLevelClasses()
                    .should().beInterfaces();

    @ArchTest
    static final ArchRule SPI_PACKAGES_CONTAIN_INTERFACES_ONLY =
            classes()
                    .that().resideInAPackage("com.workflow.contracts..spi")
                    .and().areTopLevelClasses()
                    .should().beInterfaces();

    @ArchTest
    static final ArchRule PORT_DOES_NOT_DEPEND_ON_SPI =
            noClasses()
                    .that().resideInAPackage("com.workflow.contracts..port")
                    .should().dependOnClassesThat()
                    .resideInAPackage("com.workflow.contracts..spi");

    @ArchTest
    static final ArchRule PORT_NAMES_DECLARE_THEIR_ROLE =
            classes()
                    .that().resideInAPackage("com.workflow.contracts..port")
                    .and().areTopLevelClasses()
                    // javac 生成的包元数据不属于可调用的业务端口。
                    .and().doNotHaveSimpleName("package-info")
                    .should().haveSimpleNameEndingWith("Port");

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
                    .that().resideInAPackage("com.workflow.contracts..model..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage(
                            "com.workflow.contracts..port..",
                            "com.workflow.contracts..spi..");

    // 这些旧入口会把同一能力的模型与接口拆散，新增契约必须回到所属业务能力下。
    @ArchTest
    static final ArchRule CONTRACTS_DO_NOT_REINTRODUCE_RETIRED_PACKAGES =
            noClasses()
                    .should().resideInAnyPackage(
                            "com.workflow.contracts.action..",
                            "com.workflow.contracts.ui..",
                            "com.workflow.contracts.identity.resolver..");

    @ArchTest
    static final ArchRule CONTRACT_TYPES_DO_NOT_USE_DEPRECATED_BRIDGES =
            noClasses()
                    .that().resideInAPackage("com.workflow.contracts..")
                    .should().beAnnotatedWith(Deprecated.class);
}
