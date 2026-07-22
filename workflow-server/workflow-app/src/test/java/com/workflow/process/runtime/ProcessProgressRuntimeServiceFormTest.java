package com.workflow.process.runtime;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BPMN 表单绑定解析测试。
 */
public class ProcessProgressRuntimeServiceFormTest {

    private static final String TEST_BPMN_XML = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
        "<bpmn:definitions xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\" id=\"Definitions_1\" targetNamespace=\"http://bpmn.io/schema/bpmn\">\n" +
        "  <bpmn:process id=\"projectinit\" isExecutable=\"true\">\n" +
        "    <bpmn:userTask id=\"Activity_0c9s28z\" name=\"管理员新增\" flowable:assignee=\"admin\">\n" +
        "      <bpmn:extensionElements>\n" +
        "        <flowable:properties>\n" +
        "          <flowable:property name=\"entityFormId\" value=\"2049423744157384706\" />\n" +
        "        </flowable:properties>\n" +
        "      </bpmn:extensionElements>\n" +
        "    </bpmn:userTask>\n" +
        "    <bpmn:userTask id=\"Activity_1stkhyf\" name=\"第二个节点\" flowable:assignee=\"admin\">\n" +
        "      <bpmn:extensionElements>\n" +
        "        <flowable:properties>\n" +
        "          <flowable:property name=\"entityFormId\" value=\"2049424188225126402\" />\n" +
        "          <flowable:property name=\"entityFormReadonly\" value=\"false\" />\n" +
        "        </flowable:properties>\n" +
        "      </bpmn:extensionElements>\n" +
        "    </bpmn:userTask>\n" +
        "  </bpmn:process>\n" +
        "</bpmn:definitions>";

    @Test
    void testResolveFormKeyFromBpmnForSecondNode() throws Exception {
        ProcessProgressRuntimeService service = service();
        Method method = ProcessProgressRuntimeService.class.getDeclaredMethod("resolveFormKeyFromBpmn", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, "Activity_1stkhyf", TEST_BPMN_XML);
        assertNotNull(result, "应该解析到 entityFormId");
        assertEquals("2049424188225126402", result, "第二个节点的 entityFormId 应该匹配");
    }

    @Test
    void testResolveFormKeyFromBpmnForFirstNode() throws Exception {
        ProcessProgressRuntimeService service = service();
        Method method = ProcessProgressRuntimeService.class.getDeclaredMethod("resolveFormKeyFromBpmn", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, "Activity_0c9s28z", TEST_BPMN_XML);
        assertNotNull(result, "应该解析到 entityFormId");
        assertEquals("2049423744157384706", result, "第一个节点的 entityFormId 应该匹配");
    }

    @Test
    void testResolveFormKeyFromBpmnForNonExistentNode() throws Exception {
        ProcessProgressRuntimeService service = service();
        Method method = ProcessProgressRuntimeService.class.getDeclaredMethod("resolveFormKeyFromBpmn", String.class, String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(service, "NonExistent", TEST_BPMN_XML);
        assertNull(result, "不存在的节点应该返回 null");
    }

    private ProcessProgressRuntimeService service() {
        return new ProcessProgressRuntimeService(null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
