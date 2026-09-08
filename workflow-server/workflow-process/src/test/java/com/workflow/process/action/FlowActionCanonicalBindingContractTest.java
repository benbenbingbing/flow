package com.workflow.process.action;

import com.workflow.contracts.action.FlowActionContext;
import com.workflow.process.action.api.request.FlowActionSaveRequest;
import com.workflow.process.action.application.FlowActionExecutor;
import com.workflow.process.action.infrastructure.persistence.mapper.FlowActionMapper;
import com.workflow.process.action.infrastructure.persistence.record.FlowAction;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证流程动作只使用 scopeType 与 elementId 表达 BPMN 绑定关系。 */
class FlowActionCanonicalBindingContractTest {

    @Test
    void modelsDoNotExposeRemovedCompatibilityFields() {
        for (Class<?> modelType : Set.of(
                FlowAction.class,
                FlowActionSaveRequest.class,
                FlowActionContext.class)) {
            Set<String> fields = Arrays.stream(modelType.getDeclaredFields())
                    .map(Field::getName)
                    .collect(Collectors.toSet());

            assertTrue(fields.contains("elementId"),
                    () -> modelType.getSimpleName() + " 必须暴露 elementId");
            assertFalse(fields.contains("sequenceFlowId"),
                    () -> modelType.getSimpleName() + " 不得继续暴露 sequenceFlowId");
            assertFalse(fields.contains("methodName"),
                    () -> modelType.getSimpleName() + " 不得继续暴露 methodName");
        }
    }

    @Test
    @SuppressWarnings("removal")
    void legacyContextAccessorsDeriveCanonicalBindingWithoutDuplicateField() {
        FlowActionContext context = new FlowActionContext();

        context.setSequenceFlowId("Flow_legacy");
        assertEquals("SEQUENCE_FLOW", context.getScopeType());
        assertEquals("Flow_legacy", context.getElementId());
        assertEquals("Flow_legacy", context.getSequenceFlowId());

        context.setSequenceFlowId("__PROCESS__");
        assertEquals("PROCESS", context.getScopeType());
        assertNull(context.getElementId());
        assertEquals("__PROCESS__", context.getSequenceFlowId());
    }

    @Test
    void mapperAndExecutorDoNotExposeLegacySequenceFlowEntryPoints() {
        assertFalse(Arrays.stream(FlowActionMapper.class.getDeclaredMethods())
                        .map(Method::getName)
                        .anyMatch(name -> name.contains("SequenceFlowId")),
                "Mapper 不得继续提供按 sequenceFlowId 查询的入口");
        assertFalse(Arrays.stream(FlowActionExecutor.class.getDeclaredMethods())
                        .map(Method::getName)
                        .anyMatch("executeActions"::equals),
                "执行器不得继续提供旧顺序流监听器入口");

        for (Method method : FlowActionMapper.class.getDeclaredMethods()) {
            Select select = method.getAnnotation(Select.class);
            if (select == null) {
                continue;
            }
            String sql = String.join(" ", select.value())
                    .toLowerCase(Locale.ROOT);
            assertFalse(sql.contains("sequence_flow_id"),
                    () -> method.getName() + " 不得查询已删除的 sequence_flow_id");
            assertFalse(sql.contains("method_name"),
                    () -> method.getName() + " 不得查询已删除的 method_name");
        }
    }
}
