package com.workflow.controller;

import com.workflow.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 服务任务 REST 回调接口。
 * 用于 BPMN 服务任务联调，不修改业务数据。
 */
@Slf4j
@RestController
@RequestMapping("/api/demo")
public class DemoController {

    /**
     * GET 回调：返回固定数据，不修改任何数据。
     *
     * @param name   调用方传入的名称，可选
     * @param userId 调用方传入的用户ID，可选
     * @return 固定回调数据
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
     * POST 回调：接收 JSON，返回处理结果，不修改任何数据。
     *
     * @param body 调用方提交的请求体
     * @return 回显请求体并标记已处理的结果
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
