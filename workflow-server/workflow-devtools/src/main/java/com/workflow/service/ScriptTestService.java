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
        
        ScriptExecutionContext execution = new ScriptExecutionContext(dto.getTestVariables());
        
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
    private Object executeJavaScript(String script, ScriptExecutionContext execution) throws Exception {
        ScriptEngineManager manager = new ScriptEngineManager();
        ScriptEngine engine = manager.getEngineByName("nashorn");
        if (engine == null) {
            throw new RuntimeException("当前运行环境未提供 JavaScript 脚本引擎");
        }
        engine.put("execution", execution);
        return engine.eval(script);
    }
    
    /**
     * 执行 Groovy
     */
    private Object executeGroovy(String script, ScriptExecutionContext execution) throws Exception {
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
     * 脚本测试执行上下文。
     * 封装脚本执行期间可读写的变量集合，作为脚本与平台之间的变量桥梁。
     */
    public static class ScriptExecutionContext {
        /** 脚本执行期间的变量集合 */
        private final Map<String, Object> variables = new HashMap<>();

        /**
         * 根据传入的初始变量构造执行上下文。
         *
         * @param initialVars 初始变量集合，可为 null
         */
        public ScriptExecutionContext(Map<String, Object> initialVars) {
            if (initialVars != null) {
                variables.putAll(initialVars);
            }
        }

        /**
         * 设置变量。
         *
         * @param name  变量名
         * @param value 变量值
         */
        public void setVariable(String name, Object value) {
            variables.put(name, value);
        }

        /**
         * 获取变量值。
         *
         * @param name 变量名
         * @return 变量值，不存在时返回 null
         */
        public Object getVariable(String name) {
            return variables.get(name);
        }

        /**
         * 判断变量是否存在。
         *
         * @param name 变量名
         * @return 存在返回 true，否则返回 false
         */
        public boolean hasVariable(String name) {
            return variables.containsKey(name);
        }

        /**
         * 移除指定变量。
         *
         * @param name 变量名
         */
        public void removeVariable(String name) {
            variables.remove(name);
        }

        /**
         * 获取所有变量的副本。
         *
         * @return 变量集合的拷贝，对返回值的修改不影响内部状态
         */
        public Map<String, Object> getVariables() {
            return new HashMap<>(variables);
        }
    }
}
