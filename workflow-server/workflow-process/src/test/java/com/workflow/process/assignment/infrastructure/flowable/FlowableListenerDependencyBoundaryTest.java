package com.workflow.process.assignment.infrastructure.flowable;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.admin.authorization.role.infrastructure.persistence.mapper.SysRoleMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysGroupMapper;
import com.workflow.admin.identity.group.infrastructure.persistence.mapper.SysUserGroupMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserMapper;
import com.workflow.admin.identity.user.infrastructure.persistence.mapper.SysUserRoleMapper;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.process.assignment.application.PersonResolverRuntimeService;
import com.workflow.process.definition.infrastructure.persistence.mapper.ProcessVersionHistoryMapper;
import com.workflow.process.task.application.nextapproval.NextApprovalRouteService;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideService;
import com.workflow.process.task.application.nextapproval.NextApproverOverrideStore;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class FlowableListenerDependencyBoundaryTest {

    @Test
    void assignmentListenersDependOnLeafStoreNotOverrideOrchestrator() {
        assertDirectlyDependsOn(
                MultiInstanceCollectionListener.class,
                NextApproverOverrideStore.class);
        assertDirectlyDependsOn(
                PersonResolverTaskAssignmentListener.class,
                NextApproverOverrideStore.class);
        assertNoDirectDependency(
                MultiInstanceCollectionListener.class,
                NextApproverOverrideService.class);
        assertNoDirectDependency(
                PersonResolverTaskAssignmentListener.class,
                NextApproverOverrideService.class);
    }

    @Test
    void overrideStoreCannotPullBusinessOrchestrationBackIntoListenerGraph() {
        assertNoDirectDependency(
                NextApproverOverrideStore.class,
                NextApproverOverrideService.class);
        assertNoDirectDependency(
                NextApproverOverrideStore.class,
                NextApprovalRouteService.class);
        assertNoDirectDependency(
                NextApproverOverrideStore.class,
                EntityMutationPort.class);
    }

    @Test
    void springCreatesBothListenersWithCircularReferencesDisabled() {
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext()) {
            context.getDefaultListableBeanFactory()
                    .setAllowCircularReferences(false);
            registerMock(context, RuntimeService.class);
            registerMock(context, RepositoryService.class);
            registerMock(context, TaskService.class);
            registerMock(context, ProcessVersionHistoryMapper.class);
            registerMock(context, SysGroupMapper.class);
            registerMock(context, SysUserGroupMapper.class);
            registerMock(context, SysRoleMapper.class);
            registerMock(context, SysUserRoleMapper.class);
            registerMock(context, SysUserMapper.class);
            registerMock(context, PersonResolverRuntimeService.class);
            context.registerBean(
                    "objectMapper",
                    ObjectMapper.class,
                    () -> new ObjectMapper());
            context.registerBean(NextApproverOverrideStore.class);
            context.registerBean(MultiInstanceAssignmentResolver.class);
            context.registerBean(MultiInstanceCollectionListener.class);
            context.registerBean(PersonResolverTaskAssignmentListener.class);

            context.refresh();

            assertNotNull(context.getBean(
                    NextApproverOverrideStore.class));
            assertNotNull(context.getBean(
                    MultiInstanceAssignmentResolver.class));
            assertNotNull(context.getBean(
                    MultiInstanceCollectionListener.class));
            assertNotNull(context.getBean(
                    PersonResolverTaskAssignmentListener.class));
        }
    }

    private void assertDirectlyDependsOn(
            Class<?> source,
            Class<?> dependency) {
        assertTrue(
                hasDirectDependency(source, dependency),
                () -> source.getSimpleName()
                        + " 应直接依赖 "
                        + dependency.getSimpleName());
    }

    private void assertNoDirectDependency(
            Class<?> source,
            Class<?> forbidden) {
        assertFalse(
                hasDirectDependency(source, forbidden),
                () -> source.getSimpleName()
                        + " 不得依赖 "
                        + forbidden.getSimpleName());
    }

    private boolean hasDirectDependency(
            Class<?> source,
            Class<?> dependency) {
        boolean fieldDependency = Arrays.stream(source.getDeclaredFields())
                .map(Field::getType)
                .anyMatch(dependency::equals);
        boolean constructorDependency = Arrays.stream(
                        source.getDeclaredConstructors())
                .map(Constructor::getParameterTypes)
                .flatMap(Arrays::stream)
                .anyMatch(dependency::equals);
        return fieldDependency || constructorDependency;
    }

    private <T> void registerMock(
            AnnotationConfigApplicationContext context,
            Class<T> type) {
        context.registerBean(type, () -> mock(type));
    }
}
