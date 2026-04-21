package com.workflow.config;

import com.workflow.service.ProcessDefinitionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Slf4j
// @Component  // 全流程测试完成，已禁用
@RequiredArgsConstructor
public class FullTestRunner implements CommandLineRunner {
    private final ProcessDefinitionService processService;
    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        Thread.sleep(6000);
        runFullTest();
    }

    private void runFullTest() {
        log.info("========== 全流程测试开始 ==========");
        String processId = "26";
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        
        // 清除数据
        jdbc.update("DELETE FROM assignee_config WHERE node_config_id IN (SELECT id FROM node_config WHERE process_config_id = ?)", processId);
        jdbc.update("DELETE FROM form_config WHERE node_config_id IN (SELECT id FROM node_config WHERE process_config_id = ?)", processId);
        jdbc.update("DELETE FROM node_config WHERE process_config_id = ?", processId);
        
        // 解析节点
        try {
            processService.testParseNodes(processId);
        } catch (Exception e) {
            log.error("解析失败", e);
            return;
        }
        
        // 验证结果
        verifyResults(jdbc, processId);
        log.info("========== 全流程测试结束 ==========");
    }
    
    private void verifyResults(JdbcTemplate jdbc, String processId) {
        // 1. 节点数量
        Integer nodeCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM node_config WHERE process_config_id = ?", 
            Integer.class, processId);
        log.info("节点数量: {}", nodeCount);
        
        // 2. 各类型节点数量
        List<Map<String, Object>> typeStats = jdbc.queryForList(
            "SELECT node_type, COUNT(*) as cnt FROM node_config WHERE process_config_id = ? GROUP BY node_type",
            processId);
        log.info("节点类型分布:");
        for (Map<String, Object> row : typeStats) {
            log.info("  - {}: {}", row.get("node_type"), row.get("cnt"));
        }
        
        // 3. 执行人配置
        Integer assigneeCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assignee_config ac JOIN node_config nc ON ac.node_config_id = nc.id WHERE nc.process_config_id = ?",
            Integer.class, processId);
        log.info("执行人配置数量: {}", assigneeCount);
        
        // 4. 表单配置
        Integer formCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM form_config fc JOIN node_config nc ON fc.node_config_id = nc.id WHERE nc.process_config_id = ?",
            Integer.class, processId);
        log.info("表单配置数量: {}", formCount);
        
        // 5. 跳过节点配置
        Integer skipCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM node_config WHERE process_config_id = ? AND skip_node = 1",
            Integer.class, processId);
        log.info("跳过节点数量: {}", skipCount);
        
        // 6. 多实例配置（从config_json中检查）
        List<Map<String, Object>> multiInstance = jdbc.queryForList(
            "SELECT node_id, node_name FROM node_config WHERE process_config_id = ? AND config_json LIKE '%multiInstance%'",
            processId);
        if (!multiInstance.isEmpty()) {
            log.info("多实例节点:");
            for (Map<String, Object> row : multiInstance) {
                log.info("  - {}: {}", row.get("node_id"), row.get("node_name"));
            }
        }
        
        // 7. 默认流配置
        List<Map<String, Object>> defaultFlows = jdbc.queryForList(
            "SELECT node_id, node_name, config_json FROM node_config WHERE process_config_id = ? AND config_json LIKE '%defaultFlow%'",
            processId);
        if (!defaultFlows.isEmpty()) {
            log.info("默认流配置:");
            for (Map<String, Object> row : defaultFlows) {
                log.info("  - {}: {}", row.get("node_id"), row.get("config_json"));
            }
        }
        
        // 8. 详细执行人配置
        List<Map<String, Object>> assignees = jdbc.queryForList(
            "SELECT nc.node_id, nc.node_name, ac.assignee_type, ac.assignee_value " +
            "FROM assignee_config ac JOIN node_config nc ON ac.node_config_id = nc.id " +
            "WHERE nc.process_config_id = ? ORDER BY nc.node_id, ac.priority",
            processId);
        if (!assignees.isEmpty()) {
            log.info("执行人配置详情:");
            for (Map<String, Object> row : assignees) {
                log.info("  - {} ({}): {} = {}", 
                    row.get("node_id"), row.get("node_name"), 
                    row.get("assignee_type"), row.get("assignee_value"));
            }
        }
        
        // 9. 表单配置详情
        List<Map<String, Object>> forms = jdbc.queryForList(
            "SELECT nc.node_id, nc.node_name, fc.form_key, fc.is_readonly " +
            "FROM form_config fc JOIN node_config nc ON fc.node_config_id = nc.id " +
            "WHERE nc.process_config_id = ?",
            processId);
        if (!forms.isEmpty()) {
            log.info("表单配置详情:");
            for (Map<String, Object> row : forms) {
                log.info("  - {} ({}): formKey={}, readonly={}", 
                    row.get("node_id"), row.get("node_name"), 
                    row.get("form_key"), row.get("is_readonly"));
            }
        }
        
        // 测试报告
        log.info("========== 测试报告 ==========");
        // 节点数量>=15，执行人配置>=3（会签节点不需要执行人配置）
        boolean pass = nodeCount >= 15 && assigneeCount >= 3;
        if (pass) {
            log.info("✅ 测试通过！");
            log.info("  - 节点数量: {} (预期>=15)", nodeCount);
            log.info("  - 执行人配置: {} (预期>=3)", assigneeCount);
            log.info("  - 表单配置: {}", formCount);
            log.info("  - 跳过节点: {}", skipCount);
            log.info("  - 多实例节点: {}", multiInstance.size());
            log.info("  - 默认流配置: {}", defaultFlows.size());
        } else {
            log.error("❌ 测试失败！");
            log.error("  - 节点数量: {} (预期>=15)", nodeCount);
            log.error("  - 执行人配置: {} (预期>=3)", assigneeCount);
        }
    }
}
