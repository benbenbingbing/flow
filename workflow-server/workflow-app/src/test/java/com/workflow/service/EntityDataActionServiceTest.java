package com.workflow.service;

import com.workflow.entity.data.application.EntityDataActionService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.FormSubmissionTraceService;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.form.uniqueness.application.FormUniqueMutationContext;
import com.workflow.entity.form.uniqueness.application.TrustedSubFormUniqueReference;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.form.infrastructure.persistence.record.EntityForm;
import com.workflow.entity.list.application.EntityListPublishedRuntimeService;
import com.workflow.entity.ui.api.response.UiEventExecutionResult;
import com.workflow.entity.ui.application.UiEventRuntimeService;
import com.workflow.entity.ui.application.UiViewCompositionActionService;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationBatchCommand;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPort;
import com.workflow.contracts.entity.mutation.EntityMutationResult;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.core.error.ForbiddenException;
import com.workflow.entity.data.api.response.EntityDataDTO;
import com.workflow.entity.permission.api.response.EntityActionCapabilityDTO;
import com.workflow.entity.list.infrastructure.persistence.record.EntityListConfig;
import com.workflow.entity.permission.application.EntityActionCapabilityService;
import com.workflow.entity.permission.application.EntityPermissionAction;
import com.workflow.entity.permission.application.EntityListActionConfigService;
import com.workflow.entity.permission.application.EntityListScopeAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 实体数据动作服务测试。
 *
 * <p>
 * 被测对象：{@link EntityDataActionService}，覆盖实体数据详情查询、批量删除的权限校验与回滚、
 * 创建/更新流程中服务端表单提交钩子的执行次数等场景。
 */
@ExtendWith(MockitoExtension.class)
class EntityDataActionServiceTest {

        @Mock
        private EntityDataDynamicService dynamicService;

        @Mock
        private SystemEntityReadService systemEntityReadService;

        @Mock
        private EntityMutationPort mutationPort;

        @Mock
        private EntityListActionConfigService actionConfigService;

        @Mock
        private EntityListPublishedRuntimeService publishedListRuntimeService;

        @Mock
        private EntityActionCapabilityService capabilityService;

        @Mock
        private EntityListScopeAuditService scopeAuditService;

        @Mock
        private PublishedFormSubmissionService formSubmissionService;

        @Mock
        private FormSubmissionTraceService formSubmissionTraceService;

        @Mock
        private UiEventRuntimeService eventRuntimeService;

        @Mock
        private UiViewCompositionActionService viewCompositionActionService;

        @Mock
        private EntityDefinitionMapper definitionMapper;

        @Mock
        private EntityFormMapper formMapper;

        @Spy
        private ObjectMapper objectMapper = new ObjectMapper();

        @InjectMocks
        private EntityDataActionService service;

        @BeforeEach
        void setUp() {
                EntityDefinition asset = new EntityDefinition();
                asset.setId("entity-asset");
                asset.setEntityCode("asset");
                asset.setStorageMode(EntityDefinition.StorageMode.DYNAMIC);
                lenient().when(definitionMapper.findByEntityCode("asset"))
                                .thenReturn(Optional.of(asset));
                lenient().when(publishedListRuntimeService.resolveConfig(
                                any(EntityListConfig.class),
                                isNull(),
                                isNull(),
                                isNull()))
                                .thenAnswer(invocation ->
                                        invocation.getArgument(0));
        }

        @Test
        void readOnlyDetailDoesNotExecuteUiEventChain() {
                EntityListConfig config = new EntityListConfig();
                config.setListKey("default");
                when(actionConfigService.resolveListConfig(
                                "asset", "default")).thenReturn(config);
                EntityDataDTO row = row("1", "A-1");
                when(dynamicService.findAccessibleById(
                                "asset", "1", "default")).thenReturn(row);

                EntityDataDTO result = service.getDetailReadOnly(
                                "asset", "1", "default");

                assertEquals(row, result);
                verify(capabilityService).requireStandardPermission(
                                "asset", EntityPermissionAction.VIEW);
                verify(capabilityService).requireRowActionForConfig(
                                "asset", config, "view", row);
                verify(publishedListRuntimeService).resolveConfig(
                                config, null, null, null);
                verify(eventRuntimeService, never()).execute(any(), any());
        }

        /**
         * 测试流程实例详情查询使用已解析的列表权限作用域：
         * 验证按流程实例查询实体详情时，调用的是带权限作用域的 findAccessibleByProcessInstanceId 方法。
         */
        @Test
        void processInstanceDetailUsesResolvedListPermissionScope() {
                EntityListConfig config = new EntityListConfig();
                config.setId("list-1");
                config.setListKey("default");
                when(actionConfigService.resolveListConfig("asset", "default"))
                                .thenReturn(config);
                EntityDataDTO row = row("1", "A-1");
                when(dynamicService.findAccessibleByProcessInstanceId(
                                "asset",
                                "process-1",
                                "default")).thenReturn(row);

                service.getDetailByProcessInstance("asset", "process-1", "default");

                verify(dynamicService).findAccessibleByProcessInstanceId(
                                "asset",
                                "process-1",
                                "default");
        }

        @Test
        void systemEntityUpdateIsRejectedBeforeMutation() {
                EntityDefinition entity = new EntityDefinition();
                entity.setEntityCode("sys_user");
                entity.setStorageMode(EntityDefinition.StorageMode.SYSTEM);
                when(definitionMapper.findByEntityCode("sys_user"))
                                .thenReturn(Optional.of(entity));

                BusinessConflictException error = assertThrows(
                                BusinessConflictException.class,
                                () -> service.update(
                                                "sys_user",
                                                "1",
                                                "readonly_users",
                                                Map.of("data", Map.of("nickname", "blocked"))));

                assertEquals(
                                "ENTITY_SYSTEM_RUNTIME_NOT_SUPPORTED",
                                error.getErrorCode());
                verify(mutationPort, never())
                                .execute(any(EntityMutationCommand.class));
        }

        /**
         * 测试批量删除的"全有或全无"语义：当任一行无删除权限时整批失败，
         * 验证抛出 ForbiddenException 且所有行均未执行删除。
         */
        @Test
        void batchDeleteIsAllOrNothing() {
                EntityDataDTO allowed = row("1", "A-1");
                EntityDataDTO denied = row("2", "A-2");
                when(dynamicService.findAccessibleById("asset", "1", null)).thenReturn(allowed);
                when(dynamicService.findAccessibleById("asset", "2", null)).thenReturn(denied);
                when(capabilityService.evaluateRowActionForConfig(
                                "asset", null, "batchDelete", allowed))
                                .thenReturn(EntityActionCapabilityDTO.allowed());
                when(capabilityService.evaluateRowActionForConfig(
                                "asset", null, "batchDelete", denied))
                                .thenReturn(EntityActionCapabilityDTO.hidden("仅本人草稿可以删除"));

                assertThrows(
                                ForbiddenException.class,
                                () -> service.batchDelete("asset", List.of("1", "2"), null));

                verify(mutationPort, never()).executeBatch(
                                any(EntityMutationBatchCommand.class));
        }

        /**
         * 测试批量删除在校验通过后进入同一个原子变更批次。
         */
        @Test
        void batchDeleteDeletesAllAfterValidation() {
                EntityDataDTO first = row("1", "A-1");
                EntityDataDTO second = row("2", "A-2");
                when(dynamicService.findAccessibleById("asset", "1", null)).thenReturn(first);
                when(dynamicService.findAccessibleById("asset", "2", null)).thenReturn(second);
                when(capabilityService.evaluateRowActionForConfig(
                                "asset", null, "batchDelete", first))
                                .thenReturn(EntityActionCapabilityDTO.allowed());
                when(capabilityService.evaluateRowActionForConfig(
                                "asset", null, "batchDelete", second))
                                .thenReturn(EntityActionCapabilityDTO.allowed());

                service.batchDelete("asset", List.of("1", "2"), null);

                ArgumentCaptor<EntityMutationBatchCommand> captor = ArgumentCaptor.forClass(
                                EntityMutationBatchCommand.class);
                verify(mutationPort).executeBatch(captor.capture());
                EntityMutationBatchCommand command = captor.getValue();
                assertEquals(true, command.atomic());
                assertEquals(List.of("1", "2"), command.commands()
                                .stream()
                                .map(EntityMutationCommand::recordId)
                                .toList());
                assertEquals(
                                List.of(
                                                EntityMutationOperationType.DELETE,
                                                EntityMutationOperationType.DELETE),
                                command.commands()
                                                .stream()
                                                .map(EntityMutationCommand::operationType)
                                                .toList());
        }

        /**
         * 测试创建数据时服务端 beforeSubmit 钩子恰好执行一次：
         * 验证默认表单处理与统一新增命令各执行一次。
         */
        @Test
        void createExecutesServerBeforeSubmitExactlyOnce() {
                EntityDataDTO dto = new EntityDataDTO();
                dto.setEntityCode("asset");
                dto.setData(Map.of("name", "Laptop"));
                FormSubmissionExecutionContext context = context("create-trace", "ENTITY_CREATE");
                when(formSubmissionTraceService.current(
                                eq("ENTITY_CREATE"),
                                isNull(),
                                anyMap())).thenReturn(context);
                when(formSubmissionService.applyDefaultFormWithRelease(
                                "asset",
                                null,
                                "create",
                                dto.getData(),
                                context)).thenReturn(
                                                new PublishedFormSubmissionService.DefaultFormApplication(
                                                        Map.of(
                                                                "name",
                                                                "Laptop",
                                                                "normalized",
                                                                true),
                                                        null,
                                                        null,
                                                        null,
                                                        null));
                when(mutationPort.execute(
                                any(EntityMutationCommand.class)))
                                .thenReturn(mutationResult(
                                                "1",
                                                EntityMutationOperationType.CREATE,
                                                Map.of(
                                                                "name",
                                                                "Laptop",
                                                                "normalized",
                                                                true)));

                service.create(dto);

                verify(formSubmissionService, times(1))
                                .applyDefaultFormWithRelease(
                                                "asset",
                                                null,
                                                "create",
                                                Map.of("name", "Laptop"),
                                                context);
                ArgumentCaptor<EntityMutationCommand> captor = ArgumentCaptor.forClass(
                                EntityMutationCommand.class);
                verify(mutationPort, times(1))
                                .execute(captor.capture());
                EntityMutationCommand command = captor.getValue();
                assertEquals(EntityMutationOperationType.CREATE,
                                command.operationType());
                assertEquals("CREATE_RECORD",
                                command.context().businessIntentCode());
                assertEquals("create-trace",
                                command.context().idempotencyKey());
                assertEquals(
                                Map.of(
                                                "name",
                                                "Laptop",
                                                "normalized",
                                                true),
                                command.payload().get("data"));
        }

        @Test
        void defaultFormFallbackCarriesEffectiveReleaseAndTrustedChildMarker() {
                FormUniqueMutationContext.Reference childReference =
                                new FormUniqueMutationContext.Reference(
                                                "child-form",
                                                "child-release-1",
                                                1,
                                                "child-hotfix-2",
                                                "child-hash-2",
                                                "child-target-2");
                Map<String, Object> child = new LinkedHashMap<>(
                                Map.of("name", "明细A"));
                TrustedSubFormUniqueReference.attach(
                                child,
                                "asset_detail",
                                childReference);
                Map<String, Object> processed = new LinkedHashMap<>();
                processed.put("name", "Laptop");
                processed.put("details", List.of(child));
                EntityDataDTO dto = new EntityDataDTO();
                dto.setEntityCode("asset");
                dto.setData(Map.of("name", "Laptop"));
                FormSubmissionExecutionContext context = context(
                                "default-form-trace",
                                "ENTITY_CREATE");
                when(formSubmissionTraceService.current(
                                eq("ENTITY_CREATE"),
                                isNull(),
                                anyMap())).thenReturn(context);
                when(formSubmissionService.applyDefaultFormWithRelease(
                                "asset",
                                null,
                                "create",
                                Map.of("name", "Laptop"),
                                context)).thenReturn(
                                                new PublishedFormSubmissionService.DefaultFormApplication(
                                                        processed,
                                                        "default-form",
                                                        "release-3",
                                                        3,
                                                        "hotfix-4",
                                                        "hash-4",
                                                        "target-4"));
                when(mutationPort.execute(
                                any(EntityMutationCommand.class)))
                                .thenReturn(mutationResult(
                                                "1",
                                                EntityMutationOperationType.CREATE,
                                                processed));

                service.create(dto);

                ArgumentCaptor<EntityMutationCommand> captor =
                                ArgumentCaptor.forClass(
                                                EntityMutationCommand.class);
                verify(mutationPort).execute(captor.capture());
                EntityMutationCommand command = captor.getValue();
                assertEquals(
                                "default-form",
                                command.context().extraParams().get(
                                                FormUniqueMutationContext.FORM_ID));
                assertEquals(
                                "hotfix-4",
                                command.context().extraParams().get(
                                                FormUniqueMutationContext.FORM_EFFECTIVE_RELEASE_ID));
                assertEquals(
                                "hash-4",
                                command.context().extraParams().get(
                                                FormUniqueMutationContext.FORM_EFFECTIVE_CONTENT_HASH));
                assertEquals(
                                "target-4",
                                command.context().extraParams().get(
                                                FormUniqueMutationContext.FORM_HOTFIX_TARGET_ID));
                @SuppressWarnings("unchecked")
                Map<String, Object> commandChild =
                                (Map<String, Object>) ((List<?>) ((Map<?, ?>)
                                                command.payload().get("data"))
                                                .get("details")).get(0);
                assertEquals(
                                List.of(childReference),
                                TrustedSubFormUniqueReference.remove(
                                                commandChild));
        }

        /**
         * 测试显式选择表单时按所选发布表单处理新增数据，而不是错误回退默认表单。
         */
        @Test
        void createUsesExplicitlySelectedForm() {
                EntityDataDTO dto = new EntityDataDTO();
                dto.setEntityCode("asset");
                dto.setFormId("form-1");
                dto.setFormReleaseResolutionToken("signed-form-token");
                dto.setData(Map.of("name", "Laptop"));
                EntityForm form = form("form-1", "entity-asset");
                EntityDefinition asset = new EntityDefinition();
                asset.setId("entity-asset");
                asset.setEntityCode("asset");
                when(formMapper.selectById("form-1")).thenReturn(form);
                when(definitionMapper.selectById("entity-asset"))
                                .thenReturn(asset);
                FormSubmissionExecutionContext context = context("create-form-trace", "ENTITY_CREATE");
                when(formSubmissionTraceService.current(
                                eq("ENTITY_CREATE"),
                                isNull(),
                                anyMap())).thenReturn(context);
                when(formSubmissionService.applyAuthorizedFormWithRelease(
                                "form-1",
                                null,
                                null,
                                "signed-form-token",
                                "asset",
                                null,
                                "create",
                                Map.of("name", "Laptop"),
                                context)).thenReturn(
                                                new PublishedFormSubmissionService.AuthorizedFormApplication(
                                                        Map.of(
                                                                "name",
                                                                "Laptop",
                                                        "fromSelectedForm",
                                                                true),
                                                        "release-pinned",
                                                        7,
                                                        "release-hotfix-8",
                                                        "hash-target-8",
                                                        "target-8"));
                stubDefaultEventExecution();
                when(mutationPort.execute(
                                any(EntityMutationCommand.class)))
                                .thenReturn(mutationResult(
                                                "1",
                                                EntityMutationOperationType.CREATE,
                                                Map.of(
                                                                "name",
                                                                "Laptop",
                                                                "fromSelectedForm",
                                                                true)));

                service.create(dto);

                verify(formSubmissionService).applyAuthorizedFormWithRelease(
                                "form-1",
                                null,
                                null,
                                "signed-form-token",
                                "asset",
                                null,
                                "create",
                                Map.of("name", "Laptop"),
                                context);
                verify(formSubmissionService, never())
                                .applyDefaultFormWithRelease(
                                                anyString(),
                                                isNull(),
                                                anyString(),
                                                anyMap(),
                                                any());
                ArgumentCaptor<EntityMutationCommand> commandCaptor =
                                ArgumentCaptor.forClass(EntityMutationCommand.class);
                verify(mutationPort).execute(commandCaptor.capture());
                assertEquals(
                                "release-pinned",
                                commandCaptor.getValue().context().extraParams().get(
                                                FormUniqueMutationContext.FORM_RELEASE_ID));
                assertEquals(
                                7,
                                commandCaptor.getValue().context().extraParams().get(
                                                FormUniqueMutationContext.FORM_RELEASE_VERSION));
                assertEquals(
                                "release-hotfix-8",
                                commandCaptor.getValue().context().extraParams().get(
                                                FormUniqueMutationContext.FORM_EFFECTIVE_RELEASE_ID));
                assertEquals(
                                "hash-target-8",
                                commandCaptor.getValue().context().extraParams().get(
                                                FormUniqueMutationContext.FORM_EFFECTIVE_CONTENT_HASH));
                assertEquals(
                                "target-8",
                                commandCaptor.getValue().context().extraParams().get(
                                                FormUniqueMutationContext.FORM_HOTFIX_TARGET_ID));
                assertEquals(
                                false,
                                commandCaptor.getValue().context().extraParams().containsValue(
                                                "signed-form-token"));
        }

        @Test
        void compositionCreateOverwritesClientValueWithTrustedInitialMapping() {
                EntityDataDTO dto = new EntityDataDTO();
                dto.setEntityCode("asset");
                dto.setFormId("form-1");
                dto.setFormReleaseId("form-release-1");
                dto.setFormReleaseVersion(2);
                dto.setFormReleaseResolutionToken("form-release-token");
                dto.setViewCompositionActionContextToken("composition-token");
                dto.setData(Map.of(
                                "displayName", "浏览器篡改值",
                                "note", "保留值"));
                EntityForm form = form("form-1", "entity-asset");
                EntityDefinition asset = new EntityDefinition();
                asset.setId("entity-asset");
                asset.setEntityCode("asset");
                when(formMapper.selectById("form-1")).thenReturn(form);
                when(definitionMapper.selectById("entity-asset"))
                                .thenReturn(asset);
                when(viewCompositionActionService.authorizeFormSubmission(
                                "composition-token",
                                "CREATE",
                                "asset",
                                null,
                                "form-1",
                                "form-release-1",
                                2,
                                "form-release-token"))
                                .thenReturn(Map.of(
                                                "displayName",
                                                "服务端来源值"));
                FormSubmissionExecutionContext context = context(
                                "composition-create-trace",
                                "ENTITY_CREATE");
                when(formSubmissionTraceService.current(
                                eq("ENTITY_CREATE"),
                                isNull(),
                                anyMap())).thenReturn(context);
                Map<String, Object> trustedSubmission = Map.of(
                                "displayName", "服务端来源值",
                                "note", "保留值");
                when(formSubmissionService.applyAuthorizedFormWithRelease(
                                "form-1",
                                "form-release-1",
                                2,
                                "form-release-token",
                                "asset",
                                null,
                                "create",
                                trustedSubmission,
                                context)).thenReturn(
                                        new PublishedFormSubmissionService.AuthorizedFormApplication(
                                                trustedSubmission,
                                                "form-release-1",
                                                2));
                stubDefaultEventExecution();
                when(mutationPort.execute(any(EntityMutationCommand.class)))
                                .thenReturn(mutationResult(
                                                "1",
                                                EntityMutationOperationType.CREATE,
                                                trustedSubmission));

                service.create(dto);

                verify(viewCompositionActionService)
                                .authorizeFormSubmission(
                                                "composition-token",
                                                "CREATE",
                                                "asset",
                                                null,
                                                "form-1",
                                                "form-release-1",
                                                2,
                                                "form-release-token");
                verify(formSubmissionService).applyAuthorizedFormWithRelease(
                                "form-1",
                                "form-release-1",
                                2,
                                "form-release-token",
                                "asset",
                                null,
                                "create",
                                trustedSubmission,
                                context);
                verify(actionConfigService, never())
                                .resolveListConfig(anyString(), any());
        }

        /**
         * 测试更新数据时服务端 beforeSubmit 钩子恰好执行一次：
         * 验证默认表单处理与统一更新命令各执行一次，且载荷为规范化后的数据。
         */
        @Test
        void updateExecutesServerBeforeSubmitExactlyOnce() {
                EntityListConfig config = new EntityListConfig();
                config.setId("list-1");
                config.setListKey("default");
                when(actionConfigService.resolveListConfig(
                                "asset",
                                "default")).thenReturn(config);
                EntityDataDTO existing = row("1", "A-1");
                when(dynamicService.findAccessibleById(
                                "asset",
                                "1",
                                "default")).thenReturn(existing);
                FormSubmissionExecutionContext context = context("update-trace", "ENTITY_UPDATE");
                when(formSubmissionTraceService.current(
                                eq("ENTITY_UPDATE"),
                                isNull(),
                                anyMap())).thenReturn(context);
                when(formSubmissionService.applyDefaultFormWithRelease(
                                "asset",
                                "1",
                                "edit",
                                Map.of(
                                                "name",
                                                "Laptop",
                                                "amount",
                                                12),
                                context)).thenReturn(
                                                new PublishedFormSubmissionService.DefaultFormApplication(
                                                        Map.of(
                                                                "name",
                                                                "Laptop",
                                                                "normalized",
                                                                true),
                                                        null,
                                                        null,
                                                        null,
                                                        null));
                when(mutationPort.execute(
                                any(EntityMutationCommand.class)))
                                .thenReturn(mutationResult(
                                                "1",
                                                EntityMutationOperationType.UPDATE,
                                                Map.of(
                                                                "name",
                                                                "Laptop",
                                                                "normalized",
                                                                true)));
                stubDefaultEventExecution();

                service.update(
                                "asset",
                                "1",
                                "default",
                                Map.of(
                                                "entityCode",
                                                "asset",
                                                "listKey",
                                                "default",
                                                "id",
                                                "1",
                                                "data",
                                                Map.of(
                                                                "name",
                                                                "Laptop",
                                                                "amount",
                                                                12),
                                                "startProcess",
                                                true));

                verify(formSubmissionService, times(1))
                                .applyDefaultFormWithRelease(
                                                "asset",
                                                "1",
                                                "edit",
                                                Map.of(
                                                                "name",
                                                                "Laptop",
                                                                "amount",
                                                                12),
                                                context);
                ArgumentCaptor<EntityMutationCommand> captor = ArgumentCaptor.forClass(
                                EntityMutationCommand.class);
                verify(mutationPort, times(1))
                                .execute(captor.capture());
                EntityMutationCommand command = captor.getValue();
                assertEquals(EntityMutationOperationType.UPDATE,
                                command.operationType());
                assertEquals("1", command.recordId());
                assertEquals("EDIT_RECORD",
                                command.context().businessIntentCode());
                assertEquals("update-trace",
                                command.context().idempotencyKey());
                assertEquals(
                                Map.of(
                                                "data",
                                                Map.of(
                                                                "name",
                                                                "Laptop",
                                                                "normalized",
                                                                true),
                                                "startProcess",
                                                true),
                                command.payload());
        }

        /**
         * 测试显式选择表单时按所选发布表单处理更新数据。
         */
        @Test
        void updateUsesExplicitlySelectedForm() {
                EntityDataDTO existing = row("1", "A-1");
                EntityListConfig config = new EntityListConfig();
                config.setListKey("default");
                when(actionConfigService.resolveListConfig(
                                "asset",
                                "default")).thenReturn(config);
                when(dynamicService.findAccessibleById(
                                "asset",
                                "1",
                                "default")).thenReturn(existing);
                EntityForm form = form("form-1", "entity-asset");
                EntityDefinition asset = new EntityDefinition();
                asset.setId("entity-asset");
                asset.setEntityCode("asset");
                when(formMapper.selectById("form-1")).thenReturn(form);
                when(definitionMapper.selectById("entity-asset"))
                                .thenReturn(asset);
                FormSubmissionExecutionContext context = context("update-form-trace", "ENTITY_UPDATE");
                when(formSubmissionTraceService.current(
                                eq("ENTITY_UPDATE"),
                                isNull(),
                                anyMap())).thenReturn(context);
                when(formSubmissionService.applyAuthorizedFormWithRelease(
                                "form-1",
                                null,
                                null,
                                null,
                                "asset",
                                "1",
                                "edit",
                                Map.of("name", "Laptop"),
                                context)).thenReturn(
                                                new PublishedFormSubmissionService.AuthorizedFormApplication(
                                                        Map.of(
                                                                "name",
                                                                "Laptop",
                                                                "fromSelectedForm",
                                                                true),
                                                        null,
                                                        null));
                stubDefaultEventExecution();
                when(mutationPort.execute(
                                any(EntityMutationCommand.class)))
                                .thenReturn(mutationResult(
                                                "1",
                                                EntityMutationOperationType.UPDATE,
                                                Map.of(
                                                                "name",
                                                                "Laptop",
                                                                "fromSelectedForm",
                                                                true)));

                service.update(
                                "asset",
                                "1",
                                "default",
                                Map.of(
                                                "formId",
                                                "form-1",
                                                "data",
                                                Map.of(
                                                                "name",
                                                                "Laptop")));

                verify(formSubmissionService).applyAuthorizedFormWithRelease(
                                "form-1",
                                null,
                                null,
                                null,
                                "asset",
                                "1",
                                "edit",
                                Map.of("name", "Laptop"),
                                context);
                verify(formSubmissionService, never())
                                .applyDefaultFormWithRelease(
                                                anyString(),
                                                anyString(),
                                                anyString(),
                                                anyMap(),
                                                any());
        }

        /** 构造一条包含 id 与 dataNo 的实体数据 DTO */
        private EntityDataDTO row(String id, String dataNo) {
                EntityDataDTO row = new EntityDataDTO();
                row.setId(id);
                row.setDataNo(dataNo);
                return row;
        }

        private EntityForm form(String id, String entityId) {
                EntityForm form = new EntityForm();
                form.setId(id);
                form.setEntityId(entityId);
                return form;
        }

        @SuppressWarnings("unchecked")
        private void stubDefaultEventExecution() {
                when(eventRuntimeService.execute(any(), any()))
                                .thenAnswer(invocation -> {
                                        Function<Map<String, Object>, Object> handler = invocation.getArgument(1);
                                        Object data = handler.apply(
                                                        invocation.<com.workflow.entity.ui.api.request.UiEventExecuteRequest>getArgument(
                                                                        0)
                                                                        .getInput());
                                        UiEventExecutionResult result = new UiEventExecutionResult();
                                        result.setData(data);
                                        return result;
                                });
        }

        /** 构造一个携带 traceKey 与操作的表单提交上下文 */
        private FormSubmissionExecutionContext context(
                        String traceKey,
                        String operation) {
                return new FormSubmissionExecutionContext(
                                traceKey,
                                operation,
                                Map.of());
        }

        private EntityMutationResult mutationResult(
                        String id,
                        EntityMutationOperationType operationType,
                        Map<String, Object> data) {
                return new EntityMutationResult(
                                "operation-1",
                                "asset",
                                id,
                                operationType,
                                Map.of(
                                                "id",
                                                id,
                                                "entityCode",
                                                "asset",
                                                "data",
                                                data),
                                null,
                                null,
                                true,
                                false);
        }
}
