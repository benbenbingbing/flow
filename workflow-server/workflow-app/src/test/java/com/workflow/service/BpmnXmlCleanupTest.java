package com.workflow.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * BPMN XML 清理测试。
 *
 * <p>被测逻辑：流程发布时对 BPMN XML 中重复 camunda/flowable 属性的清理与转换，
 * 以本地复现方法模拟实际清理逻辑。
 */
public class BpmnXmlCleanupTest {

    /** 测试清理重复的 assignee 属性：验证 camunda:assignee 被移除且 flowable:assignee 仅保留每任务一个 */
    @Test
    public void testDuplicateAssigneeCleanup() {
        // 测试数据：同时有 camunda:assignee 和 flowable:assignee
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\" xmlns:flowable=\"http://flowable.org/bpmn\">\n" +
            "  <process id=\"test\" name=\"测试流程\" isExecutable=\"true\">\n" +
            "    <userTask id=\"UserTask_1\" name=\"任务1\" camunda:assignee=\"admin\" flowable:assignee=\"admin\">\n" +
            "      <incoming>Flow_1</incoming>\n" +
            "      <outgoing>Flow_2</outgoing>\n" +
            "    </userTask>\n" +
            "    <userTask id=\"UserTask_2\" name=\"任务2\" flowable:assignee=\"admin\" camunda:assignee=\"admin\">\n" +
            "      <incoming>Flow_2</incoming>\n" +
            "      <outgoing>Flow_3</outgoing>\n" +
            "    </userTask>\n" +
            "  </process>\n" +
            "</definitions>";
        
        // 执行转换
        String result = cleanupBpmnXml(input);
        
        // 验证：不应该有重复的 flowable:assignee
        assertFalse(result.contains("camunda:assignee"), "应该移除 camunda:assignee");
        
        // 统计 flowable:assignee 出现次数
        int count = 0;
        int index = 0;
        while ((index = result.indexOf("flowable:assignee", index)) != -1) {
            count++;
            index++;
        }
        assertEquals(2, count, "应该只有2个 flowable:assignee（每个任务1个）");
        
        System.out.println("转换后的XML：");
        System.out.println(result);
    }
    
    /** 测试仅有 camunda:assignee 时转换为 flowable:assignee：验证转换后含 flowable:assignee 且无 camunda:assignee */
    @Test
    public void testOnlyCamundaAssignee() {
        // 测试数据：只有 camunda:assignee，没有 flowable:assignee
        String input = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\">\n" +
            "  <process id=\"test\" name=\"测试流程\" isExecutable=\"true\">\n" +
            "    <userTask id=\"UserTask_1\" name=\"任务1\" camunda:assignee=\"admin\">\n" +
            "      <incoming>Flow_1</incoming>\n" +
            "      <outgoing>Flow_2</outgoing>\n" +
            "    </userTask>\n" +
            "  </process>\n" +
            "</definitions>";
        
        String result = cleanupBpmnXml(input);
        
        // 验证：应该转换为 flowable:assignee
        assertTrue(result.contains("flowable:assignee=\"admin\""), "应该转换为 flowable:assignee");
        assertFalse(result.contains("camunda:assignee"), "应该移除 camunda:assignee");
        
        System.out.println("转换后的XML（只有camunda）：");
        System.out.println(result);
    }
    
    /**
     * 模拟 ProcessDefinitionService 中的 BPMN XML 清理逻辑
     */
    private String cleanupBpmnXml(String bpmnXml) {
        // 0. 先处理重复属性：如果同时有 flowable:xxx 和 camunda:xxx，删除 camunda:xxx
        // 处理 assignee
        bpmnXml = bpmnXml.replaceAll(
            "(<userTask[^>]*?flowable:assignee=\"[^\"]*\"[^>]*?)\\s+camunda:assignee=\"[^\"]*\"",
            "$1");
        bpmnXml = bpmnXml.replaceAll(
            "(<userTask[^>]*?)\\s+camunda:assignee=\"[^\"]*\"([^>]*?flowable:assignee=\"[^\"]*\"[^>]*)",
            "$1$2");
        // 处理 candidateGroups
        bpmnXml = bpmnXml.replaceAll(
            "(<userTask[^>]*?flowable:candidateGroups=\"[^\"]*\"[^>]*?)\\s+camunda:candidateGroups=\"[^\"]*\"",
            "$1");
        bpmnXml = bpmnXml.replaceAll(
            "(<userTask[^>]*?)\\s+camunda:candidateGroups=\"[^\"]*\"([^>]*?flowable:candidateGroups=\"[^\"]*\"[^>]*)",
            "$1$2");
        // 处理 candidateUsers
        bpmnXml = bpmnXml.replaceAll(
            "(<userTask[^>]*?flowable:candidateUsers=\"[^\"]*\"[^>]*?)\\s+camunda:candidateUsers=\"[^\"]*\"",
            "$1");
        bpmnXml = bpmnXml.replaceAll(
            "(<userTask[^>]*?)\\s+camunda:candidateUsers=\"[^\"]*\"([^>]*?flowable:candidateUsers=\"[^\"]*\"[^>]*)",
            "$1$2");
        
        // 1. 转换 camunda 属性为 flowable
        bpmnXml = bpmnXml.replaceAll("camunda:candidateGroups=\"([^\"]*)\"", "flowable:candidateGroups=\"$1\"");
        bpmnXml = bpmnXml.replaceAll("camunda:candidateUsers=\"([^\"]*)\"", "flowable:candidateUsers=\"$1\"");
        bpmnXml = bpmnXml.replaceAll("camunda:assignee=\"([^\"]*)\"", "flowable:assignee=\"$1\"");
        
        // 2. 移除 camunda 命名空间声明
        bpmnXml = bpmnXml.replaceAll("\\s+xmlns:camunda=\"[^\"]*\"", "");
        
        // 3. 移除 camunda 元素
        bpmnXml = bpmnXml.replaceAll("<camunda:[^>]*>[\\s\\S]*?</camunda:[^>]*>", "");
        
        // 4. 移除剩余的 camunda 属性
        bpmnXml = bpmnXml.replaceAll("\\s+camunda:[^=\\s]*=\"[^\"]*\"", "");
        
        return bpmnXml;
    }
}
