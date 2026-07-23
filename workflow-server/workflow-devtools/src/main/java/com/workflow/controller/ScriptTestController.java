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
    
    /** 脚本测试服务 */
    private final ScriptTestService scriptTestService;

    /**
     * 测试脚本执行。
     *
     * @param dto 脚本测试请求
     * @return 脚本执行结果，包含 success、result、variables、message 等字段
     */
    @PostMapping("/test")
    public ApiResponse<Map<String, Object>> testScript(@RequestBody ScriptTestDTO dto) {
        Map<String, Object> result = scriptTestService.testScript(dto);
        return ApiResponse.success(result);
    }
}
