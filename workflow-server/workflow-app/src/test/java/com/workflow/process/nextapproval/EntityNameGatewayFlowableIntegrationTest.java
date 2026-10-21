package com.workflow.process.nextapproval;

import com.workflow.contracts.entity.model.EntityRecordData;
import com.workflow.contracts.entity.port.EntityRecordQueryPort;
import com.workflow.process.instance.application.ProcessEntityVariableService;
import com.workflow.process.task.infrastructure.flowable.FlowableConditionEvaluator;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.ProcessEngine;
import org.flowable.engine.ProcessEngineConfiguration;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 真实引擎回归：旧实例缺少系统字段时，预览补齐与正式审批选择同一条网关分支。 */
class EntityNameGatewayFlowableIntegrationTest {

    @Test
    void repairsMissingEntityNameForEqualNotEqualAndNullWithoutWeakeningExpressionChecks() {
        ProcessEngine engine = ProcessEngineConfiguration.createStandaloneInMemProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:entity_name_gateway_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE)
                .setAsyncExecutorActivate(false)
                .buildProcessEngine();
        try {
            engine.getRepositoryService().createDeployment()
                    .addString("entity-name-gateway.bpmn20.xml", bpmn()).deploy();
            FlowableConditionEvaluator evaluator = new FlowableConditionEvaluator(engine);
            for (String name : new String[]{"1", "2", null}) {
                assertLegacyInstanceCanContinue(engine, evaluator, name);
            }
        } finally {
            engine.close();
        }
    }

    private void assertLegacyInstanceCanContinue(ProcessEngine engine, FlowableConditionEvaluator evaluator,
            String name) {
        ProcessInstance instance = engine.getRuntimeService().startProcessInstanceByKey("entity_name_gateway",
                Map.of("entityCode", "expense", "entityDataId", "record-1"));
        Task current = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();

        // 复现截图中的失败：实体记录存在，但旧流程只携带自定义字段，UEL 找不到 name。
        FlowableException failure = assertThrows(FlowableException.class,
                () -> engine.getTaskService().complete(current.getId()));
        assertTrue(failure.getMessage().contains("Unknown property"), failure.getMessage());
        assertNotNull(engine.getTaskService().createTaskQuery().taskId(current.getId()).singleResult(),
                "网关求值失败应回滚当前审批，仍可修复后重试");

        EntityRecordData record = new EntityRecordData();
        record.setName(name);
        record.setData(Map.of());
        EntityRecordQueryPort records = mock(EntityRecordQueryPort.class);
        when(records.findById("expense", "record-1")).thenReturn(record);
        Map<String, Object> preview = new LinkedHashMap<>(engine.getRuntimeService().getVariables(instance.getId()));
        Map<String, Object> missing = new ProcessEntityVariableService(records).missingRecordVariables(preview);
        preview.putAll(missing);

        boolean equalsOne = evaluator.evaluate("${name == '1'}", preview);
        boolean notEqualsOne = evaluator.evaluate("${name != '1'}", preview);
        assertEquals("1".equals(name), equalsOne);
        assertEquals(!"1".equals(name), notEqualsOne);
        assertFalse(engine.getRuntimeService().hasVariable(instance.getId(), "name"),
                "读取实体并计算预览不得写入流程变量");
        assertThrows(FlowableConditionEvaluator.UnsafePreviewExpressionException.class,
                () -> evaluator.evaluate("${misspelledName == '1'}", preview));

        engine.getRuntimeService().setVariables(instance.getId(), missing);
        assertTrue(engine.getRuntimeService().hasVariable(instance.getId(), "name"),
                "null 名称也必须是已声明的变量");
        engine.getTaskService().complete(current.getId());
        Task next = engine.getTaskService().createTaskQuery().processInstanceId(instance.getId()).singleResult();
        assertNotNull(next);
        assertEquals(equalsOne ? "equal-review" : "not-equal-review", next.getTaskDefinitionKey());
    }

    private String bpmn() {
        return """
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" targetNamespace="http://workflow.test/process">
                  <bpmn:process id="entity_name_gateway" isExecutable="true">
                    <bpmn:startEvent id="start" />
                    <bpmn:userTask id="current-review" name="当前审批" />
                    <bpmn:exclusiveGateway id="route" />
                    <bpmn:userTask id="equal-review" name="名称等于 1" />
                    <bpmn:userTask id="not-equal-review" name="名称不等于 1" />
                    <bpmn:endEvent id="end" />
                    <bpmn:sequenceFlow id="to-current" sourceRef="start" targetRef="current-review" />
                    <bpmn:sequenceFlow id="to-route" sourceRef="current-review" targetRef="route" />
                    <bpmn:sequenceFlow id="to-equal" sourceRef="route" targetRef="equal-review">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${name == '1'}</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="to-not-equal" sourceRef="route" targetRef="not-equal-review">
                      <bpmn:conditionExpression xsi:type="bpmn:tFormalExpression">${name != '1'}</bpmn:conditionExpression>
                    </bpmn:sequenceFlow>
                    <bpmn:sequenceFlow id="equal-to-end" sourceRef="equal-review" targetRef="end" />
                    <bpmn:sequenceFlow id="not-equal-to-end" sourceRef="not-equal-review" targetRef="end" />
                  </bpmn:process>
                </bpmn:definitions>
                """;
    }
}
