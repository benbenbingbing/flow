package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务任务 REST 接口示例
 * 提供不修改任何数据的测试接口，供 BPMN 服务任务调用
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    /**
     * 示例 GET 接口：返回固定数据，不修改任何数据
     */
    @GetMapping("/hello")
    public ApiResponse<Map<String, Object>> hello(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String userId) {
        log.info("[DemoController] /api/demo/hello 被调用，name={}, userId={}", name, userId);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("message", "Hello from workflow demo");
        result.put("name", name != null ? name : "guest");
        result.put("timestamp", System.currentTimeMillis());
        result.put("dataSource", "demo");

        return ApiResponse.success(result);
    }

    /**
     * 示例 POST 接口：接收 JSON，返回处理结果，不修改任何数据
     */
    @PostMapping("/process")
    public ApiResponse<Map<String, Object>> process(@RequestBody Map<String, Object> body) {
        log.info("[DemoController] /api/demo/process 被调用，body={}", body);

        Map<String, Object> result = new HashMap<>();
        result.put("status", "ok");
        result.put("received", body);
        result.put("processed", true);
        result.put("timestamp", System.currentTimeMillis());

        return ApiResponse.success(result);
    }
}
