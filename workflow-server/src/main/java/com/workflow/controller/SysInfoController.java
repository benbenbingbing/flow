package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SysInfoController {

    @GetMapping("/info")
    public Result<Map<String, Object>> info() {
        Map<String, Object> data = new HashMap<>();
        data.put("version", "1.0.0");
        data.put("name", "工作流平台");
        return Result.success(data);
    }

    @GetMapping("/config")
    public Result<Map<String, Object>> config() {
        return Result.success(Map.of());
    }

    @GetMapping("/log/level")
    public Result<Map<String, Object>> logLevel() {
        return Result.success(Map.of("level", "INFO"));
    }

    @PutMapping("/log/level")
    public Result<Void> updateLogLevel(@RequestBody Map<String, String> dto) {
        return Result.success();
    }

    @GetMapping("/cache")
    public Result<Map<String, Object>> cache() {
        return Result.success(Map.of("keys", new java.util.ArrayList<>()));
    }

    @DeleteMapping("/cache")
    public Result<Void> clearCache() {
        return Result.success();
    }
}
