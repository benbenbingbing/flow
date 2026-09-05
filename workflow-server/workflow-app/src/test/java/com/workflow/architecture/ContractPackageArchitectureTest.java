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
 * 接口内声明与该契约强绑定的值对象和异常，防止独立实现或模型混入共享契约边界。</p>
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
    static final ArchRule CONTRACT_TYPES_DO_NOT_USE_DEPRECATED_BRIDGES =
            noClasses()
                    .that().resideInAPackage("com.workflow.contracts..")
                    .should().beAnnotatedWith(Deprecated.class);
}
