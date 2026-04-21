package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import com.workflow.service.GroovyScriptEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 脚本引擎控制器
 */
@RestController
@RequestMapping("/api/script-engine")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ScriptEngineController {
    
    private final GroovyScriptEngine scriptEngine;
    
    /**
     * 执行Groovy脚本
     */
    @PostMapping("/execute")
    public ApiResponse<GroovyScriptEngine.ScriptResult> execute(@RequestBody ScriptExecuteDTO dto) {
        GroovyScriptEngine.ScriptResult result = scriptEngine.execute(
                dto.getScript(), 
                dto.getContext(),
                dto.getTimeoutMs()
        );
        return ApiResponse.success(result);
    }
    
    /**
     * 验证脚本语法
     */
    @PostMapping("/validate")
    public ApiResponse<Boolean> validate(@RequestBody ScriptValidateDTO dto) {
        boolean valid = scriptEngine.validate(dto.getScript());
        return ApiResponse.success(valid);
    }
    
    /**
     * 获取脚本模板
     */
    @GetMapping("/templates")
    public ApiResponse<Map<String, String>> getTemplates() {
        Map<String, String> templates = Map.of(
                "basic", "// 基础脚本模板\\n\\ndef name = 'World'\\nprintln \"Hello, ${name}!\"\\n\\nreturn [message: \"Hello, ${name}!\"]",
                "calculation", "// 计算脚本模板\\n\\ndef a = 10\\ndef b = 20\\ndef sum = a + b\\n\\nreturn [a: a, b: b, sum: sum]",
                "condition", "// 条件判断脚本模板\\n\\ndef score = 85\\ndef result = score >= 60 ? '及格' : '不及格'\\n\\nreturn [score: score, result: result]",
                "loop", "// 循环脚本模板\\n\\ndef items = [1, 2, 3, 4, 5]\\ndef sum = 0\\nitems.each { item ->\\n    sum += item\\n}\\n\\nreturn [items: items, sum: sum]"
        );
        return ApiResponse.success(templates);
    }
    
    // DTO
    @lombok.Data
    public static class ScriptExecuteDTO {
        private String script;
        private Map<String, Object> context;
        private Long timeoutMs;
    }
    
    @lombok.Data
    public static class ScriptValidateDTO {
        private String script;
    }
}
