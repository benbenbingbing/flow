package com.workflow.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** 数据库模块是纯方言库：标准与实现同模块，执行和装配由调用方负责。 */
@AnalyzeClasses(packages = "com.workflow", importOptions = ImportOption.DoNotIncludeTests.class)
class DatabaseAdapterBoundaryTest {
    @ArchTest
    static final ArchRule DATABASE_HAS_NO_BUSINESS_OR_EXECUTION_DEPENDENCIES = noClasses()
            .that().resideInAPackage("com.workflow.integration.database..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.workflow.entity..", "com.workflow.process..", "com.workflow.admin..",
                    "com.workflow.contracts..", "com.workflow.core..", "com.workflow.config..",
                    "com.workflow.bootstrap..", "com.workflow.adapter..", "com.workflow.web..",
                    "com.workflow.observability..",
                    "com.workflow.migration..", "java.sql..", "javax.sql..",
                    "org.springframework..", "org.apache.ibatis..", "com.baomidou..");

    @ArchTest
    static final ArchRule CALLERS_USE_PUBLIC_DIALECT_STANDARD = noClasses()
            .that().resideOutsideOfPackage("com.workflow.integration.database..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.workflow.integration.database.dialect..", "com.workflow.integration.database.query..");

    @ArchTest
    static final ArchRule DATABASE_STANDARD_IS_NOT_IN_CONTRACTS = noClasses()
            .should().resideInAPackage("com.workflow.contracts.database..");
}
