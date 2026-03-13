package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

/**
 * 数据修复控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/fix")
@RequiredArgsConstructor
public class DataFixController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 修复全流程测试流程数据
     */
    @PostMapping("/full-test-process")
    public Result<String> fixFullTestProcess() {
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
            
            // 读取SQL文件
            ClassPathResource resource = new ClassPathResource("db/migration/V8__create_full_test_process.sql");
            String sql = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            
            // 按分号分割SQL语句并执行
            String[] statements = sql.split(";");
            for (String statement : statements) {
                String trimmed = statement.trim();
                if (!trimmed.isEmpty() && 
                    !trimmed.startsWith("--") && 
                    !trimmed.startsWith("/*") &&
                    !trimmed.toUpperCase().startsWith("SET NAMES") &&
                    !trimmed.toUpperCase().startsWith("SET CHARACTER")) {
                    try {
                        jdbcTemplate.execute(trimmed);
                        log.debug("执行SQL: {}", trimmed.substring(0, Math.min(50, trimmed.length())));
                    } catch (Exception e) {
                        log.warn("执行SQL语句失败: {}, 错误: {}", trimmed.substring(0, Math.min(50, trimmed.length())), e.getMessage());
                    }
                }
            }
            
            log.info("全流程测试流程数据修复完成");
            return Result.success("全流程测试流程数据修复完成");
        } catch (Exception e) {
            log.error("修复全流程测试流程数据失败", e);
            return Result.error("修复失败: " + e.getMessage());
        }
    }
}
