package com.workflow.architecture;

import com.workflow.entity.data.application.EntityDataDynamicService;

import com.workflow.process.engine.infrastructure.flowable.ProcessEndListener;
import com.workflow.process.form.application.EntityFormResolveService;
import com.workflow.process.instance.application.ProcessRuntimeService;
import com.workflow.process.instance.application.ProcessTerminationService;
import com.workflow.process.task.application.ProcessTaskService;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 保护新增端口边界，防止业务模块绕过契约直接依赖审计实现。
 */
@AnalyzeClasses(
        packages = "com.workflow",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundaryTest {

    @ArchTest
    static final ArchRule CORE_CONTRACTS_ARE_FRAMEWORK_FREE =
            noClasses()
                    .that().resideInAPackage("com.workflow.contracts..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "org.springframework..",
                            "com.baomidou..",
                            "com.fasterxml..",
                            "jakarta.servlet..");

    @ArchTest
    static final ArchRule BUSINESS_MODULES_USE_AUDIT_CONTRACTS_ONLY =
            noClasses()
                    .that().resideOutsideOfPackage("com.workflow.admin.audit..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.workflow.admin.audit.api..",
                            "com.workflow.admin.audit.application..",
                            "com.workflow.admin.audit.domain..",
                            "com.workflow.admin.audit.infrastructure..");

    @ArchTest
    static final ArchRule PROCESS_START_RUNTIME_DOES_NOT_WRITE_ENTITY_INTERNALS =
            noClasses()
                    .that().haveSimpleName("ProcessRuntimeService")
                    .should().dependOnClassesThat().resideInAPackage(
                            "com.workflow.entity..");

    @ArchTest
    static final ArchRule ENTITY_RUNTIME_USES_PROCESS_CONTRACT =
            noClasses()
                    .that().haveSimpleName("EntityDataDynamicService")
                    .should().dependOnClassesThat().resideInAPackage(
                            "com.workflow.process..");

    @ArchTest
    static final ArchRule PROCESS_RUNTIME_USES_ENTITY_PORTS =
            noClasses()
                    .that().haveNameMatching(
                            ".*\\.(ProcessTaskService|ProcessTerminationService|"
                                    + "ProcessEndListener|EntityFormResolveService)")
                    .should().dependOnClassesThat().resideInAPackage(
                            "com.workflow.entity..");

    @ArchTest
    static final ArchRule PROCESS_RUNTIME_USES_IDENTITY_DIRECTORY =
            noClasses()
                    .that().haveNameMatching(
                            ".*\\.(ProcessTaskService|ProcessTerminationService)")
                    .should().dependOnClassesThat().haveNameMatching(
                            ".*\\.Sys(User|Group|UserGroup)(Service|Mapper)");

    @ArchTest
    static final ArchRule OPEN_API_DOES_NOT_DEPEND_ON_INTERNAL_MODULES =
            noClasses()
                    .that().resideInAPackage("com.workflow.openapi..")
                    .should().dependOnClassesThat().resideInAnyPackage(
                            "com.workflow.admin..",
                            "com.workflow.entity..",
                            "com.workflow.process..",
                            "com.workflow.project..");

    @ArchTest
    static final ArchRule SPRING_COMPONENTS_SELECT_OVERLOADED_CONSTRUCTORS =
            classes()
                    .that().areMetaAnnotatedWith(Component.class)
                    .should(new ArchCondition<>(
                            "select an injection constructor when overloaded") {
                        @Override
                        public void check(
                                JavaClass item,
                                ConditionEvents events) {
                            if (item.getConstructors().size() <= 1
                                    || item.getConstructors().stream()
                                    .anyMatch(constructor ->
                                            constructor.getRawParameterTypes()
                                                    .isEmpty())
                                    || item.getConstructors().stream()
                                    .anyMatch(constructor ->
                                            constructor.isAnnotatedWith(
                                                    Autowired.class))) {
                                return;
                            }
                            events.add(SimpleConditionEvent.violated(
                                    item,
                                    item.getName()
                                            + " has ambiguous constructors"));
                        }
                    });

    @ArchTest
    static final ArchRule PRODUCTION_CODE_AVOIDS_GLOBAL_TECHNICAL_PACKAGES =
            noClasses()
                    .should().resideInAnyPackage(
                            "com.workflow.controller..",
                            "com.workflow.service..",
                            "com.workflow.mapper..",
                            "com.workflow.dto..",
                            "com.workflow.delegate..",
                            "com.workflow.listener..",
                            "com.workflow.runner..",
                            "com.workflow.vo..");
}
