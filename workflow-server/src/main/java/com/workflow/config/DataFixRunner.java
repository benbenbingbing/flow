package com.workflow.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * 数据修复Runner
 * 用于修复流程数据的字符编码问题
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataFixRunner implements CommandLineRunner {

    private final JdbcTemplate jdbcTemplate;
    private final DataSource dataSource;

    @Override
    public void run(String... args) throws Exception {
        // 检查是否需要修复
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM process_definition_config WHERE process_key = 'full_test_process'",
            Integer.class
        );
        
        if (count != null && count > 0) {
            // 检查名称是否为乱码
            String name = jdbcTemplate.queryForObject(
                "SELECT process_name FROM process_definition_config WHERE process_key = 'full_test_process'",
                String.class
            );
            
            if (name == null || name.contains("?") || name.equals("?????")) {
                log.info("检测到全流程测试流程数据乱码，开始修复...");
                fixFullTestProcess();
            } else {
                log.info("全流程测试流程数据正常，无需修复");
            }
        } else {
            log.info("全流程测试流程不存在，将在Flyway迁移时自动创建");
        }
    }
    
    private void fixFullTestProcess() {
        try {
            // 删除旧数据
            jdbcTemplate.update("DELETE FROM node_config WHERE process_config_id IN (SELECT id FROM process_definition_config WHERE process_key = 'full_test_process')");
            jdbcTemplate.update("DELETE FROM process_definition_config WHERE process_key = 'full_test_process'");
            // 尝试删除Flyway历史记录（如果表存在）
            try {
                jdbcTemplate.update("DELETE FROM flyway_schema_history WHERE version = '8'");
            } catch (Exception e) {
                log.debug("flyway_schema_history表不存在或无需清理");
            }
            
            // 读取SQL文件并执行
            ClassPathResource resource = new ClassPathResource("db/migration/V8__create_full_test_process.sql");
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            // 分割并执行SQL语句
            String[] statements = sql.split(";");
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty() && !trimmed.startsWith("--") && !trimmed.startsWith("/*")) {
                    try {
                        jdbcTemplate.execute(trimmed);
                    } catch (Exception e) {
                        log.warn("执行SQL语句失败: {}", e.getMessage());
                    }
                }
            }
            
            log.info("全流程测试流程数据修复完成");
        } catch (Exception e) {
            log.error("修复全流程测试流程数据失败", e);
        }
    }
}
