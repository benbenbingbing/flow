package com.workflow.config;

import com.workflow.service.ProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;

@Slf4j
// @Component  // 测试完成已禁用
@RequiredArgsConstructor
public class DuplicateAssigneeTestRunner implements CommandLineRunner {
    private final ProcessDefinitionService processService;
    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        Thread.sleep(8000);
        testDuplicateAssignee();
    }

    private void testDuplicateAssignee() {
        log.info("========== 测试重复执行人问题 ==========");
        String processId = "25";
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        
        // 先更新流程25的BPMN，创建两个有相同assignee的用户任务
        String testBpmn = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\" xmlns:flowable=\"http://flowable.org/bpmn\">\n" +
            "  <process id=\"test_duplicate_assignee\" name=\"测试重复执行人\" isExecutable=\"true\">\n" +
            "    <startEvent id=\"StartEvent_1\" name=\"开始\">\n" +
            "      <outgoing>Flow_1</outgoing>\n" +
            "    </startEvent>\n" +
            "    <userTask id=\"UserTask_1\" name=\"任务1\" flowable:assignee=\"admin\">\n" +
            "      <incoming>Flow_1</incoming>\n" +
            "      <outgoing>Flow_2</outgoing>\n" +
            "    </userTask>\n" +
            "    <userTask id=\"UserTask_2\" name=\"任务2\" flowable:assignee=\"admin\">\n" +
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
        
        try {
            // 更新BPMN
            jdbc.update("UPDATE process_definition_config SET bpmn_xml = ? WHERE id = ?", testBpmn, processId);
            log.info("已更新流程25的BPMN XML");
            
            // 清除旧数据
            jdbc.update("DELETE FROM assignee_config WHERE node_config_id IN (SELECT id FROM node_config WHERE process_config_id = ?)", processId);
            jdbc.update("DELETE FROM form_config WHERE node_config_id IN (SELECT id FROM node_config WHERE process_config_id = ?)", processId);
            jdbc.update("DELETE FROM node_config WHERE process_config_id = ?", processId);
            log.info("已清除旧数据");
            
            // 解析节点
            processService.testParseNodes(processId);
            
            // 验证结果
            Integer nodeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM node_config WHERE process_config_id = ?", 
                Integer.class, processId);
            Integer assigneeCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM assignee_config ac JOIN node_config nc ON ac.node_config_id = nc.id WHERE nc.process_config_id = ?",
                Integer.class, processId);
            
            log.info("========== 测试结果 ==========");
            log.info("节点数量: {}", nodeCount);
            log.info("执行人配置数量: {}", assigneeCount);
            
            // 检查每个节点的执行人
            jdbc.queryForList(
                "SELECT nc.node_id, nc.node_name, ac.assignee_type, ac.assignee_value " +
                "FROM assignee_config ac JOIN node_config nc ON ac.node_config_id = nc.id " +
                "WHERE nc.process_config_id = ? ORDER BY nc.node_id", processId)
                .forEach(row -> {
                    log.info("  - {} ({}): {} = {}", 
                        row.get("node_id"), row.get("node_name"), 
                        row.get("assignee_type"), row.get("assignee_value"));
                });
            
            if (nodeCount == 4 && assigneeCount == 2) {
                log.info("✅ 测试通过！两个用户任务都正确保存了执行人配置");
            } else {
                log.error("❌ 测试失败！预期: 4个节点, 2个执行人配置");
                log.error("实际: {}个节点, {}个执行人配置", nodeCount, assigneeCount);
            }
            
        } catch (Exception e) {
            log.error("测试失败", e);
        }
        log.info("========== 测试结束 ==========");
    }
}
