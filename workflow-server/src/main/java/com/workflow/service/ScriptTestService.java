package com.workflow.service;

import com.workflow.dto.ScriptTestDTO;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.util.HashMap;
import java.util.Map;

/**
 * 脚本测试服务
 * 用于在流程设计阶段测试脚本代码的语法和执行结果
 */
@Slf4j
@Service
public class ScriptTestService {

    /**
     * 测试脚本执行
     *
     * @param dto 测试请求
     * @return 测试结果：success、result、variables、message
     */
    public Map<String, Object> testScript(ScriptTestDTO dto) {
        Map<String, Object> result = new HashMap<>();
        String format = dto.getScriptFormat() != null ? dto.getScriptFormat().toLowerCase() : "javascript";
        String script = dto.getScript();
        
        if (script == null || script.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "脚本内容不能为空");
            return result;
        }
        
        // 创建模拟 execution 对象
        MockExecution execution = new MockExecution(dto.getTestVariables());
        
        try {
            Object evalResult;
            switch (format) {
                case "groovy":
                    evalResult = executeGroovy(script, execution);
                    break;
                case "python":
                    result.put("success", false);
                    result.put("message", "Python 脚本测试需要 Jython 环境，当前暂不支持在线测试，请发布后在流程中验证");
                    return result;
                case "javascript":
                default:
                    evalResult = executeJavaScript(script, execution);
                    break;
            }
            
            result.put("success", true);
            result.put("result", evalResult);
            result.put("variables", execution.getVariables());
            result.put("message", "脚本执行成功");
            
            // 如果配置了 resultVariable，尝试从 execution 变量中获取
            if (dto.getResultVariable() != null && !dto.getResultVariable().isEmpty()) {
                Object rv = execution.getVariable(dto.getResultVariable());
                if (rv != null) {
                    result.put("resultVariableValue", rv);
                }
            }
            
        } catch (Exception e) {
            log.debug("脚本测试执行失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", extractErrorMessage(e));
        }
        
        return result;
    }
    
    /**
     * 执行 JavaScript (Nashorn)
     */
    private Object executeJavaScript(String script, MockExecution execution) throws Exception {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("nashorn");
        if (engine == null) {
            throw new RuntimeException("Nashorn 引擎不可用（Java 15+ 已移除，请使用 Java 11）");
        }
        engine.put("execution", execution);
        return engine.eval(script);
    }
    
    /**
     * 执行 Groovy
     */
    private Object executeGroovy(String script, MockExecution execution) throws Exception {
        GroovyShell shell = new GroovyShell();
        shell.setVariable("execution", execution);
        return shell.evaluate(script);
    }
    
    /**
     * 提取简洁的错误信息
     */
    private String extractErrorMessage(Throwable e) {
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) {
            msg = e.getClass().getSimpleName();
        }
        // 截断过长的错误信息
        if (msg.length() > 300) {
            msg = msg.substring(0, 300) + "...";
        }
        return msg;
    }
    
    /**
     * 模拟 Flowable Execution 对象
     */
    public static class MockExecution {
        private final Map<String, Object> variables = new HashMap<>();
        
        public MockExecution(Map<String, Object> initialVars) {
            if (initialVars != null) {
                variables.putAll(initialVars);
            }
        }
        
        public void setVariable(String name, Object value) {
            variables.put(name, value);
        }
        
        public Object getVariable(String name) {
            return variables.get(name);
        }
        
        public boolean hasVariable(String name) {
            return variables.containsKey(name);
        }
        
        public void removeVariable(String name) {
            variables.remove(name);
        }
        
        public Map<String, Object> getVariables() {
            return new HashMap<>(variables);
        }
    }
}
