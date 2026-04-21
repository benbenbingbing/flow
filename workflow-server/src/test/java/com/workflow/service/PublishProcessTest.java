package com.workflow.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class PublishProcessTest {

    @Test
    public void testFullPublishFlow() {
        // 模拟前端保存的 BPMN XML（包含 camunda:assignee）
        String inputBpmn = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:camunda=\"http://camunda.org/schema/1.0/bpmn\" xmlns:flowable=\"http://flowable.org/bpmn\" id=\"Definitions_1\" targetNamespace=\"http://flowable.org/bpmn20\">\n" +
            "  <process id=\"test-process\" name=\"测试流程\" isExecutable=\"true\">\n" +
            "    <startEvent id=\"StartEvent_1\" name=\"开始\">\n" +
            "      <outgoing>Flow_1</outgoing>\n" +
            "    </startEvent>\n" +
            "    <userTask id=\"UserTask_1\" name=\"任务1\" camunda:assignee=\"admin\">\n" +
            "      <incoming>Flow_1</incoming>\n" +
            "      <outgoing>Flow_2</outgoing>\n" +
            "    </userTask>\n" +
            "    <userTask id=\"UserTask_2\" name=\"任务2\" camunda:assignee=\"admin\">\n" +
            "      <incoming>Flow_2</incoming>\n" +
            "      <outgoing>Flow_3</outgoing>\n" +
            "    </userTask>\n" +
            "    <endEvent id=\"EndEvent_1\" name=\"结束\">\n" +
            "      <incoming>Flow_3</incoming>\n" +
            "    </endEvent>\n" +
            "    <sequenceFlow id=\"Flow_1\" sourceRef=\"StartEvent_1\" targetRef=\"UserTask_1\"/>\n" +
            "    <sequenceFlow id=\"Flow_2\" sourceRef=\"UserTask_1\" targetRef=\"UserTask_2\"/>\n" +
            "    <sequenceFlow id=\"Flow_3\" sourceRef=\"UserTask_2\" targetRef=\"EndEvent_1\"/>\n" +
            "  </process>\n" +
            "</definitions>";
        
        // 步骤1：执行 BPMN XML 清理（publish 方法中的逻辑）
        String cleanedBpmn = cleanupBpmnXml(inputBpmn);
        
        System.out.println("=== 清理后的 BPMN XML ===");
        System.out.println(cleanedBpmn);
        
        // 验证1：没有重复的 flowable:assignee
        int assigneeCount = countOccurrences(cleanedBpmn, "flowable:assignee");
        System.out.println("flowable:assignee 出现次数: " + assigneeCount);
        assertEquals(2, assigneeCount, "应该只有2个 flowable:assignee");
        
        // 验证2：没有 camunda:assignee
        assertFalse(cleanedBpmn.contains("camunda:assignee"), "应该没有 camunda:assignee");
        
        // 步骤2：解析执行人配置（parseAndSaveNodeConfigs 中的逻辑）
        java.util.List<String> assignees = extractAssignees(cleanedBpmn);
        System.out.println("=== 提取的执行人 ===");
        assignees.forEach(a -> System.out.println("  - " + a));
        
        // 验证3：提取到2个执行人
        assertEquals(2, assignees.size(), "应该提取到2个执行人");
        assertEquals("admin", assignees.get(0), "第一个执行人应该是 admin");
        assertEquals("admin", assignees.get(1), "第二个执行人应该是 admin");
        
        System.out.println("\n✅ 测试通过！");
    }
    
    /**
     * 模拟 ProcessDefinitionService.cleanupBpmnXml 方法
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
        
        // 5. 转换那些还没有flowable:前缀的属性
        bpmnXml = bpmnXml.replaceAll("(?<!flowable:)candidateGroups=\"([^\"]*)\"", "flowable:candidateGroups=\"$1\"");
        bpmnXml = bpmnXml.replaceAll("(?<!flowable:)candidateUsers=\"([^\"]*)\"", "flowable:candidateUsers=\"$1\"");
        bpmnXml = bpmnXml.replaceAll("(?<!flowable:)\\sassignee=\"([^\"]*)\"", " flowable:assignee=\"$1\"");
        
        return bpmnXml;
    }
    
    /**
     * 模拟 parseAndSaveAssigneeConfigs 方法提取执行人
     */
    private java.util.List<String> extractAssignees(String bpmnXml) {
        java.util.List<String> assignees = new java.util.ArrayList<>();
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "flowable:assignee=\"([^\"]+)\"");
        java.util.regex.Matcher matcher = pattern.matcher(bpmnXml);
        while (matcher.find()) {
            assignees.add(matcher.group(1));
        }
        return assignees;
    }
    
    private int countOccurrences(String str, String subStr) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(subStr, index)) != -1) {
            count++;
            index++;
        }
        return count;
    }
}
