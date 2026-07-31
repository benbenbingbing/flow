package com.workflow.service;

import com.workflow.entity.data.application.EntityDataActionService;
import com.workflow.entity.data.application.EntityDataDynamicService;
import com.workflow.entity.data.application.SystemEntityReadService;
import com.workflow.entity.definition.infrastructure.persistence.record.EntityDefinition;
import com.workflow.entity.form.application.FormSubmissionExecutionContext;
import com.workflow.entity.form.application.FormSubmissionTraceService;
import com.workflow.entity.form.application.PublishedFormSubmissionService;
import com.workflow.entity.definition.infrastructure.persistence.mapper.EntityDefinitionMapper;
import com.workflow.entity.form.infrastructure.persistence.mapper.EntityFormMapper;
import com.workflow.entity.ui.application.UiEventRuntimeService;

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
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
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
        private EntityDefinitionMapper definitionMapper;

        @Mock
        private EntityFormMapper formMapper;

        @Spy
        private ObjectMapper objectMapper = new ObjectMapper();

        @InjectMocks
        private EntityDataActionService service;

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
                verify(capabilityService).requireRowAction(
                                "asset", "default", "view", row);
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
                when(capabilityService.evaluateRowAction("asset", null, "batchDelete", allowed))
                                .thenReturn(EntityActionCapabilityDTO.allowed());
                when(capabilityService.evaluateRowAction("asset", null, "batchDelete", denied))
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
                when(capabilityService.evaluateRowAction("asset", null, "batchDelete", first))
                                .thenReturn(EntityActionCapabilityDTO.allowed());
                when(capabilityService.evaluateRowAction("asset", null, "batchDelete", second))
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
                when(formSubmissionService.applyDefaultForm(
                                "asset",
                                null,
                                "create",
                                dto.getData(),
                                context)).thenReturn(
                                                Map.of(
                                                                "name",
                                                                "Laptop",
                                                                "normalized",
                                                                true));
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
                                .applyDefaultForm(
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

        /**
         * 测试更新数据时服务端 beforeSubmit 钩子恰好执行一次：
         * 验证默认表单处理与统一更新命令各执行一次，且载荷为规范化后的数据。
         */
        @Test
        void updateExecutesServerBeforeSubmitExactlyOnce() {
                EntityDataDTO existing = row("1", "A-1");
                when(dynamicService.findAccessibleById(
                                "asset",
                                "1",
                                null)).thenReturn(existing);
                FormSubmissionExecutionContext context = context("update-trace", "ENTITY_UPDATE");
                when(formSubmissionTraceService.current(
                                eq("ENTITY_UPDATE"),
                                isNull(),
                                anyMap())).thenReturn(context);
                when(formSubmissionService.applyDefaultForm(
                                "asset",
                                "1",
                                "edit",
                                Map.of(
                                                "name",
                                                "Laptop",
                                                "amount",
                                                12),
                                context)).thenReturn(
                                                Map.of(
                                                                "name",
                                                                "Laptop",
                                                                "normalized",
                                                                true));
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
                                .applyDefaultForm(
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

        /** 构造一条包含 id 与 dataNo 的实体数据 DTO */
        private EntityDataDTO row(String id, String dataNo) {
                EntityDataDTO row = new EntityDataDTO();
                row.setId(id);
                row.setDataNo(dataNo);
                return row;
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
