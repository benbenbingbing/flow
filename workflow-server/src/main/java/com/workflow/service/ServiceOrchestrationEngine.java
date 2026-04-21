package com.workflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.workflow.entity.ServiceDefinition;
import com.workflow.entity.ServiceExecutionLog;
import com.workflow.entity.ServiceNode;
import com.workflow.mapper.ServiceExecutionLogMapper;
import com.workflow.mapper.ServiceNodeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * 服务编排执行引擎
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServiceOrchestrationEngine {
    
    private final ServiceNodeMapper nodeMapper;
    private final ServiceExecutionLogMapper executionLogMapper;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();
    
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    /**
     * 执行服务编排
     */
    @Transactional(rollbackFor = Exception.class)
    public ExecutionResult execute(ServiceDefinition service, Map<String, Object> inputParams) {
        String executionId = UUID.randomUUID().toString();
        long startTime = System.currentTimeMillis();
        
        // 创建执行日志
        ServiceExecutionLog log = new ServiceExecutionLog();
        log.setServiceId(service.getId());
        log.setExecutionId(executionId);
        log.setTriggerType("MANUAL");
        log.setInputParams(toJson(inputParams));
        log.setStatus("RUNNING");
        log.setStartTime(LocalDateTime.now());
        executionLogMapper.insert(log);
        
        try {
            // 加载所有节点
            List<ServiceNode> nodes = nodeMapper.findByServiceId(service.getId());
            Map<String, ServiceNode> nodeMap = new HashMap<>();
            for (ServiceNode node : nodes) {
                nodeMap.put(node.getNodeId(), node);
            }
            
            // 查找开始节点
            ServiceNode startNode = nodes.stream()
                    .filter(n -> "START".equals(n.getNodeType()))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("未找到开始节点"));
            
            // 执行上下文
            ExecutionContext context = new ExecutionContext();
            context.setInputParams(inputParams);
            context.setVariables(new HashMap<>(inputParams));
            context.setNodeOutputs(new HashMap<>());
            context.setExecutionId(executionId);
            
            // 执行DAG
            executeNode(startNode, nodeMap, context, service.getTimeoutMs());
            
            // 更新执行日志
            long duration = System.currentTimeMillis() - startTime;
            log.setStatus("SUCCESS");
            log.setEndTime(LocalDateTime.now());
            log.setDurationMs((int) duration);
            log.setOutputResult(toJson(context.getVariables()));
            executionLogMapper.updateById(log);
            
            return ExecutionResult.success(context.getVariables(), executionId, duration);
            
        } catch (Exception e) {
            // 更新执行日志
            long duration = System.currentTimeMillis() - startTime;
            log.setStatus("FAILED");
            log.setEndTime(LocalDateTime.now());
            log.setDurationMs((int) duration);
            log.setErrorMessage(e.getMessage());
            executionLogMapper.updateById(log);
            
            return ExecutionResult.failure(e.getMessage(), executionId, duration);
        }
    }
    
    /**
     * 执行单个节点
     */
    private void executeNode(ServiceNode node, Map<String, ServiceNode> nodeMap, 
                            ExecutionContext context, Integer timeoutMs) throws Exception {
        log.info("执行节点: {} - {}", node.getNodeId(), node.getNodeType());
        
        long nodeStartTime = System.currentTimeMillis();
        Map<String, Object> nodeOutput = new HashMap<>();
        
        try {
            switch (node.getNodeType()) {
                case "START":
                    // 开始节点，直接传递输入参数
                    nodeOutput.putAll(context.getInputParams());
                    break;
                    
                case "END":
                    // 结束节点，无需处理
                    return;
                    
                case "ENTITY_CRUD":
                    nodeOutput = executeEntityCrudNode(node, context);
                    break;
                    
                case "HTTP":
                    nodeOutput = executeHttpNode(node, context);
                    break;
                    
                case "SQL":
                    nodeOutput = executeSqlNode(node, context);
                    break;
                    
                case "SCRIPT":
                    nodeOutput = executeScriptNode(node, context);
                    break;
                    
                case "CONDITION":
                    // 条件节点在路由时处理
                    nodeOutput.putAll(context.getVariables());
                    break;
                    
                case "PARALLEL":
                    executeParallelNode(node, nodeMap, context, timeoutMs);
                    return;
                    
                case "MAPPING":
                    nodeOutput = executeMappingNode(node, context);
                    break;
                    
                case "LOG":
                    executeLogNode(node, context);
                    nodeOutput.putAll(context.getVariables());
                    break;
                    
                default:
                    log.warn("未知的节点类型: {}", node.getNodeType());
                    nodeOutput.putAll(context.getVariables());
            }
            
            // 保存节点输出
            context.getNodeOutputs().put(node.getNodeId(), nodeOutput);
            
            // 应用输出映射
            applyOutputMapping(node, context, nodeOutput);
            
        } catch (Exception e) {
            log.error("节点执行失败: {} - {}", node.getNodeId(), e.getMessage());
            throw e;
        }
        
        // 确定下一个节点
        List<String> nextNodeIds = parseNextNodes(node.getNextNodes());
        for (String nextNodeId : nextNodeIds) {
            ServiceNode nextNode = nodeMap.get(nextNodeId);
            if (nextNode != null) {
                // 检查条件
                if (shouldExecuteNode(nextNode, context)) {
                    executeNode(nextNode, nodeMap, context, timeoutMs);
                }
            }
        }
    }
    
    /**
     * 执行实体CRUD节点
     */
    private Map<String, Object> executeEntityCrudNode(ServiceNode node, ExecutionContext context) throws Exception {
        Map<String, Object> config = parseConfig(node.getConfig());
        String operation = (String) config.get("operation");
        String entityCode = (String) config.get("entityCode");
        
        Map<String, Object> result = new HashMap<>();
        
        // 这里简化处理，实际应该调用EntityDataService
        log.info("执行实体操作: {} - {}", operation, entityCode);
        result.put("success", true);
        result.put("entityCode", entityCode);
        result.put("operation", operation);
        
        return result;
    }
    
    /**
     * 执行HTTP节点
     */
    private Map<String, Object> executeHttpNode(ServiceNode node, ExecutionContext context) throws Exception {
        Map<String, Object> config = parseConfig(node.getConfig());
        String method = (String) config.getOrDefault("method", "GET");
        String url = (String) config.get("url");
        
        // 替换变量
        url = replaceVariables(url, context.getVariables());
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        HttpEntity<String> entity = new HttpEntity<>("", headers);
        
        ResponseEntity<String> response;
        switch (method.toUpperCase()) {
            case "POST":
                response = restTemplate.postForEntity(url, entity, String.class);
                break;
            case "GET":
            default:
                response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("statusCode", response.getStatusCodeValue());
        result.put("body", response.getBody());
        
        return result;
    }
    
    /**
     * 执行SQL节点
     */
    private Map<String, Object> executeSqlNode(ServiceNode node, ExecutionContext context) throws Exception {
        Map<String, Object> config = parseConfig(node.getConfig());
        String sql = (String) config.get("sql");
        
        // 替换变量
        sql = replaceVariables(sql, context.getVariables());
        
        log.info("执行SQL: {}", sql);
        
        List<Map<String, Object>> data = jdbcTemplate.queryForList(sql);
        
        Map<String, Object> result = new HashMap<>();
        result.put("data", data);
        result.put("count", data.size());
        
        return result;
    }
    
    /**
     * 执行脚本节点
     */
    private Map<String, Object> executeScriptNode(ServiceNode node, ExecutionContext context) throws Exception {
        Map<String, Object> config = parseConfig(node.getConfig());
        String scriptType = (String) config.getOrDefault("scriptType", "GROOVY");
        String script = (String) config.get("script");
        
        // 这里简化处理，实际应该使用GroovyScriptEngine
        log.info("执行脚本: {} - {}", scriptType, script.substring(0, Math.min(100, script.length())));
        
        Map<String, Object> result = new HashMap<>();
        result.put("result", "脚本执行结果");
        
        return result;
    }
    
    /**
     * 执行并行节点
     */
    private void executeParallelNode(ServiceNode node, Map<String, ServiceNode> nodeMap, 
                                     ExecutionContext context, Integer timeoutMs) throws Exception {
        List<String> branchNodeIds = parseNextNodes(node.getNextNodes());
        
        List<Future<?>> futures = new ArrayList<>();
        for (String branchNodeId : branchNodeIds) {
            ServiceNode branchNode = nodeMap.get(branchNodeId);
            if (branchNode != null) {
                Future<?> future = executorService.submit(() -> {
                    try {
                        executeNode(branchNode, nodeMap, context, timeoutMs);
                    } catch (Exception e) {
                        log.error("并行分支执行失败: {}", branchNodeId, e);
                    }
                });
                futures.add(future);
            }
        }
        
        // 等待所有分支完成
        for (Future<?> future : futures) {
            future.get(timeoutMs != null ? timeoutMs : 30000, TimeUnit.MILLISECONDS);
        }
    }
    
    /**
     * 执行映射节点
     */
    private Map<String, Object> executeMappingNode(ServiceNode node, ExecutionContext context) throws Exception {
        Map<String, Object> config = parseConfig(node.getConfig());
        Map<String, String> mappings = (Map<String, String>) config.get("mappings");
        
        Map<String, Object> result = new HashMap<>();
        if (mappings != null) {
            for (Map.Entry<String, String> entry : mappings.entrySet()) {
                String targetKey = entry.getKey();
                String sourceExpression = entry.getValue();
                Object value = evaluateExpression(sourceExpression, context.getVariables());
                result.put(targetKey, value);
            }
        }
        
        return result;
    }
    
    /**
     * 执行日志节点
     */
    private void executeLogNode(ServiceNode node, ExecutionContext context) throws Exception {
        Map<String, Object> config = parseConfig(node.getConfig());
        String message = (String) config.get("message");
        String level = (String) config.getOrDefault("level", "INFO");
        
        message = replaceVariables(message, context.getVariables());
        
        switch (level.toUpperCase()) {
            case "ERROR":
                log.error(message);
                break;
            case "WARN":
                log.warn(message);
                break;
            case "DEBUG":
                log.debug(message);
                break;
            default:
                log.info(message);
        }
    }
    
    /**
     * 判断是否应执行节点（条件判断）
     */
    private boolean shouldExecuteNode(ServiceNode node, ExecutionContext context) {
        if (node.getConditionExpression() == null || node.getConditionExpression().isEmpty()) {
            return true;
        }
        
        // 简单的条件表达式解析
        String expression = node.getConditionExpression();
        try {
            // 这里简化处理，实际应该使用表达式引擎如SpEL
            if (expression.contains("==")) {
                String[] parts = expression.split("==");
                String key = parts[0].trim().replace("${", "").replace("}", "");
                String expectedValue = parts[1].trim().replace("'", "").replace("\"", "");
                Object actualValue = context.getVariables().get(key);
                return expectedValue.equals(String.valueOf(actualValue));
            }
            return true;
        } catch (Exception e) {
            log.warn("条件表达式解析失败: {}", expression);
            return true;
        }
    }
    
    /**
     * 应用输出映射
     */
    private void applyOutputMapping(ServiceNode node, ExecutionContext context, Map<String, Object> nodeOutput) {
        if (node.getOutputMapping() == null || node.getOutputMapping().isEmpty()) {
            // 默认将所有输出合并到上下文变量
            context.getVariables().putAll(nodeOutput);
            return;
        }
        
        try {
            Map<String, String> outputMapping = objectMapper.readValue(node.getOutputMapping(), Map.class);
            for (Map.Entry<String, String> entry : outputMapping.entrySet()) {
                String sourceKey = entry.getKey();
                String targetKey = entry.getValue();
                Object value = nodeOutput.get(sourceKey);
                context.getVariables().put(targetKey, value);
            }
        } catch (Exception e) {
            log.warn("输出映射失败: {}", e.getMessage());
            context.getVariables().putAll(nodeOutput);
        }
    }
    
    /**
     * 解析配置JSON
     */
    private Map<String, Object> parseConfig(String config) throws Exception {
        if (config == null || config.isEmpty()) {
            return new HashMap<>();
        }
        return objectMapper.readValue(config, Map.class);
    }
    
    /**
     * 解析下游节点ID
     */
    private List<String> parseNextNodes(String nextNodes) {
        if (nextNodes == null || nextNodes.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return objectMapper.readValue(nextNodes, List.class);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }
    
    /**
     * 替换变量
     */
    private String replaceVariables(String str, Map<String, Object> variables) {
        if (str == null) return null;
        
        String result = str;
        for (Map.Entry<String, Object> entry : variables.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? entry.getValue().toString() : "";
            result = result.replace(placeholder, value);
        }
        return result;
    }
    
    /**
     * 评估表达式
     */
    private Object evaluateExpression(String expression, Map<String, Object> variables) {
        // 简化处理，直接返回变量值
        String key = expression.replace("${", "").replace("}", "").trim();
        return variables.get(key);
    }
    
    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "";
        }
    }
    
    /**
     * 执行上下文
     */
    @lombok.Data
    public static class ExecutionContext {
        private Map<String, Object> inputParams;
        private Map<String, Object> variables;
        private Map<String, Map<String, Object>> nodeOutputs;
        private String executionId;
    }
    
    /**
     * 执行结果
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class ExecutionResult {
        private boolean success;
        private String message;
        private Map<String, Object> output;
        private String executionId;
        private long durationMs;
        
        public static ExecutionResult success(Map<String, Object> output, String executionId, long durationMs) {
            return new ExecutionResult(true, "执行成功", output, executionId, durationMs);
        }
        
        public static ExecutionResult failure(String message, String executionId, long durationMs) {
            return new ExecutionResult(false, message, null, executionId, durationMs);
        }
    }
}
