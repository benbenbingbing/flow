package com.workflow.controller;

import com.workflow.dto.ScriptTestDTO;
import com.workflow.service.ScriptTestService;
import com.workflow.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 脚本测试控制器
 */
@RestController
@RequestMapping("/api/script")
@RequiredArgsConstructor
public class ScriptTestController {
    
    private final ScriptTestService scriptTestService;
    
    /**
     * 测试脚本执行
     */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testScript(@RequestBody ScriptTestDTO dto) {
        Map<String, Object> result = scriptTestService.testScript(dto);
        return ApiResponse.success(result);
    }
}
