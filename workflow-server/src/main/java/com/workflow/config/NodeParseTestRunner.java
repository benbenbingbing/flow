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
// @Component  // 测试完成，已禁用
@RequiredArgsConstructor
public class NodeParseTestRunner implements CommandLineRunner {
    private final ProcessDefinitionService processService;
    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        // 延迟执行，等待服务完全启动
        Thread.sleep(5000);
        testNodeParse();
    }

    private void testNodeParse() {
        log.info("========== 节点解析测试（修复后） ==========");
        String processId = "26"; // 全流程测试
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        
        // 先清除现有数据
        jdbc.update("DELETE FROM assignee_config WHERE node_config_id IN (SELECT id FROM node_config WHERE process_config_id = ?)", processId);
        jdbc.update("DELETE FROM form_config WHERE node_config_id IN (SELECT id FROM node_config WHERE process_config_id = ?)", processId);
        jdbc.update("DELETE FROM node_config WHERE process_config_id = ?", processId);
        log.info("已清除流程 {} 的现有节点数据", processId);
        
        try {
            processService.testParseNodes(processId);
            log.info("解析执行完成");
        } catch (Exception e) {
            log.error("解析失败: {}", e.getMessage(), e);
            return;
        }
        
        // 验证节点数量
        Integer nodeCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM node_config WHERE process_config_id = ?", 
            Integer.class, processId);
        log.info("节点数量: {}", nodeCount);
        
        // 验证执行人配置
        Integer assigneeCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM assignee_config ac JOIN node_config nc ON ac.node_config_id = nc.id WHERE nc.process_config_id = ?",
            Integer.class, processId);
        log.info("执行人配置数量: {}", assigneeCount);
        
        // 验证表单配置
        Integer formCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM form_config fc JOIN node_config nc ON fc.node_config_id = nc.id WHERE nc.process_config_id = ?",
            Integer.class, processId);
        log.info("表单配置数量: {}", formCount);
        
        // 详细节点列表
        List<Map<String, Object>> nodes = jdbc.queryForList(
            "SELECT node_id, node_name, node_type, skip_node FROM node_config WHERE process_config_id = ? ORDER BY created_at",
            processId);
        log.info("节点列表:");
        for (Map<String, Object> node : nodes) {
            log.info("  - {}: {} ({}) skip={}", 
                node.get("node_id"), node.get("node_name"), node.get("node_type"), node.get("skip_node"));
        }
        
        // 详细执行人配置
        List<Map<String, Object>> assignees = jdbc.queryForList(
            "SELECT nc.node_id, nc.node_name, ac.assignee_type, ac.assignee_value " +
            "FROM assignee_config ac JOIN node_config nc ON ac.node_config_id = nc.id " +
            "WHERE nc.process_config_id = ? ORDER BY nc.node_id, ac.priority",
            processId);
        if (!assignees.isEmpty()) {
            log.info("执行人配置列表:");
            for (Map<String, Object> a : assignees) {
                log.info("  - {} ({}): {} = {}", 
                    a.get("node_id"), a.get("node_name"), a.get("assignee_type"), a.get("assignee_value"));
            }
        }
        
        // 测试结果判断
        boolean pass = nodeCount > 0 && assigneeCount > 0;
        if (pass) {
            log.info("========== 测试通过！节点={}, 执行人配置={} ==========", nodeCount, assigneeCount);
        } else {
            log.error("========== 测试失败！节点={}, 执行人配置={} ==========", nodeCount, assigneeCount);
        }
    }
}
