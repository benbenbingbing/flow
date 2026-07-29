package com.workflow.entity.version.application;

import com.workflow.contracts.entity.mutation.EntityMutationCommand;
import com.workflow.contracts.entity.mutation.EntityMutationContext;
import com.workflow.contracts.entity.mutation.EntityMutationOperationType;
import com.workflow.contracts.entity.mutation.EntityMutationSourceType;
import com.workflow.contracts.entity.mutation.EntityMutationStepResult;
import com.workflow.entity.data.application.DynamicTableService;
import com.workflow.entity.data.infrastructure.persistence.mapper.EntityDataDynamicMapper;
import com.workflow.entity.version.application.model.EntityVersionConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EntityMutationBuiltInRuleExecutorTest {

    @Mock
    private EntityRecordVersionService versionService;
    @Mock
    private EntityVersionPolicyMatcher policyMatcher;
    @Mock
    private EntityDataDynamicMapper dynamicMapper;
    @Mock
    private DynamicTableService dynamicTableService;

    private EntityMutationBuiltInRuleExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new EntityMutationBuiltInRuleExecutor(
                versionService,
                policyMatcher,
                dynamicMapper,
                dynamicTableService);
    }

    @Test
    void requiredRuleUsesExistingValueForPartialUpdate() {
        EntityMutationStepResult result = executor.execute(
                step(
                        "REQUIRED_FIELDS",
                        Map.of("fields", List.of("name"))),
                command(
                        Map.of(
                                "data",
                                Map.of("description", "调整"))),
                Map.of(
                        "data",
                        Map.of("name", "原名称")),
                Map.of(
                        "data",
                        Map.of("description", "调整")));

        assertEquals(
                EntityMutationStepResult.Decision.ALLOW,
                result.decision());
    }

    @Test
    void uniqueRuleExcludesCurrentRecordAndBlocksDuplicate() {
        when(dynamicTableService.getTableName("asset"))
                .thenReturn("biz_asset");
        when(dynamicMapper.countByCondition(
                eq("biz_asset"),
                anyMap())).thenReturn(1L);

        EntityMutationStepResult result = executor.execute(
                step(
                        "UNIQUE",
                        Map.of(
                                "fields",
                                List.of("data.assetCode"))),
                command(
                        Map.of(
                                "data",
                                Map.of("assetCode", "A-002"))),
                Map.of(
                        "data",
                        Map.of("assetCode", "A-001")),
                Map.of(
                        "data",
                        Map.of("assetCode", "A-002")));

        assertEquals(
                EntityMutationStepResult.Decision.BLOCK,
                result.decision());
        ArgumentCaptor<Map<String, Object>> captor =
                ArgumentCaptor.forClass(Map.class);
        verify(dynamicMapper).countByCondition(
                eq("biz_asset"),
                captor.capture());
        assertEquals("A-002",
                captor.getValue().get("assetCode"));
        assertEquals("EQ",
                captor.getValue().get("assetCode_op"));
        assertEquals("record-1",
                captor.getValue().get("id"));
        assertEquals("NE",
                captor.getValue().get("id_op"));
    }

    @Test
    void dataRangeEvaluatesMergedRecord() {
        Map<String, Object> condition =
                Map.of(
                        "field", "data.deptCode",
                        "operator", "EQ",
                        "value", "D-02");
        when(policyMatcher.evaluateCondition(
                eq(condition),
                eq(command(Map.of(
                        "data",
                        Map.of("deptCode", "D-02")))),
                anyMap(),
                anyMap())).thenReturn(true);
        EntityMutationCommand command = command(
                Map.of(
                        "data",
                        Map.of("deptCode", "D-02")));

        EntityMutationStepResult result = executor.execute(
                step(
                        "DATA_RANGE",
                        Map.of("condition", condition)),
                command,
                Map.of(
                        "status", "ACTIVE",
                        "data",
                        Map.of("deptCode", "D-01")),
                command.payload());

        assertEquals(
                EntityMutationStepResult.Decision.ALLOW,
                result.decision());
        ArgumentCaptor<Map<String, Object>> effective =
                ArgumentCaptor.forClass(Map.class);
        verify(policyMatcher).evaluateCondition(
                eq(condition),
                eq(command),
                anyMap(),
                effective.capture());
        assertEquals("ACTIVE",
                effective.getValue().get("status"));
        assertEquals(
                "D-02",
                ((Map<?, ?>) effective.getValue()
                        .get("data")).get("deptCode"));
    }

    private EntityVersionConfiguration.Step step(
            String rule,
            Map<String, Object> config) {
        EntityVersionConfiguration.Step step =
                new EntityVersionConfiguration.Step();
        step.setStepName(rule);
        step.setStepType("BUILT_IN_RULE");
        step.setProviderCode(rule);
        step.setConfig(new LinkedHashMap<>(config));
        return step;
    }

    private EntityMutationCommand command(
            Map<String, Object> payload) {
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
                        .operator("user-1", "张三")
                        .trace("trace-1", "mutation-1")
                        .build());
    }
}
