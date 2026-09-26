package com.workflow.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** HTTP 库供流程与嵌入运行时共同使用，引擎适配只能由调用方承担。 */
@AnalyzeClasses(packages = "com.workflow.http", importOptions = ImportOption.DoNotIncludeTests.class)
class HttpTransportBoundaryTest {
    @ArchTest
    static final ArchRule HTTP_HAS_NO_ENGINE_OR_BUSINESS_DEPENDENCIES = noClasses()
            .that().resideInAPackage("com.workflow.http..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.flowable..", "com.workflow.process..", "com.workflow.entity..",
                    "com.workflow.embed..", "com.workflow.admin..");
}
