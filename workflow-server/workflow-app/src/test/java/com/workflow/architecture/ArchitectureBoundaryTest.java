package com.workflow.architecture;

import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;

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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PutMapping;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * 保护新增端口边界，防止业务模块绕过契约直接依赖审计实现。
 */
@AnalyzeClasses(
        packages = "com.workflow",
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureBoundaryTest {

    private static final Set<String> DYNAMIC_MAPPER_WRITE_METHODS =
            Set.of(
                    "insert",
                    "update",
                    "deleteById",
                    "physicalDeleteById",
                    "updateCurrentTask");
    private static final Set<String> DYNAMIC_MAPPER_WRITERS =
            Set.of(
                    "EntityDataMutationService",
                    "EntityRelationRuntimeService");

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
    static final ArchRule BUSINESS_MODULES_DO_NOT_USE_DYNAMIC_ENTITY_MAPPER =
            noClasses()
                    .that().resideInAnyPackage(
                            "com.workflow.process..",
                            "com.workflow.project..",
                            "com.workflow.admin..")
                    .should().dependOnClassesThat()
                    .haveNameMatching(
                            EntityDataDynamicMapper.class
                                    .getName()
                                    .replace(".", "\\."));

    @ArchTest
    static final ArchRule ONLY_AGGREGATE_WRITERS_MUTATE_DYNAMIC_TABLES =
            classes().should(
                    new ArchCondition<>(
                            "only mutate dynamic tables through aggregate writers") {
                        @Override
                        public void check(
                                JavaClass javaClass,
                                ConditionEvents events) {
                            if (DYNAMIC_MAPPER_WRITERS.contains(
                                    javaClass.getSimpleName())) {
                                return;
                            }
                            javaClass.getMethodCallsFromSelf()
                                    .stream()
                                    .filter(call ->
                                            call.getTargetOwner()
                                                    .getName()
                                                    .equals(
                                                            EntityDataDynamicMapper.class
                                                                    .getName()))
                                    .filter(call ->
                                            DYNAMIC_MAPPER_WRITE_METHODS
                                                    .contains(
                                                            call.getName()))
                                    .forEach(call ->
                                            events.add(
                                                    SimpleConditionEvent
                                                            .violated(
                                                                    call,
                                                                    call.getDescription()
                                                                            + " 绕过了 EntityMutationPipeline")));
                        }
                    });

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

    @ArchTest
    static final ArchRule HTTP_APIS_ONLY_USE_GET_OR_POST =
            methods()
                    .that().areDeclaredInClassesThat()
                    .resideInAPackage("..api.web..")
                    .should().notBeAnnotatedWith(PutMapping.class)
                    .andShould().notBeAnnotatedWith(
                            DeleteMapping.class)
                    .andShould().notBeAnnotatedWith(
                            PatchMapping.class);
}
