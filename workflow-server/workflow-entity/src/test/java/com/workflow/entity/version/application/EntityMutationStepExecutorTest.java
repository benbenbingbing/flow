package com.workflow.entity.version.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationPhase;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.core.error.BusinessConflictException;
import com.workflow.entity.mutationpolicy.application.EntityMutationPolicyService;
import com.workflow.entity.mutationpolicy.application.model.EntityMutationPolicyDocument;
import com.workflow.entity.ui.api.request.UiDataSourceExecuteRequest;
import com.workflow.entity.ui.application.UiDataSourceService;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityMutationStepExecutorTest {

    @Mock
    private EntityMutationPolicyService mutationPolicyService;
    @Mock
    private EntityVersionPolicyMatcher policyMatcher;
    @Mock
    private EntityMutationBuiltInRuleExecutor builtInRuleExecutor;
    @Mock
    private UiDataSourceService dataSourceService;

    private EntityMutationStepExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new EntityMutationStepExecutor(
                mutationPolicyService,
                policyMatcher,
                builtInRuleExecutor,
                dataSourceService,
                List.of(),
                new ObjectMapper());
        when(mutationPolicyService.getPublished("asset"))
                .thenReturn(Optional.of(configuration()));
    }

    /** 外部接口不能把任意实体和记录加入当前用户已经授权的变更。 */
    @Test
    void managedInterfaceCannotReturnCrossEntityMutationPlan() {
        when(dataSourceService.executeManagedMutationOperation(
                eq("managed-service"),
                eq("NORMALIZE"),
                any(UiDataSourceExecuteRequest.class)))
                .thenReturn(Map.of(
                        "decision", "MUTATION_PLAN",
                        "mutations", List.of(Map.of(
                                "entityCode", "secret_entity",
                                "recordId", "record-999",
                                "operationType", "DELETE",
                                "payload", Map.of()))));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> execute(command()));

        assertEquals(
                "ENTITY_MUTATION_MANAGED_PLAN_FORBIDDEN",
                exception.getErrorCode());
        assertEquals(
                "受管理接口暂不允许新增实体变更计划；请仅返回放行、阻止或当前命令字段补丁",
                exception.getMessage());
    }

    /** 在统一逐目标治理完成前，同记录同操作也不能成为绕过字段授权的后门。 */
    @Test
    void managedInterfaceCannotReturnSameRecordMutationPlan() {
        when(dataSourceService.executeManagedMutationOperation(
                eq("managed-service"),
                eq("NORMALIZE"),
                any(UiDataSourceExecuteRequest.class)))
                .thenReturn(Map.of(
                        "decision", "MUTATION_PLAN",
                        "mutations", List.of(Map.of(
                                "entityCode", "asset",
                                "recordId", "record-1",
                                "operationType", "UPDATE",
                                "payload", Map.of(
                                        "data", Map.of(
                                                "name", "接口改写"))))));

        BusinessConflictException exception = assertThrows(
                BusinessConflictException.class,
                () -> execute(command()));

        assertEquals(
                "ENTITY_MUTATION_MANAGED_PLAN_FORBIDDEN",
                exception.getErrorCode());
    }

    /** 合法 PATCH 仍只修改当前已授权命令，并继续进入既有写入校验链。 */
    @Test
    void managedInterfacePatchRemainsAvailable() {
        when(dataSourceService.executeManagedMutationOperation(
                eq("managed-service"),
                eq("NORMALIZE"),
                any(UiDataSourceExecuteRequest.class)))
                .thenReturn(Map.of(
                        "decision", "PATCH",
                        "patch", Map.of(
                                "data", Map.of(
                                        "name", "规范名称"))));

        EntityMutationStepExecutor.ExecutionOutcome outcome =
                execute(command());

        assertEquals(
                "规范名称",
                ((Map<?, ?>) outcome.command().payload()
                        .get("data")).get("name"));
        assertEquals(List.of(), outcome.plannedCommands());
    }

    private EntityMutationStepExecutor.ExecutionOutcome execute(
            EntityMutationCommand command) {
        return executor.execute(
                command,
                EntityMutationPhase.PREPARE,
                Map.of(),
                Map.of(),
                "ANY_SCENARIO");
    }

    private EntityMutationPolicyDocument configuration() {
        EntityVersionConfiguration.Step step =
                new EntityVersionConfiguration.Step();
        step.setStepName("规范化资产");
        step.setStepType("MANAGED_INTERFACE");
        step.setPhase("PREPARE");
        step.setProviderCode("managed-service");
        step.setConfig(Map.of("operationCode", "NORMALIZE"));

        EntityMutationPolicyDocument configuration =
                new EntityMutationPolicyDocument();
        configuration.setEntityCode("asset");
        configuration.setEnabled(true);
        configuration.setSteps(List.of(step));
        return configuration;
    }

    private EntityMutationCommand command() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", "原名称");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("data", data);
        return new EntityMutationCommand(
                "operation-1",
                "asset",
                "record-1",
                EntityMutationOperationType.UPDATE,
                payload,
                EntityMutationContext.builder(
                                EntityMutationSourceType.FORM,
                                "EDIT_RECORD",
                                "编辑实体数据")
                        .sourceRecord("asset", "record-1")
                        .operator("user-1", "张三")
                        .trace("trace-1", "mutation-1")
                        .build());
    }
}
