package com.workflow.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 保护新增端口边界，防止业务模块绕过契约直接依赖审计实现。
 */
@AnalyzeClasses(packages = "com.workflow")
class ArchitectureBoundaryTest {

    @ArchTest
    static final ArchRule CORE_CONTRACTS_ARE_FRAMEWORK_FREE =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.workflow.contracts.audit..",
                            "com.workflow.contracts.entity..",
                            "com.workflow.contracts.identity..",
                            "com.workflow.contracts.migration..",
                            "com.workflow.contracts.process..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "com.baomidou..",
                            "com.fasterxml..",
                            "jakarta.servlet..");

    @ArchTest
    static final ArchRule BUSINESS_MODULES_USE_AUDIT_CONTRACTS_ONLY =
            noClasses()
                    .that().resideOutsideOfPackage("com.workflow.system.audit..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.workflow.system.audit.api..",
                            "com.workflow.system.audit.application..",
                            "com.workflow.system.audit.domain..",
                            "com.workflow.system.audit.infrastructure..");

    @ArchTest
    static final ArchRule PROCESS_START_RUNTIME_DOES_NOT_WRITE_ENTITY_INTERNALS =
            noClasses()
                    .that().haveSimpleName("ProcessRuntimeService")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.workflow.entity.publish..",
                            "com.workflow.entity.runtime..");

    @ArchTest
    static final ArchRule ENTITY_RUNTIME_USES_PROCESS_CONTRACT =
            noClasses()
                    .that().haveSimpleName("EntityDataDynamicService")
                    .should().dependOnClassesThat().resideInAPackage(
                            "com.workflow.process.runtime..");

    @ArchTest
    static final ArchRule PROCESS_RUNTIME_USES_ENTITY_PORTS =
            noClasses()
                    .that().haveNameMatching(
                            ".*\\.(ProcessTaskService|ProcessTerminationService|"
                                    + "ProcessEndListener|EntityFormResolveService)")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.workflow.entity.form..",
                            "com.workflow.entity.policy..",
                            "com.workflow.entity.publish..",
                            "com.workflow.entity.runtime..");

    @ArchTest
    static final ArchRule PROCESS_RUNTIME_USES_IDENTITY_DIRECTORY =
            noClasses()
                    .that().haveNameMatching(
                            ".*\\.(ProcessTaskService|ProcessTerminationService)")
                    .should().dependOnClassesThat().haveNameMatching(
                            ".*\\.Sys(User|Group|UserGroup)(Service|Mapper)");
}
