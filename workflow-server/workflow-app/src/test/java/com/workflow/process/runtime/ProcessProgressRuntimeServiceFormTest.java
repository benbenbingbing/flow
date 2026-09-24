package com.workflow.process.runtime;

import com.workflow.process.instance.application.ProcessProgressRuntimeService;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 完成态表单节点选择测试。实际表单绑定由发布快照测试覆盖。
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
    void completedProcessUsesLastCompletedUserTaskInsteadOfGateway()
            throws Exception {
        ProcessProgressRuntimeService service = service();
        Method method = ProcessProgressRuntimeService.class.getDeclaredMethod(
                "resolveLastCompletedUserTaskId",
                List.class,
                String.class,
                String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(
                service,
                List.of(
                        "Start",
                        "Activity_0c9s28z",
                        "Activity_1stkhyf",
                        "Gateway_Result",
                        "End"),
                TEST_BPMN_XML,
                null);

        assertEquals(
                "Activity_1stkhyf",
                result,
                "完成态应选择最后一个用户任务作为查看表单节点");
    }

    /** 构造被测服务实例(依赖全传 null，仅测试 BPMN 解析逻辑) */
    private ProcessProgressRuntimeService service() {
        return new ProcessProgressRuntimeService(
                null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null,
                    null);
    }
}
