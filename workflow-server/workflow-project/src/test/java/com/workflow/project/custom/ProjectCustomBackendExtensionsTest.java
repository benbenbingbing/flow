package com.workflow.project.custom;

import com.workflow.admin.identity.user.infrastructure.persistence.record.SysUser;
import com.workflow.contracts.action.FlowActionContext;
import com.workflow.contracts.action.FlowActionHandler;
import com.workflow.contracts.action.FlowActionTriggerProvider;
import com.workflow.contracts.bootstrap.BootstrapJobCoordinator;
import com.workflow.contracts.entity.list.DataScopePlan;
import com.workflow.contracts.entity.list.DataScopePredicateProvider;
import com.workflow.contracts.entity.list.EntityListActionProvider;
import com.workflow.contracts.entity.list.EntityListContextResolver;
import com.workflow.contracts.entity.list.EntityListDataProvider;
import com.workflow.contracts.entity.list.EntityListRuntimeContext;
import com.workflow.contracts.entity.list.EntityListSchemaProvider;
import com.workflow.contracts.entity.mutation.EntityChangeTarget;
import com.workflow.contracts.entity.mutation.EntityChangeTargetContext;
import com.workflow.contracts.entity.mutation.EntityChangeTargetResolver;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.contracts.entity.mutation.EntityMutationStepContext;
import com.workflow.contracts.entity.mutation.EntityMutationStepProvider;
import com.workflow.contracts.entity.mutation.EntityMutationStepResult;
import com.workflow.contracts.identity.IdentityDirectoryPort;
import com.workflow.contracts.identity.IdentityUser;
import com.workflow.contracts.identity.external.ExternalIdentityResolutionRequest;
import com.workflow.contracts.identity.external.ExternalIdentityResolver;
import com.workflow.contracts.identity.resolver.PersonResolveRequest;
import com.workflow.contracts.identity.resolver.PersonResolveResult;
import com.workflow.contracts.identity.resolver.PersonResolveUsage;
import com.workflow.contracts.identity.resolver.PersonResolver;
import com.workflow.contracts.integration.IntegrationConnector;
import com.workflow.contracts.integration.IntegrationRequest;
import com.workflow.contracts.integration.IntegrationResult;
import com.workflow.contracts.integration.IntegrationSecretResolver;
import com.workflow.contracts.migration.ConfigMigrationPublishRequest;
import com.workflow.contracts.migration.MigrationAssetHandler;
import com.workflow.contracts.ui.CommonInvocationContext;
import com.workflow.contracts.ui.EntityDescriptor;
import com.workflow.contracts.ui.FormInvocationContext;
import com.workflow.contracts.ui.ListInvocationContext;
import com.workflow.contracts.ui.UiDataSourceProvider;
import com.workflow.contracts.ui.catalog.UiExtensionCatalogPort;
import com.workflow.core.result.PageResult;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.list.extension.ListFieldDataProvider;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListField;
import com.workflow.entity.permission.api.response.EntityActionRuleDTO;
import com.workflow.entity.permission.api.response.MatchConfigDTO;
import com.workflow.entity.permission.application.EntityActionRuleConditionProvider;
import com.workflow.entity.permission.application.EntityDataPermissionFilterProvider;
import com.workflow.entity.permission.application.EntityDataPermissionMatchProvider;
import com.workflow.entity.permission.application.EntityPermissionOptionProvider;
import com.workflow.outbox.api.OutboxEvent;
import com.workflow.outbox.api.OutboxEventHandler;
import com.workflow.process.cc.application.CcNotificationChannel;
import com.workflow.process.cc.application.CcRecipientResolver;
import com.workflow.process.cc.application.CcRuntimeContext;
import com.workflow.process.cc.infrastructure.persistence.record.ProcessCcRecord;
import com.workflow.storage.application.FileStorageStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@SuppressWarnings("deprecation")
class ProjectCustomBackendExtensionsTest {

    private static final EntityDescriptor PROJECT_ENTITY =
            new EntityDescriptor(
                    "ENTITY-1",
                    "project",
                    "项目",
                    "DYNAMIC",
                    1);

    @Test
    void registersComposableBackendExtensionPoints() {
        try (AnnotationConfigApplicationContext context =
                     customExtensionContext()) {
            assertEquals(
                    2,
                    context.getBeansOfType(
                            FlowActionHandler.class)
                            .size());
            assertSingleBean(
                    context,
                    FlowActionTriggerProvider.class);
            assertSingleBean(context, PersonResolver.class);
            assertSingleBean(
                    context,
                    ExternalIdentityResolver.class);
            assertEquals(
                    4,
                    context.getBeansOfType(
                            UiDataSourceProvider.class)
                            .size());
            assertSingleBean(
                    context,
                    IntegrationConnector.class);
            assertSingleBean(
                    context,
                    ListFieldDataProvider.class);
            assertSingleBean(
                    context,
                    EntityListDataProvider.class);
            assertSingleBean(
                    context,
                    EntityListSchemaProvider.class);
            assertSingleBean(
                    context,
                    EntityListContextResolver.class);
            assertSingleBean(
                    context,
                    EntityListActionProvider.class);
            assertSingleBean(
                    context,
                    DataScopePredicateProvider.class);
            assertSingleBean(
                    context,
                    EntityMutationStepProvider.class);
            assertSingleBean(
                    context,
                    EntityChangeTargetResolver.class);
            assertSingleBean(
                    context,
                    EntityPermissionOptionProvider.class);
            assertSingleBean(
                    context,
                    EntityActionRuleConditionProvider.class);
            assertSingleBean(
                    context,
                    EntityDataPermissionMatchProvider.class);
            assertSingleBean(
                    context,
                    EntityDataPermissionFilterProvider.class);
            assertSingleBean(
                    context,
                    CcRecipientResolver.class);
            assertSingleBean(
                    context,
                    CcNotificationChannel.class);
            assertSingleBean(
                    context,
                    FileStorageStrategy.class);
            assertSingleBean(
                    context,
                    OutboxEventHandler.class);

            Set<String> uiDataSourceProviderCodes =
                    context.getBeansOfType(
                                    UiDataSourceProvider.class)
                            .values()
                            .stream()
                            .map(UiDataSourceProvider::getCode)
                            .collect(Collectors.toSet());
            assertEquals(
                    Set.of(
                            ProjectCustomUiDataSourceProvider
                                    .CODE,
                            ProjectCustomEntityUiDataSourceProvider
                                    .CODE,
                            ProjectCustomFormUiDataSourceProvider
                                    .CODE,
                            ProjectCustomListUiDataSourceProvider
                                    .CODE),
                    uiDataSourceProviderCodes);
            assertEquals(
                    ProjectCustomIntegrationConnector.CODE,
                    context.getBean(
                            IntegrationConnector.class)
                            .code());
            assertEquals(
                    ProjectCustomMutationStepProvider.CODE,
                    context.getBean(
                            EntityMutationStepProvider.class)
                            .getCode());
            assertEquals(
                    ProjectCustomFileStorageStrategy
                            .STORAGE_TYPE,
                    context.getBean(
                            FileStorageStrategy.class)
                            .getStorageType());
            assertEquals(
                    ProjectCustomOutboxEventHandler.TOPIC,
                    context.getBean(
                            OutboxEventHandler.class)
                            .topic());
        }
    }

    @Test
    void keepsSingleImplementationReplacementPortsUnregistered() {
        assertFalse(component(
                ProjectCustomIntegrationSecretResolver.class));
        assertFalse(component(
                ProjectCustomMigrationAssetHandler.class));
        assertFalse(component(
                ProjectCustomMigrationAssetRecorder.class));
        assertFalse(component(
                ProjectCustomBootstrapJobCoordinator.class));
        assertFalse(component(
                ProjectCustomUiExtensionCatalogPort.class));

        try (AnnotationConfigApplicationContext context =
                     customExtensionContext()) {
            assertTrue(context.getBeansOfType(
                            IntegrationSecretResolver.class)
                    .isEmpty());
            assertTrue(context.getBeansOfType(
                            MigrationAssetHandler.class)
                    .isEmpty());
            assertTrue(context.getBeansOfType(
                            BootstrapJobCoordinator.class)
                    .isEmpty());
            assertTrue(context.getBeansOfType(
                            UiExtensionCatalogPort.class)
                    .isEmpty());
        }
    }

    @Test
    void executesSafeLogOnlyExamples() {
        DataScopePlan allowedPlan =
                new DataScopePlan(
                        true,
                        "1=1",
                        Map.of(),
                        List.of(),
                        List.of("TEST"),
                        "test",
                        1);
        EntityListRuntimeContext listContext =
                new EntityListRuntimeContext(
                        "project",
                        "default",
                        "PAGE",
                        null,
                        null,
                        null,
                        Map.of());

        ProjectCustomUiDataSourceProvider uiProvider =
                new ProjectCustomUiDataSourceProvider();
        EntityDataDTO uiRecord = new EntityDataDTO();
        uiRecord.setId("PROJECT-UI-1");
        uiRecord.setDataNo("PRJ-UI-001");
        EntityListField uiField = new EntityListField();
        uiField.setFieldCode("unifiedSummary");
        Object uiResult = uiProvider.execute(
                listInvocationContext(
                        "LIST_COLUMN",
                        "unifiedSummary"),
                allowedPlan,
                Map.of(
                        "scene", "test",
                        "valuePrefix", "统一扩展"),
                Map.of(
                        "field", uiField,
                        "records", List.of(uiRecord)));
        assertEquals(
                "统一扩展:PRJ-UI-001",
                assertInstanceOf(Map.class, uiResult)
                        .get("PROJECT-UI-1"));

        Object entityOptions =
                new ProjectCustomEntityUiDataSourceProvider()
                        .execute(
                                formInvocationContext(
                                        "FIELD_OPTIONS",
                                        "edit",
                                        "status"),
                                allowedPlan,
                                Map.of(
                                        "optionLabelPrefix",
                                        "项目状态"),
                                Map.of(
                                        "fieldCode",
                                        "status"));
        assertEquals(
                2,
                assertInstanceOf(
                        List.class,
                        entityOptions)
                        .size());

        FormInvocationContext formContext =
                formInvocationContext(
                        "FORM_INIT",
                        "create",
                        null);
        Object formResult =
                new ProjectCustomFormUiDataSourceProvider()
                        .execute(
                                formContext,
                                allowedPlan,
                                Map.of(
                                        "messagePrefix",
                                        "项目表单",
                                        "targetField",
                                        "formTrace"),
                                Map.of(
                                        "recordId",
                                        "",
                                        "data",
                                        Map.of()));
        assertEquals(
                "项目表单:FORM_INIT:create",
                assertInstanceOf(
                        Map.class,
                        formResult)
                        .get("formTrace"));

        ListInvocationContext listContextForProvider =
                listInvocationContext(
                        "LIST_COLUMN",
                        "unifiedSummary");
        Object listColumnResult =
                new ProjectCustomListUiDataSourceProvider()
                        .execute(
                                listContextForProvider,
                                allowedPlan,
                                Map.of(
                                        "columnPrefix",
                                        "列表扩展"),
                                Map.of(
                                        "field",
                                        uiField,
                                        "records",
                                        List.of(uiRecord)));
        assertEquals(
                "列表扩展:PRJ-UI-001",
                assertInstanceOf(
                        Map.class,
                        listColumnResult)
                        .get("PROJECT-UI-1"));

        Object listQueryResult =
                new ProjectCustomListUiDataSourceProvider()
                        .execute(
                                listInvocationContext(
                                        "LIST_QUERY",
                                        null),
                                allowedPlan,
                                Map.of(
                                        "pageNum", 2,
                                        "pageSize", 5),
                                Map.of(
                                        "filters",
                                        Map.of(
                                                "status",
                                                "ACTIVE")));
        PageResult<?> unifiedListPage =
                assertInstanceOf(
                        PageResult.class,
                        listQueryResult);
        assertEquals(0, unifiedListPage.getTotal());
        assertEquals(2, unifiedListPage.getPageNum());

        Object formButtonResult =
                new ProjectCustomFormUiDataSourceProvider()
                        .execute(
                                formInvocationContext(
                                        "FORM_BUTTON_CLICK",
                                        "edit",
                                        null),
                                allowedPlan,
                                Map.of(
                                        "messagePrefix",
                                        "项目表单"),
                                Map.of(
                                        "buttonKey",
                                        "verify"));
        assertTrue(
                String.valueOf(
                                assertInstanceOf(
                                        Map.class,
                                        formButtonResult)
                                        .get("message"))
                        .contains(
                                "FORM_BUTTON_CLICK"));

        IntegrationResult integrationResult =
                new ProjectCustomIntegrationConnector()
                        .execute(IntegrationRequest.builder()
                                .operation("PING")
                                .connectorConfigId("CONFIG-1")
                                .idempotencyKey("KEY-1")
                                .parameters(Map.of(
                                        "requestId",
                                        "REQ-1"))
                                .dataScopePlan(allowedPlan)
                                .build());
        assertTrue(integrationResult.isSuccess());
        assertEquals(
                "PROJECT_CUSTOM_LOGGED",
                integrationResult.getCode());

        Object page = new ProjectCustomEntityListDataProvider()
                .query(
                        listContext,
                        allowedPlan,
                        Map.of(
                                "pageNum", 2,
                                "pageSize", 5));
        PageResult<?> pageResult =
                assertInstanceOf(PageResult.class, page);
        assertEquals(0, pageResult.getTotal());
        assertEquals(2, pageResult.getPageNum());

        Map<String, Object> schema =
                new ProjectCustomEntityListSchemaProvider()
                        .enhance(
                                listContext,
                                Map.of("columns", List.of()));
        assertTrue(schema.containsKey(
                "projectCustomSchema"));
        assertTrue(
                new ProjectCustomEntityListContextResolver()
                        .resolve(listContext)
                        .isEmpty());
        assertEquals(
                "LOGGED",
                assertInstanceOf(
                        Map.class,
                        new ProjectCustomEntityListActionProvider()
                                .execute(
                                        listContext,
                                        "verify",
                                        Map.of("id", "1")))
                        .get("status"));

        DataScopePlan emptyPlan =
                new ProjectCustomDataScopePredicateProvider()
                        .compile(
                                "project",
                                Map.of("mode", "EMPTY_RESULT"),
                                Map.of("userId", "USER-1"));
        assertEquals("1 = 0",
                emptyPlan.sqlFragment());

        EntityDataDTO record = new EntityDataDTO();
        record.setId("PROJECT-1");
        record.setDataNo("PRJ-001");
        EntityListField field =
                new EntityListField();
        field.setFieldCode("customSummary");
        new ProjectCustomListFieldDataProvider()
                .enrich(
                        List.of(record),
                        List.of(field),
                        Map.of("entityCode", "project"));
        assertEquals(
                "项目扩展:PRJ-001",
                record.getExtData()
                        .get("customSummary"));
    }

    private FormInvocationContext formInvocationContext(
            String bindingCode,
            String mode,
            String fieldCode) {
        return new FormInvocationContext(
                commonInvocationContext(
                        bindingCode,
                        "FORM",
                        "FORM-1",
                        fieldCode == null ? "OWNER" : "FIELD",
                        fieldCode,
                        "FORM-RELEASE-1"),
                PROJECT_ENTITY,
                "FORM-1",
                "demo",
                "项目表单",
                mode,
                null,
                fieldCode,
                null,
                null);
    }

    private ListInvocationContext listInvocationContext(
            String bindingCode,
            String fieldCode) {
        return new ListInvocationContext(
                commonInvocationContext(
                        bindingCode,
                        "LIST",
                        "LIST-1",
                        fieldCode == null ? "OWNER" : "COLUMN",
                        fieldCode,
                        "LIST-RELEASE-1"),
                PROJECT_ENTITY,
                "LIST-1",
                "default",
                "项目列表",
                1,
                20,
                fieldCode,
                "PAGE");
    }

    private CommonInvocationContext commonInvocationContext(
            String bindingCode,
            String ownerType,
            String ownerId,
            String targetType,
            String targetKey,
            String releaseId) {
        return new CommonInvocationContext(
                "SERVICE-1",
                "operation",
                bindingCode,
                ownerType,
                ownerId,
                targetType,
                targetKey,
                "USER-1",
                "tester",
                "TENANT-1",
                "ORG-1",
                "DEPT-1",
                releaseId,
                1,
                "REQUEST-1");
    }

    @Test
    void executesActionAndIdentityExamples() {
        FlowActionContext actionContext =
                new FlowActionContext();
        actionContext.setActionId("ACTION-1");
        actionContext.setActionName("项目验证");
        actionContext.setProcessInstanceId("PROC-1");
        actionContext.setEntityCode("project");
        actionContext.setEntityDataId("PROJECT-1");
        actionContext.setExtraParams(Map.of(
                "scenario", "test"));
        new ProjectCustomFlowActionHandler()
                .execute(actionContext);
        assertEquals(
                ProjectCustomFlowActionHandler.class
                        .getSimpleName(),
                assertInstanceOf(
                        Map.class,
                        actionContext
                                .getExecutionResult())
                        .get("handledBy"));

        new ProjectCustomTypedFlowActionHandler()
                .execute(
                        actionContext,
                        new ProjectCustomTypedFlowActionHandler
                                .Parameters(
                                "test",
                                1,
                                true));
        assertEquals(
                true,
                assertInstanceOf(
                        Map.class,
                        actionContext
                                .getExecutionResult())
                        .get("dryRun"));
        assertEquals(
                ProjectCustomFlowActionTriggerProvider
                        .TIMING,
                new ProjectCustomFlowActionTriggerProvider()
                        .getTriggerOptions()
                        .iterator()
                        .next()
                        .getValue());

        PersonResolveResult personResult =
                new ProjectCustomPersonResolver()
                        .resolve(new PersonResolveRequest(
                                1,
                                "TRACE-1",
                                "KEY-1",
                                PersonResolveUsage.ASSIGNEE,
                                "CONFIG-1",
                                "DEF-1",
                                "PROC-1",
                                "PROJECT-1",
                                "TASK-1",
                                "复核",
                                null,
                                "project",
                                "PROJECT-1",
                                "USER-INITIATOR",
                                "USER-OPERATOR",
                                Map.of(),
                                Map.of(),
                                Map.of(
                                        "userKeys",
                                        List.of("USER-1"))));
        assertEquals(
                1,
                personResult.principals().size());

        IdentityDirectoryPort directory =
                mock(IdentityDirectoryPort.class);
        when(directory.findUser("external-user"))
                .thenReturn(Optional.of(
                        new IdentityUser(
                                "USER-1",
                                "demo",
                                "演示用户",
                                "ORG-1",
                                "DEPT-1")));
        Optional<String> resolved =
                new ProjectCustomExternalIdentityResolver(
                        directory)
                        .resolve(
                                new ExternalIdentityResolutionRequest(
                                        ProjectCustomExternalIdentityResolver
                                                .NAMESPACE,
                                        "external-user",
                                        "project-system",
                                        "project",
                                        "PROJECT-1",
                                        null,
                                        Map.of()));
        assertEquals(
                Optional.of("demo"),
                resolved);
    }

    @Test
    void executesMutationPermissionAndMessagingExamples() {
        EntityMutationCommand command =
                EntityMutationCommand.update(
                        "project",
                        "PROJECT-1",
                        Map.of("name", "demo"),
                        EntityMutationContext.builder(
                                        EntityMutationSourceType.FORM,
                                        "TEST",
                                        "test")
                                .build());
        EntityMutationStepResult stepResult =
                new ProjectCustomMutationStepProvider()
                        .execute(
                                new EntityMutationStepContext(
                                        EntityMutationPhase
                                                .BEFORE_WRITE,
                                        command,
                                        Map.of(),
                                        command.payload(),
                                        Map.of(
                                                "scene",
                                                "test")));
        assertEquals(
                EntityMutationStepResult.Decision.ALLOW,
                stepResult.decision());

        List<EntityChangeTarget> targets =
                new ProjectCustomChangeTargetResolver()
                        .resolve(
                                new EntityChangeTargetContext(
                                        "project_change",
                                        "CHANGE-1",
                                        Map.of(
                                                "data",
                                                Map.of(
                                                        "targetId",
                                                        "PROJECT-1")),
                                        "PROC-1",
                                        Map.of(
                                                "recordIdPath",
                                                "data.targetId"),
                                        Map.of()));
        assertEquals("PROJECT-1",
                targets.get(0).recordId());

        ProjectCustomPermissionOptionProvider
                permissionProvider =
                new ProjectCustomPermissionOptionProvider();
        String permissionCode =
                permissionProvider.permissionCode(
                        "Project");
        assertTrue(permissionProvider
                .supportsPermission(
                        "Project",
                        permissionCode));

        SysUser user = new SysUser();
        user.setId("USER-1");
        user.setUsername("demo");
        MatchConfigDTO.MatchConditionDTO match =
                new MatchConfigDTO.MatchConditionDTO();
        match.setScopeType(
                ProjectCustomDataPermissionMatchProvider
                        .SCOPE_TYPE);
        match.setTargetIds(List.of("demo"));
        match.setOperator("ANY");
        assertTrue(
                new ProjectCustomDataPermissionMatchProvider()
                        .matches(match, user));

        EntityDataDTO row = new EntityDataDTO();
        row.setId("PROJECT-1");
        row.setData(Map.of("riskLevel", "HIGH"));
        EntityActionRuleDTO.RuleNode condition =
                new EntityActionRuleDTO.RuleNode();
        condition.setType(
                ProjectCustomActionRuleConditionProvider
                        .TYPE);
        condition.setField("riskLevel");
        condition.setOperator("EQ");
        condition.setValue("HIGH");
        assertTrue(
                new ProjectCustomActionRuleConditionProvider()
                        .evaluate(
                                condition,
                                row,
                                user,
                                "PROCESSING"));
        assertEquals(
                "1=0",
                new ProjectCustomDataPermissionFilterProvider()
                        .toSql(
                                "project",
                                condition,
                                user));

        CcRuntimeContext ccContext =
                new CcRuntimeContext(
                        "PROC-1",
                        "DEF-1",
                        "project",
                        "项目流程",
                        "PROJECT-1",
                        "TASK-1",
                        "复核",
                        "TASK_COMPLETE",
                        "USER-1",
                        Map.of());
        assertEquals(
                List.of("USER-1"),
                new ProjectCustomCcRecipientResolver()
                        .resolve(
                                ccContext,
                                Map.of(
                                        "fallbackToOperator",
                                        true)));

        ProcessCcRecord ccRecord =
                new ProcessCcRecord();
        ccRecord.setId("CC-1");
        ccRecord.setProcessInstanceId("PROC-1");
        ccRecord.setCcUserId("USER-1");
        new ProjectCustomCcNotificationChannel()
                .send(
                        ccRecord,
                        Map.of("title", "demo"));

        new ProjectCustomOutboxEventHandler()
                .handle(new OutboxEvent(
                        "OUTBOX-1",
                        ProjectCustomOutboxEventHandler
                                .TOPIC,
                        "EVENT-1",
                        "PROJECT",
                        "PROJECT-1",
                        "{}",
                        0,
                        LocalDateTime.now()));
        assertThrows(
                UnsupportedOperationException.class,
                () -> new ProjectCustomFileStorageStrategy()
                        .getAccessUrl("demo.txt"));
    }

    @Test
    void executesUnregisteredReplacementExamplesSafely() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> new ProjectCustomIntegrationSecretResolver()
                        .resolve("project/demo"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> new ProjectCustomBootstrapJobCoordinator()
                        .executeOnce(
                                "project-demo",
                                1,
                                () -> "done"));
        assertTrue(
                new ProjectCustomUiExtensionCatalogPort()
                        .listCatalogItems()
                        .isEmpty());

        ConfigMigrationPublishRequest request =
                new ConfigMigrationPublishRequest();
        request.setMigrationTag("project-demo");
        ProjectCustomMigrationAssetHandler handler =
                new ProjectCustomMigrationAssetHandler();
        handler.recordEntity(
                "ENTITY-1",
                "RELEASE-1",
                request);
        handler.recordProcess(
                "PROCESS-1",
                "VERSION-1",
                request);
        handler.recordSystemEntityUi(
                "ENTITY-1",
                "UI-RELEASE-1",
                request);
        handler.recordWorkCalendar(
                "CALENDAR-1",
                request);
        handler.recordTaskSlaPolicy(
                "SLA-1",
                request);
        new ProjectCustomMigrationAssetRecorder()
                .recordEntity(
                        "ENTITY-1",
                        "RELEASE-1",
                        request);
    }

    private AnnotationConfigApplicationContext
            customExtensionContext() {
        IdentityDirectoryPort directory =
                mock(IdentityDirectoryPort.class);
        when(directory.findUser("external-user"))
                .thenReturn(Optional.of(
                        new IdentityUser(
                                "USER-1",
                                "demo",
                                "演示用户",
                                "ORG-1",
                                "DEPT-1")));
        AnnotationConfigApplicationContext context =
                new AnnotationConfigApplicationContext();
        context.registerBean(
                IdentityDirectoryPort.class,
                () -> directory);
        context.scan("com.workflow.project.custom");
        context.refresh();
        return context;
    }

    private <T> void assertSingleBean(
            AnnotationConfigApplicationContext context,
            Class<T> type) {
        Map<String, T> beans =
                context.getBeansOfType(type);
        assertEquals(
                1,
                beans.size(),
                () -> type.getSimpleName()
                        + " beans: "
                        + beans.keySet());
        assertNotNull(
                beans.values().iterator().next());
    }

    private boolean component(Class<?> type) {
        return AnnotatedElementUtils.hasAnnotation(
                type,
                Component.class);
    }
}
