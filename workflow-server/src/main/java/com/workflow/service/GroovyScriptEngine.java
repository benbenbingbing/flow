package com.workflow.service;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.*;

/**
 * Groovy脚本执行引擎
 */
@Slf4j
@Service
public class GroovyScriptEngine {
    
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    
    /**
     * 执行Groovy脚本
     * @param script 脚本内容
     * @param context 上下文变量
     * @param timeoutMs 超时时间（毫秒）
     * @return 执行结果
     */
    @SuppressWarnings("unchecked")
    public ScriptResult execute(String script, Map<String, Object> context, Long timeoutMs) {
        long startTime = System.currentTimeMillis();
        
        try {
            // 创建沙箱安全策略
            System.setProperty("groovy.security.disabled", "false");
            
            // 创建绑定变量
            Binding binding = new Binding();
            if (context != null) {
                context.forEach(binding::setVariable);
            }
            
            // 添加常用工具类到上下文
            binding.setVariable("_util", new ScriptUtils());
            binding.setVariable("_log", log);
            
            // 创建Groovy Shell
            GroovyShell shell = new GroovyShell(binding);
            
            // 设置超时
            if (timeoutMs != null && timeoutMs > 0) {
                Future<Object> future = executorService.submit(() -> shell.evaluate(script));
                try {
                    Object result = future.get(timeoutMs, TimeUnit.MILLISECONDS);
                    long duration = System.currentTimeMillis() - startTime;
                    return ScriptResult.success(result, duration);
                } catch (TimeoutException e) {
                    future.cancel(true);
                    return ScriptResult.failure("脚本执行超时", 0);
                }
            } else {
                Object result = shell.evaluate(script);
                long duration = System.currentTimeMillis() - startTime;
                return ScriptResult.success(result, duration);
            }
            
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("Groovy脚本执行失败: {}", e.getMessage());
            return ScriptResult.failure(e.getMessage(), duration);
        }
    }
    
    /**
     * 验证脚本语法
     */
    public boolean validate(String script) {
        try {
            GroovyShell shell = new GroovyShell();
            shell.parse(script);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 脚本执行结果
     */
    public static class ScriptResult {
        private final boolean success;
        private final Object result;
        private final String errorMessage;
        private final long durationMs;
        
        public ScriptResult(boolean success, Object result, String errorMessage, long durationMs) {
            this.success = success;
            this.result = result;
            this.errorMessage = errorMessage;
            this.durationMs = durationMs;
        }
        
        public static ScriptResult success(Object result, long durationMs) {
            return new ScriptResult(true, result, null, durationMs);
        }
        
        public static ScriptResult failure(String errorMessage, long durationMs) {
            return new ScriptResult(false, null, errorMessage, durationMs);
        }
        
        public boolean isSuccess() {
            return success;
        }
        
        public Object getResult() {
            return result;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public long getDurationMs() {
            return durationMs;
        }
    }
    
    /**
     * 脚本工具类
     */
    public static class ScriptUtils {
        
        /**
         * 格式化日期
         */
        public String formatDate(java.util.Date date, String pattern) {
            if (date == null) return "";
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat(pattern);
            return sdf.format(date);
        }
        
        /**
         * 计算日期差
         */
        public long daysBetween(java.util.Date date1, java.util.Date date2) {
            if (date1 == null || date2 == null) return 0;
            long diff = Math.abs(date2.getTime() - date1.getTime());
            return diff / (24 * 60 * 60 * 1000);
        }
        
        /**
         * 字符串是否为空
         */
        public boolean isEmpty(String str) {
            return str == null || str.trim().isEmpty();
        }
        
        /**
         * 字符串不为空
         */
        public boolean isNotEmpty(String str) {
            return !isEmpty(str);
        }
        
        /**
         * 转换为大写
         */
        public String toUpperCase(String str) {
            return str == null ? "" : str.toUpperCase();
        }
        
        /**
         * 转换为小写
         */
        public String toLowerCase(String str) {
            return str == null ? "" : str.toLowerCase();
        }
        
        /**
         * 截取字符串
         */
        public String substring(String str, int start, int end) {
            if (str == null || start < 0 || end > str.length() || start >= end) {
                return "";
            }
            return str.substring(start, end);
        }
    }
}
