package com.workflow.controller;

import com.workflow.common.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/monitor")
@RequiredArgsConstructor
public class MonitorController {

    @GetMapping("/system")
    public Result<Map<String, Object>> system() {
        Map<String, Object> data = new HashMap<>();
        data.put("cpu", Map.of("usage", 0.15));
        data.put("memory", Map.of("usage", 0.42));
        data.put("disk", Map.of("usage", 0.35));
        return Result.success(data);
    }

    @GetMapping("/online")
    public Result<Map<String, Object>> online() {
        return Result.success(Map.of("total", 1, "list", new ArrayList<>()));
    }

    @GetMapping("/data")
    public Result<Map<String, Object>> data() {
        return Result.success(Map.of());
    }

    @GetMapping("/server")
    public Result<Map<String, Object>> server() {
        Map<String, Object> data = new HashMap<>();
        data.put("os", System.getProperty("os.name"));
        data.put("javaVersion", System.getProperty("java.version"));
        return Result.success(data);
    }
}
